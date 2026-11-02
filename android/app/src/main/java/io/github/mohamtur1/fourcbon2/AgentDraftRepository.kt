package io.github.mohamtur1.fourcbon2

import java.util.UUID

/**
 * Division 15: Offline Agent Builder drafts — save, edit, load, instantiate templates,
 * enforce execution caps, and track draft lifecycle state offline.
 */
enum class JobProfile(val label: String, val summary: String) {
    COMPETITION_SOLVER(
        label = "Competition Solver",
        summary = "Tabular validation, baseline preparation, and gated submission checks.",
    ),
    RESEARCH_SYNTHESIS(
        label = "Research Synthesis",
        summary = "Evidence gathering, citation checks, and offline report drafting.",
    ),
    CODE_COMPANION(
        label = "Code & Notebook Companion",
        summary = "Repository diff inspection, patch preparation, and notebook validation.",
    ),
    PHONE_TASK_ASSISTANT(
        label = "Scoped Phone Assistant",
        summary = "Selected-app intent handoffs and deterministic owner-triggered routines.",
    ),
}

enum class AgentDraftState(val label: String) {
    DRAFT("Draft"),
    READY_TO_QUEUE("Ready to Queue"),
    ARCHIVED("Archived"),
}

data class ExecutionCaps(
    val maxSteps: Int = 10,
    val maxRuntimeMinutes: Int = 15,
    val maxCostUsd: Double = 0.0,
    val allowNetwork: Boolean = false,
    val allowCloudAi: Boolean = false,
) {
    init {
        require(maxSteps in 1..200) { "maxSteps must be between 1 and 200." }
        require(maxRuntimeMinutes in 1..240) { "maxRuntimeMinutes must be between 1 and 240." }
        require(maxCostUsd >= 0.0 && maxCostUsd.isFinite()) { "maxCostUsd must be non-negative." }
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "maxSteps" to JsonValue.Num(maxSteps.toDouble(), maxSteps.toString()),
            "maxRuntimeMinutes" to JsonValue.Num(maxRuntimeMinutes.toDouble(), maxRuntimeMinutes.toString()),
            "maxCostUsd" to JsonValue.Num(maxCostUsd),
            "allowNetwork" to JsonValue.Bool(allowNetwork),
            "allowCloudAi" to JsonValue.Bool(allowCloudAi),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj?): ExecutionCaps {
            if (obj == null) return ExecutionCaps()
            return ExecutionCaps(
                maxSteps = obj.int("maxSteps", 10).coerceIn(1, 200),
                maxRuntimeMinutes = obj.int("maxRuntimeMinutes", 15).coerceIn(1, 240),
                maxCostUsd = obj.double("maxCostUsd", 0.0).coerceAtLeast(0.0),
                allowNetwork = obj.bool("allowNetwork", false),
                allowCloudAi = obj.bool("allowCloudAi", false),
            )
        }
    }
}

data class AgentTemplate(
    val id: String,
    val title: String,
    val defaultGoal: String,
    val profile: JobProfile,
    val defaultCaps: ExecutionCaps,
    val suggestedAllowedApps: List<String>,
)

data class AgentDraft(
    val id: String,
    val title: String,
    val goal: String,
    val templateId: String?,
    val profile: JobProfile,
    val caps: ExecutionCaps,
    val allowedApps: List<String>,
    val state: AgentDraftState,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(title.isNotBlank()) { "Agent draft title must not be blank." }
        require(goal.isNotBlank()) { "Agent draft goal must not be blank." }
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "id" to JsonValue.Str(id),
            "title" to JsonValue.Str(title),
            "goal" to JsonValue.Str(goal),
            "templateId" to (templateId?.let(JsonValue::Str) ?: JsonValue.Null),
            "profile" to JsonValue.Str(profile.name),
            "caps" to caps.toJson(),
            "allowedApps" to JsonValue.Arr(allowedApps.map(JsonValue::Str)),
            "state" to JsonValue.Str(state.name),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "updatedAtEpochMs" to JsonValue.Num(updatedAtEpochMs.toDouble(), updatedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): AgentDraft = AgentDraft(
            id = obj.stringOrDefault("id", UUID.randomUUID().toString()),
            title = obj.stringOrDefault("title", "Untitled Draft"),
            goal = obj.stringOrDefault("goal", "Unspecified goal"),
            templateId = obj.string("templateId"),
            profile = runCatching { JobProfile.valueOf(obj.stringOrDefault("profile")) }
                .getOrDefault(JobProfile.RESEARCH_SYNTHESIS),
            caps = ExecutionCaps.fromJson(obj.obj("caps")),
            allowedApps = obj.stringList("allowedApps"),
            state = runCatching { AgentDraftState.valueOf(obj.stringOrDefault("state")) }
                .getOrDefault(AgentDraftState.DRAFT),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            updatedAtEpochMs = obj.long("updatedAtEpochMs", 0L),
        )
    }
}

object BuiltInAgentTemplates {
    val templates: List<AgentTemplate> = listOf(
        AgentTemplate(
            id = "tpl_tabular_kaggle",
            title = "Tabular Kaggle Baseline",
            defaultGoal = "Validate local CSV schema, run stratified baseline checks, and prepare an artifact manifest without submitting.",
            profile = JobProfile.COMPETITION_SOLVER,
            defaultCaps = ExecutionCaps(maxSteps = 12, maxRuntimeMinutes = 20, maxCostUsd = 0.0, allowNetwork = true, allowCloudAi = true),
            suggestedAllowedApps = emptyList(),
        ),
        AgentTemplate(
            id = "tpl_offline_evidence_brief",
            title = "Offline Evidence Synthesis",
            defaultGoal = "Search saved library notes and imported CSV/JSON reports, then assemble a cited offline summary.",
            profile = JobProfile.RESEARCH_SYNTHESIS,
            defaultCaps = ExecutionCaps(maxSteps = 8, maxRuntimeMinutes = 10, maxCostUsd = 0.0, allowNetwork = false, allowCloudAi = false),
            suggestedAllowedApps = emptyList(),
        ),
        AgentTemplate(
            id = "tpl_repo_diff_review",
            title = "GitHub Diff & Notebook Check",
            defaultGoal = "Inspect cached mohamtur1/4CBOn2 metadata, review local changes, and prepare an exportable patch.",
            profile = JobProfile.CODE_COMPANION,
            defaultCaps = ExecutionCaps(maxSteps = 10, maxRuntimeMinutes = 15, maxCostUsd = 0.0, allowNetwork = true, allowCloudAi = false),
            suggestedAllowedApps = emptyList(),
        ),
        AgentTemplate(
            id = "tpl_scoped_app_handoff",
            title = "Scoped App Share / Open Routine",
            defaultGoal = "Prepare a local summary artifact and open a user-selected target app via Android Intent for manual review.",
            profile = JobProfile.PHONE_TASK_ASSISTANT,
            defaultCaps = ExecutionCaps(maxSteps = 5, maxRuntimeMinutes = 5, maxCostUsd = 0.0, allowNetwork = false, allowCloudAi = false),
            suggestedAllowedApps = listOf("com.android.chrome"),
        ),
    )

    fun findById(id: String): AgentTemplate? = templates.firstOrNull { it.id == id }
}

class AgentDraftRepository(private val database: FourCBOn2Database) {
    fun instantiateFromTemplate(
        template: AgentTemplate,
        customGoal: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): AgentDraft {
        val draft = AgentDraft(
            id = "draft_${UUID.randomUUID().toString().take(8)}",
            title = template.title,
            goal = customGoal?.takeIf { it.isNotBlank() } ?: template.defaultGoal,
            templateId = template.id,
            profile = template.profile,
            caps = template.defaultCaps,
            allowedApps = template.suggestedAllowedApps,
            state = AgentDraftState.DRAFT,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
        return save(draft, nowEpochMs)
    }

    fun createDraft(
        title: String,
        goal: String,
        profile: JobProfile,
        caps: ExecutionCaps = ExecutionCaps(),
        allowedApps: List<String> = emptyList(),
        state: AgentDraftState = AgentDraftState.DRAFT,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): AgentDraft {
        val draft = AgentDraft(
            id = "draft_${UUID.randomUUID().toString().take(8)}",
            title = title.trim(),
            goal = goal.trim(),
            templateId = null,
            profile = profile,
            caps = caps,
            allowedApps = allowedApps,
            state = state,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
        return save(draft, nowEpochMs)
    }

    fun save(draft: AgentDraft, nowEpochMs: Long = draft.updatedAtEpochMs): AgentDraft {
        val updated = draft.copy(
            title = draft.title.trim(),
            goal = draft.goal.trim(),
            updatedAtEpochMs = nowEpochMs,
        )
        database.backend.upsert(
            namespace = FourCBOn2Database.NS_AGENT_DRAFTS,
            id = updated.id,
            payloadJson = JsonCodec.stringify(updated.toJson()),
            updatedAtEpochMs = updated.updatedAtEpochMs,
        )
        return updated
    }

    fun load(id: String): AgentDraft? {
        val row = database.backend.get(FourCBOn2Database.NS_AGENT_DRAFTS, id) ?: return null
        return runCatching { AgentDraft.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listAll(): List<AgentDraft> =
        database.backend.listByNamespace(FourCBOn2Database.NS_AGENT_DRAFTS)
            .mapNotNull { row ->
                runCatching { AgentDraft.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    fun delete(id: String): Boolean =
        database.backend.delete(FourCBOn2Database.NS_AGENT_DRAFTS, id)
}
