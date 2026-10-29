# Division 12 — Selected-app capability registry

**Work branch:** `arena/74f65509-4cbon2` (the fixed Arena session branch; source baseline imported from `arena/0eb49904-4cbon2` at `2a45201`)
**Scope:** A minimal owner-selected app identity and action-capability model, action-level support, explicit unavailable states, and narrow discovery. No blanket installed-app inventory.
**Evidence class:** Source/static checks plus JVM unit tests, followed by no-artifact CI (receipt recorded separately once verified).

## Like you are two

The app now keeps a short list of helpers you picked yourself. You say “this app may help with this one thing.” 4CBOn2 checks the gentlest way first, and if nobody can do it, it says “can’t do that” instead of guessing. It never goes looking at all the other apps on the phone.

## Like you are an adult

Division 12 adds a dependency-free, in-memory registry of **owner-selected** apps and their declared, action-level capabilities. An app is present only because the owner named it; nothing enumerates installed packages, activities, notifications, or screen content.

Resolution is fail-closed and returns one explicit decision:

| Decision | Meaning |
| --- | --- |
| `CapabilityDecision.Supported` | One concrete route was chosen, together with the action’s sensitivity |
| `CapabilityDecision.Unavailable` | One explicit reason: not selected, owner-disabled, action not declared, or no supported route currently reachable |

Supported routes are held in a fixed order from least to most powerful, and resolution must pick the earliest one that is both declared and currently reachable:

```text
AUTHORIZED_API -> APP_INTENT -> APP_LINK_OR_SHARE -> FIXED_ROUTINE -> MANUAL_HANDOFF
```

An `ActionRouteProbe` answers only the exact app/action/route question it is given. The default probe treats every route as unreachable, and a probe that throws is treated as unreachable rather than as permission to escalate. A paused (owner-disabled) app is never probed at all.

## Model

| Type | Purpose and limits |
| --- | --- |
| `SelectedApp` | Dotted Android package name, short display label, owner selection timestamp. No account, credential, device identifier, or inventory fields. |
| `AppActionCapability` | Action ID, label, `ActionSensitivity`, and the declared route set. At least one route is required. |
| `ActionSensitivity.MANUAL_ONLY` | Must declare **exactly** `MANUAL_HANDOFF`; any stronger route is rejected at construction. |
| `ActionRoute` | Fixed least-to-most-powerful ordering; `leastPowerful(routes)` is the only route selector. |
| `CapabilityDecision` | `Supported` (route + sensitivity + `requiresOwnerAction`) or `Unavailable` (explicit reason). |
| `SelectedAppCapabilityRegistry` | Owner selection, pause/resume, action declaration, deterministic snapshot, and resolution. |
| `SelectedAppSummary` | Deterministic snapshot entry (package name order, declared action IDs sorted) for future persistence or display. |

Sensitive workflows — banking, payment, password, one-time codes, biometrics, permissions, and security settings — are enforced as manual-only by the type itself, not by a convention that a later caller can forget. Manual-handoff decisions report `requiresOwnerAction = true`.

## Deliberate boundaries

- **No UI wiring:** nothing in the app screen reads or writes the registry yet.
- **No persistence:** no schema version, database migration, or file format was added in this division; the deterministic snapshot is the hand-off point for a later persistence decision.
- **No installed-app inventory:** no `PackageManager` query, no blanket discovery, no neighbouring-app inspection.
- **No accessibility service, screen capture, overlay, notification reader, or app automation:** capability identity and routing only.
- **No network, credential, account, or live-service call**; tests use synthetic package names such as `org.kaggle.app` and `org.bank.app`.
- **No claim of universal phone/app control**, production signing, or device verification.

## Tests

Two synthetic JUnit test files add 15 tests:

| File | Tests | Coverage |
| --- | --- | --- |
| `SelectedAppCapabilityTest` | 5 | Package-name validation, label/limit validation, route set validation, manual-only enforcement, fixed route ordering |
| `SelectedAppCapabilityRegistryTest` | 10 | Not-selected, owner-disabled without probing, undeclared action, least-powerful reachable route, manual fallback, nothing reachable, probe failure denial, narrow probing scope, duplicate/unselected writes, deterministic snapshot |

The active suite is **40** (`25` preserved + `15` new). The CI source-inventory guard, step label, notice, and exact XML aggregation assertion are aligned to 40; none was weakened.

## Local checks

- Active `@Test` source count: **40**.
- New runtime dependency count: **0**.
- Local Android JDK/Gradle/Android SDK toolchain: **absent**; no local Gradle compilation, APK assembly, alignment, or signature is claimed.
- Isolated Kotlin 2.4.21 / JVM 17 type-check of the new source and test files: **passed** (not a project Gradle build).
- Reflective standalone JVM run of the real test classes with a stub JUnit harness: **15 passed, 0 failed** (`STANDALONE_TEST_RUN passed=15 failed=0`). Not a Gradle, emulator, or device test.

## Approved no-artifact CI verification

Pending owner approval of the source push and a successful workflow run that records:

- compilation and all 40 unit tests with zero failures/errors/skips;
- fresh debug APK assembly;
- zip alignment and APK Signature Scheme v2 verification;
- SHA-256;
- clean-source check;
- zero uploaded artifacts;
- no historical 09c device-APK artifact marker in the commit message.
