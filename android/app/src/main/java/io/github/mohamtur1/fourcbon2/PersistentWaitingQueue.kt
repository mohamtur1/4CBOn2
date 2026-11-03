package io.github.mohamtur1.fourcbon2

import java.util.UUID

/**
 * Division 19: Persistent Waiting Queue (durable outbox).
 *
 * Persists queued jobs across app restarts, defers network/account/runtime-dependent tasks
 * honestly, enforces deadlines, and guarantees that interrupted consequential external jobs
 * transition to [QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME] on restart rather than replaying
 * automatically.
 */
enum class QueuedJobState(val label: String) {
    DRAFT("Draft"),
    WAITING_FOR_INTERNET("Waiting for Internet"),
    WAITING_FOR_ACCOUNT("Waiting for Account"),
    WAITING_FOR_RUNTIME("Waiting for Runtime"),
    WAITING_FOR_APPROVAL("Waiting for Approval"),
    READY("Ready"),
    RUNNING("Running"),
    UNCERTAIN_EXTERNAL_OUTCOME("Uncertain External Outcome"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
}

data class QueueEnvironmentSnapshot(
    val isNetworkAvailable: Boolean,
    val configuredCredentials: Set<CredentialSlot>,
    val isColabRuntimeVerified: Boolean = false,
)

data class QueuedJob(
    val id: String,
    val draftId: String?,
    val title: String,
    val goal: String,
    val profile: JobProfile,
    val workflowVersion: Int,
    val inputArtifactHashes: List<String>,
    val maxSteps: Int,
    val deadlineEpochMs: Long?,
    val requiresNetwork: Boolean,
    val requiredCredentialSlot: CredentialSlot?,
    val requiresColabRuntime: Boolean,
    val consequentialExternalAction: Boolean,
    val approvedByOwner: Boolean,
    val state: QueuedJobState,
    val statusDetail: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "id" to JsonValue.Str(id),
            "draftId" to (draftId?.let(JsonValue::Str) ?: JsonValue.Null),
            "title" to JsonValue.Str(title),
            "goal" to JsonValue.Str(goal),
            "profile" to JsonValue.Str(profile.name),
            "workflowVersion" to JsonValue.Num(workflowVersion.toDouble(), workflowVersion.toString()),
            "inputArtifactHashes" to JsonValue.Arr(inputArtifactHashes.map(JsonValue::Str)),
            "maxSteps" to JsonValue.Num(maxSteps.toDouble(), maxSteps.toString()),
            "deadlineEpochMs" to (
                deadlineEpochMs?.let { JsonValue.Num(it.toDouble(), it.toString()) } ?: JsonValue.Null
                ),
            "requiresNetwork" to JsonValue.Bool(requiresNetwork),
            "requiredCredentialSlot" to (requiredCredentialSlot?.name?.let(JsonValue::Str) ?: JsonValue.Null),
            "requiresColabRuntime" to JsonValue.Bool(requiresColabRuntime),
            "consequentialExternalAction" to JsonValue.Bool(consequentialExternalAction),
            "approvedByOwner" to JsonValue.Bool(approvedByOwner),
            "state" to JsonValue.Str(state.name),
            "statusDetail" to JsonValue.Str(statusDetail),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "updatedAtEpochMs" to JsonValue.Num(updatedAtEpochMs.toDouble(), updatedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): QueuedJob = QueuedJob(
            id = obj.stringOrDefault("id", "job_${UUID.randomUUID().toString().take(8)}"),
            draftId = obj.string("draftId"),
            title = obj.stringOrDefault("title", "Queued Job"),
            goal = obj.stringOrDefault("goal", ""),
            profile = runCatching { JobProfile.valueOf(obj.stringOrDefault("profile")) }
                .getOrDefault(JobProfile.RESEARCH_SYNTHESIS),
            workflowVersion = obj.int("workflowVersion", 1),
            inputArtifactHashes = obj.stringList("inputArtifactHashes"),
            maxSteps = obj.int("maxSteps", 10),
            deadlineEpochMs = obj.entries["deadlineEpochMs"]?.let { (it as? JsonValue.Num)?.toLongOrNull() },
            requiresNetwork = obj.bool("requiresNetwork", false),
            requiredCredentialSlot = obj.string("requiredCredentialSlot")?.let {
                runCatching { CredentialSlot.valueOf(it) }.getOrNull()
            },
            requiresColabRuntime = obj.bool("requiresColabRuntime", false),
            consequentialExternalAction = obj.bool("consequentialExternalAction", false),
            approvedByOwner = obj.bool("approvedByOwner", false),
            state = runCatching { QueuedJobState.valueOf(obj.stringOrDefault("state")) }
                .getOrDefault(QueuedJobState.DRAFT),
            statusDetail = obj.stringOrDefault("statusDetail", ""),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            updatedAtEpochMs = obj.long("updatedAtEpochMs", 0L),
        )
    }
}

class PersistentWaitingQueue(private val database: FourCBOn2Database) {
    fun enqueueFromDraft(
        draft: AgentDraft,
        env: QueueEnvironmentSnapshot,
        requiredCredentialSlot: CredentialSlot? = defaultSlotFor(draft),
        requiresColabRuntime: Boolean = draft.caps.allowCloudAi,
        consequentialExternalAction: Boolean = false,
        inputArtifactHashes: List<String> = emptyList(),
        deadlineEpochMs: Long? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): QueuedJob {
        val requiresNetwork = draft.caps.allowNetwork || requiresColabRuntime || requiredCredentialSlot != null
        val (initialState, detail) = evaluateWaitingState(
            requiresNetwork = requiresNetwork,
            requiredCredentialSlot = requiredCredentialSlot,
            requiresColabRuntime = requiresColabRuntime,
            consequentialExternalAction = consequentialExternalAction,
            approvedByOwner = false,
            deadlineEpochMs = deadlineEpochMs,
            env = env,
            nowEpochMs = nowEpochMs,
        )
        val job = QueuedJob(
            id = "job_${UUID.randomUUID().toString().take(8)}",
            draftId = draft.id,
            title = draft.title,
            goal = draft.goal,
            profile = draft.profile,
            workflowVersion = 1,
            inputArtifactHashes = inputArtifactHashes,
            maxSteps = draft.caps.maxSteps,
            deadlineEpochMs = deadlineEpochMs,
            requiresNetwork = requiresNetwork,
            requiredCredentialSlot = requiredCredentialSlot,
            requiresColabRuntime = requiresColabRuntime,
            consequentialExternalAction = consequentialExternalAction,
            approvedByOwner = false,
            state = initialState,
            statusDetail = detail,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
        return persist(job)
    }

    /**
     * Reconciles durable queue state on application startup after a restart or crash.
     *
     * Safety invariant: Any job that was [QueuedJobState.RUNNING] and carries
     * [QueuedJob.consequentialExternalAction] == true transitions to
     * [QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME] and is NEVER automatically re-dispatched.
     */
    fun recoverAfterRestart(
        env: QueueEnvironmentSnapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<QueuedJob> {
        val current = listJobs()
        return current.map { job ->
            when (job.state) {
                QueuedJobState.RUNNING -> {
                    if (job.consequentialExternalAction) {
                        persist(
                            job.copy(
                                state = QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME,
                                statusDetail = "App restarted while an external action was in flight; manual check required before retry.",
                                updatedAtEpochMs = nowEpochMs,
                            ),
                        )
                    } else {
                        val (nextState, detail) = evaluateWaitingState(
                            requiresNetwork = job.requiresNetwork,
                            requiredCredentialSlot = job.requiredCredentialSlot,
                            requiresColabRuntime = job.requiresColabRuntime,
                            consequentialExternalAction = false,
                            approvedByOwner = job.approvedByOwner,
                            deadlineEpochMs = job.deadlineEpochMs,
                            env = env,
                            nowEpochMs = nowEpochMs,
                        )
                        persist(
                            job.copy(
                                state = nextState,
                                statusDetail = "Recovered after restart: $detail",
                                updatedAtEpochMs = nowEpochMs,
                            ),
                        )
                    }
                }
                QueuedJobState.WAITING_FOR_INTERNET,
                QueuedJobState.WAITING_FOR_ACCOUNT,
                QueuedJobState.WAITING_FOR_RUNTIME,
                QueuedJobState.WAITING_FOR_APPROVAL,
                QueuedJobState.READY,
                -> refreshSingleJob(job, env, nowEpochMs)
                else -> job
            }
        }
    }

    /**
     * Re-evaluates open waiting jobs when connectivity, credentials, or runtime state changes.
     * Never modifies [QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME], [QueuedJobState.COMPLETED],
     * [QueuedJobState.FAILED], or [QueuedJobState.CANCELLED].
     */
    fun refreshReadiness(
        env: QueueEnvironmentSnapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<QueuedJob> = listJobs().map { job ->
        when (job.state) {
            QueuedJobState.WAITING_FOR_INTERNET,
            QueuedJobState.WAITING_FOR_ACCOUNT,
            QueuedJobState.WAITING_FOR_RUNTIME,
            QueuedJobState.WAITING_FOR_APPROVAL,
            QueuedJobState.READY,
            -> refreshSingleJob(job, env, nowEpochMs)
            else -> job
        }
    }

    fun approveJob(
        jobId: String,
        env: QueueEnvironmentSnapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): QueuedJob? {
        val job = getJob(jobId) ?: return null
        if (job.state == QueuedJobState.CANCELLED || job.state == QueuedJobState.COMPLETED) return job
        val (nextState, detail) = evaluateWaitingState(
            requiresNetwork = job.requiresNetwork,
            requiredCredentialSlot = job.requiredCredentialSlot,
            requiresColabRuntime = job.requiresColabRuntime,
            consequentialExternalAction = job.consequentialExternalAction,
            approvedByOwner = true,
            deadlineEpochMs = job.deadlineEpochMs,
            env = env,
            nowEpochMs = nowEpochMs,
        )
        return persist(
            job.copy(
                approvedByOwner = true,
                state = nextState,
                statusDetail = detail,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    fun markRunning(jobId: String, nowEpochMs: Long = System.currentTimeMillis()): QueuedJob? {
        val job = getJob(jobId) ?: return null
        require(job.state == QueuedJobState.READY) {
            "Only READY jobs may start running (current state: ${job.state})."
        }
        return persist(
            job.copy(
                state = QueuedJobState.RUNNING,
                statusDetail = "Executing within bounded cap (${job.maxSteps} max steps).",
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    fun markCompleted(
        jobId: String,
        summary: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): QueuedJob? {
        val job = getJob(jobId) ?: return null
        return persist(
            job.copy(
                state = QueuedJobState.COMPLETED,
                statusDetail = summary,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    /**
     * Explicit owner reconciliation for [QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME].
     */
    fun reconcileUncertainOutcome(
        jobId: String,
        externalSucceeded: Boolean,
        note: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): QueuedJob? {
        val job = getJob(jobId) ?: return null
        if (job.state != QueuedJobState.UNCERTAIN_EXTERNAL_OUTCOME) return job
        return persist(
            job.copy(
                state = if (externalSucceeded) QueuedJobState.COMPLETED else QueuedJobState.FAILED,
                statusDetail = note.ifBlank {
                    if (externalSucceeded) "Owner confirmed external completion." else "Owner marked external attempt failed."
                },
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    fun cancelJob(jobId: String, nowEpochMs: Long = System.currentTimeMillis()): QueuedJob? {
        val job = getJob(jobId) ?: return null
        if (job.state == QueuedJobState.COMPLETED) return job
        return persist(
            job.copy(
                state = QueuedJobState.CANCELLED,
                statusDetail = "Cancelled by owner.",
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    fun getJob(jobId: String): QueuedJob? {
        val row = database.backend.get(FourCBOn2Database.NS_WAITING_QUEUE, jobId) ?: return null
        return runCatching { QueuedJob.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listJobs(): List<QueuedJob> =
        database.backend.listByNamespace(FourCBOn2Database.NS_WAITING_QUEUE)
            .mapNotNull { row ->
                runCatching { QueuedJob.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    private fun refreshSingleJob(
        job: QueuedJob,
        env: QueueEnvironmentSnapshot,
        nowEpochMs: Long,
    ): QueuedJob {
        val (nextState, detail) = evaluateWaitingState(
            requiresNetwork = job.requiresNetwork,
            requiredCredentialSlot = job.requiredCredentialSlot,
            requiresColabRuntime = job.requiresColabRuntime,
            consequentialExternalAction = job.consequentialExternalAction,
            approvedByOwner = job.approvedByOwner,
            deadlineEpochMs = job.deadlineEpochMs,
            env = env,
            nowEpochMs = nowEpochMs,
        )
        return if (nextState == job.state && detail == job.statusDetail) {
            job
        } else {
            persist(job.copy(state = nextState, statusDetail = detail, updatedAtEpochMs = nowEpochMs))
        }
    }

    private fun evaluateWaitingState(
        requiresNetwork: Boolean,
        requiredCredentialSlot: CredentialSlot?,
        requiresColabRuntime: Boolean,
        consequentialExternalAction: Boolean,
        approvedByOwner: Boolean,
        deadlineEpochMs: Long?,
        env: QueueEnvironmentSnapshot,
        nowEpochMs: Long,
    ): Pair<QueuedJobState, String> {
        if (deadlineEpochMs != null && nowEpochMs > deadlineEpochMs) {
            return QueuedJobState.FAILED to "Deadline expired before execution."
        }
        if (requiresNetwork && !env.isNetworkAvailable) {
            return QueuedJobState.WAITING_FOR_INTERNET to "Deferred offline: waiting for network connectivity."
        }
        if (requiredCredentialSlot != null && !isSlotSatisfied(requiredCredentialSlot, env.configuredCredentials)) {
            return QueuedJobState.WAITING_FOR_ACCOUNT to "Waiting for local credential: ${requiredCredentialSlot.displayTitle}."
        }
        if (requiresColabRuntime && !env.isColabRuntimeVerified) {
            return QueuedJobState.WAITING_FOR_RUNTIME to "Waiting for verified Colab runtime connection."
        }
        if (consequentialExternalAction && !approvedByOwner) {
            return QueuedJobState.WAITING_FOR_APPROVAL to "Requires explicit owner approval before external action."
        }
        return QueuedJobState.READY to if (requiresNetwork) {
            "Prerequisites met; ready to dispatch."
        } else {
            "Offline/local task ready to run."
        }
    }

    private fun isSlotSatisfied(required: CredentialSlot, configured: Set<CredentialSlot>): Boolean =
        when (required) {
            CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN,
            CredentialSlot.KAGGLE_API_TOKEN,
            -> CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN in configured ||
                CredentialSlot.KAGGLE_API_TOKEN in configured
            else -> required in configured
        }

    private fun defaultSlotFor(draft: AgentDraft): CredentialSlot? = when {
        !draft.caps.allowNetwork -> null
        draft.profile == JobProfile.COMPETITION_SOLVER -> CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN
        draft.profile == JobProfile.CODE_COMPANION -> CredentialSlot.GITHUB_FINE_GRAINED_PAT
        else -> null
    }

    private fun persist(job: QueuedJob): QueuedJob {
        database.backend.upsert(
            namespace = FourCBOn2Database.NS_WAITING_QUEUE,
            id = job.id,
            payloadJson = JsonCodec.stringify(job.toJson()),
            updatedAtEpochMs = job.updatedAtEpochMs,
        )
        return job
    }
}
