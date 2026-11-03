# 4CBOn2 Android Prototype — Toolchain Provisioning Record

**Build continuation:** 03a — Provision and verify workspace-local Android toolchain  
**Status:** Finished  
**Date:** 23 October 2026  
**Scope:** Development-tool setup only. No Android application source, Gradle project, APK, emulator/physical-device test, account connection, phone permission, or PAT was created or used.

## Like you are two

We brought the special Android building blocks to the workshop.

We checked the names on the blocks to make sure they are the right ones.

Now the blocks are ready. Next time, we can start making the little helper house.

## Like you are an adult

A workspace-local Android build toolchain is now available in the active sandbox. The JDK archive and Gradle archive passed SHA-256 verification against publisher-provided values. The Android command-line-tools archive passed the SHA-1 value published in the official Android repository metadata; its calculated SHA-256 is recorded below for future comparison.

Only packages needed to create and compile the first basic Android project were installed. No emulator image was installed. No Android application source has been generated, so successful tooling installation is not yet evidence that the project or APK will compile.

## Provisioned components and verification

| Component | Actual verified version / result |
| --- | --- |
| JDK | Eclipse Temurin `17.0.21+9`; `java` and `javac` both report 17.0.21 |
| Gradle | `9.4.1`; launcher JVM is the provisioned JDK 17 |
| Android command-line tools | `23.0.0` reported by the SDK manager |
| Android platform tools | `37.0.1`; `adb version` executed successfully |
| Android platforms | `platforms/android-36` revision `2.0.0` and `platforms/android-37.0` revision `2.0.0`; both `android.jar` files present. API 37 was added during the first project build because selected stable Compose/AndroidX dependencies require it at compile time. |
| Android Build Tools | `36.0.0`; `aapt2` and `apksigner` present and executable |
| Android SDK licenses command | The current CLI stated that `sdkmanager --licenses` is no longer needed; required packages then installed successfully |
| Emulator | Not installed or tested |
| Android project/APK | Not created |

## Provenance and integrity record

| Artifact | Source | Integrity record |
| --- | --- | --- |
| Temurin JDK 17.0.21+9 | Adoptium metadata selected the package `OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz` | Publisher metadata SHA-256 verified: `8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711` |
| Android command-line tools 23.0 | Official Android repository metadata, archive `commandlinetools-linux-16111833_latest.zip` | Official repository SHA-1 verified: `e025545c62a8e64c7559119566a569fb1dec5f60`; calculated SHA-256 recorded: `0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8` |
| Gradle 9.4.1 binary | Official Gradle distribution | Publisher `.sha256` verified: `2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb` |

The JDK source resolved at provisioning time to:

```text
https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz
```

The local toolchain root is:

```text
/home/user/.cache/4cbon2-android-toolchain
```

It intentionally sits in a workspace cache location. Tool binaries and SDK packages are not source deliverables and may not survive a later sandbox snapshot. The source requirements, versions, checksums, and build files will remain in the normal workspace; if the local toolchain is reset, it must be reprovisioned from this record and reverified—not assumed to exist.

## Permitted next use

Division 04 may use this local toolchain to create a minimal Kotlin/Compose Android project. It must:

- Keep the Android SDK/JDK/Gradle binary directories out of the future repository.
- Add only the source, Gradle Wrapper files, version catalog/build configuration, and minimal app files needed for the narrow project target.
- Use the pinned JDK 17 / AGP 9.2.0 / Gradle 9.4.1 / Kotlin Gradle Plugin 2.2.10 / compile-target API 36 baseline unless an actual compatibility failure requires a documented change.
- Avoid credentials, services, phone-control permissions, and unreviewed third-party SDKs.
- Verify the Gradle configuration or compile result honestly; source creation is not automatically a successful build.

## Completion check

- [x] Workspace-local JDK 17 was downloaded and SHA-256 verified.
- [x] Workspace-local Android command-line tools were downloaded and verified against official repository metadata.
- [x] Required Android API 36 platform, Build Tools 36.0.0, and platform-tools packages were installed and their required artifacts checked.
- [x] Workspace-local Gradle 9.4.1 was downloaded and SHA-256 verified.
- [x] JDK, Gradle, ADB, AAPT2, APK signer, and SDK package presence were checked.
- [x] No root/system-wide installation, credential, APK, Android source project, emulator, device test, or account connection was used.

**Next eligible division:** 04 — Create the minimal Android project.
