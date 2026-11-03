package io.github.mohamtur1.fourcbon2

import java.util.UUID

/**
 * Divisions 12 & 13:
 * - Division 12: Selected-app capability registry (narrow discovery, owner-selected identities,
 *   action-level coverage states, all apps Off by default, no blanket inventory collection).
 * - Division 13: On-device scope contract (app/account/data/action/expiry/action-count limits,
 *   denies unselected targets and unauthorized cloud transfers).
 */
enum class AppCoverageState(val label: String) {
    API_ACTIONS_SUPPORTED("API Actions Supported"),
    OPEN_PREPARE_SHARE_SUPPORTED("Open / Prepare / Share Supported"),
    FIXED_ROUTINE_CONDITIONALLY_SUPPORTED("Fixed Routine Conditionally Supported"),
    EXPERIMENTAL_UI_TEST("Experimental UI Test (Lab Only)"),
    MANUAL_HANDOFF_REQUIRED("Manual Handoff Required"),
    UNAVAILABLE_OR_BLOCKED("Unavailable / Blocked"),
}

enum class ScopedActionType(val label: String) {
    OPEN_HTTPS_URL("Open HTTPS Link"),
    LAUNCH_CUSTOM_TAB("Launch Chrome Custom Tab"),
    SHARE_TEXT_DRAFT("Prepare Share Draft"),
    PREPARE_CALENDAR_EVENT("Prepare Calendar Draft"),
    READ_GITHUB_METADATA("Read GitHub Metadata/Diff"),
    READ_KAGGLE_STATUS("Read Kaggle Status"),
    RUN_FIXED_ROUTINE("Run Human-Defined Routine"),
    EXPERIMENTAL_FIXTURE_UI("Experimental Fixture UI Step"),
}

data class SelectedAppEntry(
    val packageName: String,
    val displayName: String,
    val ownerSelected: Boolean,
    val coverageState: AppCoverageState,
    val supportedActions: Set<ScopedActionType>,
    val boundAccountHint: String?,
    val adapterVersion: String,
    val protectedManualOnly: Boolean,
    val updatedAtEpochMs: Long,
) {
    init {
        require(PACKAGE_REGEX.matches(packageName)) {
            "Invalid Android package identifier: $packageName"
        }
        require(displayName.isNotBlank()) { "App display name must not be blank." }
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "packageName" to JsonValue.Str(packageName),
            "displayName" to JsonValue.Str(displayName),
            "ownerSelected" to JsonValue.Bool(ownerSelected),
            "coverageState" to JsonValue.Str(coverageState.name),
            "supportedActions" to JsonValue.Arr(supportedActions.map { JsonValue.Str(it.name) }),
            "boundAccountHint" to (boundAccountHint?.let(JsonValue::Str) ?: JsonValue.Null),
            "adapterVersion" to JsonValue.Str(adapterVersion),
            "protectedManualOnly" to JsonValue.Bool(protectedManualOnly),
            "updatedAtEpochMs" to JsonValue.Num(updatedAtEpochMs.toDouble(), updatedAtEpochMs.toString()),
        ),
    )

    companion object {
        private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

        fun fromJson(obj: JsonValue.Obj): SelectedAppEntry = SelectedAppEntry(
            packageName = obj.stringOrDefault("packageName", "com.example.unknown"),
            displayName = obj.stringOrDefault("displayName", "Unknown App"),
            ownerSelected = obj.bool("ownerSelected", false),
            coverageState = runCatching { AppCoverageState.valueOf(obj.stringOrDefault("coverageState")) }
                .getOrDefault(AppCoverageState.UNAVAILABLE_OR_BLOCKED),
            supportedActions = obj.stringList("supportedActions").mapNotNull {
                runCatching { ScopedActionType.valueOf(it) }.getOrNull()
            }.toCollection(linkedSetOf()),
            boundAccountHint = obj.string("boundAccountHint"),
            adapterVersion = obj.stringOrDefault("adapterVersion", "1.0"),
            protectedManualOnly = obj.bool("protectedManualOnly", false),
            updatedAtEpochMs = obj.long("updatedAtEpochMs", 0L),
        )
    }
}

class SelectedAppCapabilityRegistry(private val database: FourCBOn2Database) {
    /**
     * Seeds curated known targets with `ownerSelected = false` (all apps start Off for control).
     * Never scans or collects installed apps on the phone.
     */
    fun ensureDefaultCatalog(nowEpochMs: Long = System.currentTimeMillis()) {
        for (preset in DEFAULT_CATALOG) {
            if (getApp(preset.packageName) == null) {
                saveApp(preset.copy(updatedAtEpochMs = nowEpochMs))
            }
        }
    }

    fun registerOrUpdateApp(
        packageName: String,
        displayName: String,
        coverageState: AppCoverageState,
        supportedActions: Set<ScopedActionType>,
        ownerSelected: Boolean = false,
        boundAccountHint: String? = null,
        protectedManualOnly: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SelectedAppEntry {
        val effectiveSelected = if (protectedManualOnly || coverageState == AppCoverageState.UNAVAILABLE_OR_BLOCKED) {
            false
        } else {
            ownerSelected
        }
        val effectiveActions = if (protectedManualOnly) emptySet() else supportedActions
        val entry = SelectedAppEntry(
            packageName = packageName.trim(),
            displayName = displayName.trim(),
            ownerSelected = effectiveSelected,
            coverageState = if (protectedManualOnly) AppCoverageState.MANUAL_HANDOFF_REQUIRED else coverageState,
            supportedActions = effectiveActions,
            boundAccountHint = boundAccountHint?.trim()?.takeIf { it.isNotEmpty() },
            adapterVersion = "1.0",
            protectedManualOnly = protectedManualOnly,
            updatedAtEpochMs = nowEpochMs,
        )
        return saveApp(entry)
    }

    fun setOwnerSelected(
        packageName: String,
        selected: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SelectedAppEntry? {
        val existing = getApp(packageName) ?: return null
        if (existing.protectedManualOnly || existing.coverageState == AppCoverageState.UNAVAILABLE_OR_BLOCKED) {
            return saveApp(existing.copy(ownerSelected = false, updatedAtEpochMs = nowEpochMs))
        }
        return saveApp(existing.copy(ownerSelected = selected, updatedAtEpochMs = nowEpochMs))
    }

    fun getApp(packageName: String): SelectedAppEntry? {
        val row = database.backend.get(NS_CAPABILITY_REGISTRY, packageName) ?: return null
        return runCatching { SelectedAppEntry.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listApps(): List<SelectedAppEntry> =
        database.backend.listByNamespace(NS_CAPABILITY_REGISTRY)
            .mapNotNull { row ->
                runCatching { SelectedAppEntry.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    fun supportsAction(packageName: String, action: ScopedActionType): Boolean {
        val entry = getApp(packageName) ?: return false
        return entry.ownerSelected &&
            !entry.protectedManualOnly &&
            entry.coverageState != AppCoverageState.UNAVAILABLE_OR_BLOCKED &&
            action in entry.supportedActions
    }

    private fun saveApp(entry: SelectedAppEntry): SelectedAppEntry {
        database.backend.upsert(
            namespace = NS_CAPABILITY_REGISTRY,
            id = entry.packageName,
            payloadJson = JsonCodec.stringify(entry.toJson()),
            updatedAtEpochMs = entry.updatedAtEpochMs,
        )
        return entry
    }

    companion object {
        const val NS_CAPABILITY_REGISTRY = "selected_app_registry"

        private val DEFAULT_CATALOG: List<SelectedAppEntry> = listOf(
            SelectedAppEntry(
                packageName = "com.android.chrome",
                displayName = "Google Chrome (Custom Tabs)",
                ownerSelected = false,
                coverageState = AppCoverageState.OPEN_PREPARE_SHARE_SUPPORTED,
                supportedActions = setOf(ScopedActionType.OPEN_HTTPS_URL, ScopedActionType.LAUNCH_CUSTOM_TAB),
                boundAccountHint = null,
                adapterVersion = "1.0",
                protectedManualOnly = false,
                updatedAtEpochMs = 0L,
            ),
            SelectedAppEntry(
                packageName = "com.google.android.gm",
                displayName = "Gmail (Compose Draft Only)",
                ownerSelected = false,
                coverageState = AppCoverageState.OPEN_PREPARE_SHARE_SUPPORTED,
                supportedActions = setOf(ScopedActionType.SHARE_TEXT_DRAFT),
                boundAccountHint = null,
                adapterVersion = "1.0",
                protectedManualOnly = false,
                updatedAtEpochMs = 0L,
            ),
            SelectedAppEntry(
                packageName = "com.google.android.calendar",
                displayName = "Google Calendar (Prepare Event)",
                ownerSelected = false,
                coverageState = AppCoverageState.OPEN_PREPARE_SHARE_SUPPORTED,
                supportedActions = setOf(ScopedActionType.PREPARE_CALENDAR_EVENT),
                boundAccountHint = null,
                adapterVersion = "1.0",
                protectedManualOnly = false,
                updatedAtEpochMs = 0L,
            ),
            SelectedAppEntry(
                packageName = "com.android.settings",
                displayName = "Android System Settings (Protected)",
                ownerSelected = false,
                coverageState = AppCoverageState.MANUAL_HANDOFF_REQUIRED,
                supportedActions = emptySet(),
                boundAccountHint = null,
                adapterVersion = "1.0",
                protectedManualOnly = true,
                updatedAtEpochMs = 0L,
            ),
        )
    }
}

data class TaskScopeContract(
    val id: String,
    val taskTitle: String,
    val allowedPackages: Set<String>,
    val allowedAccounts: Set<String>,
    val allowedActions: Set<ScopedActionType>,
    val allowedDataRefs: Set<String>,
    val allowCloudTransfer: Boolean,
    val stopAtDraftOrConfirmation: Boolean,
    val maxActionCount: Int,
    val actionsUsed: Int,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
) {
    init {
        require(taskTitle.isNotBlank()) { "Scope contract title must not be blank." }
        require(maxActionCount in 1..200) { "maxActionCount must be 1..200." }
        require(expiresAtEpochMs > createdAtEpochMs) { "Scope contract expiry must be after creation." }
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "id" to JsonValue.Str(id),
            "taskTitle" to JsonValue.Str(taskTitle),
            "allowedPackages" to JsonValue.Arr(allowedPackages.map(JsonValue::Str)),
            "allowedAccounts" to JsonValue.Arr(allowedAccounts.map(JsonValue::Str)),
            "allowedActions" to JsonValue.Arr(allowedActions.map { JsonValue.Str(it.name) }),
            "allowedDataRefs" to JsonValue.Arr(allowedDataRefs.map(JsonValue::Str)),
            "allowCloudTransfer" to JsonValue.Bool(allowCloudTransfer),
            "stopAtDraftOrConfirmation" to JsonValue.Bool(stopAtDraftOrConfirmation),
            "maxActionCount" to JsonValue.Num(maxActionCount.toDouble(), maxActionCount.toString()),
            "actionsUsed" to JsonValue.Num(actionsUsed.toDouble(), actionsUsed.toString()),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "expiresAtEpochMs" to JsonValue.Num(expiresAtEpochMs.toDouble(), expiresAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): TaskScopeContract = TaskScopeContract(
            id = obj.stringOrDefault("id", "scope_${UUID.randomUUID().toString().take(8)}"),
            taskTitle = obj.stringOrDefault("taskTitle", "Scoped Task"),
            allowedPackages = obj.stringList("allowedPackages").toCollection(linkedSetOf()),
            allowedAccounts = obj.stringList("allowedAccounts").toCollection(linkedSetOf()),
            allowedActions = obj.stringList("allowedActions").mapNotNull {
                runCatching { ScopedActionType.valueOf(it) }.getOrNull()
            }.toCollection(linkedSetOf()),
            allowedDataRefs = obj.stringList("allowedDataRefs").toCollection(linkedSetOf()),
            allowCloudTransfer = obj.bool("allowCloudTransfer", false),
            stopAtDraftOrConfirmation = obj.bool("stopAtDraftOrConfirmation", true),
            maxActionCount = obj.int("maxActionCount", 5).coerceIn(1, 200),
            actionsUsed = obj.int("actionsUsed", 0).coerceAtLeast(0),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            expiresAtEpochMs = obj.long("expiresAtEpochMs", 1L),
        )
    }
}

data class ProposedScopedAction(
    val contractId: String,
    val targetPackage: String?,
    val accountId: String? = null,
    val actionType: ScopedActionType,
    val dataRef: String? = null,
    val transfersDataToCloud: Boolean = false,
)

sealed interface ScopeDecision {
    data class Permitted(val updatedContract: TaskScopeContract) : ScopeDecision
    data class Denied(val reason: String) : ScopeDecision
}

class OnDeviceScopeBroker(
    private val database: FourCBOn2Database,
    private val capabilityRegistry: SelectedAppCapabilityRegistry,
) {
    fun createContract(
        taskTitle: String,
        allowedPackages: Set<String>,
        allowedActions: Set<ScopedActionType>,
        allowedAccounts: Set<String> = emptySet(),
        allowedDataRefs: Set<String> = emptySet(),
        allowCloudTransfer: Boolean = false,
        stopAtDraftOrConfirmation: Boolean = true,
        maxActionCount: Int = 10,
        ttlMillis: Long = 30L * 60L * 1000L,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TaskScopeContract {
        val contract = TaskScopeContract(
            id = "scope_${UUID.randomUUID().toString().take(8)}",
            taskTitle = taskTitle.trim(),
            allowedPackages = allowedPackages,
            allowedAccounts = allowedAccounts,
            allowedActions = allowedActions,
            allowedDataRefs = allowedDataRefs,
            allowCloudTransfer = allowCloudTransfer,
            stopAtDraftOrConfirmation = stopAtDraftOrConfirmation,
            maxActionCount = maxActionCount,
            actionsUsed = 0,
            createdAtEpochMs = nowEpochMs,
            expiresAtEpochMs = nowEpochMs + ttlMillis.coerceAtLeast(1_000L),
        )
        return persist(contract, nowEpochMs)
    }

    fun getContract(id: String): TaskScopeContract? {
        val row = database.backend.get(NS_SCOPE_CONTRACTS, id) ?: return null
        return runCatching { TaskScopeContract.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listContracts(): List<TaskScopeContract> =
        database.backend.listByNamespace(NS_SCOPE_CONTRACTS)
            .mapNotNull { row ->
                runCatching { TaskScopeContract.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    /**
     * Validates a proposed action locally against expiry, action caps, cloud-transfer consent,
     * data-reference boundaries, account bindings, and the Selected-App Capability Registry.
     */
    fun authorizeAndConsume(
        proposal: ProposedScopedAction,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ScopeDecision {
        val contract = getContract(proposal.contractId)
            ?: return ScopeDecision.Denied("Scope contract '${proposal.contractId}' does not exist.")

        if (nowEpochMs > contract.expiresAtEpochMs) {
            return ScopeDecision.Denied("Scope contract has expired.")
        }
        if (contract.actionsUsed >= contract.maxActionCount) {
            return ScopeDecision.Denied("Scope action count cap (${contract.maxActionCount}) reached.")
        }
        if (proposal.actionType !in contract.allowedActions) {
            return ScopeDecision.Denied("Action '${proposal.actionType.name}' is outside task scope.")
        }
        if (proposal.transfersDataToCloud && !contract.allowCloudTransfer) {
            return ScopeDecision.Denied("Unauthorized cloud data transfer denied by on-device scope.")
        }
        if (proposal.accountId != null &&
            contract.allowedAccounts.isNotEmpty() &&
            proposal.accountId !in contract.allowedAccounts
        ) {
            return ScopeDecision.Denied("Account '${proposal.accountId}' is not permitted by task scope.")
        }
        if (proposal.dataRef != null && proposal.dataRef !in contract.allowedDataRefs) {
            return ScopeDecision.Denied("Data reference '${proposal.dataRef}' was not authorized for this task.")
        }

        val pkg = proposal.targetPackage
        if (pkg != null) {
            if (pkg !in contract.allowedPackages) {
                return ScopeDecision.Denied("Target app '$pkg' is not selected in this task scope.")
            }
            if (!capabilityRegistry.supportsAction(pkg, proposal.actionType)) {
                return ScopeDecision.Denied(
                    "Target app '$pkg' is either unselected by owner, manual-only, or does not support ${proposal.actionType.name}.",
                )
            }
        }

        val updated = persist(
            contract.copy(actionsUsed = contract.actionsUsed + 1),
            nowEpochMs = nowEpochMs,
        )
        return ScopeDecision.Permitted(updated)
    }

    private fun persist(contract: TaskScopeContract, nowEpochMs: Long): TaskScopeContract {
        database.backend.upsert(
            namespace = NS_SCOPE_CONTRACTS,
            id = contract.id,
            payloadJson = JsonCodec.stringify(contract.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        return contract
    }

    companion object {
        const val NS_SCOPE_CONTRACTS = "scope_contracts"
    }
}
