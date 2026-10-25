# 4CBOn2 Android Prototype — GitHub Read Access

**Build division:** 08 — Prove GitHub read access  
**Status:** Finished with a verified public-read route; authenticated/private access remains unverified  
**Date:** 23 October 2026

## Like you are two

We made a safe little path to look at the GitHub book house.

We asked, “Can we see the front of the book house?”

GitHub said yes.

We did not change the book house. We did not use a secret key. We did not open any secret rooms.

## Like you are an adult

The project now contains a narrowly scoped, read-only GitHub metadata probe for the intended repository `mohamtur1/4CBOn2`. It has two explicit modes:

1. **Public probe:** sends no credential and can only inspect public metadata visibility.
2. **Authorized probe:** requires a future user-entered fine-grained PAT from the protected local credential boundary. It does not fall back to anonymous access when the expected credential is missing.

Neither route is invoked automatically by the app. There is no connection UI, stored credential, account session, background polling, repository write method, or device test yet.

## Actual external read result

A build-environment, unauthenticated HTTPS request was made to each of these endpoints on 23 October 2026:

```text
GET https://api.github.com/repos/mohamtur1/4CBOn2
GET https://api.github.com/repos/mohamtur1/4CBOn2/contents/
```

Both returned **HTTP 200**. The second is GitHub’s root-content listing endpoint, so this verifies that public metadata and the public root-content listing were readable at that time without a credential. The response bodies were deliberately discarded and not retained in the workspace.

This proves **public GitHub read visibility from the build environment**, not that the APK was installed on a phone, not that the app performed the request on a device, and not authenticated/private repository access.

## Adapter boundaries

| Control | Implemented behavior |
| --- | --- |
| Destination | Validated fixed repository ID: `mohamtur1/4CBOn2` |
| Endpoint | Fixed HTTPS GitHub REST metadata endpoint only |
| HTTP method | `GET` only; no POST, PUT, PATCH, or DELETE source path |
| Redirects | Disabled to avoid carrying an authorization header to another destination |
| Network timing | 10-second connection/read bounds; no automatic retry loop |
| Response handling | Maps concise status categories; does not store server body text |
| Public route | Does not load/read a credential at all |
| Authorized route | Reads only `GITHUB_FINE_GRAINED_PAT` through `CredentialStore`; missing credential returns `CredentialMissing` without starting a connection |
| Cleartext traffic | Disabled in the app manifest |
| Network permission | Normal `android.permission.INTERNET` is now declared because future explicit HTTPS requests require it |

The app must not interpret a public `200` as authority to write, push, change repository settings, access another repository, or expose a PAT.

## Verification performed

| Check | Actual result |
| --- | --- |
| Kotlin/Compose compile | Passed |
| Debug build | Passed |
| Debug APK signature | v2 verification passed |
| Unit tests | Passed: 9 total, 0 failures, 0 errors, 0 skipped |
| Public-route test | Passed: test fake rejects any credential load; result is visible only when probe succeeds |
| Authorized-route missing-credential test | Passed: returns `CredentialMissing` without starting a connection |
| Endpoint validation/status mapping tests | Passed |
| Static source scan | Passed: fixed HTTPS API endpoint, GET only, redirects disabled, timeouts bounded, no logging, no write verbs |
| External unauthenticated GitHub metadata/root listing probe | HTTP 200 at build time; no body retained |
| Local PAT / authenticated GitHub API call | Not performed |
| APK installation / phone network behavior | Not tested |

The current ignored local verification APK for this source state has SHA-256:

```text
99c90656b6ec29a1634c3251881c96863c60f022bb47f64952b6a082b97a9f8d
```

The retained root `4CBOn2-debug.apk` is still the historical Division 05 foundation artifact and does not include this source update.

## PAT decision

A PAT is **not needed now** to read the currently visible public metadata/content listing. Do not paste one into chat.

A future PAT is still needed if an authenticated/private route or authorized write route is genuinely required. At that time it must be entered locally in a real credential-entry screen, be fine-grained, restricted to `mohamtur1/4CBOn2`, minimum-permission, read-only initially, and verified on a real device. None of those conditions exist yet.

## Not implemented

- App-side connection screen/status UI.
- Public file listing/body parsing in the APK.
- Downloading a repository file or notebook.
- Local credential-entry screen.
- Authenticated PAT test.
- GitHub write, commit, issue, release, branch, or settings operations.
- Phone/network/emulator test.

## Completion check

- [x] Fixed read-only public and authorized GitHub probe routes exist in source.
- [x] Public route does not read a credential.
- [x] Authorized route cannot begin without the specific local credential slot.
- [x] Public metadata and root-content listing were observed as HTTP 200 from the build environment, with no body retained.
- [x] Build, signature, static boundary, and nine unit-test checks passed.
- [x] PAT/authenticated/mobile claims remain clearly unverified.

**Next eligible division:** 09 — Prove Kaggle read access through an authorized, minimum-permission route.
