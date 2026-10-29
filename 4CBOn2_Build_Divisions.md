# 4CBOn2 — Timeboxed Build Plan

**Build in progress · Updated 29 October 2026 · Divisions 01–13 are complete for their documented source/build scope; Division 13 — On-device scope contract passed no-artifact CI with 55 tests; Divisions 14–42 have not started. Division 09c’s owner smoke test and final wording correction are recorded with their stated limits. Historical 09/09b/10/10a details remain below; active source has no Kaggle API/token/OAuth or app-side account session, the fixed Colab notebook target is missing, and browser-specific/unreported 09c subchecks remain unverified.**

## Decision

Use **42 small build divisions for the expanded first prototype**. This retains the earlier Android/notebook work and adds twelve phone-control work packages. The divisions below are renumbered in dependency order and supersede the earlier 30-division numbering.

The product supports selected, authorized app actions—not universal access to every app. Follow `4CBOn2_Phone_Control_Design_v4.md` for the distribution, device, permission, and takeover gates. The notebook target remains `4CBOn2_Kaggle6.ipynb` in `mohamtur1/4CBOn2`.

Every division is a **maximum 30-minute work session, including waiting, verification, and saving progress**. This is a session limit, not a guarantee that a dependency download, account approval, first compile, complete feature, or device test finishes in 30 minutes.

If unfinished, stop safely, checkpoint the exact state, and create a small continuation, such as 10a/10b. Do not run longer or label incomplete/unverified work complete. Forty-two divisions are an initial decomposition; discoveries can require additional timeboxed subdivisions. This is not a promise to complete universal phone control in 21 hours.

## Session contract

- Up to **20 minutes** implementation, investigation, or necessary waiting.
- Up to **7 minutes** targeted verification.
- Reserve **3 minutes** for a recoverable checkpoint and progress report.
- Finish earlier when the narrow target is verified.
- Do not start a long operation near the cap. Bound/cancel local work when safe; record an already accepted remote job as pending instead of waiting indefinitely or repeating it.
- Retain the last verified version. Extend the same application incrementally, one division at a time.
- Owner permission for real external effects remains a separate gate. No credentials in source code, logs, or design documents.

## The 42 build divisions

| No. | Division | Narrow deliverable / verification target |
| --- | --- | --- |
| 01 | Freeze expanded prototype scope | Record supported profiles, distribution assumptions, exclusions, dependencies, and acceptance checks |
| 02 | Phone-control feasibility and policy gate | Review deployment/device prerequisites; record unverified phone capabilities and prohibit policy/security workarounds |
| 03 | Prepare Android build tools | Inventory Java/SDK/build tooling, pin compatible versions, and record missing downloads or blockers |
| 04 | Create minimal Android project | A Kotlin/Compose project with a compilable entry screen |
| 05 | Produce first debug APK | Development build/signing result, artifact path, and checksum; installation/device verification recorded separately |
| 06 | Add navigation | Home, Ask, Agents, Browser, Activity; disabled/unimplemented features labeled |
| 07 | Protected credential storage | Secure native settings and credential boundary; no embedded tokens or sensitive logs |
| 08 | Prove GitHub read access | Authorized repository read or an honest account/network blocker |
| 09 | Manual Kaggle competition handoff | Validated competition URL and explicit browser handoff; owner handles sign-in, rules, joining, and submission; no Kaggle API/token/OAuth |
| 10 | Prove Colab entry/control route | Notebook opening plus supported control-path investigation; manual-only versus actual API capability recorded |
| 11 | Local database | Initial entities and persistence/restart test |
| 12 | Selected-app capability registry | Owner-selected app identities, narrow discovery, action-level support, and unavailable states; no blanket inventory collection |
| 13 | On-device scope contract | App/account/data/action/expiry limits; deny unselected targets and unauthorized cloud transfer |
| 14 | Takeover and cancellation lease | Local Take Over/Stop state; reject stale commands and require explicit resume |
| 15 | Offline agent drafts | Save/edit/load goal, template, job profile, caps, and draft state |
| 16 | Safe file import/export | User-selected documents and workspace export without broad phone filesystem access |
| 17 | Offline knowledge library | Read/search saved reports and evidence with provenance and update times |
| 18 | Small local analysis | One bounded CSV/JSON routine with valid/invalid fixtures |
| 19 | Persistent waiting queue | Save jobs, recover after restart, defer network tasks; no automatic consequential replay |
| 20 | Sensitive-action approval gate | Scoped destination/artifact identity, expiry, caps, and confirmation tests |
| 21 | One harmless app intent/API action | A supported open/prepare action with actual result/handoff semantics; no unrestricted UI input |
| 22 | GitHub cache and diff | Refresh a permitted version, show local diff, and prepare an exportable change; no unapproved write/force-push |
| 23 | Manual Kaggle competition tracker | Owner-entered competition status and owner-update timestamps; no API sync or live-status claim |
| 24 | Chrome-backed opening | Custom Tab/service links and browser fallback; no unrestricted Chrome-control claim |
| 25 | Controlled browser workspace | One reviewed public-page/reader workflow, URL boundaries, no privileged native bridge to untrusted content |
| 26 | Consented observation boundary | Shared/structured data first; any screen capture separately gated, secure/sensitive contexts excluded, cloud upload Off by default |
| 27 | Actual action timeline | Record plan/action/result/check events; distinguish attempted, pending, manual, verified, and uncertain outcomes |
| 28 | Shared job bundle | Versioned app/notebook contract, manifest, private export/import, hashes, and policy revalidation; a hash is not authority |
| 29 | Notebook skeleton | Exact file `4CBOn2_Kaggle6.ipynb`, runtime detection, controller/runner roles, capability panel, and manual upload/run handoff; no Kaggle API/runtime-control claim |
| 30 | Colab built-in AI adapter | Real service/model probe and bounded request; no provider fallback or proxy-credential export |
| 31 | One bounded agent loop | One supported goal -> permitted action -> verification workflow; stop/error path demonstrated |
| 32 | Critical phone-action guard | Sensitive/manual-only contexts, exact action preview, account checks, protected-screen handoff, and no blind irreversible retry |
| 33 | Human-defined routine engine | Fixed script, explicit human trigger, known preconditions, and bounded execution; not an exemption for AI-driven Accessibility use |
| 34 | Conditional personal-lab UI runner | Only a permitted, authorized experimental configuration against an owner-controlled fixture app; unavailable if eligibility fails; not Play-compliance proof |
| 35 | Privacy and prompt-injection tests | Malicious page/message, unauthorized destination, secret/screen-content leakage, and data-sharing scope fixtures |
| 36 | Online/offline phone-control routing | Available local/API/model routes only; no fake cloud planning, locked-device bypass, or silent alternate provider |
| 37 | Offline competition preparation pipeline | Owner-selected local tabular template, explicit validation, artifact checks, and reproducible result; no API download/upload |
| 38 | Manual submission gate and receipt states | Limits, owner-performed Kaggle submission, owner-recorded acknowledgment/uncertainty, and mocked tests; no live app submission or receipt verification |
| 39 | Minimal Improvement Lab | One baseline/candidate report, preserved versions, fixed evaluator, approval before promotion |
| 40 | Harmless cross-app integration test | A few selected supported workflows with synthetic data; scope escape, takeover, and manual handoff tested |
| 41 | Focused recovery/safety smoke tests | Offline/reconnect, stale cloud response, interrupted work, revoked permission, ambiguous acknowledgment, and duplicate-action fixtures |
| 42 | Package prototype and support matrix | Rebuild eligible debug artifact(s) and notebook bundle; checksums, instructions, actual test results, blocked capabilities, and distribution limitations |

## Current build status

| Division | Work status | Verification | Checkpoint |
| --- | --- | --- | --- |
| 01 — Freeze expanded prototype scope | **Finished** | Static design/consistency check | `android/PROTOTYPE_SCOPE.md` |
| 02 — Phone-control feasibility and policy gate | **Finished** | Static policy/architecture consistency check; no device or store test | `android/PHONE_CONTROL_FEASIBILITY.md` |
| 03 — Prepare Android build tools | **Finished** | Local inventory, compatibility baseline, and verified workspace-local provision | `android/BUILD_TOOLCHAIN.md` + `android/TOOLCHAIN_PROVISIONING.md` |
| 03a — Provision workspace-local Android toolchain | **Finished** | JDK/Gradle integrity checks; Android SDK artifact and command checks | `android/TOOLCHAIN_PROVISIONING.md` |
| 04 — Create minimal Android project | **Finished** | Wrapper, Kotlin compile, debug package, signing, and manifest checks passed in sandbox; no device test | `android/PROJECT_FOUNDATION.md` |
| 05 — Produce first debug APK | **Finished** | Clean rebuild, SHA-256, alignment, v2 debug signature, and APK manifest/package checks passed; no installation test | `android/DEBUG_APK_RELEASE.md` + `4CBOn2-debug.apk` |
| 06 — Add navigation | **Finished** | Five destinations, disabled Ask/How-do-I controls, one unit test, and sandbox build/signature checks passed; no device test | `android/NAVIGATION_FOUNDATION.md` |
| 07 — Protected credential storage | **Finished** | Keystore/AES-GCM boundary compiled; four unit tests and static/build/signature checks passed; no secret/device-keystore/live-service test | `android/CREDENTIAL_STORAGE.md` |
| 08 — Prove GitHub read access | **Finished (public route)** | HTTPS public metadata/root listing returned HTTP 200 from build environment; 9 unit/static/build/signature checks passed; no PAT, authenticated app, or device test | `android/GITHUB_READ_ACCESS.md` |
| 09 — Manual Kaggle competition handoff | **Finished (manual source/build route)** | Active API/token/OAuth code removed; validated URL and explicit `ACTION_VIEW` handoff added; static checks and 21 CI unit tests passed; no browser/device/competition test | `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md` |
| 09a — Former local Kaggle token route | **Superseded; no further work** | Owner selected manual participation; token/API source and tests removed, historical evidence retained | `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md` |
| 09b — Verify revised manual Kaggle handoff | **Finished** | Approved CI run 40228035751 passed compilation, 21 tests, fresh debug APK assembly, zip alignment, v2 signature, SHA-256, and clean-source checks; no retained artifact or device/competition test | `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md` + `android/DIV09B_TOOLCHAIN_CHECKPOINT.md` (historical local-environment evidence) |
| 09c — Owner external-handoff smoke test | **Finished for recorded scope** | Installed Kaggle app opened the correct page before sign-in; owner later signed in personally and performed Cancel/Back; final wording correction passed CI run 40268393469; no join/submission or credential sharing. Browser-specific and unreported subchecks remain unverified | `4CBOn2_Next_Thread_Handoff.md` |
| 10 — Prove Colab entry/control route | **Finished (manual route)** | Fixed manual-handoff reference and public route investigation complete; target notebook reported missing; compile, aggregate 15-test suite, debug build, v2 signature, and static checks passed; no account/runtime/AI/device test | `android/COLAB_ENTRY_ROUTE.md` |
| 10a — Restore/verify Division 10 toolchain checkpoint | **Finished** | Toolchain restored from verified sources; compile/test/APK verification completed with bounded one-worker memory configuration | `android/COLAB_ENTRY_ROUTE.md` |
| 11 — Local database | **Finished for source/build scope** | Approved no-artifact CI run 40316111676 at `eef1a18` passed compilation, 25 tests with zero failures/errors/skips, fresh debug APK assembly, zip alignment, v2 signature, SHA-256, and clean-source checks; artifact count zero. Synthetic file-backed close/reopen proven on JVM; no device restart test | `4CBOn2_Division11_Verification.md` + `android/LOCAL_DATABASE.md` |
| 12 — Selected-app capability registry | **Finished for source/build scope** | Approved no-artifact CI run 40812912554 at `75564a8` passed compilation, 40 tests with zero failures/errors/skips, fresh debug APK assembly, zip alignment, v2 signature, SHA-256, and clean-source checks; artifact count zero. Owner-selected apps, action-level routes, fail-closed decisions, and narrow non-enumerating discovery; no installed-app inventory, UI, persistence, or device test | `4CBOn2_Division12_Verification.md` + `android/SELECTED_APP_CAPABILITIES.md` |
| 13 — On-device scope contract | **Finished for source/build scope** | Approved no-artifact CI run 40831655312 at `4d18e2b` passed compilation, 55 tests with zero failures/errors/skips, fresh debug APK assembly, zip alignment, v2 signature, SHA-256, and clean-source checks; artifact count zero. Bounded per-job scope with hard caps, opt-in cloud transfer, derived expiry, exclusions, and pre-discovery refusals; no UI, persistence, real data, network, or device test | `4CBOn2_Division13_Verification.md` + `android/SCOPE_CONTRACT.md` |
| 14–42 | **Not started** | Not tested | None |

Divisions 01–02 are foundation documentation checkpoints; Division 03/03a provisioned the active sandbox toolchain; Division 04 created and locally compiled the source skeleton; Division 05 retained and verified a debug-signed development APK; Division 06 added locally verified navigation source; Division 07 added a compiled, unpopulated credential-storage boundary; Division 08 added a read-only GitHub boundary and verified public repository visibility from the build environment; Division 09 originally established and then superseded an authorization-blocked Kaggle read boundary; Division 10 added and locally verified a fixed manual Colab handoff reference, while the target notebook itself remains missing and runtime/AI capability remains untested. These results do **not** establish phone compatibility, authenticated GitHub/Kaggle access, a Colab account/notebook/runtime or built-in-AI call, special permissions beyond the normal Internet declaration, a real device test, store eligibility, credential entry/storage on a device, production signing, or production release readiness. The retained Division 05 APK predates the Division 06–10 source updates. No later division is implied to exist merely because it is listed.

Revised Division 09 removed the active Kaggle API/token/OAuth route and added a validated manual competition URL handoff with ten new source tests. Revised Division 09b then used separately approved GitHub workflow pushes and completed run [40228035751](https://github.com/mohamtur1/4CBOn2/actions/runs/40228035751) at commit `d1be9aa`: compilation and all 21 tests passed, a fresh debug APK was assembled, zip alignment and v2 signature checks passed, and its SHA-256 was `02eb96bebda5563bc00f55e35af7356d142e9b96185b896f877110dcc6b14264`. The ephemeral CI artifact was not uploaded or retained. Former Division 09a requires no further implementation. Revised 09/09b are closed for source/build verification. During Division 09c, the installed Kaggle app handled the explicit `ACTION_VIEW` request and showed the correct competition page before sign-in; the owner then signed in personally and performed Cancel/Back. No credential was shared, and no competition join or submission occurred. Browser-specific handling and the empty/invalid/no-automatic-launch device subchecks were not separately verified. The wording-only correction must pass CI before Division 11 — Local database starts.

Division 11 added a versioned, dependency-free, file-backed `LocalEntityDatabase` with two initial entity types (`LocalAgentDraft` and `LocalActionEvent`) and four synthetic JUnit tests. The active suite rose from 21 to 25 tests, and the CI workflow’s source-inventory guard, step label, notice, and XML aggregation assertion were all updated to exactly 25. CI run [40316111676](https://github.com/mohamtur1/4CBOn2/actions/runs/40316111676) passed at commit `eef1a18d671db2747ea5d4ca7c739e696079377d`, including debug APK assembly, zip alignment, APK Signature Scheme v2, SHA-256 `471efe77c87f83088796a7710295e353e5cf10f8e9a6a735c01be46d1a676b3a`, and a clean-source check. The artifact-retention step was skipped and the artifact count was zero. A local standalone JVM harness also proved a real temporary-file close/reopen, but no emulator, real-device, production-signing, or live-service claim is made. Division 12 has not started.

Division 12 added a dependency-free, owner-selected capability registry. `SelectedApp` records only a package name, display label, and owner selection time; `AppActionCapability` declares action-level routes; `ActionRoute` fixes the least-to-most-powerful order (`AUTHORIZED_API` → `APP_INTENT` → `APP_LINK_OR_SHARE` → `FIXED_ROUTINE` → `MANUAL_HANDOFF`). `SelectedAppCapabilityRegistry` resolves the earliest declared **and** reachable route, returns explicit unavailable reasons (not selected, owner-disabled, action not declared, no route reachable), never probes a paused app, and treats a failing probe as unreachable. Sensitive actions are `MANUAL_ONLY` at the type level and may declare only `MANUAL_HANDOFF`. Fifteen synthetic JVM tests were added; the active suite is 40 (25 preserved + 15 new), and the CI inventory guard, step label, notice, and exact XML assertion are aligned to 40. No installed-app enumeration, accessibility service, screen capture, persistence, UI wiring, credential, or live-service call was added. No-artifact CI run [40812912554](https://github.com/mohamtur1/4CBOn2/actions/runs/40812912554) passed at commit `75564a83ad2f4e2ada66a87ffc686cb81f0e96bd` with 40 passed, 0 failures/errors/skips, a fresh debug APK, zip alignment, v2 signature, SHA-256 `3f2597d7413b3b805436189f06e72ce5432d263d9b4602d01e412e9d217dc6e7`, a clean-source check, the 09c retention step skipped, and an artifact count of zero. See `4CBOn2_Division12_Verification.md`.

Division 13 added a bounded, local scope contract enforced by `ActionScopeEnforcer`. `ScopeContract` records the goal, selected targets with their exact allowed actions, allowed recipients and file labels, `cloudTransferAllowed` (default **false**), completion mode, bounded limits, creation time, and exclusions. `ScopeBounds` hard-caps a job at 20 actions and 5 minutes, so a looser contract cannot be constructed. Requests are refused before any discovery when the job is expired, the target or action is excluded, the target/action is outside the job, cloud transfer was not permitted, or the action budget is spent; a registry failure surfaces as `CAPABILITY_UNAVAILABLE` with its reason. Owner confirmation is required for per-step mode and for every `MANUAL_HANDOFF` route. Fifteen synthetic JVM tests were added; the active suite is 55 (40 preserved + 15 new), and the CI inventory guard, step label, notice, and exact XML assertion are aligned to 55. No UI wiring, persistence, real personal data, network I/O, accessibility service, or screen capture was added. No-artifact CI run [40831655312](https://github.com/mohamtur1/4CBOn2/actions/runs/40831655312) passed at commit `4d18e2bf4cb39d0e014fb434a5aad1cdb1a6ea46` with 55 passed, 0 failures/errors/skips, a fresh debug APK, zip alignment, v2 signature, SHA-256 `d3cba101119afe0364767f4aceb0ee2bd32ff7e1c01072b0a979b635a37890e9`, a clean-source check, the 09c retention step skipped, and an artifact count of zero. See `4CBOn2_Division13_Verification.md`.

## Dependency and blocker rules

- Division 02 is an early feasibility/policy gate, not proof that the owner device supports the desired permissions.
- Divisions 08–10 establish actual service routes or blockers. A browser-open state is not authenticated programmatic control.
- Division 30 proves built-in AI itself; runtime-management access in 10 is a different capability.
- Division 14 must work before any interactive phone input is enabled. Late remote commands must not resume a cancelled session.
- The standard profile prioritizes APIs/intents. A general-purpose autonomous Accessibility runner is not represented as Play-eligible because scope/stop controls exist.
- Division 34 stays blocked or omitted if the device/deployment is ineligible. Do not supply an OS/security bypass or mislabel the app as an accessibility tool.
- Phone UI tests initially use an owner-controlled test app and synthetic data. Do not test automation on live banking, authentication, permission-grant, or payment screens.
- Division 40 requires the applicable scope, takeover, observation, and critical-action tests. Unsupported routes remain explicit manual handoffs.
- Division 31 requires genuine supporting capabilities. A file-transfer/manual route is not a completed autonomous cloud round trip.
- Division 38 may pass mocked/local tests while live submission remains unverified; never conflate the two.
- A cloud job may outlast a session. Save its reference and check in a later session; do not wait past the cap or blindly repeat it.
- Safe offline modules can progress while unrelated remote/device gates are blocked, if their own dependencies pass.
- Division 42 is a prototype handoff, not production/security certification or Play approval.

## Progress record

Track two separate dimensions:

1. **Work status:** Not started, in progress, checkpointed, blocked, or finished.
2. **Verification:** Not tested, static check, mocked/local test, emulator test, real-device test, or live-service test.

Each division report records elapsed time, exact files/changes, actual tests/outcomes, checkpoint/artifact path, blockers, remaining work, any continuation needed, and the next eligible division.

A successful mocked or emulator test must not be labeled real-device compatible, live-service verified, or store-approved. Local checkpoints do not imply a repository commit/upload; report those only if they actually occurred through an authorized route.

## Still outside the prototype's promises

- Universal autonomous control of every installed app or website.
- Control of protected credential, payment, lock-screen, or system-consent workflows.
- Root access, hidden input, bypassed security settings, or self-granted permissions.
- A mandatory on-device LLM or full offline reasoning on an untested phone.
- An always-on backend or guaranteed Colab availability.
- A general arbitrary-code sandbox or unrestricted remote browser service.
- Every Kaggle competition type.
- Production penetration testing, Play Store approval, and production signing/distribution.

These require further legitimate, separately scoped work if applicable. They cannot be squeezed into the final division or presented as completed without evidence.

## Readiness decision

**Divisions 11–13 are closed for their documented source/build scope. Division 14 — Takeover and cancellation lease is the next eligible division but has not started; broader phone control remains gated.**

The goal is one verified increment or an honest recoverable checkpoint per session—not a guaranteed entire product at 42 times 30 minutes.
