# 4CBOn2 — Division 20 verification receipt

**Date:** 30 October 2026
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Verified source commit:** `859b0972d1592da9ededff987a2c277abf7ab717`
**Verified source tree:** `1738bcbd10630b6b1ccadad814677f24e26b377f`
**CI run:** https://github.com/mohamtur1/4CBOn2/actions/runs/41020784286
**CI conclusion:** Success, no uploaded artifacts

> Arena fixes this session to `arena/74f65509-4cbon2`. Division 20 continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Like you are two

The app asks permission properly now. Before it does something spill-y — like sending something to somebody — it needs a little slip from you that says *this exact thing, to this exact person, good until this time, and only so many times.* Not "yes, probably." Not "yes to everyone."

A few careful bits:

- If you write "send to every\*one," it says no. A star is not a person.
- If the slip is old, it stops working. It goes bad on purpose.
- If the slip says five times and you try six, the sixth is a no.
- If you try the wrong person, the wrong thing, or the wrong job, it says no — and it does **not** use up one of your five. So a mistake doesn't cost you.
- If you close the app and open it, all the slips are gone. That sounds annoying, but it is the safe way round: better to ask you again than to do something because of an old yes you forgot about.

The big robot checked 198 little things and all passed, and it kept no app file.

## Like you are an adult

Division 20 added the approval gate for sensitive acts. The design question the work order opened with — how this relates to `ScopeContract` and `ActionLease` — was answered by **composition rather than duplication**: the scope answers *what may this job touch*, the lease answers *who is in control*, and this gate answers *did the owner approve this one act*. The gate requires an already-permitted scope decision, so the two are conjunctive.

Twenty-nine synthetic JVM tests were added to the 169 preserved tests, bringing the active suite to 198; the CI inventory guard, step label, notice, and exact XML assertion were all moved to 198 without weakening.

## CI evidence

| Check | Actual result |
| --- | --- |
| Workflow | `Android Division 09b Verification` |
| Run | `41020784286` (push to `arena/74f65509-4cbon2`) |
| Conclusion | **Success** in 3m46s |
| Verified commit | `859b0972d1592da9ededff987a2c277abf7ab717` |
| Verified tree | `1738bcbd10630b6b1ccadad814677f24e26b377f` |
| Active source test inventory | 198 |
| JUnit aggregation | 198 passed; 0 failures; 0 errors; 0 skipped |
| Debug APK assembly | Passed |
| Zip alignment | Passed |
| APK Signature Scheme v2 | Passed |
| Debug APK SHA-256 | `6fcee2e447a97613554c3f09cf6f9d34ae234c2a4a42f4c0e569a3cbed1dc387` |
| Build did not modify source | Passed |
| Artifact-retention step | Skipped |
| CI artifact count | `0` (read back from the run-artifacts API) |
| Historical 09c artifact marker in commit message | Absent |
| Credentials, secrets, live accounts, PR, or merge | None used; none opened |

The APK was ephemeral CI output: not uploaded, retained, committed, installed, or production-signed.

## What was built

```kotlin
enum class ApprovalActionKind { SEND_TO_RECIPIENT, CLOUD_TRANSFER, SHARE_FILE }

data class ApprovalConfirmation(
    scopeId, actionKind, targetIdentity, confirmedAtEpochMillis, explicitSingleTap,
)

class SensitiveActionApproval(
    approvalId, scopeId, actionKind, targetIdentity,
    issuedAtEpochMillis, durationMillis, maxUses,
) { val expiresAtEpochMillis; val remainingUses; val isRevoked }

data class ApprovalRequest(approvalId, scopeId, actionKind, targetIdentity)
```

`ApprovalActionKind` is closed — no `ANY`, `OTHER`, or wildcard member — so an approval can
only be issued for an act this build understands. A test asserts the forbidden names are
absent.

### Composition, tested rather than asserted

`authorise()` requires a `ScopeDecision` and refuses outright when it is a `Refused`. The
test `scopeRefusalCanNeverBeRescuedByAnApproval` presents a matching, valid, unspent approval
together with a refused scope, and asserts the gate returns `SCOPE_NOT_PERMITTED` and spends
nothing. An approval can never widen a scope, and a scope refusal is final.

### Exact identity, not a pattern

Destination matching is string equality only. Eight near-misses are refused against a valid
approval: prefix, suffix, substring, two case variants, two whitespace variants, and empty.
No use is spent on any of them. Case-folding is deliberately withheld — two identities
differing only in case are different destinations, so the tool never merges what the owner
may consider distinct.

### Structural expiry, atomic use caps

Expiry is checked inside `authorise()`, the only method that returns a permit; `isLiveAt()`
is a reporting helper whose KDoc says so. The window is half-open, and tests pin both edges
against each approval's own start time.

`authorise()` is `@Synchronized` and consumption happens only after every check passes, so a
failed attempt never burns the owner's budget — tested with four distinct failure modes
followed by a successful redemption still holding the full count. `spendOneUse()` re-checks
revocation and exhaustion itself, so a future caller cannot bypass the guard.

`ApprovalBounds.MAX_DURATION_MILLIS` equals `ScopeBounds.MAX_DURATION_MILLIS`, and a test
asserts the relationship so an approval can never outlive the job that justified it.

### Deliberate confirmation

Fixed validation order at issue time: **missing → ambiguous → stale → not-a-single-tap**.
Wildcards (`*`, `?`, `%`) are refused as ambiguous, which is the direct expression of
"refusing an approval whose declared scope is wider than the action being performed."
`CONFIRMATION_NOT_SINGLE_TAP` exists so a caller cannot synthesise a standing permission by
setting one boolean.

### Bounded and non-echoing

| Cap | Value |
| --- | --- |
| `MAX_DURATION_MILLIS` | 300 000 |
| `MAX_USES` | 10 |
| `MAX_TARGET_LENGTH` | 200 |
| `MAX_CONFIRMATION_AGE_MILLIS` | 60 000 |
| `MAX_LIVE_APPROVALS` | 50 |

A full gate refuses rather than evicting an outstanding approval; the test fills to the cap,
asserts nothing was dropped, advances the clock past expiry, and proves a slot frees.
Refusals carry fixed phrases only — a sentinel pushed through two refusal paths never appears
in the returned detail.

## Schema decision — no bump, and why

This division deliberately did **not** raise the schema. Approvals live in memory only.

A grant persisted to disk could be redeemed by a restarted process, which is exactly the
replay hazard Division 19 exists to prevent. In-memory approvals mean a restart silently
invalidates every outstanding approval — failing in the safe direction, so the owner is asked
again rather than a stale permission being honoured. This also matches `ActionLease`, which
is in-memory for the same reason. The database stays at **version 3**, `LocalEntityDatabase.kt`
is byte-unchanged in this commit, and no migration was needed.

The cost is stated plainly: an owner must re-approve after a restart. For a sensitive action
that is correct behaviour, not a limitation to engineer around.

## Inertness, proven three ways

1. **Structural** — a reflection test asserts no declared method is named `run`, `execute`,
   `start`, `launch`, `dispatch`, `perform`, `invoke`, `apply`, `send`, `share`, `transfer`,
   `upload`, `post`, `deliver`, or `fire`, and that no signature accepts or returns a socket,
   URL, output stream, file, registry, lease, executor, or intent.
2. **Behavioural** — issuing, authorising, and revoking leave an empty capability registry
   unselected and a `STOPPED` lease terminated.
3. **Source-level** — the gate file has no imports at all, so it cannot reach a network, a
   filesystem, or an executor.

`ApprovalOutcome.Approved` is a record, not a capability: holding it performs nothing.

## Development record — three failures, one a real gap

The first run had three failures. Two were mine; **one was a genuine hole in the source**.

1. **Real production gap.** `SensitiveActionApproval`'s constructor checked
   `targetIdentity.isNotBlank()` but never enforced the length or control-separator bounds
   that its own `ApprovalBounds` declares. Only `ApprovalConfirmation` enforced them, so the
   entity depended on its caller being careful. **The entity was hardened** to enforce its own
   bounds, with a comment recording that a constructor trusting its caller is not a
   structural guarantee. The test was right and found the bug.
2. **My test bug.** `expiryIsCheckedInsideTheOnlyPermitPath` assumed a second approval would
   expire at the first one's deadline; it had been issued later, so its window started later.
   The code was right. The test now derives each approval's own expiry.
3. **My test bug.** `redemptionChecksRunInTheDocumentedOrder` issued approvals with a
   timestamped confirmation after advancing the clock, so they were correctly refused as
   `CONFIRMATION_STALE`. The shared helper now stamps confirmations at the current instant,
   so this cannot recur.

The CI total was also corrected rather than assumed: I first wrote 202, verified the real
count is 198, and fixed the workflow.

## Testing note

The local harness's JUnit stub lacked `assertNull(message, actual)`, which real JUnit
provides. The **stub** was extended, not the test — production and test source were not
weakened to suit a stand-in.

## Document status

`android/SENSITIVE_APPROVAL.md` records this division's design and its CI section reads
“Pending”, because this follow-up commit is restricted to root Markdown. This receipt is the
authoritative supersession of that section; no Android source or workflow file changed after
the CI-verified commit.

Eight earlier `android/*.md` files still show their own “Pending” sections; their root
receipts supersede them. Only a source-touching division can correct the files themselves.

## Repository recovery during this division

The local Git-metadata reset recurred a **sixth** time, between committing locally and
pushing. The recovery guard added after the fifth occurrence worked as designed: it detected
the missing commit, re-fetched, `reset --mixed`, rebuilt the index, and confirmed the rebuilt
tree matched `1738bcbd10630b6b1ccadad814677f24e26b377f` exactly before re-committing and
pushing in the same invocation.

The commit chain was `0105e9e` → destroyed → `859b097` (pushed). Identical tree, so the
approved content never changed; only commit identity did. Nothing partial was uploaded,
because the push and the recovery ran in one shell invocation and the push was verified with
`git ls-remote` afterwards.

## Boundaries

- No emulator, real-device, permission-granted, production-signing, Play Store, or
  live-service claim.
- No UI: there is no confirmation dialog, notification, or biometric prompt. The gate
  validates a confirmation value; it does not render one.
- No persistence: approvals die with the process, deliberately.
- No performance or memory measurement on a phone.
- No claim that any sensitive action is wired end to end: this division supplies the gate,
  not an integration.
- Division 21 — One harmless app intent/API action has not started.
