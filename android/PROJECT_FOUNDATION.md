# 4CBOn2 Android Prototype — Minimal Project Foundation

**Build division:** 04 — Create the minimal Android project  
**Status:** Finished  
**Date:** 23 October 2026  
**Source root:** `android/`  
**Application ID:** `io.github.mohamtur1.fourcbon2`

## Like you are two

The helper house has its first little room now.

When it opens, it says: **4CBOn2**.

It also tells the truth: it is only a beginning. It cannot ask questions, teach you, use other apps, or use online accounts yet.

## Like you are an adult

The repository-local Android source skeleton now has a versioned Gradle/Kotlin/Compose configuration, an Android manifest, one launcher `MainActivity`, a minimal Compose foundation screen, resource strings, and a verified Gradle Wrapper.

It is intentionally not a simulated finished product. The rendered initial screen explicitly says no account connection, tutorial, agent, or phone-control permission is active.

## Created source structure

```text
android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/io/github/mohamtur1/fourcbon2/MainActivity.kt
│       └── res/values/strings.xml
├── gradle/libs.versions.toml
├── gradle/wrapper/                     # Generated Gradle Wrapper files
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradlew / gradlew.bat
├── .gitignore
└── README.md
```

The existing design, feasibility, toolchain, and scope documents remain alongside this source tree.

## Baseline actually used

| Setting | Verified value |
| --- | --- |
| Language/UI | Kotlin + Jetpack Compose |
| Application ID / namespace | `io.github.mohamtur1.fourcbon2` |
| Minimum SDK | 26 |
| Compile SDK | 37 |
| Target SDK | 36 |
| JDK | 17.0.21 |
| Android Gradle Plugin | 9.2.0 |
| Gradle Wrapper | 9.4.1 with distribution SHA-256 pin |
| Kotlin support | AGP 9 built-in Kotlin plus the Kotlin Compose compiler plugin |
| Stable AndroidX/Compose versions | Version-catalog pinned and resolved during the build |

### Why compile SDK changed from 36 to 37

The selected stable Compose/AndroidX versions reported an AAR metadata requirement for compile API 37. The official selected AGP supports API 37. Compile SDK determines which APIs/dependency metadata the code can compile against; it is distinct from `targetSdk`, which remains 36 pending separate behavior-change tests. This was an observed build requirement, not an assumed feature claim.

### AGP 9 Kotlin migration

The initial configuration applied `org.jetbrains.kotlin.android` and failed because AGP 9 already enables built-in Kotlin. The final project correctly removes that duplicate plugin and keeps the separate Compose compiler plugin. This follows Android’s AGP 9 built-in Kotlin migration guidance.

### Sandbox memory adaptation

The sandbox has about 1.9 GiB RAM with no swap. An initial build daemon and a stale Kotlin compiler daemon caused kernel out-of-memory termination. The final configuration uses a single Gradle worker and capped Gradle/Kotlin memory. The successful build used this constrained configuration. This is a sandbox build setting, not a phone runtime requirement.

## Verification performed

| Check | Actual result |
| --- | --- |
| Gradle Wrapper generation | Passed |
| Wrapper version check | Passed: Gradle 9.4.1 / provisioned JDK 17.0.21 |
| Kotlin compile task | Passed: `:app:compileDebugKotlin` |
| Debug package task | Passed: `:app:assembleDebug` |
| Debug artifact validation | `apksigner verify --verbose` passed with one v2-scheme debug signer |
| APK manifest/package inspection | Passed: expected application ID, version `0.1.0-dev`, min SDK 26, target SDK 36, compile SDK 37, and launcher activity |
| Unit-test task | Passed but reported `NO-SOURCE`; no test assertions exist yet |
| Emulator / physical device | Not installed or tested |
| Account/service/PAT integration | Not implemented or tested |

The successful verification-only debug output had SHA-256:

```text
b568e5a46b9fd80e9d8c64165418479e065205e10c645af481e2287f4ee9dc5e
```

It is a temporary build output under `app/build/`, which is deliberately ignored and excluded from source snapshots. It is **not** yet the formal first debug deliverable. That belongs to Division 05, which will rebuild, record the artifact and checksum intentionally, and keep this distinction clear.

## Explicitly not included

- Ask a Question / How-do-I interface.
- Agent profiles, task queue, database, files, or offline library.
- Colab, GitHub, Kaggle, browser, or account adapters.
- PAT/token input or storage.
- Accessibility service, app inventory, screen capture, foreground service, or other phone-control permission.
- Notification, network, storage, camera, location, SMS, call-log, or broad file permission.
- Emulator/phone installation or production signing.

## Completion check

- [x] A minimal native Android project was created under the planned `android/` source root.
- [x] It uses the planned Android application ID and Kotlin/Compose implementation direction.
- [x] It displays a truthful foundation screen without pretending later capability exists.
- [x] Wrapper and build configuration are present and source-controlled in principle; local paths, SDKs, build outputs, and secrets are ignored.
- [x] The project compiled and assembled a debug package in the active sandbox.
- [x] Package signing and manifest metadata were inspected.
- [x] No account, credential, special phone-control permission, or live service action was used.

**Next eligible division:** 05 — Produce the first debug APK as a deliberate, reproducible development artifact.
