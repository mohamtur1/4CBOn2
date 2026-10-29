# 4CBOn2 — New-Thread Handoff for Division 13

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 29 October 2026
**Verified Division 12 commit:** `75564a83ad2f4e2ada66a87ffc686cb81f0e96bd`
**Next division:** 13 — On-device scope contract
**Do not start:** Division 13 without an explicit “Continue”, Division 14 or later, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

The app now keeps a short list of helpers you picked yourself, and it checks the gentlest way first. If nobody can do it, it says “can’t do that” instead of guessing. The big robot builder checked 40 little things and all passed, and it did not keep any big app file. The next job is a promise sheet: what one job may do, for how long, and who it may talk to. We are not doing that yet.

## Like you are an adult

Division 12 is complete for its documented source/build scope. A dependency-free, owner-selected capability registry now exists with an action-level routing model, explicit unavailable states, and a narrow per-app probe boundary that never enumerates installed apps. The 25 Division 11 tests remain intact; 15 new capability tests bring the active total to 40.

The no-artifact CI run [40812912554](https://github.com/mohamtur1/4CBOn2/actions/runs/40812912554) succeeded at commit `75564a83ad2f4e2ada66a87ffc686cb81f0e96bd`: 40 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `3f2597d7413b3b805436189f06e72ce5432d263d9b4602d01e412e9d217dc6e7`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test. See `4CBOn2_Division12_Verification.md` and `android/SELECTED_APP_CAPABILITIES.md`.

Arena fixed this session to `arena/74f65509-4cbon2`, so work continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `75564a83ad2f4e2ada66a87ffc686cb81f0e96bd` |
| Final Division 12 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/40812912554 — **Success** |
| Unit tests | **40 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, APK Signature Scheme v2 verified |
| Division 12 APK SHA-256 | `3f2597d7413b3b805436189f06e72ce5432d263d9b4602d01e412e9d217dc6e7` |
| CI artifacts | **Zero** |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device test, no permission-granted or accessibility flow, no production signing, no Play approval, no universal phone control |

The historical 09c APK artifact and earlier 21-/25-test CI records are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 13 narrow work order — do not begin yet

When the owner explicitly says “Continue”, start only Division 13 — On-device scope contract:

1. Define a minimal **local** scope contract for one job: goal, selected apps and accounts, allowed data/action/recipient/file bounds, cloud-transfer permission (default off), completion mode, action/time limits, expiry, and explicit exclusions.
2. Deny unselected targets and unauthorized cloud transfer by construction; fail closed with explicit, testable refusal reasons.
3. Keep it dependency-free and synthetic in tests; do not read real accounts, contacts, files, notifications, or installed-app lists.
4. Reuse the Division 12 registry model rather than duplicating route or sensitivity rules; keep manual-only workflows manual-only.
5. Preserve the 40 existing tests and update the CI exact total honestly if new tests are added.
6. Require full no-artifact CI verification before closure, then update the plan/recovery documents.
7. Stop before Division 14.

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
4CBOn2_Division12_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
4CBOn2_GitHub_Scoped_Handoff.md
android/SELECTED_APP_CAPABILITIES.md
android/LOCAL_DATABASE.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/SelectedAppCapability.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/SelectedAppCapabilityRegistry.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source. If local `HEAD` again returns to `ed169b6` while newer workspace files remain, preserve the working tree, compare byte-for-byte against the published branch baseline, use only a mixed reset, and never hard-reset or discard local work. An approval pause can reset local Git metadata; verify the approved commit ID still exists before pushing, and request fresh exact-step approval if it does not.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 13 — On-device scope contract when explicitly asked, and do not skip ahead. Preserve the existing 40 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
