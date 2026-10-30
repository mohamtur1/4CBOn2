# Division 21 — One harmless app intent action

`BoundedAppAction` performs one harmless **open** of one owner-declared entry point, and
reports honestly what happened. It is the first division that reaches an actual external
effect; everything before it was local logic.

**Verification status: local JVM only.** 30 new tests ran in a standalone Kotlin harness
(`passed=207 failed=0` over 18 classes). No-artifact CI is **pending**.

> **The real dispatch is untested.** CI runs on a plain JVM with no Android framework, so
> every test here drives a *fake* port. No real intent was ever sent, on any device or
> emulator. The decision logic and the outcome reporting are tested; the platform call is not.

## The one action, and why it is harmless

The action opens a screen that the owner already selected in
`SelectedAppCapabilityRegistry` and explicitly declared as a capability. It sends nothing,
buys nothing, deletes nothing, posts nothing, and writes nothing outside the target app.

**Worst case if it misfires:** an app opens to a screen the owner did not want. That is
visible, harmless, and reversible by going back. There is no state to corrupt and nothing to
undo.

Anything consequential is excluded **structurally**, not by good intentions:

- Only `ActionRoute.APP_INTENT` is dispatched.
- `APP_LINK_OR_SHARE` is refused with `APPROVAL_REQUIRED` — a share is one of the acts
  Division 20's approval gate governs.
- `MANUAL_HANDOFF` is refused with `MANUAL_STEP_ONLY` — the owner performs that step.
- `AUTHORIZED_API` and `FIXED_ROUTINE` are refused with `ROUTE_NOT_DISPATCHABLE`.
- A `cloudTransfer = true` request is refused with `APPROVAL_REQUIRED`.

The class has **no parameter** that would let it redeem a `SensitiveActionApproval`, so there
is no path from here to an ungated sensitive act. It declines gated work rather than
performing it.

## The honesty model — the point of this division

```kotlin
sealed interface ActionOutcome {
    data class Refused(...)   // nothing was attempted
    data class Completed(...) // the platform confirmed delivery
    data class Failed(...)    // attempted, platform proved nothing happened
    data class Unknown(...)   // attempted, result could not be determined
}
```

Four cases exist because collapsing any two would mislead the owner:

| Case | Guarantee |
| --- | --- |
| `Refused` | **Nothing was attempted.** Only produced before the port is called. |
| `Completed` | The platform reported `Delivered` inside the deadline. |
| `Failed` | A dispatch happened and the platform *proved* it did not take effect. |
| `Unknown` | A dispatch happened and the outcome cannot be established. |

`Unknown` is a first-class outcome, never a soft failure and never a soft success. A test
drives three different indeterminate paths and asserts the result is `Unknown`, is **not**
`Completed`, **not** `Failed`, and **not** `Refused`.

### What produces `Unknown`

| Cause | Why it cannot be success or failure |
| --- | --- |
| `DEADLINE_EXCEEDED` | The answer arrived after the promised window, so it describes a different moment than the one we bounded. |
| `BOUNDARY_ERROR` | The call threw; we cannot know whether it threw before or after the platform acted. |
| `BOUNDARY_IN_DOUBT` | The boundary itself declined to say. |

The deadline case is tested against a port that reports `Delivered` **and overruns**: the
result is still `Unknown`, because a late success claim is not an answer. Exactly at the
deadline the result is `Completed`, so the boundary is a real edge rather than an estimate.

### No retry

There is no retry anywhere, and no method named `retry`, `rerun`, `repeat`, `escalate`,
`fallback`, `broaden`, `widen`, `upgrade`, `queue`, `schedule`, `background`, `loop`, `drain`,
or `poll`. A test asserts the absent names.

Retrying after `Unknown` could perform the act twice, so an unknown result is reported and
left alone. The test asserts exactly **one** port call for an indeterminate result.

## Composition

Every pre-flight check runs before the port, in a fixed documented order:

1. `REQUEST_INVALID` — the package or action id is not a bounded identifier
2. `CAPABILITY_UNAVAILABLE` — the registry does not support this action
3. `MANUAL_STEP_ONLY` — the owner performs that route
4. `APPROVAL_REQUIRED` — a share route
5. `ROUTE_NOT_DISPATCHABLE` — not a screen open
6. `SCOPE_REFUSED` — the scope enforcer declined
7. `LEASE_NOT_READY` — the lease is not ready for a step
8. `APPROVAL_REQUIRED` — a cloud transfer

Because every refusal happens before the port is called, `Refused` genuinely means "nothing
was attempted". A recording port proves it: across seven distinct refusals and three invalid
request shapes, the port records **zero** calls.

Each authority is consulted rather than re-implemented: the registry answers *is this a thing
the owner declared*, the enforcer answers *is it in this job*, and the lease answers *am I in
control right now*.

### Two ordering details worth stating

- **Share beats scope.** A gated route is reported as `APPROVAL_REQUIRED` even when the job
  is also out of scope, because "this needs an approval I do not handle" is the more useful
  truth.
- **Scope beats the transfer check.** A `cloudTransfer` request against a scope that forbids
  cloud transfer reports `SCOPE_REFUSED`, not `APPROVAL_REQUIRED`. The scope refusal is the
  truthful first cause. The stronger property — refusing the transfer *even when the scope
  allows it* — is tested separately, which is where the real guarantee lives.

## The platform seam

```kotlin
fun interface AppActionPort {
    fun dispatch(request: AppOpenRequest): DispatchReport
    companion object { val NONE: AppActionPort = ... }   // refuses everything
}
```

`AppActionPort.NONE` is the default and the only implementation this build wires. It returns
`Rejected("No platform port is configured in this build.")` without touching anything, so the
**shipped app cannot dispatch at all**. Wiring a real port is a separate, reviewable decision
for a later division, and it should come with a device test.

`DispatchReport` has three cases — `Delivered`, `Rejected`, `InDoubt` — so a boundary cannot
silently treat doubt as either answer.

## Bounds

| Bound | Value |
| --- | --- |
| `MAX_ROUND_TRIP_MILLIS` | 5 000 |
| `MAX_ACTION_ID_LENGTH` | 64 |

The request type carries exactly two fields — `packageName` and `actionId` — and a test
asserts no field named `uri`, `url`, `intent`, `command`, `extras`, `data`, `chooser`, or
`text` exists. There is no free text, no chooser, and no arbitrary destination.

## Persistence

None. The outcome is returned to the caller, not stored. Schema stays at **version 3** with
no migration.

A result here is a moment in time, not a durable record: an owner who wants to keep it can
save it through the Division 17 knowledge library, where provenance is recorded explicitly.
Persisting every attempt automatically would create a log nobody asked for.

## Tests — 30 new, all synthetic

| File | Count | Coverage |
| --- | --- | --- |
| `AppOpenRequestTest` | 6 | Request bounds, no-smuggle field set, default port refusal, report distinctness |
| `BoundedAppActionTest` | 24 | All four outcomes, unknown non-collapse, no-retry, pre-flight refusals never reaching the port, route narrowing, ordering, preview, backwards clock, inertness |

Active suite: **228** tests (198 preserved plus these 30).

Local harness: `STANDALONE_TEST_RUN passed=207 failed=0` over 18 classes — the 207 pure-JVM
tests, up from 177. Tests needing the Android framework run only in CI.

## Development record

The first run had three failures, then one more. All four were **my fixtures**, but the fourth
exposed a **real production gap**.

1. My fixture probed `MANUAL_HANDOFF` while declaring only `APP_INTENT`, so the registry
   correctly answered "unavailable". Fixed the fixture.
2. My gated-act test used a scope that forbids cloud transfer, so the truthful first cause was
   `SCOPE_REFUSED`. Rewritten to test the stronger property: refused **even when the scope
   allows it**.
3. The ordering test needed the same scope correction.
4. **Real gap.** My test listed `APP_LINK_OR_SHARE` as a refusal and got `Completed` — the
   class would happily have dispatched a **share**, which is exactly a Division 20 gated act.
   The route gate was narrowed so only `APP_INTENT` dispatches, `APP_LINK_OR_SHARE` is refused
   as `APPROVAL_REQUIRED`, and three new tests pin the narrowed set — including one that walks
   `ActionRoute.entries` and asserts every non-`APP_INTENT` route is refused, so the set cannot
   quietly grow.

That fourth failure is the most valuable thing that happened in this division: the class
claimed "harmless opens only" in prose while its code would have shared.

## CI

**Pending.** The exact total moves from 198 to 228 in all five workflow sites.

## Boundaries — read these before trusting this division

- **No real dispatch has ever been performed.** Not on a device, not on an emulator, not
  against a real app. The platform call is unverified.
- No emulator, real-device, permission-granted, or live-service evidence of any kind.
- No UI: nothing in the app calls this yet, and there is no screen showing an outcome.
- No performance measurement on a phone.
- No claim that an app actually opened.
- The 5-second deadline is a policy bound, not a measured platform latency.
