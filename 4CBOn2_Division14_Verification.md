# 4CBOn2 — Division 14 verification receipt

**Date:** 30 October 2026
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Verified source commit:** `8ae74d28ee6673bc7926392c75df722051cecb5b`
**Verified source tree:** `fd81a3a82bafc662a77321a2075b73edf02bf2ee`
**CI run:** https://github.com/mohamtur1/4CBOn2/actions/runs/40987374233
**CI conclusion:** Success, no uploaded artifacts

> Arena fixes this session to `arena/74f65509-4cbon2`. Division 14 continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Like you are two

The big red stop button works. You can say “I’ll do it,” “wait,” or “stop,” and the app listens right away without asking the internet. If a helper was busy and tries to finish an old job after you press stop, the app throws that old answer away. To start again, **you** must press start, and the app must look around first. The big robot checked 71 little things and all of them passed, and it did not keep any big app file.

## Like you are an adult

Division 14 added a local control lease and controller with owner-only resume, epoch-bound stale-command rejection, a terminal stop, a fresh post-resume state check, and lease-before-scope ordering. Sixteen synthetic JVM tests were added to the 55 preserved tests, bringing the active suite to 71; the CI inventory guard, step label, notice, and exact XML assertion were all raised to 71 without weakening.

The verified workflow compiled the Android source, ran all 71 unit tests, assembled a debug APK, checked zip alignment and APK Signature Scheme v2, recorded the APK SHA-256, confirmed the build did not modify source, skipped the historical 09c artifact-retention step, and uploaded zero artifacts. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, authenticated, or live-service test.

## CI evidence

| Check | Actual result |
| --- | --- |
| Workflow | `Android Division 09b Verification` |
| Run | `40987374233` (push to `arena/74f65509-4cbon2`) |
| Conclusion | **Success** in 3m11s |
| Verified commit | `8ae74d28ee6673bc7926392c75df722051cecb5b` |
| Verified tree | `fd81a3a82bafc662a77321a2075b73edf02bf2ee` |
| Active source test inventory | 71 |
| JUnit aggregation | 71 passed; 0 failures; 0 errors; 0 skipped |
| Debug APK assembly | Passed |
| Zip alignment | Passed |
| APK Signature Scheme v2 | Passed |
| Debug APK SHA-256 | `c9504f2bfc8d513cda7e44ab9d1d8f3d856fc3fa008e0ebf1195f5f77d2c268d` |
| Build did not modify source | Passed |
| Artifact-retention step | Skipped |
| CI artifact count | `0` |
| Historical 09c artifact marker in commit message | Absent |
| Credentials, secrets, live accounts, PR, or merge | None used; none opened |

The artifact count was read back from the GitHub Actions run-artifacts API. The APK was ephemeral CI output: not uploaded, retained, committed, installed, or production-signed.

## Division 14 scope as verified

- `ActionLease.kt` — `ACTIVE` / `OWNER_TAKEOVER` / `PAUSED` / terminal `STOPPED` state, monotonic epoch, bounded duration, atomic `LeaseSnapshot`, and nine explicit refusal causes.
- `BoundedJobController.kt` — lease evaluated before scope and capability discovery; epoch-checked completion; late-result discarding by epoch.
- `ActionLeaseTest` (8 tests) and `BoundedJobControllerTest` (8 tests) — synthetic package names only.

Safety properties actually enforced in code:

- Every accepted transition increments the epoch and clears the fresh-state flag; a new lease starts fresh by construction.
- `TAKE_OVER`, `PAUSE`, and `STOP` may be issued by the automation, because restricting is always safe; `RESUME` from `AUTOMATION` is refused as `NOT_OWNER_ORIGINATED`.
- Commands and completions carrying an older epoch are refused as `STALE_EPOCH`; future epochs as `UNISSUED_EPOCH`; anything after `STOP` as `LEASE_TERMINATED`.
- Restrictive commands still apply after expiry, so safety never depends on a clock; only `RESUME` is refused when expired.
- A taken-over, paused, stopped, expired, or not-yet-rechecked job performs no capability discovery.
- Resume never restores permissions: every later step is re-checked against the same `ScopeContract` budget, expiry, exclusions, and cloud-transfer setting, and against the registry's owner-pause state.
- No UI wiring, persistence, network call, permission request, screen capture, or authentication claim was added. "Owner only" models the local owner surface, not a biometric or credential proof.

## Commit continuity note

The Division 14 commit was first created as `5504d31c8a1f612395c26664f7805371615746b7`, and it was never pushed. During the approval pause the local repository was re-cloned, which destroyed the local object database and that commit object. The working tree survived intact; it was snapshotted, compared byte-for-byte against published head `d5aa8ba`, realigned with `git reset --mixed` only (no hard reset), and restaged. The rebuilt content tree reproduced the approved tree exactly (`fd81a3a…`), so the replacement commit `8ae74d2` carries byte-identical content. Owner approval was requested again for the new commit ID before anything was pushed.

## Document status

`android/TAKEOVER_LEASE.md` still contains a “Pending” CI section because this approved follow-up commit is restricted to root Markdown. This receipt is the authoritative supersession of that section; no Android source or workflow file changed after the CI-verified commit.

## Boundaries

- No emulator, real-device, permission-granted, production-signing, Play Store, or live-service claim.
- No persistence of lease state and no database schema change.
- Division 15 — Offline agent drafts has not started.
