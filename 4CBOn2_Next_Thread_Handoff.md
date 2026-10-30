# 4CBOn2 — New-Thread Handoff for Division 16

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 30 October 2026
**Verified Division 15 commit:** `dd09caf00fe2238530748fa3c6237235bf0748b1`
**Next division:** 16 — Safe file import/export
**Do not start:** Division 16 without an explicit “Continue”, Division 17 or later, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

The app now has a little notebook where you can write a plan and find it again later. You can change it, or cross it out for good. Writing a plan never turns anything on and never starts a job. The big robot checked 86 little things and all passed, and it did not keep any big app file. The next job is letting you pick a file yourself — the app should never go looking through your phone on its own. We are not doing that yet.

## Like you are an adult

Division 15 is complete for its documented source/build scope. A bounded create/edit/list/load layer now sits over the existing local entity database at unchanged schema version 1, with an explicit draft state machine, terminal cancellation, paired events, and structural inertness toward the registry, scope, and lease. The 71 earlier tests remain intact; 15 new tests bring the active total to 86.

The no-artifact CI run [40989542029](https://github.com/mohamtur1/4CBOn2/actions/runs/40989542029) succeeded at commit `dd09caf00fe2238530748fa3c6237235bf0748b1`: 86 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `2c79ed9de20b2cf7ed95a942c31c824be40a42b6aa63a7eaea44102873d3f347`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test. See `4CBOn2_Division15_Verification.md` and `android/OFFLINE_DRAFTS.md`.

Arena fixes this session to `arena/74f65509-4cbon2`, so work continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `dd09caf00fe2238530748fa3c6237235bf0748b1` |
| Final Division 15 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/40989542029 — **Success** |
| Unit tests | **86 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, APK Signature Scheme v2 verified |
| Division 15 APK SHA-256 | `2c79ed9de20b2cf7ed95a942c31c824be40a42b6aa63a7eaea44102873d3f347` |
| CI artifacts | **Zero** |
| Database schema | **Version 1**, unchanged since Division 11 |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device test, no permission-granted or accessibility flow, no production signing, no Play approval, no authentication proof, no encryption of the local database, no universal phone control |

The historical 09c APK artifact and earlier 21-/25-/40-/55-/71-test CI records are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 16 narrow work order — do not begin yet

When the owner explicitly says “Continue”, start only Division 16 — Safe file import/export:

1. Model an **owner-picked** file: the owner chooses the document, and 4CBOn2 receives only that selection. No directory walking, no media-store scanning, no whole-filesystem access, and no `MANAGE_EXTERNAL_STORAGE` or broad-storage permission.
2. Keep the model a bounded, pure-Kotlin value layer (label, size and type limits, declared purpose) that is testable with synthetic data. Any platform picker wiring is a separate, explicitly scoped step; do not imply the picker exists before it does.
3. Reject oversized, wrong-type, missing, and unselected inputs with explicit refusal reasons. Prefer a size cap checked **before** reading content.
4. Never read, log, copy, or transmit file contents during this division. Provenance metadata only, and no cloud upload.
5. Do not modify the schema casually: reuse `LocalEntityDatabase` version 1 if the model fits. If a persisted field is genuinely needed, treat it as an explicit schema decision and document the migration honestly.
6. Preserve the 86 existing tests and update the CI exact total honestly if new tests are added.
7. Require full no-artifact CI verification before closure, then update the plan/recovery documents.
8. Stop before Division 17.

## Standing owner requirements

- Work in one maximum-30-minute division at a time, including waiting, verification, and checkpointing.
- Explain every progress/completion update in both **Like you are two** and **Like you are an adult** styles unless explicitly told otherwise.
- Before every work step, state whether upload is needed. For each external write/transfer, identify the exact destination, files/data, and reason and obtain explicit owner approval first.
- Never request, accept, display, or store passwords, tokens, cookies, authorization codes, or browser sessions.
- Do not ask design clarification questions; choose interaction design autonomously within documented safety boundaries.
- Preserve least-powerful supported routes and manual-only handling for sensitive phone workflows.
- Never claim universal phone/app control, production signing, Play Store approval, or live Kaggle joining/submission without actual evidence.
- No PR is required unless the owner separately asks and approves one.

## Files to read first in the next thread

```text
4CBOn2_Division15_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
android/OFFLINE_DRAFTS.md
android/LOCAL_DATABASE.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/OfflineDraftRepository.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/LocalEntityDatabase.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source. A local Git-metadata reset has now occurred **three times**, twice destroying commits that had not yet been pushed. If local `HEAD` returns to `ed169b6` while newer workspace files remain: take a working-tree snapshot first, compare byte-for-byte against the published branch head, use only `git reset --mixed`, and never hard-reset. Always verify the approved commit ID still exists immediately before pushing, and request fresh exact-step approval if it does not. A rebuilt tree that reproduces the approved tree hash is byte-identical content, but it is still a new commit ID that needs its own approval.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 16 — Safe file import/export when explicitly asked, and do not skip ahead. Preserve the existing 86 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
