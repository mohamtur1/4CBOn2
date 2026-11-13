# 4CBOn2 — Debug signing identity

**Division:** 52 — Package & verify v0.3
**Status:** pinned · **Pinned:** 13 November 2026

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

1. restores `~/.android/debug.keystore` from the Actions cache
   (`4cbon2-debug-keystore-v2`);
2. generates the keystore only when that cache is cold;
3. saves it back at the end of the job, even on failure;
4. compares the built APK's certificate digest against
   `android/debug-signer.sha256` and **fails the build on drift**.

Only the certificate's public SHA-256 fingerprint is committed. The keystore and
its private key never enter the repository, and the `android` / `android` debug
passwords are the standard public debug values, not secrets.

| Field | Value |
| --- | --- |
| Pin file | `android/debug-signer.sha256` |
| Signer certificate SHA-256 | `d2ffb5ea8d17a3a966dedfeb62b1b43e2bcf5408a570d48dac318be8139be7c7` |
| District name (DN) | `CN=Android Debug, O=Android, C=US` |
| Key | RSA 2048, validity 10,000 days, alias `androiddebugkey` |
| Scheme | APK Signature Scheme v2 |

## If the pin ever needs to change

A new keystore means every owner must uninstall before installing. Treat it as a
breaking change: update the pin file, say so in the release body, and keep the
old build available until the new one is installed.

## Caveat

This is a debug identity for sideloaded personal builds. It is not an
owner-controlled release key and must not be reused as the production signing
plan.
