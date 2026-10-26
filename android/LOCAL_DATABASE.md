# Division 11 — Local database

**Work branch:** `arena/74f65509-4cbon2` (the fixed Arena session branch; source baseline imported from `arena/0eb49904-4cbon2` at `2a45201`)
**Scope:** Initial local entities, file-backed access, and one real close/reopen persistence proof.
**Evidence class:** Source/static checks plus JVM unit tests and no-artifact CI (CI receipt recorded below once verified).

## Like you are two

The app now has a tiny memory box. We put a pretend draft and a pretend check mark inside, close the box, open a new box pointed at the same file, and the things are still there. This is a file on a test computer, not a real person's phone or account.

## Like you are an adult

Division 11 adds a dependency-free, versioned, file-backed entity store. `LocalEntityDatabase` opens a `RandomAccessFile`, validates a schema header, parses two fixed entity types, and flushes/syncs every mutation. `close()` closes the file and clears in-memory state. A second instance must reread the same file, which distinguishes restart persistence from in-memory state reuse.

The implementation deliberately does **not** add Room/SQL, cloud sync, account sessions, credentials, service-response bodies, screen capture, broad telemetry, UI wiring, or full agent execution. This is an initial local persistence boundary for later offline drafts and bounded local state. The file is not encrypted; sensitive values must never be stored in these entity fields.

## Initial schema

Schema version: `1`
Default file name: `4cbon2-local-entities.v1.db`

| Entity | Fields | Purpose |
| --- | --- | --- |
| `LocalAgentDraft` | stable ID, title, goal, template name, fixed draft state, created/updated epoch milliseconds | Minimal future offline draft identity and state |
| `LocalActionEvent` | stable ID, draft ID, fixed event kind, fixed outcome, short note, created epoch milliseconds | Minimal bounded local event tied to a draft |

Validation limits IDs, short text lengths, enum values, timestamps, duplicate IDs, schema version, Base64 row fields, and draft/event references. Text fields are URL-safe Base64 encoded in tab-separated rows; an empty note uses an explicit sentinel so row shape remains stable.

## Persistence/restart test

`LocalEntityDatabaseTest` uses a JUnit `TemporaryFolder` and synthetic records only. The central test:

1. Opens the database at a temporary file.
2. Writes one synthetic draft and one synthetic event.
3. Closes the first database instance.
4. Confirms the file exists and contains data.
5. Opens a new `LocalEntityDatabase` against the same file.
6. Reads back the exact draft/event and finds the draft by ID.

Three more tests verify the version header, corrupt/unsupported file rejection, closed-instance rejection, entity validation, and missing-draft event rejection. Four source tests were added; the active suite is **25** (`21` existing + `4` new). The test is a local JVM unit test, not an emulator or real-device restart test.

## Safety and scope boundaries

- No credential, token, cookie, password, account ID, phone identifier, owner file, live service, or network call is used.
- No APK, database file, or CI artifact is retained in source control.
- No UI currently writes to or reads from this database; this division is the persistence layer only.
- No encryption, production signing, general device compatibility, or real-device crash/reboot behavior is claimed.
- Existing manual Kaggle handoff behavior and the previous 21 tests remain unchanged.

## Local checks

- Active `@Test` source count: **25**.
- CI exact XML assertion, step label, notice, and source-inventory assertion: **25**.
- New dependency count: **0**.
- Local Android JDK/Gradle/SDK toolchain: **absent**; no local Gradle compilation, APK assembly, alignment, or signature is claimed.
- Isolated Kotlin 2.4.21 compiler/JVM 17 syntax and type check of the new source/test files: **passed** (not a project Gradle build).
- Standalone JVM close/reopen harness against the same `LocalEntityDatabase` and a real temporary file: **passed** (`STANDALONE_FILE_BACKED_REOPEN=PASS`). It is not a JUnit, emulator, or real-device test.
- Whitespace, forbidden artifact, credential-pattern, YAML, and consistency checks: recorded in the division commit/CI verification.

## Approved no-artifact CI verification

Pending. This section will be updated only after a real successful workflow run with:

- compilation and all 25 unit tests with zero failures/errors/skips;
- fresh debug APK assembly;
- zip alignment and APK Signature Scheme v2 verification;
- SHA-256;
- clean-source check;
- zero uploaded artifacts;
- no historical 09c device-APK artifact marker in the commit message.
