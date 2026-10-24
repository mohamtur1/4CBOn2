# 4CBOn2 — GitHub-Scoped New-Thread Handoff

**Repository scope:** `https://github.com/mohamtur1/4CBOn2`  
**Branch:** `main`  
**Prepared:** 23 October 2026  
**Purpose:** Let a new thread continue only the 4CBOn2 Android prototype safely from the public repository record and its source checkpoint.

---

## Read this first

This is a repository-scoped continuation, not permission to modify the repository.

- Use the public repository only for reading/checking unless the owner explicitly asks for an upload.
- Do not request, accept, use, or reproduce any credential, token, password, cookie, authorization code, or browser session in chat/source/logs.
- A GitHub PAT exposed earlier was revoked. It must never be repeated or searched for.
- This environment has no supported GitHub connector. Do not treat a pasted token as a substitute for an approved connector.
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

## Current source of truth and continuation order

1. Read **`4CBOn2 — New-Thread Engineering.md`** first.
2. Read **`4CBOn2_Build_Divisions.md`** next. Its detailed status table is the operational status; if any short summary line appears inconsistent, defer to the detailed status table and the engineering handoff.
3. Use **`4CBOn2_Checkpoint_Div10_source_docs.zip`** as the source/test/document snapshot if the new thread does not already have workspace access.
4. Continue only from **Division 09a — Prepare local Kaggle token route**, when the owner explicitly says “Continue.”
5. Do not jump to Division 11 until Division 09a is checked, documented, and stopped at its boundary.

## Current actual status

| Division | State |
| --- | --- |
| 01–08 | Finished and locally verified as documented. |
| 09 — Kaggle read access | Auth-blocked. The public competition-list endpoint returned HTTP 401. No real account/token/OAuth/device test occurred. |
| 09a — Local Kaggle token route | Next; not started. Confirm exact documented Kaggle token format/scoping and create/test only local safe-entry behavior before any real token is used. |
| 10 — Colab entry/control | Finished as a fixed manual-handoff reference. The planned public notebook path reported “Notebook not found.” No browser launch, account, runtime, or AI call exists. |
| 10a — toolchain restoration and verification | Finished. |
| 11–42 | Not started. |

Latest verified Android source result:

```text
:app:testDebugUnitTest :app:assembleDebug
15 tests passed; 0 failures, 0 errors, 0 skipped
APK v2 signature passed
Generated debug verification APK SHA-256:
e3250cd2c8aa86bd910e73575ce59f074618cccecf9219f5b1762e9de5169e07
```

This was a local debug verification result—not a retained release APK, store release, device installation, authenticated-service proof, or production certification.

## Critical Kaggle route boundary

The only tested public Kaggle request was:

```text
GET https://www.kaggle.com/api/v1/competitions/list?group=general
```

It returned HTTP 401 without a credential.

The owner may prefer a manually created, minimum-permission Kaggle token entered in a future local app UI instead of a third-party OAuth registration. That is a possible private-owner route only if the exact token format, available scope controls, and endpoint compatibility are documented and verified. Do not assume a generic personal/API token is equivalent to an OAuth Bearer token. Never request that token in chat.

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

> Continue only the 4CBOn2 Android prototype scoped to `https://github.com/mohamtur1/4CBOn2`. Use `4CBOn2 — New-Thread Engineering.md`, `4CBOn2_Build_Divisions.md`, and `4CBOn2_Checkpoint_Div10_source_docs.zip` as the recovery set. Start only with Division 09a — Prepare local Kaggle token route. Before every step state whether an upload is needed. Do not request or accept credentials in chat. Explain updates like I am two years old and as an adult unless I explicitly request only one style.

## Honest backup assurance

No system can promise that every device, account, or service will never fail. However, the essential Division 10 source/documents are now redundantly present in the workspace and in three publicly verified GitHub repository objects listed above. Keep those repository files, and do not delete them. For later work, create another source/document checkpoint before replacing or removing any prior checkpoint.
