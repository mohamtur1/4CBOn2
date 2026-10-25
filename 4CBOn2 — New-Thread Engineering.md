# 4CBOn2 — New-Thread Engineering Handoff (Division 10 + Part 9 Update)

**Prepared:** 23 October 2026  
**Purpose:** Continue the native Android prototype from a fresh, short conversation without depending on the prior long thread.  
**Current safe stop:** Revised Division **09** replaces Kaggle API/token work with a manual competition handoff and is source/static-checkpointed but not compiled or finished. Former Division **09a** token work is superseded and its active code has been removed. Revised Division **09b** must verify the manual handoff before Division 11; the sandbox toolchain/dependency blocker remains.

---

## 1. Security and repository facts

- A GitHub Personal Access Token was exposed in the prior conversation and the owner reports that it has been **revoked**.
- Never repeat, search for, request, accept, store, use, or include that token in any artifact.
- Earlier GitHub verification was read-only public API work. In this continuation, no remote push, commit, or credential use has been performed by the agent.
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
| 09 — Manual Kaggle competition handoff | **Source/static checkpoint; not finished** | API/token/OAuth code removed; validated competition URL and explicit browser handoff added. Static checks passed; 21 test methods present but unexecuted. No browser/device/competition test or current-source APK. See `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md`. |
| 09a — Former local Kaggle token route | **Superseded; no further work** | Owner selected manual participation; token/API source and tests removed, historical evidence retained. |
| 09b — Verify revised manual Kaggle handoff | **Next; not started** | Restore or approve a build route, compile/test the revised source, and verify a fresh debug APK before Division 11. Device/competition tests remain separate. |
| 10 — Colab entry/control route | Finished (manual route only) | Fixed manual-handoff reference compiled/tested. The exact planned notebook is currently missing from the public repository path. No browser launch, account, runtime control, or AI call exists. |
| 10a — toolchain restore/verification | Finished | Toolchain restored and Division 10 compile/test/APK checks passed. |
| 11–42 | Not started | Not tested. |

**Authoritative plan:** `/home/user/4CBOn2/4CBOn2_Build_Divisions.md`

## 4. Latest verified Android build

```text
Gradle tasks: :app:testDebugUnitTest :app:assembleDebug
Unit tests: 15 passed, 0 failures, 0 errors, 0 skipped
APK v2 signature: passed
Generated debug APK SHA-256:
e3250cd2c8aa86bd910e73575ce59f074618cccecf9219f5b1762e9de5169e07
```

This Division 10 result predates all Division 09/09a and revised manual-handoff source changes. It is a historical local debug verification artifact, not a current-source build, retained release artifact, production build, or device-installed test.

The historical retained root APK at `/home/user/4CBOn2-debug.apk` predates Divisions 06–10 and must not be represented as the latest source build.

## 5. Current source and documents

### Android project

```text
/home/user/4CBOn2/android/
```

Key source boundaries:

- `AndroidKeyStoreCredentialStore.kt` — AndroidKeyStore AES-GCM storage boundary for the separate future GitHub route. The Kaggle slot was removed; no populated secret or device-keystore test exists.
- `KaggleCompetitionHandoff.kt` — strict competition-URL validation and secret-free manual-handoff state. No joined/submitted status or network/client dependency exists.
- `MainActivity.kt` — Home-linked manual competition screen; an explicit tap requests `ACTION_VIEW`. No browser/device or real competition test has occurred.
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
- The owner personally handles Kaggle sign-in, rules acceptance, joining, and submission. No browser/device handoff, Kaggle account session, competition join, or submission was tested.
- Divisions 23/29/37/38 are replanned for manual tracking, file-based notebook handoff, offline preparation, and owner-recorded submission receipts. They have not started.
- Never request Kaggle credentials or replace manual participation with cookie/session capture or hidden website automation.

### Colab

- The public direct route for the intended notebook reported “Notebook not found” at the repository/branch path during testing.
- `google.colab.ai` is an in-notebook Python library, not an Android remote-control API. Its genuine, bounded probe belongs to Division 30.
- No Google/Colab sign-in, credential, runtime, AI call, or browser/UI automation occurred.

## 7. Revised Division 09 checkpoint and required next work: Division 09b

The owner selected manual Kaggle competition participation. Revised Division 09 removed the active Kaggle API/token/OAuth route and added strict competition-URL validation plus an explicit `ACTION_VIEW` handoff. Ten manual-handoff tests were added; the current source contains 21 test methods. Static checks passed, but nothing compiled and no tests executed. See `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md`.

Former Division 09a is superseded and requires no further work. Revised Division 09 is **checkpointed, not finished**. The local toolchain/dependency blocker documented in `android/DIV09B_TOOLCHAIN_CHECKPOINT.md` remains.

### Narrow revised 09b work order

1. Restore a verified local toolchain/dependency route, or prepare a separate external build route only with explicit owner approval for its exact upload/workflow step.
2. Run `:app:testDebugUnitTest` and `:app:assembleDebug` against the revised manual-handoff source.
3. Fix only revised Division 09 failures, rerun checks, and verify a fresh debug APK signature/checksum if assembly succeeds.
4. Update `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md` and the detailed status table honestly.
5. Treat browser/device handoff and real competition participation as separate, explicitly approved tests.
6. Stop before Division 11 unless revised Division 09 is verified and closed.

### Upload required for local 09b?

```text
No.
```

A remote CI build is not equivalent to local restoration. Before any source/workflow push or other external write, state **Upload needed? Yes**, identify the exact destination, files/data, and reason, and obtain explicit owner approval for that step.

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

> Continue the 4CBOn2 Android prototype from the attached `4CBOn2_New_Thread_Handoff.md` and `4CBOn2_Checkpoint_Div10_Part9_source_docs.zip`. Follow the security and upload boundaries. Continue only with revised Division 09b — verify the manual Kaggle competition handoff in `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md`; use a local verified toolchain or a separately approved external build route, and do not skip to Division 11. Explain each update both like I am two years old and as an adult, unless I explicitly ask for only one style. Before every step state whether an upload is needed. Do not request or accept credentials in chat.
