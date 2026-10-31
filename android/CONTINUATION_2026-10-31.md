# Android release continuation — 31 October 2026

The `android/` directory was recovered from the actual release source at
`01c7bbb2f334886293c0e6b37a051fedd48fde99`, rather than the older Division 10
ZIP. The existing release included much of the plan as disconnected Kotlin
scaffolding, not as working UI. The older build plan and batch receipts are
historical; they are not proof that the end-to-end features work.

## New user-facing increment

- A single editable local goal draft with validation, save and confirmed
  deletion. It is private app storage, not a job runner or a secret vault.
- Fixed, manual HTTPS browser handoffs to the repository, Kaggle and Colab.
  No account, runtime, notebook execution or page control is implied.
- Scrollable navigation screens with explicit availability and safety status.
- Fixed record escape decoding and a queue iteration crash; observation
  consent now also checks the actual local consent state.

## Verification and blockers

- GitHub Actions run 41184224136 compiled the app and all test sources, then
  found failures in the inherited unit tests. These include privacy/safety
  and local analysis behavior, not just test configuration. The current
  debug release **has not been replaced**. CI publication remains disabled.
- GitHub Actions builds a debug APK artifact even when tests fail, but the job
  remains failed, and that artifact is not a verified release. Re-run all tests
  and review remaining failures before enabling publication.
- No emulator, phone installation, Android Keystore, real accounts, live
  Kaggle/Colab operation, or production signing has been tested here.
- Debug signing keys may change between builds. Android may reject an upgrade;
  uninstalling the existing app deletes its local data. Do not uninstall
  without first preserving any important data.

## Next work

Fix the failing inherited tests and their underlying behavior without weakening
security requirements. Particularly review observation consent, approval and
submission state machines, JSON validation, notebook parsing, and bounded
routines. Then run the complete test suite, verify the APK signature, and only
publish after reviewing the results and updating the release notes and checksum.
A full implementation of Divisions 11–42 is not claimed.
