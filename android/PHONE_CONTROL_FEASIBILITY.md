# 4CBOn2 Android Prototype — Phone-Control Feasibility Gate

**Build division:** 02 — Phone-control feasibility and policy gate  
**Status:** Finished as a documented feasibility gate; no device, app, account, permission, or store submission has been tested  
**Date:** 23 October 2026

## Decision

Proceed with the Android prototype only as a **selected-app, scope-limited assistant**. The standard product route is official service APIs, Android intents/app links/share flows, local file interfaces, and clear manual handoffs.

Do not promise universal control over every installed app. Do not use policy, root, overlays, or alternate execution paths to defeat an app, Android, or service-provider restriction.

## Like you are two

We checked the helper’s rules.

It can use the doors that the phone and each app safely open for it.

It cannot pick locks or look in secret rooms.

If it cannot use a door safely, it tells you: “Your turn.”

## Like you are an adult

Phone control has four separate questions. A Yes to one does not mean Yes to all:

1. **Technical:** Does Android/the other app expose an interface for this action?
2. **Authorized:** Has the owner selected the app, account, data, and action in the active scope?
3. **Permitted:** Do Android, the receiving service, and the selected distribution route allow this use?
4. **Verifiable:** Can the prototype observe enough real result to know whether it completed safely?

The action is available only when all four are true. Otherwise it is a prepared handoff, manual step, or unavailable.

## Feasibility matrix

| Route | Standard prototype decision | Evidence required before claiming it works | Current state |
| --- | --- | --- | --- |
| Official service/app API | Primary route for a specifically implemented adapter | Owner authorization, documented scope, actual connection, request/result test, error/revocation test | Unverified; GitHub/Kaggle/Colab checks occur later |
| Android intent, App Link, Custom Tab, share sheet, file picker | Primary route for opening, selecting, preparing, or handing off work | Receiving handler/device behavior and actual returned or observable result | Unverified; first harmless action is Division 21 |
| Local files/local analysis | Permitted inside app-owned storage and user-selected content | Local fixtures, restart behavior, data-boundary tests | Deferred to later divisions |
| Fixed, human-defined routine | Conditional and narrowly bounded; exact human-authored steps, trigger, preconditions, target, stop conditions, and verification required | Device-specific test against owner-controlled fixture only; review of current deployment rules | Deferred; does not make general AI UI automation eligible |
| General AI-directed Accessibility UI automation | **Not in the Standard prototype** | Not applicable for Standard distribution; scope and Stop controls do not override policy | Blocked in Standard profile |
| Personal-device experimental UI test | Separate optional lab only; default Off, owner-controlled fixture app/synthetic data first | Actual device/permission behavior, legitimate deployment review, protected-screen stop behavior, takeover test | Deferred/conditional; not Play-approval proof |
| Screen capture/observation | Structured/shared data first; capture is exceptional and separately consented | Per-session consent, foreground visibility/configuration where required, data minimization, secure-screen handling | Deferred; no capture permission requested |
| Banking/payment/password/OTP/biometric/identity/permission/security screens | Manual-only in the first prototype | No test is planned for automation | Blocked |

## Distribution gate

### Standard profile

The Standard profile must remain compatible with the stated Android/Google Play constraints:

- Prefer narrow APIs and supported Android interfaces instead of broad device control.
- Treat each installed app as private unless the owner selects it and Android/that app exposes an allowed route.
- Never claim that scope consent makes a prohibited or unsupported operation allowed.
- Do not identify the product as a disability accessibility tool unless its genuine primary purpose and qualifying evidence support that status.
- Do not include a hidden general UI-driving fallback after an API/intent/action is denied.

Google Play’s AccessibilityService guidance says automation actions must have a narrow, clearly understood purpose and prohibits using that API for a general app to autonomously initiate, plan, and execute actions or decisions. It distinguishes deterministic behavior based on a static human-defined script, and gives a limited accessibility-tool exception only where helping people with disabilities is the genuine core purpose. [Google Play policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)

### Personal-device lab profile

The personal-device lab is a proposed test profile, not a distribution loophole. It remains disabled unless all listed prerequisites have been tested. It must:

- Show an unmistakable experimental label and active-session indicator.
- Start with an owner-controlled fixture application and synthetic data.
- Use explicit owner enablement, a specific app/action scope, short expiry, Take Over/Pause/Stop controls, and no automatic resume.
- Stop on unknown/protected/sensitive screens, lost observation, lock, account mismatch, owner cancellation, or revoked permission.
- Never bypass Android security, service restrictions, `FLAG_SECURE`, login/identity safeguards, or app-specific protections.
- Remain unavailable if the device, app, policy route, or required permission makes it ineligible.

A working local test does not establish compatibility with all devices, all apps, or Play Store acceptance.

## Android technical constraints recorded before implementation

- **App isolation:** Android’s application sandbox isolates app resources; an ordinary app cannot read or change another app’s private data simply because it is installed. [Android app security](https://source.android.com/docs/security/overview/app-security)
- **Inter-app handoffs:** intents request an action from a component that chooses to handle it; the receiving app/device determines whether it can handle the request. [Android intents](https://developer.android.com/training/basics/intents)
- **Package visibility:** installed-app discovery is limited on modern Android. The app will prefer owner-selected apps/narrow manifest declarations instead of a blanket installed-app inventory. [Package visibility](https://developer.android.com/guide/topics/manifest/queries-element)
- **Accessibility capability is not universal authority:** the API can expose UI actions/gestures where an enabled service has declared the necessary capabilities, but that does not establish legal, reliable, policy-compliant, or safe product use. [Accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service)
- **Screen capture:** for apps targeting Android 14+, screen-capture consent is requested for each MediaProjection session. Capture consent is not touch-input authorization. [MediaProjection](https://developer.android.com/media/grow/media-projection)
- **Protected content:** secure windows can block screenshots/non-secure display. The prototype must respect that boundary and hand back control rather than trying another capture path. [FLAG_SECURE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- **No universal touch detector:** the agent will rely on explicit Take Over/Pause/Stop control and safe state checks; it must not claim it can infallibly recognize every owner touch in every other app.

## Local control invariants

These invariants become implementation tests later; they are not yet code:

1. The planner, a website, a message, a notebook, or an agent-generated text cannot grant phone permissions or increase scope.
2. The local broker requires current app, account, action, data, risk, expiry, action-count, and cancellation checks before execution.
3. A Take Over/Stop event changes the local cancellation epoch; stale queued or returning cloud commands are rejected.
4. A denial by an API, app, or system is final for that route. The action does not silently fall back to broad UI automation.
5. Observation permission is separate from action permission; cloud transfer is separate from both.
6. Irreversible/externally consequential actions require distinct receipt/uncertainty handling and are never blindly retried.
7. Sensitive categories are manual-only in the first prototype.
8. The app does not request, capture, store, or transmit screen contents by default.

## Explicitly unverified / blocked today

- Android SDK, Gradle, Java version, emulator, and physical-device availability have **not** yet been inspected; that is Division 03.
- No Android project source exists yet.
- No ordinary Android permission, accessibility setting, notification permission, package query, screen-capture consent, or foreground service has been requested.
- No phone model, Android version, installed app, account, or receiving app integration has been tested.
- No Google Play Console account, declaration, or distribution review exists.
- GitHub, Kaggle, Colab, Chrome, and repository connections remain untested. No PAT is needed for this division.
- `4CBOn2_Kaggle6.ipynb` still does not exist.

## Division 02 completion check

- [x] Standard, personal-lab, manual-only, and blocked routes are separated.
- [x] Accessibility and distribution constraint is explicitly recorded.
- [x] No bypass path is proposed for a denied/protected route.
- [x] Per-session screen capture and secure-content boundaries are recorded.
- [x] Device/account/store claims remain unverified until real tests.
- [x] Next technical prerequisite is identified.

**Next eligible division:** 03 — Prepare Android build tools.
