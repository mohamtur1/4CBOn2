# 4CBOn2 — New-Thread Handoff for Division 11

**Repository:** `mohamtur1/4CBOn2`
**Assigned branch:** `arena/0eb49904-4cbon2`
**Prepared:** 26 October 2026
**Verified branch head at preparation:** `9485b42a7815f464e57587297d066aa444556a8a`
**Next division:** 11 — Local database
**Do not start:** Division 12 or later work, live Kaggle joining/submission, or a PR/merge

## Like you are two

The Kaggle button test is finished. It opened the correct page in the Kaggle app, and the builder passed all 21 tests.

Next, make a small database inside the app. Save something, close the database, open it again, and prove the saved thing is still there. Do not start the next job after that.

## Like you are an adult

Revised Divisions 09/09b and the 09c owner smoke test are complete for their documented scope. Division 11 is next and has not been implemented. Its narrow target is **initial local-database entities and a genuine persistence/restart test**, not the selected-app registry, manual Kaggle tracker, cloud sync, or a general phone-control data layer.

No pull request is required. Continue on the assigned branch and obtain exact-step approval before every external write or transfer.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/0eb49904-4cbon2` at `9485b42a7815f464e57587297d066aa444556a8a` when this handoff was prepared |
| Final 09c CI | https://github.com/mohamtur1/4CBOn2/actions/runs/40268393469 — **Success** |
| Unit tests | **21 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, and APK Signature Scheme v2 verified |
| Final 09c APK SHA-256 | `365650e63823b61ade462c2fcaae10d351df299370ef52e7d2b041ffb598a68d` |
| Artifacts from that run | **Zero** |
| 09c device result | Installed Kaggle app opened the correct competition page before sign-in; owner then signed in personally and performed Cancel/Back |
| Kaggle account actions | No credential shared with 4CBOn2 or the agent; no competition join or submission |
| Unverified 09c limitations | Browser-specific handling and empty/invalid/no-automatic-launch device subchecks were not separately verified |
| Production/device claims | No production signing, Play Store approval, universal phone control, or general device compatibility is established |

The older 09c APK artifact is historical and must not be represented as the current-source build after the wording correction. Earlier 09/09b evidence remains in `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md` and Git history.

## Division 11 narrow work order

1. **Inspect before editing.** Read the current Gradle catalog, app source, existing tests, and relevant design documents. Confirm the branch and clean working tree.
2. **Implement only initial local persistence.** Add the smallest useful local-database schema/entities and access layer justified by the existing prototype. Use stable local identifiers and explicit state/timestamp fields only where they serve the design. No account session, server, cloud sync, Kaggle API, or cross-app registry.
3. **Prove persistence across reopening.** Add a test that writes through the real persistence layer, closes it, recreates/reopens it against the same temporary/file-backed storage, and reads the expected record back. An in-memory-only database or recreating a Kotlin state object is not persistence/restart evidence.
4. **Keep the test hermetic.** Use temporary storage and synthetic data. Do not use owner files, credentials, phone identifiers, live accounts, or external services. Label the evidence accurately as local JVM/Robolectric/instrumentation/device according to what actually runs.
5. **Preserve existing behavior.** Do not change the manual Kaggle intent route or unrelated phone/Colab features. Existing tests must continue to pass.
6. **Update the CI test total honestly.** The workflow currently names and asserts exactly 21 tests. After adding tests, update the count in `.github/workflows/android-division09b.yml` to the new actual total, including the XML aggregation assertion, step/notice labels, and documentation. Do not remove the exact-total assertion, skip tests, or relabel unexecuted tests as passed.
7. **Validate locally as far as the environment permits.** Run whitespace/static/hygiene checks. Do not claim a local Gradle build if the local toolchain is unavailable; use the approved CI route for actual compilation.
8. **Commit locally, then request exact push approval.** Identify every changed file, the destination branch, the resulting no-artifact CI run, and the reason. Do not include `[device-apk-09c]` in the commit message and do not upload an APK.
9. **Verify CI before closure.** Require compilation, the new exact test total with zero failures/errors/skips, debug APK assembly, zip alignment, v2 signature, SHA-256, and clean-source checks. Record the run/commit/hash and update the plan/handoffs honestly.
10. **Stop at the division boundary.** Do not start Division 12. If the 30-minute cap is reached, save a precise checkpoint instead of claiming completion.

## Division 11 acceptance checklist

- [ ] Initial local-database entities/access layer exist and are limited to prototype needs.
- [ ] A real temporary/file-backed persistence test closes and reopens the store and proves the saved record survives.
- [ ] Test storage/data are synthetic and contain no credentials or owner/account data.
- [ ] Existing manual Kaggle behavior and tests remain intact.
- [ ] CI's exact test count is updated to the new actual total without weakening verification.
- [ ] Full CI compilation/tests/APK/alignment/v2-signature/SHA-256/clean-source checks pass.
- [ ] No CI artifact is uploaded and no commit contains the old 09c artifact marker.
- [ ] Evidence identifies the test type honestly; no unrun emulator/real-device restart is claimed.
- [ ] Plan and recovery documents record the actual result and blockers.
- [ ] Division 12 has not started.

## CI and artifact rules

The existing workflow is `.github/workflows/android-division09b.yml`, despite its historical name. It runs on relevant pushes to the assigned branch and currently provides checksum-pinned build verification with `contents: none`.

For Division 11:

- Keep credentials, secrets, CI repository writes, and artifact uploads out of the workflow.
- Do not use the historical `[device-apk-09c]` marker. Without that marker, the artifact-retention step must remain skipped.
- A root-Markdown-only handoff push normally does not match the Android workflow path filter; Android source/workflow changes do trigger it.
- If the workflow's hard-coded 21-test assertion is not updated after new tests are added, a correct new test suite can still fail CI merely because the total changed. Fix the assertion to the actual total rather than bypassing it.
- Do not open a PR merely to run this branch workflow. Any later PR/merge requires separate exact-step approval.

## Files to read first

```text
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
4CBOn2_GitHub_Scoped_Handoff.md
android/app/build.gradle.kts
android/gradle/libs.versions.toml
android/app/src/main/java/io/github/mohamtur1/fourcbon2/
android/app/src/test/
.github/workflows/android-division09b.yml
android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as a historical backup. Do not restore it over newer source.

## Workspace/Git recovery rule

A prior session exposed a bookkeeping mismatch: local `HEAD` returned to the original `ed169b6` while all newer workspace files remained present and the remote branch retained the correct commits. If this happens again:

1. Do not stage, delete, overwrite, or hard-reset files.
2. Compare `git rev-parse HEAD`, `git status --short --branch`, and `git ls-remote origin refs/heads/arena/0eb49904-4cbon2`.
3. Fetch only the assigned branch from `origin`.
4. Compare every published file against the workspace byte-for-byte.
5. Only if there are **zero mismatches**, align the local branch/index with:

   ```bash
   git reset --mixed origin/arena/0eb49904-4cbon2
   ```

6. If any file differs, stop and preserve the evidence; do not use a hard reset or discard local work.

Never rename the assigned branch, create another session branch, or delete the repository root or `.git` directory.

## Standing owner requirements

- Work in one maximum-30-minute division at a time, including waiting, verification, and checkpointing.
- Explain every progress/completion update in both **Like you are two** and **Like you are an adult** styles unless explicitly told otherwise.
- Before every work step, state whether upload is needed. For each external write/transfer, identify the exact destination, files/data, and reason and obtain explicit owner approval first.
- Never request, accept, display, or store passwords, tokens, cookies, authorization codes, or browser sessions.
- Do not ask design clarification questions; choose interaction design autonomously within the documented safety boundaries.
- Preserve least-powerful supported routes and manual-only handling for sensitive phone workflows.
- Never claim universal phone/app control, production signing, Play Store approval, or live Kaggle joining/submission without actual evidence.
- No PR is needed for Division 11.

## Ready-to-paste new-thread message

> Continue the 4CBOn2 Android prototype in `mohamtur1/4CBOn2` on branch `arena/0eb49904-4cbon2`. Read `4CBOn2_Next_Thread_Handoff.md` first, then `4CBOn2 — New-Thread Engineering.md` and `4CBOn2_Build_Divisions.md`. Start only Division 11 — Local database: implement minimal initial local entities/access and prove real file-backed persistence after close/reopen with synthetic data. Preserve the existing 21 tests, update CI's exact test-count assertion to the new actual total, and require full no-artifact CI verification before closure. Do not start Division 12, use live accounts, or open a PR. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer. Never request or accept credentials.
