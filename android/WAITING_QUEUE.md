# Division 19 — Persistent waiting queue

`PersistentWaitingQueue` is a local list of jobs the owner wants to do later. It survives
process death. It **cannot run anything**, and a restarted app can only report what is
waiting — never replay it.

**Verification status: local JVM.** 25 new tests plus 1 updated database test ran in a
standalone Kotlin harness (`passed=148 failed=0` over 14 classes); no-artifact CI is
**pending** (see the CI section).

## Why this division is the risky one

Every previous division stored things that could not act. This one stores things that
*look* actionable: "refresh the source", "review the report". The obvious implementation —
a queue with a worker that drains it on startup — is exactly the bug. A phone reboots, the
queue drains, and work the owner never approved in that session happens by itself.

So the design inverts the usual shape. There is no worker, no executor, and no replay
path to forget to disable. The queue is a **list**, and the safety property is proven by
the absence of a mechanism rather than by a guard.

## The shape

```kotlin
enum class QueuedJobState { WAITING, CANCELLED }

enum class DeferralReason {
    NETWORK_UNAVAILABLE,      // this device had no usable network when the owner looked
    AWAITING_OWNER_DECISION,
    AWAITING_PERMISSION,
}

data class LocalQueuedJob(
    val id: String,                              // [A-Za-z0-9._:-]{1,64}
    val summary: String,                         // ≤200, no control separators
    val state: QueuedJobState,
    val deferral: DeferralReason?,               // null = plainly waiting
    val queuedAtEpochMillis: Long,
    val lastOwnerActionAtEpochMillis: Long,
)
```

There is deliberately **no** `RUNNING`, `ATTEMPTED`, `SUCCEEDED`, `FAILED`, or `COMPLETED`
value. A state that could be read as "this already ran" would let a restarted process imply
work it never did. A test asserts those names are absent from both enums.

`CANCELLED` is terminal and keeps the row, so the owner can still see that something was
dropped rather than finding it silently gone.

## Recovery is derived, not stored

The single most important design choice. A job counts as recovered when:

```
state == WAITING && lastOwnerActionAtEpochMillis < sessionStartedAtEpochMillis
```

`sessionStartedAtEpochMillis` is captured once when the queue object is constructed.
Nothing is written on open, so **opening the queue cannot rewrite history** — and a test
asserts the database file is byte-identical before and after constructing a second queue
over it.

This has a nice consequence: recovery is a comparison, so the same job can be recovered in
two different sessions without any flag being stored, flipped, or forgotten. A test drives
three restarts to prove it.

`acknowledgeRecovered(id)` is the explicit fresh owner action a recovered entry requires.
It stamps the owner's attention — and does nothing else. The job stays `WAITING` with its
deferral intact. **Acknowledgement is not execution**, and a test asserts every other field
is unchanged.

## Deferral records a local observation, never a remote result

`DeferralReason.NETWORK_UNAVAILABLE` means "this device had no usable network when the
owner looked". It does **not** mean a request was attempted, sent, or failed. That is why
the queue has no attempt counter and no outcome field: a deferred network task is recorded
as deferred, and there is no vocabulary in this division for claiming anything else.

## API

| Method | Behaviour |
| --- | --- |
| `enqueue(id, summary, deferral?)` | Refuses `DUPLICATE_JOB_ID` (even against a cancelled job) and `QUEUE_FULL` |
| `defer(id, reason)` | Waiting jobs only; replaces the reason; refused as `JOB_TERMINAL` once cancelled |
| `cancel(id)` | `WAITING → CANCELLED`; second cancel is `JOB_TERMINAL` |
| `acknowledgeRecovered(id)` | Recovered jobs only; otherwise `NOT_A_RECOVERED_JOB` |
| `job(id)` / `active()` / `recovered()` / `recoveredCount()` / `all()` | Reads only |

`active()` is ordered by queued time, then identifier — deterministic, and asserted to be
stable across repeated reads. `all()` includes cancelled jobs; `active()` does not.

## Caps

| Cap | Value |
| --- | --- |
| `MAX_ACTIVE_JOBS` | 100 |
| `MAX_SUMMARY_LENGTH` | 200 |

A full queue **refuses** a new job with `QUEUE_FULL` rather than dropping an existing one.
A test fills the queue to the cap, asserts the refusal, asserts nothing was silently
evicted, then cancels one job and proves the slot is freed — so the cap is a live limit
rather than a dead end.

## Schema decision — version 2 to 3

| Fact | Detail |
| --- | --- |
| Previous version | 2 (`LocalAgentDraft`, `LocalActionEvent`, `LocalKnowledgeItem`) |
| New version | 3 (adds `LocalQueuedJob`) |
| Migration kind | Header upgrade only — no existing row shape changed |
| Old files still load | **Yes**, proven for a hand-written **version 2** file with a knowledge row, and by the pre-existing version 1 test |
| Upgrade timing | In place on `open()`, which normalises the header to version 3 |
| Unknown future version | Still rejected — a version 4 header fails closed |
| Oldest readable | `OLDEST_READABLE_SCHEMA_VERSION = 1` unchanged |

The new row is `QUEUED_JOB` with seven columns: type, id, summary, state, deferral,
queuedAt, lastOwnerActionAt. Free text is Base64-encoded like every other row, and a test
asserts the summary never appears in cleartext on disk. A job with no deferral stores the
sentinel `-`, which can never collide with an enum name.

**Three existing tests were updated honestly rather than deleted:**

1. `LocalEntityDatabaseTest.freshDatabaseWritesTheCurrentSchemaHeader` now asserts header
   version 3.
2. `LocalEntityDatabaseTest.unsupportedSchemaAndMalformedRowsAreRejected` now uses version
   **4** as its unsupported example, since version 3 is current.
3. `OfflineKnowledgeLibraryTest` asserted the version 2 header in two places; both now
   assert version 3, and `legacyVersionOneFileIsReadAndUpgradedToVersionTwo` was renamed
   to `...ToTheCurrentVersion` so the name stops describing a stale target.

## Closing a carried-forward gap

`KnowledgeRefusal.QUERY_TOO_LONG` and `LIMIT_OUT_OF_RANGE` were declared in Division 17 but
could never be returned — `search()` enforces both bounds with `require` and throws, which
is correct for a caller programming error but left two enum values as decoration.

They have been **removed**, with a comment explaining that an out-of-range search argument
is a caller bug rather than a runtime refusal. The alternative — inventing a fault-recovery
path to justify them — would have added unused complexity. A future division that wants a
non-throwing search API should change the return type deliberately rather than re-adding
values nothing returns.

This was carried forward through two divisions; it is now closed rather than carried again.

## Inertness, proven three ways

1. **Structural** — `PersistentWaitingQueue` holds only a database and a clock. A
   reflection test asserts no declared method is named `run`, `execute`, `start`, `launch`,
   `dispatch`, `perform`, `invoke`, `apply`, `send`, `fire`, `replay`, `resume`, `trigger`,
   or `do`, and that no signature accepts or returns a registry, lease, scope, intent,
   socket, or URL.
2. **Behavioural** — enqueueing, deferring, cancelling, and reading all leave an empty
   capability registry unselected and a `STOPPED` lease terminated.
3. **Source-level** — the queue's own file references no `Socket`, `URL(`, HTTP client,
   `java.net`, `java.io.File`, or `RandomAccessFile`.

`LocalQueuedJob` likewise carries no field whose name contains `url`, `uri`, `intent`,
`command`, `package`, `path`, `token`, `credential`, or `endpoint` — there is nothing a
machine could turn back into an action.

## Why queue changes are not paired with `LocalActionEvent`

Division 15 pairs every draft write with a fixed-kind event. This division does not, and the
reason is a real constraint rather than an omission: `LocalActionEvent` is **draft-scoped**
by design — `recordEvent` requires an existing draft and the loader rejects an event whose
draft is missing. A queued job is not a draft. Inventing a synthetic draft to satisfy that
constraint would put a fake entry in the owner's data. The job row carries its state and
both timestamps itself instead.

## Tests — 25 new, all synthetic

| Area | Tests |
| --- | --- |
| Entity bounds and vocabulary | `LocalQueuedJobTest` — 7 tests including the forbidden-outcome and forbidden-field assertions |
| Persistence and recovery | `queuedJobSurvivesRestartAndIsReportedAsRecovered`, `constructingTheQueueOverAnExistingFileWritesNothing`, `recoveryIsDerivedFromTheClockSoTheSameJobCanRecoverTwice` |
| Acknowledgement | `acknowledgingRecoveredJobClearsItWithoutRunningAnything`, `acknowledgingAJobQueuedThisSessionIsRefused` |
| Terminal cancel | `cancellationIsTerminalAndKeepsTheRecord`, `cancellationSurvivesRestartAndNeverBecomesActive` |
| Deferral honesty | `deferringRecordsTheReasonWithoutClaimingAnAttempt`, `deferredNetworkJobSurvivesRestartStillDeferred` |
| Caps and identity | `duplicateIdentifiersAreRefusedEvenAfterCancellation`, `aFullQueueRefusesNewJobsInsteadOfDroppingThem` |
| Ordering | `activeQueueIsOrderedByQueuedTimeThenIdentifier` |
| Inertness | `queueStaysInertWithRespectToRegistryScopeAndLease`, `queueOffersNoWayToExecuteAnything`, `queueSourceOpensNoSocketAndTouchesNoFilesystemPath` |
| Serialisation | `queueRowsRoundTripThroughTheDatabaseInTheirOwnEntityType` |
| Input rejection | `queueRejectsAnOversizedOrMalformedSummary` |
| Schema | `LocalEntityDatabaseTest.legacyVersionTwoFileLoadsUnchangedAndUpgradesToVersionThree` |

Active suite: **169** tests (144 preserved plus these 25).

Local harness: `STANDALONE_TEST_RUN passed=148 failed=0` over 14 classes — the 148
pure-JVM tests, up from 123. Tests needing the Android framework run only in CI.

## One test failure during development, recorded honestly

`activeQueueIsOrderedByQueuedTimeThenIdentifier` first expected `[c, a, b]`. Two jobs shared
a queued timestamp, so the identifier tie-break correctly produced `[a, c, b]`. **The
production code was right and my expectation was wrong.** The test was corrected; no source
was changed to match a mistaken test.

## CI

**Pending.** The exact total moves from 144 to 169 in all five workflow sites (inventory
guard, XML assertion, step label, totals check, notice).

## Boundaries

- No emulator, real-device, permission-granted, or live-service evidence.
- No worker, scheduler, background service, or notification: nothing drains this queue.
- No UI wiring — nothing in the app calls the queue yet.
- No encryption of the local database.
- No claim that a queued job will ever run: this division provides storage and refusal only.
