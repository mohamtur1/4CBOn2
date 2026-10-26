# 4CBOn2 — New-Thread Handoff for Division 12

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 26 October 2026
**Verified Division 11 commit:** `eef1a18d671db2747ea5d4ca7c739e696079377d`
**Next division:** 12 — Selected-app capability registry
**Do not start:** Division 12 or later work, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

The app now has a tiny memory box. We saved a pretend note, closed the box, opened it again, and the note was still there. The robot builder checked 25 little things and all passed. It did not keep any big app file. The next job is to make a careful list of which apps are allowed to help, but we are not doing that yet.

## Like you are an adult

Division 11 is complete for its documented source/build scope. A minimal, dependency-free, file-backed local entity store now exists with two initial entity types, validation, a versioned schema, and a real close/reopen persistence test using synthetic data. The previous 21 tests remain intact; four new tests bring the active total to 25.

The no-artifact CI run [40316111676](https://github.com/mohamtur1/4CBOn2/actions/runs/40316111676) succeeded at commit `eef1a18d671db2747ea5d4ca7c739e696079377d`: 25 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `471efe77c87f83088796a7710295e353e5cf10f8e9a6a735c01be46d1a676b3a`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator or real-device restart test. See `4CBOn2_Division11_Verification.md` and `android/LOCAL_DATABASE.md`.

Arena fixed this session to `arena/74f65509-4cbon2`, so the work was continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and pushed only there. No PR was opened. Do not rename the session branch or push to a different branch without a new exact-step approval.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `eef1a18d671db2747ea5d4ca7c739e696079377d` |
| Final Division 11 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/40316111676 — **Success** |
| Unit tests | **25 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, and APK Signature Scheme v2 verified |
| Division 11 APK SHA-256 | `471efe77c87f83088796a7710295e353e5cf10f8e9a6a735c01be46d1a676b3a` |
| CI artifacts | **Zero** |
| File-backed reopen | Proven by a synthetic local JVM test and standalone harness, not a device reboot |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device database restart, production signing, Play approval, universal phone control, or general device compatibility |

The historical 09c APK artifact and earlier 21-test CI records are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 12 narrow work order — do not begin yet

When the owner explicitly says “Continue,” start only Division 12 — Selected-app capability registry:

1. Define a minimal owner-selected app identity and action-capability model.
2. Use explicit supported/unavailable states and narrow discovery only; do not collect a blanket installed-app inventory.
3. Keep the least-powerful route ordering: authorized API → supported intent/app link/share → fixed human-defined routine → manual handoff.
4. Preserve sensitive/manual-only workflows, owner takeover/pause/stop, privacy boundaries, and no cloud upload by default.
5. Use synthetic/local data in tests; no live account, credential, unrestricted screen reading/tapping, or external write without separate exact-step approval.
6. Preserve the current 25 tests and update the exact CI total honestly if new tests are added.
7. Require full no-artifact CI verification before closure, then update the plan/recovery documents.
8. Stop before Division 13.

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
4CBOn2_Division11_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
4CBOn2_GitHub_Scoped_Handoff.md
android/LOCAL_DATABASE.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/LocalEntityDatabase.kt
android/app/src/test/java/io/github/mohamtur1/fourcbon2/LocalEntityDatabaseTest.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source. If local `HEAD` again returns to `ed169b6` while newer workspace files remain, preserve the working tree, compare byte-for-byte against the published branch baseline, use only a mixed reset, and never hard-reset or discard local work. Note that an approval pause can reset local Git metadata; recreate the approved content tree and request fresh exact-step approval if the approved commit ID changes.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 12 — Selected-app capability registry when explicitly asked, and do not skip ahead. Preserve the existing 25 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
