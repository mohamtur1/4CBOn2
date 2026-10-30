# 4CBOn2 — New-Thread Engineering Handoff (Division 10 + Part 9 Update)

> **Division 11 update (26 October 2026):** The current active source has 25 tests, not 21. A file-backed local entity store and a synthetic close/reopen test were added, and no-artifact CI run [40316111676](https://github.com/mohamtur1/4CBOn2/actions/runs/40316111676) passed at commit `eef1a18d671db2747ea5d4ca7c739e696079377d`. Debug APK SHA-256: `471efe77c87f83088796a7710295e353e5cf10f8e9a6a735c01be46d1a676b3a`; artifact count: zero. See `4CBOn2_Division11_Verification.md` and the updated `4CBOn2_Next_Thread_Handoff.md`. Historical 21-test records below remain historical.
**Prepared:** 23 October 2026  
**Purpose:** Continue the native Android prototype from a fresh, short conversation without depending on the prior long thread.  
**Current safe stop:** Revised Divisions **09/09b** are finished for source/CI verification. Division **09c** recorded the installed Kaggle app opening the correct page before sign-in, followed by owner-performed sign-in, Cancel, and Back; no join/submission or credential sharing occurred. Browser-specific and unreported subchecks remain unverified. A wording-only correction must pass CI before **Division 11 — Local database** starts. See `4CBOn2_Next_Thread_Handoff.md`.

---

## 1. Security and repository facts

- A GitHub Personal Access Token was exposed in the prior conversation and the owner reports that it has been **revoked**.
- Never repeat, search for, request, accept, store, use, or include that token in any artifact.
- Earlier GitHub verification was read-only public API work. Later source/workflow commits were pushed to the assigned branch only after separate exact-step owner approvals; no credential was requested in chat, CI made no repository writes, and no build artifact was uploaded.
- The current Arena environment provides configured Git/GitHub tooling and an assigned working branch. This removes the old manual owner-upload requirement, but access does not imply approval for an external write.
- Local reading, editing, and testing need no upload. Before any push, pull request, issue, release, or other external transfer, state the exact destination, files/data, and reason and obtain explicit owner approval for that step.
- Never request a PAT or other GitHub credential in chat. Keep the original recovery archive; current local edits do not require the owner to upload a replacement checkpoint.

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
| 09 — Manual Kaggle competition handoff | **Finished for manual source/build verification** | API/token/OAuth code removed; validated competition URL and explicit browser handoff added. Static checks and 21 CI unit tests passed. No browser/device/competition test. See `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md`. |
| 09a — Former local Kaggle token route | **Superseded; no further work** | Owner selected manual participation; token/API source and tests removed, historical evidence retained. |
| 09b — Verify revised manual Kaggle handoff | **Finished** | Approved CI run 40228035751 passed compilation, 21 tests, fresh debug APK assembly, zip alignment, v2 signature, SHA-256, and clean-source checks. No artifact was retained; device/competition tests remain separate. |
| 09c — Owner external-handoff smoke test | **Finished for recorded scope** | Installed Kaggle app opened the correct page before sign-in; owner later signed in personally and performed Cancel/Back. No join/submission or credential sharing; browser-specific and unreported subchecks remain unverified. |
| 10 — Colab entry/control route | Finished (manual route only) | Fixed manual-handoff reference compiled/tested. The exact planned notebook is currently missing from the public repository path. No browser launch, account, runtime control, or AI call exists. |
| 10a — toolchain restore/verification | Finished | Toolchain restored and Division 10 compile/test/APK checks passed. |
| 11 — Local database | Finished for source/build scope | File-backed local entity store with synthetic close/reopen test; no-artifact CI run 40316111676 passed 25 tests plus APK verification with zero artifacts. |
| 12 — Selected-app capability registry | **Finished for source/build scope** | Owner-selected app identity, least-to-most-powerful route ordering, explicit unavailable reasons, and narrow non-enumerating discovery; 15 synthetic JVM tests bring the suite to 40. No-artifact CI run 40812912554 passed 40 tests plus APK verification with zero artifacts. No UI, persistence, or installed-app inventory. |
| 13 — On-device scope contract | **Finished for source/build scope** | Bounded per-job scope with target/action allow-lists, exclusions, cloud transfer off by default, 20-action/5-minute caps, expiry, and pre-discovery refusals; 15 synthetic JVM tests bring the suite to 55. No-artifact CI run 40831655312 passed 55 tests plus APK verification with zero artifacts. No UI, persistence, real data, or network. |
| 14 — Takeover and cancellation lease | **Finished for source/build scope** | Local owner Take Over/Pause/Stop with owner-only resume, epoch-bound stale-command rejection, terminal stop, and a fresh post-resume state check; lease is evaluated before scope and discovery. 16 synthetic JVM tests bring the suite to 71. No-artifact CI run 40987374233 passed 71 tests plus APK verification with zero artifacts. No UI wiring, persistence, network, or authentication claim. |
| 15 — Offline agent drafts | **Finished for source/build scope** | Create/edit/list/load drafts over the existing entity database at unchanged schema version 1, with an explicit state machine, terminal cancel, paired events, and inert behaviour toward registry/scope/lease. 15 synthetic JVM tests bring the suite to 86. No-artifact CI run 40989542029 passed 86 tests plus APK verification with zero artifacts. No UI, encryption, network, or cap persistence. |
| 16 — Safe file import/export | **Finished for source/build scope** | Owner-picked metadata only, exact-match MIME allow-list, 5 MiB cap on reported size, six fail-closed refusals with size before type, and a plan-only export side. No byte/stream/file/path accessor exists; no storage permission, directory walking, picker wiring, or content transfer. 17 synthetic JVM tests bring the suite to 103; schema stays version 1. No-artifact CI run 40990799099 passed 103 tests plus APK verification with zero artifacts. |
| 17–42 | Not started | Not tested. |

**Authoritative plan:** `/home/user/4CBOn2/4CBOn2_Build_Divisions.md`

## 4. Latest verified Android build

```text
Commit: d1be9aa3bfd15fcce30277e4f676e03326e29f2a
GitHub Actions run: 40228035751
:app:testDebugUnitTest — 21 passed, 0 failures, 0 errors, 0 skipped
:app:assembleDebug — passed
APK zip alignment — passed
APK v2 signature — passed
Generated debug APK SHA-256:
02eb96bebda5563bc00f55e35af7356d142e9b96185b896f877110dcc6b14264
```

Run receipt: https://github.com/mohamtur1/4CBOn2/actions/runs/40228035751

This was an ephemeral CI debug verification result. The APK was not uploaded or retained as an artifact, and it is not a production build, production signature, device-installed test, or live Kaggle result. Older Division 10 and retained root APK results are historical and must not be represented as the latest source build.


## 5. Current source and documents

### Android project

```text
/home/user/4CBOn2/android/
```

Key source boundaries:

- `AndroidKeyStoreCredentialStore.kt` — AndroidKeyStore AES-GCM storage boundary for the separate future GitHub route. The Kaggle slot was removed; no populated secret or device-keystore test exists.
- `KaggleCompetitionHandoff.kt` — strict competition-URL validation and secret-free manual-handoff state. No joined/submitted status or network/client dependency exists.
- `MainActivity.kt` — Home-linked manual competition screen; an explicit tap requests `ACTION_VIEW`. During 09c the installed Kaggle app handled it and showed the correct page; wording now says external app or browser because Android does not guarantee which handler answers.
- `GitHubRepositoryReadClient.kt` — fixed read-only public/authorized GitHub metadata route. No authenticated app/device test and no write operation.
- `ColabNotebookReference.kt` — fixed manual-handoff-only notebook reference. It never launches a browser, sends a request, signs in, creates a runtime, or calls AI.

Key evidence documents:

```text
android/GITHUB_READ_ACCESS.md
android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md
android/DIV09B_TOOLCHAIN_CHECKPOINT.md
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

- The active app has no Kaggle API client, token entry/storage, OAuth flow, account/session access, competition-list read, automated download/upload, or submission path.
- The historical unauthenticated competition-list request returned HTTP 401; that read client and the later token-entry source have been removed.
- Revised Division 09 validates only `https://www.kaggle.com/competitions/<slug>` and requests `ACTION_VIEW` after an explicit owner tap. `OPEN_REQUESTED` is not proof of page visibility or account action.
- The owner personally handles Kaggle sign-in, rules acceptance, joining, and submission. During 09c the owner signed in personally in the Kaggle app after its correct page opened; no credential was shared with 4CBOn2 or the agent, and no competition join or submission occurred.
- Divisions 23/29/37/38 are replanned for manual tracking, file-based notebook handoff, offline preparation, and owner-recorded submission receipts. They have not started.
- Never request Kaggle credentials or replace manual participation with cookie/session capture or hidden website automation.

### Colab

- The public direct route for the intended notebook reported “Notebook not found” at the repository/branch path during testing.
- `google.colab.ai` is an in-notebook Python library, not an Android remote-control API. Its genuine, bounded probe belongs to Division 30.
- No Google/Colab sign-in, credential, runtime, AI call, or browser/UI automation occurred.

## 7. Revised Division 09/09b closure and next eligible division

The owner selected manual Kaggle competition participation. Revised Division 09 removed the active Kaggle API/token/OAuth route and added strict competition-URL validation plus an explicit `ACTION_VIEW` handoff. Former Division 09a is superseded and requires no further work.

Revised Division 09b used separately approved source/workflow pushes. Final run [40228035751](https://github.com/mohamtur1/4CBOn2/actions/runs/40228035751) at commit `d1be9aa3bfd15fcce30277e4f676e03326e29f2a` passed compilation, exactly 21 unit tests with no failures/errors/skips, fresh debug APK assembly, zip alignment, APK Signature Scheme v2, SHA-256, and clean-source checks. The verified APK SHA-256 is `02eb96bebda5563bc00f55e35af7356d142e9b96185b896f877110dcc6b14264`. The artifact count was zero: the APK was not uploaded or retained.

The local sandbox toolchain/dependency blocker remains historical environment evidence in `android/DIV09B_TOOLCHAIN_CHECKPOINT.md`; the approved CI route is not described as local restoration. Revised 09/09b are closed for source/build verification. During 09c the installed Kaggle app opened the correct page before sign-in, after which the owner signed in personally and performed Cancel/Back. No join/submission or credential sharing occurred. Browser-specific and unreported subchecks remain unverified.

**Next prepared work:** Division 16 passed no-artifact CI run 40990799099 at commit `47c5b16` (103 tests, APK verified, zero artifacts) and is closed for its documented source/build scope. Division 17 — Offline knowledge library is next, in a new maximum-30-minute session. The 09c intent implementation is unchanged, so no repeat phone test is required solely for that wording change. No PR is required. Real Kaggle joining/submission remains later owner-driven work.

Local reading/editing/testing needs no upload. Before any source/workflow push or other external write, state **Upload needed? Yes**, identify the exact destination, files/data, and reason, and obtain explicit owner approval for that step.

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
Android command-line tools package 23.0 (archive 16111833; CI sdkmanager reports 1.1.16717390 (Android CLI))
Android platform-tools 37.0.1
Android platforms 36 and 37.0
Android Build Tools 36.0.0
```

If available, build with:

```bash
chmod +x /home/user/4CBOn2/android/gradlew
source /home/user/.cache/4cbon2-android-toolchain/provision.env
cd /home/user/4CBOn2/android
./gradlew --no-daemon --console=plain --max-workers=1 :app:testDebugUnitTest :app:assembleDebug
```

Current proven constrained-build settings in `android/gradle.properties`:

```text
org.gradle.jvmargs=-Xmx640m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8
org.gradle.workers.max=1
kotlin.compiler.execution.strategy=in-process
```

If absent, restore from `android/TOOLCHAIN_PROVISIONING.md`, verify the recorded checksums, and do not claim a source change was built until restoration succeeds.

The separately approved build-only CI alternative is `.github/workflows/android-division09b.yml`. It uses an unauthenticated public checkout, checksum-pinned tools, `contents: none`, no secrets, no CI repository writes, and no artifact upload. Its successful revised-09 receipt is run 40228035751. A new external build or workflow change still requires exact-step owner approval.

## 9. Phone-control safety design retained

Future selected-app coordination follows:

```text
Authorized API -> supported Android intent/app link/share -> permitted fixed human-defined routine -> manual handoff
```

Never expose unrestricted screen-reading/tapping to a cloud planner. Scope contracts must record the goal, selected apps/accounts, data/action/recipient/file bounds, cloud-transfer permission, completion mode, limits, expiry, and exclusions.

Initial owner-started proposal: five minutes or twenty actions. Take Over/Pause/Stop stay local; takeover must reject stale/late commands and require explicit resume plus a fresh state check.

Banking/payment/password/OTP/biometric/permission/security-setting workflows remain manual-only initially.

## 10. Backup/new-thread files

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` and the repository recovery documents as historical backups. They contain source, tests, Gradle configuration, plans/evidence, and durable design documents while excluding generated caches/build folders, local settings, APK binaries, and credentials.

Current local branch edits do not require the owner to upload replacement files manually. Create another source/document checkpoint before replacing or removing any prior checkpoint, and keep generated artifacts and credentials out of it.

A local commit is not an upload. Any push, pull request, release, or other external write requires its own explicit owner-approved destination/files/reason step. Never use or request a credential in chat as part of checkpointing.

## 11. Ready-to-use new-thread message

> Continue the 4CBOn2 Android prototype on branch `arena/0eb49904-4cbon2` from `4CBOn2_Next_Thread_Handoff.md`, `4CBOn2 — New-Thread Engineering.md`, and `4CBOn2_Build_Divisions.md`. First confirm green CI for the 09c external-handoff wording correction; do not proceed if it failed. Then start only Division 11 — Local database, targeting initial entities and a persistence/restart test in one maximum-30-minute session. The owner’s Kaggle-app handoff and sign-in are documented; no join or submission occurred. Do not open a PR unless separately approved. Explain each update both like I am two years old and as an adult, unless I explicitly ask for only one style. Before every step state whether an upload is needed and obtain exact-step approval before any external write or transfer. Do not request or accept credentials in chat.
