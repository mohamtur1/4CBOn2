package io.github.mohamtur1.fourcbon2

import java.util.UUID

/**
 * Divisions 14, 20, 26, and 32:
 * - Division 14: Takeover and cancellation lease (local Pause / Take Over / Stop, monotonic epoch,
 *   stale-command rejection, explicit owner resume required).
 * - Division 20: Sensitive-action approval gate (destination + artifact SHA-256 binding, expiry,
 *   and bounded use count).
 * - Division 26: Consented observation boundary (structured/shared data first, screen capture
 *   separately gated, sensitive contexts excluded, cloud upload Off by default, secret redaction).
 * - Division 32: Critical phone-action guard (manual-only protected screens, exact action preview,
 *   account verification, and zero blind irreversible retries).
 */
enum class ControlLeaseMode(val label: String) {
    IDLE("Idle"),
    AUTOMATION_ACTIVE("Automation Active"),
    PAUSED_BY_OWNER("Paused by Owner"),
    OWNER_TAKEN_OVER("Owner Taken Over"),
    STOPPED("Stopped"),
}

data class ControlLeaseSnapshot(
    val activeSessionId: String?,
    val leaseEpoch: Long,
    val mode: ControlLeaseMode,
    val statusNote: String,
    val updatedAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "activeSessionId" to (activeSessionId?.let(JsonValue::Str) ?: JsonValue.Null),
            "leaseEpoch" to JsonValue.Num(leaseEpoch.toDouble(), leaseEpoch.toString()),
            "mode" to JsonValue.Str(mode.name),
            "statusNote" to JsonValue.Str(statusNote),
            "updatedAtEpochMs" to JsonValue.Num(updatedAtEpochMs.toDouble(), updatedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): ControlLeaseSnapshot = ControlLeaseSnapshot(
            activeSessionId = obj.string("activeSessionId"),
            leaseEpoch = obj.long("leaseEpoch", 1L),
            mode = runCatching { ControlLeaseMode.valueOf(obj.stringOrDefault("mode")) }
                .getOrDefault(ControlLeaseMode.IDLE),
            statusNote = obj.stringOrDefault("statusNote", "Idle"),
            updatedAtEpochMs = obj.long("updatedAtEpochMs", 0L),
        )
    }
}

sealed interface LeaseValidationOutcome {
    data class Valid(val snapshot: ControlLeaseSnapshot) : LeaseValidationOutcome
    data class RejectedStaleOrCancelled(val reason: String) : LeaseValidationOutcome
}

class TakeoverLeaseController(private val database: FourCBOn2Database) {
    private val lock = Any()

    fun currentSnapshot(): ControlLeaseSnapshot = synchronized(lock) {
        val row = database.backend.get(NS_CONTROL_LEASE, LEASE_SINGLETON_ID)
            ?: return ControlLeaseSnapshot(
                activeSessionId = null,
                leaseEpoch = 1L,
                mode = ControlLeaseMode.IDLE,
                statusNote = "Ready; no automated session running.",
                updatedAtEpochMs = 0L,
            )
        runCatching { ControlLeaseSnapshot.fromJson(JsonCodec.parseObject(row.payloadJson)) }
            .getOrElse {
                ControlLeaseSnapshot(null, 1L, ControlLeaseMode.STOPPED, "Recovered after state error.", 0L)
            }
    }

    /**
     * Starts or explicitly resumes automation under a fresh monotonic [ControlLeaseSnapshot.leaseEpoch].
     * If currently in [ControlLeaseMode.OWNER_TAKEN_OVER], [ControlLeaseMode.PAUSED_BY_OWNER], or
     * [ControlLeaseMode.STOPPED], only an explicit owner call (`explicitOwnerResume = true`) may start!
     */
    fun startOrResumeAutomation(
        sessionId: String = "sess_${UUID.randomUUID().toString().take(8)}",
        explicitOwnerResume: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ControlLeaseSnapshot = synchronized(lock) {
        val cur = currentSnapshot()
        if (cur.mode in setOf(
                ControlLeaseMode.OWNER_TAKEN_OVER,
                ControlLeaseMode.PAUSED_BY_OWNER,
                ControlLeaseMode.STOPPED,
            ) && !explicitOwnerResume
        ) {
            throw IllegalStateException("Cannot resume automation after ${cur.mode.label} without explicit owner action.")
        }
        val next = ControlLeaseSnapshot(
            activeSessionId = sessionId,
            leaseEpoch = cur.leaseEpoch + 1L,
            mode = ControlLeaseMode.AUTOMATION_ACTIVE,
            statusNote = "Active session $sessionId (epoch ${cur.leaseEpoch + 1L}).",
            updatedAtEpochMs = nowEpochMs,
        )
        persistLocked(next)
    }

    fun pauseByOwner(
        reason: String = "Paused by owner.",
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ControlLeaseSnapshot = synchronized(lock) {
        val cur = currentSnapshot()
        persistLocked(
            ControlLeaseSnapshot(
                activeSessionId = cur.activeSessionId,
                leaseEpoch = cur.leaseEpoch + 1L,
                mode = ControlLeaseMode.PAUSED_BY_OWNER,
                statusNote = reason,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    fun takeOver(
        reason: String = "Owner took control; automated commands halted.",
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ControlLeaseSnapshot = synchronized(lock) {
        val cur = currentSnapshot()
        persistLocked(
            ControlLeaseSnapshot(
                activeSessionId = null,
                leaseEpoch = cur.leaseEpoch + 1L,
                mode = ControlLeaseMode.OWNER_TAKEN_OVER,
                statusNote = reason,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    fun stopAll(
        reason: String = "Emergency/owner Stop triggered.",
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ControlLeaseSnapshot = synchronized(lock) {
        val cur = currentSnapshot()
        persistLocked(
            ControlLeaseSnapshot(
                activeSessionId = null,
                leaseEpoch = cur.leaseEpoch + 1L,
                mode = ControlLeaseMode.STOPPED,
                statusNote = reason,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    /**
     * Validates an incoming automated/cloud step against the sole local lease.
     * Immediately rejects any command whose epoch or session does not match an active lease.
     */
    fun validateCommand(sessionId: String, commandEpoch: Long): LeaseValidationOutcome = synchronized(lock) {
        val cur = currentSnapshot()
        if (cur.mode != ControlLeaseMode.AUTOMATION_ACTIVE) {
            return LeaseValidationOutcome.RejectedStaleOrCancelled(
                "Control lease is '${cur.mode.label}'; automated commands are rejected until explicit owner resume.",
            )
        }
        if (cur.activeSessionId != sessionId) {
            return LeaseValidationOutcome.RejectedStaleOrCancelled(
                "Session mismatch (expected ${cur.activeSessionId}, got $sessionId).",
            )
        }
        if (cur.leaseEpoch != commandEpoch) {
            return LeaseValidationOutcome.RejectedStaleOrCancelled(
                "Stale command epoch ($commandEpoch != current ${cur.leaseEpoch}).",
            )
        }
        LeaseValidationOutcome.Valid(cur)
    }

    private fun persistLocked(snapshot: ControlLeaseSnapshot): ControlLeaseSnapshot {
        database.backend.upsert(
            namespace = NS_CONTROL_LEASE,
            id = LEASE_SINGLETON_ID,
            payloadJson = JsonCodec.stringify(snapshot.toJson()),
            updatedAtEpochMs = snapshot.updatedAtEpochMs,
        )
        return snapshot
    }

    companion object {
        const val NS_CONTROL_LEASE = "control_lease"
        private const val LEASE_SINGLETON_ID = "active_lease"
    }
}

// --- Division 20: Sensitive-Action Approval Gate ---

data class SensitiveActionGrant(
    val id: String,
    val actionType: String,
    val destinationIdentity: String,
    val artifactSha256: String,
    val maxUses: Int,
    val usesConsumed: Int,
    val approvedByOwner: Boolean,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "id" to JsonValue.Str(id),
            "actionType" to JsonValue.Str(actionType),
            "destinationIdentity" to JsonValue.Str(destinationIdentity),
            "artifactSha256" to JsonValue.Str(artifactSha256),
            "maxUses" to JsonValue.Num(maxUses.toDouble(), maxUses.toString()),
            "usesConsumed" to JsonValue.Num(usesConsumed.toDouble(), usesConsumed.toString()),
            "approvedByOwner" to JsonValue.Bool(approvedByOwner),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "expiresAtEpochMs" to JsonValue.Num(expiresAtEpochMs.toDouble(), expiresAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): SensitiveActionGrant = SensitiveActionGrant(
            id = obj.stringOrDefault("id"),
            actionType = obj.stringOrDefault("actionType"),
            destinationIdentity = obj.stringOrDefault("destinationIdentity"),
            artifactSha256 = obj.stringOrDefault("artifactSha256"),
            maxUses = obj.int("maxUses", 1).coerceAtLeast(1),
            usesConsumed = obj.int("usesConsumed", 0).coerceAtLeast(0),
            approvedByOwner = obj.bool("approvedByOwner", false),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            expiresAtEpochMs = obj.long("expiresAtEpochMs", 0L),
        )
    }
}

sealed interface ApprovalGateResult {
    data class ApprovedAndConsumed(val grant: SensitiveActionGrant) : ApprovalGateResult
    data class Denied(val reason: String) : ApprovalGateResult
}

class SensitiveActionApprovalGate(private val database: FourCBOn2Database) {
    fun requestApproval(
        actionType: String,
        destinationIdentity: String,
        artifactSha256: String,
        maxUses: Int = 1,
        ttlMillis: Long = 5L * 60L * 1000L,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SensitiveActionGrant {
        require(actionType.isNotBlank() && destinationIdentity.isNotBlank() && artifactSha256.isNotBlank()) {
            "Sensitive action type, destination identity, and artifact SHA-256 are required."
        }
        val grant = SensitiveActionGrant(
            id = "appr_${UUID.randomUUID().toString().take(8)}",
            actionType = actionType.trim(),
            destinationIdentity = destinationIdentity.trim(),
            artifactSha256 = artifactSha256.trim().lowercase(),
            maxUses = maxUses.coerceIn(1, 10),
            usesConsumed = 0,
            approvedByOwner = false,
            createdAtEpochMs = nowEpochMs,
            expiresAtEpochMs = nowEpochMs + ttlMillis.coerceAtLeast(1_000L),
        )
        return persist(grant, nowEpochMs)
    }

    fun confirmByOwner(grantId: String, nowEpochMs: Long = System.currentTimeMillis()): SensitiveActionGrant? {
        val grant = getGrant(grantId) ?: return null
        if (nowEpochMs > grant.expiresAtEpochMs) return grant
        return persist(grant.copy(approvedByOwner = true), nowEpochMs)
    }

    fun consumeApproval(
        grantId: String,
        expectedActionType: String,
        expectedDestination: String,
        expectedArtifactSha256: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ApprovalGateResult {
        val grant = getGrant(grantId)
            ?: return ApprovalGateResult.Denied("Approval grant '$grantId' not found.")
        if (!grant.approvedByOwner) {
            return ApprovalGateResult.Denied("Owner confirmation has not been granted.")
        }
        if (nowEpochMs > grant.expiresAtEpochMs) {
            return ApprovalGateResult.Denied("Approval grant has expired.")
        }
        if (grant.usesConsumed >= grant.maxUses) {
            return ApprovalGateResult.Denied("Approval grant cap (${grant.maxUses}) already exhausted.")
        }
        if (grant.actionType != expectedActionType.trim()) {
            return ApprovalGateResult.Denied("Action type mismatch against approved grant.")
        }
        if (grant.destinationIdentity != expectedDestination.trim()) {
            return ApprovalGateResult.Denied("Destination identity mismatch ('${grant.destinationIdentity}' != '$expectedDestination').")
        }
        if (grant.artifactSha256 != expectedArtifactSha256.trim().lowercase()) {
            return ApprovalGateResult.Denied("Artifact SHA-256 mismatch; approval invalidated.")
        }

        val updated = persist(grant.copy(usesConsumed = grant.usesConsumed + 1), nowEpochMs)
        return ApprovalGateResult.ApprovedAndConsumed(updated)
    }

    fun getGrant(id: String): SensitiveActionGrant? {
        val row = database.backend.get(NS_APPROVAL_GRANTS, id) ?: return null
        return runCatching { SensitiveActionGrant.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listGrants(): List<SensitiveActionGrant> =
        database.backend.listByNamespace(NS_APPROVAL_GRANTS)
            .mapNotNull { row ->
                runCatching { SensitiveActionGrant.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    private fun persist(grant: SensitiveActionGrant, nowEpochMs: Long): SensitiveActionGrant {
        database.backend.upsert(
            namespace = NS_APPROVAL_GRANTS,
            id = grant.id,
            payloadJson = JsonCodec.stringify(grant.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        return grant
    }

    companion object {
        const val NS_APPROVAL_GRANTS = "sensitive_approval_grants"
    }
}

// --- Division 26: Consented Observation Boundary ---

enum class ObservationChannel {
    STRUCTURED_API_OR_INTENT_RESULT,
    USER_SHARED_DOCUMENT,
    APPROVED_PUBLIC_PAGE_TEXT,
    OPT_IN_SCREEN_CAPTURE,
}

enum class ScreenSensitivity {
    NORMAL_APP_SURFACE,
    FLAG_SECURE_SURFACE,
    AUTHENTICATION_OR_OTP,
    BANKING_OR_PAYMENT,
    PERMISSION_OR_SECURITY_SETTINGS,
}

data class ObservationPolicySettings(
    val allowScreenCaptureOptIn: Boolean = false,
    val allowCloudObservationUpload: Boolean = false,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "allowScreenCaptureOptIn" to JsonValue.Bool(allowScreenCaptureOptIn),
            "allowCloudObservationUpload" to JsonValue.Bool(allowCloudObservationUpload),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj?): ObservationPolicySettings = ObservationPolicySettings(
            allowScreenCaptureOptIn = obj?.bool("allowScreenCaptureOptIn", false) ?: false,
            allowCloudObservationUpload = obj?.bool("allowCloudObservationUpload", false) ?: false,
        )
    }
}

sealed interface ObservationOutcome {
    data class Captured(
        val channel: ObservationChannel,
        val redactedContent: String,
        val eligibleForCloudUpload: Boolean,
    ) : ObservationOutcome

    data class Blocked(val reason: String) : ObservationOutcome
}

class ConsentedObservationBoundary(private val database: FourCBOn2Database) {
    fun currentPolicy(): ObservationPolicySettings {
        val row = database.backend.get(FourCBOn2Database.NS_SETTINGS, KEY_OBSERVATION_POLICY)
            ?: return ObservationPolicySettings()
        return runCatching { ObservationPolicySettings.fromJson(JsonCodec.parseObject(row.payloadJson)) }
            .getOrDefault(ObservationPolicySettings())
    }

    fun updatePolicy(
        settings: ObservationPolicySettings,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ObservationPolicySettings {
        database.backend.upsert(
            namespace = FourCBOn2Database.NS_SETTINGS,
            id = KEY_OBSERVATION_POLICY,
            payloadJson = JsonCodec.stringify(settings.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        return settings
    }

    fun observe(
        channel: ObservationChannel,
        rawText: String,
        sensitivity: ScreenSensitivity = ScreenSensitivity.NORMAL_APP_SURFACE,
        requestCloudUpload: Boolean = false,
    ): ObservationOutcome {
        if (sensitivity != ScreenSensitivity.NORMAL_APP_SURFACE) {
            return ObservationOutcome.Blocked(
                "Observation excluded on sensitive/protected context (${sensitivity.name}).",
            )
        }
        val policy = currentPolicy()
        if (channel == ObservationChannel.OPT_IN_SCREEN_CAPTURE && !policy.allowScreenCaptureOptIn) {
            return ObservationOutcome.Blocked(
                "Screen capture is disabled by default; structured/shared data must be used instead.",
            )
        }
        if (requestCloudUpload && !policy.allowCloudObservationUpload) {
            return ObservationOutcome.Blocked(
                "Cloud upload of observations is Off by default and has not been enabled by the owner.",
            )
        }
        return ObservationOutcome.Captured(
            channel = channel,
            redactedContent = redactSecrets(rawText),
            eligibleForCloudUpload = requestCloudUpload && policy.allowCloudObservationUpload,
        )
    }

    companion object {
        private const val KEY_OBSERVATION_POLICY = "observation_policy_v1"

        private val SECRET_PATTERNS = listOf(
            Regex("\\bgithub_pat_[A-Za-z0-9_]{12,}\\b"),
            Regex("\\bgh[pousr]_[A-Za-z0-9]{16,}\\b"),
            Regex("\\bKGAT_[A-Za-z0-9_\\-]{10,}\\b"),
            Regex("(?i)Bearer\\s+[A-Za-z0-9._\\-]{12,}"),
            Regex("(?i)Basic\\s+[A-Za-z0-9+/=]{12,}"),
            Regex("(?i)(\"key\"\\s*:\\s*\")([^\"]+)(\")"),
        )

        fun redactSecrets(input: String): String {
            var out = input
            for (idx in 0 until SECRET_PATTERNS.size - 1) {
                out = SECRET_PATTERNS[idx].replace(out, "[REDACTED_SECRET]")
            }
            out = SECRET_PATTERNS.last().replace(out) { m -> "${m.groupValues[1]}[REDACTED_KEY]${m.groupValues[3]}" }
            return out
        }
    }
}

// --- Division 32: Critical Phone-Action Guard ---

enum class ActionRiskContext {
    HARMLESS_LOCAL_OR_PREPARE,
    AUTHENTICATION_OR_OTP,
    BANKING_OR_PAYMENT,
    OS_PERMISSION_OR_SECURITY,
    LOCK_SCREEN_OR_BIOMETRIC,
    CONSEQUENTIAL_EXTERNAL_WRITE_OR_SUBMIT,
}

data class ActionPreview(
    val targetAppOrService: String,
    val expectedAccount: String?,
    val actionSummary: String,
    val payloadSha256: String,
)

data class CriticalActionRequest(
    val idempotencyKey: String,
    val riskContext: ActionRiskContext,
    val targetAppOrService: String,
    val activeAccount: String?,
    val actionSummary: String,
    val payloadSha256: String,
    val previewShownToOwner: ActionPreview?,
    val approvalGrantId: String? = null,
)

sealed interface CriticalGuardDecision {
    data object Permitted : CriticalGuardDecision
    data class ManualHandoffRequired(val reason: String) : CriticalGuardDecision
    data class Blocked(val reason: String) : CriticalGuardDecision
}

class CriticalPhoneActionGuard(
    private val database: FourCBOn2Database,
    private val approvalGate: SensitiveActionApprovalGate,
) {
    fun evaluate(
        request: CriticalActionRequest,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CriticalGuardDecision {
        // 1. Protected screens ALWAYS hand off manually (never automated)
        when (request.riskContext) {
            ActionRiskContext.AUTHENTICATION_OR_OTP,
            ActionRiskContext.BANKING_OR_PAYMENT,
            ActionRiskContext.OS_PERMISSION_OR_SECURITY,
            ActionRiskContext.LOCK_SCREEN_OR_BIOMETRIC,
            -> return CriticalGuardDecision.ManualHandoffRequired(
                "Protected context (${request.riskContext.name}) is manual-only; handing control back to owner.",
            )
            ActionRiskContext.HARMLESS_LOCAL_OR_PREPARE -> {
                val preview = request.previewShownToOwner
                if (preview != null && preview.expectedAccount != null && preview.expectedAccount != request.activeAccount) {
                    return CriticalGuardDecision.Blocked(
                        "Account mismatch: expected '${preview.expectedAccount}', active '${request.activeAccount}'.",
                    )
                }
                return CriticalGuardDecision.Permitted
            }
            ActionRiskContext.CONSEQUENTIAL_EXTERNAL_WRITE_OR_SUBMIT -> {
                // Prevent blind irreversible retry on already-attempted idempotency keys
                if (database.backend.get(NS_IRREVERSIBLE_LEDGER, request.idempotencyKey) != null) {
                    return CriticalGuardDecision.Blocked(
                        "Duplicate irreversible retry blocked for '${request.idempotencyKey}'; manual reconciliation required.",
                    )
                }

                val preview = request.previewShownToOwner
                    ?: return CriticalGuardDecision.Blocked("Consequential action requires an exact owner preview.")

                if (preview.targetAppOrService != request.targetAppOrService ||
                    preview.payloadSha256.lowercase() != request.payloadSha256.lowercase() ||
                    preview.actionSummary != request.actionSummary
                ) {
                    return CriticalGuardDecision.Blocked("Action parameters do not match the preview shown to owner.")
                }

                if (preview.expectedAccount != null && preview.expectedAccount != request.activeAccount) {
                    return CriticalGuardDecision.Blocked(
                        "Account check failed: preview bound to '${preview.expectedAccount}', got '${request.activeAccount}'.",
                    )
                }

                val grantId = request.approvalGrantId
                    ?: return CriticalGuardDecision.Blocked("Missing sensitive-action approval grant.")

                return when (
                    val gate = approvalGate.consumeApproval(
                        grantId = grantId,
                        expectedActionType = request.actionSummary,
                        expectedDestination = request.targetAppOrService,
                        expectedArtifactSha256 = request.payloadSha256,
                        nowEpochMs = nowEpochMs,
                    )
                ) {
                    is ApprovalGateResult.Denied -> CriticalGuardDecision.Blocked(gate.reason)
                    is ApprovalGateResult.ApprovedAndConsumed -> {
                        database.backend.upsert(
                            namespace = NS_IRREVERSIBLE_LEDGER,
                            id = request.idempotencyKey,
                            payloadJson = JsonCodec.stringify(
                                JsonValue.Obj(
                                    mapOf(
                                        "target" to JsonValue.Str(request.targetAppOrService),
                                        "payloadSha256" to JsonValue.Str(request.payloadSha256.lowercase()),
                                    ),
                                ),
                            ),
                            updatedAtEpochMs = nowEpochMs,
                        )
                        CriticalGuardDecision.Permitted
                    }
                }
            }
        }
    }

    /** Explicit owner reset after reconciling an uncertain external outcome. */
    fun clearIrreversibleIdempotencyLock(idempotencyKey: String): Boolean =
        database.backend.delete(NS_IRREVERSIBLE_LEDGER, idempotencyKey)

    companion object {
        const val NS_IRREVERSIBLE_LEDGER = "irreversible_action_ledger"
    }
}
