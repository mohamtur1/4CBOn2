# Division 17 — Offline knowledge library

**Work branch:** `arena/74f65509-4cbon2` (the fixed Arena session branch; source baseline imported from `arena/0eb49904-4cbon2` at `2a45201`)
**Scope:** Save and search bounded local reports and evidence with honest provenance, persisted through the local entity database at a new schema version.
**Evidence class:** Source/static checks plus JVM unit tests, followed by no-artifact CI (receipt recorded separately once verified).

## Like you are two

Now there is a little shelf. You put something you learned on it, with a name, the words, and a note about where it came from. Later you type a word, and the shelf finds it again. The shelf only says where a thing came from — it never says “this is true and checked,” because the app didn’t go and check. Saving on the shelf never turns anything on and never starts a job.

## Like you are an adult

Division 17 adds `LocalKnowledgeItem`, `KnowledgeProvenance`, and `OfflineKnowledgeLibrary`, plus a **schema version 2** upgrade in `LocalEntityDatabase`.

## Schema decision — version 1 → 2

This division adds a genuinely new persisted entity type, so the schema had to change. It was not done casually:

| Fact | Detail |
| --- | --- |
| Previous version | 1 (`LocalAgentDraft`, `LocalActionEvent`) |
| New version | 2 (adds `LocalKnowledgeItem`) |
| Migration kind | Header upgrade only — no existing row shape changed |
| Old files still load? | **Yes**, proven by test: a hand-written version 1 file loads its draft and event intact |
| Upgrade timing | In place on `open()`, which normalises the header to version 2 |
| Unknown future version | Still rejected (a version 3 header fails closed) |

`OLDEST_READABLE_SCHEMA_VERSION = 1` is therefore part of the public contract, and `open()` accepts exactly the version 1 or version 2 header. The default **file name** remains `4cbon2-local-entities.v1.db` — the suffix names the file lineage, not the current schema, and renaming it would orphan existing app data. That mismatch is deliberate and documented here rather than silently “fixed”.

**Two existing tests changed** to reflect reality:

1. `freshDatabaseWritesTheVersionOneSchemaHeader` → renamed `freshDatabaseWritesTheCurrentSchemaHeader`, now asserting header version 2 and `OLDEST_READABLE_SCHEMA_VERSION == 1`.
2. `unsupportedSchemaAndMalformedRowsAreRejected` previously used a version **2** header as the “unsupported” example; that is now a valid header, so it uses version **3**. Its second case (legacy version 1 header with a malformed row) still proves that readable doesn’t mean tolerant.

The test count was preserved, not reduced: both tests still exist and still assert rejection behaviour.

## Entity

| Field | Bound |
| --- | --- |
| `id` | 1–64 of `A-Za-z0-9._:-` |
| `title` | 120 characters, non-blank, no control separators |
| `body` | 4,000 characters, may be empty, no control separators |
| `sourceLabel` | 120 characters, non-blank, no control separators |
| `provenance` | `OWNER_SUPPLIED`, `OWNER_OBSERVED`, `LOCAL_DRAFT_DERIVED` |
| `savedAtEpochMillis` | positive |

## Provenance is recorded, not inferred

There is deliberately **no** `SERVICE_RESPONSE`, `MODEL_OUTPUT`, `CITATION`, `VERIFIED`, or `TRUSTED` value, and a test asserts those names are absent. This division has no network access, so it cannot honestly claim a service response or a citation it never received. An item states where it came from and when it was saved — nothing more.

## Library operations

| Operation | Behaviour |
| --- | --- |
| `saveItem(...)` | Refuses `DUPLICATE_ITEM_ID` rather than overwriting a saved report |
| `loadItem(id)` | Returns `Found` or `Refused(UNKNOWN_ITEM)` |
| `allItems()` | Oldest first, identifier as stable tie-break |
| `search(query, limit)` | Case-insensitive containment over title, body, and source label only |
| `saveFromDraft(draftId, itemId, sourceLabel)` | Records `LOCAL_DRAFT_DERIVED`; refuses an unknown draft |

Search purity guarantees:

- The **identifier is not searched**, because it is not owner-visible text.
- Ordering is deterministic: saved time, then identifier.
- `limit` is bounded to 1–50 (default 20) and `query` to 120 characters; out-of-range values throw.
- `totalMatches` and `truncated` make a capped result **visible** instead of silently misleading.

## Inertness

The library holds a database and a clock, nothing else. A test proves that saving an item with an empty registry and a `STOPPED` lease changes neither: no app becomes selected, and the lease remains terminated. A saved item is text the owner can find again. It is not an authorization to act.

## Deliberate boundaries

- **No UI wiring:** nothing on screen lists or searches the shelf yet.
- **No encryption:** values are Base64-encoded, not encrypted; sensitive text must not be saved.
- **No network, cloud sync, credential, account, or live service.**
- **No claim** that any saved item is true, current, or externally verified.
- Production signing, device verification, and universal phone control remain unclaimed.

## Tests

Two new files add 16 tests, and 2 existing database tests were updated:

| File | Tests | Coverage |
| --- | --- | --- |
| `LocalKnowledgeItemTest` | 6 | Bounded values and empty body; identifier/timestamp rejection; text bounds and control separators; provenance values contain no invented origins; case-insensitive matching over visible fields only; search-bound sanity |
| `OfflineKnowledgeLibraryTest` | 10 | Close/reopen persistence with no cleartext body on disk; duplicate refusal; unknown item; deterministic case-insensitive search; capped result with visible truncation; out-of-range query/limit; **legacy v1 file read and upgraded to v2**; draft-derived provenance; inertness; knowledge rows serialise as their own entity type |

The active suite is **119** (`103` preserved + `16` new). The CI source-inventory guard, step label, notice, and exact XML aggregation assertion are aligned to 119; none was weakened.

## Local checks

- Active `@Test` source count: **119**.
- New runtime dependency count: **0**.
- Schema version: **2**; oldest readable: **1**.
- Local Android JDK/Gradle/Android SDK toolchain: **absent**; no local Gradle compilation, APK assembly, alignment, or signature is claimed.
- Isolated Kotlin 2.4.21 / JVM 17 type-check: **passed** (not a project Gradle build).
- Reflective standalone JVM run with a stub JUnit harness: **20 passed, 0 failed** for the database/knowledge classes, and **98 passed, 0 failed** across all pure-Kotlin classes (`STANDALONE_TEST_RUN passed=98 failed=0`). Not a Gradle, emulator, or device test.
- Test-side defects found and fixed during implementation: one assertion expected 50 items back from an empty library (it conflated the *cap* with the *count*); the stub `assertThrows` lacked JUnit’s message overload. Both were harness/test issues — production behaviour was not changed to suit them.

## Approved no-artifact CI verification

Pending owner approval of the source push and a successful workflow run that records:

- compilation and all 119 unit tests with zero failures/errors/skips;
- fresh debug APK assembly;
- zip alignment and APK Signature Scheme v2 verification;
- SHA-256;
- clean-source check;
- zero uploaded artifacts;
- no historical 09c device-APK artifact marker in the commit message.
