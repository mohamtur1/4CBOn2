# 4CBOn2 Android Prototype — Build Toolchain Checkpoint

**Build division:** 03 — Prepare Android build tools  
**Status:** Checkpointed — toolchain is specified, but the local environment is not yet provisioned  
**Date:** 23 October 2026

## Like you are two

We found some building tools, but not the special Android blocks yet.

We know exactly which blocks we need next.

We do not start making the house until the right blocks are ready.

## Like you are an adult

The local workspace has basic download/archive/checksum tools and can reach the Android and Gradle download servers, but it does not have an Android SDK, Gradle, Kotlin compiler, Android emulator, Android platform tools, or a suitable JDK for the proposed modern Android build.

This checkpoint pins a compatible toolchain baseline and records the dependency gate. It does not create an Android project, download SDK packages, alter system packages, or prove that an APK can compile.

## Local inspection results

| Component | Actual result | Meaning |
| --- | --- | --- |
| Host | Linux x86_64 | Plausible Linux Android build host; no device/emulator conclusion follows |
| Java/Javac | OpenJDK 11 | Present, but below selected JDK 17 baseline |
| Gradle | Not found | A Gradle wrapper cannot run until a compatible JDK and distribution are supplied |
| Kotlin compiler | Not found | Expected to arrive through the Gradle build after bootstrap; not a standalone blocker by itself |
| Android SDK | Not found in inspected standard locations | No `platforms`, `build-tools`, or command-line manager available |
| Android platform tools / ADB | Not found | No physical-device or emulator interaction is currently possible |
| Android emulator / AVD manager | Not found | No local emulator test path currently exists |
| APK tools | `aapt`, `aapt2`, `zipalign`, `apksigner`, `apkanalyzer` not found | No package inspection/signing path currently exists |
| Workspace Android project | Not found | No source or APK has been created yet |
| Bootstrap utilities | `curl`, `wget`, `unzip`, `zip`, `tar`, `sha256sum`, `git`, and Python found | Sufficient to bootstrap workspace-local tools after the next explicit continuation |
| HTTPS reachability | HEAD requests to `dl.google.com` and `services.gradle.org` returned HTTP 200 | Download routes appear reachable at this moment; no package was downloaded or checksum verified |

## Pinned baseline for the first Android source build

| Item | Decision | Basis / caveat |
| --- | --- | --- |
| Host JDK | **17** | Android Gradle Plugin 9.2 lists JDK 17; Java 11 found locally is insufficient for this selected baseline |
| Android Gradle Plugin | **9.2.0** | Official compatibility table lists Gradle 9.4.1, JDK 17, Kotlin Gradle Plugin 2.2.10, and support through API 37 |
| Gradle Wrapper | **9.4.1** | Official default/minimum match for selected AGP release |
| Kotlin Gradle Plugin | **2.2.10** | Official selected-AGP compatibility table value |
| Android compile SDK | **37** | Revised during the first actual build: the selected stable Compose/AndroidX dependencies require API 37 at compile time; selected AGP supports it |
| Android target SDK | **36** | Kept at the Play minimum baseline while runtime behavior changes for a higher target are tested separately |
| Android Build Tools | **36.0.0** | Official selected-AGP table default; confirm installed package during bootstrap |
| Android minimum SDK | **26** | Retained first-prototype design decision (Android 8.0 baseline); actual device coverage remains untested |
| UI implementation | Kotlin + Compose | Dependency/BOM versions intentionally wait until project creation so they can be locked in one version catalog and tested as a compatible set |
| Dependency retrieval | Gradle Wrapper only after bootstrap | No global Gradle assumption; dependencies must be version-locked, attributable, and reviewed |

Official references checked for this checkpoint:

- [Android Gradle Plugin 9.2 compatibility](https://developer.android.com/build/releases/gradle-plugin)
- [Google Play target API requirement](https://developer.android.com/google/play/requirements/target-sdk)
- [Kotlin Gradle/JDK toolchain guidance](https://kotlinlang.org/docs/gradle-configure-project.html)

## Required bootstrap inputs — no credentials

The next continuation needs workspace-local, reproducible tooling:

1. A JDK 17 distribution, with its source/checksum recorded.
2. Android command-line tools from the official Android distribution, with source/checksum recorded.
3. SDK packages: `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`, and the selected command-line tooling.
4. A Gradle Wrapper 9.4.1 generated or verified only after JDK 17 is available.
5. License acceptance performed interactively/non-deceptively with the official SDK manager, then logged as a local environment state—not placed in source.

An emulator system image is **not** required for the first source compile. It will be a separately recorded optional test dependency after the basic project compiles. A real-device test will require a device and owner action later.

## Bootstrap security and reproducibility rules

- Download only from official/verified distribution endpoints; record version, URL, date, and SHA-256 where supplied or calculated.
- Install tools under a local non-secret workspace location; do not commit SDK binaries, JDK binaries, generated caches, secrets, or license files to the future repository.
- Do not use `curl | sh`, random third-party scripts, root, or system-wide package changes for convenience.
- Do not proceed on a checksum/source mismatch.
- Do not accept Android SDK licenses by pretending that acceptance occurred. Record the actual interactive result.
- Tool download success is not an APK build success. The first compile remains Division 04/05 verification.
- No GitHub PAT, Kaggle token, Colab credential, or phone permission is needed for bootstrap.

## Blocker / continuation decision

**Blocker:** The selected JDK 17, Android SDK command-line tools, SDK packages, and Gradle bootstrap are absent.

**Required continuation:** **03a — Provision and verify workspace-local Android toolchain.** It will be separately timeboxed to 30 minutes. If downloads or license interaction exceed the limit, it will checkpoint without creating an Android project or falsely claiming a build.

## Completion check for this checkpoint

- [x] Java, Gradle, SDK, Android tools, emulator, APK tools, workspace project state, disk space, and network reachability inspected.
- [x] A compatible current toolchain baseline is selected and sourced.
- [x] Missing local dependencies and the exact blocker are recorded.
- [x] No source, APK, account credential, Android permission, license acceptance, or external service action was created.
- [x] A safe, reproducible bootstrap continuation is defined.

**Next eligible work:** 03a — Provision and verify workspace-local Android toolchain.
