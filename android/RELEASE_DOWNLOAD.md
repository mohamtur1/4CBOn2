# 4CBOn2 — Downloading the APK from GitHub Releases

**Division:** 52 — Package & verify v0.3
**Channel:** rolling pre-release, tag `4cbon2-debug-apk-latest`
**Release page:** <https://github.com/mohamtur1/4CBOn2/releases/tag/4cbon2-debug-apk-latest>
**Shortcut:** <https://github.com/mohamtur1/4CBOn2/releases/latest>
**Built by:** `.github/workflows/build-debug-apk.yml` on GitHub's runner (no local SDK needed)

## Like you are two

The app is in a box on the internet shelf.

You open the GitHub app, find the box for 4CBOn2, and tap it.

The phone asks "do you want to put this on me?" You say yes.

Then the app is on your home screen with its own little picture.

## Like you are an adult

Every push to `main` that touches the Android sources rebuilds the APK on a
GitHub-hosted runner, verifies it (`zipalign`, `apksigner`, SHA-256, signing
certificate), and refreshes one rolling release. There is nothing to build on your
machine and no artifact to hunt for: the release always points at the newest
verified debug build of merged code. Work branches run the same build and checks,
but only `main` publishes.

### 1. Download it on the phone

**GitHub mobile app (Android)**

1. Open the GitHub app and go to the repository **`mohamtur1/4CBOn2`**.
2. Tap **Releases** (on the repo screen; it may sit under the *⋯ / More* menu).
3. Open the release titled **4CBOn2 Debug APK — built `<date> <time> UTC>`** — the
   build time is part of the title, so the newest one is identifiable at a glance.
4. Tap the asset **`4CBOn2-debug.apk`** under *Assets*.

**Phone browser**

Open <https://github.com/mohamtur1/4CBOn2/releases/latest> and tap
**`4CBOn2-debug.apk`**.

If the GitHub app only offers a viewer instead of a download, use the browser
route — it always serves the file.

### 2. Install it

1. Open the downloaded file (notification shade → the `.apk` entry, or
   *Files → Downloads*).
2. If Android blocks it, tap **Settings** in the prompt and allow
   **Install unknown apps** for the app you downloaded it with (Chrome, Files,
   or the GitHub app). This is a per-app switch, not a global one.
3. Play Protect may show *"unsafe app blocked"* or ask you to scan — this is
   expected for any sideloaded debug build. Choose **Install anyway** only if you
   built this from your own repository, which you did.
4. Tap **Install**, then **Open**.

The app appears on the home screen as **4CBOn2**.

### 3. Upgrading from an older install

Every build now signs with one committed debug keystore (`android/debug.keystore`,
pinned by `android/debug-signer.sha256` and named explicitly in
`android/app/build.gradle.kts`), so new APKs install over the previous one and keep
their on-device data. CI fails the build rather than publish a differently signed
APK. See `android/DEBUG_SIGNING.md`.

One exception: builds published **before 13 November 2026** — the 31 October APK and
the refreshes published that morning — were signed with throwaway per-run keys. If
Android reports **App not installed** or a signature mismatch while updating one of
those:

1. Uninstall the old 4CBOn2 (long-press the icon → *Uninstall*). This clears its
   local data — the app stores everything on-device.
2. Install the new APK from the release.

That is a one-time step. Every build after it shares the same signature.

### 4. What this build is

| Field | Value |
| --- | --- |
| File | `4CBOn2-debug.apk` |
| Package | `io.github.mohamtur1.fourcbon2` |
| Version | `0.3.0-dev` (code `3`) |
| Signer certificate SHA-256 | `ea1f65ed3e32acafabeabdc8bd9af697520929fbe9b8849eb612efbf0fe3b316` — pinned, identical on every build from 13 November 2026 onward |
| Size / APK SHA-256 | Change on every rebuild; read them from the release body's *Build record* table |
| Built | Stamped in the release title, the release body, and the app's Home screen |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |
| Build type | Debug (debug-signed, sideload only) |

Debug builds are not byte-for-byte reproducible (zip entry timestamps), so the
file's SHA-256 changes on every rebuild. **The authoritative checksum for the
current file is the digest GitHub reports for the release asset**, and the release
body repeats it in a *Build record* table together with the build time, commit and
workflow run that produced it. Verify on a phone or desktop with:

```bash
sha256sum 4CBOn2-debug.apk
```

#### Which build am I actually looking at?

Three places carry the same stamp, so a stale or repackaged APK cannot pass as the
current one:

| Where | What it shows |
| --- | --- |
| Release **title** | `4CBOn2 Debug APK — built 2026-11-13 14:31 UTC` |
| Release **body**, *Build record* table | build time, version, signer digest, APK SHA-256, size, commit, branch and run |
| **In the app**, Home screen under the `4CBOn2` title | `Build 0.3.0-dev (code 3)`, the same build time, when Android installed it on this phone, and whether the installed APK's own certificate matches the project's pinned key |

The in-app stamp is read from the installed package (`PackageManager` signing info)
and from values stamped into `BuildConfig` at compile time — it is not a string
somebody typed into a layout, so a re-signed APK reports the mismatch instead of
claiming to be a project build. If that line ever reads *does NOT match the
project's pinned key*, uninstall it.

One trap worth knowing: the date GitHub prints next to the release title is the day
the rolling release was **first published** (31 October 2026). It does not change
when the APK is replaced. Use the build time in the title, or the asset's own
timestamp, to tell builds apart.

### 5. What the build is not

- Not Play-Store-ready: debug signature, no release signing identity.
- Not a device-tested release: passing `:app:assembleDebug` proves it compiles
  and packages, not that every screen behaves on every Android 8+ phone.
- Not a distribution claim: `versionCode 1`-era metadata and the prototype
  boundaries in `android/PROTOTYPE_SCOPE.md` still apply.

### 6. Rebuilding by hand (optional)

Push any change to `main` under `android/app/`, the Gradle files, the committed
keystore or its pin, or the workflow itself, and the release refreshes
automatically. Documentation under `android/*.md` deliberately does not retrigger a
build, so a docs-only commit cannot quietly change the published checksum. You can
also trigger it from the Actions tab: **Build 4CBOn2 Debug APK → Run workflow**;
note that a manual run on a branch builds and verifies the APK but does not publish
it, because only `main` refreshes the rolling release.

To build locally instead, `scripts/ensure_toolchain.sh` provisions JDK 17 and
the Android SDK, then:

```bash
cd android
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

A local build signs with the same committed `android/debug.keystore`, so its APK
installs over a CI-built one — and over anything else built since 13 November 2026.
Check that before shipping a build to a phone:

```bash
./gradlew --no-daemon :app:signingReport
cat debug-signer.sha256
```
