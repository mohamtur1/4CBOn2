# 4CBOn2 — Android Companion
## Online, offline, and browser design · Revision 3

**Design only · 21 October 2026**

**Build-readiness verdict:** Ready to begin a scoped prototype, not ready to claim a finished autonomous cloud-connected APK. The first implementation must prove account authorization, notebook connectivity, runtime availability, and browser boundaries before promising unattended operation.

**Status:** No Android source application, APK, notebook, account connection, repository update, or live submission has been created or tested in this design revision.

**Existing notebook target:** `4CBOn2_Kaggle6.ipynb`, repository root of [mohamtur1/4CBOn2](https://github.com/mohamtur1/4CBOn2).

**Architecture decision:** Add one native, offline-first Android companion to the shared agent system. Keep the Colab built-in AI as the chosen cloud inference interface. The app is not a replacement model provider, an always-on Colab runtime, or a second unrelated agent organization.

This revision extends the unified agent design. Its mobile interface and connectivity decisions supersede conflicting earlier assumptions; the four job profiles, permission boundaries, evaluation safeguards, and notebook filename remain.

---

# Part I — Explain it simply

## 1. As if I were two

Your phone gets a little helper house.

When there is no internet, the helper can open its saved books, keep your notes, and put new jobs in a waiting box.

When the internet comes back, it can ask its big helpers for help. Colab helps it think. Kaggle helps it try puzzles. GitHub keeps the instruction book.

There is a little window where you can watch it visit a page and check something. You can stop it or take over.

A helper that is asleep cannot answer just because your phone has internet. Sometimes you must wake it up and sign in.

**Without internet: use what is saved and prepare work. With internet and available helpers: connect and do more.**

## 2. As an adult

The APK should be a durable personal workspace, not a browser wrapper that becomes useless offline. Its interface, saved knowledge, agent drafts, files, job queue, and past results live on the phone. Network services extend those capabilities.

Use two explicit modes:

- **Offline workspace:** Local, deterministic functions and cached information. Optional downloaded on-device AI can be added later, but is not silently substituted for Colab.
- **Online companion:** Authorized service APIs, notebook opening, synchronization, and supported cloud jobs. Live Colab AI is enabled only after a successful end-to-end integration test.

**Internet access is necessary, but not sufficient.** The relevant account must be authorized, the service must be reachable, the requested capability must be supported, and any required runtime must be available.

The product can show its plan, actions, observations, and checks. It should not pretend that a browser is literally thinking or display invented activity as proof of work.

---

# Part II — Chosen product design

## 3. Fixed implementation choices

| Item | Chosen design |
| --- | --- |
| Product name | 4CBOn2 |
| First platform | Native Android, one owner initially |
| Application ID | `io.github.mohamtur1.fourcbon2` as the proposed identifier |
| First installable build | `4CBOn2-debug.apk`, clearly marked as a development build |
| Later release target | Signed `4CBOn2.apk`; release key remains under the owner's control |
| Minimum Android target | Android 8 / API 26 for the basic app; optional AI features may require more |
| UI implementation | Kotlin and Jetpack Compose |
| Local store | Room-based local data layer; encrypted sensitive storage and artifacts with a Keystore-protected key design |
| Synchronization | Persistent outbox, service-specific adapters, and constraint-aware WorkManager jobs |
| Browser | Custom Tabs for external sites/sign-in; isolated WebView workspace for supported content |
| Cloud AI | Colab built-in `google.colab.ai`, subject to actual access and transport validation |
| Offline AI | Explicit optional on-device component, disabled until installed and device-tested |
| First server requirement | No mandatory custom server for the basic offline/API/browser-opening prototype |
| Advanced control gateway | Add outside Colab only if a validated integration requires it; no public unauthenticated notebook server |
| Repository organization | Existing notebook at root; proposed Android project under `android/`, design documents under `docs/` |

These are design defaults, not claims about an existing APK's size, performance, device compatibility, or Play Store approval. The target SDK and dependencies must be selected against current stable tooling and distribution requirements at build time.

The local-first store and queued synchronization follow Android's offline-first architecture guidance. WorkManager supports network-constrained deferred work; it does not promise immediate execution whenever connectivity appears. [14](https://developer.android.com/topic/architecture/data-layer/offline-first)

## 4. What it can do online and offline

| Function | Offline design | Online design |
| --- | --- | --- |
| Agent Builder | Create/edit goals, templates, budgets, permissions, and draft jobs; instantiate known local templates | Request new AI-assisted plans through a verified Colab connection |
| Ask a Question | Search saved knowledge and show supporting passages; label responses as local lookup rather than fresh cloud reasoning | Evidence-based questions and follow-ups when the AI/retrieval route is available |
| Research library | Read saved reports, citations, PDFs, and permitted page snapshots | Retrieve permitted sources and synchronize approved evidence |
| Files and notes | Create/edit app documents; organize files the user selected; export job bundles | Exchange authorized artifacts with the relevant service |
| Data analysis | Small supported CSV/JSON checks, statistics, and charts using reviewed local routines | Larger trusted analysis and ML jobs on supported cloud runtimes |
| Code/notebook work | View and edit text, prepare patches, validate notebook JSON and supported schemas | Run supported reviewed jobs in the appropriate environment; do not execute arbitrary downloaded programs on the phone |
| Kaggle | Read cached competition details, inspect saved results, prepare artifacts and submission intent | Refresh requirements/status, run supported notebook workflows, and submit only when authorized |
| GitHub | Read downloaded files, edit a local draft, inspect cached versions and diffs | Fetch current versions and propose/perform explicitly authorized repository changes |
| Job queue | Save tasks, dependencies, deadlines, and waiting reasons | Dispatch supported tasks only after checking authorization, availability, and deadlines |
| Browser | Open permitted saved reader snapshots; no fresh visits or remote sign-in | Open real sites and run only supported authorized browsing actions |
| Communication | Draft reports or messages | Send/publish only through an enabled adapter with a valid scoped grant |
| Monitoring | Show last-known results with their last-sync time and a stale indicator | Refresh actual service/job status and record acknowledgments |
| Reminders | Save reminders and request supported local notifications | Synchronize task events; do not confuse a reminder with successful remote execution |
| Optional local AI | After a model is deliberately installed and tested: limited local drafting or summarization | Download/update the approved model only with consent and suitable connectivity |

Offline capability is useful even without a language model. It is not equivalent to running Colab's AI service without the internet. Remote login, live browsing, uploads, cloud training, new cloud reasoning, and real competition submission cannot happen offline.

### Offline inference decision

Include an extension point for **On-device AI**, but do not bundle a mandatory large model in the first APK. A later opt-in package can use a current supported runtime, such as LiteRT-LM, with a licensed, integrity-checked model that passes memory, thermal, quality, and battery tests on the device.

Google's current mobile inference guidance directs projects from the deprecated mobile MediaPipe LLM implementation toward LiteRT-LM. Do not begin a new optional AI integration by copying an obsolete example dependency. [4](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)

The app must label which engine produced an answer: **saved evidence**, **local routine**, **on-device model**, or **Colab AI**. No silent provider/model fallback and no claim that a small local model has the same quality as the cloud service. The basic app must continue to work if the phone cannot run a local model.

## 5. How each service connects

### GitHub

Initial capabilities: fetch repository metadata and notebook versions, cache code, show diffs, and prepare a proposed commit. A write requires the authorized account, selected repository, exact change, current base revision, and a permitted action.

For the single-owner prototype, support a user-created fine-grained token restricted to the intended repository and minimum needed permissions, entered in native secure settings. Production can move to a registered GitHub App with an appropriate supported browser-based authorization flow. No reusable client secret belongs in the APK.

Read-only access is the default. A request to edit the notebook does not grant permission to delete branches, change repository visibility, rewrite history, or force-push. Never assume the repository branch, visibility, existing notebook, or write access before checking.

### Kaggle

Initial integration uses the documented account/API-token route and typed supported operations. Store the credential encrypted; do not put it into a WebView or pass it to the model. The official CLI documentation describes API-token authentication. [2](https://github.com/Kaggle/kaggle-cli/blob/main/docs/README.md)

Capabilities can include inspecting competition metadata, reading submission history, preparing or managing permitted notebook jobs, and making a supported authorized submission. The adapter must respect the actual competition contract. Official submission commands distinguish file and notebook-version routes. [16](https://github.com/Kaggle/kaggle-cli/blob/main/docs/competitions.md)

Rules acceptance, eligibility, team limits, external-data/model rules, deadlines, and runtime restrictions remain authoritative. Opening a competition page is not proof the account can enter it.

### Colab

**Guaranteed design route for the first prototype:** open the user's notebook in a browser-backed Custom Tab, let the user sign in and start an available session, and exchange approved prepared artifacts. Opening the page is not proof that the app has programmatic runtime control.

**Advanced connection to prove early:** an officially supported, authorized runtime/notebook integration that can execute the chosen controller and return real observations to the phone. Google now documents programmatic routes, including a runtime API and official automation tooling. The REST API is described as beta and allowlisted; access cannot be assumed for the owner's account. [1](https://developers.google.com/colab/api/reference/rest) [3](https://developers.googleblog.com/announcing-the-colab-mcp-server-connect-any-ai-agent-to-google-colab/)

The official Colab CLI is another candidate for a separately hosted adapter; its supported execution environment and authentication must be validated. It is not something the APK can simply embed and assume will work on Android. [12](https://github.com/googlecolab/google-colab-cli)

The **runtime-management API**, **notebook-control tools**, and **built-in text-generation interface** are different capabilities. Successful authentication to one does not prove access to the others. The built-in AI example documents model use within Colab. [5](https://colab.research.google.com/github/googlecolab/colabtools/blob/main/notebooks/Getting_started_with_google_colab_ai.ipynb)

Before enabling mobile live-AI mode, prove that the allowed route can run the actual `google.colab.ai` call in the target environment and return a result without exporting Colab's internal model-proxy credentials. If not, retain the explicit browser/manual handoff rather than inventing a private endpoint or changing inference providers.

Do not make Colab a permanent public web server, add anti-idle workarounds, or assume the notebook survives because the phone remains online.

### Connection status must be honest

Track each provider separately: **not configured**, **browser opened**, **API authorized**, **capability verified**, **runtime unavailable**, **quota exhausted**, **reauthorization needed**, **offline/stale**, or **blocked**.

A Wi-Fi/mobile-data icon is not a green light for every service. A browser login cannot automatically be inspected by the app. Only a successful permitted capability probe can establish the corresponding connected state.

## 6. Chrome inside the app: the precise design

The request is achievable as an **in-app browsing experience**, not by placing the entire installed Chrome application inside an arbitrary app view.

Use two complementary browser surfaces and one optional later execution service.

### A. Chrome-backed Custom Tabs: real browser experience

Use Custom Tabs for Colab, Kaggle, GitHub, Google sign-in, and ordinary external links. When Chrome is the selected supported browser, the experience is Chrome-backed. Otherwise use the user's supported browser or an explicit external-browser fallback.

Custom Tabs provide browser-managed sessions, but the app does not gain unrestricted access to their cookies, DOM, passwords, or arbitrary page scripting. Android documents the distinction between Custom Tabs' limited integration and WebView's deeper UI/control surface. [11](https://developer.android.com/develop/ui/views/layout/webapps/in-app-browsing-embedded-web)

Provider authentication must not be forced into the app's raw WebView. Google prohibits its OAuth authorization in embedded WebViews and recommends browser-based alternatives. [7](https://developers.googleblog.com/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/)

**Consequence:** Opening a Colab Custom Tab does not make the agent able to click Run All or read the notebook's private state. Use a supported API/control integration if available; otherwise the user performs that browser step.

### B. Agent Browser Workspace: observable supported browsing

Provide a separate WebView/reader workspace for approved content and bounded browsing recipes. It can display permitted public pages, extracted text, URLs, citations, and evidence of completed actions. Unsupported or authenticated flows open in the real browser surface instead.

An agent may use only reviewed actions for the specific supported site, such as navigating to an allowed URL or extracting a public passage. A form submission, account change, upload, or publication passes the normal approval gate. APIs are preferred over fragile DOM automation.

Do not give arbitrary websites a privileged native bridge. Android warns that `addJavascriptInterface` with untrusted content can expose app capabilities to hostile pages. [2](https://developer.android.com/develop/ui/views/layout/webapps/webview)

Apply HTTPS-only navigation, URL and redirect validation, blocked private/metadata destinations, disabled broad file access, no universal cross-origin file permissions, safe handling of downloads/uploads, and an isolated app-managed browsing state. Do not import the user's Chrome cookies or record login/2FA secrets.

### C. Remote Chromium worker: optional advanced feature

If later workflows genuinely need broader browser automation, use a dedicated, permission-controlled browser worker outside the phone and outside an improvised free-Colab web service. It can return allowed screenshots and action results to the app.

Label the surface **Remote Browser**, not local Chrome. The worker has separate costs, session credentials, isolation, network boundaries, and availability requirements. It is not part of the first APK and is not a bypass for website controls, CAPTCHAs, or provider terms.

### What 'like it was thinking' should look like

Show an **Agent Activity** panel alongside the browser workspace:

```text
Goal: inspect the competition evaluation method
Plan: find the official evaluation description
Action: opened the permitted evaluation page
Observation: saved the relevant passage and its URL
Check: the passage supports the recorded metric
Next: prepare a validation plan
Status: waiting for the required authorization or runtime
```

These are concise plan/action/result summaries, not a claim to reveal private internal thought. Display an event only when it actually occurred; do not use an animation to imply a page was read or a submission succeeded.

Show **Pause**, **Take Over**, **Stop**, the current actor, the actual URL, and when the evidence was captured. Redact sensitive screenshots. Stopping prevents new controlled actions but cannot undo an already accepted external action.

No accessibility-service takeover, root access, invisible cross-app tapping, password capture, or unrestricted phone control is part of this design.

## 7. Mobile interface

Use five main destinations, with advanced features in a menu:

| Destination | Purpose |
| --- | --- |
| **Home** | Service health, runtime readiness, recent work, offline/online state |
| **Ask** | Live supported questions or clearly labeled offline saved-knowledge lookup |
| **Agents** | Agent Builder, saved agents, job profiles, budgets, queued drafts |
| **Browser** | Agent workspace, page evidence, and browser-opening controls |
| **Activity** | Plans, actual actions, artifacts, approvals, progress, and failures |

The menu contains **Knowledge**, **Improvement Lab**, **Files**, **Connections**, **Settings**, and **Export/Restore**. The existing notebook UI remains available when using Colab directly; the APK is a mobile companion, not a reason to duplicate every wide notebook table on a small screen.

Use a persistent engine/status label: **Offline Library**, **Local Task**, **On-device AI**, **Colab AI**, **Kaggle Job**, or **Manual Browser**. An unavailable mode is disabled with an explanation rather than simulated.

---

# Part III — Safety, synchronization, and build readiness

## 8. How the app shares the existing agent core

```text
ANDROID APP
  Local database + artifacts + saved knowledge
  Agent drafts + permissions + persistent outbox
  Local reviewed routines + browser surfaces
           |
           +--> GitHub adapter: versioned code and authorized changes
           +--> Kaggle adapter: supported jobs/status/submissions
           +--> Colab browser route: interactive notebook access
           +--> Verified Colab control route: only after proof
                         |
                         v
              Existing notebook Agent Execution Core
              Plan -> Act -> Verify -> Remember -> Recover
```

Use the same versioned job specification, artifact manifest, tool contracts, budget semantics, and outcome states across app and notebook. The mobile app supplies limited local runners; it does not pretend a Python cloud environment exists on every phone.

If a later control gateway is required, host it on suitable persistent infrastructure with authentication and scoped credentials. Do not use a GitHub repository as a live command database or assume a relay keeps Colab alive. Adding a gateway is an explicit deployment/cost decision, not a hidden prerequisite of the offline prototype.

## 9. Offline queue and reconnection rules

Each queued job records its owner/account, goal, workflow version, inputs, artifact hashes, base revision, resource caps, dependencies, deadline, authorization scope/expiry, and current state.

Suggested states: **Draft**, **Waiting for Internet**, **Waiting for Account**, **Waiting for Runtime**, **Ready**, **Running**, **Waiting for Approval**, **Uncertain External Outcome**, **Completed**, **Failed**, or **Cancelled**.

On reconnect:

1. Synchronize safe local drafts and permitted cached metadata.
2. Re-check account authorization and actual service capabilities.
3. Reject expired or ineligible jobs and stale authority.
4. Revalidate changed competition rules, artifacts, and repository revisions.
5. Execute only actions still covered by the current grant.
6. Reconcile uncertain external results before any retry.

Do not automatically submit to Kaggle or push a repository change merely because connectivity returns. A scoped prior grant may be reused only if its conditions still hold. Never resolve a repository conflict with an automatic force-push.

Use an intent ledger and unique run marker where supported. An external service might accept an action before a connection is lost; a local queue alone cannot guarantee exactly-once execution. Show uncertainty and reconcile actual service history.

Last-known cloud status must include its timestamp. While the phone is offline, an already accepted cloud job may continue if its platform/runtime permits it; the app cannot promise that it continued or finished until it reconnects and checks.

## 10. Security, data, battery, and background work

- Secrets live in encrypted credential storage protected using Android Keystore facilities; private artifacts/data also require encryption. Room by itself is not a claim of database encryption.
- No provider token, reusable client secret, signing key, or Colab proxy credential is embedded in the APK, model context, repository, page JavaScript, or routine logs.
- Allow file access through the app workspace and user-selected documents. No broad all-files access is needed for the first release.
- Restrict each integration to the intended account, repository, competition, destination, and data scope.
- Treat pages, documents, dataset cells, and model outputs as untrusted. They cannot grant permissions, change recipients, or authorize publishing.
- Sending private data to cloud AI requires an applicable consent/rules check, even when the data is already cached on the phone.
- No arbitrary native/Python/shell code downloaded from a website is automatically executed by the phone.
- Enable Data Saver by default: synchronize small status/metadata first, do not automatically download datasets or models over cellular, and require consent for large transfers.
- Initial cache target: 100 MB for downloadable ordinary content, with cleanup controls. Optional model files and explicit user downloads have separately displayed storage requirements. This is a proposed cap, not a measured APK size.
- Use WorkManager for eligible deferrable synchronization and appropriate user-initiated transfer mechanisms for large active transfers. Do not promise exact timing or uninterrupted background execution.
- Android limits background/foreground-service work; newer versions also impose job quotas. A persistent notification does not grant unlimited runtime. [6](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- Keep first-release long-running inference/training off the phone. Any optional on-device AI must pass thermal, memory, cancellation, and battery tests.
- Support sign-out, credential revocation, cache deletion, export, and clearly explained backup behavior. Restored copies must not silently restore authorization to perform external actions.

## 11. Are we ready to build?

**Yes: ready to begin the first prototype. No: not ready to promise the complete final product.**

The offline-first app, shared job contracts, UI structure, safe browser approach, and phased scope are sufficiently specified to start. The following are engineering proofs to do early, not reasons to keep adding features indefinitely.

| Area | Readiness verdict |
| --- | --- |
| Product scope and shared agent architecture | Ready for implementation |
| Offline utility without cloud AI | Ready for implementation |
| Agent drafts, cache, and explicit outbox | Ready for implementation |
| Browser-backed notebook/service opening | Ready to implement and device-test |
| GitHub/Kaggle account operations | Need real authorization and adapter tests |
| Full phone-to-Colab built-in AI control | Critical integration proof required; not guaranteed |
| Autonomous control of every Chrome website | Not an MVP promise; unsupported universal requirement |
| Strong custom-code sandbox / remote browser worker | Later separate implementation and testing |
| Fully generative offline AI on the owner's phone | Optional; hardware/model proof required |
| Signed production release and distribution | Not ready until security, device, update, and signing tests pass |

### Milestone 1 — Small installable offline prototype

Build the native shell, local database, agent drafts, saved report reader, small supported file checks, outbox, and honest status labels. Test airplane mode, process restart, and cancellation. Produce an installable debug APK plus source and a checksum when actually built.

### Milestone 2 — Prove the three service paths

Fetch an authorized GitHub file and Kaggle account result. Open the exact notebook through a verified repository branch. Separately attempt the supported Colab control/AI proof and record what works for the account.

Do this proof before investing heavily in remote browser automation. If full Colab control is unavailable, ship only a clearly labeled browser/manual companion mode or revise the deployment deliberately. Do not call that partial mode full live mobile AI.

### Milestone 3 — One real end-to-end agent workflow

Start with a supported research or tabular competition task. Create the goal, obtain a valid plan through the available route, run trusted work, verify the artifact, and return the real result to the app. Use prepare-only mode before an explicitly authorized live submission.

### Milestone 4 — Controlled visible browsing

Add a small number of reviewed browsing recipes and event-backed observations. Test account/login handoff and verify that no native privilege is exposed to untrusted pages. Wider browser automation remains conditional.

### Milestone 5 — Optional offline intelligence

Add a deliberately downloaded on-device model only after selecting a license-compliant, device-compatible model/runtime and measuring its limitations. Keep offline utility available without it.

### Milestone 6 — Release hardening

Test encryption, token expiry/revocation, synchronization conflicts, duplicate prevention, permission boundaries, privacy, accessibility of the UI, supported Android devices, background behavior, update compatibility, and release signing. Store the release key outside the repository and use a reproducible, reviewed build pipeline.

## 12. Acceptance tests before release claims

- Airplane mode retains saved knowledge, agent drafts, local routines, and a durable waiting queue without fake cloud answers.
- A fresh install works for basic offline features without a downloaded model or a cloud account.
- A lost connection during an external action does not trigger blind resubmission.
- Reconnection checks deadlines, versions, rules, account identity, and authorization before consequential work.
- GitHub conflicts preserve both versions and do not force-push.
- API/auth/runtime/capability states are distinct from phone connectivity and browser-open state.
- Live Colab AI is enabled only after a real supported invocation through the intended route.
- Google/provider sign-in uses an approved browser-based flow, not credential-capturing WebView automation.
- Untrusted pages cannot call privileged native services or read app credentials.
- Browser progress events correspond to actual actions; no fabricated reasoning or success traces.
- Stop prevents new controlled actions and accurately explains limits on cancelling accepted remote work.
- Background operation handles Android scheduling delays and process termination without an unlimited-runtime claim.
- Private credentials and competition data do not appear in the notebook, repository, APK resources, screenshots, or logs.
- Optional on-device AI identifies itself, stays within device limits, and never silently replaces Colab.
- A build labeled release has passed the applicable signing, security, installation, and update tests; a debug APK is not presented as production-ready.

**Final recommendation:** Start building the offline-first companion and the notebook integration proofs next. Keep the ambitious features in the roadmap, but do not make them prerequisites for the first useful, testable APK. We remain in design until implementation is explicitly started.

---

# Sources and scope notes

Official materials checked on 21 October 2026. Design choices above are proposals; no account, phone, model, runtime, browser-control path, APK, or submission was tested here.

- Android offline-first queues and connectivity: [14](https://developer.android.com/topic/architecture/data-layer/offline-first)
- Android Custom Tabs versus WebView: [11](https://developer.android.com/develop/ui/views/layout/webapps/in-app-browsing-embedded-web)
- Google OAuth embedded-WebView restrictions: [7](https://developers.googleblog.com/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/)
- WebView bridge risks: [2](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- Background worker and foreground-service limits: [6](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- Official Colab runtime API and access requirements: [1](https://developers.google.com/colab/api/reference/rest)
- Official Colab MCP announcement and workflow: [3](https://developers.googleblog.com/announcing-the-colab-mcp-server-connect-any-ai-agent-to-google-colab/)
- Official Colab CLI: [12](https://github.com/googlecolab/google-colab-cli)
- Colab built-in AI examples: [5](https://colab.research.google.com/github/googlecolab/colabtools/blob/main/notebooks/Getting_started_with_google_colab_ai.ipynb)
- Kaggle token authentication: [2](https://github.com/Kaggle/kaggle-cli/blob/main/docs/README.md)
- Kaggle submission routes: [16](https://github.com/Kaggle/kaggle-cli/blob/main/docs/competitions.md)
- Current mobile LLM runtime migration guidance: [4](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)
