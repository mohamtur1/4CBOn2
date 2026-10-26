# 4CBOn2 Android

Native Kotlin/Compose Android companion for the 4CBOn2 prototype.

## Current state

The source foundation was created in Build Division 04 and its first local navigation was added in Division 06. It contains one launcher activity, five visible local destinations (Home, Ask, Agents, Browser, Activity), truthful unavailable-state screens, and a Home-linked manual Kaggle competition handoff checkpointed in revised Division 09.

The Ask destination visibly reserves **Ask a Question** and **How do I? tutorial** entry points, but they are deliberately disabled until their real local/online capabilities are implemented and verified.

It does **not** yet contain working question answers, tutorial plans, agents, local knowledge storage, active service connections, phone-control permissions, accessibility service, screen capture, general browser automation, user credentials, or an updated retained APK for the current source state. The revised Division 09 source has passed static checks but has not compiled because the toolchain/dependency blocker remains; revised Division 09b is required before Division 11.

The active Kaggle API client, token entry, token slot, and OAuth route have been removed. The manual Kaggle screen validates one competition URL and requests an external Android handler—an installed app or browser—only after an explicit owner tap. During 09c the installed Kaggle app opened the correct page before sign-in; the owner then signed in personally and performed Cancel/Back. No credential was shared, and no competition join or submission occurred. Browser-specific and unreported subchecks remain unverified. The Android Keystore boundary remains only for the separately scoped future GitHub credential route and is unpopulated. GitHub public metadata/root listing was previously visible from the build environment. A fixed, manual-handoff-only Colab notebook reference also exists; its target notebook was not publicly found. See `KAGGLE_MANUAL_COMPETITION_HANDOFF.md`, `CREDENTIAL_STORAGE.md`, `GITHUB_READ_ACCESS.md`, and `COLAB_ENTRY_ROUTE.md`.

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
