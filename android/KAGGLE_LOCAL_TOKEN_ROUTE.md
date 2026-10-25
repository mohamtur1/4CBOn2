# 4CBOn2 Android Prototype — Local Kaggle Token Route

**Build division:** 09a — Prepare local Kaggle token route  
**Continuation required:** 09b — Restore the pinned toolchain and verify the 09a source  
**Status:** **Checkpointed; not build-verified or finished**  
**Date:** 24 October 2026  
**Upload/commit/push:** None

> **Superseded 25 October 2026:** The owner selected manual Kaggle competition participation. The token screen, token slot, and Kaggle API client described here have been removed from active source. This is historical evidence only; see `KAGGLE_MANUAL_COMPETITION_HANDOFF.md`.

## Like you are two

We made a hidden typing box for a Kaggle pass that might be used later.

The box can clear cancelled typing. It is designed to put a saved pass in the phone's locked box. It does not show the pass, send it to Kaggle, or put it in pictures.

No real pass was used. Our building tools are missing, so we could not finish checking the app. We must fix and check it before moving on.

## Like you are an adult

The checkpoint adds a local-only Compose entry screen for Kaggle's documented personal API token route. It uses the existing AndroidKeyStore AES-GCM credential boundary and a secret-free UI state model. The entry screen never invokes the Kaggle read client or any network API.

Official public documentation and source support treating a website-generated personal API token as an opaque Bearer token for the Kaggle API, including the selected competition-list request definition. This is **static/source compatibility evidence only**. No real token was inspected or used, and authorized endpoint compatibility has not had a live-service or device test.

The reviewed public documentation defines granular scopes for third-party OAuth, including `competitions.get` and `competitions.viewer`. It does not document equivalent user-selectable scope controls for personal API tokens. That is a documentation limitation, not proof that a settings-page control cannot exist. The app therefore does **not** claim that a personal token is intrinsically read-only or minimum-permission. Its own future request remains fixed to one read-only GET route.

Division 09a cannot be marked finished because the pinned JDK/Gradle/Android SDK toolchain is absent. The new tests have been written but not executed, and the changed Android source has not compiled. Division 09b must restore the verified toolchain and run the checks before Division 11 is eligible.

## Public documentation findings

| Question | Evidence-backed conclusion | Remaining boundary |
| --- | --- | --- |
| Which website-created credential is in scope? | Kaggle's current CLI documentation describes **Generate New Token** under account API settings and supports the resulting standalone personal API token through `KAGGLE_API_TOKEN` or `~/.kaggle/access_token`. | No token was requested, created, copied, or entered in this division. |
| Is it the legacy username/key format? | No. The CLI separately documents legacy `kaggle.json` / username/key credentials. The Android entry route does not accept that pair. | No legacy credential route was added. |
| Is it transported as Bearer? | The official SDK selects its Bearer authentication adapter when an API token is present; legacy username/key credentials use HTTP Basic instead. | This is source evidence, not a live authorized response. |
| Does it match the selected request? | The same official SDK defines `ApiListCompetitionsRequest.endpoint()` as `/api/v1/competitions/list`, matching the Android client's fixed route. | A real authorized request remains unverified. |
| What token text format is accepted? | The app treats the personal token as an opaque non-blank value and does not enforce a prefix or length. | No token-value validation test was added or run. |
| What scopes can the owner select? | Granular scopes are documented for OAuth. Equivalent personal-token scope controls were not established by the reviewed public docs. | Do not claim a scoped/read-only personal token; review the actual owner settings only in a later explicitly approved step. |
| What can this app do with it? | The existing client has only a fixed `GET` competition-list metadata probe with redirects disabled and no response-body retention. | No request is made by the entry screen, and no live request was made in 09a. |

### Official references reviewed on 24 October 2026

- [Kaggle public API authentication and OAuth scopes](https://www.kaggle.com/docs/api)
- [Kaggle CLI authentication options](https://github.com/Kaggle/kaggle-cli/blob/2076e087da9fb8186636d2fe4d8c9f8cf6c56ac9/docs/README.md#authentication)
- [Kaggle CLI standalone-token introspection and distinct authentication methods](https://github.com/Kaggle/kaggle-cli/blob/2076e087da9fb8186636d2fe4d8c9f8cf6c56ac9/src/kaggle/api/kaggle_api_extended.py#L1565-L1578)
- [Kaggle SDK Bearer versus legacy authentication selection](https://github.com/Kaggle/kagglesdk/blob/46efde203816039e34205bb6cd62ad57d946c25a/kagglesdk/kaggle_http_client.py#L255-L283)
- [Kaggle SDK competition-list request and exact endpoint](https://github.com/Kaggle/kagglesdk/blob/46efde203816039e34205bb6cd62ad57d946c25a/kagglesdk/competitions/types/competition_api_service.py#L3469-L3610)

## Implemented local source

- Restored the 49-file `android/` source/test/document baseline from the verified Division 10 checkpoint because the checkout had no extracted Android project.
- Added a Home-screen entry point for **Kaggle local token setup**.
- Added a masked, single-line personal-token field using a password keyboard and visual transformation.
- Added explicit read-only application behavior and personal-token scope uncertainty explanations.
- Kept the credential value out of `KaggleTokenEntryState`; the model stores only booleans and a notice enum.
- Disabled saving for missing/blank input.
- Added cancellation and back handling that clear transient input without invoking storage or a network client.
- Routed the non-empty save path through `AndroidKeyStoreCredentialStore`; the UI does not load or display the stored value.
- Clear the temporary `CharArray` and Compose input after save attempts. As with other Compose text input, the original immutable `String` cannot be explicitly zeroed; it is cleared from state and left for garbage collection.
- Set `FLAG_SECURE` to prevent activity screenshots/recents thumbnails and excluded the activity view hierarchy from autofill.
- Added three unit-test source cases limited to missing, blank, and cancelled states. No token value, credential store operation, or non-empty save test appears in the test.
- Updated the read client's comments to distinguish personal Bearer tokens, unconfigured OAuth, and legacy credentials. Its request behavior did not change.

## Verification record

| Check | Actual result |
| --- | --- |
| Public documentation/source review | Completed; references and limitations recorded above |
| Real credential inspection or use | **Not performed** |
| Secret-free state/static assertions | Passed |
| Masking, `FLAG_SECURE`, autofill exclusion, local save boundary, and cancellation source assertions | Passed |
| Entry UI contains no Kaggle client, connection, probe, credential load, or Android logging call | Passed |
| New test source contains no credential value or credential-store operation | Passed |
| Manifest XML parse | Passed |
| Final newline/trailing-whitespace checks for new/edited Kotlin files | Passed |
| Untouched restored Android files versus checkpoint | Passed; only `MainActivity.kt` and `KaggleCompetitionReadClient.kt` differ intentionally |
| `:app:testDebugUnitTest` | **Not run: pinned toolchain absent** |
| `:app:assembleDebug` | **Not run: pinned toolchain absent** |
| Current-source APK/signature/checksum | **Not produced; do not reuse an older result for this source** |
| AndroidKeystore encryption on emulator/real device | Not tested |
| Screen/autofill behavior on emulator/real device | Not tested |
| Non-empty local save | Not tested, as required by the no-token-value boundary |
| Authorized Kaggle live-service request | Not performed |

The build attempt stopped before Gradle launch because `/home/user/.cache/4cbon2-android-toolchain/provision.env` was absent. A bounded local inventory also found no alternate JDK, Gradle, Android SDK, or existing Gradle cache. No unverified toolchain substitute was downloaded.

## Files in this checkpoint

New Division 09a files:

- `android/KAGGLE_LOCAL_TOKEN_ROUTE.md`
- `android/app/src/main/java/io/github/mohamtur1/fourcbon2/KaggleTokenEntryState.kt`
- `android/app/src/test/java/io/github/mohamtur1/fourcbon2/KaggleTokenEntryStateTest.kt`

Modified baseline files:

- `android/app/src/main/java/io/github/mohamtur1/fourcbon2/MainActivity.kt`
- `android/app/src/main/java/io/github/mohamtur1/fourcbon2/KaggleCompetitionReadClient.kt`

The other restored Android files match `4CBOn2_Checkpoint_Div10_source_docs.zip`. The checkpoint archive itself was not replaced or removed.

## Required continuation: 09b

**24 October 2026 update:** The first 09b restoration attempt is blocked because the sandbox cannot reach the official Gradle/Android distribution or required dependency hosts and has no usable cache. See `DIV09B_TOOLCHAIN_CHECKPOINT.md`. Retry these steps only after that environment blocker is remediated.

1. Restore the pinned JDK 17 / Gradle 9.4.1 / Android SDK toolchain from verified permitted sources; do not weaken integrity checks.
2. Run `:app:testDebugUnitTest` and `:app:assembleDebug` with the documented one-worker settings.
3. Inspect test counts and any compiler failures; fix only 09a-related failures and rerun.
4. If the build passes, verify the generated debug APK signature and record a new checksum without presenting it as a device or live-service result.
5. Update this record and the build-status table. Do not begin Division 11 until 09a is verified and closed.

A real token is still not requested. Any future real-device entry or authorized Kaggle request requires its own explicit owner-approved step and upload/credential safety statement.

**Next eligible work:** Retry Division 09b only after remediating the environment blocker in `DIV09B_TOOLCHAIN_CHECKPOINT.md`. Divisions 09a and 09b remain open; Division 11 remains blocked.
