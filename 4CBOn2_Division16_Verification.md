# 4CBOn2 — Division 16 verification receipt

**Date:** 30 October 2026
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Verified source commit:** `47c5b167ca1130b19f830fc88424c3b325a6e328`
**Verified source tree:** `f49cd8b67c82541422fb5d85f95524e2a6b6f38c`
**CI run:** https://github.com/mohamtur1/4CBOn2/actions/runs/40990799099
**CI conclusion:** Success, no uploaded artifacts

> Arena fixes this session to `arena/74f65509-4cbon2`. Division 16 continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Like you are two

You pick the file yourself, and the app never goes looking through your phone on its own. It peeks only at the label — the name, the type, and how big the file says it is. Too big? No. Wrong kind? No. Nothing picked? No. It says “no” with one clear reason, never a guess. It never looks inside your file, and when you ask to send something out it only writes a little plan. The big robot checked 103 little things and all of them passed, and it did not keep any big app file.

## Like you are an adult

Division 16 added a bounded, pure-Kotlin owner-picked file model with an exact-match MIME allow-list, a 5 MiB cap applied to reported metadata, six fail-closed refusal reasons, and a plan-only export side. Seventeen synthetic JVM tests were added to the 86 preserved tests, bringing the active suite to 103; the CI inventory guard, step label, notice, and exact XML assertion were all raised to 103 without weakening.

The verified workflow compiled the Android source, ran all 103 unit tests, assembled a debug APK, checked zip alignment and APK Signature Scheme v2, recorded the APK SHA-256, confirmed the build did not modify source, skipped the historical 09c artifact-retention step, and uploaded zero artifacts. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test.

## CI evidence

| Check | Actual result |
| --- | --- |
| Workflow | `Android Division 09b Verification` |
| Run | `40990799099` (push to `arena/74f65509-4cbon2`) |
| Conclusion | **Success** in 2m42s |
| Verified commit | `47c5b167ca1130b19f830fc88424c3b325a6e328` |
| Verified tree | `f49cd8b67c82541422fb5d85f95524e2a6b6f38c` |
| Active source test inventory | 103 |
| JUnit aggregation | 103 passed; 0 failures; 0 errors; 0 skipped |
| Debug APK assembly | Passed |
| Zip alignment | Passed |
| APK Signature Scheme v2 | Passed |
| Debug APK SHA-256 | `2607e0016380fe7f67fbe73d7fe3d6d25774f20e36f082cdc3fee85b2207c533` |
| Build did not modify source | Passed |
| Artifact-retention step | Skipped |
| CI artifact count | `0` |
| Historical 09c artifact marker in commit message | Absent |
| Credentials, secrets, live accounts, PR, or merge | None used; none opened |

The artifact count was read back from the GitHub Actions run-artifacts API. The APK was ephemeral CI output: not uploaded, retained, committed, installed, or production-signed.

## Division 16 scope as verified

- `OwnerFileIntake.kt` — `OwnerFileDescriptor` (untrusted picker values), `OwnerFileMetadata` (validated), `FileIntakePolicy`, `OwnerFileSource`, six `FileRefusal` causes, and the `OwnerExportRequest` / `FileExportPlanner` / `ExportPlan` pair.
- `OwnerFileIntakeTest` — 17 synthetic tests, including a reflection test over the whole layer.

Safety properties actually enforced in code:

- **No content access exists at all**: no `ByteArray`, `InputStream`, `OutputStream`, `Reader`, `Writer`, `java.io.File`, or `java.nio.file.Path` appears in any class of the layer, asserted by reflection rather than asserted in prose.
- Refusal order: `UNSELECTED` → `MISSING` → `INVALID_METADATA` → `EMPTY_FILE` → `TOO_LARGE` → `TYPE_NOT_ALLOWED`, with size deliberately checked before type.
- A blank or over-long handle is refused **without consulting the source at all**; a source that throws is treated as missing, never as permission.
- MIME matching is exact: `text/*`, `*/*`, `TEXT/PLAIN`, and `text/plain;charset=utf-8` are all refused.
- A refusal does not echo the handle back, because a platform handle can reveal a filename or path fragment.
- An unconfirmed export is refused **before** any draft lookup, so it cannot cause a read of any kind; a confirmed export yields an `ExportPlan` description, not bytes.
- `LocalEntityDatabase` stays at schema version **1**; `LocalEntityDatabase.kt` and `LocalEntities.kt` were not modified.
- No storage permission, no `MANAGE_EXTERNAL_STORAGE`, no directory walking, no media scanning, no picker wiring, no UI, and no cloud upload. The manifest still declares only `INTERNET`.

## Development note

One test failed during implementation: an assertion compared metadata built with 1024 bytes against a descriptor whose default is 2048. The production code was correct; the test was wrong and was corrected rather than bending the source to match it. A hygiene scan also flagged the string `MANAGE_EXTERNAL_STORAGE`; it was verified to occur only in the documentation sentence stating that the permission is *not* used.

## Document status

`android/FILE_HANDOFF.md` still contains a “Pending” CI section because this follow-up commit is restricted to root Markdown. This receipt is the authoritative supersession of that section; no Android source or workflow file changed after the CI-verified commit.

## Boundaries

- No emulator, real-device, permission-granted, production-signing, Play Store, or live-service claim.
- No platform picker is wired; this division defines the boundary a picker would later call.
- Producing export bytes is deliberately deferred to a later, separately scoped division.
- Division 17 — Offline knowledge library has not started.
