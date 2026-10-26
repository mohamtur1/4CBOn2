# 4CBOn2 Android Prototype — Manual Kaggle Competition Handoff

**Build division:** Revised 09/09b — Manual Kaggle competition handoff and verification
**Status:** **Finished: manual source and CI build verified; device/competition tests remain separate**
**Date:** 25 October 2026
**Source session:** 18:05:49–18:10:36 UTC (approximately 5 minutes)
**09b sessions:** Preparation 19:10:47–19:24:24 UTC (approximately 14 minutes); verification 19:36:39–19:55:42 UTC (approximately 19 minutes), separated by owner permission recovery
**Supersedes:** Authenticated Kaggle read and local-token route
**Upload/commit/push:** Approved source/workflow pushes through `d1be9aa`; this evidence update remains local until separately approved

## Like you are two

We took out the Kaggle pass box.

Now you can paste a competition link and tap a button to ask an installed app or browser to open it. You sign in, read the rules, and tap Join on Kaggle yourself.

The app cannot join or submit for you. It does not know your Kaggle password and does not check your Kaggle account.

The new app pieces passed their real build checks and all 21 tests. The APK also passed its alignment and signature checks. We still have not tested the browser button on a phone or joined a real competition.

## Like you are an adult

The active Android Kaggle integration is now a manual competition handoff. It has no Kaggle API client, token entry/storage, OAuth flow, competition-list read, account-status synchronization, automated download/upload, or submission mechanism.

A validated competition URL can be handed to an external Android handler—an installed app or browser—only after an explicit owner tap. Android does not guarantee which eligible handler answers. The owner handles any Kaggle sign-in, rules review, joining, notebook submission, and final submission on Kaggle. The app records only local handoff UI state and deliberately has no joined, submitted, or synchronized status.

This preserves owner-driven competition participation without app-side Kaggle authorization. It does not make Kaggle itself account-free and does not claim autonomous competition entry.

## Implemented boundary

| Area | Actual source behavior |
| --- | --- |
| Accepted destination | Only `https://www.kaggle.com/competitions/<slug>` with an optional single trailing slash |
| Rejections | Empty/malformed input, non-HTTPS schemes, other hosts, explicit ports, user-info, query/fragment, non-competition paths, extra paths, and overlong input |
| Canonicalization | Rebuilds the accepted URL from the fixed HTTPS host/path and validated slug; does not retain arbitrary URL components |
| External app/browser handoff | Explicit button tap creates `Intent.ACTION_VIEW` with `CATEGORY_BROWSABLE`; an installed Kaggle app or browser may handle it; no automatic launch or redirect handling in the app |
| No-browser/security failure | Reports only `OPEN_FAILED`; does not convert failure into a successful handoff |
| Successful `startActivity` return | Reports only `OPEN_REQUESTED`; this does not identify the handler or prove page visibility, sign-in, joining, or submission |
| Cancellation/back | Clears the transient URL/reference and records only cancellation |
| Kaggle account/session | Not accessed, stored, logged, inspected, or controlled |
| Competition data/status | Not fetched or synchronized; no joined/submitted model exists |
| Persistence | None in this division; URL input is transient |
| Internet permission | Retained only because the separate GitHub read boundary still exists; the manual Kaggle code does not use it |

## Removed active source and tests

- `KaggleCompetitionReadClient.kt`
- `KaggleCompetitionEndpoint.kt`
- `KaggleReadResult.kt`
- `KaggleTokenEntryState.kt`
- Their four associated API/result test files
- `KaggleTokenEntryStateTest.kt`
- `KAGGLE_BEARER_ACCESS_TOKEN` from `CredentialSlot` and its test expectation
- Token-only activity screenshot/autofill changes added for the removed screen

The AndroidKeyStore boundary remains for the separately scoped future GitHub credential route. The original recovery ZIP and historical evidence documents remain retained and are marked superseded where applicable.

## New source and tests

- `KaggleCompetitionHandoff.kt` — strict URL validation and secret-free manual-handoff UI state
- `KaggleCompetitionHandoffTest.kt` — ten source test cases covering accepted links, rejection rules, missing reference, open request/failure, and cancellation
- `MainActivity.kt` — Home entry and explicit manual browser-handoff screen

There are **21 `@Test` methods in source**. All 21 executed and passed in the approved GitHub Actions verification run, with zero failures, errors, or skipped tests.

## Verification record

| Check | Actual result |
| --- | --- |
| Active Kaggle token/OAuth/API symbol scan | Passed: none present |
| Kaggle credential store/HTTP authorization path scan | Passed: none in manual UI/model |
| Handoff mechanism scan | Passed: explicit `ACTION_VIEW` only |
| Manual test source scan | Passed: no credential store or HTTP client |
| Kotlin final-newline/trailing-whitespace checks | Passed |
| Manifest XML parse | Passed |
| Original recovery archive retained | Passed |
| Kotlin/Compose compilation | **Passed in approved GitHub Actions run 40228035751** |
| JUnit tests | **21 passed; 0 failures, 0 errors, 0 skipped** |
| Fresh debug APK assembly | **Passed on ephemeral CI runner** |
| APK zip alignment | **Passed** |
| APK Signature Scheme v2 | **Passed** |
| Fresh debug APK SHA-256 | `02eb96bebda5563bc00f55e35af7356d142e9b96185b896f877110dcc6b14264` |
| Artifact retention/upload | **None; run artifact count was zero** |
| Source unchanged by build | **Passed** |
| External app-link handoff on owner device | **Passed in 09c:** installed Kaggle app opened the correct competition page before sign-in; browser-specific handling not verified |
| Owner Kaggle sign-in | **Performed personally in the Kaggle app during 09c**; no credential shared with 4CBOn2 or the agent |
| Real competition join or submission | **Not performed**; remains separate owner-driven work |
| Cancel and Back | **Performed by owner during 09c** |
| Empty/invalid/no-automatic-launch device subchecks | Not separately reported; not claimed as device passes |

Final verification evidence:

- Commit: `d1be9aa3bfd15fcce30277e4f676e03326e29f2a`
- Workflow: `.github/workflows/android-division09b.yml`
- Run: https://github.com/mohamtur1/4CBOn2/actions/runs/40228035751
- Job: `121273552843`; completed successfully at 19:55:42 UTC
- Android command-line-tools archive `16111833` retained its pinned SHA-256. Its `sdkmanager --version` reported `1.1.16717390 (Android CLI)`, which is the CLI implementation version rather than the package label used in older records.
- Platform-tools reported `Version 37.0.1-15733141`; CI invoked the explicitly installed ADB path.

The local sandbox toolchain/distribution blocker remains historical environment evidence in `DIV09B_TOOLCHAIN_CHECKPOINT.md`; it was not silently relabeled as local restoration. The separately approved CI route completed the revised-source build instead. The CI APK was verified but not retained as a downloadable artifact, and it is not a production-signed or device-installed build.

## Later-division planning impacts

- **23:** Replace live Kaggle metadata/status with an owner-entered manual competition tracker and owner-update timestamps.
- **29:** Keep notebook skeleton work file-based with manual upload/run handoff; no Kaggle runtime-control claim.
- **37:** Use owner-selected local files for offline competition preparation; no API download/upload.
- **38:** Use a manual submission checklist and owner-recorded receipt states; no live submission or receipt verification.

These are planning updates only. Those divisions have not started.

## Revised 09/09b closure and remaining owner tests

- [x] Used a separately owner-approved external build route; no credentials or artifact uploads were added.
- [x] Ran `:app:testDebugUnitTest` and verified exactly 21 passing tests.
- [x] Ran `:app:assembleDebug` and verified a fresh debug APK.
- [x] Verified zip alignment, APK Signature Scheme v2, and the recorded SHA-256.
- [x] Confirmed the build did not modify source and uploaded no artifact.
- [x] Record the 09c external app-link handoff: the installed Kaggle app opened the correct page before sign-in; owner then signed in personally and performed Cancel/Back.
- [ ] Keep browser-specific handling and unreported device subchecks explicitly unverified.
- [ ] Let the owner personally perform any future rules review, joining, or submission; none occurred in 09c.

Revised Divisions 09 and 09b are **closed for manual source/build verification**. This does not close the separate device or live-competition tests.

**Next prepared work:** Confirm green CI for the 09c external-handoff wording correction, then start Division 11 — Local database in a new maximum-30-minute session. The intent implementation is unchanged, so no repeat phone test is required solely for this wording change. See `4CBOn2_Next_Thread_Handoff.md`.
