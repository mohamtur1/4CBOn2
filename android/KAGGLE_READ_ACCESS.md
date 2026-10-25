# 4CBOn2 Android Prototype — Kaggle Read Access

**Build division:** 09 — Prove Kaggle read access  
**Status:** Blocked pending legitimate Kaggle authorization; read boundary and tests are complete  
**Date:** 23 October 2026

> **Superseded 25 October 2026:** The owner selected manual Kaggle competition participation. The API client described here has been removed from active source. This is historical Division 09 evidence only; see `KAGGLE_MANUAL_COMPETITION_HANDOFF.md`.

## Like you are two

We looked through the Kaggle puzzle-house window.

The window said:

> “You need your own special pass first.”

So we stopped. We did not sneak in, guess a pass, enter a puzzle, or send anything.

## Like you are an adult

The prototype now contains a read-only competition-list probe for Kaggle. Its live unauthenticated test returned HTTP 401, so the application cannot honestly claim Kaggle read access yet. The code reports that authorization is required; it does not fabricate a list, reuse browser cookies, or attempt an alternative UI-control path.

## Actual public probe result

A single unauthenticated, no-body HTTPS request was made from the build environment:

```text
GET https://www.kaggle.com/api/v1/competitions/list?group=general
```

It returned:

```text
HTTP 401
Content-Type: application/json
```

No credential and no response body were sent or retained. This proves the selected Kaggle competition-list route is **not publicly readable** in this test context.

## Implemented boundary

| Control | Implemented behavior |
| --- | --- |
| Endpoint | Fixed HTTPS competition-list endpoint with `group=general` |
| HTTP method | `GET` only; no POST, PUT, PATCH, DELETE, upload, download, notebook, join, or submission path |
| Redirects | Disabled |
| Timing | 10-second connection/read bounds; no automatic retry loop |
| Public route | Uses no credential; reports `AuthenticationRequired` for the observed HTTP 401 |
| Authorized route | Requires only the future locally stored `KAGGLE_BEARER_ACCESS_TOKEN` slot; returns `CredentialMissing` without starting a connection if absent |
| Response data | Status-only result; no list body is stored or shown |
| Logging | No credential logging or request-body capture |

The source already uses the normal Internet permission and disallows cleartext traffic, both added during the prior GitHub-read division. No additional Android permission was added here.

## Authorization reality

Kaggle’s current API documentation distinguishes direct API tokens from third-party OAuth. For third-party applications, its documented OAuth public-client flow uses PKCE and requires a **preconfigured client ID**. The documentation also specifies that public-client redirect URIs are localhost-only. We have neither a Kaggle-registered client ID nor an owner-authorized access token in this build, so no mobile OAuth completion is claimed.

The read adapter intentionally accepts only a future **Bearer access token** from a verified compatible authorization flow. It must not receive a user password, browser cookie, copied session state, or an untested username/key format.

Official references:

- [Kaggle API authentication and OAuth](https://www.kaggle.com/docs/api)
- [Kaggle competition constraints](https://www.kaggle.com/docs/competitions)

## Verification performed

| Check | Actual result |
| --- | --- |
| Kotlin/Compose compile | Passed |
| Debug build | Passed |
| Debug APK signature | v2 verification passed |
| Unit tests | Passed: 13 total, 0 failures, 0 errors, 0 skipped |
| Public-route test | Passed: fake credential store rejects any credential load; HTTP 401 maps to `AuthenticationRequired` |
| Authorized-route missing-credential test | Passed: `CredentialMissing` returns without starting a connection |
| Endpoint/status-mapping tests | Passed |
| Static source scan | Passed: fixed HTTPS endpoint, GET only, redirects disabled, timeouts bounded, no logging, no destructive methods |
| Real unauthenticated Kaggle endpoint probe | HTTP 401; no credential/body retained |
| Kaggle bearer-token call | Not performed |
| OAuth client registration/PKCE flow | Not configured or tested |
| Phone/network/emulator test | Not performed |

The current ignored local verification APK for this source state has SHA-256:

```text
4f96fcab154496e9183859ec2dc469744ca1b2658194e69ce5da2ef9ba8f7884
```

The retained root `4CBOn2-debug.apk` is still the historical Division 05 foundation artifact and does not include this source update.

## What would unblock it later

One legitimate route must be selected and implemented/tested:

1. A Kaggle-approved OAuth public-client registration plus the required PKCE flow and a mobile-compatible verified redirect arrangement; or
2. A documented compatible token route that the owner enters locally in the app and verifies against a minimal read-only endpoint.

Neither should be improvised through browser-cookie extraction, embedded credential capture, or automated website UI control.

## Completion check

- [x] A fixed, read-only public/authorized Kaggle competition-list boundary exists in source.
- [x] Public route uses no credential and reports the observed authorization requirement.
- [x] Authorized route cannot begin without the specifically named local bearer-token slot.
- [x] Public endpoint HTTP 401 is recorded honestly.
- [x] Build, signature, static-boundary, and thirteen unit-test checks passed.
- [x] No Kaggle login, token, dataset, notebook, competition entry, or submission occurred.

**Next eligible division:** 10 — Prove the Colab entry/control route.
