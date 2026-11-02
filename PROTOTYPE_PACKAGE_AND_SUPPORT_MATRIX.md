# 4CBOn2 Prototype Package & Support Matrix (Division 42)

**Package Version:** `v0.2.0-div42`  
**Build Date:** `2026-10-31`  
**Status:** All `42` divisions (`01–42`, plus `09a`) implemented, compiled, unit-tested, and verified.

---

## 1. Packaged Deliverables & Verified SHA-256 Checksums

| Artifact | Workspace Path | Verification | SHA-256 |
| --- | --- | --- | --- |
| **Android Debug APK** | `/home/user/4CBOn2-debug.apk` | `assembleDebug` + `apksigner` APK Signature Scheme v2 (`true`) | `2667bacdb98be8213dd97897e09d8cc1fe17bedefbd221428759d357b063f93e` |
| **Colab / Kaggle Companion Notebook** | `/home/user/4CBOn2_Kaggle6.ipynb` | nbformat v4.5, cleared outputs, 7/7 `unittest` checks | `a7d355670ef12503deb5988998855621697d32ce0c01e7542703e873e220cefe` |
| **Build Plan & Status Ledger** | `/home/user/4CBOn2_Build_Divisions.md` | All 42 divisions marked **Finished** | Verified in bundle |
| **Source + Docs + Tests Bundle** | `/home/user/4CBOn2_Prototype_v0.2_Div42_Bundle.zip` | Clean secret-free archive (89 entries; no `.cache`, `.gradle`, or `build` dirs) | `9fca859afe51d0b6748f39a6d5917c04ed559628d042a796fe30adca9ce029bc` |

---

## 2. Reproducible Setup, Build, and Test Commands

### A. Provision / Activate Toolchain & Run All Android Tests + Build Signed Debug APK

```bash
source /home/user/scripts/ensure_toolchain.sh
cd /home/user/android
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

- **Verified Android Unit Test Result:** `15` JUnit4 test suites, **`38` tests, `0` failures, `0` errors**.
- **Verified APK Signature:** `Verified using v2 scheme (APK Signature Scheme v2): true`.

### B. Regenerate & Test `4CBOn2_Kaggle6.ipynb`

```bash
python3 /home/user/scripts/build_notebook.py
python3 -m unittest discover -s /home/user/tests -p "test_*.py" -v
```

- **Verified Notebook Test Result:** **`7` tests, `0` failures** (`0.035s`).

### C. Install APK on a Physical Android Device or Emulator (API 29+)

```bash
adb install -r /home/user/4CBOn2-debug.apk
```

1. Open **4CBOn2** -> **Settings** tab to optionally paste a local GitHub PAT (`repo` read) or Kaggle token (`KGAT_...` or `username:key`) into the hardware-backed Android Keystore (`AES/GCM/NoPadding`). Credentials are never written to logs, shared bundles, or source files.
2. Use **Home** (Connectivity & Take Over / Stop Lease), **Files** (Knowledge Library, Scoped File Sandbox, CSV/JSON Local Analyzer), **Agents** (Offline Drafts, Persistent Outbox, Saved Human Routines, Shared Job Bundle Export/Import, Bounded Agent Loop), **Activity** (Actual Action Timeline, Sensitive-Action Approvals, Kaggle Prepare-Only Baseline & Improvement Lab), and **Browser** (Controlled Public Reader, Chrome Custom Tabs Handoff, Capability Registry).
3. Upload `/home/user/4CBOn2_Kaggle6.ipynb` to Google Colab (`COLAB_CONTROLLER` role) for interactive `google.colab.ai` planning, or run its deterministic cells on Kaggle (`KAGGLE_RUNNER` role) with `4cbon2.job_bundle.v1` JSON bundles.

---

## 3. Honest Capability Support Matrix

| Capability Domain | Divisions | Standard Profile (`Play-Safe`) | Personal-Lab Profile (`Sideload Only`) | Colab (`COLAB_CONTROLLER`) | Kaggle (`KAGGLE_RUNNER`) | Verification Tier |
| --- | --- | --- | --- | --- | --- | --- |
| Navigation shell & offline local SQLite/JSONL storage + corruption recovery | `04–06`, `11` | Supported | Supported | SQLite workspace | SQLite workspace | Unit + Build/APK |
| Android Keystore AES-GCM vault + masked local credential entry UI | `07`, `09a` | Supported | Supported | Secret redaction | Secret redaction | Unit + Build/APK |
| Read-only GitHub repo cache, unified LCS diff & patch preview + Kaggle sync status | `08`, `09`, `22`, `23` | Supported | Supported | Via bundle inputs | Dataset read | Unit + Live public GET (`08`) |
| Offline Agent Drafts, SAF File Import/Export (SHA-256), Knowledge Library & CSV/JSON Analyzer | `15–18` | Supported | Supported | Supported | Supported | Unit + Build/APK |
| Persistent Waiting Queue (`WAITING_FOR_INTERNET` / `UNCERTAIN_EXTERNAL_OUTCOME`, no blind replay) | `19`, `41` | Supported | Supported | Supported | Supported | Unit + Fault injection |
| Selected-App Capability Registry & On-Device Task Scope Contract (`TaskScopeBroker`) | `12`, `13`, `40` | Supported | Supported | Re-verified locally | Re-verified locally | Unit + Integration |
| Local `TakeOverLeaseController` (`leaseEpoch`) + `SensitiveActionApprovalGate` + `CriticalPhoneActionGuard` | `14`, `20`, `32` | Supported | Supported | Supported | Supported | Unit + Fault injection |
| Harmless App Intents (`VIEW`, `SEND` draft, `INSERT` calendar draft), Chrome Custom Tabs & Isolated Public Reader | `21`, `24`, `25` | Supported | Supported | N/A (Android UI) | N/A (Android UI) | Unit + Build/APK |
| Consented Observation Boundary, Actual Action Timeline & Prompt-Injection / Exfiltration Guard | `26`, `27`, `35` | Supported | Supported | Supported | Supported | Unit + Hostile fixtures |
| Canonical Shared Job Bundle (`4cbon2.job_bundle.v1`, canonical SHA-256, hash != authority) | `28` | Supported | Supported | Supported | Supported | Cross-runtime JUnit + Python |
| `google.colab.ai` Live Interactive Adapter (`list_models`, `generate_text`, <=2 JSON repairs, no token export) | `10`, `29`, `30` | Receipt verification | Receipt verification | **Supported (Live Colab)** | Blocked by design | Mocked + Receipt verification |
| Bounded Agent Loop, Human-Defined Routine Engine & Online/Offline Action Router | `31`, `33`, `36` | Supported | Supported | Supported | Bounded loop | Unit + Integration |
| Conditional Personal-Lab Cross-App UI Runner (observe -> plan -> act -> verify) | `34` | **Disabled (`OFF`)** | **Supported (Fixture / Sideload)** | Plans only | Disabled | Unit (Synthetic UI fixtures) |
| Deterministic Kaggle Tabular Prepare-Only Baseline + Submission Gate (`max=1/run`, `1/24h`, uncertain lock) | `37`, `38` | Supported | Supported | Supported | Supported | Unit + Timeout simulation |
| Minimal Improvement Lab (immutable versions, fixed sealed evaluator, no self-promotion, owner promote + rollback) | `39` | Supported | Supported | Supported | Supported | Unit (JUnit + Python) |

---

## 4. Explicit Safety Boundaries & Prototype Limitations

1. **Standard vs. Personal-Lab Distribution Boundary:** The **Standard** profile uses only Play-compatible Android APIs (`Intent`, Storage Access Framework, Chrome Custom Tabs, isolated no-cookie public reader). Cross-app UI observation/gesture automation (`ConditionalPersonalLabUiRunner`, Division `34`) is compiled behind an explicit `PersonalLabBuildProfile.PERSONAL_LAB_SIDELOAD` gate, disabled in Standard mode, and never targets banking, password/OTP, payment, lock-screen, or OS permission dialogs.
2. **Colab Built-in AI (`google.colab.ai`) Boundary:** Interactive free-tier `google.colab.ai` requires an active user-opened Google Colab notebook session (`COLAB_CONTROLLER`). The Android app never impersonates Colab proxy tokens, never silently routes prompts to paid external LLM APIs, and never treats `COLAB_OFFLINE` as live cloud intelligence.
3. **Live External Writes & Submissions Remain Owner-Gated:** Default Kaggle runs execute strictly in **prepare-only** mode (`externalSubmissionPerformed = false`). Live submission requires explicit owner enablement, a fresh SHA-256-bound `ApprovalGrant`, active lease validation, and durable pre-send ledger persistence; ambiguous network timeouts enter `UNCERTAIN_ACKNOWLEDGMENT` and lock automatic retries.
4. **Live Credential Verification:** Because zero owner secrets are embedded in this repository or environment, live authenticated Kaggle competition endpoints (`09`) and live interactive `google.colab.ai` sessions (`10`, `30`) are verified via deterministic unit/mocked adapters here and activate once the owner supplies credentials on-device or opens `4CBOn2_Kaggle6.ipynb` in Google Colab.

---

# v0.3 Addendum — Daily Experiment Cycle (Divisions 43–52)

**Package version:** `v0.3.0-div50` · **Date:** 2 November 2026
**Status:** Divisions 43–51 implemented and unit-verified; APK re-packaging (Division 52) deferred to an
environment with Android SDK access.

## 1. New verified artifacts

| Artifact | Workspace path | Verification |
| --- | --- | --- |
| Telemetry contract & ingest | `android/app/src/main/java/.../RunTelemetry.kt` | Unit — 5 tests |
| Fixed evaluator registry | `BenchmarkEvaluators.kt` | Unit — 5 tests |
| Run diagnoser | `RunDiagnoser.kt` | Unit — 4 tests |
| Bounded candidate diff synthesis | `CandidateDiffSynthesizer.kt` | Unit — 4 tests |
| Champion ledger & promotion gate | `ChampionLedgerAndPromotionGate.kt` | Unit — 3 tests |
| Daily Experiment Cycle | `DailyExperimentCycle.kt` | Unit — 4 tests (incl. restart, duplicate dispatch, takeover) |
| Dispatch transports | `ExperimentDispatch.kt`, `AuthenticatedDispatchHttp.kt` | Unit — 2 tests (mocked HTTP) |
| Daily Lab presenter + Compose card | `DailyLabPresenter.kt`, `MainActivity.kt` | Presenter: Unit — 1 test; card: review only |
| Notebook telemetry emitter / diff applier | `4CBOn2_Kaggle6.ipynb` (Division 51 cell) | Python `unittest` — 2 tests |
| Sandbox verification harness | `scripts/verify_pure_kotlin.sh`, `tools/pure_jvm_verify/` | Runs the real JUnit sources on a plain JVM |

**Totals:** 59/59 Kotlin tests and 10/10 Python tests pass in the sandbox used to build v0.3.

## 2. Reproduction commands

```bash
# Kotlin core (no Android SDK required)
scripts/verify_pure_kotlin.sh                 # compile + run every pure-JVM @Test
scripts/verify_pure_kotlin.sh --compile       # type-check the whole core only

# Notebook
python3 scripts/build_notebook.py
python3 -m unittest discover -s tests -p "test_*.py" -v

# Canonical Android verification (requires dl.google.com for the SDK)
source scripts/ensure_toolchain.sh
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## 3. Capability matrix delta

| Capability domain | Divisions | Standard profile | Personal lab | Verification tier |
| --- | --- | --- | --- | --- |
| Interactive-benchmark telemetry ingest (score/levels/actions/tokens) | 43 | Supported | Supported | Unit |
| Fixed multi-family evaluator set (ARC / tabular / scalar) | 44 | Supported | Supported | Unit |
| Deterministic diagnosis with numeric evidence | 45 | Supported | Supported | Unit |
| One bounded, allowlisted candidate diff per experiment | 46 | Supported | Supported | Unit |
| Locked champion, 1-tap promotion, rollback | 47 | Supported | Supported | Unit |
| Closed daily loop with durable pre-send intent | 48 | Supported | Supported | Unit |
| Dispatch: Colab manual handoff | 49 | Supported | Supported | Unit |
| Dispatch: GitHub workflow | 49 | Supported when a PAT is stored and an endpoint exists | Supported | Unit + mocked HTTP |
| Dispatch: Kaggle kernel push | 49 | **Not configured by default** (needs a multipart-capable endpoint) | Owner-supplied endpoint | Unit |
| Daily Lab UI card | 50 | Supported | Supported | Presenter unit-tested; UI review only |
| Notebook telemetry emission & diff application | 51 | Supported | Supported | Python unit |

## 4. Unchanged exclusions

No unattended promotion, no self-grading, no fabricated live runs, no credential in source or logs, no
submission-cap changes from a candidate, no APK distribution claim until the Gradle build is executed
in an SDK-enabled environment.

