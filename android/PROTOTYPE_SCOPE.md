# 4CBOn2 Android Prototype — Frozen Scope

**Build division:** 01 — Freeze expanded prototype scope  
**Status:** Finished  
**Date:** 23 October 2026  
**Implementation state:** Scope artifact only. No Android project source, APK, notebook, account connection, credential, or live service test has been created.

## Purpose

Build one native Android companion that is useful offline, connects to approved services when genuinely available, and coordinates selected app actions only within an owner-defined scope.

The product is not promised to control every installed app. It supports verified, authorized actions in selected apps through the least-powerful adequate mechanism: official service API, Android intent/app link/share interface, permitted fixed routine, or an honest manual handoff.

## First-prototype user surfaces

1. **Home** — current connection state, queued work, active tutorial/control session, and safe shortcuts.
2. **Ask** — two user intents:
   - **Ask a Question:** answer from saved evidence offline; use verified Colab planning only when online and permitted.
   - **How do I…?:** explain how to accomplish a task with the assistant, a selected phone app, Colab, Kaggle, GitHub, or manually.
3. **Agents** — saved agent profiles, task drafts, scopes, and permitted actions.
4. **Browser** — Custom Tab/service handoffs plus the separately restricted browser workspace.
5. **Activity** — real plan/action/observation/check events, timestamps, blockers, results, and Take Over/Pause/Stop state.
6. **Control Center** — selected apps, action capability states, data-sharing settings, active scope, time/action budget, and cancellation controls.

Unimplemented surfaces must be visibly labeled unavailable or disabled. They must not simulate service availability or imply that an action happened.

## “How do I…?” tutorial contract

### What it does

A tutorial request is converted into a **Tutorial Plan**, not directly into unbounded phone control. The plan must show:

- The stated goal in plain language.
- Whether the answer comes from saved guidance, a live supported source, or a manual explanation.
- The selected app/service and account only when relevant and authorized.
- Capability state: API action, open/prepare/handoff, fixed routine, experimental/unavailable, or manual-only.
- Prerequisites: sign-in, connection, app version, permissions, selected files, or owner approval.
- Ordered, short steps.
- Which steps the app can perform, which need the owner, and why.
- Expected observable result and recovery path.
- A warning when instructions may be stale or cannot be verified.

### Tutorial modes

| Mode | Behavior |
| --- | --- |
| Explain | Present short written steps and an evidence/source or capability basis. It does not operate another app. |
| Guide me | Start a temporary scoped session, execute only verified supported non-sensitive steps, show each actual result, and stop at an owner step, a protected screen, an unknown state, a scope expiry, or a failure. |
| Prepare | Create a permitted draft, file, link, checklist, or handoff. It does not send, publish, submit, delete, purchase, or grant permissions without the separately required authority. |

The tutorial engine uses the same local safety broker as task execution. A tutorial is not a loophole around app scope, sensitive-action controls, or Android/service limitations.

### Initial tutorial acceptance cases

1. Offline: “How do I open my saved research report?” returns local steps and identifies the report as saved/offline.
2. Connection-dependent: “How do I open my Colab notebook?” explains that browser opening may work while programmatic runtime/AI control remains separately unverified.
3. App selection: “How do I share this selected local file?” offers a supported Android sharing handoff and reports whether a receiving app is available.
4. Scope boundary: “How do I send this message?” may prepare the message, but must show the recipient/content preview and stop before a prohibited or unapproved send.
5. Unsupported/protected case: “How do I log in to my bank?” explains that the owner must use the bank app manually; it does not inspect, capture, or automate the sensitive workflow.
6. Takeover: during Guide me, Take Over immediately stops new automatic steps; a fresh owner action and state check are required to resume.

## Scope and safety baseline

- Control is Off for every other app until the owner selects a specific app and action scope.
- Default session limit: one owner-started task; five minutes or twenty actions, whichever occurs first.
- The cloud planner can propose typed steps. The phone-local broker alone checks app, account, action, data, risk, budget, expiry, and cancellation state before execution.
- A read grant does not grant cloud upload. A draft grant does not grant sending. One account does not grant another.
- The app stops on lock, permission loss, scope expiry, unexpected target/account, unknown/protected context, failed verification, or Take Over/Stop.
- The Standard prototype uses supported APIs, intents, links, share interfaces, and permitted human-defined routines. It does not claim a general AI-driven Accessibility UI runner is Google Play eligible.
- Protected credentials, one-time codes, biometrics, payment, banking, identity verification, Android permission grants, and security-setting changes are manual-only in the initial prototype.
- No root access, accessibility-policy workaround, security bypass, hidden overlay, or secret-screen capture is in scope.

## Data and credential baseline

- Local drafts, knowledge, queues, and allowed evidence are stored locally; sensitive local data requires encryption design backed by Android Keystore keys when implemented.
- Cloud transfer of phone screen content is Off by default. Data leaves the phone only through an explicitly authorized adapter and scope.
- No credential may be hard-coded, stored in source, included in a notebook export, or pasted in ordinary logs/chat.
- GitHub is initially read-only. The future credential route is a user-created, fine-grained GitHub PAT restricted to the intended repository and minimum permissions, entered locally at the actual connection step.
- **No PAT is required for Division 01.** Do not provide one now.

## Existing service decisions retained

- Future notebook: `4CBOn2_Kaggle6.ipynb`.
- Intended repository: `mohamtur1/4CBOn2`.
- Colab built-in AI remains the selected cloud inference interface. It must be probed in a real permitted Colab context; no silent alternate provider is allowed.
- Kaggle is used for bounded prepared workflows and requires actual competition/service-rule gates.
- Chrome-backed Custom Tabs provide sign-in/manual browser handoff; they do not provide unrestricted Chrome DOM/cookie control.

## First end-to-end target

A user asks: **“How do I summarize this selected local research file and prepare notes for my chosen destination?”**

The prototype must:

1. Explain what it can do offline and what requires a connection.
2. Ask for/validate the selected file and chosen destination scope.
3. Create a saved local summary/draft using the actually available local or verified cloud capability.
4. Show an action timeline and a preview.
5. Stop before any external send/publish/write that is not currently authorized.
6. Preserve enough evidence to show what happened and identify any manual next step.

## Deferred from the first prototype

- Universal control over every app.
- A guaranteed on-device LLM or full fresh generative AI without internet.
- A general autonomous AccessibilityService UI controller in the standard distribution profile.
- Always-on automation, unrestricted background activity, and automatic actions after reconnect.
- Live banking/payment/credential/identity/permission workflows.
- Guaranteed Colab runtime control, full phone-to-built-in-AI control, or any untested account integration.
- Production release, Play Store acceptance, and complete device compatibility.

## Division 01 completion check

- [x] Scope distinguishes supported selected-app actions from universal phone control.
- [x] Ask / How do I tutorial behavior and test cases are specified.
- [x] Take Over, scope, protected action, and data-transfer rules are stated.
- [x] Credential/PAT rule is stated: none needed or accepted now.
- [x] A first end-to-end target exists.
- [x] Deferred/unverified capabilities are listed.

**Next eligible division:** 02 — Phone-control feasibility and policy gate.
