# 4CBOn2 Android Prototype — Colab Entry and Control Route

**Build division:** 10 — Prove Colab entry/control route  
**Status:** Checkpointed — source boundary and public-route investigation recorded; Android toolchain restoration is required before compile/test verification  
**Date:** 23 October 2026

## Like you are two

We found the Colab house door.

But the special notebook we want is not inside the GitHub box yet, so the door says, “Notebook not found.”

The phone is only allowed to show you the door later. It cannot secretly press Colab’s buttons or make a computer run by itself.

## Like you are an adult

Division 10 separates three different capabilities that must not be conflated:

1. **Opening a fixed notebook URL in a browser** — a future owner-controlled manual handoff only.
2. **Controlling a Colab runtime from the Android app** — no supported/verified API was found; unavailable in this prototype.
3. **Calling `from google.colab import ai`** — an in-notebook Python capability to be tested later in Division 30; it is not an Android or remote-runtime control API.

No browser was launched by the app. No Google account, Colab session, GitHub authorization, cookie, notebook secret, runtime, prompt, or service credential was used.

## Exact future notebook identity

```text
Repository: mohamtur1/4CBOn2
Branch: main
Notebook: 4CBOn2_Kaggle6.ipynb
GitHub page: https://github.com/mohamtur1/4CBOn2/blob/main/4CBOn2_Kaggle6.ipynb
Colab open route: https://colab.research.google.com/github/mohamtur1/4CBOn2/blob/main/4CBOn2_Kaggle6.ipynb
```

The Android source now holds this exact fixed reference in `ColabNotebookReference.kt`. It is navigation metadata only: it cannot start an activity, make a network request, sign in, create a notebook, invoke a runtime, or call an AI model.

## Public-route investigation

A public request to the fixed Colab route returned a Colab error page stating:

```text
Notebook not found
Could not find 4CBOn2_Kaggle6.ipynb in
https://api.github.com/repos/mohamtur1/4CBOn2/contents/?per_page=100&ref=main
```

This is evidence that the planned notebook was not publicly available at that repository/branch path during this check. It is not evidence of an account, a private notebook, or runtime-control failure; no sign-in or GitHub authorization was attempted.

Google’s Colab FAQ documents that notebooks can be loaded from GitHub and are normally stored in Google Drive. It also states that runtime VMs are private to an account, deleted after idle time, and subject to maximum lifetimes and availability limits. [Colab FAQ](https://research.google.com/colaboratory/faq.html)

## Control-path decision

| Requested capability | Actual Division 10 state |
| --- | --- |
| Fixed notebook identity | Recorded in source |
| Later browser opening of that fixed URL | Manual handoff only; no launch code yet |
| Generic web/Chrome control | Not implemented and not claimed |
| Android-to-Colab runtime control | No supported/verified route; unavailable |
| Remote shell/desktop workaround | Prohibited; not attempted |
| `google.colab.ai` model list/generation | Not attempted; belongs to Division 30 inside an interactive Colab notebook |
| Notebook creation | Not performed; belongs to Division 29 |
| Runtime creation/execution | Not performed |

The later Browser/Chrome division may add an explicit user-tapped, fixed-URL Custom Tab/browser handoff. That is still not browser automation or evidence that the notebook is available.

## Colab built-in AI boundary

Current official Colab material provides examples such as:

```python
from google.colab import ai
models = ai.list_models()
response = ai.generate_text("Example prompt")
```

The library runs within a Colab notebook and its availability can depend on the user/session/eligibility. It must not be replaced silently with another model provider, copied into the Android app as a credential, or represented as a remote phone-control API. [Colab AI getting-started notebook](https://colab.research.google.com/github/googlecolab/colabtools/blob/main/notebooks/Getting_started_with_google_colab_ai.ipynb)

## Source boundary added

| File | Purpose |
| --- | --- |
| `app/src/main/java/io/github/mohamtur1/fourcbon2/ColabNotebookReference.kt` | Fixed notebook/Colab URLs and explicit capability states |
| `app/src/test/java/io/github/mohamtur1/fourcbon2/ColabNotebookReferenceTest.kt` | Intended future compile-time/unit checks for exact identity and manual-only status |

The only exposed control capability is:

```text
MANUAL_BROWSER_HANDOFF_ONLY
```

The source explicitly records:

```text
PROGRAMMATIC_RUNTIME_CONTROL_UNAVAILABLE
BUILT_IN_AI_UNPROBED
```

## Verification status

| Check | Result |
| --- | --- |
| Public fixed Colab URL fetch | Completed; reported notebook not found |
| Fixed source URL/name inspection | Completed |
| Static source boundary scan | Passed: fixed URL/name, manual-only status, unavailable runtime/AI states, and no browser launch/network/credential code |
| Android Kotlin compile after source addition | Passed after verified local toolchain restoration |
| Division 10 unit tests | Passed; aggregate project result: 15 tests, 0 failures, 0 errors, 0 skipped |
| Debug APK after source addition | Produced and v2 signature verified; SHA-256 below |
| Emulator/physical-device browser handoff | Not run |
| Colab account/session | Not used |
| `google.colab.ai` runtime probe | Not run |

The current generated verification APK has SHA-256:

```text
e3250cd2c8aa86bd910e73575ce59f074618cccecf9219f5b1762e9de5169e07
```

It is a debug-signed local verification artifact, not a retained release artifact or device-installed test. The prior Division 09 generated APK hash was `4f96fcab154496e9183859ec2dc469744ca1b2658194e69ce5da2ef9ba8f7884`.

## Toolchain-restoration note

The original nonpersistent cache had disappeared. It was restored from verified sources using Temurin JDK 17.0.21+9, Android command-line tools 23.0.0, Android platforms 36/37.0, Build Tools 36.0.0, platform-tools 37.0.1, and Gradle 9.4.1. The JDK, command-line-tools, and Gradle archive checksums matched the existing provisioning record.

The initial restored build exposed an insufficient 512 MiB heap / 192 MiB metaspace configuration. The exact successful build used one worker, 640 MiB heap, and 384 MiB metaspace; those bounded settings are now recorded in `gradle.properties`. This is a build-environment reliability adjustment, not an app capability change.

## Completion check

- [x] Exact notebook and Colab URL are fixed in source.
- [x] Public Colab route was investigated without authentication or data retention.
- [x] Missing notebook outcome is recorded honestly.
- [x] Manual browser handoff is separated from runtime control.
- [x] No unsupported remote-control, proxy, credential, or provider-substitution path was added.
- [x] Kotlin compile, aggregate 15-test suite, debug APK build, v2 signature, and static boundary checks passed after toolchain restoration.

**Next eligible division:** 11 — Local database.
