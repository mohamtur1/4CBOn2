# 4CBOn2 — Debug signing identity

**Division:** 52 — Package & verify v0.3
**Status:** pinned · **Updated:** 13 November 2026

## Like you are two

Every copy of the app carries a small name stamp.

If the stamp is the same as the copy already on your phone, the new copy can
slide in next to it and keep its stuff.

If the stamp is different, the phone says no, and you must take the old copy off
first.

The stamp now lives in a file in the project, and every build uses that one file.

## Like you are an adult

Debug builds only install over each other while the signing certificate is
identical. A runner that mints a fresh debug keystore per job therefore ships an
APK that every existing install rejects (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` /
*App not installed*), and the workaround — uninstall — destroys on-device data.

The identity is now fully repository-owned, with no runner state involved:

1. `android/debug.keystore` holds the key (JKS, alias `androiddebugkey`, store and
   key password `android`).
2. `android/app/build.gradle.kts` points the `debug` signing config's `storeFile`
   at that path, so the Android Gradle Plugin never has to guess where the debug
   key lives.
3. `.github/workflows/build-debug-apk.yml` checks the keystore's certificate
   digest against `android/debug-signer.sha256` **before** building, asks Gradle
   which key it actually resolved (`:app:signingReport`), and asserts the finished
   APK's signer matches the pin. Drift fails the build instead of shipping an APK
   nobody can upgrade to.
4. Nothing generates a key at build time, and nothing reads the Actions cache or
   the runner's home directory.

### What was tried first, and what each attempt proved

| Attempt | Outcome |
| --- | --- |
| Cache `~/.android/debug.keystore` with `actions/cache` (key `4cbon2-debug-keystore-v2`) | **Failed on `main`.** Actions caches are branch-scoped, so the key saved by a work branch was invisible to the merge build, which minted its own certificate and tripped the pin check (run `43433628923`). |
| Commit the keystore and copy it to `~/.android/debug.keystore` | **Still failed.** Run `43434173107` (commit `4d50ee4`) produced an APK signed `bca50a27…` while the committed keystore held `ea1f65ed…`: the plugin did not sign with the file placed at its conventional location. |
| Commit the keystore **and** name it in the Gradle debug signing config (current) | Signing no longer depends on cache scope, home directory, or runner image. |

Row two is why the workflow now verifies Gradle's *own* view of the signing config
rather than assuming a conventional path works. **Unresolved detail:** I did not
establish *why* the plugin ignored `~/.android/debug.keystore` on that runner
(`ANDROID_USER_HOME` redirection and a pre-baked runner keystore are both
plausible, and neither was tested). Treat any claim about that mechanism as
unconfirmed; the explicit `storeFile` makes the question moot for this build.

### Why the keystore is committed

The first attempt cached the key with `actions/cache`. That fails for a structural
reason: **Actions caches are branch-scoped**, so a key saved by a feature branch is
invisible to a build on `main`, and each branch minted its own certificate.
Committing the file removes the branch dependency entirely.

This is a deliberate, documented trade-off, and it is the weakest available option
*for a debug key*:

- The debug keystore is not a secret by design: the passwords and alias are the
  well-known public values (`android` / `android` / `androiddebugkey`), and
  Android's own historical default debug key was public in the SDK.
- It grants no distribution authority. It is not a release identity, cannot be
  used for Play Store uploads, and does not unlock anything a sideloaded debug
  build relies on.
- What it buys is stable upgrade installs: one certificate on every build means a
  new APK installs over the old one instead of forcing an uninstall that wipes
  on-device data.

The honest cost: anyone with this repository can sign an APK that Android will
accept as an in-place update to `io.github.mohamtur1.fourcbon2`. Only install this
app from the project's own release page
(`mohamtur1/4CBOn2` → **Releases** → the release titled *4CBOn2 Debug APK — built …*).

A secret-stored key would avoid that cost, but it was not reachable from this
environment: the CI token cannot read or write Actions secrets
(`GET /repos/mohamtur1/4CBOn2/actions/secrets` → `403 Resource not accessible by
integration`). **Upgrade path:** if the owner adds a `DEBUG_KEYSTORE_BASE64`
repository secret, decode it to a file in the workflow, point the signing config at
that path, and delete `android/debug.keystore` from the repository and its history.

### The pinned identity

| Field | Value |
| --- | --- |
| Keystore | `android/debug.keystore` (JKS, 2,151 bytes, file SHA-256 `2066394b7ca3e312fdcffac3f0c4bd6d73327108f418a7486b79ad4aac92580d`) |
| Pin file | `android/debug-signer.sha256` |
| Signer certificate SHA-256 | `ea1f65ed3e32acafabeabdc8bd9af697520929fbe9b8849eb612efbf0fe3b316` |
| Certificate SHA-1 | `4fcf97ba1fa7072582ea98974fe6affcd3a80cf2` |
| Distinguished name | `CN=Android Debug, O=Android, C=US` |
| Key | RSA 2048, `SHA256withRSA`, validity 10,000 days (13 Nov 2026 → 31 Mar 2054) |
| Alias / passwords | `androiddebugkey` / store `android` / key `android` |
| Scheme | APK Signature Scheme v2 |

Verified locally with a Temurin JDK `keytool`, not assumed:

- `keytool -list -v -keystore android/debug.keystore -storepass android` → loads
  cleanly (a wrong store password raises *Keystore was tampered with*), one
  `PrivateKeyEntry`, alias `androiddebugkey`, SHA-256 fingerprint matching the pin.
- `keytool -importkeystore -srcstorepass android -srckeypass android` into a
  throwaway PKCS#12 → succeeded, which proves the key password as well as the store
  password (a wrong key password fails with *Cannot recover key*).
- The certificate itself was parsed independently (DER, 792 bytes) and its SHA-256
  recomputed, matching `ea1f65ed…`.

### Checking the pin on the phone

CI is not the only place the pin is enforced. The Home screen shows the installed
APK's **own** certificate digest — read from `PackageManager` signing info at
runtime, not from a string in a layout — beside the pinned digest stamped into
`BuildConfig` at compile time, and states the verdict in words:

| Verdict | Meaning |
| --- | --- |
| *matches the project's pinned key* | This is a project build. |
| *does NOT match the project's pinned key* | Something re-signed or repackaged it. Shown in the error colour. Uninstall. |
| *could not read this APK's signature* | The package manager gave no certificate. Reported, not guessed. |
| *this build recorded no pinned key to compare against* | `android/debug-signer.sha256` was missing when this APK was built. |

Code: `BuildStamp.kt` (pure JVM model, unit-tested in `BuildStampTest.kt`) and
`AndroidBuildStampReader.kt` (the package-manager glue).

The stamp also carries the build time and the version. The version identity is
deliberately unchanged by this (`0.3.0-dev`, code `3`): the build time
distinguishes builds, and a version bump belongs to the next division-level
release rather than to a packaging change.

### Who publishes

Only pushes to `main` refresh the rolling release
`4cbon2-debug-apk-latest`. Work-branch builds compile, verify and sign the APK as a
pre-merge check and upload it as a workflow artifact, so the public download always
comes from merged code.

## If the pin ever needs to change

A new keystore means every owner must uninstall before installing. Treat it as a
breaking change:

1. Generate the replacement key outside the repository.
2. Update `android/debug.keystore` **and** `android/debug-signer.sha256` in the same
   commit — the pre-build check fails on any mismatch between them.
3. Say so in the release body, and keep the old build downloadable until the new one
   is installed.

## Builds published before this pin

Releases built before 13 November 2026, and the builds published that morning,
carried throwaway certificates (`3c20f399…`, `6a69c16d…`, `69f5d941…`,
`d2ffb5ea…`). Any of those already on a phone must be uninstalled **once** before
installing a build signed by the pinned key. From this commit onward every build
shares the pinned identity, so later upgrades install in place.

## Caveat

This is a debug identity for sideloaded personal builds. It is not an
owner-controlled release key and must not be reused as the production signing plan.
