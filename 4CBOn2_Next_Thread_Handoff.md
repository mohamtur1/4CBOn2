# 4CBOn2 — New-Thread Handoff for Division 20

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 30 October 2026
**Verified Division 19 commit:** `5ac1b3b7c7139b3bbd4ffdc3fa3d7f4d37ad0542`
**Next division:** 20 — Sensitive-action approval gate
**Do not start:** Division 20 without an explicit “Continue”, Division 21 or later, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

The app keeps a waiting list now, and it still cannot go and do the things on it by itself — it only shows you what is waiting. The big robot checked 169 little things and all passed, and it kept no app file.

The next job is a permission slip. Before the app does anything big or spill-y — like sending something somewhere — somebody has to say exactly yes. Not "yes, probably," but *this exact thing, to this exact place, good until this time, and no more than so many times.* And if the slip gets old, it stops working. We are not doing that yet.

## Like you are an adult

Division 19 is complete for its documented source/build scope. A restart-surviving local queue now stores work the owner wants later, with no executor, scheduler, worker, or network client — recovery is derived by comparing a job's last owner action against the session start, so opening the queue can only report what is waiting, and a test proves the file is byte-identical after construction. The 144 earlier tests remain intact; 25 new tests bring the active total to 169. The database moved to schema version 3 with versions 1 and 2 still readable.

The no-artifact CI run [41006981403](https://github.com/mohamtur1/4CBOn2/actions/runs/41006981403) succeeded at commit `5ac1b3b7c7139b3bbd4ffdc3fa3d7f4d37ad0542`: 169 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `4b36b3ceb1715e9f7593dd33815bee28c9ae240ca674c903dc1a064fcaee9cea`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test. See `4CBOn2_Division19_Verification.md` and `android/WAITING_QUEUE.md`.

Arena fixes this session to `arena/74f65509-4cbon2`, so work continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `5ac1b3b7c7139b3bbd4ffdc3fa3d7f4d37ad0542` (tree `2e3c9f8`) |
| Final Division 19 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/41006981403 — **Success** |
| Unit tests | **169 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, APK Signature Scheme v2 verified |
| Division 19 APK SHA-256 | `4b36b3ceb1715e9f7593dd33815bee28c9ae240ca674c903dc1a064fcaee9cea` |
| CI artifacts | **Zero** |
| Database schema | **Version 3**, oldest readable version 1 |
| Manifest permissions | `INTERNET` only |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device test, no permission-granted or accessibility flow, no production signing, no Play approval, no authentication proof, no encryption of the local database, no picker wiring, no UI wiring for the knowledge library, analyser, or queue, no universal phone control |

The historical 09c APK artifact and earlier CI records at 21/25/40/55/71/86/103/119/144 tests are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 20 narrow work order — do not begin yet

When the owner explicitly says “Continue”, start only Division 20 — Sensitive-action approval gate:

1. Model an approval as a **narrow, unforgeable record of one owner decision**: exactly what may happen, to which destination or artifact, for how long, and how many times. It must not be a general "this app is trusted" flag; a blanket permission is the failure mode this division exists to prevent.
2. Bind identity precisely. A destination or artifact identity must match exactly — no substring, prefix, wildcard, or case-folded match — so an approval for one target can never be spent on a neighbouring one.
3. Make expiry structural. An expired approval must be unable to authorise anything, and the check must not depend on a caller remembering to test it.
4. Enforce a use cap. A single-use approval must be exhaustible, and the cap must be checked and consumed atomically so concurrent paths cannot spend it twice.
5. Confirmation must be deliberate. Cover the confirmation tests explicitly: refusing an empty, missing, ambiguous, or stale confirmation; and refusing an approval whose declared scope is wider than the action being performed.
6. Keep the gate **inert and standalone**. It records and refuses; it must not itself perform the sensitive action, open a socket, write outside the local database, or bypass `ScopeContract`, `ActionLease`, or the capability registry. Do not wire it to a real external effect.
7. Reconcile with existing types honestly. `ScopeContract` already bounds targets and `ActionLease` already models a time-bounded owner grant — decide explicitly whether the approval gate composes with them, reuses them, or must stay separate, and state the reasoning. Do not silently duplicate a concept that already exists.
8. Persisting approvals is a **schema decision** again. Version 4 with a proven v1/v2/v3 read path is the likely shape, but justify it — or justify keeping approvals in memory and out of the database.
9. Bound everything that can grow, refuse rather than truncate, and never echo a destination identity into a refusal message that may be logged.
10. Preserve the 169 existing tests, update the CI exact total honestly, require full no-artifact CI verification before closure, update the plan/recovery documents, and stop before Division 21.

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

The local Git-metadata reset has occurred **five times** across Divisions 14–18, destroying commits after local verification. In every case the working tree survived, the published branch head was intact, and the push failed closed so nothing partial was uploaded.

Working recovery, in order — never deviate:

1. Take a safety snapshot first: a tarball **and** a per-file SHA-256 manifest. Note that `find | xargs sha256sum` silently drops filenames containing spaces; use the tarball as the authority.
2. `git fetch origin arena/74f65509-4cbon2` and confirm `FETCH_HEAD` equals the last published head.
3. `git reset --mixed <FETCH_HEAD>` — **never** `--hard`.
4. `git add -A` then `git write-tree`, and compare the tree hash to the approved tree. A match proves byte-identity independently of commit identity.
5. Re-create the commit, verify the tree hash again, and **push in the same shell invocation as the recovery**, so there is no window between calls for another reset.
6. Never claim a push happened without `git ls-remote` afterwards.

A rebuilt tree that reproduces the approved tree hash is byte-identical content, but it is still a new commit ID. Division 18 needed three IDs (`c75842f` → `bd9d89d` → `c9522e0`) for one unchanged tree.

## Known open items carried forward

- Seven Android evidence documents still show a “Pending” CI section from their own division (`android/FILE_HANDOFF.md`, `android/KNOWLEDGE_LIBRARY.md`, `android/LOCAL_ANALYSIS.md`, `android/WAITING_QUEUE.md`, `android/OFFLINE_DRAFTS.md`, `android/TAKEOVER_LEASE.md`, `android/SCOPE_CONTRACT.md`, `android/SELECTED_APP_CAPABILITIES.md`). Root receipts supersede them; a source-touching division can correct the files themselves.
- Division 19 deliberately does **not** pair queue writes with `LocalActionEvent`, because events are draft-scoped by design. If a future division needs an audit trail for queue operations, that requires widening the event model deliberately, not by inventing synthetic drafts.
- The `KnowledgeRefusal` dead-value gap carried since Division 17 is **closed** in Division 19 (the two unreachable values were removed). Do not re-open it by re-adding values nothing returns.
- There is no local JDK/Gradle/Android SDK in this workspace, and `/tmp` toolchains do not persist across an environment reset. Any local harness result must be labelled with the exact bytes it ran against; CI on a clean checkout is the authoritative verification.

## Files to read first in the next thread

```text
4CBOn2_Division19_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
android/WAITING_QUEUE.md
android/SCOPE_CONTRACT.md
android/TAKEOVER_LEASE.md
android/LOCAL_DATABASE.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/ActionScopeContract.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/ActionLease.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/PersistentWaitingQueue.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/OwnerFileIntake.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 20 — Sensitive-action approval gate when explicitly asked, and do not skip ahead. Approvals must be narrow, exact-match, expiring, and use-capped, and the gate must record and refuse without performing the sensitive action. Preserve the existing 169 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
