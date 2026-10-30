# 4CBOn2 — Division 19 verification receipt

**Date:** 30 October 2026
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Verified source commit:** `5ac1b3b7c7139b3bbd4ffdc3fa3d7f4d37ad0542`
**Verified source tree:** `2e3c9f869bb889141ca5e978123be34d88364781`
**CI run:** https://github.com/mohamtur1/4CBOn2/actions/runs/41006981403
**CI conclusion:** Success, no uploaded artifacts

> Arena fixes this session to `arena/74f65509-4cbon2`. Division 19 continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Like you are two

The app can keep a waiting list now. You write down something you want to do later, and it is still there after the app closes and opens again.

The important part is the thing it **cannot** do. When the app wakes up, it does not go and do the waiting things by itself. It has no way to — there is no robot arm attached to the list, only a piece of paper. It just shows you what is still waiting and says "these were left over." Then *you* look at them.

And when you put something down as "waiting for internet," it only remembers that your phone had no internet. It never pretends it tried and failed, because it never tried.

The big robot checked 169 little things and all passed, and it kept no app file.

## Like you are an adult

Division 19 added a restart-surviving local queue — the first division to store something that *looks* actionable. The obvious implementation, a queue with a worker that drains on startup, is precisely the bug: a phone reboots, the queue drains, and work the owner never approved in that session happens by itself.

The design inverts that. There is no worker, no executor, and no replay path to forget to disable. The queue is a list, and the safety property is proven by the absence of a mechanism rather than by a guard.

Twenty-five synthetic JVM tests were added to the 144 preserved tests, bringing the active suite to 169; the CI inventory guard, step label, notice, and exact XML assertion were all moved to 169 without weakening.

## CI evidence

| Check | Actual result |
| --- | --- |
| Workflow | `Android Division 09b Verification` |
| Run | `41006981403` (push to `arena/74f65509-4cbon2`) |
| Conclusion | **Success** in 3m11s |
| Verified commit | `5ac1b3b7c7139b3bbd4ffdc3fa3d7f4d37ad0542` |
| Verified tree | `2e3c9f869bb889141ca5e978123be34d88364781` |
| Active source test inventory | 169 |
| JUnit aggregation | 169 passed; 0 failures; 0 errors; 0 skipped |
| Debug APK assembly | Passed |
| Zip alignment | Passed |
| APK Signature Scheme v2 | Passed |
| Debug APK SHA-256 | `4b36b3ceb1715e9f7593dd33815bee28c9ae240ca674c903dc1a064fcaee9cea` |
| Build did not modify source | Passed |
| Artifact-retention step | Skipped |
| CI artifact count | `0` (read back from the run-artifacts API) |
| Historical 09c artifact marker in commit message | Absent |
| Credentials, secrets, live accounts, PR, or merge | None used; none opened |

The APK was ephemeral CI output: not uploaded, retained, committed, installed, or production-signed.

## What was built

```kotlin
enum class QueuedJobState { WAITING, CANCELLED }

enum class DeferralReason {
    NETWORK_UNAVAILABLE, AWAITING_OWNER_DECISION, AWAITING_PERMISSION,
}

data class LocalQueuedJob(
    id, summary, state, deferral, queuedAtEpochMillis, lastOwnerActionAtEpochMillis,
)
```

There is deliberately **no** `RUNNING`, `ATTEMPTED`, `SUCCEEDED`, `FAILED`, or `COMPLETED`
value. A state that could be read as "this already ran" would let a restarted process imply
work it never did. A test asserts those names are absent from both enums.

### Recovery is derived, not stored

A job counts as recovered when:

```
state == WAITING && lastOwnerActionAtEpochMillis < sessionStartedAtEpochMillis
```

Nothing is written on open, so **opening the queue cannot rewrite history** — a test asserts
the database file is byte-identical before and after constructing a second queue over it.
Because recovery is a comparison rather than a flag, the same job can recover in two
different sessions with nothing to flip or forget; a test drives three restarts.

`acknowledgeRecovered(id)` is the explicit fresh owner action. It stamps the owner's
attention and does nothing else: the job stays `WAITING`, its deferral intact. The test
asserts every other field is unchanged. **Acknowledgement is not execution.**

### Deferral records a local observation

`NETWORK_UNAVAILABLE` means "this device had no usable network when the owner looked". It
does **not** mean a request was attempted, sent, or failed. There is no attempt counter and
no outcome field, so this division has no vocabulary for claiming otherwise.

### Caps and refusal

| Cap | Value |
| --- | --- |
| `MAX_ACTIVE_JOBS` | 100 |
| `MAX_SUMMARY_LENGTH` | 200 |

A full queue **refuses** with `QUEUE_FULL` rather than evicting. The test fills to the cap,
asserts the refusal, asserts nothing was silently dropped, then cancels one job and proves
the slot frees — so the cap is a live limit, not a dead end. Duplicate identifiers are
refused even against a cancelled job, so a cancellation keeps its history.

## Inertness, proven three ways

1. **Structural** — a reflection test asserts no declared method is named `run`, `execute`,
   `start`, `launch`, `dispatch`, `perform`, `invoke`, `apply`, `send`, `fire`, `replay`,
   `resume`, `trigger`, or `do`, and that no signature accepts or returns a registry, lease,
   scope, intent, socket, or URL.
2. **Behavioural** — enqueueing, deferring, cancelling, and reading leave an empty capability
   registry unselected and a `STOPPED` lease terminated.
3. **Source-level** — the queue's own file references no `Socket`, `URL(`, HTTP client,
   `java.net`, `java.io.File`, or `RandomAccessFile`.

`LocalQueuedJob` carries no field whose name contains `url`, `uri`, `intent`, `command`,
`package`, `path`, `token`, `credential`, or `endpoint`.

## Schema decision — version 2 to 3

| Fact | Detail |
| --- | --- |
| Migration kind | Header upgrade only — no existing row shape changed |
| Old files still load | **Yes**, proven for a hand-written **version 2** file with a knowledge row, and by the existing version 1 test |
| Upgrade timing | In place on `open()`, which normalises the header to version 3 |
| Unknown future version | Still rejected — a version 4 header fails closed |
| Oldest readable | `OLDEST_READABLE_SCHEMA_VERSION = 1` unchanged |
| File name | Deliberately still `4cbon2-local-entities.v1.db`; it names file lineage, not schema |

The new row is `QUEUED_JOB`, seven columns. Free text is Base64-encoded and a test asserts
the summary never appears in cleartext on disk. A job with no deferral stores the sentinel
`-`, which can never collide with an enum name because `enumValueOf` only accepts identifiers.

**Four existing tests were updated honestly rather than deleted:**

1. `LocalEntityDatabaseTest.freshDatabaseWritesTheCurrentSchemaHeader` — now asserts header 3.
2. `LocalEntityDatabaseTest.unsupportedSchemaAndMalformedRowsAreRejected` — now uses version
   **4** as unsupported, since 3 is current.
3. `OfflineKnowledgeLibraryTest` asserted the version 2 header in two places; both now
   assert 3.
4. `legacyVersionOneFileIsReadAndUpgradedToVersionTwo` was renamed to
   `...ToTheCurrentVersion`, so the name stops describing a stale target.

## Carried-forward gap closed

`KnowledgeRefusal.QUERY_TOO_LONG` and `LIMIT_OUT_OF_RANGE` were declared in Division 17 but
could never be returned — `search()` enforces both bounds with `require` and throws, which is
correct for a caller programming error but left two enum values as decoration.

They have been **removed**, with an inline comment recording why. The alternative, inventing
a fault-recovery path to justify them, would have added unused complexity. This item was
carried through two divisions; it is now closed rather than carried a third time.

## Development record

One test failed on the first run: `activeQueueIsOrderedByQueuedTimeThenIdentifier` expected
`[c, a, b]`. Two jobs shared a queued timestamp, so the identifier tie-break correctly
produced `[a, c, b]`. **The production code was right and my expectation was wrong.** The
test was corrected; no source was changed to match a mistaken test.

Three failures appeared together: that one, plus two stale header assertions in
`OfflineKnowledgeLibraryTest` that the legitimate schema move to version 3 had invalidated.
All three were test-side corrections.

## Why queue changes are not paired with `LocalActionEvent`

Division 15 pairs every draft write with a fixed-kind event. This division does not, and the
reason is a real constraint: `LocalActionEvent` is **draft-scoped** by design —
`recordEvent` requires an existing draft and the loader rejects an event whose draft is
missing. A queued job is not a draft, and inventing a synthetic draft to satisfy that
constraint would put a fake entry in the owner's data. The job row carries its state and both
timestamps itself instead.

## Document status

`android/WAITING_QUEUE.md` records this division's design and its CI section reads
“Pending”, because this follow-up commit is restricted to root Markdown. This receipt is the
authoritative supersession of that section; no Android source or workflow file changed after
the CI-verified commit.

Seven earlier `android/*.md` files still show their own “Pending” sections; their root
receipts supersede them. Only a source-touching division can correct the files themselves.

## Boundaries

- No emulator, real-device, permission-granted, production-signing, Play Store, or
  live-service claim.
- No worker, scheduler, background service, or notification: nothing drains this queue.
- No UI wiring — nothing in the app calls the queue yet.
- No encryption of the local database.
- No claim that a queued job will ever run: this division provides storage and refusal only.
- Division 20 — Sensitive-action approval gate has not started.
