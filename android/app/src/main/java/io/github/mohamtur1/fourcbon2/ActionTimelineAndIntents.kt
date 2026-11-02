package io.github.mohamtur1.fourcbon2

import java.util.UUID

/**
 * Divisions 21 & 27:
 * - Division 27: Actual Action Timeline — records real plan/action/result/check events and
 *   distinguishes attempted, pending, manual, verified, blocked, and uncertain outcomes.
 * - Division 21: One harmless app intent/API action — executes supported open/prepare/share
 *   actions through the full Takeover Lease (14) -> Critical Guard (32) -> Scope Broker (13)
 *   pipeline with truthful handoff semantics and zero unrestricted UI tapping.
 */
enum class TimelineStage(val label: String) {
    PLAN("Plan"),
    APPROVAL_CHECK("Gate Check"),
    ACTION("Action"),
    OBSERVATION("Observation"),
    VERIFICATION("Check"),
    TAKEOVER_OR_STOP("Takeover / Stop"),
}

enum class TimelineActor(val label: String) {
    LOCAL_BROKER("Local Broker"),
    LOCAL_ROUTINE("Local Routine"),
    OWNER("Owner"),
    COLAB_AGENT("Colab Agent"),
    BROWSER_WORKSPACE("Browser Workspace"),
}

enum class TimelineOutcomeState(val label: String) {
    ATTEMPTED("Attempted"),
    PENDING("Pending"),
    MANUAL_HANDOFF("Manual Handoff"),
    VERIFIED("Verified"),
    UNCERTAIN("Uncertain"),
    BLOCKED("Blocked"),
    FAILED("Failed"),
}

data class ActionTimelineEvent(
    val id: String,
    val jobId: String?,
    val stage: TimelineStage,
    val actor: TimelineActor,
    val summary: String,
    val targetReference: String?,
    val outcome: TimelineOutcomeState,
    val timestampEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "id" to JsonValue.Str(id),
            "jobId" to (jobId?.let(JsonValue::Str) ?: JsonValue.Null),
            "stage" to JsonValue.Str(stage.name),
            "actor" to JsonValue.Str(actor.name),
            "summary" to JsonValue.Str(summary),
            "targetReference" to (targetReference?.let(JsonValue::Str) ?: JsonValue.Null),
            "outcome" to JsonValue.Str(outcome.name),
            "timestampEpochMs" to JsonValue.Num(timestampEpochMs.toDouble(), timestampEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): ActionTimelineEvent = ActionTimelineEvent(
            id = obj.stringOrDefault("id", "evt_${UUID.randomUUID().toString().take(8)}"),
            jobId = obj.string("jobId"),
            stage = runCatching { TimelineStage.valueOf(obj.stringOrDefault("stage")) }
                .getOrDefault(TimelineStage.ACTION),
            actor = runCatching { TimelineActor.valueOf(obj.stringOrDefault("actor")) }
                .getOrDefault(TimelineActor.LOCAL_BROKER),
            summary = obj.stringOrDefault("summary", ""),
            targetReference = obj.string("targetReference"),
            outcome = runCatching { TimelineOutcomeState.valueOf(obj.stringOrDefault("outcome")) }
                .getOrDefault(TimelineOutcomeState.ATTEMPTED),
            timestampEpochMs = obj.long("timestampEpochMs", 0L),
        )
    }
}

class ActionTimelineRepository(private val database: FourCBOn2Database) {
    fun recordEvent(
        stage: TimelineStage,
        actor: TimelineActor,
        summary: String,
        outcome: TimelineOutcomeState,
        jobId: String? = null,
        targetReference: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ActionTimelineEvent {
        val redactedSummary = ConsentedObservationBoundary.redactSecrets(summary.trim())
        val event = ActionTimelineEvent(
            id = "evt_${nowEpochMs}_${UUID.randomUUID().toString().take(6)}",
            jobId = jobId,
            stage = stage,
            actor = actor,
            summary = redactedSummary,
            targetReference = targetReference?.trim()?.takeIf { it.isNotEmpty() },
            outcome = outcome,
            timestampEpochMs = nowEpochMs,
        )
        database.backend.upsert(
            namespace = NS_ACTION_TIMELINE,
            id = event.id,
            payloadJson = JsonCodec.stringify(event.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        return event
    }

    fun listEvents(jobIdFilter: String? = null, limit: Int = 50): List<ActionTimelineEvent> =
        database.backend.listByNamespace(NS_ACTION_TIMELINE)
            .mapNotNull { row ->
                runCatching { ActionTimelineEvent.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }
            .let { events ->
                if (jobIdFilter == null) events else events.filter { it.jobId == jobIdFilter }
            }
            .take(limit)

    fun clearTimeline() {
        database.backend.clearNamespace(NS_ACTION_TIMELINE)
    }

    companion object {
        const val NS_ACTION_TIMELINE = "action_timeline"
    }
}

// --- Division 21: Harmless App Intent / Prepare Action ---

sealed interface HarmlessIntentSpec {
    val targetPackage: String
    val requiredActionType: ScopedActionType

    data class SharePlainTextDraft(
        override val targetPackage: String,
        val subject: String,
        val draftBody: String,
        val dataRef: String? = null,
    ) : HarmlessIntentSpec {
        override val requiredActionType: ScopedActionType = ScopedActionType.SHARE_TEXT_DRAFT
    }

    data class PrepareCalendarDraft(
        override val targetPackage: String,
        val title: String,
        val description: String,
        val beginTimeEpochMs: Long,
        val endTimeEpochMs: Long,
    ) : HarmlessIntentSpec {
        override val requiredActionType: ScopedActionType = ScopedActionType.PREPARE_CALENDAR_EVENT
    }

    data class OpenHttpsDestination(
        override val targetPackage: String,
        val httpsUrl: String,
    ) : HarmlessIntentSpec {
        override val requiredActionType: ScopedActionType = ScopedActionType.OPEN_HTTPS_URL
    }
}

sealed interface IntentDispatchResult {
    data class HandedOffToApp(val targetPackage: String, val detail: String) : IntentDispatchResult
    data class NoHandlerAvailable(val targetPackage: String) : IntentDispatchResult
    data class BlockedByLease(val reason: String) : IntentDispatchResult
    data class BlockedByCriticalGuard(val reason: String, val manualOnly: Boolean) : IntentDispatchResult
    data class BlockedByScope(val reason: String) : IntentDispatchResult
    data class InvalidParameters(val reason: String) : IntentDispatchResult
}

fun interface PlatformIntentLauncher {
    fun launchPreparedIntent(spec: HarmlessIntentSpec): Boolean
}

class HarmlessAppIntentExecutor(
    private val leaseController: TakeoverLeaseController,
    private val criticalGuard: CriticalPhoneActionGuard,
    private val scopeBroker: OnDeviceScopeBroker,
    private val timeline: ActionTimelineRepository,
    private val launcher: PlatformIntentLauncher,
) {
    fun execute(
        sessionId: String,
        commandEpoch: Long,
        scopeContractId: String,
        spec: HarmlessIntentSpec,
        riskContext: ActionRiskContext = ActionRiskContext.HARMLESS_LOCAL_OR_PREPARE,
        jobId: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): IntentDispatchResult {
        // 1. Validate Takeover / Cancellation Lease (Division 14)
        when (val lease = leaseController.validateCommand(sessionId, commandEpoch)) {
            is LeaseValidationOutcome.RejectedStaleOrCancelled -> {
                timeline.recordEvent(
                    stage = TimelineStage.TAKEOVER_OR_STOP,
                    actor = TimelineActor.LOCAL_BROKER,
                    summary = "Blocked intent (${spec.requiredActionType.name}): ${lease.reason}",
                    outcome = TimelineOutcomeState.BLOCKED,
                    jobId = jobId,
                    targetReference = spec.targetPackage,
                    nowEpochMs = nowEpochMs,
                )
                return IntentDispatchResult.BlockedByLease(lease.reason)
            }
            is LeaseValidationOutcome.Valid -> Unit
        }

        // 2. Validate Critical Phone-Action Guard (Division 32)
        val guardDecision = criticalGuard.evaluate(
            CriticalActionRequest(
                idempotencyKey = "intent_${sessionId}_${commandEpoch}_${spec.targetPackage}",
                riskContext = riskContext,
                targetAppOrService = spec.targetPackage,
                activeAccount = null,
                actionSummary = spec.requiredActionType.name,
                payloadSha256 = "local_prepare",
                previewShownToOwner = null,
            ),
            nowEpochMs = nowEpochMs,
        )
        when (guardDecision) {
            is CriticalGuardDecision.ManualHandoffRequired -> {
                timeline.recordEvent(
                    stage = TimelineStage.APPROVAL_CHECK,
                    actor = TimelineActor.LOCAL_BROKER,
                    summary = guardDecision.reason,
                    outcome = TimelineOutcomeState.MANUAL_HANDOFF,
                    jobId = jobId,
                    targetReference = spec.targetPackage,
                    nowEpochMs = nowEpochMs,
                )
                return IntentDispatchResult.BlockedByCriticalGuard(guardDecision.reason, manualOnly = true)
            }
            is CriticalGuardDecision.Blocked -> {
                timeline.recordEvent(
                    stage = TimelineStage.APPROVAL_CHECK,
                    actor = TimelineActor.LOCAL_BROKER,
                    summary = guardDecision.reason,
                    outcome = TimelineOutcomeState.BLOCKED,
                    jobId = jobId,
                    targetReference = spec.targetPackage,
                    nowEpochMs = nowEpochMs,
                )
                return IntentDispatchResult.BlockedByCriticalGuard(guardDecision.reason, manualOnly = false)
            }
            CriticalGuardDecision.Permitted -> Unit
        }

        // 3. Validate Spec Parameters
        if (spec is HarmlessIntentSpec.OpenHttpsDestination) {
            val urlCheck = ControlledUrlValidator.validatePublicHttpsUrl(spec.httpsUrl)
            if (urlCheck is UrlValidationResult.Rejected) {
                return IntentDispatchResult.InvalidParameters(urlCheck.reason)
            }
        }

        // 4. Validate On-Device Scope Contract + Selected-App Capability Registry (Divisions 12 & 13)
        val dataRef = (spec as? HarmlessIntentSpec.SharePlainTextDraft)?.dataRef
        when (
            val scopeRes = scopeBroker.authorizeAndConsume(
                ProposedScopedAction(
                    contractId = scopeContractId,
                    targetPackage = spec.targetPackage,
                    actionType = spec.requiredActionType,
                    dataRef = dataRef,
                    transfersDataToCloud = false,
                ),
                nowEpochMs = nowEpochMs,
            )
        ) {
            is ScopeDecision.Denied -> {
                timeline.recordEvent(
                    stage = TimelineStage.APPROVAL_CHECK,
                    actor = TimelineActor.LOCAL_BROKER,
                    summary = "Scope denied ${spec.requiredActionType.name}: ${scopeRes.reason}",
                    outcome = TimelineOutcomeState.BLOCKED,
                    jobId = jobId,
                    targetReference = spec.targetPackage,
                    nowEpochMs = nowEpochMs,
                )
                return IntentDispatchResult.BlockedByScope(scopeRes.reason)
            }
            is ScopeDecision.Permitted -> Unit
        }

        // 5. Launch Harmless Open/Prepare Intent (final send/save stays manual in target app!)
        val launched = launcher.launchPreparedIntent(spec)
        return if (launched) {
            val detail = "Opened '${spec.targetPackage}' via ${spec.requiredActionType.label}; final completion stays with owner in target app."
            timeline.recordEvent(
                stage = TimelineStage.ACTION,
                actor = TimelineActor.LOCAL_ROUTINE,
                summary = detail,
                outcome = TimelineOutcomeState.MANUAL_HANDOFF,
                jobId = jobId,
                targetReference = spec.targetPackage,
                nowEpochMs = nowEpochMs,
            )
            IntentDispatchResult.HandedOffToApp(spec.targetPackage, detail)
        } else {
            timeline.recordEvent(
                stage = TimelineStage.ACTION,
                actor = TimelineActor.LOCAL_ROUTINE,
                summary = "No installed handler accepted ${spec.requiredActionType.label} for '${spec.targetPackage}'.",
                outcome = TimelineOutcomeState.FAILED,
                jobId = jobId,
                targetReference = spec.targetPackage,
                nowEpochMs = nowEpochMs,
            )
            IntentDispatchResult.NoHandlerAvailable(spec.targetPackage)
        }
    }
}
