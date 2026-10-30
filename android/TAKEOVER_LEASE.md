# Division 14 — Takeover and cancellation lease

**Work branch:** `arena/74f65509-4cbon2` (the fixed Arena session branch; source baseline imported from `arena/0eb49904-4cbon2` at `2a45201`)
**Scope:** A local, owner-controlled Take Over / Pause / Stop lease with epoch-bound commands, stale-command rejection, explicit resume plus a fresh state check, and cancellation that never depends on a cloud planner.
**Evidence class:** Source/static checks plus JVM unit tests, followed by no-artifact CI (receipt recorded separately once verified).

## Like you are two

Now there is a big red stop button. You can say “I’ll do it,” “wait a moment,” or “stop.” The app listens right away, without asking the internet. If a helper was busy and tries to finish an old job after you pressed stop, the app throws that old answer away. To start again, **you** must press start, and the app must look around first to see what is really there.

## Like you are an adult

Division 14 adds a local control lease (`ActionLease`) and a controller (`BoundedJobController`) that binds it to the Division 13 scope contract. Everything is decided on device: cancelling never waits on a network call, so losing connectivity can never make "stop" unavailable.

Each command carries the lease **epoch** it was written against. Any accepted transition increments the epoch, so a command or result written before a takeover cannot arrive later and quietly undo it.

## States and commands

| State | Meaning |
| --- | --- |
| `ACTIVE` | Steps may run while the scope contract permits them |
| `OWNER_TAKEOVER` | The owner is driving; the automation may not act |
| `PAUSED` | Temporarily held; equivalent refusal to takeover for stepping |
| `STOPPED` | Terminal; nothing may resume this job |

| Command | Allowed origins | Effect |
| --- | --- | --- |
| `TAKE_OVER` | Owner, automation | Enters `OWNER_TAKEOVER`, epoch + 1 |
| `PAUSE` | Owner, automation (stopping is always safe) | Enters `PAUSED`, epoch + 1 |
| `STOP` | Owner, automation | Enters `STOPPED`, epoch + 1, terminal |
| `RESUME` | **Owner only** | Returns to `ACTIVE`, epoch + 1, fresh check required |

The automation can always restrict itself, but it can never loosen control: a `RESUME` marked `AUTOMATION` is refused with `NOT_OWNER_ORIGINATED`.

## Refusals

| Reason | When |
| --- | --- |
| `STALE_EPOCH` | Command or completion epoch is older than the current epoch |
| `UNISSUED_EPOCH` | Epoch is in the future and was never issued |
| `NOT_OWNER_ORIGINATED` | A permissive command came from the automation |
| `INVALID_TRANSITION` | Redundant resume/pause, or a fresh check against a non-active lease |
| `LEASE_TERMINATED` | Any command or step after `STOP` |
| `LEASE_EXPIRED` | Lease older than its bounded duration |
| `OWNER_TAKEOVER_ACTIVE` / `PAUSED` | Step attempted while the owner holds control |
| `FRESH_STATE_REQUIRED` | Step attempted after a transition before the fresh check |

Restrictive commands still apply after expiry, so safety never depends on a clock reading; only `RESUME` is refused when expired.

## Ordering guarantees

`BoundedJobController.authorizeStep` evaluates the lease **before** the scope contract and the capability registry, so a taken-over, paused, stopped, expired, or not-yet-rechecked job performs no discovery at all. `recordCompletion` requires the caller's epoch to equal the current epoch, so an in-flight action authorized before a takeover cannot consume budget afterwards. `acceptResult` discards late results by epoch with an explicit reason.

A resume never restores earlier permissions: every subsequent step is re-checked against the same `ScopeContract`, including its action budget, expiry, exclusions, and cloud-transfer permission, and against the registry's owner-pause state.

## Deliberate boundaries

- **No authentication claim:** "owner only" means the local owner surface, modelled by `LeaseCommandOrigin`. There is no biometric or credential proof, and this division is not a security boundary against a hostile app.
- **No UI wiring, no persistence, no service, no network call, no permission request.**
- **No cloud dependency:** cancellation is local by construction.
- **No claim of universal phone/app control**, production signing, or device verification.
- A stale `STOP` is refused rather than honoured, because the owner surface always issues the current epoch. A `STOP` at the current epoch is always accepted.

## Tests

Two synthetic JUnit test files add 16 tests:

| File | Tests | Coverage |
| --- | --- | --- |
| `ActionLeaseTest` | 8 | Initial state/epoch/expiry boundary, construction bounds, takeover→resume→fresh-check, stale/unissued epochs, terminal stop, automation pause vs owner resume, redundant/expired transitions, fresh-check epoch matching |
| `BoundedJobControllerTest` | 8 | In-scope permit, takeover refusal with no discovery, expired lease, fresh-check gating, stale completion not consuming budget, budget accounting, resume not restoring out-of-scope permissions, late-result discarding |

The active suite is **71** (`55` preserved + `16` new). The CI source-inventory guard, step label, notice, and exact XML aggregation assertion are aligned to 71; none was weakened.

## Local checks

- Active `@Test` source count: **71**.
- New runtime dependency count: **0**.
- Local Android JDK/Gradle/Android SDK toolchain: **absent**; no local Gradle compilation, APK assembly, alignment, or signature is claimed.
- Isolated Kotlin 2.4.21 / JVM 17 type-check: **passed** (not a project Gradle build).
- Reflective standalone JVM run using a stub JUnit harness: **16 passed, 0 failed** for Division 14, and **46 passed, 0 failed** across the Division 12–14 classes (`STANDALONE_TEST_RUN passed=46 failed=0`). Not a Gradle, emulator, or device test.
- Design defect found by the tests and fixed in production code: a brand-new lease initially started with `freshStateConfirmed = false`, forcing a redundant fresh-state check before the very first step. A newly created lease is fresh by construction, so the flag now starts `true` and is cleared by every accepted transition. The tests were not weakened to accommodate the bug.

## Approved no-artifact CI verification

Pending owner approval of the source push and a successful workflow run that records:

- compilation and all 71 unit tests with zero failures/errors/skips;
- fresh debug APK assembly;
- zip alignment and APK Signature Scheme v2 verification;
- SHA-256;
- clean-source check;
- zero uploaded artifacts;
- no historical 09c device-APK artifact marker in the commit message.
