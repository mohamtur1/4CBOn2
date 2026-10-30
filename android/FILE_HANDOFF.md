# Division 16 — Safe file import/export

**Work branch:** `arena/74f65509-4cbon2` (the fixed Arena session branch; source baseline imported from `arena/0eb49904-4cbon2` at `2a45201`)
**Scope:** A bounded, owner-picked file model with explicit refusals, a size cap applied to reported metadata, and an export *plan* that produces no content.
**Evidence class:** Source/static checks plus JVM unit tests, followed by no-artifact CI (receipt recorded separately once verified).

## Like you are two

You pick the file yourself. The app never goes looking through your phone on its own. Before it says yes, it peeks at the **label** — the name, the type, and how big the file says it is — and nothing else. Too big? No. Wrong kind? No. Nobody picked anything? No. It says “no” with one clear reason, never a guess. And when you ask to send something out, it only writes a little plan. It does not make the file yet.

## Like you are an adult

Division 16 adds a pure-Kotlin value layer for owner-initiated file handoff. There is **no content access anywhere in it**: no byte array, no stream, no reader, no writer, no `java.io.File`, no path. That is enforced by a reflection test rather than by promise, so "the size cap is checked before content is read" holds structurally — there is no read step to order against.

The platform picker is **not wired**. This division defines the boundary a picker would later call; claiming a working picker would be dishonest, and the work order explicitly separates that step.

## Import model

| Type | Role |
| --- | --- |
| `OwnerFileDescriptor` | Untrusted raw values as a picker would report them: label, MIME type, size, picked-at |
| `OwnerFileMetadata` | The same values after validation; the only thing a decision may carry |
| `FileIntakePolicy` | Exact-match MIME allow-list plus a size cap, hard-bounded at 5 MiB |
| `OwnerFileSource` | Narrow `describe(handle)` boundary; default returns `null` |
| `FileIntakeDecision` | `Accepted(metadata, purpose)` or `Refused(reason, purpose)` |

Platform data is treated as untrusted: `OwnerFileDescriptor` is unvalidated, and `OwnerFileMetadata.from(...)` is the only gate. Invalid platform values become `INVALID_METADATA` rather than an exception escaping to the caller.

## Refusal order (fail-closed)

| # | Check | Refusal |
| --- | --- | --- |
| 1 | Handle blank or longer than 2048 characters | `UNSELECTED` |
| 2 | Source returns nothing, or throws | `MISSING` |
| 3 | Platform values fail validation | `INVALID_METADATA` |
| 4 | Reported size is zero | `EMPTY_FILE` |
| 5 | Reported size exceeds the cap | `TOO_LARGE` |
| 6 | MIME type not in the exact allow-list | `TYPE_NOT_ALLOWED` |

Size is checked **before** type, so an oversized file of a disallowed type reports the size reason first — the cheaper, more resource-protective refusal wins. A source that throws is treated as missing, never as permission. No app is consulted at all for steps 1–2.

A refusal deliberately does **not** echo the handle back, because a platform handle can reveal a filename or path fragment and a refusal record is the kind of thing that gets logged.

Matching is exact: `text/*`, `*/*`, `TEXT/PLAIN`, and `text/plain;charset=utf-8` are all refused. There is no prefix match and no fallback.

## Export model

| Type | Role |
| --- | --- |
| `OwnerExportRequest` | Draft ID, label, purpose, and `ownerConfirmedAtEpochMillis` — null means not confirmed |
| `FileExportPlanner` | Refuses `NOT_OWNER_CONFIRMED` (without any lookup) or `DRAFT_UNKNOWN` |
| `ExportPlan` | A description of what *would* be written |
| `ExportDecision` | `Planned(plan)` or `Refused(reason)` |

An unconfirmed request is refused before the draft is even looked up, so an unconfirmed export cannot cause a read of any kind. Producing actual bytes is explicitly left to a later, separately scoped division.

## Schema decision

**Reused, unchanged.** No persisted field was needed: an accepted file is described by the same bounded label/type/size values already expressible in the existing entities, and an export plan is ephemeral. `LocalEntityDatabase` stays at **schema version 1** with no migration, and this division does not touch `LocalEntityDatabase.kt` or `LocalEntities.kt`.

## Deliberate boundaries

- **No picker wiring:** no `ActivityResultContracts`, no `content://` resolution, no `ContentResolver` call.
- **No storage permission**, and in particular no `MANAGE_EXTERNAL_STORAGE`, no broad media permission, and no permission request of any kind.
- **No directory walking, media-store scanning, or filesystem enumeration.**
- **No file content** is read, logged, copied, or transmitted; no cloud upload exists.
- **No UI wiring and no persistence.**
- **No claim** of device verification, production signing, or universal phone control.

## Tests

`OwnerFileIntakeTest` adds 17 synthetic tests:

| Area | Tests | Coverage |
| --- | --- | --- |
| Metadata validation | 2 | Accepted bounded values and round-trip through the descriptor; rejection of blank/control/over-long labels, malformed MIME types, negative sizes, non-positive timestamps |
| Policy | 1 | Empty allow-list, wildcards, mixed case, parameterised types, zero and oversized caps |
| Import refusals | 6 | Unselected without consulting the source; missing descriptor; throwing source; invalid platform metadata; empty file; oversized from reported size alone |
| Ordering | 1 | Size reported before type for a file that is both too large and disallowed |
| Acceptance | 2 | Accepted values with declared purpose; inclusive size boundary and exactly one `describe` call |
| Structural | 1 | Reflection proof that no class in the layer exposes byte, stream, reader, writer, file, or path access |
| Export | 4 | Request validation and confirmation default; unconfirmed refused with no lookup; unknown draft; confirmed plan without content |

The active suite is **103** (`86` preserved + `17` new). The CI source-inventory guard, step label, notice, and exact XML aggregation assertion are aligned to 103; none was weakened.

## Local checks

- Active `@Test` source count: **103**.
- New runtime dependency count: **0**.
- Schema version: **1**, unchanged; `LocalEntityDatabase.kt` and `LocalEntities.kt` untouched.
- Local Android JDK/Gradle/Android SDK toolchain: **absent**; no local Gradle compilation, APK assembly, alignment, or signature is claimed.
- Isolated Kotlin 2.4.21 / JVM 17 type-check: **passed** (not a project Gradle build).
- Reflective standalone JVM run with a stub JUnit harness: **17 passed, 0 failed** for Division 16, and **82 passed, 0 failed** across the Division 11–16 pure-Kotlin classes (`STANDALONE_TEST_RUN passed=82 failed=0`). Not a Gradle, emulator, or device test.
- Test-side defect found and fixed during implementation: one assertion compared metadata built with 1024 bytes against a descriptor whose default is 2048. The production code was correct; the test was wrong and was corrected rather than the source being bent to match it.

## Approved no-artifact CI verification

Pending owner approval of the source push and a successful workflow run that records:

- compilation and all 103 unit tests with zero failures/errors/skips;
- fresh debug APK assembly;
- zip alignment and APK Signature Scheme v2 verification;
- SHA-256;
- clean-source check;
- zero uploaded artifacts;
- no historical 09c device-APK artifact marker in the commit message.
