# Batch D Receipt — Divisions 32–36 (Guards, Routines, Lab, Privacy, Routing)

**Date:** 31 October 2026
**Branch:** `arena/61ac1ccb-4cbon2`

## What shipped

| Division | Deliverable | Files |
|---|---|---|
| 32 | Critical phone-action guard: sensitive/manual-only contexts, exact preview, account checks, no blind irreversible retry | `guard/CriticalActionGuard.kt` |
| 33 | Human-defined routine engine: fixed script, explicit human trigger, preconditions, bounded execution | `routines/RoutineEngine.kt` |
| 34 | Conditional personal-lab UI runner eligibility: config + fixture + device-test gate, never claims Play compliance | `lab/LabRunnerEligibility.kt` |
| 35 | Privacy and prompt-injection test fixtures: unauthorized destinations, secret-leakage attempts, bundle tamper, consent defaults | `app/src/test/.../safety/PrivacyAndInjectionTest.kt` |
| 36 | Online/offline phone-control routing: local/API/model only, locked-device NONE, no silent alternate provider, prompt text never routes | `routing/ControlRoutingPolicy.kt` |

## Verification

- **Local JVM unit tests written** (JUnit 4): 25 tests across 5 test classes —
  guard decisions for every sensitive context and uncertain-outcome retry
  block, routine trigger/precondition/budget boundaries, lab eligibility
  conditions and Play-compliance honesty, routing priorities + locked device +
  injection-immunity, and the privacy/injection fixture suite.
- **Static/source review** of all new classes.
- **NOT performed in this sandbox:** compilation/execution (no JDK/Gradle).
  Reported as **static/source + unit-test-written**.

## Boundaries

- Division 34 remains **device-eligibility blocked**: the eligibility gate is
  implemented; actual lab-runner execution requires an eligible device and a
  tested fixture app.
- No accessibility-service takeover, root, or invisible cross-app tapping is
  implemented — by design.
