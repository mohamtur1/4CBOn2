# 4CBOn2 APK — download and install from your phone

**Direct download (always the newest successful build):**

https://github.com/mohamtur1/4CBOn2/releases/download/4cbon2-debug-apk-latest/4CBOn2-debug.apk

Release page (same file, plus build notes): <https://github.com/mohamtur1/4CBOn2/releases/tag/4cbon2-debug-apk-latest>

## Current artifact

| Field | Value |
| --- | --- |
| File | `4CBOn2-debug.apk` |
| Size | 11,762,681 bytes (~11.2 MiB) |
| Package | `io.github.mohamtur1.fourcbon2` |
| Version | `0.1.0-dev` (versionCode 1) |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |
| Build type | Debug, debug-signed (sideload only, not Play-ready) |
| Launcher activity | `io.github.mohamtur1.fourcbon2.MainActivity` |
| Android requirement | Android 8.0 (API 26) or newer |

Built by GitHub Actions on `ubuntu-latest`: JDK 17 (Temurin), Android
command-line tools `16111833`, `platforms;android-37.0`, `build-tools;36.0.0`.
The CI job also runs `zipalign -c -v 4` and `apksigner verify --verbose` on the
APK before publishing it.

## Install with a phone browser (itel A60 / Android Go)

1. Tap the direct download link above. If GitHub shows the release page instead,
   tap the `4CBOn2-debug.apk` asset.
2. When Chrome says the file type can harm your device, choose **Download anyway**.
   This warning appears for every APK that is not installed from the Play Store.
3. Open **Files → Downloads → 4CBOn2-debug.apk**. (Or tap the download-complete
   notification.)
4. Android may block the install with *"For your security, your phone is not allowed
   to install unknown apps from this source."* Tap **Settings**, enable
   **Allow from this source** for Chrome, then press Back and tap **Install**.
5. If Play Protect shows *"Unsafe app blocked"*, tap **More details → Install anyway**.
   The APK is signed with a debug key, which is why Play Protect is cautious.
6. Open **4CBOn2** from the app drawer.

## What this build actually does

This is the current native Kotlin/Compose prototype, not the finished assistant:

- Bottom navigation with five destinations: Home, Ask, Agents, Browser, Activity.
- Home shows the prototype status cards; Agents / Browser / Activity are honest
  "not implemented yet" screens.
- Ask shows **Ask a Question** and **How do I? tutorial** entry points, both
  deliberately disabled until the real capability exists.
- No AI answers, no agents, no browser automation, no stored credentials, no
  phone-control permissions. Network permission is declared but nothing is sent.

## Rebuilding

The workflow is `.github/workflows/build-debug-apk.yml` and runs on every push
that touches `android/**`, or manually from **Actions → Build 4CBOn2 Debug APK →
Run workflow**. It uploads a per-run artifact and refreshes the rolling
`4cbon2-debug-apk-latest` release, so the download link above never changes.

## Fixes required before this build could succeed

The Android source snapshotted into this branch did not compile. Five Kotlin
errors were fixed in commit `Fix five Kotlin compile errors blocking the Android build`:

1. `routines/RoutineEngine.kt` — enum entries used unqualified (`NOT_FOUND`,
   `NOT_READY`, `MAX_STEPS_REACHED`, `TRIGGERED`); now qualified as
   `TriggerResult.*`.
2. `notebook/NotebookContract.kt` — a private `indexOf(String, Int)` shadowed
   `String.indexOf(Char, Int)`; the call now targets the receiver explicitly.
3. `local/JobQueue.kt` — `isDispatchable` is a property of `JobState`, not
   `JobRecord`; now `it.state.isDispatchable`.
4. `agent/BoundedAgentLoop.kt` — `Step` is a SAM `fun interface`, so `step.name`
   could not exist; messages now identify steps by position in the plan.
