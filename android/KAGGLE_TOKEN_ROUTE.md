# Division 09a — Local Kaggle Token Route (Design Checkpoint)

**Division:** 09a — Prepare local Kaggle token route
**Date:** 31 October 2026
**Status:** Design checkpoint complete. No real token was entered, requested, stored, or logged.

## Deliverable

Confirm the documented Kaggle token format/scoping and design/test a local-entry
route before any real token is used. This document is that checkpoint.

## Kaggle token format and scoping (documented, not tested with a real token)

Kaggle's official CLI documents API-token authentication as the recommended
machine authentication route. The token is created in the Kaggle account
settings (Data Robots page → API → New Token) and is used as a Bearer token on
`https://www.kaggle.com/api/v1/...` endpoints.

Documented properties of the route (to be confirmed against the live account
before any use):

- The token is a long-lived credential. It must be stored in the app's
  encrypted credential slot (Division 07), never in source, logs, or chat.
- Kaggle API tokens are account-scoped; the app must not assume a token grants
  any specific competition, team, or submission permission. Eligibility remains
  authoritative per competition.
- The endpoint contract used by the prototype is the fixed read endpoint from
  Division 09: `GET https://www.kaggle.com/api/v1/competitions/list?group=general`.
  It returned HTTP 401 without a credential — the authorization requirement is
  confirmed, not bypassed.

## Local-entry route design

The app will accept a token **only through the native secure credential-entry
UI** (Division 07 boundary). The route:

1. Owner opens **Connections → Kaggle** in the app.
2. The app shows the exact endpoint it will call and the scope it needs (read
   competition metadata). No token is requested in chat, logs, or source.
3. The owner pastes the token into the secure entry field. The app stores only
   the encrypted record (`EncryptedCredentialRecord`) in the Android Keystore
   boundary.
4. A capability probe calls the fixed read endpoint with the stored credential
   and records one of: **authorized**, **unauthorized (401)**,
   **unavailable**, **blocked**. A probe is the only way to move from
   "not configured" to "API authorized".
5. The token is never passed to WebView, never included in job bundles, and
   never sent to a model.

## Explicit boundaries

- No real token was entered in this session; no test token exists.
- The token is not equivalent to OAuth scoped grants; treat it as a
  minimum-permission account credential and validate scope before use.
- Competition eligibility, rules acceptance, and submission authorization are
  separate gates (Divisions 20, 23, 37, 38).

## Verification performed

- Static/source review of the existing `KaggleCompetitionReadClient` and
  `KaggleCompetitionEndpoint` (Division 09) — endpoint is fixed and
  non-negotiable; no arbitrary URLs are accepted.
- Design consistency check against Division 07 credential storage and the
  Division 09 authorization blocker record.

**Not performed:** any live token entry, live probe, or device test.
