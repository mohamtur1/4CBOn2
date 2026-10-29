# 4CBOn2 — Division 12 verification receipt

**Date:** 29 October 2026
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Verified source commit:** `75564a83ad2f4e2ada66a87ffc686cb81f0e96bd`
**Verified source tree:** `21ef2ccb1b646bd8cba131d1686db0054e0c131a`
**CI run:** https://github.com/mohamtur1/4CBOn2/actions/runs/40812912554
**CI conclusion:** Success, no uploaded artifacts

> Arena fixes this session to `arena/74f65509-4cbon2`. Division 12 therefore continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Like you are two

The app keeps a short list of helpers you picked yourself. You say “this app may help with this one thing.” 4CBOn2 tries the gentlest way first, and if nobody can do it, it says “can’t do that” instead of guessing. It never goes looking at all the other apps on the phone. The big robot builder checked 40 little things and all of them passed. It did not keep any big app file.

## Like you are an adult

Division 12 added a dependency-free, owner-selected capability registry with action-level support, explicit unavailable states, and narrow, non-enumerating discovery. Fifteen synthetic JVM tests were added to the 25 preserved tests, bringing the active suite to 40; the CI workflow’s inventory guard, step label, notice, and exact XML assertion were all raised to 40 without weakening.

The verified workflow compiled the Android source, ran all 40 unit tests, assembled a debug APK, checked zip alignment and APK Signature Scheme v2, recorded the APK SHA-256, confirmed the build did not modify source, skipped the historical 09c artifact-retention step, and uploaded zero artifacts. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test.

## CI evidence

| Check | Actual result |
| --- | --- |
| Workflow | `Android Division 09b Verification` |
| Run | `40812912554` (push to `arena/74f65509-4cbon2`) |
| Conclusion | **Success** in 3m38s |
| Verified commit | `75564a83ad2f4e2ada66a87ffc686cb81f0e96bd` |
| Active source test inventory | 40 |
| JUnit aggregation | 40 passed; 0 failures; 0 errors; 0 skipped |
| Debug APK assembly | Passed |
| Zip alignment | Passed |
| APK Signature Scheme v2 | Passed |
| Debug APK SHA-256 | `3f2597d7413b3b805436189f06e72ce5432d263d9b4602d01e412e9d217dc6e7` |
| Build did not modify source | Passed |
| Artifact-retention step | Skipped |
| CI artifact count | `0` |
| Historical 09c artifact marker in commit message | Absent |
| Credentials, secrets, live accounts, PR, or merge | None used; none opened |

The artifact count was read back from the GitHub Actions run-artifacts API. The APK was ephemeral CI output: not uploaded, retained, committed, installed, or production-signed.

## Division 12 scope as verified

- `SelectedAppCapability.kt` — owner-selected app identity, action capability, fixed `ActionRoute` ordering, `ActionSensitivity`, and a sealed `CapabilityDecision` with explicit unavailable reasons.
- `SelectedAppCapabilityRegistry.kt` — owner selection and pause, action declaration, deterministic snapshot, least-powerful reachable-route resolution, and a narrow `ActionRouteProbe` boundary.
- `SelectedAppCapabilityTest` (5 tests), `SelectedAppCapabilityRegistryTest` (10 tests) — synthetic package names only.
- `android/SELECTED_APP_CAPABILITIES.md`, `android/README.md`, and root plan/recovery documents updated for the current 40-test state.

Safety properties actually enforced in code:

- Resolution returns exactly one route or one reason: `APP_NOT_SELECTED`, `APP_DISABLED_BY_OWNER`, `ACTION_NOT_DECLARED`, `NO_SUPPORTED_ROUTE_AVAILABLE`.
- Route selection is `AUTHORIZED_API → APP_INTENT → APP_LINK_OR_SHARE → FIXED_ROUTINE → MANUAL_HANDOFF`, always the earliest declared **and** reachable route.
- A paused app is never probed; a probe that throws is treated as unreachable, never as permission to escalate.
- `MANUAL_ONLY` actions must declare exactly `MANUAL_HANDOFF`; a stronger route is rejected at construction.
- No installed-app enumeration, accessibility service, screen capture, overlay, notification reader, network call, credential, account, persistence, or UI wiring was added.

## Document status

`android/SELECTED_APP_CAPABILITIES.md` still contains a “Pending” CI section because this approved follow-up commit is restricted to root Markdown. This receipt is the authoritative supersession of that section; no Android source or workflow file changed after the CI-verified commit.

## Boundaries

- No emulator, real-device, permission-granted, production-signing, Play Store, or live-service claim.
- No persistence of the registry and no database schema change; the deterministic snapshot is the hand-off point for a later persistence decision.
- Division 13 — On-device scope contract has not started.
