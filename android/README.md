# 4CBOn2 Android

Native Kotlin/Compose Android companion for the 4CBOn2 prototype.

## Current state

The source foundation was created in Build Division 04 and its first local navigation was added in Division 06. It contains one launcher activity, five visible local destinations (Home, Ask, Agents, Browser, Activity), and truthful unavailable-state screens.

The Ask destination visibly reserves **Ask a Question** and **How do I? tutorial** entry points, but they are deliberately disabled until their real local/online capabilities are implemented and verified.

It does **not** yet contain working question answers, tutorial plans, agents, local knowledge storage, app-side connection UI, phone-control permissions, accessibility service, screen capture, browser automation, credential-entry UI, user credentials, or an updated retained APK for the Division 06–10 source state.

A narrow Android Keystore-backed credential-storage boundary exists in source for future local GitHub/Kaggle entry, but it has no user interface or stored secret. Fixed, read-only GitHub and Kaggle probe boundaries also exist in source, but have no app UI and no authenticated device test. GitHub public metadata/root listing was visible from the build environment; the selected Kaggle competition-list endpoint returned authorization-required. A fixed, manual-handoff-only Colab notebook reference also exists; its target notebook was not publicly found, and it has no browser launch/runtime-control/AI call. See `CREDENTIAL_STORAGE.md`, `GITHUB_READ_ACCESS.md`, `KAGGLE_READ_ACCESS.md`, and `COLAB_ENTRY_ROUTE.md`.

## Get the APK without a build machine

GitHub Actions builds the debug APK and publishes it to a rolling release:

<https://github.com/mohamtur1/4CBOn2/releases/download/4cbon2-debug-apk-latest/4CBOn2-debug.apk>

Step-by-step phone instructions: [`APK_DOWNLOAD.md`](APK_DOWNLOAD.md).
Workflow: `.github/workflows/build-debug-apk.yml`.

## Baseline

- Application ID: `io.github.mohamtur1.fourcbon2`
- Minimum SDK: 26
- Compile SDK: 37 (required by the selected stable Compose/AndroidX set)
- Target SDK: 36 (pending separate runtime-behavior testing)
- JDK: 17
- Android Gradle Plugin: 9.2.0
- Gradle Wrapper: 9.4.1
- Kotlin Gradle Plugin: 2.2.10

Use `./gradlew :app:assembleDebug` after provisioning a compatible local Android SDK/JDK. `local.properties`, SDK/toolchain directories, secrets, and generated build output are intentionally ignored.

See the root `android/` design and build-status documents for scope and current verified status.
