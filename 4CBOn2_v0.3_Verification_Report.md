# 4CBOn2 v0.3 — Verification Report

**Date:** 2 November 2026 · **Branch:** `arena/f4b22ec8-4cbon2` · **Package:** `v0.3.0-div50`
**Baseline carried in:** v0.2 package (`v0.2.0-div42`, APK SHA-256 `2667bacd…63f93e`)

---

## 1. Headline result

| Suite | Command | Result |
| --- | --- | --- |
| Kotlin core (pure JVM) | `scripts/verify_pure_kotlin.sh` | **59 / 59 pass, 0 fail** |
| Notebook (Python) | `python3 -m unittest discover -s tests -p "test_*.py"` | **10 / 10 pass, 0 fail** |
| Android Gradle build / APK | `./gradlew :app:testDebugUnitTest :app:assembleDebug` | **not executed** (see §4) |

Relative to the v0.2 baseline (38 Kotlin tests, 8 Python tests), v0.3 adds **21 Kotlin tests** and
**2 Python tests**, and all 38 pre-existing Kotlin tests still pass unchanged.

---

## 2. Kotlin test inventory

| Test class | Tests | What it proves |
| --- | --- | --- |
| `Batch1LocalCoreTest` | 6 | Persistence, drafts, knowledge library, analyzer, file caps, outbox (unchanged) |
| `Batch2ControlBrowserServiceTest` | 5 | Capability registry, scope broker, lease, browser boundaries (unchanged) |
| `Batch3BundleLoopRouterTest` | 3 | Job bundles, bounded loop, routine engine, router (unchanged) |
| `Batch4PipelinesLabAndSecurityTest` | 6 | Prepare-only pipeline, submission gate, Improvement Lab, injection guard (unchanged) |
| `Batch5RunTelemetryAndEvaluatorsTest` | 5 | **Div 43/44** — `r11l` fixture (96,609 tokens / 53 actions ⇒ 1,822.8 tokens/action), rejection paths, redaction, fixed evaluator registry |
| `Batch6DiagnosisAndCandidateDiffTest` | 7 | **Div 45/46** — 25.83→25.15 regression, noise tolerance, token burn, one-key diff policy, over-cap refusal, imported-diff revalidation |
| `Batch7ChampionGateAndDailyCycleTest` | 6 | **Div 47/48/49** — owner approval, rollback, cost envelope, takeover-before-dispatch, duplicate-dispatch prevention, uncertain dispatch reconciliation across restart, `NO_ACTION_NEEDED` honesty |
| `Batch8DailyLabAndTransportTest` | 3 | **Div 49/50** — bearer header construction, credential buffer zeroing, status-code mapping, Daily Lab card state |
| Existing unit classes (9) | 18 | Credential handling, GitHub/Kaggle read clients, navigation, Colab reference (unchanged) |
| **Total** | **59** | |

### Key assertions (excerpts)

- **Telemetry**: `96_609.0/53.0` tokens per action reproduced exactly from the live campaign payload;
  missing score/actions/tokens and queued kernels are *rejected*, and nothing is stored for them.
- **Redaction**: a payload containing `Bearer …` and `KGAT_…` is stored with both values masked and
  `redactions_applied = true`; a credential-*shaped key* is dropped entirely.
- **Diagnosis**: 25.83 → 25.15 produces `SCORE_REGRESSION` (High) with `delta = -0.68` in the evidence
  line; a 0.4% drop produces `INSUFFICIENT_EVIDENCE` and **no** proposed change.
- **Candidate diff**: the token-burn fixture yields exactly one key,
  `reasoning_token_budget=360` (= champion pace × 0.90). A champion pace above the owner cap is
  refused with `policy_violation` instead of silently raising the budget.
- **Promotion**: the champion does not move on `evaluate(approvedByOwner = false)`; it moves only via
  `applyOwnerDecision(approved = true)`, and `rollbackLastPromotion` restores `v3`.
- **Cycle**: a full run from pasted telemetry → diagnosis → revert diff → dispatch → candidate
  telemetry → gate → owner promotion completes with ≥5 audit-timeline events, survives a simulated
  process restart mid-flight, and never re-sends a duplicate dispatch on double-tap.

---

## 3. Notebook test inventory

| Test | What it proves |
| --- | --- |
| `test_notebook_structure_and_clean_outputs` | 10 cells, no stored outputs, no execution counts |
| `test_runtime_detection_and_capability_panel` | Runtime roles + 15-row capability panel |
| `test_shared_job_bundle_integrity_and_hash_is_not_authority` | Bundle hashing and authority separation |
| `test_colab_built_in_ai_adapter_probe_repair_and_kaggle_block` | Colab AI probe/repair, Kaggle block |
| `test_agent_execution_core_bounded_loop_handoff_and_stop` | Bounded loop + Stop |
| `test_privacy_prompt_injection_and_exfiltration_guard` | Injection + exfiltration guard |
| `test_kaggle_prepare_pipeline_submission_gate_and_improvement_lab` | Prepare-only + gate + lab |
| `test_arc_agi_3_frontier_policy_and_live_entrypoint` | Frontier policy + entrypoint |
| **`test_run_telemetry_emission_is_validated_and_secret_free`** | **Div 51** — emits `4cbon2.run_telemetry.v1`, refuses missing score/actions, masks `Bearer`/`KGAT_`, atomic write, tampered record refused |
| **`test_candidate_diff_application_is_single_key_bounded_and_revalidated`** | **Div 51** — one allowlisted key, immutable input config, protected keys/unknown keys/out-of-range/contract mismatch refused, revert restores champion config |

---

## 4. What was **not** executed, and why

| Item | Status | Reason / next step |
| --- | --- | --- |
| `./gradlew :app:testDebugUnitTest` | Not run | Fresh sandbox has no JDK/Android SDK and cannot reach `dl.google.com` (the only blocked host in this environment's allowlist) |
| `:app:assembleDebug` + `apksigner verify` | Not run | Same; no new APK checksum can be claimed for v0.3 |
| Compose Daily Lab card (Division 50) | Review only | Written against the unit-tested `DailyLabPresenter`; needs a compile in an SDK-enabled environment |
| Live Kaggle kernel dispatch | Not run | Endpoint requires a multipart notebook body; ships **unconfigured** by design |
| Live GitHub workflow dispatch | Not run | Requires a stored fine-grained PAT; the transport is exercised against a mocked `HttpURLConnection` |
| Live Google MCP / Colab AI calls | Not run | Requires an owner-present Colab session |

**To finish Division 52 on a machine with SDK access:**

```bash
scripts/ensure_toolchain.sh            # provisions JDK 17 + Android SDK
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

Then record the new APK SHA-256 in `PROTOTYPE_PACKAGE_AND_SUPPORT_MATRIX.md`.

---

## 5. Sandbox harness note

`scripts/verify_pure_kotlin.sh` compiles the real Kotlin sources (every file that does not import
`android.*`, plus `FourCBOn2WorkspaceContainer.kt` and `ScopedWorkspaceFileManager.kt` against
compile-only Android stubs) together with the real `src/test/**` classes and a minimal `org.junit`
shim, then executes every `@Test` method reflectively.

Justification: the canonical Gradle path is blocked here, and a project whose core is deliberately
Android-free should be verifiable without an emulator. The harness is *additive*: it does not modify
production code paths, and Gradle/JUnit remains the authority before shipping.

Supporting refactors made in this pass (behaviour-preserving, same package, same public API):

- `LocalDatabase.kt` → `AndroidSqliteBackend.kt` holds the only `android.*` code in persistence.
- `ActionTimelineAndIntents.kt` → `AndroidIntentLauncher.kt` holds the only `android.*` intent code.
- `BrowserSurfaces.kt` → `AndroidBrowserPlatform.kt` holds Custom Tabs + WebView hardening;
  `configureIsolatedWebView` is now a top-level extension with the same call syntax.

---

## 6. Honest capability statement for v0.3

> 4CBOn2 v0.3 can ingest a finished run's evidence, diagnose it against a locked champion, propose one
> bounded change with a stated hypothesis, dispatch it through an owner-approved channel, and gate the
> result through a fixed evaluator that it cannot edit — with every step durable, auditable, and
> stoppable. It still cannot promote itself, invent a score, submit a competition entry without a
> human tap, or replace the evaluator that judges it. And until the APK is rebuilt with Gradle, the new
> UI card is code-complete rather than device-verified.
