# 4CBOn2 — New-Thread Engineering Handoff (v0.2 Complete -> v0.3 Next-Gen Loop)

**Prepared:** 2 November 2026  
**Purpose:** Cleanly continue `4CBOn2` Android + Notebook Companion development in a fresh Arena thread without losing any code, architecture rules, tests, or live ARC-AGI-3 benchmark findings.

---

## 1. Security & Architectural Invariants (Carry Forward Unchanged)

- **Never solicit, paste, store, or echo raw credentials/PATs/API keys in chat, source files, logs, or notebooks.**
- Authenticated access on Android uses `AndroidKeyStoreCredentialStore` (`AES/GCM/NoPadding`, locally entered on-device only).
- **Owner-gated autonomy:** Preserve pause/takeover/stop controls, human-approved promotion (`Division 39`), submission caps (`Division 38`), and explicit scope boundaries (`4CBOn2_Phone_Control_Design_v4.md`).
- Keep explaining major milestone completions **both like the user is two years old AND as an adult** when building divisions.

---

## 2. Verified v0.2 Baseline Completed (`Divisions 01–42`, `09a`)

All 42 divisions of Prototype `v0.2` are implemented and verified:

| Artifact | Path in Handoff Zip | Verification Status |
| :--- | :--- | :--- |
| **Android Companion Source (`01–42`)** | `android/` | `:app:testDebugUnitTest` (`38/38` JUnit4 tests passing) + `:app:assembleDebug` (`4CBOn2-debug.apk`, v2-signed, SHA-256 `2667bacdb98be8213dd97897e09d8cc1fe17bedefbd221428759d357b063f93e`) |
| **Canonical `Kaggle6` Notebook** | `4CBOn2_Kaggle6.ipynb` + `scripts/build_notebook.py` | `8/8` Python `unittest` tests (`tests/test_kaggle6_notebook.py`), pure-Python cells, includes `google.colab.ai` bounded probe, tabular Improvement Lab, and ARC-AGI-3 `State-Graph Frontier Explorer` (`0.10` Kaggle Public LB, Version `364758641`) |
| **Guarded Multimodal REPL Notebook** | `4CBOn2_Guarded_REPL_ARC_AGI_3_Solution.ipynb` | 23 cells, AST-verified; adds 6 `4CBOn2` runtime transition & token-pace invariants atop the `25.83` Champion baseline (`354898739`) |
| **ARC Prize 2026 Paper Track Package** | `ARC_Prize_2026_Paper_Track_Writeup.md` + `.png` figures | `1,302` words (`<= 1,500` verified), `560x280` Card + `1600x900` Cover diagrams |
| **Architecture Specs** | `AgentLab_Blueprint.md`, `4CBOn2_Android_Design_v3.md`, `4CBOn2_Phone_Control_Design_v4.md`, `4CBOn2_Kaggle6_Design.md`, `4CBOn2_Build_Divisions.md`, `PROTOTYPE_PACKAGE_AND_SUPPORT_MATRIX.md` | Authoritative design & scope contracts |

---

## 3. Core Gap Identified During Live ARC-AGI-3 Campaign (Target for `v0.3`)

During live Kaggle ARC-AGI-3 deployment (`2 November 2026`), the owner asked the key architectural question:
> *"4CBOn2 app was supposed to think and get better at this everyday by itself — Right?"*

### Why `v0.2` Stopped Short of Daily Self-Improving Iteration:
1. **Tabular-only local evaluator (`Division 37`):** `v0.2`'s Improvement Lab only evaluates local `.csv` tabular metrics, whereas interactive agent benchmarks (like ARC-AGI-3) evaluate code patches, prompt/guard flags, and per-game diagnostic JSON/HTML (`score`, `levels`, `actions`, `tokens`).
2. **Manual-only notebook handoff (`Division 10`):** Android `v0.2` opens Colab/Kaggle URLs manually in Custom Tabs; it does not yet pull back structured run telemetry (`diagnostics.json` / Kaggle kernel status / GitHub Actions or Kaggle API kernel push-and-poll) to automatically propose the *next* bounded candidate diff for owner `1-tap Approve & Run`.
3. **No closed-loop "Daily Experiment Cycle" UX:** The user wants `4CBOn2` to genuinely act as an **everyday research & competition lab**:
   - **Ingest latest run diagnostics** (e.g., detecting `r11l` burned `96,609` tokens for `53` actions, or `v4` regressed `25.83 -> 25.15`),
   - **Synthesize a bounded Candidate Diff (`v_next`)** (config flags, Python helper skill, or strategy mutation) with an explicit hypothesis,
   - **Dispatch / schedule evaluation** via legitimate user-authorized Kaggle API / GitHub workflow / Colab runner,
   - **Gate promotion automatically** against the locked Champion baseline (`Promote` only when `candidate_score > champion_score`, with 1-tap owner sign-off).

---

## 4. Repeatable Android Toolchain Bootstrap (For Fresh Sandbox Threads)

Because Arena workspace snapshots exclude `.cache` / SDK binaries, a new thread can restore compilation & unit tests in ~90 seconds if needed:
1. Check `java -version` (JDK 17/21) and install command-line Android SDK (`platforms;android-35`, `build-tools;35.0.0`) under `/tmp/android-sdk` with `ANDROID_HOME=/tmp/android-sdk`.
2. Run `cd /home/user/android && ./gradlew testDebugUnitTest assembleDebug` to verify all `38/38` existing tests pass before starting `v0.3` divisions.

---

## 5. v0.3 Progress — Daily Experiment Cycle (added 2 November 2026)

**Package:** `v0.3.0-div50` · **Branch of record:** `arena/f4b22ec8-4cbon2`
**Verification:** 64/64 Kotlin tests (pure JVM) and 10/10 Python notebook tests pass.
**APK:** not rebuilt — this environment cannot reach `dl.google.com` for the Android SDK (see §6).

### What the gap in §3 now looks like

| §3 gap | v0.3 division | Where it lives |
| --- | --- | --- |
| Tabular-only local evaluator | 43, 44 | `RunTelemetry.kt`, `BenchmarkEvaluators.kt` |
| No telemetry pull-back | 43 | `RunTelemetryIngest` (diagnostics JSON, kernel status, manual) |
| No closed-loop daily cycle | 45–49 | `RunDiagnoser.kt`, `CandidateDiffSynthesizer.kt`, `ChampionLedgerAndPromotionGate.kt`, `DailyExperimentCycle.kt`, `ExperimentDispatch.kt` |
| No Daily Lab UI | 50 | `DailyLabPresenter.kt` + the "Daily Lab" card in `MainActivity.kt` |
| Notebook could not emit or receive the loop's artifacts | 51 | Division 51 cell in `4CBOn2_Kaggle6.ipynb` / `scripts/build_notebook.py` |

### The five rules that define v0.3 behaviour

1. **Nothing is invented.** Missing score/actions/tokens, an unfinished kernel, or a run smaller than
   the significance floor are rejected or marked inconclusive — never defaulted.
2. **The candidate cannot grade itself.** The evaluator set is compiled in; diffs cannot install,
   replace, or re-tune an evaluator.
3. **One change per experiment.** A diff touching more than one allowlisted key is refused, because a
   mixed change cannot be attributed to a cause.
4. **Durability before danger.** The dispatch intent is persisted before the transport call with an
   idempotency key; ambiguous outcomes must be reconciled, never blindly retried.
5. **Stop wins.** Every mutating cycle step re-reads the control lease; takeover/Stop blocks dispatch
   before any external call.

### How to verify in a fresh thread

```bash
# Always works, even without the Android SDK:
scripts/verify_pure_kotlin.sh                    # 64/64 Kotlin tests
python3 scripts/build_notebook.py
python3 -m unittest discover -s tests -p "test_*.py" -v   # 10/10 Python tests

# When dl.google.com is reachable (finishes Division 52):
source scripts/ensure_toolchain.sh
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

### What is still open

- **Division 52**: rebuild and sign the debug APK with Gradle; record its SHA-256 in
  `PROTOTYPE_PACKAGE_AND_SUPPORT_MATRIX.md`; compile-verify the Compose Daily Lab card.
- **Kaggle kernel dispatch**: `DispatchEndpoints.DEFAULT_KAGGLE_KERNEL_ENDPOINT` is intentionally empty
  because Kaggle's push API needs a multipart notebook body the app does not build. Supply an endpoint
  that matches your own runner (or a GitHub workflow) and the card will enable `Approve & Run`.
- **First live loop**: lock a champion from a real ARC-AGI-3 run, then run one cycle end to end and
  compare its diagnosis against what you would have concluded by hand.
