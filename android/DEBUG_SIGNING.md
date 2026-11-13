# 4CBOn2 — Debug signing identity

**Division:** 52 — Package & verify v0.3
**Status:** pinned · **Updated:** 13 November 2026

## Like you are two

Every copy of the app carries a small name stamp.

If the stamp is the same as the copy already on your phone, the new copy can
slide in next to it and keep its stuff.

If the stamp is different, the phone says no, and you must take the old copy off
first.

We now keep one stamp and reuse it, so you do not have to do that.

## Like you are an adult

Debug builds only install over each other while the signing certificate is
identical. A runner that mints a fresh debug keystore per job therefore ships an
APK that every existing install rejects (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` /
*App not installed*), and the workaround — uninstall — destroys on-device data.

`.github/workflows/build-debug-apk.yml` therefore:

1. copies the committed `android/debug.keystore` to `~/.android/debug.keystore`,
   which is where the Android Gradle Plugin looks for the debug identity;
2. compares the built APK's certificate digest against
   `android/debug-signer.sha256` and **fails the build on drift**, so a broken
   keystore step cannot quietly ship an unupgradable APK;
3. never generates a key at build time, so every runner signs identically.

### Why the keystore is committed

The first attempt cached `~/.android/debug.keystore` with `actions/cache`. That
fails for a structural reason: **Actions caches are branch-scoped**, so a key
saved by a feature branch is invisible to a build on `main`, and each branch
minted its own certificate. Committing the file removes the branch dependency
entirely.

This is a deliberate, documented trade-off, and it is safe *for a debug key*:

- The debug keystore is not a secret by design: the password and alias are the
  well-known public values (`android` / `androiddebugkey`), and Android itself
  ships a public default debug key.
- It grants no distribution authority. It is not a release identity, cannot be
  used for Play Store uploads, and does not protect anything a sideloaded debug
  build relies on.
- What it *does* buy is stable upgrade installs: the same certificate on every
  build means a new APK installs over the old one instead of forcing an uninstall
  that wipes on-device data.

If this repository ever moves to release signing, generate a real owner-held key
outside the repository, store it as an Actions secret, and retire
`android/debug.keystore` from the build path.

| Field | Value |
| --- | --- |
| Keystore | `android/debug.keystore` (JKS) |
| Pin file | `android/debug-signer.sha256` |
| Signer certificate SHA-256 | `ea1f65ed3e32acafabeabdc8bd9af697520929fbe9b8849eb612efbf0fe3b316` |
| District name (DN) | `CN=Android Debug, O=Android, C=US` |
| Key | RSA 2048, `SHA256withRSA`, validity 10,000 days (to 2054), alias `androiddebugkey` |
| Scheme | APK Signature Scheme v2 |

## If the pin ever needs to change

A new keystore means every owner must uninstall before installing. Treat it as a
breaking change: update both `android/debug.keystore` and
`android/debug-signer.sha256`, say so in the release body, and keep the old build
available until the new one is installed.

## Builds published before this pin

Releases built on 13 November 2026 before this keystore landed carried
throwaway certificates (`6a69c16d…`, `69f5d941…`, `d2ffb5ea…`). Any of those
installed on a phone must be uninstalled once before installing a build signed by
the pinned key. Builds from this commit onward all share the pinned identity.

## Caveat

This is a debug identity for sideloaded personal builds. It is not an
owner-controlled release key and must not be reused as the production signing
plan.
