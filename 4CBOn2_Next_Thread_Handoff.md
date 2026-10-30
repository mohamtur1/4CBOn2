# 4CBOn2 — New-Thread Handoff for Division 21

**Repository:** `mohamtur1/4CBOn2`
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline used:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Prepared:** 30 October 2026
**Verified Division 20 commit:** `859b0972d1592da9ededff987a2c277abf7ab717`
**Next division:** 21 — One harmless app intent/API action
**Do not start:** Division 21 without an explicit “Continue”, Division 22 or later, live Kaggle joining/submission, a PR/merge, or any credential/account action

## Like you are two

The app asks permission properly now — a little exact slip for one thing, one person, one short time, and it goes bad on purpose. The big robot checked 198 little things and all passed, and it kept no app file.

The next job is the first one where the app actually *reaches out and touches something* outside itself. Everything so far has been paper. This one is a real, small, harmless reach: open something, or prepare something, and then honestly say what actually happened.

That means the most important word in this whole job is **honest**. If it opened, say opened. If nothing happened, say nothing happened. If it does not know, say it does not know. We are **not** doing that yet.

## Like you are an adult

Division 20 is complete for its documented source/build scope. A sensitive-action approval gate now records one deliberate owner decision per act, bound to an exact destination identity with no prefix, substring, or case-folded match, a lifetime never longer than the job scope, and a use count consumed atomically so a failed attempt spends nothing. It composes with `ScopeContract` rather than duplicating it, and approvals live only in memory so a restart invalidates them instead of honouring a stale permission. The 169 earlier tests remain intact; 29 new tests bring the active total to 198. The database stayed at schema version 3 with no migration.

The no-artifact CI run [41020784286](https://github.com/mohamtur1/4CBOn2/actions/runs/41020784286) succeeded at commit `859b0972d1592da9ededff987a2c277abf7ab717`: 198 passed, 0 failures/errors/skips; debug APK assembled, zip-aligned, and v2-signature verified; SHA-256 `6fcee2e447a97613554c3f09cf6f9d34ae234c2a4a42f4c0e569a3cbed1dc387`; clean-source check passed; artifact-retention step skipped; artifact count zero. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test. See `4CBOn2_Division20_Verification.md` and `android/SENSITIVE_APPROVAL.md`.

Arena fixes this session to `arena/74f65509-4cbon2`, so work continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Verified starting point

| Item | Evidence |
| --- | --- |
| Branch/head | `arena/74f65509-4cbon2`, verified source commit `859b0972d1592da9ededff987a2c277abf7ab717` (tree `1738bcb`) |
| Final Division 20 CI | https://github.com/mohamtur1/4CBOn2/actions/runs/41020784286 — **Success** |
| Unit tests | **198 passed; 0 failures, 0 errors, 0 skipped** |
| APK verification | Debug APK assembled, zip-aligned, APK Signature Scheme v2 verified |
| Division 20 APK SHA-256 | `6fcee2e447a97613554c3f09cf6f9d34ae234c2a4a42f4c0e569a3cbed1dc387` |
| CI artifacts | **Zero** |
| Database schema | **Version 3**, oldest readable version 1 (unchanged by Division 20) |
| Manifest permissions | `INTERNET` only |
| Credentials/live accounts | None used; no PR or merge |
| Unverified limits | No emulator/real-device test, no permission-granted or accessibility flow, no production signing, no Play approval, no authentication proof, no encryption of the local database, no picker wiring, no UI wiring for any division since 16, no universal phone control |

The historical 09c APK artifact and earlier CI records at 21/25/40/55/71/86/103/119/144/169 tests are not current-source evidence. Earlier division evidence remains in Git history and the root/Android documentation.

## Division 21 narrow work order — do not begin yet

When the owner explicitly says “Continue”, start only Division 21 — One harmless app intent/API action:

1. **This is the first division that reaches an actual external effect.** Everything so far has been pure local logic. Treat that step change as the main risk, not as routine.
2. Choose **one** genuinely harmless action — an open or a prepare, never a send, purchase, delete, post, or anything consequential. State explicitly in the design document why the chosen action is harmless and what the worst case is if it misfires.
3. Report **actual** outcome honestly. The result type must distinguish at least: the action genuinely happened, it was refused before attempting, the platform reported failure, and the outcome is **unknown**. An unknown outcome must never be collapsed into success or failure; that is the single most important property of this division.
4. Keep the surface narrow. No unrestricted UI input, no free-text command, no user-supplied URI or intent string, no chooser, no arbitrary package. Whatever is opened must be a fixed, bounded, owner-visible reference.
5. Compose with the existing boundary rather than bypassing it: the action must require a resolved capability from the registry, a permitted `ScopeDecision`, a live `ActionLease`, and — where the approval gate applies — a valid `SensitiveActionApproval`. Do not invent a parallel path around any of them.
6. Keep it local and credential-free. No network call, no account, no token, and no real personal data. If the action needs the internet to be meaningful, pick a different action.
7. Persisting an outcome is a **schema decision** again. Reusing the knowledge library or the queue's existing storage is acceptable; a version 4 bump needs the same justification and proven v1/v2/v3 read path as before. Stating "no persistence" and why is equally acceptable.
8. Bound everything that can grow or block: a timeout for the platform round trip, a refusal for an unresponsive platform, and no unbounded retry. A hung platform must produce a bounded, honest outcome rather than a hung test.
9. Do not assert device behaviour you cannot verify. CI runs on a JVM with no Android framework, so any real intent dispatch will be **unverifiable in CI**. Design the seam so the pure decision logic is JVM-testable and the platform call is a narrow, injectable boundary that CI can fake — and say plainly in the receipt that the real dispatch is untested.
10. Preserve the 198 existing tests, update the CI exact total honestly, require full no-artifact CI verification before closure, update the plan/recovery documents, and stop before Division 22.

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

The local Git-metadata reset has now occurred **six times**, including once during Division 20 between committing locally and pushing. In every case the working tree survived, the published branch head was intact, and the push failed closed so nothing partial was uploaded.

Working recovery, in order — never deviate:

1. Take a safety snapshot first: a tarball **and** a per-file SHA-256 manifest. Note that `find | xargs sha256sum` silently drops filenames containing spaces; use the tarball as the authority.
2. `git fetch origin arena/74f65509-4cbon2` and confirm `FETCH_HEAD` equals the last published head.
3. `git reset --mixed <FETCH_HEAD>` — **never** `--hard`.
4. `git add -A` then `git write-tree`, and compare the tree hash to the approved tree. A match proves byte-identity independently of commit identity.
5. Re-create the commit, verify the tree hash again, and **push in the same shell invocation as the recovery**, so there is no window between calls for another reset.
6. Never claim a push happened without `git ls-remote` afterwards.

A rebuilt tree that reproduces the approved tree hash is byte-identical content, but it is still a new commit ID. Division 18 needed three IDs and Divisions 19–20 needed two each for unchanged trees.

## Known open items carried forward

- Eight Android evidence documents still show a “Pending” CI section from their own division (`android/FILE_HANDOFF.md`, `android/KNOWLEDGE_LIBRARY.md`, `android/LOCAL_ANALYSIS.md`, `android/WAITING_QUEUE.md`, `android/SENSITIVE_APPROVAL.md`, `android/OFFLINE_DRAFTS.md`, `android/TAKEOVER_LEASE.md`, `android/SCOPE_CONTRACT.md`, `android/SELECTED_APP_CAPABILITIES.md`). Root receipts supersede them; a source-touching division can correct the files themselves.
- Division 19 deliberately does **not** pair queue writes with `LocalActionEvent`, because events are draft-scoped by design. A future division needing an audit trail for queue or approval operations must widen the event model deliberately, not by inventing synthetic drafts.
- Division 20 approvals are in-memory by design, so they do not survive a restart. That is the safe direction and should not be "fixed" without a replay-safety argument.
- The `KnowledgeRefusal` dead-value gap carried since Division 17 is **closed** in Division 19. Do not re-open it by re-adding values nothing returns.
- There is no local JDK/Gradle/Android SDK in the workspace, and `/tmp` toolchains do not persist across an environment reset. Any local harness result must be labelled with the exact bytes it ran against; CI on a clean checkout is the authoritative verification.

## Files to read first in the next thread

```text
4CBOn2_Division20_Verification.md
4CBOn2_Build_Divisions.md
4CBOn2 — New-Thread Engineering.md
android/SENSITIVE_APPROVAL.md
android/SELECTED_APP_CAPABILITIES.md
android/SCOPE_CONTRACT.md
android/TAKEOVER_LEASE.md
android/app/src/main/java/io/github/mohamtur1/fourcbon2/SensitiveActionApproval.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/SelectedAppCapabilityRegistry.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/ActionScopeEnforcer.kt
android/app/src/main/java/io/github/mohamtur1/fourcbon2/ActionLease.kt
.github/workflows/android-division09b.yml
```

Retain `4CBOn2_Checkpoint_Div10_source_docs.zip` as historical backup. Do not restore it over newer source.

## Ready-to-paste new-thread message

> Continue 4CBOn2 in `mohamtur1/4CBOn2` on the active Arena session branch. Read `4CBOn2_Next_Thread_Handoff.md` first. Start only Division 21 — One harmless app intent/API action when explicitly asked, and do not skip ahead. This is the first division that reaches an external effect: choose one harmless open/prepare action, report an honest outcome that distinguishes a real success, a pre-attempt refusal, a platform failure, and an unknown result, and never collapse unknown into success. Keep the platform call behind a narrow injectable seam so the pure logic is JVM-testable, and state plainly that real dispatch is unverified in CI. Compose with the registry, scope, lease, and approval gate rather than bypassing them. Preserve the existing 198 tests, update CI’s exact test-count assertion if needed, and require full no-artifact CI verification before closure. Do not use live accounts, open a PR, or request/accept credentials. Work in one maximum-30-minute session, preserve both explanation styles, state upload requirements before every step, and obtain exact-step approval before every external write or transfer.
