# 4CBOn2 — Division 17 verification receipt

**Date:** 30 October 2026
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Verified source commit:** `264e854feb58fbc86ced5d513bc16eb9e6148b51`
**Verified source tree:** `186228850a73f1d1a5620b0245747e6bf582aa70`
**CI run:** https://github.com/mohamtur1/4CBOn2/actions/runs/40991856148
**CI conclusion:** Success, no uploaded artifacts

> Arena fixes this session to `arena/74f65509-4cbon2`. Division 17 continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Like you are two

Now there is a little shelf. You put something you learned on it, with a name, the words, and a note about where it came from. Later you type a word, and the shelf finds it again — even if you use different capital letters. The shelf only says *where* a thing came from. It never says “this is true and checked,” because the app didn’t go and check. The big robot checked 119 little things and all of them passed, and it did not keep any big app file.

## Like you are an adult

Division 17 added a local knowledge shelf with recorded provenance, bounded text, duplicate-save refusal, and bounded case-insensitive search with visible truncation. Persisting a new entity type required a real schema change, which was handled explicitly rather than quietly: the local database moved from version 1 to **version 2**, with version 1 files still readable and upgraded in place.

Sixteen synthetic JVM tests were added to the 103 preserved tests, bringing the active suite to 119; the CI inventory guard, step label, notice, and exact XML assertion were all raised to 119 without weakening.

The verified workflow compiled the Android source, ran all 119 unit tests, assembled a debug APK, checked zip alignment and APK Signature Scheme v2, recorded the APK SHA-256, confirmed the build did not modify source, skipped the historical 09c artifact-retention step, and uploaded zero artifacts. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test.

## CI evidence

| Check | Actual result |
| --- | --- |
| Workflow | `Android Division 09b Verification` |
| Run | `40991856148` (push to `arena/74f65509-4cbon2`) |
| Conclusion | **Success** in 2m40s |
| Verified commit | `264e854feb58fbc86ced5d513bc16eb9e6148b51` |
| Verified tree | `186228850a73f1d1a5620b0245747e6bf582aa70` |
| Active source test inventory | 119 |
| JUnit aggregation | 119 passed; 0 failures; 0 errors; 0 skipped |
| Debug APK assembly | Passed |
| Zip alignment | Passed |
| APK Signature Scheme v2 | Passed |
| Debug APK SHA-256 | `1d5bf6f1d59b484b0105dd7d0f103f9ff7902f95b8334bf24d9ddaffb1ae7738` |
| Build did not modify source | Passed |
| Artifact-retention step | Skipped |
| CI artifact count | `0` |
| Historical 09c artifact marker in commit message | Absent |
| Credentials, secrets, live accounts, PR, or merge | None used; none opened |

The artifact count was read back from the GitHub Actions run-artifacts API. The APK was ephemeral CI output: not uploaded, retained, committed, installed, or production-signed.

## Schema decision — version 1 to 2

This is the first division to change the persisted format, so the decision is recorded in full.

| Fact | Detail |
| --- | --- |
| Previous version | 1 (`LocalAgentDraft`, `LocalActionEvent`) |
| New version | 2 (adds `LocalKnowledgeItem`) |
| Migration kind | Header upgrade only — no existing row shape changed |
| Old files still load | **Yes**, proven by a hand-written version 1 file loading its draft and event intact |
| Upgrade timing | In place on `open()`, which normalises the header to version 2 |
| Unknown future version | Still rejected — a version 3 header fails closed |
| Oldest readable | `OLDEST_READABLE_SCHEMA_VERSION = 1`, now part of the public contract |
| File name | Deliberately still `4cbon2-local-entities.v1.db`; it names the file lineage, not the schema, and renaming it would orphan existing app data |

**Two existing tests were updated honestly rather than deleted or weakened:**

1. `freshDatabaseWritesTheVersionOneSchemaHeader` was renamed to `freshDatabaseWritesTheCurrentSchemaHeader` and now asserts header version 2 plus `OLDEST_READABLE_SCHEMA_VERSION == 1`.
2. `unsupportedSchemaAndMalformedRowsAreRejected` previously used a version **2** header as its "unsupported" example; version 2 is now valid, so it uses version **3**. Its second case — a legacy version 1 header carrying a malformed row — is retained and still proves that readable is not the same as tolerant.

## Division 17 scope as verified

- `LocalKnowledgeEntities.kt` — `LocalKnowledgeItem` with bounded fields, `KnowledgeProvenance`, and `KnowledgeSearchBounds`.
- `OfflineKnowledgeLibrary.kt` — save, load, list, bounded search, and draft-derived save.
- `LocalEntityDatabase.kt` — schema version 2, knowledge rows, and version 1 read compatibility.
- `LocalKnowledgeItemTest` (6 tests) and `OfflineKnowledgeLibraryTest` (10 tests) — synthetic data only.

Safety properties actually enforced in code:

- Provenance is recorded, not inferred: only `OWNER_SUPPLIED`, `OWNER_OBSERVED`, and `LOCAL_DRAFT_DERIVED` exist. There is no `SERVICE_RESPONSE`, `MODEL_OUTPUT`, `CITATION`, `VERIFIED`, or `TRUSTED` value, and a test asserts those names are absent. Nothing asserts an item is true, current, or externally verified.
- A duplicate save is refused rather than overwriting an existing saved report.
- Search is case-insensitive over title, body, and source label only — the identifier is not searched because it is not owner-visible text.
- Ordering is deterministic (saved time, then identifier); the result cap is bounded to 1–50 and the query to 120 characters; `totalMatches` and `truncated` make a capped result visible rather than silently misleading.
- The library holds only a database and a clock. A test proves saving an item with an empty registry and a `STOPPED` lease changes neither.
- No UI wiring, encryption, network call, credential, account, or live service was added. Values are Base64-encoded, not encrypted, so sensitive text must not be saved.

## Development note

One test failed during implementation: an assertion expected 50 items back from an empty library, conflating the search *cap* with the returned *count*. The production code was correct; the test was wrong and was corrected. The local stub `assertThrows` also lacked JUnit's message overload; the harness was extended rather than changing production code to suit it.

## Document status

`android/KNOWLEDGE_LIBRARY.md` still contains a “Pending” CI section because this follow-up commit is restricted to root Markdown. This receipt is the authoritative supersession of that section; no Android source or workflow file changed after the CI-verified commit.

## Boundaries

- No emulator, real-device, permission-granted, production-signing, Play Store, or live-service claim.
- No encryption of the local database.
- Division 18 — Small local analysis has not started.
