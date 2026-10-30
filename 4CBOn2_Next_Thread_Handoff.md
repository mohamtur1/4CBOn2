# 4CBOn2 — New-Thread Handoff for Division 15

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 30 October 2026
**Verified Division 14 commit:** `8ae74d28ee6673bc7926392c75df722051cecb5b`
**Next division:** 15 — Offline agent drafts
**Do not start:** Division 15 without an explicit “Continue”, Division 16 or later, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

The app now has a big red stop button that works right away, without asking the internet. If a helper tries to finish an old job after you press stop, the app throws that old answer away. You must press start again, and the app must look around first. The big robot checked 71 little things and all passed, and it did not keep any big app file. The next job is saving your drafts so you can write a goal, close the app, and find it again. We are not doing that yet.

## Like you are an adult

Division 14 is complete for its documented source/build scope. A local control lease and controller now provide owner-only resume, monotonic epoch binding, terminal stop, a fresh post-resume state check, and lease-before-scope ordering, so cancellation never depends on a cloud planner and a paused job performs no capability discovery. The 55 earlier tests remain intact; 16 new tests bring the active total to 71.

The no-artifact CI run [40987374233](https://github.com/mohamtur1/4CBOn2/actions/runs/40987374233) succeeded at commit `8ae74d28ee6673bc7926392c75df722051cecb5b`: 71 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `c9504f2bfc8d513cda7e44ab9d1d8f3d856fc3fa008e0ebf1195f5f77d2c268d`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, authenticated, or live-service test. See `4CBOn2_Division14_Verification.md` and `android/TAKEOVER_LEASE.md`.

Arena fixes this session to `arena/74f65509-4cbon2`, so work continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `8ae74d28ee6673bc7926392c75df722051cecb5b` |
| Final Division 14 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/40987374233 — **Success** |
| Unit tests | **71 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, APK Signature Scheme v2 verified |
| Division 14 APK SHA-256 | `c9504f2bfc8d513cda7e44ab9d1d8f3d856fc3fa008e0ebf1195f5f77d2c268d` |
| CI artifacts | **Zero** |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device test, no permission-granted or accessibility flow, no production signing, no Play approval, no authentication proof, no universal phone control |

The historical 09c APK artifact and earlier 21-/25-/40-/55-test CI records are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 15 narrow work order — do not begin yet

When the owner explicitly says “Continue”, start only Division 15 — Offline agent drafts:

1. Let the owner create, edit, list, and load a local draft with a goal, template name, and draft state, using the existing `LocalAgentDraft` entity rather than inventing a parallel model.
2. Persist drafts through the existing `LocalEntityDatabase`; do not add a new storage engine, schema version, or dependency. If an entity must change, treat it as an explicit schema decision and document the migration honestly.
3. Keep the job-profile and cap fields bounded and local; no account, credential, service response, or cloud-sync field may enter a draft.
4. Keep drafts inert: saving a draft must not grant permission, start a job, or bypass `ScopeContract`, `ActionLease`, or the capability registry.
5. Use synthetic local data in tests; no live account, real personal document, network call, or external write.
6. Preserve the 71 existing tests and update the CI exact total honestly if new tests are added.
7. Require full no-artifact CI verification before closure, then update the plan/recovery documents.
8. Stop before Division 16.

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
4CBOn2_Division14_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
android/TAKEOVER_LEASE.md
android/SCOPE_CONTRACT.md
android/LOCAL_DATABASE.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/ActionLease.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/LocalEntityDatabase.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source. A local Git-metadata reset has now recurred **three times** before a division starts, twice destroying local commits entirely. If local `HEAD` returns to `ed169b6` while newer workspace files remain: take a working-tree snapshot first, compare byte-for-byte against the published branch head, use only `git reset --mixed`, and never hard-reset. Always verify the approved commit ID still exists immediately before pushing, and request fresh exact-step approval if it does not. A rebuilt tree that reproduces the approved tree hash is byte-identical content and is strong evidence, but it is still a new commit ID that needs its own approval.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 15 — Offline agent drafts when explicitly asked, and do not skip ahead. Preserve the existing 71 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
