package io.github.mohamtur1.fourcbon2

private val APPROVAL_IDENTIFIER = Regex("[A-Za-z0-9._:-]{1,64}")

/**
 * The narrow set of sensitive acts that need their own owner decision.
 *
 * Deliberately small and deliberately closed. There is no `ANY`, no `OTHER`, and no
 * wildcard member, so an approval can only ever be issued for an act this build
 * understands. Adding a value is a conscious widening.
 */
enum class ApprovalActionKind {
    /** Sending a message or payload to one named recipient. */
    SEND_TO_RECIPIENT,

    /** Moving a file or content off the device to one named destination. */
    CLOUD_TRANSFER,

    /** Handing one named artifact to another app. */
    SHARE_FILE,
}

/** Hard caps for one approval. Nothing can widen them from outside. */
object ApprovalBounds {
    /**
     * Longest an approval may live.
     *
     * Matches [ScopeBounds.MAX_DURATION_MILLIS] on purpose: an approval must never outlive
     * the job it was issued inside.
     */
    const val MAX_DURATION_MILLIS = ScopeBounds.MAX_DURATION_MILLIS

    /** Most times one approval may be spent. */
    const val MAX_USES = 10

    /** Longest accepted destination or artifact identity. */
    const val MAX_TARGET_LENGTH = 200

    /** How old an owner confirmation may be and still be accepted at issue time. */
    const val MAX_CONFIRMATION_AGE_MILLIS = 60L * 1_000L

    /** Most approvals this gate will hold at once. */
    const val MAX_LIVE_APPROVALS = 50
}

/**
 * The owner's deliberate confirmation of one sensitive act.
 *
 * This is an input to the gate, not proof of anything on its own: the gate validates every
 * field and refuses anything incomplete, ambiguous, stale, or wider than an exact binding.
 * [explicitSingleTap] records that the owner performed one deliberate confirmation rather
 * than a standing grant, so a bulk "always allow" can never be constructed.
 */
data class ApprovalConfirmation(
    val scopeId: String,
    val actionKind: ApprovalActionKind,
    val targetIdentity: String,
    val confirmedAtEpochMillis: Long,
    val explicitSingleTap: Boolean,
) {
    init {
        require(APPROVAL_IDENTIFIER.matches(scopeId)) {
            "scopeId must be 1-64 characters using letters, digits, dot, underscore, colon, or hyphen."
        }
        require(targetIdentity.length <= ApprovalBounds.MAX_TARGET_LENGTH) {
            "targetIdentity must be at most ${ApprovalBounds.MAX_TARGET_LENGTH} characters."
        }
        require(targetIdentity.none { character -> character == '\n' || character == '\r' || character == '\t' }) {
            "targetIdentity must not contain control separators."
        }
        require(confirmedAtEpochMillis > 0L) { "confirmedAtEpochMillis must be positive." }
    }
}

/**
 * One owner-authorized sensitive act, bound to an exact destination and an exact job.
 *
 * **This is not a permission for an app.** It names one act, one destination identity, one
 * scope, a short lifetime, and a use count. It carries no account, credential, token,
 * session, or device identifier, and it cannot be widened after issue: every field is
 * `val` and the only mutable state is the remaining-use counter.
 *
 * An approval is bound to a [scopeId] so it can only ever be spent inside the exact job it
 * was issued for. It can never be presented against a different job, a different act, or a
 * different destination.
 */
class SensitiveActionApproval internal constructor(
    val approvalId: String,
    val scopeId: String,
    val actionKind: ApprovalActionKind,
    val targetIdentity: String,
    val issuedAtEpochMillis: Long,
    val durationMillis: Long,
    val maxUses: Int,
) {
    init {
        require(APPROVAL_IDENTIFIER.matches(approvalId)) {
            "approvalId must be 1-64 characters using letters, digits, dot, underscore, colon, or hyphen."
        }
        require(APPROVAL_IDENTIFIER.matches(scopeId)) { "scopeId must be a valid identifier." }
        // The entity enforces its own bounds rather than trusting the gate to have validated
        // an `ApprovalConfirmation` first. A constructor that relies on its caller is not a
        // structural guarantee.
        require(targetIdentity.isNotBlank()) { "targetIdentity must not be blank." }
        require(targetIdentity.length <= ApprovalBounds.MAX_TARGET_LENGTH) {
            "targetIdentity must be at most ${ApprovalBounds.MAX_TARGET_LENGTH} characters."
        }
        require(targetIdentity.none { character -> character == '\n' || character == '\r' || character == '\t' }) {
            "targetIdentity must not contain control separators."
        }
        require(issuedAtEpochMillis > 0L) { "issuedAtEpochMillis must be positive." }
        require(durationMillis in 1L..ApprovalBounds.MAX_DURATION_MILLIS) {
            "durationMillis must be between 1 and ${ApprovalBounds.MAX_DURATION_MILLIS}."
        }
        require(maxUses in 1..ApprovalBounds.MAX_USES) { "maxUses must be between 1 and ${ApprovalBounds.MAX_USES}." }
    }

    /** The instant this approval stops being able to authorise anything. */
    val expiresAtEpochMillis: Long get() = issuedAtEpochMillis + durationMillis

    @Volatile
    private var usesSpent: Int = 0

    @Volatile
    private var revoked: Boolean = false

    /** Uses still available. Reads a volatile so a caller cannot see a torn count. */
    val remainingUses: Int get() = maxUses - usesSpent

    val isRevoked: Boolean get() = revoked

    /**
     * True only when this approval could still authorise something at [nowEpochMillis].
     *
     * This is a **reporting** helper. It never authorises anything on its own; the only
     * method that returns a permit is [ApprovalGate.authorise], which performs the same
     * checks internally rather than trusting a caller to have called this first.
     */
    fun isLiveAt(nowEpochMillis: Long): Boolean =
        !revoked && nowEpochMillis < expiresAtEpochMillis && remainingUses > 0

    internal fun revoke() {
        revoked = true
    }

    /** Consumes one use. Called only after every check has passed. */
    internal fun spendOneUse() {
        check(!revoked) { "A revoked approval cannot be spent." }
        check(remainingUses > 0) { "An exhausted approval cannot be spent." }
        usesSpent += 1
    }
}

/**
 * A request to redeem an approval for one concrete act.
 *
 * The request restates the scope, the act, and the destination rather than trusting the
 * approval alone, so a mismatch between what was approved and what is about to happen is
 * caught instead of assumed away.
 */
data class ApprovalRequest(
    val approvalId: String,
    val scopeId: String,
    val actionKind: ApprovalActionKind,
    val targetIdentity: String,
)

/** Why a redemption or issue was refused. Every value names exactly one cause. */
enum class ApprovalRefusal {
    /** No approval with that identifier exists. */
    UNKNOWN_APPROVAL,

    /** The owner revoked it. Terminal. */
    APPROVAL_REVOKED,

    /** Its lifetime has elapsed. */
    APPROVAL_EXPIRED,

    /** Every use has been spent. */
    APPROVAL_EXHAUSTED,

    /** It was issued inside a different job. */
    SCOPE_MISMATCH,

    /** The bound scope refused this act, so no approval can rescue it. */
    SCOPE_NOT_PERMITTED,

    /** A different act than the one approved. */
    ACTION_MISMATCH,

    /** A different destination than the one approved. */
    TARGET_MISMATCH,

    /** The owner did not confirm at all. */
    CONFIRMATION_MISSING,

    /** The confirmation was blank, or used a wildcard instead of one exact destination. */
    CONFIRMATION_AMBIGUOUS,

    /** The confirmation was too old to still describe a deliberate, current decision. */
    CONFIRMATION_STALE,

    /** The confirmation was a standing grant rather than a single deliberate tap. */
    CONFIRMATION_NOT_SINGLE_TAP,

    /** [ApprovalBounds.MAX_LIVE_APPROVALS] are already outstanding. */
    TOO_MANY_LIVE_APPROVALS,

    /** That approval identifier is already in use. */
    DUPLICATE_APPROVAL_ID,
}

/** The result of one gate operation: a permit with the remaining count, or one refusal. */
sealed interface ApprovalOutcome {
    /**
     * A permit.
     *
     * Holding this value does not perform anything: it records that the owner's decision
     * covered this exact act. The gate has no capability to act, so a caller that wants the
     * effect must still perform it as an owner-visible step.
     */
    data class Approved(
        val approvalId: String,
        val actionKind: ApprovalActionKind,
        val remainingUses: Int,
    ) : ApprovalOutcome

    data class Issued(
        val approvalId: String,
        val expiresAtEpochMillis: Long,
        val maxUses: Int,
    ) : ApprovalOutcome

    data class Revoked(val approvalId: String) : ApprovalOutcome

    /**
     * A refusal.
     *
     * [detail] is a fixed phrase for the reason. It never repeats the destination identity
     * or the approval's target, because refusal records are exactly what gets logged and a
     * destination can reveal a person, an account, or a file name.
     */
    data class Refused(
        val approvalId: String,
        val reason: ApprovalRefusal,
    ) : ApprovalOutcome {
        val detail: String get() = when (reason) {
            ApprovalRefusal.UNKNOWN_APPROVAL -> "No such approval is outstanding."
            ApprovalRefusal.APPROVAL_REVOKED -> "The owner revoked this approval."
            ApprovalRefusal.APPROVAL_EXPIRED -> "This approval has expired."
            ApprovalRefusal.APPROVAL_EXHAUSTED -> "This approval has no uses left."
            ApprovalRefusal.SCOPE_MISMATCH -> "This approval was issued inside a different job."
            ApprovalRefusal.SCOPE_NOT_PERMITTED -> "The job scope does not permit this act."
            ApprovalRefusal.ACTION_MISMATCH -> "This approval covers a different act."
            ApprovalRefusal.TARGET_MISMATCH -> "This approval covers a different destination."
            ApprovalRefusal.CONFIRMATION_MISSING -> "The owner did not confirm."
            ApprovalRefusal.CONFIRMATION_AMBIGUOUS -> "The confirmation named no single exact destination."
            ApprovalRefusal.CONFIRMATION_STALE -> "The confirmation is too old to be current."
            ApprovalRefusal.CONFIRMATION_NOT_SINGLE_TAP -> "The confirmation was not one deliberate tap."
            ApprovalRefusal.TOO_MANY_LIVE_APPROVALS -> "Too many approvals are outstanding."
            ApprovalRefusal.DUPLICATE_APPROVAL_ID -> "That approval identifier is already in use."
        }
    }
}

/**
 * The owner's approval gate for sensitive acts.
 *
 * **Composition, not duplication.** Three existing types answer three different questions
 * and none of them can substitute for another:
 *
 * - [ScopeContract] answers *what may this job touch* — targets, actions, recipients, file
 *   labels, cloud transfer, a time window, and an action budget.
 * - [ActionLease] answers *who is in control right now* — take over, pause, stop, resume,
 *   with an epoch and a fresh-state check.
 * - This gate answers *did the owner specifically approve this one sensitive act* — one
 *   exact destination, a short lifetime, and a small use count.
 *
 * The gate therefore **requires** an already-permitted [ScopeDecision]. A scope refusal can
 * never be rescued by an approval, and an approval can never widen a scope. The two are
 * conjunctive: both must permit.
 *
 * **Approvals never claim an effect.** The gate has no network client, no file handle, and
 * no reference to the capability registry or the lease. It records and refuses; it cannot
 * perform the act it authorises. That is why [ApprovalOutcome.Approved] is a record rather
 * than a capability.
 *
 * **Approvals live in memory only, on purpose.** A grant persisted to disk could be redeemed
 * by a restarted process — exactly the replay hazard Division 19 exists to prevent. Keeping
 * them in memory means a restart silently invalidates every outstanding approval, which
 * fails in the safe direction: the owner is asked again rather than a stale permission being
 * honoured. This also matches [ActionLease], which is in-memory for the same reason, and it
 * keeps the database at schema version 3 with no migration.
 */
class SensitiveActionApprovalGate(
    private val nowEpochMillis: () -> Long,
) {
    private val approvals = linkedMapOf<String, SensitiveActionApproval>()

    /**
     * Records one deliberate owner confirmation as a bounded approval.
     *
     * Every confirmation field is re-validated here rather than trusted, and the check order
     * is fixed and documented: missing → ambiguous → stale → not-a-single-tap.
     */
    @Synchronized
    fun issue(
        approvalId: String,
        confirmation: ApprovalConfirmation,
        durationMillis: Long,
        maxUses: Int,
    ): ApprovalOutcome {
        val refusal = confirmationRefusal(approvalId, confirmation)
        if (refusal != null) {
            return ApprovalOutcome.Refused(approvalId, refusal)
        }
        if (approvals.containsKey(approvalId)) {
            return ApprovalOutcome.Refused(approvalId, ApprovalRefusal.DUPLICATE_APPROVAL_ID)
        }
        if (liveApprovalCount() >= ApprovalBounds.MAX_LIVE_APPROVALS) {
            return ApprovalOutcome.Refused(approvalId, ApprovalRefusal.TOO_MANY_LIVE_APPROVALS)
        }

        val approval = SensitiveActionApproval(
            approvalId = approvalId,
            scopeId = confirmation.scopeId,
            actionKind = confirmation.actionKind,
            targetIdentity = confirmation.targetIdentity,
            issuedAtEpochMillis = nowEpochMillis(),
            durationMillis = durationMillis,
            maxUses = maxUses,
        )
        approvals[approvalId] = approval
        return ApprovalOutcome.Issued(
            approvalId = approval.approvalId,
            expiresAtEpochMillis = approval.expiresAtEpochMillis,
            maxUses = approval.maxUses,
        )
    }

    /**
     * The single method in this build that returns a permit.
     *
     * Checks run in a fixed, fail-closed order and the whole method is one atomic step, so a
     * use can never be spent twice by two callers. A **failed** redemption spends nothing:
     * only a fully successful check consumes a use, so a mistaken attempt cannot burn the
     * owner's budget.
     */
    @Synchronized
    fun authorise(
        request: ApprovalRequest,
        scopeDecision: ScopeDecision,
    ): ApprovalOutcome {
        val approval = approvals[request.approvalId]
            ?: return ApprovalOutcome.Refused(request.approvalId, ApprovalRefusal.UNKNOWN_APPROVAL)

        val refusal = redemptionRefusal(approval, request, scopeDecision)
        if (refusal != null) {
            return ApprovalOutcome.Refused(request.approvalId, refusal)
        }

        approval.spendOneUse()
        return ApprovalOutcome.Approved(
            approvalId = approval.approvalId,
            actionKind = approval.actionKind,
            remainingUses = approval.remainingUses,
        )
    }

    /** Withdraws an approval. Revocation is terminal; a revoked approval is never spendable. */
    @Synchronized
    fun revoke(approvalId: String): ApprovalOutcome {
        val approval = approvals[approvalId]
            ?: return ApprovalOutcome.Refused(approvalId, ApprovalRefusal.UNKNOWN_APPROVAL)
        approval.revoke()
        return ApprovalOutcome.Revoked(approvalId)
    }

    @Synchronized
    fun approval(approvalId: String): SensitiveActionApproval? = approvals[approvalId]

    /** Approvals that could still authorise something at the current instant. */
    @Synchronized
    fun liveApprovals(): List<SensitiveActionApproval> {
        val now = nowEpochMillis()
        return approvals.values.filter { it.isLiveAt(now) }
    }

    private fun liveApprovalCount(): Int {
        val now = nowEpochMillis()
        return approvals.values.count { it.isLiveAt(now) }
    }

    private fun confirmationRefusal(
        approvalId: String,
        confirmation: ApprovalConfirmation,
    ): ApprovalRefusal? {
        if (confirmation.targetIdentity.isBlank()) return ApprovalRefusal.CONFIRMATION_MISSING
        if (!APPROVAL_IDENTIFIER.matches(approvalId)) return ApprovalRefusal.CONFIRMATION_MISSING
        // A wildcard is a refusal, not a pattern: the owner must name one exact destination.
        if (confirmation.targetIdentity.any { it == '*' || it == '?' || it == '%' }) {
            return ApprovalRefusal.CONFIRMATION_AMBIGUOUS
        }
        if (!confirmation.explicitSingleTap) return ApprovalRefusal.CONFIRMATION_NOT_SINGLE_TAP
        val age = nowEpochMillis() - confirmation.confirmedAtEpochMillis
        if (age > ApprovalBounds.MAX_CONFIRMATION_AGE_MILLIS) return ApprovalRefusal.CONFIRMATION_STALE
        return null
    }

    private fun redemptionRefusal(
        approval: SensitiveActionApproval,
        request: ApprovalRequest,
        scopeDecision: ScopeDecision,
    ): ApprovalRefusal? = when {
        approval.isRevoked -> ApprovalRefusal.APPROVAL_REVOKED
        nowEpochMillis() >= approval.expiresAtEpochMillis -> ApprovalRefusal.APPROVAL_EXPIRED
        approval.remainingUses <= 0 -> ApprovalRefusal.APPROVAL_EXHAUSTED
        // Composition: a scope refusal is final and cannot be rescued by any approval.
        scopeDecision is ScopeDecision.Refused -> ApprovalRefusal.SCOPE_NOT_PERMITTED
        approval.scopeId != request.scopeId -> ApprovalRefusal.SCOPE_MISMATCH
        scopeDecision.scopeId != approval.scopeId -> ApprovalRefusal.SCOPE_MISMATCH
        approval.actionKind != request.actionKind -> ApprovalRefusal.ACTION_MISMATCH
        // Exact identity only: no prefix, substring, or case-folded match.
        approval.targetIdentity != request.targetIdentity -> ApprovalRefusal.TARGET_MISMATCH
        else -> null
    }
}
