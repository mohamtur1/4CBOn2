# 4CBOn2 — New-Thread Engineering Handoff (Division 10 + Part 9 Update)

**Prepared:** 23 October 2026  
**Purpose:** Continue the native Android prototype from a fresh, short conversation without depending on the prior long thread.  
**Current safe stop:** Division 09 remains authorization-blocked. Start only with Division **09a — Prepare local Kaggle token route** when the owner says “Continue.” Do not skip to Division 11 first.

---

## 1. Security and repository facts

- A GitHub Personal Access Token was exposed in the prior conversation and the owner reports that it has been **revoked**.
- Never repeat, search for, request, accept, store, use, or include that token in any artifact.
- No Git remote push, repository write, commit, or credential use was performed by the agent. Earlier GitHub work was read-only public API verification.
- GitHub has no supported connector in this environment. Do not request a PAT in chat as a workaround.
- The owner is replacing the old GitHub checkpoint files with the current source-and-documents archive and this handoff. Verify their public upload only after they say it is done; never use a credential.

## 2. Standing owner requirements

- Build the native Android companion prototype incrementally, one maximum-30-minute division at a time.
- Do not ask design clarification questions; choose interaction design autonomously.
- Explain every progress/completion response in two parts: **Like you are two** and **Like you are an adult**—unless the owner explicitly asks for only one style in that message.
- Before every work step, say clearly:

  ```text
  Upload needed? Yes or No
  Destination: [only if Yes]
  Files/data: [only if Yes]
  Reason: [only if Yes]
  ```

  Never upload, send, publish, or share a file/data item unless the owner explicitly chooses that upload step.
- Required future areas: Agent Builder, Ask a Question / “How do I?” tutorials, online/offline behavior, GitHub/Kaggle/Colab boundaries, browser experience, and selected-app coordination only through legitimate supported routes.
- Never claim universal phone/app control. Preserve narrow scope, owner Take Over/Pause/Stop, sensitive/manual handoffs, and privacy boundaries.
- Never solicit or accept GitHub/Kaggle/Colab credentials in chat, source, logs, notebooks, or handoff documents.
- The intended future notebook remains `4CBOn2_Kaggle6.ipynb` at `mohamtur1/4CBOn2`.
- The only selected future in-notebook AI library is `from google.colab import ai`; do not silently substitute another provider.

## 3. Current division status

| Division | Status | Meaning |
| --- | --- | --- |
| 01–08 | Finished | Scope, toolchain, Android foundation, navigation, credential boundary, and GitHub public-read boundary completed/verified as documented. |
| 09 — Kaggle read access | Blocked by legitimate authorization | Public competition-list endpoint returned HTTP 401. The fixed read-only boundary is implemented and tested; no real account/token/OAuth/device test occurred. |
| 09a — Local Kaggle token route | **Next; not started** | Decide/verify a documented manually created token format and create a local-only entry route before any real token is used. |
| 10 — Colab entry/control route | Finished (manual route only) | Fixed manual-handoff reference compiled/tested. The exact planned notebook is currently missing from the public repository path. No browser launch, account, runtime control, or AI call exists. |
| 10a — toolchain restore/verification | Finished | Toolchain restored and Division 10 compile/test/APK checks passed. |
| 11–42 | Not started | Not tested. |

**Authoritative plan:** `/home/user/4CBOn2_Build_Divisions.md`

## 4. Latest verified Android build

```text
Gradle tasks: :app:testDebugUnitTest :app:assembleDebug
Unit tests: 15 passed, 0 failures, 0 errors, 0 skipped
APK v2 signature: passed
Generated debug APK SHA-256:
e3250cd2c8aa86bd910e73575ce59f074618cccecf9219f5b1762e9de5169e07
```

This is a local debug verification artifact, not a retained release artifact, production build, or device-installed test.

The historical retained root APK at `/home/user/4CBOn2-debug.apk` predates Divisions 06–10 and must not be represented as the latest source build.

## 5. Current source and documents

### Android project

```text
/home/user/android/
```

Key source boundaries:

- `AndroidKeyStoreCredentialStore.kt` — future AndroidKeyStore AES-GCM storage boundary. No local entry UI, populated secret, or device-keystore test.
- `GitHubRepositoryReadClient.kt` — fixed read-only public/authorized GitHub metadata route. No authenticated app/device test and no write operation.
- `KaggleCompetitionReadClient.kt` — fixed read-only Kaggle competition-list route. Its public route reports authorization-required; authorized route expects a future locally stored Bearer access token but no real token has been used.
- `ColabNotebookReference.kt` — fixed manual-handoff-only notebook reference. It never launches a browser, sends a request, signs in, creates a runtime, or calls AI.

Key evidence documents:

```text
android/GITHUB_READ_ACCESS.md
android/KAGGLE_READ_ACCESS.md
android/KAGGLE_OAUTH_REGISTRATION_REQUEST.md
android/COLAB_ENTRY_ROUTE.md
android/CREDENTIAL_STORAGE.md
android/TOOLCHAIN_PROVISIONING.md
android/README.md
```

## 6. Service-route facts — never overclaim

### GitHub

- Unauthenticated, no-body build-environment GET probes previously returned HTTP 200 for the public metadata and root contents of `mohamtur1/4CBOn2`.
- This proves public visibility at that time only—not private/authenticated access, device access, repository write, or notebook existence.
- Do not request a PAT in chat.

### Kaggle

- The unauthenticated no-body GET to:

  ```text
  https://www.kaggle.com/api/v1/competitions/list?group=general
  ```

  returned HTTP 401.
- No Kaggle account, API token, OAuth client, competition join, data download, notebook action, or submission occurred.
- An OAuth public-client registration request is prepared but **unsent** at `android/KAGGLE_OAUTH_REGISTRATION_REQUEST.md`.
- The owner asked whether they can instead create a scoped token on Kaggle’s website and paste it into the future local app UI. This may be a suitable owner-operated/private-prototype route only if the exact Kaggle token format, scope controls, and endpoint compatibility are documented and verified. Do not assume a generic personal/API token is compatible with the current `Authorization: Bearer` route.

### Colab

- The public direct route for the intended notebook reported “Notebook not found” at the repository/branch path during testing.
- `google.colab.ai` is an in-notebook Python library, not an Android remote-control API. Its genuine, bounded probe belongs to Division 30.
- No Google/Colab sign-in, credential, runtime, AI call, or browser/UI automation occurred.

## 7. Required next work: Division 09a

**Goal:** Prepare the safer, local-only Kaggle-token option before any real credential exists or is entered.

### Narrow work order

1. Review Kaggle’s currently documented token mechanism and verify whether a token created through the owner’s Kaggle website can authorize the selected read-only competition-list endpoint.
2. Distinguish any personal API key/token format from an OAuth Bearer access token. Do not label them interchangeable without evidence.
3. Design/implement only a local native token-entry screen and a clear read-only permission explanation if the route is supported.
4. Use the existing AndroidKeyStore storage boundary; do not log/display/store a pasted token in source, test fixtures, screenshots, or chat.
5. Add no token-value test. Test only empty/missing/cancelled UI/credential states locally.
6. Do not ask the owner to enter a token until a local entry screen exists and an explicit device-test step is ready.
7. Build/test/document one small increment, then stop before Division 11.

### Upload required for 09a?

```text
No.
```

No GitHub/Kaggle/Colab upload, account request, or real credential is needed to start or complete the local UI/design portion.

## 8. Toolchain and repeatable build

The workspace-local toolchain is intentionally nonpersistent and may disappear between sessions.

Expected root if available:

```text
/home/user/.cache/4cbon2-android-toolchain
```

Pinned/verified baseline:

```text
Temurin JDK 17.0.21+9
Gradle 9.4.1
Android command-line tools 23.0.0
Android platform-tools 37.0.1
Android platforms 36 and 37.0
Android Build Tools 36.0.0
```

If available, build with:

```bash
chmod +x /home/user/android/gradlew
source /home/user/.cache/4cbon2-android-toolchain/provision.env
cd /home/user/android
./gradlew --no-daemon --console=plain --max-workers=1 :app:testDebugUnitTest :app:assembleDebug
```

Current proven constrained-build settings in `android/gradle.properties`:

```text
org.gradle.jvmargs=-Xmx640m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8
org.gradle.workers.max=1
kotlin.compiler.execution.strategy=in-process
```

If absent, restore from `android/TOOLCHAIN_PROVISIONING.md`, verify the recorded checksums, and do not claim a source change was built until restoration succeeds.

## 9. Phone-control safety design retained

Future selected-app coordination follows:

```text
Authorized API -> supported Android intent/app link/share -> permitted fixed human-defined routine -> manual handoff
```

Never expose unrestricted screen-reading/tapping to a cloud planner. Scope contracts must record the goal, selected apps/accounts, data/action/recipient/file bounds, cloud-transfer permission, completion mode, limits, expiry, and exclusions.

Initial owner-started proposal: five minutes or twenty actions. Take Over/Pause/Stop stay local; takeover must reject stale/late commands and require explicit resume plus a fresh state check.

Banking/payment/password/OTP/biometric/permission/security-setting workflows remain manual-only initially.

## 10. Backup/new-thread files

The current workspace archive is intended to be uploaded to the GitHub repository root together with this handoff:

```text
4CBOn2_Checkpoint_Div10_Part9_source_docs.zip
4CBOn2_New_Thread_Handoff.md
```

The archive contains source, tests, Gradle configuration, current plans/evidence, and durable Markdown design documents. It excludes generated caches/build folders, local settings, APK binaries, and credentials.

When the owner says the two replacement files are uploaded, perform only an unauthenticated public metadata/content-name/hash check. Do not use a credential or make any repository write.

## 11. Ready-to-use new-thread message

> Continue the 4CBOn2 Android prototype from the attached `4CBOn2_New_Thread_Handoff.md` and `4CBOn2_Checkpoint_Div10_Part9_source_docs.zip`. Follow the security and upload boundaries. Begin only with Division 09a — Prepare local Kaggle token route. Explain each update both like I am two years old and as an adult, unless I explicitly ask for only one style. Before every step state whether an upload is needed. Do not request or accept credentials in chat.
