# 4CBOn2 — Next-Thread Handoff after Division 09c

**Repository:** `mohamtur1/4CBOn2`
**Assigned branch:** `arena/0eb49904-4cbon2`
**Prepared:** 25 October 2026; owner result recorded 26 October 2026
**Current task:** Confirm green CI for the 09c external-handoff wording correction, then start Division 11 — Local database
**Not the current task:** Real Kaggle rules acceptance, joining, or submission

## Like you are two

You tried the Kaggle button on your phone. It opened the correct page in your Kaggle app, and you tried Cancel and Back.

The app now says “an installed app or browser” so its words match what can happen. After the builder checks that small wording change, the next job is Division 11.

## Like you are an adult

Revised Divisions 09/09b are closed for source/build verification. Division 09c recorded a successful explicit external app-link handoff: the installed Kaggle app opened the correct competition page before the owner signed in. The owner later signed in personally and performed Cancel and Back. No credential was shared with 4CBOn2 or the agent, and no competition join or submission occurred.

This is not browser-specific verification. Empty-field, invalid-link, no-automatic-launch, device-model/Android-version, and installer-permission-revocation outcomes were not separately reported. The functional intent code is unchanged; only wording/evidence is corrected. Division 11 must wait for green CI on that correction.

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
- Division 09c owner result: installed Kaggle app opened the correct page before sign-in; owner later signed in personally; Cancel and Back performed; no join/submission or credential sharing.

The hash above belongs only to run 40228830760. A new 09c build must be identified by its own commit, run, and freshly reported hash. Do not install one APK while checking the hash of another.

## Historical 09c test artifact — used for the owner smoke test

- Build commit: `042bc6dbcc341562efdba61242d38062955863f9`
- Successful run: https://github.com/mohamtur1/4CBOn2/actions/runs/40230830330
- Artifact: `app-debug.apk` (artifact ID `12613226429`)
- Size: **11,645,983 bytes**
- SHA-256 / artifact digest: `638f1cf46e473b8bd2e2087bf9dc140a4d0cd436be2a5c9105840327adf02cc1`
- Created: `2026-10-25T20:30:17Z`
- Expires: `2026-11-01T20:30:15Z`
- Verification: 21 tests passed; APK assembled, zip-aligned, and v2-signature verified; exactly one artifact was retained.

This artifact was used for the reported 09c smoke test. The subsequent wording correction changes source, so this APK is historical and must not be represented as the latest build. Do not repeat the device test merely for the wording-only change; if a functional change or a new artifact is later required, use a new exact-step approval and record the new commit/run/hash.


## Historical Division 09c APK route

The artifact above was produced, verified, and used for 09c. It is retained only as historical evidence for that exact build; it is not a current-source APK after the wording correction.

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

## Historical Part B — 09c download/install procedure (already performed)

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

## Historical Part C — 09c smoke-test procedure (already performed)

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

## Part D — Recorded 09c result without sensitive data

The owner-reported outcomes are recorded below. Unreported items remain explicitly unverified rather than being inferred as passes:

| Item | Result / evidence to record |
| --- | --- |
| CI run, commit, artifact name, and fresh APK SHA-256 | Run 40230830330; commit `042bc6d`; `app-debug.apk`; SHA-256 `638f1cf46e473b8bd2e2087bf9dc140a4d0cd436be2a5c9105840327adf02cc1` |
| Phone make/model and Android version | **Not reported**; no serial numbers, device IDs, phone numbers, or account identifiers recorded |
| Browser and version, if known | **Not applicable to the reported handler**; the installed Kaggle app, not a browser, opened the link |
| 09c-01 Launch/navigation | **Passed by owner report**: the manual screen was reached and used |
| 09c-02 Empty state | **Not separately reported**; do not claim a device pass |
| 09c-03 Invalid reference | **Not separately reported**; do not claim a device pass |
| 09c-04 Valid reference/no automatic launch | **Not separately reported**; do not claim this specific device check |
| 09c-05 External handler accepted the handoff | **Passed with route correction**: the installed Kaggle app handled the link; browser-specific handling was not verified |
| 09c-06 Correct canonical destination and honest app state | **Correct page passed before sign-in**; the old “Browser handoff requested” wording was inaccurate for this handler and is corrected in source |
| 09c-07 Cancellation/back | **Passed by owner report**: owner cancelled the link and pressed Back |
| Temporary installer permission revoked | **Not reported** |
| Kaggle sign-in/join/submission | Owner signed in personally in the Kaggle app after the page opened; **no join or submission**, and no credential shared with 4CBOn2 or the agent |

Do not upload screenshots, screen recordings, browser sessions, or account data without a separate exact-step approval. A text result is sufficient for this smoke test.

## What happens next

- Confirm that the 09c wording-correction commit passes all 21 tests and APK/source checks in CI. Do not start Division 11 on a failed or unverified run.
- The intent implementation is unchanged, so no repeat phone test is required solely for the wording correction. Browser-specific handling and unreported subchecks remain unverified limitations.
- After green CI, proceed to **Division 11 — Local database** in a new maximum-30-minute division: initial entities and a persistence/restart test.
- Real owner-driven Kaggle joining/submission remains scheduled after Division 38 and before final packaging at Division 42. The 09c sign-in does not evidence either action.

## Standing rules for the next thread

- Work only on `arena/0eb49904-4cbon2`; do not rename it or create another branch for this session.
- Work in one maximum-30-minute division at a time.
- Before every work step, state whether upload is needed. For every external write/transfer, identify the exact destination, files/data, and reason and obtain explicit owner approval first.
- Never request, accept, display, or store passwords, tokens, cookies, authorization codes, or browser sessions.
- Explain progress/completion updates in both **Like you are two** and **Like you are an adult** styles unless the owner explicitly asks otherwise.
- Do not ask design clarification questions; choose interaction design within the documented safety boundaries.
- Never claim universal phone/app control, production signing, Play Store approval, or live Kaggle verification without actual evidence.
- No PR is needed for 09c closure or Division 11. Any later PR/merge is a separate owner-approved operation.

## Ready-to-paste next-thread message

> Continue the 4CBOn2 Android prototype in `mohamtur1/4CBOn2` on branch `arena/0eb49904-4cbon2`. Read `4CBOn2_Next_Thread_Handoff.md`, `4CBOn2 — New-Thread Engineering.md`, and `4CBOn2_Build_Divisions.md`. First confirm green CI for the recorded 09c external-handoff wording correction; do not proceed if it failed. Then start only Division 11 — Local database, targeting initial entities and a persistence/restart test in one maximum-30-minute session. The owner’s Kaggle-app handoff and sign-in are documented; no join or submission occurred. Do not open a PR unless separately approved. Preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer. Never request or accept credentials.
