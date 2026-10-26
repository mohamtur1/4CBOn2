# 4CBOn2 — GitHub-Scoped New-Thread Handoff

**Repository scope:** `https://github.com/mohamtur1/4CBOn2`  
**Assigned working branch:** `arena/0eb49904-4cbon2` (`main` fingerprints below are historical)
**Prepared:** 23 October 2026  
**Purpose:** Let a new thread continue only the 4CBOn2 Android prototype safely from the public repository record and its source checkpoint.

---

## Read this first

This is a repository-scoped continuation, not permission to modify the repository.

- Work locally on the assigned branch as requested by the owner. Repository access does not itself approve a push or other external write.
- Do not request, accept, use, or reproduce any credential, token, password, cookie, authorization code, or browser session in chat/source/logs.
- A GitHub PAT exposed earlier was revoked. It must never be repeated or searched for.
- The current Arena environment provides configured Git/GitHub tooling and an assigned working branch. Do not treat repository access as approval for an external write, and never request a pasted token as a workaround.
- Before every work step, state:

  ```text
  Upload needed? Yes or No
  Destination: [only if Yes]
  Files/data: [only if Yes]
  Reason: [only if Yes]
  ```

- Never upload/publish/send anything without explicit owner approval for that exact step.

## Public repository recovery set — verified

The following public repository files were checked by Git object fingerprint against the matching workspace copies on 23 October 2026:

| Repository-root file | Purpose | Public size | Match result |
| --- | --- | ---: | --- |
| `4CBOn2 — New-Thread Engineering.md` | Main detailed engineering handoff | 11,112 bytes | Exact match |
| `4CBOn2_Build_Divisions.md` | 42-division plan and current status table | 16,097 bytes | Exact match |
| `4CBOn2_Checkpoint_Div10_source_docs.zip` | Compact Android source/tests/docs checkpoint through Division 10 | 181,381 bytes | Exact match |

The ZIP contains the Android project source, Kotlin tests, Gradle wrapper/configuration, build/evidence records, handoff, and durable Markdown designs. It intentionally excludes generated caches/build output, SDK/JDK/Gradle tool installations, local machine paths, APK binaries, and all credentials.

The three verified Git blob fingerprints are:

```text
4CBOn2 — New-Thread Engineering.md
21f1a9e87f94f8ca56f7d90cce3f505cce49e7f2

4CBOn2_Build_Divisions.md
3af90b4e8958eca6fda8dd895a6a42f92a5645ca

4CBOn2_Checkpoint_Div10_source_docs.zip
e4cc4a5df540e4434228328c8a001c173d0727d8
```

These are historical public-object fingerprints from 23 October 2026. They do not represent the later manual-handoff source. Separately approved pushes have published revised 09/09b source and workflow commits through `d1be9aa3bfd15fcce30277e4f676e03326e29f2a` on `arena/0eb49904-4cbon2`; any newer evidence-edit commit must be identified by its own hash and approval record.

## Current source of truth and continuation order

1. Read **`4CBOn2 — New-Thread Engineering.md`** first.
2. Read **`4CBOn2_Build_Divisions.md`** next. Its detailed status table is the operational status; if any short summary line appears inconsistent, defer to the detailed status table and the engineering handoff.
3. Use **`4CBOn2_Checkpoint_Div10_source_docs.zip`** as the source/test/document snapshot if the new thread does not already have workspace access.
4. Revised Divisions 09/09b are **finished for manual source/build verification**. Former Division 09a token work is superseded and its active code has been removed. See `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md` for the CI receipt and separate device/competition limitations.
5. Division 09c recorded the installed Kaggle app opening the correct page before sign-in, followed by owner-performed sign-in, Cancel, and Back; no join/submission or credential sharing occurred. Confirm green CI for the wording-only correction before starting Division 11. No PR is required. Do not claim browser-specific or unreported subchecks.

## Current actual status

| Division | State |
| --- | --- |
| 01–08 | Finished and locally verified as documented. |
| 09 — Manual Kaggle competition handoff | **Finished for manual source/build verification.** Active API/token/OAuth code removed. Validated competition URL and explicit browser handoff added. Static checks and 21 CI unit tests passed. No browser/device/competition test. See `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md`. |
| 09a — Former local Kaggle token route | **Superseded; no further work.** Owner selected manual participation; token/API source and tests removed, historical evidence retained. |
| 09b — Verify revised manual Kaggle handoff | **Finished.** Approved CI run 40228035751 passed compilation, 21 tests, fresh debug APK assembly, zip alignment, v2 signature, SHA-256, and clean-source checks. No artifact was retained; device/competition tests remain separate. |
| 09c — Owner external-handoff smoke test | **Owner test recorded; wording correction requires green CI before Division 11.** Installed Kaggle app opened the correct page before sign-in; owner later signed in personally and performed Cancel/Back. No join/submission or credential sharing; browser-specific and unreported subchecks remain unverified. |
| 10 — Colab entry/control | Finished as a fixed manual-handoff reference. The planned public notebook path reported “Notebook not found.” No browser launch, account, runtime, or AI call exists. |
| 10a — toolchain restoration and verification | Finished. |
| 11–42 | Not started. |

Latest verified Android source result:

```text
Commit: d1be9aa3bfd15fcce30277e4f676e03326e29f2a
GitHub Actions run: 40228035751
:app:testDebugUnitTest — 21 passed; 0 failures, 0 errors, 0 skipped
:app:assembleDebug — passed
APK zip alignment — passed
APK v2 signature — passed
Generated debug verification APK SHA-256:
02eb96bebda5563bc00f55e35af7356d142e9b96185b896f877110dcc6b14264
```

Run receipt: https://github.com/mohamtur1/4CBOn2/actions/runs/40228035751

This was an ephemeral CI debug verification result. The APK was not uploaded or retained as an artifact, and the result is not a store release, device installation, authenticated Kaggle/Colab proof, production signature, or production certification. The older Division 10 local APK result remains historical.

## Critical Kaggle route boundary

The active Android app has **no Kaggle API client, token entry/storage, OAuth flow, or account/session access**. The historical public competition-list probe returned HTTP 401; that read client has now been removed.

Revised Division 09 accepts only a validated `https://www.kaggle.com/competitions/<slug>` reference and requests `Intent.ACTION_VIEW` after an explicit owner tap. A successful `startActivity` return means only `OPEN_REQUESTED`; it does not prove page visibility, sign-in, joining, or submission.

The owner personally handles Kaggle sign-in, rules acceptance, joining, and submission. The app must not claim those actions or replace them with cookies, browser-session capture, hidden API calls, or autonomous website control. See `android/KAGGLE_MANUAL_COMPETITION_HANDOFF.md`.

## Phone-control boundaries that remain mandatory

Use the least-powerful supported route:

```text
Authorized API -> supported Android intent/app link/share -> permitted fixed human-defined routine -> manual handoff
```

Never expose general screen-reading/tapping to a cloud planner. Keep banking, payments, passwords, OTPs, biometrics, permissions, and security-setting workflows manual-only. Take Over/Pause/Stop remain local and must reject late/stale commands after a takeover.

## Required user-facing style

Unless the owner explicitly asks for only one style in that message, every build progress/completion response must include:

```text
Like you are two
Like you are an adult
```

Use honest evidence labels: static/source, local unit test, build/signature, emulator, real device, or live service. Never represent a public read as authentication, a browser handoff as remote control, or a source edit as a verified APK.

## Suggested new-thread message

> Continue only the 4CBOn2 Android prototype scoped to `https://github.com/mohamtur1/4CBOn2` on branch `arena/0eb49904-4cbon2`. Use `4CBOn2_Next_Thread_Handoff.md`, `4CBOn2 — New-Thread Engineering.md`, and `4CBOn2_Build_Divisions.md`. First confirm green CI for the 09c external-handoff wording correction; do not proceed if it failed. Then start only Division 11 — Local database, targeting initial entities and a persistence/restart test in one maximum-30-minute session. The owner’s Kaggle-app handoff and sign-in are documented; no join or submission occurred. Do not open a PR unless separately approved. Before every step state whether an upload is needed and obtain exact-step approval before every external write or transfer. Do not request or accept credentials in chat. Explain updates like I am two years old and as an adult unless I explicitly request only one style.

## Honest backup assurance

No system can promise that every device, account, or service will never fail. However, the essential Division 10 source/documents are now redundantly present in the workspace and in three publicly verified GitHub repository objects listed above. Keep those repository files, and do not delete them. For later work, create another source/document checkpoint before replacing or removing any prior checkpoint.
