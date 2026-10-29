# 4CBOn2 — Division 13 verification receipt

**Date:** 29 October 2026
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Verified source commit:** `4d18e2bf4cb39d0e014fb434a5aad1cdb1a6ea46`
**Verified source tree:** `5c40739648e7f0be40ec09a328988886af5afc9f`
**CI run:** https://github.com/mohamtur1/4CBOn2/actions/runs/40831655312
**CI conclusion:** Success, no uploaded artifacts

> Arena fixes this session to `arena/74f65509-4cbon2`. Division 13 therefore continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Like you are two

Before 4CBOn2 does a job, it writes a little promise sheet. The sheet says which helper apps may help, which one thing each may do, who they may talk to, how many steps are allowed, and when the sheet expires. If a step is not on the sheet, the answer is “no.” Sending anything off the phone is “no” too, unless you wrote “yes” yourself. The big robot checked 55 little things and all of them passed, and it did not keep any big app file.

## Like you are an adult

Division 13 added a bounded, local scope contract with explicit refusals, hard provider-side caps, opt-in cloud transfer, derived expiry, and exclusions. Fifteen synthetic JVM tests were added to the 40 preserved tests, bringing the active suite to 55; the CI inventory guard, step label, notice, and exact XML assertion were all raised to 55 without weakening.

The verified workflow compiled the Android source, ran all 55 unit tests, assembled a debug APK, checked zip alignment and APK Signature Scheme v2, recorded the APK SHA-256, confirmed the build did not modify source, skipped the historical 09c artifact-retention step, and uploaded zero artifacts. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test.

## CI evidence

| Check | Actual result |
| --- | --- |
| Workflow | `Android Division 09b Verification` |
| Run | `40831655312` (push to `arena/74f65509-4cbon2`) |
| Conclusion | **Success** in 2m43s |
| Verified commit | `4d18e2bf4cb39d0e014fb434a5aad1cdb1a6ea46` |
| Active source test inventory | 55 |
| JUnit aggregation | 55 passed; 0 failures; 0 errors; 0 skipped |
| Debug APK assembly | Passed |
| Zip alignment | Passed |
| APK Signature Scheme v2 | Passed |
| Debug APK SHA-256 | `d3cba101119afe0364767f4aceb0ee2bd32ff7e1c01072b0a979b635a37890e9` |
| Build did not modify source | Passed |
| Artifact-retention step | Skipped |
| CI artifact count | `0` |
| Historical 09c artifact marker in commit message | Absent |
| Credentials, secrets, live accounts, PR, or merge | None used; none opened |

The artifact count was read back from the GitHub Actions run-artifacts API. The APK was ephemeral CI output: not uploaded, retained, committed, installed, or production-signed.

## Division 13 scope as verified

- `ActionScopeContract.kt` — `ScopeContract`, `ScopeTarget`, `ScopeLimits`, `ScopeBounds` (20 actions / 5 minutes hard caps), `CompletionMode`, and sealed `ScopeDecision`.
- `ActionScopeEnforcer.kt` — fail-closed evaluation with eight explicit refusal causes, all decided before capability discovery, plus budget accounting isolated in `recordCompletion`.
- `ActionScopeContractTest` (5 tests) and `ActionScopeEnforcerTest` (10 tests) — synthetic package names only.

Safety properties actually enforced in code:

- Refusal order: `SCOPE_EXPIRED` → `TARGET_EXCLUDED` → `ACTION_EXCLUDED` → `TARGET_NOT_IN_SCOPE` → `ACTION_NOT_IN_SCOPE` → `CLOUD_TRANSFER_NOT_PERMITTED` → `ACTION_LIMIT_REACHED` → `CAPABILITY_UNAVAILABLE`.
- A contract wider than 20 actions or 5 minutes cannot be constructed.
- `cloudTransferAllowed` defaults to `false`; requesting transfer on such a contract is refused.
- Exclusions override allow-list entries.
- Owner confirmation is required for `OWNER_CONFIRMS_EACH_STEP` and for every `MANUAL_HANDOFF` route, even in `AUTOMATIC_WITHIN_SCOPE`.
- No UI wiring, persistence, real personal data, network I/O, accessibility service, screen capture, or permission request was added.

## Document status

`android/SCOPE_CONTRACT.md` still contains a “Pending” CI section because this approved follow-up commit is restricted to root Markdown. This receipt is the authoritative supersession of that section; no Android source or workflow file changed after the CI-verified commit.

## Boundaries

- No emulator, real-device, permission-granted, production-signing, Play Store, or live-service claim.
- No persistence of contracts and no database schema change.
- Division 14 — Takeover and cancellation lease has not started.
