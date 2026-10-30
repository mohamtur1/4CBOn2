# 4CBOn2 — New-Thread Handoff for Division 19

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 30 October 2026
**Verified Division 18 commit:** `c9522e0bb2ca9eecdf17706ddd5a39eaea5bd338`
**Next division:** 19 — Persistent waiting queue
**Do not start:** Division 19 without an explicit “Continue”, Division 20 or later, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

The app can count now. You give it a tiny table and it tells you the smallest, the biggest, and the sum for each column — and if the table is too big or a number is written strangely, it says "I can't do that" instead of guessing. The big robot checked 144 little things and all passed, and it kept no app file.

The next job is a waiting list. You write down something you want to do later — now the app remembers it even after it closes down. The one big care is this: when the app wakes up, it must **not** just go and do the waiting things by itself. It should show you the list and wait for you to say go. We are not doing that yet.

## Like you are an adult

Division 18 is complete for its documented source/build scope. One bounded pure function now summarises a small CSV or JSON table into exact integer per-column count/minimum/maximum/total, or refuses it with one of eleven named reasons in a documented fixed order. Over-cap tables are refused rather than trimmed, canonical-number rules reject ambiguous tokens, and `MAX_ABS_TOTAL` proves a column sum cannot overflow a `Long`. The 119 earlier tests remain intact; 25 new tests bring the active total to 144.

The no-artifact CI run [41005091011](https://github.com/mohamtur1/4CBOn2/actions/runs/41005091011) succeeded at commit `c9522e0bb2ca9eecdf17706ddd5a39eaea5bd338`: 144 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `f3686291ef07f3bb47fa1c617d5ec18ef9761f313f32025cfa2099bef89683f2`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test. See `4CBOn2_Division18_Verification.md` and `android/LOCAL_ANALYSIS.md`.

Arena fixes this session to `arena/74f65509-4cbon2`, so work continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `c9522e0bb2ca9eecdf17706ddd5a39eaea5bd338` (tree `7e73789`) |
| Final Division 18 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/41005091011 — **Success** |
| Unit tests | **144 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, APK Signature Scheme v2 verified |
| Division 18 APK SHA-256 | `f3686291ef07f3bb47fa1c617d5ec18ef9761f313f32025cfa2099bef89683f2` |
| CI artifacts | **Zero** |
| Database schema | **Version 2**, oldest readable version 1 |
| Manifest permissions | `INTERNET` only |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device test, no permission-granted or accessibility flow, no production signing, no Play approval, no authentication proof, no encryption of the local database, no picker wiring, no UI wiring for the knowledge library or analyser, no universal phone control |

The historical 09c APK artifact and earlier CI records at 21/25/40/55/71/86/103/119 tests are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 19 narrow work order — do not begin yet

When the owner explicitly says “Continue”, start only Division 19 — Persistent waiting queue:

1. Model a queued job with bounded identity, a declared intent, a saved time, and an explicit state. Save it through the existing local entity database so it survives process death, and prove recovery with a synthetic file-backed close/reopen test.
2. **No automatic consequential replay.** On restart, a recovered job must never resume by itself. It surfaces as waiting and requires a fresh owner action; a recovered job that skips that step is the single worst failure this division can ship.
3. Every deferral must record **why** it is waiting. A deferred network task must be recorded as deferred, never as attempted, succeeded, or failed — provenance stays honest, exactly as Division 17 required for saved knowledge.
4. Keep the queue inert: enqueueing or recovering must not grant permission, start a job, bypass `ScopeContract`, `ActionLease`, or the capability registry, or open a socket. No credential, no real personal data, no live service call.
5. Persisting a new entity type is a **schema decision** again. Version 3 with a documented header-only upgrade and a proven v1/v2 read path is the likely shape, but the decision must be made and justified explicitly rather than assumed; a reason to stay at version 2 is equally acceptable if stated honestly.
6. Bound everything that can grow — queue length, identifier length, intent text, and any retry counter — and make a full or capped queue visible rather than silently dropping an entry.
7. Carry forward the known gap: `KnowledgeRefusal.QUERY_TOO_LONG` and `LIMIT_OUT_OF_RANGE` are declared but never returned. If this division touches the knowledge library, resolve them deliberately — wire them in or remove them. Do not leave them permanently aspirational.
8. Preserve the 144 existing tests and update the CI exact total honestly if new tests are added.
9. Require full no-artifact CI verification before closure, then update the plan/recovery documents.
10. Stop before Division 20.

## Standing owner requirements

- Work in one maximum-30-minute division at a time, including waiting, verification, and checkpointing.
- Explain every progress/completion update in both **Like you are two** and **Like you are an adult** styles unless explicitly told otherwise.
- Before every work step, state whether upload is needed. For each external write/transfer, identify the exact destination, files/data, and reason and obtain explicit owner approval first.
- Never request, accept, display, or store passwords, tokens, cookies, authorization codes, or browser sessions.
- Do not ask design clarification questions; choose interaction design autonomously within documented safety boundaries.
- Preserve least-powerful supported routes and manual-only handling for sensitive phone workflows.
- Never claim universal phone/app control, production signing, Play Store approval, or live Kaggle joining/submission without actual evidence.
- No PR is required unless the owner separately asks and approves one.

## Repository recovery — read this before the first push

The local Git-metadata reset has now occurred **five times**, twice in Division 18 alone, destroying commits after local verification. In every case the working tree survived and the published branch head was intact, and in every case the push failed closed so nothing partial was uploaded.

Working recovery, in order — never deviate:

1. Take a safety snapshot first: a tarball **and** a per-file SHA-256 manifest. Note that `find | xargs sha256sum` silently drops filenames containing spaces; use the tarball as the authority.
2. `git fetch origin arena/74f65509-4cbon2` and confirm `FETCH_HEAD` equals the last published head.
3. `git reset --mixed <FETCH_HEAD>` — **never** `--hard`.
4. `git add -A` then `git write-tree`, and compare the tree hash to the approved tree. A match proves byte-identity independently of commit identity.
5. Re-create the commit, verify the tree hash again, and **push in the same shell invocation as the recovery**, so there is no window between calls for another reset.
6. Never claim a push happened without `git ls-remote` afterwards.

A rebuilt tree that reproduces the approved tree hash is byte-identical content, but it is still a new commit ID. Division 18 needed three IDs (`c75842f` → `bd9d89d` → `c9522e0`) for one unchanged tree; the first two were destroyed before any push and each was re-approved or re-verified before use.

## Known open items carried forward

- `KnowledgeRefusal.QUERY_TOO_LONG` and `LIMIT_OUT_OF_RANGE` are declared but never returned — `search()` enforces bounds with `require` instead. Decide deliberately in a future source division: either wire them into the refusal path or remove them.
- Seven Android evidence documents still show a “Pending” CI section from their own division (`android/FILE_HANDOFF.md`, `android/KNOWLEDGE_LIBRARY.md`, `android/LOCAL_ANALYSIS.md`, `android/OFFLINE_DRAFTS.md`, `android/TAKEOVER_LEASE.md`, `android/SCOPE_CONTRACT.md`, `android/SELECTED_APP_CAPABILITIES.md`). Root receipts supersede them; a source-touching division can correct the files themselves.
- There is no local JDK/Gradle/Android SDK in this workspace, and `/tmp` toolchains do not persist across an environment reset. Any local harness result must be labelled with the exact bytes it ran against; CI on a clean checkout is the authoritative verification.

## Files to read first in the next thread

```text
4CBOn2_Division18_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
android/LOCAL_ANALYSIS.md
android/KNOWLEDGE_LIBRARY.md
android/LOCAL_DATABASE.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/LocalEntityDatabase.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/LocalTableAnalysis.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/OfflineKnowledgeLibrary.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/BoundedJobController.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 19 — Persistent waiting queue when explicitly asked, and do not skip ahead. The queue must survive restart without ever replaying a consequential action automatically. Preserve the existing 144 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
