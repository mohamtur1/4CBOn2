# Division 20 — Sensitive-action approval gate

`SensitiveActionApprovalGate` records one deliberate owner decision for one sensitive act,
bound to an exact destination, a short lifetime, and a small use count. It records and
refuses; it **cannot perform the act it authorises**.

**Verification status: local JVM.** 29 new tests ran in a standalone Kotlin harness
(`passed=177 failed=0` over 16 classes); no-artifact CI is **pending** (see the CI section).

## The composition question, answered first

Three existing types already answer three different questions. The gate must not pretend to
be a fourth version of one of them.

| Type | Question it answers |
| --- | --- |
| `ScopeContract` | *What may this job touch?* Targets, actions, recipients, file labels, cloud transfer, a time window, and an action budget. |
| `ActionLease` | *Who is in control right now?* Take over, pause, stop, resume, with an epoch and a fresh-state check. |
| **`SensitiveActionApprovalGate`** (new) | *Did the owner specifically approve this one sensitive act?* One exact destination, a short lifetime, a small use count. |

None can substitute for another, and the gate does not duplicate either. It is
**conjunctive** with the scope: `authorise()` takes a `ScopeDecision` and refuses outright
when that decision is a `Refused`. A scope refusal can never be rescued by an approval, and
an approval can never widen a scope. Both must permit.

That is tested directly — `scopeRefusalCanNeverBeRescuedByAnApproval` passes a matching,
valid, unspent approval together with a refused scope and asserts the gate refuses and
spends nothing.

## Approvals live in memory, on purpose

This division deliberately does **not** bump the schema.

A grant persisted to disk could be redeemed by a restarted process — exactly the replay
hazard Division 19 exists to prevent. Keeping approvals in memory means a restart silently
invalidates every outstanding approval, which fails in the safe direction: the owner is
asked again rather than a stale permission being honoured. It also matches `ActionLease`,
which is in-memory for the same reason.

The cost is honest and stated: an owner must re-approve after a restart. For a sensitive
action that is the correct behaviour, not a limitation to engineer around. A test
(`approvalsLiveOnlyInMemoryAndDieWithTheGate`) builds a second gate over the same clock and
asserts nothing is recovered. The database stays at **schema version 3** with no migration
and no new table.

## The shape

```kotlin
enum class ApprovalActionKind { SEND_TO_RECIPIENT, CLOUD_TRANSFER, SHARE_FILE }

data class ApprovalConfirmation(
    scopeId, actionKind, targetIdentity, confirmedAtEpochMillis, explicitSingleTap,
)

class SensitiveActionApproval(
    approvalId, scopeId, actionKind, targetIdentity,
    issuedAtEpochMillis, durationMillis, maxUses,
) {
    val expiresAtEpochMillis; val remainingUses; val isRevoked
}

data class ApprovalRequest(approvalId, scopeId, actionKind, targetIdentity)
```

`ApprovalActionKind` is closed: there is no `ANY`, `OTHER`, or wildcard member, so an
approval can only ever be issued for an act this build understands. Adding a value is a
conscious widening, and a test asserts the forbidden names are absent.

An approval is bound to a `scopeId`, so it can only ever be spent inside the exact job it
was issued for. It can never be presented against a different job, a different act, or a
different destination.

## Exact match, not a pattern

Destination identity matches by **string equality only** — no prefix, no substring, no
case-folding, no trimming. The test drives eight near-misses at a valid approval:

`synthetic-recip` · `synthetic-recipient-two` · `nthetic-recipient` ·
`Synthetic-Recipient` · `SYNTHETIC-RECIPIENT` · ` synthetic-recipient` ·
`synthetic-recipient ` · `` (empty)

All eight are refused and **no use is spent**. Case-folding is deliberately *not* applied:
two identities differing only in case are treated as different destinations, because
silently folding them would merge two things the owner may consider distinct.

## Structural expiry

Expiry is checked inside `authorise()` — the single method in this build that returns a
permit. There is no separate "is it expired?" path that grants anything; `isLiveAt()` is a
reporting helper only and its KDoc says so.

The window is half-open: live at `expiresAt - 1`, refused at `expiresAt`. Tests pin both
edges for two different approvals with different start times, so the check is against the
approval's own window rather than a shared constant.

`ApprovalBounds.MAX_DURATION_MILLIS` equals `ScopeBounds.MAX_DURATION_MILLIS` by
construction — an approval must never outlive the job that justified it — and a test asserts
that relationship rather than trusting it.

## Use caps consumed atomically

`authorise()` is `@Synchronized`, so two callers cannot both pass the remaining-use check
and spend the same use. Consumption happens only after every check has passed:

- a **failed** redemption spends nothing, so a mistaken attempt cannot burn the owner's
  budget — tested with four different failure modes followed by a successful redemption that
  still has the full count;
- `spendOneUse()` itself re-checks both revocation and exhaustion, so a future caller cannot
  bypass the guard by calling it directly.

`MAX_USES` is 10; a single-use approval is exhaustible and a second attempt reports
`APPROVAL_EXHAUSTED`.

## Deliberate confirmation

Confirmation is validated at issue time in a fixed order: **missing → ambiguous → stale →
not-a-single-tap**.

| Case | Refusal | Meaning |
| --- | --- | --- |
| Blank or absent target | `CONFIRMATION_MISSING` | The owner named nothing |
| Contains `*`, `?`, or `%` | `CONFIRMATION_AMBIGUOUS` | A wildcard is not a destination |
| Older than 60 s | `CONFIRMATION_STALE` | Not a current decision |
| `explicitSingleTap = false` | `CONFIRMATION_NOT_SINGLE_TAP` | A standing grant, not one tap |

The wildcard rule is the direct expression of "refusing an approval whose declared scope is
wider than the action being performed": `*`, `recipient*`, `?`, `%`, `any*one`, and `*@*`
are all refused, so a bulk "send to everyone" can never be constructed from a confirmation.

`CONFIRMATION_NOT_SINGLE_TAP` exists so a caller cannot synthesise a standing permission by
setting one boolean. It is a refusal, not a default.

## Redemption refusal order

Fixed and documented, cheapest and most fundamental first:

`UNKNOWN_APPROVAL` → `APPROVAL_REVOKED` → `APPROVAL_EXPIRED` → `APPROVAL_EXHAUSTED` →
`SCOPE_NOT_PERMITTED` → `SCOPE_MISMATCH` → `ACTION_MISMATCH` → `TARGET_MISMATCH`

`SCOPE_NOT_PERMITTED` sits before the field comparisons deliberately: when the job scope
already refuses, that is the more truthful cause, and reporting a target mismatch instead
would imply the approval was otherwise usable. A test drives colliding cases at each
boundary.

## No echoing of destinations

`ApprovalOutcome.Refused` carries a reason and a **fixed phrase**, never an interpolated
destination. Refusal records are exactly what gets logged, and a destination can reveal a
person, an account, or a file name. A test pushes a sentinel through two refusal paths and
asserts neither the sentinel nor the approved target appears in the detail text.

## Inertness, proven three ways

1. **Structural** — a reflection test asserts no declared method is named `run`, `execute`,
   `start`, `launch`, `dispatch`, `perform`, `invoke`, `apply`, `send`, `share`, `transfer`,
   `upload`, `post`, `deliver`, or `fire`, and that no signature accepts or returns a socket,
   URL, output stream, file, registry, lease, executor, or intent.
2. **Behavioural** — issuing, authorising, and revoking leave an empty capability registry
   unselected and a `STOPPED` lease terminated.
3. **Entity-level** — `SensitiveActionApproval` carries no field whose name contains
   `token`, `credential`, `password`, `secret`, `session`, `cookie`, `account`, `url`,
   `uri`, `endpoint`, `command`, `package`, or `intent`.

`ApprovalOutcome.Approved` is a **record**, not a capability. Holding it performs nothing;
a caller that wants the effect must still perform it as an owner-visible step.

## Caps

| Cap | Value | Why |
| --- | --- | --- |
| `MAX_DURATION_MILLIS` | 300 000 (5 min) | Matches the scope's own limit |
| `MAX_USES` | 10 | Bounded blast radius per decision |
| `MAX_TARGET_LENGTH` | 200 | Bounds one identity |
| `MAX_CONFIRMATION_AGE_MILLIS` | 60 000 | A confirmation must be current |
| `MAX_LIVE_APPROVALS` | 50 | Bounds gate memory |

A full gate refuses with `TOO_MANY_LIVE_APPROVALS` rather than evicting an outstanding
approval. A test fills to the cap, asserts the refusal, asserts nothing was silently
evicted, then advances the clock past expiry and proves a slot frees — so the cap tracks
*live* approvals rather than history.

## Test inventory — 29 new

| File | Count | Coverage |
| --- | --- | --- |
| `SensitiveActionApprovalTest` | 8 | Bounds, window edges, spend/revoke, forbidden fields, closed enum |
| `SensitiveActionApprovalGateTest` | 21 | Exact match, expiry, caps, confirmation, composition, ordering, inertness |

Active suite: **198** tests (169 preserved plus these 29).

Local harness: `STANDALONE_TEST_RUN passed=177 failed=0` over 16 classes — the 177 pure-JVM
tests, up from 148. Tests needing the Android framework run only in CI.

## Development record — three failures, one of them a real gap

The first run had three failures. Two were mine; **one was a genuine hole in the source**.

1. **Real production gap.** `SensitiveActionApproval`'s constructor checked
   `targetIdentity.isNotBlank()` but not the length or control-separator bounds its own
   `ApprovalBounds` declares. Only `ApprovalConfirmation` enforced them, so the entity
   relied on its caller being careful. **The entity was hardened to enforce its own bounds**,
   with a comment recording why: a constructor that trusts its caller is not a structural
   guarantee.
2. **My test bug.** `expiryIsCheckedInsideTheOnlyPermitPath` assumed a second approval would
   expire at the first one's deadline. It had been issued later, so its window started
   later — the code was right. The test now derives each approval's own expiry and pins both
   edges against it.
3. **My test bug.** `redemptionChecksRunInTheDocumentedOrder` issued approvals with a
   timestamped confirmation after advancing the clock, so they were correctly refused as
   `CONFIRMATION_STALE`. The fix stamps confirmations at the current instant, and the shared
   `issueDefault` helper now always does so, so this cannot recur.

The test-count assertion was also corrected: I first wrote 202, verified the real total is
198, and fixed the workflow rather than trusting the estimate.

## CI

**Pending.** The exact total moves from 169 to 198 in all five workflow sites (inventory
guard, XML assertion, step label, totals check, notice).

## Boundaries

- No emulator, real-device, permission-granted, or live-service evidence.
- No UI: there is no confirmation dialog, notification, or biometric prompt. The gate
  validates a confirmation value; it does not render one.
- No persistence: approvals die with the process, deliberately.
- No performance or memory measurement on a phone.
- No claim that any sensitive action is wired end to end: this division supplies the gate,
  not an integration.
