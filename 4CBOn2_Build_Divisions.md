# 4CBOn2 — Build Plan

**Active build · Revised 31 October 2026 · Batches 1–3 completed (`01–34`, `36`, `09a`); 32 JUnit + 5 Python notebook tests passing, `4CBOn2_Kaggle6.ipynb` created, and fresh v2-signed `4CBOn2-debug.apk` built; Batch 4 (`35`, `37–42`) next**

## Decision

Build the expanded first prototype across **42 modular divisions**, retaining the earlier Android and notebook work and covering the twelve phone-control work packages in dependency order.

The product supports selected, authorized app actions—not universal access to every app. Follow `4CBOn2_Phone_Control_Design_v4.md` for distribution, permission, and takeover architecture. The notebook target is `4CBOn2_Kaggle6.ipynb` in `mohamtur1/4CBOn2` (created directly in Division 29).

Divisions are **functional milestones, not timeboxes**. Implement and verify multiple divisions per session without artificial 30-minute caps, run builds and test suites to completion, and ship working code rather than splitting unfinished tasks into `a/b` continuation slices.

## Execution rules

- **Batch and parallelize divisions:** Complete multiple related divisions in a single continuous pass whenever their technical dependencies are satisfied.
- **Run builds and tests to completion:** Do not impose session timers or abort builds, SDK tasks, or test runs near an arbitrary clock limit. Track long-running remote jobs asynchronously by reference rather than waiting idly or duplicating requests.
- **Never stall on external credentials or hardware:** Missing live tokens, OAuth approvals, cloud runtimes, or physical devices do not block development. Build against clean interfaces and local mocks/fixtures immediately; run live checks when credentials or hardware are connected.
- **Keep artifacts updated continuously:** Extend the existing codebase directly and refresh the debug APK after meaningful batches rather than deferring packaging to the end.
- **Ship code, not checkpoint paperwork:** Treat compiling source and automated tests as the primary deliverables. Update the status table in this file per batch instead of writing separate per-division report documents.
- **Maintain core safety boundaries:** Keep real external writes/submissions gated on explicit owner confirmation, enforce immediate local Take Over/Stop, and never place credentials in source code, logs, or documentation.

## The 42 build divisions

| No. | Division | Deliverable / verification target |
| --- | --- | --- |
| 01 | Freeze expanded prototype scope | Supported profiles, distribution model, exclusions, dependencies, and acceptance criteria |
| 02 | Phone-control feasibility and policy gate | Deployment/device prerequisites and supported OS/store permission boundaries |
| 03 | Prepare Android build tools | Compatible Java/SDK/Gradle versions pinned and local build toolchain provisioned |
| 04 | Create minimal Android project | Compilable Kotlin/Compose project with entry screen |
| 05 | Produce first debug APK | Signed debug APK build, artifact path, and SHA-256 checksum |
| 06 | Add navigation | Home, Ask, Agents, Browser, and Activity destinations; unbuilt controls labeled |
| 07 | Protected credential storage | AndroidKeyStore AES-GCM credential boundary with zero embedded tokens or sensitive logs |
| 08 | Prove GitHub read access | Read-only repository client verified against public/authorized endpoints |
| 09 | Prove Kaggle read access | Read-only competition client and local token-entry storage (`09a`) verified with unit/mock tests |
| 10 | Prove Colab entry/control route | Notebook launch/handoff route and runtime vs. manual capability boundary |
| 11 | Local database | Core local entities with persistence and restart unit tests |
| 12 | Selected-app capability registry | Owner-selected app identities, scoped capability lookup, and unavailable states |
| 13 | On-device scope contract | App/account/data/action/expiry limits; block unselected targets and unauthorized cloud transfer |
| 14 | Takeover and cancellation lease | Immediate local Take Over/Stop state; reject stale commands until explicit resume |
| 15 | Offline agent drafts | Save, edit, and load goals, templates, job profiles, execution caps, and draft state |
| 16 | Safe file import/export | User-selected document import and workspace export via standard scoped storage APIs |
| 17 | Offline knowledge library | Local storage, search, provenance, and timestamps for saved reports and evidence |
| 18 | Small local analysis | Bounded CSV/JSON analysis routines verified with valid and invalid fixtures |
| 19 | Persistent waiting queue | Durable job queue across restarts for deferred network tasks without automatic side-effect replay |
| 20 | Sensitive-action approval gate | Destination/artifact identity checks, expiry, execution caps, and confirmation tests |
| 21 | One harmless app intent/API action | Supported Android intent/API open-or-prepare action with verified handoff/result semantics |
| 22 | GitHub cache and diff | Permitted snapshot refresh, local diff view, and exportable change preparation; writes owner-gated |
| 23 | Kaggle metadata/status | Competition and job status views with last-sync, stale, and auth-error states |
| 24 | Chrome-backed opening | Custom Tabs / service links with standard browser fallback |
| 25 | Controlled browser workspace | Scoped public-page reader workflow with URL boundaries and isolated web context |
| 26 | Consented observation boundary | Structured/shared data first; opt-in screen capture excluding sensitive contexts; cloud upload off by default |
| 27 | Actual action timeline | Event log recording planned, pending, manual, verified, and uncertain outcomes |
| 28 | Shared job bundle | Versioned app/notebook manifest, private export/import, SHA-256 checks, and policy revalidation |
| 29 | Notebook skeleton | Create `4CBOn2_Kaggle6.ipynb` with runtime detection, controller/runner roles, and capability panel |
| 30 | Colab built-in AI adapter | `google.colab.ai` probe and bounded request adapter with local mock support for offline testing |
| 31 | One bounded agent loop | End-to-end goal -> permitted action -> verification workflow with stop and error paths |
| 32 | Critical phone-action guard | Action preview, account checks, protected-screen manual handoff, and no blind irreversible retry |
| 33 | Human-defined routine engine | Deterministic human-triggered scripts with precondition checks and bounded execution |
| 34 | Conditional personal-lab UI runner | Opt-in experimental UI runner restricted to eligible owner-controlled fixture apps |
| 35 | Privacy and prompt-injection tests | Automated fixtures for untrusted page/message injection, unauthorized destinations, and secret-leak prevention |
| 36 | Online/offline phone-control routing | Dynamic routing across available local, intent, API, and cloud paths with graceful offline fallback |
| 37 | Kaggle prepare-only pipeline | Trusted tabular baseline template with schema validation, artifact checks, and reproducible output |
| 38 | Submission gate and receipt states | Submission caps, durable intent log, receipt/uncertainty handling, mocked tests, and owner-gated live submit |
| 39 | Minimal Improvement Lab | Baseline vs. candidate comparison report, version retention, fixed evaluator, and approved promotion |
| 40 | Harmless cross-app integration test | Multi-workflow integration suite with synthetic data covering scope limits, takeover, and manual handoffs |
| 41 | Focused recovery/safety smoke tests | Fault tests for offline/reconnect, stale responses, interrupted jobs, revoked permissions, and deduplication |
| 42 | Package prototype and support matrix | Updated debug APK and `4CBOn2_Kaggle6.ipynb` bundle with checksums, setup guide, and verified capability matrix |

## Current build status

| Division | Work status | Verification | Key artifact / path |
| --- | --- | --- | --- |
| 01 — Freeze expanded prototype scope | **Finished** | Static | `android/PROTOTYPE_SCOPE.md` |
| 02 — Phone-control feasibility and policy gate | **Finished** | Static | `android/PHONE_CONTROL_FEASIBILITY.md` |
| 03 / 03a — Prepare Android build tools | **Finished** | Build/Toolchain | `android/BUILD_TOOLCHAIN.md`, `android/TOOLCHAIN_PROVISIONING.md` |
| 04 — Create minimal Android project | **Finished** | Build/APK | `android/PROJECT_FOUNDATION.md` |
| 05 — Produce first debug APK | **Finished** | Build/APK | `4CBOn2-debug.apk` |
| 06 — Add navigation | **Finished** | Unit + Build/APK | `android/NAVIGATION_FOUNDATION.md` |
| 07 — Protected credential storage | **Finished** | Unit + Build/APK | `android/CREDENTIAL_STORAGE.md` |
| 08 — Prove GitHub read access | **Finished** | Unit + Live public GET | `android/GITHUB_READ_ACCESS.md` |
| 09 — Prove Kaggle read access | **Finished** | Unit + Build/APK (Bearer + Basic auth routes) | `KaggleCompetitionReadClient.kt` |
| 09a — Local Kaggle token UI | **Finished** | Unit + Build/APK | `CredentialEntryController.kt`, `MainActivity.kt` |
| 10 / 10a — Prove Colab entry/control route | **Finished (handoff)** | Unit + Build/APK | `android/COLAB_ENTRY_ROUTE.md` |
| 11 — Local database | **Finished** | Unit (restart + corruption) + Build/APK | `LocalDatabase.kt` |
| 12 — Selected-app capability registry | **Finished** | Unit + Build/APK | `PhoneControlRegistryAndScope.kt` |
| 13 — On-device scope contract | **Finished** | Unit + Build/APK | `PhoneControlRegistryAndScope.kt` |
| 14 — Takeover and cancellation lease | **Finished** | Unit + Build/APK | `TakeoverAndCriticalGuards.kt`, `MainActivity.kt` |
| 15 — Offline agent drafts | **Finished** | Unit + Build/APK | `AgentDraftRepository.kt`, `MainActivity.kt` |
| 16 — Safe file import/export | **Finished** | Unit (SHA-256 + cap + SAF) + Build/APK | `ScopedWorkspaceFileManager.kt` |
| 17 — Offline knowledge library | **Finished** | Unit + Build/APK | `OfflineKnowledgeLibrary.kt`, `MainActivity.kt` |
| 18 — Small local analysis | **Finished** | Unit (valid/invalid CSV & JSON) + Build/APK | `LocalDataAnalyzer.kt` |
| 19 — Persistent waiting queue | **Finished** | Unit (restart + no auto-replay) + Build/APK | `PersistentWaitingQueue.kt` |
| 20 — Sensitive-action approval gate | **Finished** | Unit + Build/APK | `TakeoverAndCriticalGuards.kt` |
| 21 — One harmless app intent/API action | **Finished** | Unit + Build/APK | `ActionTimelineAndIntents.kt` |
| 22 — GitHub cache and diff | **Finished** | Unit (LCS diff + export patch) + Build/APK | `ServiceCacheAndStatus.kt` |
| 23 — Kaggle metadata/status | **Finished** | Unit (sync/stale/auth states) + Build/APK | `ServiceCacheAndStatus.kt` |
| 24 — Chrome-backed opening | **Finished** | Unit + Build/APK | `BrowserSurfaces.kt`, `MainActivity.kt` |
| 25 — Controlled browser workspace | **Finished** | Unit (URL/redirect/JS isolation) + Build/APK | `BrowserSurfaces.kt`, `MainActivity.kt` |
| 26 — Consented observation boundary | **Finished** | Unit (redaction + sensitive block) + Build/APK | `TakeoverAndCriticalGuards.kt` |
| 27 — Actual action timeline | **Finished** | Unit + Build/APK | `ActionTimelineAndIntents.kt`, `MainActivity.kt` |
| 28 — Shared job bundle | **Finished** | JUnit + Python cross-verified | `SharedJobBundleAndColabBridge.kt`, `4CBOn2_Kaggle6.ipynb` |
| 29 — Notebook skeleton | **Finished** | Python nbformat + runtime tests | `4CBOn2_Kaggle6.ipynb` |
| 30 — Colab built-in AI adapter | **Finished** | Mocked `google.colab.ai` + Android receipt tests | `4CBOn2_Kaggle6.ipynb`, `SharedJobBundleAndColabBridge.kt` |
| 31 — One bounded agent loop | **Finished** | JUnit + Python loop/stop/error tests | `AgentLoopRoutinesAndRouter.kt`, `4CBOn2_Kaggle6.ipynb` |
| 32 — Critical phone-action guard | **Finished** | Unit (manual handoff + no blind retry) + Build/APK | `TakeoverAndCriticalGuards.kt` |
| 33 — Human-defined routine engine | **Finished** | Unit (rejects AI trigger + locked device) + Build/APK | `AgentLoopRoutinesAndRouter.kt` |
| 34 — Conditional personal-lab UI runner | **Finished** | Unit (disabled in Standard; fixture-only in Lab) | `AgentLoopRoutinesAndRouter.kt` |
| 35 — Privacy and prompt-injection tests | **Finished** | Unit (hostile fixtures + exfiltration blocks) + Python | `PrivacyAndPromptInjectionGuard.kt`, `4CBOn2_Kaggle6.ipynb` |
| 36 — Online/offline phone-control routing | **Finished** | Unit (no fake cloud / lock bypass / alt provider) | `AgentLoopRoutinesAndRouter.kt` |
| 37 — Kaggle prepare-only pipeline | **Finished** | Unit (seeded k-fold + schema checks) + Python | `KagglePipelineGateAndImprovementLab.kt`, `4CBOn2_Kaggle6.ipynb` |
| 38 — Submission gate and receipt states | **Finished** | Unit (caps + durable ledger + timeout lock) + Python | `KagglePipelineGateAndImprovementLab.kt`, `4CBOn2_Kaggle6.ipynb` |
| 39 — Minimal Improvement Lab | **Finished** | Unit (sealed evaluator + owner gate + rollback) + Python | `KagglePipelineGateAndImprovementLab.kt`, `4CBOn2_Kaggle6.ipynb` |
| 40 — Harmless cross-app integration test | **Finished** | Integration (reader + loop + scope + takeover + guard) | `Batch4PipelinesLabAndSecurityTest.kt` |
| 41 — Focused recovery/safety smoke tests | **Finished** | Fault injection (reconnect + stale lease + restart + dedup) | `Batch4PipelinesLabAndSecurityTest.kt` |
| 42 — Package prototype and support matrix | **Finished** | Build/APK (v2 signed) + Notebook + Checksums | `PROTOTYPE_PACKAGE_AND_SUPPORT_MATRIX.md`, `4CBOn2-debug.apk`, `4CBOn2_Kaggle6.ipynb` |

All **42 divisions (`01–42`, plus `09a`)** are complete and verified: **38 Android JUnit4 tests (`0` failures)** across 15 suites + **7 Python notebook tests (`0` failures)**, producing `/home/user/4CBOn2_Kaggle6.ipynb` (`a7d355670ef12503deb5988998855621697d32ce0c01e7542703e873e220cefe`), v2-signed `/home/user/4CBOn2-debug.apk` (`2667bacdb98be8213dd97897e09d8cc1fe17bedefbd221428759d357b063f93e`), and `/home/user/PROTOTYPE_PACKAGE_AND_SUPPORT_MATRIX.md`.

## Dependencies and execution batches

- **Parallel progress over auth/device waits:** Local Android databases, UI screens, scope/takeover engines, browser/intent workflows, and `4CBOn2_Kaggle6.ipynb` never wait on live credentials or physical device tests.
- **Recommended implementation batches:**
  1. **Local state & credential UI:** Divisions `09a`, `11`, `15`, `16`, `17`, `18`, `19`
  2. **Scope, takeover & action guards:** Divisions `12`, `13`, `14`, `20`, `26`, `27`, `32`
  3. **Intents, service views & browser:** Divisions `21`, `22`, `23`, `24`, `25`
  4. **Shared bundle, Colab notebook & agent loop:** Divisions `28`, `29`, `30`, `31`, `33`, `34`, `36`
  5. **Pipelines, Improvement Lab, verification & packaging:** Divisions `35`, `37`, `38`, `39`, `40`, `41`, `42`
- **Hard technical ordering:**
  - Complete Take Over/Stop (`14`) before enabling automated interactive actions (`21`, `31`, `33`, `34`).
  - Complete the shared job bundle (`28`) and notebook skeleton (`29`) before the integrated cloud agent loop (`31`).
  - Keep banking, authentication, payment, lock-screen, and OS-permission screens manual-only; run UI automation tests against synthetic fixtures (`34`, `40`).
  - Require explicit owner confirmation before live repository writes (`22`) or live Kaggle submissions (`38`).

## Progress tracking

Update the status table per completed batch using two concise dimensions:

1. **Work status:** `Ready`, `In progress`, `Blocked (external)`, or `Finished`.
2. **Verification:** `Static`, `Unit/Mocked`, `Build/APK`, `Emulator`, `Device`, or `Live service`.

Label verification accurately by the highest tier actually executed, and omit per-division prose reports, elapsed-time logs, and continuation boilerplate.

## Out of scope

- Universal autonomous control of every installed app or website.
- Control of protected credential, payment, lock-screen, or system-consent workflows.
- Root access, hidden input, bypassed security settings, or self-granted permissions.
- A mandatory on-device LLM or full offline reasoning on unverified hardware.
- An always-on backend or guaranteed Colab runtime availability.
- A general arbitrary-code sandbox or unrestricted remote browser service.
- Every Kaggle competition format beyond the supported tabular baseline.
- Production penetration testing, Play Store certification, and production signing.

## Build readiness

**Cleared for continuous, multi-division execution.** Advance directly through `09a` and `11–42` in batches, verify with automated tests and updated debug APKs, and defer only live credential or hardware checks that require direct owner interaction.
