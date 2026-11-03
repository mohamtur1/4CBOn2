# 4CBOn2 — Scoped Phone Control
## Design revision 4 · Supported apps, owner control, and safety boundaries

**Design only · 23 October 2026**

**What changed:** The proposed product expands from an Android research companion into a scope-limited personal phone assistant. It may coordinate supported actions across selected applications, while the owner can inspect, pause, restrict, or take over.

**Status:** No APK or notebook has been built. No app has been controlled, no account has been connected, and no accessibility or screen-capture permission has been requested. This is an architectural decision, not a claim of working phone control.

**Existing targets remain:** repository `mohamtur1/4CBOn2`; notebook `4CBOn2_Kaggle6.ipynb`. Colab built-in AI remains the chosen cloud inference interface, subject to its existing integration gate.

**Decision:** Keep one agent system. Add a Phone Control Layer beneath the shared agent engine, with an on-device scope broker, per-app capability registry, takeover controls, and explicitly separate observation/execution adapters. Do not give the cloud planner unrestricted phone input or universal app access.

This revision supersedes the earlier blanket exclusion of all cross-app interaction. It does not remove Android protections, platform/service rules, or the earlier restrictions on secrets, high-impact actions, arbitrary code, and self-promotion.

---

# Part I — Explain it simply

## 1. As if I were two

Your phone has lots of little rooms. Each app is a room.

The helper can go into the rooms you choose and use the tools it is allowed to use.

You can say: **“Use this room. Do this little job. Do not send anything.”**

Some rooms have locked doors. The helper cannot break the locks.

If you say **“My turn,”** the helper stops giving new instructions and lets you take over. It waits until you ask it to continue.

It can do some little jobs without internet. For bigger thinking, its online helper must be awake and available.

**You choose the rooms and the job. The helper handles the allowed steps. You keep the keys.**

## 2. As an adult

We are no longer designing only an app that reads reports and opens notebooks. We are designing an assistant that can coordinate supported tasks across other apps on your own phone.

That adds four major requirements:

1. **Per-app control:** Know which actions each selected app actually exposes.
2. **Per-task scope:** Enforce what the agent may read, change, send, and spend.
3. **Local safety enforcement:** The phone—not the AI's promises—decides whether each action may execute.
4. **Reliable handover:** Stop new actions when you take control, and never resume silently.

The correct promise is not “control every app.” It is: **perform supported, authorized actions in the apps you select, using permitted mechanisms, and hand back control when a step cannot safely or legitimately be completed.**

---

# Part II — What can work and what cannot be promised

## 3. Android capability is not universal authority

Android provides intents for requesting another app to perform an exposed action, such as opening content or preparing a task. Available behavior depends on the receiving app and the device. [3](https://developer.android.com/training/basics/intents)

Android also exposes accessibility mechanisms that can inspect provided UI structure and perform supported clicks, scrolling, or gestures when the necessary service capabilities and user authorization are available. That technical ability is not a universal permission or a guarantee that a particular app can be operated reliably. [26](https://developer.android.com/guide/topics/ui/accessibility/service)

Apps remain isolated by Android's security model; installing our APK does not grant access to every other app's private data or protected APIs. [7](https://source.android.com/docs/security/overview/app-security)

### The important distribution constraint

Google Play's current AccessibilityService policy prohibits using that API to enable a general-purpose app to autonomously initiate, plan, and execute actions or decisions. It distinguishes this from deterministic automation following a static, human-defined script. Verified disability-focused accessibility tools have a limited exception for their genuine accessibility purpose; a general assistant cannot simply declare itself one. [1](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)

Consequences for this design:

- Supported API/intent-based agent actions are the primary route. The cited restriction is specifically about Accessibility API use, not a blanket prohibition on every form of AI-assisted app integration.
- A scope screen, visible indicator, approval button, or emergency stop does not by itself make autonomous Accessibility-based UI driving eligible for Google Play.
- Do not treat “the model generated a script and the user approved it” as an automatic exception for static, human-defined automation.
- An AI planner must not indirectly select or initiate Accessibility recipes in a Play candidate and call that a loophole.
- Personal installation has a different distribution context, but does not remove Android restrictions, account rules, consent requirements, or protected-screen boundaries.
- Do not mislabel the product as a disability accessibility tool, conceal its purpose, disable protections, or build a policy-bypass path.

### My distribution decision

Maintain one codebase with distinct capability profiles:

1. **Standard prototype:** API/intent integrations, browser handoffs, selected-data analysis, and explicitly permitted human-defined routines. No general AI-controlled Accessibility runner enabled.
2. **Optional personal-device lab:** A separate, clearly labeled experimental build/profile for testing additional UI interaction only when technically available and permitted in that deployment. It is not automatically activated or represented as Play-compliant. Device eligibility, disclosure, actual permissions, and safety tests are prerequisites.

The experimental profile is not a promise that full AI UI automation will work on the owner's phone. If the device or applicable rules prevent a capability, show it as unavailable instead of supplying a workaround.

## 4. Per-app capability coverage

| Coverage state | Meaning |
| --- | --- |
| **API actions supported** | The app/service exposes an authorized interface for the particular actions implemented by our adapter |
| **Open / prepare / share supported** | We can invoke a supported intent, link, share sheet, or app-provided entry point; final completion may remain manual |
| **Fixed routine conditionally supported** | A human-defined routine can run only in a permitted configuration and after its preconditions pass |
| **Experimental UI test** | Additional interaction is under device-specific testing in the separate personal lab; not a default or universal feature |
| **Manual handoff required** | The assistant can explain or prepare, but you perform the protected or unsupported step |
| **Unavailable / blocked** | The required interface, permission, policy allowance, account access, or reliable observation is absent |

Installed-app discovery is itself scoped. Android filters package visibility and provides mechanisms to declare specific interaction needs. Prefer a user-selected app list and narrow discovery rather than collecting the entire app inventory. [14](https://developer.android.com/guide/topics/manifest/queries-element)

The registry stores the app identity, supported action types, adapter version, tested prerequisites, account mapping where available, and current permission state. It must not infer authority from an app being installed or from a familiar display name.

### Illustrative task coverage

- **Documents:** Work with a user-selected file or folder through a permitted file interface; do not read another app's private database.
- **Calendar/alarm workflows:** Use exposed intents or authorized APIs; prepare an event or perform a specifically authorized supported action.
- **Messaging:** Prepare an allowed draft; sending needs the correct recipient, content, attachments, account, and confirmation/authority.
- **Browser:** Open approved content, follow supported routes, and report actual observations. Opening Chrome does not grant control of all Chrome tabs.
- **Cloud development:** Continue the existing supported GitHub/Kaggle/Colab adapters and their account/runtime gates.
- **Banking, payments, credentials, identity, and security settings:** Manual-only protected workflows in the first release. Opening an app for the owner is not permission to inspect or operate its sensitive screens.

These are proposed categories, not a tested compatibility list. We will publish actual coverage only after device/app tests.

## 5. Action selection order

Use the least powerful adequate method:

```text
Authorized app/service API
    -> Supported Android intent / verified app link / share interface
    -> Permitted fixed human-defined routine
    -> Explicit manual handoff
```

Any experimental UI runner is a separate gated route, not an automatic fallback after permission failure. A service denying an operation is not a reason to try to bypass that denial through its UI.

Do not expose an unrestricted `tap anything` or `read every screen` tool to the standard cloud agent. The standard planner proposes typed business actions, and the local broker validates them.

---

# Part III — How owner control works

## 6. New architecture

```text
OWNER GOAL + SCOPE
        |
        v
SHARED AGENT PLANNER
  Colab AI when genuinely available;
  known local workflows or optional tested local AI otherwise
        |
        v
ON-DEVICE SCOPE / SAFETY BROKER
  App + account + data + action + time + risk + session checks
        |
        +--> API adapter
        +--> Android intent / app-link adapter
        +--> Permitted fixed-routine runner
        +--> Optional personal-lab UI runner, if eligible
        +--> Manual handoff
        |
        v
OBSERVE RESULT -> VERIFY -> RECORD -> CONTINUE OR STOP
```

The on-device broker cannot be replaced by a prompt telling the model to be careful. The model, a web page, an incoming message, or a notebook cannot grant a permission or edit the broker.

### New Phone Control modules

- **App Capability Registry:** Selected apps and action-level support.
- **Scope Editor:** Owner-selected permissions, data boundaries, constraints, and expiry.
- **Control Session Manager:** Sole active control lease, owner, cancellation epoch, and lifecycle.
- **Action Router:** Chooses an implemented permitted interface, not an arbitrary workaround.
- **Observation Adapter:** Accesses only the selected, consented context and reports when it cannot see enough.
- **Critical Action Guard:** Handles irreversible/externally consequential actions and manual-only categories.
- **Takeover Controller:** Stops new automated work and prevents stale commands from resuming it.
- **Audit and Privacy Service:** Records minimal real events without becoming an always-on phone recorder.

These are software modules, not eight new LLMs. The original research organization and four job profiles remain.

## 7. Scope is a concrete task contract

All apps begin **Off** for control. The owner selects capabilities; “technically possible” never means “already authorized.”

For a task, record:

- Goal and permitted end state.
- Allowed app identities and accounts.
- Allowed actions in each app.
- Allowed files, records, folders, recipients, and data categories.
- Whether any selected data may be sent to cloud AI.
- Whether a supported action may complete automatically or must stop at a draft/confirmation.
- Time, action-count, cost, and retry limits.
- Expiry, cancellation conditions, and manual-only exclusions.

### Example scope

> Read the documents in this selected folder, create a summary in my chosen notes destination, and prepare a calendar event. Do not delete originals, send messages, publish content, change accounts, or inspect other apps. Do not upload private document content unless I separately permit that transfer.

Initial phone-control default: **one owner-started task, at most five minutes or twenty actions**, whichever ends first. These are proposed execution caps, separate from the existing 30-minute build-session limit. The owner can set stricter limits; Android and policy restrictions cannot be overridden by an app scope.

A grant for reading a document is not a grant to send it to Colab. A grant for preparing a message is not a grant to send it. A grant for one account is not a grant for another.

The first sensitive-action default is to show an exact preview and require a fresh, scoped confirmation. Payments, password/OTP entry, biometric authentication, permission grants, and protected security settings remain manual in the initial release.

## 8. Take over must work locally

Provide **Take Over**, **Pause**, and **Stop** in the app and an appropriate visible control-session notification. Start input automation only when its required stop/visibility controls are available.

When the owner takes over:

1. The local session changes to **Human Control**.
2. Its control lease/cancellation epoch changes immediately.
3. Queued and late-returning commands from the old epoch are rejected.
4. New automated input and new observation/uploads stop.
5. Cancel in-flight work where the platform permits it.
6. Resume only after explicit owner action and a fresh state check.

Do not claim to detect every finger touch in every app: ordinary apps do not have a universal reliable global touch hook. Use the explicit takeover controls as the dependable path, and pause on detected manual interference or unexpected navigation where those signals are legitimately available.

Never fight user navigation, obscure the stop controls, prevent disabling/uninstalling the service, or silently reactivate after a crash/reboot. Stop on lock, revoked permission, unexpected app/account, lost observation, expired scope, or ambiguous target. A changed screen invalidates stale planned input.

Stopping cannot undo an already accepted message, upload, transaction, or other external action. Such actions require their own acknowledgment and recovery handling.

## 9. Observe only what is permitted

Observation is separate from action authority. Prefer app-provided structured data or content the owner explicitly shares. Do not start with whole-phone recording.

If screen capture is genuinely needed, use the permitted session-based Android mechanism with its required disclosure, service configuration, and consent. For apps targeting Android 14+, MediaProjection capture requires consent for each capture session; capture authority is not permission to inject touches. [2](https://developer.android.com/about/versions/14/behavior-changes-14)

Secure-window protections can prevent screenshots and non-secure display output. Respect protected content and stop rather than attempting another capture path to defeat that protection. [13](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)

The initial design excludes observation of banking/payment, password-manager, authenticator, lock-screen, permission-grant, and identity-verification workflows. Unknown app contexts default to handoff, not capture-first/classify-later.

The existing built-in Colab examples are text-oriented. Do not assume a universal screenshot-to-action vision API. If a future permitted workflow needs local OCR, that is an explicit adapter with its own consent, accuracy, and redaction checks. [5](https://colab.research.google.com/github/googlecolab/colabtools/blob/main/notebooks/Getting_started_with_google_colab_ai.ipynb)

Default cloud transfer of phone-screen content is **Off**. Only owner-approved, minimized context may leave the phone. Screenshots are not kept by default; audit logs contain minimal action metadata rather than continuous screen contents. When sensitive-data filtering is uncertain, hand off rather than upload.

## 10. Verify after every action

Before a step, check the current scope, session epoch, actual target app/account, current supported screen/state, risk category, and data authority. After it, verify the expected result using a real callback, artifact, receipt, or observable state.

A launched activity is not a completed task. A successful tap callback is not proof a message was sent correctly. A visible button is not evidence the owner authorized its effect.

Do not blindly retry a possible Send, Delete, Purchase, or Submit action after an ambiguous response. Record **Uncertain External Outcome** and reconcile or ask the owner. Limit harmless retries and stop on unexpected layouts, localization, app updates, keyboard changes, or insufficient observations.

Screens, documents, notifications, messages, and web pages are untrusted input. Their text cannot authorize a new target, a data transfer, or a permission change. The planner's role is to interpret the owner's goal, not obey every instruction it sees.

The Improvement Lab may improve task procedures under fixed evaluation, but cannot increase phone access, rewrite the local guard, or promote new high-risk UI recipes without review.

## 11. Online and offline phone control

| Mode | Permitted design |
| --- | --- |
| Offline, no local model | Known local functions, supported intents, owner-defined eligible routines, saved knowledge, and waiting jobs |
| Offline, tested optional local model | Additional bounded local reasoning, but exactly the same scope and safety broker |
| Online, Colab available through a verified route | New supported plans based on permitted context; all physical phone actions still pass the on-device broker |
| Online, Colab unavailable | Known local workflows or manual handoff; no invented cloud response or silent alternate provider |
| Phone locked or owner control withdrawn | Pause interactive control; do not bypass the lock or restart old commands |

Internet does not remove app restrictions. Offline operation does not remove them either. Some receiving apps inherently require the network, and no local runner can make an online-only action succeed without it.

## 12. What changes in the interface and build plan

Add **Phone Control** under Agents and a **Control Center** accessible from Home and the active-session notification. The Control Center shows selected apps, allowed actions, data sharing, active scope, remaining budget, current actor, and takeover controls.

Actual other apps run as their own Android apps. They do not all become embedded views inside our APK. The assistant may navigate to a real app, while the session status remains visible through supported controls.

### Work that remains useful

Keep the native app foundation, encrypted local storage, offline knowledge, agent drafts, task queue, service adapters, notebook, and research/verification organization.

### Work that needs revision

Strengthen the permission broker, task schemas, local cancellation, observation boundary, critical-action previews, capability registry, audit data minimization, device tests, and distribution documentation. Browser control is one adapter—not a universal route to all phone apps.

### Revised build count

Expand the initial plan from **30 to 42 small timeboxed divisions**. The additional twelve cover:

1. Device/distribution feasibility.
2. Selected-app capability registry.
3. Local per-app scope contract.
4. Takeover/cancellation lease.
5. One harmless intent/API action.
6. Consented observation boundary.
7. Critical-action guard.
8. Deterministic routine engine.
9. Conditional personal-lab UI runner.
10. Privacy/prompt-injection tests.
11. Online/offline control routing.
12. Harmless cross-app integration testing.

The separate build-plan file is renumbered in dependency order. Every session still stops within 30 minutes, including waiting, verification, and checkpointing. Unfinished work becomes another small continuation. Forty-two slots are an initial decomposition, not a promise to finish universal phone control in 21 hours.

## 13. Tests before enabling phone input

- An unselected app receives no input and no observation grant.
- A user-defined scope cannot override an Android or service restriction.
- A stale cloud response cannot act after takeover, scope expiry, or account/app change.
- A denied permission remains denied; the assistant never approves its own Android permission prompt.
- Protected/unknown/sensitive screens stop observation and execution as required.
- Screen-reading consent does not enable action authority or cloud upload.
- A malicious page/message cannot change the destination, recipient, or permitted tools.
- Fixed human routines are not represented as an exemption for general AI-driven Accessibility automation.
- API/intent operations, experimental UI operations, and manual handoffs have different verified capability states.
- A possible irreversible action is not retried blindly after interruption.
- Crash, restart, lock, or permission withdrawal does not reactivate an interactive control session.
- Tests begin with synthetic data and an owner-controlled test app, then a few harmless supported workflows. Mocks/emulators do not establish real-phone compatibility or Play approval.

**Readiness verdict:** The foundation remains ready for phased implementation when requested. Broad phone-control claims remain gated on policy, device capability, actual permissions, and safety tests. Still designing now; no implementation has started.

---

# Sources and scope notes

Official materials checked on 23 October 2026. Platform facts are cited; architecture, default caps, profiles, and division counts are design decisions. No actual phone or account was tested.

- Google Play Accessibility API automation and eligibility rules: [1](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- Android accessibility service capabilities: [26](https://developer.android.com/guide/topics/ui/accessibility/service)
- Supported inter-app interaction through intents: [3](https://developer.android.com/training/basics/intents)
- Android app security and limited resource access: [7](https://source.android.com/docs/security/overview/app-security)
- Scoped package visibility declarations: [14](https://developer.android.com/guide/topics/manifest/queries-element)
- Android 14 screen-capture session consent: [2](https://developer.android.com/about/versions/14/behavior-changes-14)
- Secure-window behavior: [13](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- Colab built-in AI examples: [5](https://colab.research.google.com/github/googlecolab/colabtools/blob/main/notebooks/Getting_started_with_google_colab_ai.ipynb)
