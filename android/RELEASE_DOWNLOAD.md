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

Every push that touches the Android sources rebuilds the APK on a GitHub-hosted
runner, verifies it (`zipalign`, `apksigner`, SHA-256), and refreshes one rolling
release. There is nothing to build on your machine and no artifact to hunt for:
the release always points at the newest verified debug build.

### 1. Download it on the phone

**GitHub mobile app (Android)**

1. Open the GitHub app and go to the repository **`mohamtur1/4CBOn2`**.
2. Tap **Releases** (on the repo screen; it may sit under the *⋯ / More* menu).
3. Open **4CBOn2 Debug APK (latest)**.
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

The workflow caches one debug keystore (`4cbon2-debug-keystore-v1`), so builds
from now on share a signature and install over each other cleanly.

Builds published before that cache existed (including the 31 October 2026 APK)
were signed with a throwaway per-run key. If Android reports **App not
installed** or a signature mismatch while updating that old build:

1. Uninstall the old 4CBOn2 (long-press the icon → *Uninstall*). This clears its
   local data — the app stores everything on-device.
2. Install the new APK from the release.

### 4. What this build is

| Field | Value |
| --- | --- |
| File | `4CBOn2-debug.apk` |
| Package | `io.github.mohamtur1.fourcbon2` |
| Version | `0.3.0-dev` (code `3`) |
| Size | 12,137,771 bytes |
| Signer certificate SHA-256 | `6a69c16df8f28cfd240225edbb82d2b8ada9e19ebcc5df28872b8cd5e3af3772` |
| SHA-256 (one example — the 13 Nov 2026 build) | `dce0c5a706cc54116d8e9ac14f06a16d0c471de81e8c76ea27064c9a3ccc4c4e` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |
| Build type | Debug (debug-signed, sideload only) |

Debug builds are not byte-for-byte reproducible (zip entry timestamps), so the
file's SHA-256 changes on every rebuild. **The authoritative checksum for the
current file is the digest GitHub reports for the release asset**, and it is
repeated in the release body's *Build record* section together with the commit
that produced it. Verify on a phone or desktop with:

```bash
sha256sum 4CBOn2-debug.apk
```

### 5. What the build is not

- Not Play-Store-ready: debug signature, no release signing identity.
- Not a device-tested release: passing `:app:assembleDebug` proves it compiles
  and packages, not that every screen behaves on every Android 8+ phone.
- Not a distribution claim: `versionCode 1`-era metadata and the prototype
  boundaries in `android/PROTOTYPE_SCOPE.md` still apply.

### 6. Rebuilding by hand (optional)

Push any change under `android/app/`, the Gradle files, or the workflow itself
and the release refreshes automatically. You can also trigger it from the
Actions tab: **Build 4CBOn2 Debug APK → Run workflow**.

To build locally instead, `scripts/ensure_toolchain.sh` provisions JDK 17 and
the Android SDK, then:

```bash
cd android
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```
