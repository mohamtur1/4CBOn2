# 4CBOn2 — Next-Thread Handoff for Division 09c

**Repository:** `mohamtur1/4CBOn2`
**Assigned branch:** `arena/0eb49904-4cbon2`
**Prepared:** 25 October 2026
**Current task:** Division 09c — owner-in-the-loop Android browser-handoff smoke test
**Not the current task:** Real Kaggle sign-in, rules acceptance, joining, or submission

## Like you are two

The app passed its builder tests. Next, you can try its Kaggle button on your phone.

The button should only ask your browser to open the competition page. It should not join, submit, sign in, or control Kaggle for you.

A grown-up helper called a pull request is **not needed** for this test.

## Like you are an adult

Revised Divisions 09/09b are closed for source/build verification. Division 09c checks the already implemented manual browser handoff on an owner device. It is a small, separately evidenced test; it is not live Kaggle participation and not a production-release test.

No pull request is required before this test or before continuing on the assigned branch. The branch is already published. A PR is optional later for review/merge into `main`, and opening or merging one requires its own exact-step owner approval.

## Verified starting point

- Published documentation commit: `690e3092baec4cf9c123b68505bfcc7598f3d438`
- Successful CI run: https://github.com/mohamtur1/4CBOn2/actions/runs/40228830760
- Unit tests: **21 passed; 0 failures, 0 errors, 0 skipped**
- Fresh debug APK: assembled, zip-aligned, and APK Signature Scheme v2 verified
- SHA-256 for that particular CI build: `bfbd20e798e2ca7967ba2e96ece8f5010338fb19ea06188d54841a1c54d986c2`
- Artifact count for that run: **zero**
- Application ID: `io.github.mohamtur1.fourcbon2`
- App label/version: `4CBOn2` / `0.1.0-dev`
- No browser/device test or real Kaggle account action has occurred.

The hash above belongs only to run 40228830760. A new 09c build must be identified by its own commit, run, and freshly reported hash. Do not install one APK while checking the hash of another.

## Current 09c test artifact — ready for the owner device test

- Build commit: `042bc6dbcc341562efdba61242d38062955863f9`
- Successful run: https://github.com/mohamtur1/4CBOn2/actions/runs/40230830330
- Artifact: `app-debug.apk` (artifact ID `12613226429`)
- Size: **11,645,983 bytes**
- SHA-256 / artifact digest: `638f1cf46e473b8bd2e2087bf9dc140a4d0cd436be2a5c9105840327adf02cc1`
- Created: `2026-10-25T20:30:17Z`
- Expires: `2026-11-01T20:30:15Z`
- Verification: 21 tests passed; APK assembled, zip-aligned, and v2-signature verified; exactly one artifact was retained.

The phone/browser test has **not** been performed yet. Check that the artifact is still unexpired before installing it. If it has expired or the source changes, prepare a new artifact only through a new exact-step approval and record the new commit/run/hash.


## Division 09c APK route

The artifact above has now been produced and verified. The next thread must inspect its current expiry/source state rather than assuming it remains usable.

The prepared workflow change retains an APK only when all of these conditions are true:

1. The event is a push to the assigned branch.
2. The approved commit message contains `[device-apk-09c]`.
3. Compilation, all 21 tests, APK assembly, alignment, v2-signature verification, and clean-source checks pass.

Only then does the final step retain `app-debug.apk` for seven days, using GitHub's official `actions/upload-artifact` pinned to commit `cf430e030ddbb5b0abf93d22962f4752f3646cd9` (`v7.0.2`). Ordinary pushes and `workflow_dispatch` runs do not upload an APK. The artifact contains a debug build, not credentials or a production-signed release.

Before the workflow commit push and resulting artifact upload, the agent must state:

```text
Upload needed? Yes
Destination: origin/arena/0eb49904-4cbon2 and that run's GitHub Actions artifact store
Files/data: the exact named source/handoff files plus one verified app-debug.apk
Reason: enable the owner-approved Division 09c device/browser smoke test
```

The owner must approve that exact step. No PR is needed for this push-triggered route.

## Part A — Test build completed

- [x] The seven-file preparation commit was validated and approved for its exact push/artifact step.
- [x] Commit `042bc6dbcc341562efdba61242d38062955863f9` was pushed to the assigned branch.
- [x] Run 40230830330 passed all 21 tests and every APK/source check.
- [x] Exactly one `app-debug.apk` was retained with the recorded matching SHA-256/digest.
- [x] No PR, credential, Kaggle account action, or production artifact was created.

Do not repeat the artifact build while this artifact remains valid for the unchanged source. If CI had failed, installation would not have been offered. The owner may now follow Part B; the agent should not perform the phone taps or request an account credential.

## Part B — Owner downloads and installs the debug APK

You need an Android phone running **Android 8.0/API 26 or later** (`minSdk = 26`) with a browser. This declared minimum is not proof of compatibility. A computer is optional. You do **not** need a Kaggle account for this smoke test.

1. Open the agent-provided Actions run link.
2. Sign in to GitHub yourself if GitHub asks. Never send the password, token, cookie, or session to the agent.
3. In **Artifacts**, download `app-debug.apk`. If GitHub supplies a ZIP instead, extract it first.
4. Compare the downloaded APK's SHA-256 with the fresh value supplied for that exact run.
5. If you downloaded on a computer, transfer only that APK to your phone with a trusted local method such as USB.
6. On the phone, open the APK from your Files app.
7. If Android requires it, allow **Install unknown apps** only for the Files/browser app you are using. Do not disable Play Protect, enable Developer Options, grant Accessibility access, or change unrelated security settings for this test.
8. Install and open **4CBOn2**. If the identity/hash does not match, or you are uncomfortable with the warning, stop and record the blocker.
9. After testing, revoke the temporary **Install unknown apps** permission for that Files/browser app.

Optional computer hash commands, using the actual download location:

```bash
# Linux
sha256sum app-debug.apk

# macOS
shasum -a 256 app-debug.apk
```

```powershell
# Windows PowerShell
Get-FileHash .\app-debug.apk -Algorithm SHA256
```

## Part C — Owner performs the browser-handoff smoke test

Use only this public test reference:

```text
https://www.kaggle.com/competitions/titanic
```

Do not sign in, accept rules, tap Join, upload a notebook, or submit during 09c.

| Test | Owner action | Expected result |
| --- | --- | --- |
| 09c-01 Launch/navigation | Open `4CBOn2`; tap `Kaggle competition — manual` on Home | Manual-handoff screen opens; it states that there is no Kaggle API/app sign-in |
| 09c-02 Empty state | With an empty `Competition link` field, inspect the open button | `Open competition page — manual handoff` is disabled; no browser opens |
| 09c-03 Invalid reference | Enter `https://example.com` | App asks for one valid HTTPS Kaggle competition link; open remains disabled; no browser opens |
| 09c-04 Valid reference, no automatic launch | Replace the field contents with the public test reference above | `Competition-link format accepted. Nothing has opened yet.` appears; nothing opens before your tap |
| 09c-05 Explicit browser handoff | Tap `Open competition page — manual handoff` | Your browser is asked to open the canonical `www.kaggle.com/competitions/titanic` page; no join/submission occurs |
| 09c-06 Honest app state | Return to `4CBOn2` | App says browser handoff was requested and joining/rules/submission remain manual and unverified |
| 09c-07 Cancellation/back | Enter the reference again, then tap `Cancel and clear link`; afterwards test `Back` | Link clears with cancelled notice; no new browser handoff; Back returns Home without retaining the link |

A network failure while Kaggle loads in the browser is not automatically an app-intent failure. Record separately whether the browser accepted the intent and whether the website loaded. Do not troubleshoot by adding credentials or weakening app validation.

## Part D — Record the result without sensitive data

Copy this table into the 09c result record and use **Passed**, **Failed**, **Blocked**, or **Not tested** with a short factual note:

| Item | Result / evidence to record |
| --- | --- |
| CI run, commit, artifact name, and fresh APK SHA-256 | Run 40230830330; commit `042bc6d`; `app-debug.apk`; SHA-256 `638f1cf46e473b8bd2e2087bf9dc140a4d0cd436be2a5c9105840327adf02cc1` |
| Phone make/model and Android version | Pending; do not record serial numbers, device IDs, phone numbers, or account identifiers |
| Browser and version, if known | Pending |
| 09c-01 Launch/navigation | Pending |
| 09c-02 Empty state | Pending |
| 09c-03 Invalid reference | Pending |
| 09c-04 Valid reference/no automatic launch | Pending |
| 09c-05 Browser accepted the handoff | Pending |
| 09c-06 Correct canonical destination and honest app state | Pending |
| 09c-07 Cancellation/back | Pending |
| Temporary installer permission revoked | Pending |
| Kaggle sign-in/join/submission | Must remain **Not performed** in 09c |

Do not upload screenshots, screen recordings, browser sessions, or account data without a separate exact-step approval. A text result is sufficient for this smoke test.

## What happens next

- If 09c passes, record the evidence and proceed to **Division 11 — Local database** in a new maximum-30-minute division.
- If 09c finds an app defect, pause Division 11, fix only the relevant handoff issue, rerun the 21 tests and APK checks, and repeat the affected owner test.
- If no device is available, mark 09c **Blocked/Not tested**. Division 11 may proceed with that limitation documented, but never claim device compatibility.
- Real owner-driven Kaggle participation remains scheduled after Division 38 and before final packaging at Division 42.

## Standing rules for the next thread

- Work only on `arena/0eb49904-4cbon2`; do not rename it or create another branch for this session.
- Work in one maximum-30-minute division at a time.
- Before every work step, state whether upload is needed. For every external write/transfer, identify the exact destination, files/data, and reason and obtain explicit owner approval first.
- Never request, accept, display, or store passwords, tokens, cookies, authorization codes, or browser sessions.
- Explain progress/completion updates in both **Like you are two** and **Like you are an adult** styles unless the owner explicitly asks otherwise.
- Do not ask design clarification questions; choose interaction design within the documented safety boundaries.
- Never claim universal phone/app control, production signing, Play Store approval, or live Kaggle verification without actual evidence.
- No PR is needed for 09c. Any later PR/merge is a separate owner-approved operation.

## Ready-to-paste next-thread message

> Continue the 4CBOn2 Android prototype in `mohamtur1/4CBOn2` on branch `arena/0eb49904-4cbon2`. Read `4CBOn2_Next_Thread_Handoff.md`, `4CBOn2 — New-Thread Engineering.md`, and `4CBOn2_Build_Divisions.md`. Continue only with Division 09c: verify that the recorded debug-APK artifact is still valid, then guide and record the owner-in-the-loop browser-handoff smoke test exactly as documented. If the artifact expired or source changed, obtain new exact-step approval before replacing it. Do not sign in, join, or submit to Kaggle. Do not open a PR unless separately approved. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer. Never request or accept credentials.
