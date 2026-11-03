# 4CBOn2 v0.3 — Daily Experiment Cycle Design

**Version:** `v0.3.0-div50` · **Date:** 2 November 2026
**Scope:** Divisions 43–52 · **Status:** core complete, unit-verified; APK packaging pending SDK access.

---

## 1. The problem this design solves

v0.2 shipped 42 divisions of local, owner-gated tooling: credential vault, scope contracts, takeover
lease, prepare-only Kaggle pipeline, submission gate, and a tabular Improvement Lab. That is a solid
*apparatus*, but it is not a *researcher*.

During the live ARC-AGI-3 campaign on 2 November 2026 the owner asked the question that defines v0.3:

> "4CBOn2 app was supposed to think and get better at this everyday by itself — Right?"

The honest answer was no, for three reasons:

| Gap | v0.2 reality | v0.3 answer |
| --- | --- | --- |
| Wrong evidence type | Evaluator only read local `.csv` folds (`CV_RMSE`) | `RunTelemetry` normalizes `score` / `levels` / `actions` / `tokens` from ARC diagnostics, Kaggle kernel status, or manual entry (Div 43) |
| No telemetry return path | App opened Colab/Kaggle URLs and hoped | Ingest → durable records → diagnosis against a locked champion (Div 43/45) |
| No closed loop | Manual comparison inside one lab session | `DailyExperimentCycle` state machine with dispatch, gate, promotion, rollback (Div 47–49) |

---

## 2. The loop

```text
                    ┌──────────────────────────────────────────────┐
                    │  Owner: "Run today's cycle"                  │
                    └───────────────────┬──────────────────────────┘
                                        ▼
   run diagnostics.json  ──▶  [43] RunTelemetryIngest ──▶ 4cbon2.run_telemetry.v1 (sanitized)
                                        ▼
                            [44] FixedEvaluatorRegistry ──▶ EvaluatedScore | Inconclusive | Rejected
                                        ▼
                            [45] RunDiagnoser (vs locked champion) ──▶ ranked findings + evidence
                                        ▼
                            [46] CandidateDiffSynthesizer ──▶ ONE allowlisted patch + hypothesis
                                        ▼
                     ┌──────────────────────────────────────────┐
                     │  Owner: 1-tap "Approve & Run"            │
                     └──────────────────┬───────────────────────┘
                                        ▼
                            [48] durable dispatch intent (pre-send, idempotency key)
                                        ▼
                            [49] transport: Kaggle kernel | GitHub workflow | Colab handoff
                                        ▼
   candidate diagnostics.json  ──▶  ingest ──▶ [47] FixedPromotionGate
                                        ▼
                     ┌──────────────────────────────────────────┐
                     │  Owner: 1-tap "Approve Promotion"        │
                     └──────────────────┬───────────────────────┘
                                        ▼
                         champion pointer moves · history appended · rollback available
```

Every arrow is a durable write. Closing the app mid-cycle loses nothing: the state machine resumes at
the stage it reached.

---

## 3. State machine (Division 48)

```text
INGESTED ──diagnose──▶ DIAGNOSED ──propose──▶ PROPOSED
                                                │ owner approves
                                                ▼
                                            DISPATCHED ──telemetry──▶ GATED
                                                │                        │ owner decides
                                    (ambiguous) ▼                        ├──▶ PROMOTED
                                     AWAITING_RECONCILIATION              ├──▶ REJECTED
                                                                         └──▶ INCONCLUSIVE
Terminal: PROMOTED · REJECTED · INCONCLUSIVE · NO_ACTION_NEEDED · STOPPED · BLOCKED
```

Rules enforced in code and covered by tests:

1. **One live cycle per competition.** `openCycle` returns `AlreadyRunning(existing)` rather than
   creating a parallel experiment.
2. **Pre-send intent.** `DispatchIntent(state = PREPARED)` is persisted *before* the transport call,
   then updated to `DISPATCHED` / `UNCERTAIN` / `REJECTED` / `NOT_CONFIGURED`.
3. **No blind retry.** An `UNCERTAIN` intent blocks re-dispatch until `reconcileDispatch(landed = …)`.
4. **Double-tap safe.** Re-entering `approveAndDispatch` on a dispatched cycle returns the existing
   dispatch reference and sends nothing.
5. **Stop and takeover bite first.** `leaseController.currentSnapshot().mode` must be
   `AUTOMATION_ACTIVE`, otherwise the cycle goes `BLOCKED` with a takeover timeline event and the
   transport is never called.
6. **Restart-safe.** All state lives in the local store (`daily_cycles`, `candidate_diffs`,
   `diagnosis_reports`, `daily_dispatch_intents`); a new process reconstructs the same stage.

---

## 4. Contracts

### 4.1 `4cbon2.run_telemetry.v1`

| Field | Meaning | Required |
| --- | --- | --- |
| `telemetryId` | stable id | yes |
| `competitionSlug` | competition key | yes |
| `benchmarkKind` | `ARC_AGI_INTERACTIVE` \| `TABULAR_CSV_CV` \| `SCALAR_METRIC` | yes |
| `versionRef` | kernel version / notebook version | yes |
| `metricName` + `primaryMetric` + `higherIsBetter` | the number under comparison | yes |
| `levelsCompleted` / `totalActions` / `tokensUsed` / `wallClockMs` | behaviour + cost | yes |
| `source` | diagnostics JSON / kernel status / Colab receipt / manual | yes |
| `redactionApplied` | the record passed `TelemetrySanitizer` | yes |
| `diagnostics` | sanitized key/values (`redactions_applied` when masking occurred) | optional |

Derived, never stored: `tokensPerAction`, `actionsPerLevel`.

Ingest rejections (each becomes a `BLOCKED` cycle with a precise reason): `malformed_json`,
`missing_tokens`, `missing_actions`, `missing_score`, `missing_competition`, `kernel_not_complete`,
`negative_counters`.

### 4.2 `4cbon2.diagnosis_report.v1`

Ranked `DiagnosisFinding`s, each with `kind`, `severity`, `lever`, `summary`, and `evidence` lines that
contain the raw numbers. Ranking is deterministic: severity, then a fixed kind priority, then name.
`recommendedLever` is derived from the top finding only.

### 4.3 `4cbon2.candidate_diff.v1`

| Property | Enforcement |
| --- | --- |
| Exactly one key | `MAX_KEYS_PER_DIFF = 1` |
| Allowlisted keys only | `reasoning_token_budget`, `max_actions_per_level`, `exploration_temperature`, `exploration_frontier_weight`, `dead_action_prune_threshold`, `max_wall_clock_ms`, `max_actions`, `max_total_tokens`, `revert_to_version` |
| Bounded values | each key has a hard numeric range; spend keys are capped by `ExperimentBudgetCaps` |
| No protected areas | keys touching `evaluator*`, `submission*`, `credential*`, `secret*`, `promotion*`, `champion*` are refused |
| No evaluator mutation | an attempted `evaluator_id` is refused with a named reason |
| Re-validated on import | `validateImportedDiff` re-runs every rule for diffs arriving from outside the app |

### 4.4 `4cbon2.promotion_report.v1`

Verdicts: `AWAITING_OWNER_APPROVAL`, `PROMOTED`, `REJECTED`, `INCONCLUSIVE`, `BLOCKED`.

Hard gates before any comparison: comparable benchmark and metric, identical direction, sanitized
telemetry, non-zero token usage, completed run, enough actions to be significant.

Quality rule: the candidate must beat the champion by at least the declared relative margin, and its
token pace must stay inside the champion's cost envelope (default +25%). Anything inside the noise
band is `INCONCLUSIVE` — never a promotion, and never reported as a regression.

---

## 5. Safety posture (unchanged from v0.2, extended)

| Invariant | Where enforced |
| --- | --- |
| No credential ever stored, logged, or echoed | `TelemetrySanitizer`, `BearerAuthenticatedDispatchHttp` (buffer zeroed after one header use) |
| Owner-gated promotion | `PromotionGateOutcome.Promoted` only via `approvedByOwner = true` |
| Immediate Stop / Takeover | lease check inside `approveAndDispatch`; timeline event |
| Submission caps untouched | diff allowlist cannot see submission settings |
| Honest capability reporting | `NotConfigured` instead of a fabricated dispatch, `Inconclusive` instead of a guessed score |
| Immutable history | champion records are append-only; promotion supersedes, rollback restores |

---

## 6. What v0.3 deliberately does **not** claim

- It does not fine-tune a model, rewrite its own source, or promote anything unattended.
- It does not fabricate a live Kaggle/GitHub run when a credential or endpoint is missing: the cycle
  reports `NotConfigured` and stops.
- The Kaggle kernel-push endpoint requires a multipart notebook body that the app does not build, so
  the Kaggle channel ships **empty** (honestly "not configured") until the owner supplies an endpoint
  that matches their own runner. The Colab manual handoff and the GitHub workflow dispatch are wired.
- The Compose Daily Lab card (Division 50) was written against the verified presenter but could not be
  compiled in the sandbox that produced this document (no Android SDK access). See the verification
  report for exactly what was and was not executed.

---

## 7. Verification

| Layer | Command | Result |
| --- | --- | --- |
| Kotlin core (pure JVM) | `scripts/verify_pure_kotlin.sh` | **59 / 59 tests pass** |
| Notebook (Python) | `python3 -m unittest discover -s tests` | **10 / 10 tests pass** |
| Android build (Gradle/APK) | `./gradlew :app:testDebugUnitTest :app:assembleDebug` | **not run here** — requires `dl.google.com` |

The sandbox harness exists because the canonical Gradle path needs the Android SDK, which some
environments block. It compiles the real sources and executes the real `@Test` methods; it is a
convenience, not a replacement for Gradle before shipping an APK.
