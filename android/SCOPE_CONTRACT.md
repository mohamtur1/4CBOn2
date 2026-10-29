# Division 13 — On-device scope contract

**Work branch:** `arena/74f65509-4cbon2` (the fixed Arena session branch; source baseline imported from `arena/0eb49904-4cbon2` at `2a45201`)
**Scope:** A local, per-job promise sheet covering goal, selected apps, allowed actions, recipients, files, cloud-transfer permission, completion mode, bounded limits, expiry, and exclusions.
**Evidence class:** Source/static checks plus JVM unit tests, followed by no-artifact CI (receipt recorded separately once verified).

## Like you are two

Before 4CBOn2 does a job, it writes a little promise sheet. The sheet says which helper apps may help, which one thing each may do, who they may talk to, whether anything may leave the phone, how many steps are allowed, and when the sheet expires. If a step is not written on the sheet, the answer is “no.” Leaving the phone is a “no” too, unless you wrote “yes” yourself.

## Like you are an adult

Division 13 adds a bounded, local scope contract enforced in pure Kotlin with no new dependencies. It composes with the Division 12 capability registry instead of duplicating route or sensitivity rules: the registry decides *which route* is possible, and the scope contract decides *whether this job may use it at all*.

Every request resolves to exactly one `ScopeDecision`: a `Permitted` step carrying the concrete route, sensitivity, and whether owner confirmation is required — or a `Refused` step with one explicit `ScopeRefusal`.

## Refusal order (fail-closed, before any discovery)

| # | Check | Refusal |
| --- | --- | --- |
| 1 | Contract expired (`now >= createdAt + duration`) | `SCOPE_EXPIRED` |
| 2 | Package is explicitly excluded | `TARGET_EXCLUDED` |
| 3 | Action is explicitly excluded | `ACTION_EXCLUDED` |
| 4 | Package is not a target of this job | `TARGET_NOT_IN_SCOPE` |
| 5 | Action is not allowed for that target | `ACTION_NOT_IN_SCOPE` |
| 6 | Cloud transfer requested but not permitted | `CLOUD_TRANSFER_NOT_PERMITTED` |
| 7 | Action budget already consumed | `ACTION_LIMIT_REACHED` |
| 8 | Registry has no reachable route | `CAPABILITY_UNAVAILABLE` (+ registry reason) |

Exclusions always beat allow-list entries, and an expired or exhausted job is refused before the registry is consulted at all, so no discovery happens for a dead scope.

## Model

| Type | Purpose and limits |
| --- | --- |
| `ScopeBounds` | Hard caps: at most 20 actions and at most 5 minutes (the owner-started proposal). A looser contract cannot be constructed. |
| `ScopeLimits` | Per-job `maxActions` (1–20) and `durationMillis` (1–300000). |
| `CompletionMode` | `OWNER_CONFIRMS_EACH_STEP`, `OWNER_CONFIRMS_AT_END`, `AUTOMATIC_WITHIN_SCOPE`. |
| `ScopeTarget` | Dotted package name plus the exact allowed action IDs for this job. Being listed grants nothing outside those actions. |
| `ScopeContract` | Goal, targets, allowed recipients, allowed file labels, `cloudTransferAllowed` (**default `false`**), completion mode, limits, creation time, and exclusions. Derives `expiresAtEpochMillis`. |
| `ScopeDecision` | `Permitted` (route, sensitivity, `requiresOwnerConfirmation`, `requiresOwnerAction`, `cloudTransfer`) or `Refused` (reason + optional detail). |
| `ActionScopeEnforcer` | Read-only `authorize(...)` plus `recordCompletion(...)`, which is the only operation that consumes the action budget. |

Owner confirmation is required whenever the completion mode asks for per-step approval **or** the resolved route is `MANUAL_HANDOFF`. Manual handoff therefore always needs the owner even under `AUTOMATIC_WITHIN_SCOPE`, and a `MANUAL_ONLY` action stays manual because the Division 12 model permits only the handoff route.

## Deliberate boundaries

- **No UI wiring and no persistence:** no new schema version, file format, or database migration.
- **No real data:** recipients and file labels are bounded local labels; no contacts, documents, accounts, notifications, or installed-app list is read.
- **No cloud transfer:** the flag defaults to off; nothing in this division performs network I/O, so permission here is a precondition for later work, not an implementation of upload.
- **No accessibility service, screen capture, overlay, or automation**, and no permission request.
- **No claim of universal phone/app control**, production signing, or device verification.

## Tests

Two synthetic JUnit test files add 15 tests:

| File | Tests | Coverage |
| --- | --- | --- |
| `ActionScopeContractTest` | 5 | Bounded limits, target/duplicate validation, cloud-transfer default and derived expiry, goal/recipient/file-label bounds, exclusion validation |
| `ActionScopeEnforcerTest` | 10 | In-scope permit, unselected target, disallowed action, exclusions beating allow-lists, cloud transfer off/on, expiry boundary, registry reason surfacing, owner pause inside a scope, budget accounting, owner-confirmation rules |

The active suite is **55** (`40` preserved + `15` new). The CI source-inventory guard, step label, notice, and exact XML aggregation assertion are aligned to 55; none was weakened.

## Local checks

- Active `@Test` source count: **55**.
- New runtime dependency count: **0**.
- Local Android JDK/Gradle/Android SDK toolchain: **absent**; no local Gradle compilation, APK assembly, alignment, or signature is claimed.
- Isolated Kotlin 2.4.21 / JVM 17 type-check of the new source and test files: **passed** (not a project Gradle build).
- Reflective standalone JVM run using a stub JUnit harness: **15 passed, 0 failed** for the Division 13 classes and **15 passed, 0 failed** for the Division 12 regression run (`STANDALONE_TEST_RUN passed=15 failed=0`). Not a Gradle, emulator, or device test.
- Harness fidelity finding: the first reflective run reused one test instance across methods and leaked the injected clock, which correctly failed two tests. The harness now creates a fresh instance per test method, matching JUnit; the production code was not changed to satisfy the harness.

## Approved no-artifact CI verification

Pending owner approval of the source push and a successful workflow run that records:

- compilation and all 55 unit tests with zero failures/errors/skips;
- fresh debug APK assembly;
- zip alignment and APK Signature Scheme v2 verification;
- SHA-256;
- clean-source check;
- zero uploaded artifacts;
- no historical 09c device-APK artifact marker in the commit message.
