# 4CBOn2 — Timeboxed Build Plan

**Build in progress · Revised 31 October 2026 · Divisions 01–10 verified in the Division 10 checkpoint; Division 09a + 11–42 implemented as source + unit-test checkpoints in batches A–E (see `android/BATCH_*.md` receipts); the fixed Colab notebook `4CBOn2_Kaggle6.ipynb` now exists; live Kaggle/Colab remain authorization/runtime-blocked; no updated retained APK, account connection, credential entry, or phone/device test exists yet**

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
| 09 | Prove Kaggle read access | Permitted account/API read; distinguish missing authorization from unsupported operations |
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
| 23 | Kaggle metadata/status | Supported competition/job views with real last-sync and stale/error states |
| 24 | Chrome-backed opening | Custom Tab/service links and browser fallback; no unrestricted Chrome-control claim |
| 25 | Controlled browser workspace | One reviewed public-page/reader workflow, URL boundaries, no privileged native bridge to untrusted content |
| 26 | Consented observation boundary | Shared/structured data first; any screen capture separately gated, secure/sensitive contexts excluded, cloud upload Off by default |
| 27 | Actual action timeline | Record plan/action/result/check events; distinguish attempted, pending, manual, verified, and uncertain outcomes |
| 28 | Shared job bundle | Versioned app/notebook contract, manifest, private export/import, hashes, and policy revalidation; a hash is not authority |
| 29 | Notebook skeleton | Exact file `4CBOn2_Kaggle6.ipynb`, runtime detection, controller/runner roles, capability panel |
| 30 | Colab built-in AI adapter | Real service/model probe and bounded request; no provider fallback or proxy-credential export |
| 31 | One bounded agent loop | One supported goal -> permitted action -> verification workflow; stop/error path demonstrated |
| 32 | Critical phone-action guard | Sensitive/manual-only contexts, exact action preview, account checks, protected-screen handoff, and no blind irreversible retry |
| 33 | Human-defined routine engine | Fixed script, explicit human trigger, known preconditions, and bounded execution; not an exemption for AI-driven Accessibility use |
| 34 | Conditional personal-lab UI runner | Only a permitted, authorized experimental configuration against an owner-controlled fixture app; unavailable if eligibility fails; not Play-compliance proof |
| 35 | Privacy and prompt-injection tests | Malicious page/message, unauthorized destination, secret/screen-content leakage, and data-sharing scope fixtures |
| 36 | Online/offline phone-control routing | Available local/API/model routes only; no fake cloud planning, locked-device bypass, or silent alternate provider |
| 37 | Kaggle prepare-only pipeline | One trusted tabular template, explicit validation, artifact checks, reproducible result |
| 38 | Submission gate and receipt states | Limits, durable intent, acknowledgment/uncertainty handling, mocked tests; live submission separately authorized |
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
| 09 — Prove Kaggle read access | **Blocked (Kaggle authorization)** | Public competition-list endpoint returned HTTP 401; read boundary, 13 unit/static/build/signature checks passed; a minimal read-only OAuth registration request is prepared but unsent; no token/OAuth client/device test | `android/KAGGLE_READ_ACCESS.md` + `android/KAGGLE_OAUTH_REGISTRATION_REQUEST.md` |
| 09a — Prepare local Kaggle token route | **Not started** | Confirm documented token format/scoping and design/test a local-entry route before any real token is entered; no token in chat/source/logs | None |
| 10 — Prove Colab entry/control route | **Finished (manual route)** | Fixed manual-handoff reference and public route investigation complete; target notebook reported missing; compile, aggregate 15-test suite, debug build, v2 signature, and static checks passed; no account/runtime/AI/device test | `android/COLAB_ENTRY_ROUTE.md` |
| 10a — Restore/verify Division 10 toolchain checkpoint | **Finished** | Toolchain restored from verified sources; compile/test/APK verification completed with bounded one-worker memory configuration | `android/COLAB_ENTRY_ROUTE.md` |
| 09a — Prepare local Kaggle token route | **Finished (design checkpoint)** | Documented token format/scoping and local-entry route; no token entered, probed, or stored | `android/KAGGLE_TOKEN_ROUTE.md` |
| 11 — Local database | **Finished (source)** | JSON document store with restart persistence; unit tests written; no build verification in this sandbox | `android/local/` |
| 12 — Selected-app capability registry | **Finished (source)** | Action-level support with unavailable states; unit tests written | `android/capability/` |
| 13 — On-device scope contract | **Finished (source)** | Destination allow-list, expiry, action cap, cloud-transfer gate; unit tests written | `android/scope/` |
| 14 — Takeover and cancellation lease | **Finished (source)** | Stale-command rejection, explicit resume; unit tests written | `android/control/` |
| 15 — Offline agent drafts | **Finished (source)** | Draft lifecycle with budget/scope; unit tests written | `android/agents/` |
| 16 — Safe file import/export | **Finished (source)** | Workspace-only paths, traversal rejection; unit tests written | `android/files/` |
| 17 — Offline knowledge library | **Finished (source)** | Search with provenance and update time; unit tests written | `android/knowledge/` |
| 18 — Small local analysis | **Finished (source)** | Bounded CSV stats + structural JSON validation; valid/invalid fixtures in tests | `android/analysis/` |
| 19 — Persistent waiting queue | **Finished (source)** | Durable states, restart recovery, no consequential replay; unit tests written | `android/local/` |
| 20 — Sensitive-action approval gate | **Finished (source)** | Scoped destination/artifact, expiry, per-request caps; unit tests written | `android/approval/` |
| 21 — One harmless app intent/API action | **Finished (source)** | Open/prepare/share result/handoff semantics; no unrestricted UI input; unit tests written | `android/action/` |
| 22 — GitHub cache and diff | **Finished (source)** | Permitted-path refresh, local diff, exportable change requiring approval; unit tests written | `android/github/` |
| 23 — Kaggle metadata/status | **Finished (source, live data auth-blocked)** | Status adapter with honest Unauthorized state; no credential entered; live refresh requires owner authorization | `android/kaggle/` |
| 24 — Chrome-backed opening | **Finished (source)** | Custom Tab decision with external fallback; HTTPS/host policy; unit tests written | `android/browser/` |
| 25 — Controlled browser workspace | **Finished (source)** | Host allow-list, private/metadata block, no native bridge; unit tests written | `android/browser/` |
| 26 — Consented observation boundary | **Finished (source)** | Capture Off by default, sensitive-context exclusion, cloud upload Off by default; unit tests written | `android/observation/` |
| 27 — Actual action timeline | **Finished (source)** | Plan/action/result/check events with honest statuses, restart-persistent; unit tests written | `android/timeline/` |
| 28 — Shared job bundle | **Finished (source)** | SHA-256 manifest, import/export with hash + contract revalidation; unit tests written | `android/bundle/` |
| 29 — Notebook skeleton | **Finished (source + JSON validated)** | `4CBOn2_Kaggle6.ipynb` with runtime detection, controller/runner roles, capability panel; notebook JSON verified by parser | `4CBOn2_Kaggle6.ipynb`, `android/notebook/` |
| 30 — Colab built-in AI adapter | **Finished (source, runtime-blocked)** | Adapter with honest capability states; live probe requires an actual Colab runtime; no fallback | `android/ai/` |
| 31 — One bounded agent loop | **Finished (source)** | Goal → permitted action → verification with budget, stop, error paths; unit tests written | `android/agent/` |
| 32 — Critical phone-action guard | **Finished (source)** | Sensitive contexts manual-only, preview + account checks, no blind retry; unit tests written | `android/guard/` |
| 33 — Human-defined routine engine | **Finished (source)** | Fixed script, explicit trigger, preconditions, bounded execution; unit tests written | `android/routines/` |
| 34 — Conditional personal-lab UI runner | **Finished (source, device-blocked)** | Eligibility gate (config + fixture + device test); execution requires an eligible device; not Play-compliance proof | `android/lab/` |
| 35 — Privacy and prompt-injection tests | **Finished (tests)** | Malicious destination/leakage/tamper/consent fixtures; unit tests written | `android/app/src/test/.../safety/` |
| 36 — Online/offline phone-control routing | **Finished (source)** | Local/API/model only; locked device NONE; prompt text never routes; unit tests written | `android/routing/` |
| 37 — Kaggle prepare-only pipeline | **Finished (source, live submission blocked)** | Trusted template, explicit validation, artifact hash checks, reproducible result; submission separately authorized | `android/pipeline/` |
| 38 — Submission gate and receipt states | **Finished (source, mocked tests)** | Durable intent, limits, ack/uncertainty handling, reconcile-before-retry; live submission separately authorized | `android/submission/` |
| 39 — Minimal Improvement Lab | **Finished (source)** | Baseline/candidate reports, preserved versions, fixed evaluator, approval before promotion; unit tests written | `android/lab/` |
| 40 — Harmless cross-app integration test | **Finished (tests)** | Selected supported workflows with synthetic data; scope escape, takeover, manual handoff | `android/app/src/test/.../integration/` |
| 41 — Focused recovery/safety smoke tests | **Finished (tests)** | Offline/reconnect, stale response, interrupted work, revoked permission, ambiguous ack, duplicate action | `android/app/src/test/.../safety/` |
| 42 — Package prototype and support matrix | **Finished (manifest)** | Source checksums, rebuild instructions, blocked capabilities; rebuild requires Android toolchain | `android/PROTOTYPE_PACKAGE_MANIFEST.md` |

Divisions 01–02 are foundation documentation checkpoints; Division 03/03a provisioned the active sandbox toolchain; Division 04 created and locally compiled the source skeleton; Division 05 retained and verified a debug-signed development APK; Division 06 added locally verified navigation source; Division 07 added a compiled, unpopulated credential-storage boundary; Division 08 added a read-only GitHub boundary and verified public repository visibility from the build environment; Division 09 added a read-only Kaggle boundary but correctly stopped at Kaggle’s authorization requirement; Division 10 added and locally verified a fixed manual Colab handoff reference, while the target notebook itself remains missing and runtime/AI capability remains untested. These results do **not** establish phone compatibility, authenticated GitHub/Kaggle access, a Colab account/notebook/runtime or built-in-AI call, special permissions beyond the normal Internet declaration, a real device test, store eligibility, credential entry/storage on a device, production signing, or production release readiness. The retained Division 05 APK predates the Division 06–10 source updates. No later division is implied to exist merely because it is listed.

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

**Ready for phased foundation implementation when requested; broader phone control remains gated. Still designing now.**

The goal is one verified increment or an honest recoverable checkpoint per session—not a guaranteed entire product at 42 times 30 minutes.
