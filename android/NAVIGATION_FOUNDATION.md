# 4CBOn2 Android Prototype — Navigation Foundation

**Build division:** 06 — Add navigation  
**Status:** Finished  
**Date:** 23 October 2026

## Like you are two

The helper house has five doors now:

- Home
- Ask
- Agents
- Browser
- Activity

The Ask room has a button for questions and a button for “How do I?” lessons.

The buttons are sleeping until we build their real work. The helper says they are not ready instead of pretending.

## Like you are an adult

The application now has an in-process Compose navigation foundation driven by a small `AppDestination` model. It provides five visible mobile destinations: **Home, Ask, Agents, Browser, and Activity**. This is deliberately local UI state, not a claim of persisted navigation, a background service, an agent router, or cross-app control.

## Implemented UI behavior

| Destination | Actual current behavior |
| --- | --- |
| Home | Shows an honest foundation status and explains that unfinished functions remain unavailable |
| Ask | Reserves visible entry points for **Ask a Question** and **How do I? tutorial**; both controls are disabled and labeled “coming soon” |
| Agents | States that profiles, scopes, and task drafts are not built yet |
| Browser | States that no browser workspace, sign-in handoff, or phone/browser control is active |
| Activity | States that no agent actions have run and describes the future real-event timeline |

The bottom navigation changes the visible local screen. It does not invoke an Android browser, account, API, phone automation, or background operation.

## “How do I?” commitment preserved

The Ask screen now visibly reserves the requested tutorial experience. It will later support explanation, guided safe steps, and prepared handoffs as defined in the prototype scope. In this build it has no data source, no live AI, no local knowledge search, and no app-action route, so its button is disabled rather than producing invented guidance.

## Source added/changed

- `app/src/main/java/io/github/mohamtur1/fourcbon2/AppDestination.kt` — Five destination model.
- `app/src/main/java/io/github/mohamtur1/fourcbon2/MainActivity.kt` — Bottom navigation and truthful destination screens.
- `app/src/test/java/io/github/mohamtur1/fourcbon2/AppDestinationTest.kt` — Navigation-model unit test.
- `README.md` — Current compile/target SDK distinction retained.

No Android manifest permission, service, receiver, provider, network adapter, account adapter, credential store, accessibility service, screen capture, browser intent, or other cross-app integration was added.

## Verification performed

| Check | Actual result |
| --- | --- |
| Kotlin/Compose compile | Passed |
| `:app:assembleDebug` | Passed |
| `:app:testDebugUnitTest` | Passed: 1 test, 0 failures, 0 errors, 0 skipped |
| Navigation model | Verified expected labels: Home, Ask, Agents, Browser, Activity |
| Source manifest inspection | No `<uses-permission>`, service, receiver, or provider declaration added |
| Generated verification APK signature | Debug APK v2 signature verification passed |
| Emulator / physical device | Not installed or tested |

The temporary generated verification build for this source state had SHA-256:

```text
2971f64d58dfc33d26674357f231a10e08b1a3eccc1c3b59c90ad0c37e004077
```

## Artifact distinction

The retained root artifact `4CBOn2-debug.apk` is still the formal **Division 05** foundation-only checkpoint, SHA-256:

```text
b568e5a46b9fd80e9d8c64165418479e065205e10c645af481e2287f4ee9dc5e
```

It predates this navigation source update and therefore does **not** contain the Division 06 navigation UI. The current navigation APK exists only as ignored local build output for verification. A new retained APK will be intentionally created only in a later artifact/release division, rather than silently replacing the documented Division 05 checkpoint.

## Still not implemented

- Actual Ask answers, local search, or Colab answers.
- How-do-I tutorial plans, Guide me, or task scope handling.
- Agent profiles, local database, queue, files, or knowledge library.
- Browser, Custom Tabs, GitHub, Kaggle, Colab, or authentication.
- Phone-control permissions, accessibility service, screen capture, or app capability discovery.
- Emulator/real-device UX testing and accessibility testing.

## Completion check

- [x] All five planned top-level destinations are visible.
- [x] Ask a Question and How do I? entry points are visible and honestly disabled.
- [x] Every unfinished destination names its unavailable state.
- [x] A real navigation-model unit test passes.
- [x] Debug package build/signature checks pass for this source state.
- [x] No new permission, account, service, or phone-control capability was added.
- [x] Old retained APK versus new source/verification build distinction is recorded.

**Next eligible division:** 07 — Add protected credential storage boundary.
