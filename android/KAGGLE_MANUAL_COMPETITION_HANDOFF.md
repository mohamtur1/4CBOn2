# 4CBOn2 Android Prototype — Manual Kaggle Competition Handoff

**Build division:** Revised 09 — Manual Kaggle competition handoff  
**Status:** **Source/static checkpoint; not build-verified or finished**  
**Date:** 25 October 2026  
**Session checkpoint:** 18:05:49–18:10:36 UTC (approximately 5 minutes)  
**Supersedes:** Authenticated Kaggle read and local-token route  
**Upload/commit/push:** None

## Like you are two

We took out the Kaggle pass box.

Now you can paste a competition link and tap a button to ask your browser to open it. You sign in, read the rules, and tap Join on Kaggle yourself.

The app cannot join or submit for you. It does not know your Kaggle password and does not check your Kaggle account.

The new app pieces passed paper checks. They still need their real build checks.

## Like you are an adult

The active Android Kaggle integration is now a manual competition handoff. It has no Kaggle API client, token entry/storage, OAuth flow, competition-list read, account-status synchronization, automated download/upload, or submission mechanism.

A validated competition URL can be handed to an Android browser only after an explicit owner tap. The owner handles any Kaggle sign-in, rules review, joining, notebook submission, and final submission on Kaggle. The app records only local handoff UI state and deliberately has no joined, submitted, or synchronized status.

This preserves owner-driven competition participation without app-side Kaggle authorization. It does not make Kaggle itself account-free and does not claim autonomous competition entry.

## Implemented boundary

| Area | Actual source behavior |
| --- | --- |
| Accepted destination | Only `https://www.kaggle.com/competitions/<slug>` with an optional single trailing slash |
| Rejections | Empty/malformed input, non-HTTPS schemes, other hosts, explicit ports, user-info, query/fragment, non-competition paths, extra paths, and overlong input |
| Canonicalization | Rebuilds the accepted URL from the fixed HTTPS host/path and validated slug; does not retain arbitrary URL components |
| Browser handoff | Explicit button tap creates `Intent.ACTION_VIEW` with `CATEGORY_BROWSABLE`; no automatic launch or redirect handling in the app |
| No-browser/security failure | Reports only `OPEN_FAILED`; does not convert failure into a successful handoff |
| Successful `startActivity` return | Reports only `OPEN_REQUESTED`; this is not proof of page visibility, sign-in, joining, or submission |
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

There are currently **21 `@Test` methods in source**. They have **not been executed** in this division.

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
| Kotlin/Compose compilation | **Not run: toolchain/dependency blocker remains** |
| JUnit tests | **Not run** |
| Current-source APK/signature/checksum | **Not produced** |
| Android browser intent on emulator/real device | Not tested |
| Real Kaggle sign-in, competition join, or submission | Not performed |

The previously recorded sandbox blocker still prevents Gradle/Android/dependency restoration. See `DIV09B_TOOLCHAIN_CHECKPOINT.md` for that environment evidence. The current 09b task is now to verify this revised manual handoff, not the removed token route.

## Later-division planning impacts

- **23:** Replace live Kaggle metadata/status with an owner-entered manual competition tracker and owner-update timestamps.
- **29:** Keep notebook skeleton work file-based with manual upload/run handoff; no Kaggle runtime-control claim.
- **37:** Use owner-selected local files for offline competition preparation; no API download/upload.
- **38:** Use a manual submission checklist and owner-recorded receipt states; no live submission or receipt verification.

These are planning updates only. Those divisions have not started.

## Required next work: revised 09b

1. Restore a verified local toolchain/dependency route, or use a separately owner-approved external build route.
2. Run `:app:testDebugUnitTest` and `:app:assembleDebug` against the revised manual-handoff source.
3. Fix only failures related to this revision and rerun the checks.
4. If assembly succeeds, record a fresh debug APK signature/checksum without implying a device or Kaggle result.
5. Treat browser/device handoff and real competition participation as separate, explicitly approved tests.

**Next eligible work:** Revised Division 09b — verify the manual Kaggle competition handoff. Division 11 remains blocked until the revised Division 09 is verified and closed.
