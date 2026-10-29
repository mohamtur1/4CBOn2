# 4CBOn2 — New-Thread Handoff for Division 14

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 29 October 2026
**Verified Division 13 commit:** `4d18e2bf4cb39d0e014fb434a5aad1cdb1a6ea46`
**Next division:** 14 — Takeover and cancellation lease
**Do not start:** Division 14 without an explicit “Continue”, Division 15 or later, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

The app now writes a promise sheet before a job starts. It says which helper apps may help, what each may do, how many steps are allowed, and when the sheet expires. If a step is not on the sheet, the answer is “no.” The big robot checked 55 little things and all passed, and it did not keep any big app file. The next job is the “stop” button: you can take over, pause, or stop at any time, and old leftover instructions must not sneak through. We are not doing that yet.

## Like you are an adult

Division 13 is complete for its documented source/build scope. A bounded, local scope contract now exists with hard owner-started caps (20 actions, 5 minutes), opt-in cloud transfer, derived expiry, explicit exclusions, and eight fail-closed refusal causes decided before any capability discovery. The 40 earlier tests remain intact; 15 new scope tests bring the active total to 55.

The no-artifact CI run [40831655312](https://github.com/mohamtur1/4CBOn2/actions/runs/40831655312) succeeded at commit `4d18e2bf4cb39d0e014fb434a5aad1cdb1a6ea46`: 55 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `d3cba101119afe0364767f4aceb0ee2bd32ff7e1c01072b0a979b635a37890e9`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test. See `4CBOn2_Division13_Verification.md` and `android/SCOPE_CONTRACT.md`.

Arena fixes this session to `arena/74f65509-4cbon2`, so work continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `4d18e2bf4cb39d0e014fb434a5aad1cdb1a6ea46` |
| Final Division 13 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/40831655312 — **Success** |
| Unit tests | **55 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, APK Signature Scheme v2 verified |
| Division 13 APK SHA-256 | `d3cba101119afe0364767f4aceb0ee2bd32ff7e1c01072b0a979b635a37890e9` |
| CI artifacts | **Zero** |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device test, no permission-granted or accessibility flow, no production signing, no Play approval, no universal phone control |

The historical 09c APK artifact and earlier 21-/25-/40-test CI records are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 14 narrow work order — do not begin yet

When the owner explicitly says “Continue”, start only Division 14 — Takeover and cancellation lease:

1. Define a local Take Over / Pause / Stop lease independent of any cloud planner, so cancellation never depends on a remote service.
2. Reject stale or late commands: a command must carry a monotonically increasing lease epoch (or equivalent), and anything from an older epoch must be refused with an explicit reason rather than applied.
3. Require explicit resume after a takeover or stop, plus a fresh state check before any new step; a resumed job must not silently inherit prior permissions beyond its `ScopeContract`.
4. Keep the lease bounded and synthetic in tests; no new dependency, no UI wiring, no permission, no network call.
5. Preserve the 55 existing tests and update the CI exact total honestly if new tests are added.
6. Require full no-artifact CI verification before closure, then update the plan/recovery documents.
7. Stop before Division 15.

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
4CBOn2_Division13_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
android/SCOPE_CONTRACT.md
android/SELECTED_APP_CAPABILITIES.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/ActionScopeContract.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/ActionScopeEnforcer.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source. A local Git-metadata reset has now recurred before each division: if local `HEAD` returns to `ed169b6` while newer workspace files remain, take a working-tree snapshot, compare byte-for-byte against the published branch head, use only a mixed reset, and never hard-reset. Verify the approved commit ID still exists immediately before pushing, and request fresh exact-step approval if it does not.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 14 — Takeover and cancellation lease when explicitly asked, and do not skip ahead. Preserve the existing 55 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
