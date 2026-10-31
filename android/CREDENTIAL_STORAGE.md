# 4CBOn2 Android Prototype — Credential Storage Boundary

**Build division:** 07 — Protected credential storage  
**Status:** Finished  
**Date:** 23 October 2026

## Like you are two

We built a tiny locked box for special keys.

The box is empty.

Later, if you choose to give the helper a key, it puts the key in the locked box. It does not write the key in its notebook or shout the key out loud.

## Like you are an adult

The app source now contains a narrow Android Keystore-backed boundary for future user-entered GitHub and Kaggle credentials. There is deliberately no credential-entry interface and no connection adapter yet. No PAT, Kaggle token, Colab credential, browser cookie, or account information was requested, received, stored, loaded, logged, exported, or tested.

## Implemented boundary

| Component | Actual role |
| --- | --- |
| `CredentialStore` | Narrow interface: save, load, remove, and presence-check an explicitly named credential slot |
| `CredentialSlot` | Only `GITHUB_FINE_GRAINED_PAT` and `KAGGLE_BEARER_ACCESS_TOKEN`; no Colab proxy-token slot exists |
| `AndroidKeyStoreCredentialStore` | Android implementation using a non-exportable AES key in `AndroidKeyStore` |
| `EncryptedCredentialRecord` | Versioned serialized envelope containing GCM IV and ciphertext only |
| Private SharedPreferences | Stores the encrypted envelope, not plaintext |

The implementation uses `AES/GCM/NoPadding`, a randomized 12-byte IV, and a 128-bit GCM authentication tag. The key alias is namespaced to the application and is not put in source control or exported from the Android Keystore.

## Data-flow boundary

```text
Future explicit local credential entry
  -> caller provides a CharArray
  -> AES-GCM encryption using an AndroidKeyStore key
  -> ciphertext + IV written to private app storage

Future authorized adapter
  -> explicitly requests a named slot
  -> AES-GCM decrypts locally
  -> adapter uses value only for its authorized request
```

The current app has no entry screen and no adapter, so the data-flow diagram is a compiled boundary—not an active service flow.

## Security rules implemented or retained

- No plaintext credential is written through `SharedPreferences`.
- No credential logging or `println` call exists in the credential source.
- No credential is written into a notebook, task history, export bundle, repository file, or ordinary app UI.
- The source manifest adds no permission, service, receiver, or provider for credential storage.
- Stored data is versioned so unsupported/malformed ciphertext fails closed with a recovery message instead of becoming a fake missing credential.
- Removing a slot deletes its stored encrypted envelope. It does not claim to erase immutable transient strings, operating-system backups outside our control, or an already-used remote token.
- The app continues to use `android:allowBackup="false"` from the original manifest.

## Important limits

Android Keystore storage improves protection of a credential **at rest on the device**. It does not make a credential harmless if a future authorized adapter deliberately sends it to the corresponding service, if a compromised/unlocked device is under attack, or while a credential is briefly present in memory for a permitted request.

The code clears its temporary plaintext byte arrays after encryption/decryption. JVM/Android immutable `String` instances cannot be reliably zeroed; that is one reason future adapters must keep credential use short, local, and out of logs/UI/history.

This initial key is not configured to require biometric/device-auth confirmation for every decrypt. That may be a later owner-security choice, but it must be tested against background/reconnect behavior rather than added blindly.

## Verification performed

| Check | Actual result |
| --- | --- |
| Kotlin/Compose compile | Passed |
| Debug build | Passed |
| Debug APK signature | v2 verification passed |
| Unit tests | Passed: 4 total, 0 failures, 0 errors, 0 skipped |
| Record serialization tests | Passed: valid envelope round trip and malformed record rejection |
| Credential-slot test | Passed: only the two planned user credential slots exist |
| Static source inspection | Passed: AndroidKeyStore + AES-GCM selected; no plaintext preference write/logging; no source special permission |
| Android Keystore encrypt/decrypt on emulator/device | Not tested yet |
| User credential entry / GitHub / Kaggle request | Not implemented or tested |

The current ignored local verification APK for this source state has SHA-256:

```text
7021eeb367998c878b8222946a4ad54ad704840f86cf79a712fe25c48c48b051
```

The retained `4CBOn2-debug.apk` remains the older Division 05 foundation artifact, so it does not contain this credential-storage source update.

## PAT decision remains unchanged

Do **not** paste a PAT into chat or source files. The storage boundary is ready for a later, local, user-controlled entry screen. A PAT is not needed until the actual GitHub connection test, and then it must be fine-grained, minimum-permission, repository-specific, and initially read-only.

## Completion check

- [x] AndroidKeyStore-backed AES-GCM storage source exists.
- [x] Only planned GitHub/Kaggle user credential slots are supported.
- [x] No credential value, UI entry field, connection, or token request was added.
- [x] No special phone-control permission was added.
- [x] Unit/static/build/signature checks passed.
- [x] Device-keystore and live-service tests remain clearly unverified.

**Next eligible division:** 08 — Prove GitHub read access through an authorized, minimum-permission route.
