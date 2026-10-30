# 4CBOn2 — New-Thread Handoff for Division 18

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 30 October 2026
**Verified Division 17 commit:** `264e854feb58fbc86ced5d513bc16eb9e6148b51`
**Next division:** 18 — Small local analysis
**Do not start:** Division 18 without an explicit “Continue”, Division 19 or later, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

You can put things you learned on a little shelf now, and find them again later by typing a word. Each thing remembers where it came from, and the shelf never pretends the app went and checked. The big robot checked 119 little things and all passed, and it did not keep any big app file. The next job is a tiny counting machine that looks at a small table of numbers — like a list of rows and columns — and tells you one simple thing about it, saying clearly when a row is wrong. We are not doing that yet.

## Like you are an adult

Division 17 is complete for its documented source/build scope. A local knowledge library now stores saved reports and evidence with bounded title, body, source label, recorded provenance, and a saved time; it reads, lists, and searches them with deterministic ordering, a bounded result cap, and visible truncation. Persisting a new entity type required a real schema change: the local database moved from version 1 to **version 2** as a header-only upgrade, with version 1 files still readable and upgraded in place, and the oldest-readable floor published as an explicit constant. The 103 earlier tests remain intact; 16 new tests bring the active total to 119.

The no-artifact CI run [40991856148](https://github.com/mohamtur1/4CBOn2/actions/runs/40991856148) succeeded at commit `264e854feb58fbc86ced5d513bc16eb9e6148b51`: 119 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `1d5bf6f1d59b484b0105dd7d0f103f9ff7902f95b8334bf24d9ddaffb1ae7738`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test. See `4CBOn2_Division17_Verification.md` and `android/KNOWLEDGE_LIBRARY.md`.

Arena fixes this session to `arena/74f65509-4cbon2`, so work continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `264e854feb58fbc86ced5d513bc16eb9e6148b51` (tree `1862288`) |
| Final Division 17 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/40991856148 — **Success** |
| Unit tests | **119 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, APK Signature Scheme v2 verified |
| Division 17 APK SHA-256 | `1d5bf6f1d59b484b0105dd7d0f103f9ff7902f95b8334bf24d9ddaffb1ae7738` |
| CI artifacts | **Zero** |
| Database schema | **Version 2**, oldest readable version 1 |
| Manifest permissions | `INTERNET` only |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device test, no permission-granted or accessibility flow, no production signing, no Play approval, no authentication proof, no encryption of the local database, no picker wiring, no UI wiring for the knowledge library, no universal phone control |

The historical 09c APK artifact and earlier CI records at 21/25/40/55/71/86/103 tests are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 18 narrow work order — do not begin yet

When the owner explicitly says “Continue”, start only Division 18 — Small local analysis:

1. Implement exactly **one** bounded analysis routine over a small local CSV or JSON input, with a clear contract for what it computes.
2. Parse defensively: reject malformed input with named, fail-closed refusal reasons rather than throwing raw parser exceptions; decide and document a fixed check order.
3. Bound everything that can grow — input bytes, row count, column count, field length, and any numeric magnitude — and make a truncation or cap visible in the result rather than silently wrong.
4. Keep invalid fixtures honest: ship tests for well-formed input *and* for each refusal path, so the routine is proven to fail closed rather than assumed to.
5. Stay local and inert: no network, no cloud upload, no credential, no real personal data, and no bypass of `ScopeContract`, `ActionLease`, or the capability registry. Analysis must not itself grant permission or start a job.
6. Add no new persisted entity type unless genuinely required; if the result must be saved, reuse the existing knowledge library and its schema rather than silently widening the schema again.
7. Preserve the 119 existing tests and update the CI exact total honestly if new tests are added.
8. Require full no-artifact CI verification before closure, then update the plan/recovery documents.
9. Stop before Division 19.

## Standing owner requirements

- Work in one maximum-30-minute division at a time, including waiting, verification, and checkpointing.
- Explain every progress/completion update in both **Like you are two** and **Like you are an adult** styles unless explicitly told otherwise.
- Before every work step, state whether upload is needed. For each external write/transfer, identify the exact destination, files/data, and reason and obtain explicit owner approval first.
- Never request, accept, display, or store passwords, tokens, cookies, authorization codes, or browser sessions.
- Do not ask design clarification questions; choose interaction design autonomously within documented safety boundaries.
- Preserve least-powerful supported routes and manual-only handling for sensitive phone workflows.
- Never claim universal phone/app control, production signing, Play Store approval, or live Kaggle joining/submission without actual evidence.
- No PR is required unless the owner separately asks and approves one.

## Known open items carried forward

- `KnowledgeRefusal.QUERY_TOO_LONG` and `LIMIT_OUT_OF_RANGE` are declared but never returned — `search()` enforces bounds with `require` instead. Decide deliberately in a future source division: either wire them into the refusal path or remove them. Do not leave them permanently aspirational.
- Several Android evidence documents still show a “Pending” CI section from their own division (`android/FILE_HANDOFF.md`, `android/KNOWLEDGE_LIBRARY.md`, `android/OFFLINE_DRAFTS.md`, `android/TAKEOVER_LEASE.md`, `android/SCOPE_CONTRACT.md`, `android/SELECTED_APP_CAPABILITIES.md`). Receipts in the repository root supersede them; a source-touching division can correct the files themselves.

## Files to read first in the next thread

```text
4CBOn2_Division17_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
android/KNOWLEDGE_LIBRARY.md
android/FILE_HANDOFF.md
android/LOCAL_DATABASE.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/OfflineKnowledgeLibrary.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/LocalEntityDatabase.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/OwnerFileIntake.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source. A local Git-metadata reset has now occurred **three times**, twice destroying commits that had not yet been pushed. If local `HEAD` returns to `ed169b6` while newer workspace files remain: take a working-tree snapshot first, compare byte-for-byte against the published branch head, use only `git reset --mixed`, and never hard-reset. Always verify the approved commit ID still exists immediately before pushing, and request fresh exact-step approval if it does not. A rebuilt tree that reproduces the approved tree hash is byte-identical content, but it is still a new commit ID that needs its own approval.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 18 — Small local analysis when explicitly asked, and do not skip ahead. Preserve the existing 119 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
