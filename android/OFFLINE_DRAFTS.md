# Division 15 — Offline agent drafts

**Work branch:** `arena/74f65509-4cbon2` (the fixed Arena session branch; source baseline imported from `arena/0eb49904-4cbon2` at `2a45201`)
**Scope:** Owner create/edit/list/load of local agent drafts with a goal, template name, and draft state, persisted through the existing local entity database.
**Evidence class:** Source/static checks plus JVM unit tests, followed by no-artifact CI (receipt recorded separately once verified).

## Like you are two

You can write a little plan now. You give it a name, a wish, and a helper pattern, and the app keeps it in its own notebook file. You can close the app, come back later, and your plan is still there and still yours to change. If you cross a plan out, it stays crossed out. Writing a plan never turns anything on and never starts a job.

## Like you are an adult

Division 15 adds `OfflineDraftRepository`, a bounded local layer over the Division 11 `LocalEntityDatabase`. It uses the existing `LocalAgentDraft` and `LocalActionEvent` entities unchanged, so **schema version stays 1**: no migration, no new file format, no new dependency, and no parallel draft model.

Every write pairs the draft with a fixed-kind local event, so the on-disk trail matches what the owner did.

## Operations

| Operation | Result | Event recorded |
| --- | --- | --- |
| `createDraft(title, goal, templateName, id?)` | New draft in `DRAFT` | `DRAFT_CREATED` / `PLANNED` |
| `editDraft(id, title, goal, templateName)` | Rewritten text, same identity, state, and creation time | `DRAFT_UPDATED` / `PLANNED` |
| `submitForReview(id)` | `DRAFT` → `WAITING_FOR_REVIEW` | `LOCAL_CHECK_RECORDED` / `PLANNED` |
| `returnToDraft(id)` | `WAITING_FOR_REVIEW` → `DRAFT` | `DRAFT_UPDATED` / `PLANNED` |
| `cancelDraft(id)` | → terminal `CANCELLED` | `DRAFT_UPDATED` / `CANCELLED` |
| `prepareManualHandoff(id)` | Draft text and state unchanged | `MANUAL_HANDOFF_PREPARED` / `PLANNED` |
| `loadDraft(id)` / `listDrafts()` / `eventsFor(id)` | Read-only views | None |

## State machine

```text
DRAFT <--> WAITING_FOR_REVIEW
   |              |
   +------> CANCELLED <------+
          (terminal)
```

`CANCELLED` refuses editing, submission, return-to-draft, and manual-handoff preparation with an explicit reason. An invalid starting state is refused **without writing anything**, so a refused operation never leaves a partial pair of rows.

## Refusals

| Reason | When |
| --- | --- |
| `UNKNOWN_DRAFT` | The identifier is not in the local file |
| `DUPLICATE_DRAFT_ID` | Creation would overwrite an existing draft |
| `EDIT_NOT_ALLOWED_IN_STATE` | Editing or handoff preparation on a cancelled draft |
| `INVALID_STATE_TRANSITION` | A move the state machine does not allow |

Pure value validation (blank title, over-long goal, control separators) throws `IllegalArgumentException` from the existing entity constructor and is checked before any write.

## Inertness

The repository holds only the database and a clock. It has **no reference** to `SelectedAppCapabilityRegistry`, `ActionScopeEnforcer`, `ActionLease`, or `BoundedJobController`, so saving a draft cannot grant a permission, start a job, resume a stopped lease, or bypass a scope contract. A dedicated test proves this behaviourally: with an empty registry and a `STOPPED` lease, drafts still save, and afterwards no app has become selected and the lease is still stopped.

A draft is text the owner can return to. It is not an authorization to act.

## Bounded fields

| Field | Limit | Source of truth |
| --- | --- | --- |
| Title | 120 characters, non-blank, no control separators | `LocalAgentDraft` |
| Goal | 1,000 characters, non-blank, no control separators | `LocalAgentDraft` |
| Template name | 80 characters, non-blank, no control separators | `LocalAgentDraft` |
| Identifier | 1–64 of `A-Za-z0-9._:-` | `LocalAgentDraft` |
| Event note | 160 characters | `LocalActionEvent` |

No account, credential, token, service response, or cloud-sync field can enter a draft, because none exists on the entity.

## Event identifiers

An event identifier is `evt-<hex hash of draft id>-<n>`, where `n` is the number of events already stored for that draft. The count is recomputed from the file, so identifiers stay unique after a restart, and the result stays within the 64-character identifier limit even for a maximum-length draft identifier. The hash suffix keeps two long draft identifiers with a shared prefix from colliding in the common case; it is not a cryptographic uniqueness guarantee, which is acceptable for a local prototype and is documented rather than overstated.

## Persistence proof

The central test writes a draft, edits it, closes the database, confirms the file on disk does **not** contain the plaintext goal, then opens a **new** database and a **new** repository against the same file and reads back the edited goal, the state, and the ordered event trail. This distinguishes restart persistence from in-memory reuse. It is a JVM temporary-file test, not a device reboot test.

## Deliberate boundaries

- **No UI wiring:** nothing on screen creates or lists drafts yet.
- **No encryption:** the file is Base64-encoded, not encrypted; sensitive values must not be stored in drafts.
- **No job profile or cap persistence:** the bounded caps live in `ScopeLimits` and are applied by a `ScopeContract` when a job is actually started. Attaching caps to a stored draft would require a schema decision and has been deliberately deferred rather than smuggled in.
- **No network, credential, account, or live service.**
- **No claim** of production signing, device verification, or universal phone control.

## Tests

`OfflineDraftRepositoryTest` adds 15 synthetic tests:

| Area | Tests | Coverage |
| --- | --- | --- |
| Persistence | 2 | Creation event pairing; close/reopen under a new database and repository, with no plaintext goal on disk |
| Creation | 2 | Duplicate identifier refused without overwrite; bounded-field validation before any write |
| Editing | 3 | Text rewrite and update-time advance; unknown draft; identity/state/creation time preserved |
| Listing | 1 | Deterministic order by creation time, then identifier |
| State machine | 4 | Submit to review; invalid transition writes nothing; cancel is terminal; cancel/return from review |
| Handoff | 1 | Event recorded with the draft unchanged |
| Inertness | 1 | Saves with an empty registry and a stopped lease; neither is altered |
| Identifiers | 1 | Event identifiers unique across reopen and within the identifier limit |

The active suite is **86** (`71` preserved + `15` new). The CI source-inventory guard, step label, notice, and exact XML aggregation assertion are aligned to 86; none was weakened.

## Local checks

- Active `@Test` source count: **86**.
- New runtime dependency count: **0**.
- Schema version: **1**, unchanged; no migration.
- Local Android JDK/Gradle/Android SDK toolchain: **absent**; no local Gradle compilation, APK assembly, alignment, or signature is claimed.
- Isolated Kotlin 2.4.21 / JVM 17 type-check: **passed** (not a project Gradle build).
- Reflective standalone JVM run with a stub JUnit harness: **15 passed, 0 failed** for Division 15, and **65 passed, 0 failed** across the Division 11–15 pure-Kotlin classes (`STANDALONE_TEST_RUN passed=65 failed=0`). Not a Gradle, emulator, or device test.
- Harness fidelity notes: the stub `@Rule` annotation initially lacked a property-getter target and the stub `TemporaryFolder` initially hid `root`; both were corrected to match real JUnit rather than changing production code.

## Approved no-artifact CI verification

Pending owner approval of the source push and a successful workflow run that records:

- compilation and all 86 unit tests with zero failures/errors/skips;
- fresh debug APK assembly;
- zip alignment and APK Signature Scheme v2 verification;
- SHA-256;
- clean-source check;
- zero uploaded artifacts;
- no historical 09c device-APK artifact marker in the commit message.
