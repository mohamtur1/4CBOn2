# 4CBOn2 — First Formal Debug APK

**Build division:** 05 — Produce first debug APK  
**Status:** Finished  
**Built:** 23 October 2026  
**Artifact:** `/home/user/4CBOn2-debug.apk`  
**Checksum:** `/home/user/4CBOn2-debug.apk.sha256`  
**Verification transcript:** `android/DEBUG_APK_VERIFICATION.txt`

## Like you are two

We made a practice copy of the helper house.

We checked that the box opens, has the right name, and has a practice safety stamp.

It is not the final shiny house yet. We have not put it on a real phone yet.

## Like you are an adult

This is the first intentionally retained **debug-signed** APK for the minimal 4CBOn2 Android foundation. It is a reproducible development checkpoint, not a production/signed-release candidate and not a statement of device compatibility.

## Artifact identity

| Field | Verified value |
| --- | --- |
| File | `4CBOn2-debug.apk` |
| Size | 11,613,147 bytes |
| SHA-256 | `b568e5a46b9fd80e9d8c64165418479e065205e10c645af481e2287f4ee9dc5e` |
| Package / application ID | `io.github.mohamtur1.fourcbon2` |
| Version | Code `1`; name `0.1.0-dev` |
| Minimum SDK | 26 |
| Target SDK | 36 |
| Compile SDK | 37 |
| Launcher activity | `io.github.mohamtur1.fourcbon2.MainActivity` |
| Application label | `4CBOn2` |
| Build type | Debug |

## What was actually verified

1. A clean constrained build completed successfully with `:app:assembleDebug`.
2. The build output was copied from the ignored `app/build/` directory into the workspace root as the formal artifact.
3. The stored SHA-256 file verifies the artifact successfully.
4. `zipalign -c -p 4` completed successfully.
5. `apksigner verify --verbose` passed with one signer and APK Signature Scheme v2 verification.
6. `aapt dump badging` confirmed the expected package, SDK values, label, and launcher activity.
7. The existing unit-test task completed but reported `NO-SOURCE`; this is a build-task check, not evidence of unit-test coverage.

## Debug signing state

The package is signed with the normal Android debug certificate:

```text
DN: C=US, O=Android, CN=Android Debug
Certificate SHA-256: 3c20f399f3414977dfff9af77db2701a471c5de4ab0e0821be6cb1d81b7da876
Signature verification: v2 PASS
```

This debug certificate is appropriate only for development. It is not an owner-controlled production signing identity, does not establish Play Store readiness, and must not be reused as the future release-signing plan.

## Packaged manifest observation

The primary project manifest contains no declared `<uses-permission>`, service, receiver, or provider component beyond the launcher activity. The **merged APK** does contain this app-scoped permission:

```text
io.github.mohamtur1.fourcbon2.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

It appeared during dependency/manifest merging. It is not a requested phone-control, screen-capture, accessibility, storage, network, SMS, location, or account permission. The packaged APK—not just the handwritten source manifest—remains the verification source of truth, so this check must be repeated after dependency changes.

## What this APK does today

It opens one simple Compose foundation screen that says `4CBOn2` and truthfully explains that no account connection, tutorial, agent, or phone-control permission is active yet.

## What this APK does not establish

- Installation or launch on an emulator or a physical phone.
- Compatibility with a particular Android version/device maker.
- Ask a Question, How-do-I tutorials, agents, offline storage, or phone control.
- GitHub, Kaggle, Colab, browser, account, PAT, or external-service access.
- Runtime permission behavior.
- Production signing, release hardening, Play Store eligibility, or distribution.

## Rebuild path

From the Android source root, with the verified local JDK/SDK/Gradle environment available:

```bash
./gradlew --no-daemon --max-workers=1 \
  -Dkotlin.compiler.execution.strategy=in-process \
  clean :app:assembleDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

The constrained options are required for the present low-memory sandbox. They are not a claim about device runtime behavior.

## Completion check

- [x] Debug artifact intentionally copied outside the ignored build directory.
- [x] SHA-256 recorded and independently rechecked.
- [x] ZIP alignment check passed.
- [x] Debug signature check passed and certificate fingerprint recorded.
- [x] APK package/SDK/launcher metadata inspected.
- [x] Merged-permission observation recorded.
- [x] No device installation, production signing, account connection, PAT, or app-control feature was used.

**Next eligible division:** 06 — Add navigation: Home, Ask, Agents, Browser, and Activity, with unimplemented features visibly disabled or labeled.
