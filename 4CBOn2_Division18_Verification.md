# 4CBOn2 — Division 18 verification receipt

**Date:** 30 October 2026
**Current session branch:** `arena/74f65509-4cbon2`
**Source baseline:** `arena/0eb49904-4cbon2` at `2a4520118e31e9eb8d2cc2585f3ad95d3847cb2f`
**Verified source commit:** `c9522e0bb2ca9eecdf17706ddd5a39eaea5bd338`
**Verified source tree:** `7e7378953d519e16c18cdea593f1d455cf9e50c0`
**CI run:** https://github.com/mohamtur1/4CBOn2/actions/runs/41005091011
**CI conclusion:** Success, no uploaded artifacts

> Arena fixes this session to `arena/74f65509-4cbon2`. Division 18 continued from the named `arena/0eb49904-4cbon2` source baseline on the fixed session branch and was pushed only there. No PR was opened.

## Like you are two

Now the app can count. You give it a tiny table — a list of rows and columns — and it tells you, for each column, how many numbers there are, the smallest, the biggest, and the sum. It does the adding exactly, with no rounding, so the answer never quietly drifts.

The important part is what it refuses. If the table is too big, it says "too big" instead of secretly skipping rows — because if it skipped a row, it would still look confident while being wrong. If a number is written strangely, like `007` or `1.5`, it says "I can't read that" instead of guessing what you meant. When it says no, it never repeats your actual numbers back in the message, because those messages get written down in logs.

The big robot checked 144 little things and all passed, and it did not keep any app file.

## Like you are an adult

Division 18 added the first routine that inspects data, which is also the first place a wrong answer would look plausible. `LocalTableAnalysis.summarize` takes a `String` and returns either exact per-column statistics or one named refusal. It refuses far more than it accepts, deliberately.

Twenty-five synthetic JVM tests were added to the 119 preserved tests, bringing the active suite to 144; the CI inventory guard, step label, notice, and exact XML assertion were all moved to 144 without weakening.

The verified workflow compiled the Android source, ran all 144 unit tests, assembled a debug APK, checked zip alignment and APK Signature Scheme v2, recorded the APK SHA-256, confirmed the build did not modify source, skipped the historical 09c artifact-retention step, and uploaded zero artifacts. This is CI/JVM and build evidence, not an emulator, real-device, permission-granted, or live-service test.

## CI evidence

| Check | Actual result |
| --- | --- |
| Workflow | `Android Division 09b Verification` |
| Run | `41005091011` (push to `arena/74f65509-4cbon2`) |
| Conclusion | **Success** in 3m28s |
| Verified commit | `c9522e0bb2ca9eecdf17706ddd5a39eaea5bd338` |
| Verified tree | `7e7378953d519e16c18cdea593f1d455cf9e50c0` |
| Active source test inventory | 144 |
| JUnit aggregation | 144 passed; 0 failures; 0 errors; 0 skipped |
| Debug APK assembly | Passed |
| Zip alignment | Passed |
| APK Signature Scheme v2 | Passed |
| Debug APK SHA-256 | `f3686291ef07f3bb47fa1c617d5ec18ef9761f313f32025cfa2099bef89683f2` |
| Build did not modify source | Passed |
| Artifact-retention step | Skipped |
| CI artifact count | `0` (read back from the run-artifacts API) |
| Historical 09c artifact marker in commit message | Absent |
| Credentials, secrets, live accounts, PR, or merge | None used; none opened |

The APK was ephemeral CI output: not uploaded, retained, committed, installed, or production-signed.

## What the routine does

Accepted input is a strict subset, documented in `android/LOCAL_ANALYSIS.md`:

- **CSV** — one header line, then data lines. Comma-separated, **no quoting and no escapes**, LF or CRLF, no blank lines. Fields are used verbatim, so ` 1` is refused rather than trimmed into `1`.
- **JSON** — only an array of flat objects whose values are integer literals. No escapes, decimals, exponents, booleans, nulls, or nesting. Whitespace is tolerated; later objects may reorder keys, and columns follow the first object's order.

A leading `[` selects JSON; everything else is CSV. That sniffing rule is documented and tested, so it is a stated behaviour rather than a hidden surprise.

### Caps and the overflow proof

| Cap | Value |
| --- | --- |
| `MAX_INPUT_CHARS` | 20 000 |
| `MAX_ROWS` | 200 |
| `MAX_COLUMNS` | 8 |
| `MAX_FIELD_LENGTH` | 64 |
| `MAX_ABS_MAGNITUDE` | 1 000 000 000 |
| `MAX_MESSAGE_LENGTH` | 160 |

The worst-case total is `200 × 1 000 000 000 = 2 × 10¹¹`. `AnalysisBounds.MAX_ABS_TOTAL` states that product, and two tests pin it: one asserts it is below `Long.MAX_VALUE`, and one builds a table at both caps and asserts the sum is exactly `MAX_ABS_TOTAL`. `ColumnSummary.total` therefore **cannot overflow by construction**, not by luck.

### Refusal order

The enum declaration order is the evaluation order, and a test pins the four interesting collisions:

`EMPTY_INPUT` → `INPUT_TOO_LARGE` → `MALFORMED_SYNTAX` → `TOO_MANY_COLUMNS` → `TOO_MANY_ROWS` → `FIELD_TOO_LONG` → `DUPLICATE_COLUMN` → `COLUMN_MISMATCH` → `NON_NUMERIC_VALUE` → `VALUE_OUT_OF_RANGE` → `NO_DATA_ROWS`

Two design choices are worth naming because they are judgement calls, not inevitabilities:

- `COLUMN_MISMATCH` covers both shapes. A ragged CSV row has no names of its own and cannot match the header's field count; a JSON row carries names and must match the first row's key set. Both mean "this row is not the same table", so both report one reason rather than two near-duplicates.
- Numbers are checked in **two passes** — form first, magnitude second — so a malformed cell late in the table cannot be reported after an out-of-range cell early in it. Without the second pass, precedence would depend on where the scan happened to stop.

### Honesty properties

- **Over-cap tables are refused, never trimmed.** Silently dropping a row yields a confident, wrong total, which is worse than a refusal. The cap is reported instead.
- **Canonical numbers only.** `007`, `+1`, `1.0`, `1e3`, ` 1`, `1_000`, `0x10`, and `-0` are refused rather than reinterpreted. The tool does not decide what an ambiguous token means.
- **Refusals never echo input.** Messages report shapes and counts only — never a cell or a column name — because a refusal record is exactly what gets logged and a table may hold private values. A test drives three sentinel values through three refusal paths and asserts none returns.
- **No boundary handles.** A reflection test asserts `LocalTableAnalysis` has no instance state, that `summarize` takes a `String` and returns an `AnalysisOutcome`, and that no method accepts or returns a database, registry, lease, or scope type.
- **No owner decision changes.** A behavioural test runs an analysis with an empty capability registry and a `STOPPED` lease and asserts both are unchanged.
- **No persistence.** The knowledge library from Division 17 is the intended place to save a result and was not touched, so the schema stays at **version 2**. `LocalEntityDatabase.kt` is byte-unchanged in this commit.

## Test inventory

25 new tests, all synthetic:

| Area | Count | Coverage |
| --- | --- | --- |
| Exact statistics and ordering | 3 | Totals, min/max, header column order, negatives |
| Shape independence and determinism | 2 | CSV and JSON agree; repeated runs identical |
| Empty and size boundaries | 2 | Blank input; over-cap input; exactly-at-cap boundary |
| CSV syntax | 2 | Quotes refused; blank lines refused |
| JSON subset | 1 | 16 out-of-subset fixtures |
| Caps | 3 | Columns, rows, field length precedence |
| Column rules | 3 | Case-insensitive duplicates; ragged CSV; missing/extra JSON key |
| Number rules | 2 | 10 non-canonical forms; magnitude edges inclusive on both sides |
| Empty table | 1 | Header without data rows |
| Refusal hygiene | 1 | Bounded length, no echo of values or names |
| Ordering | 1 | Four documented collisions |
| Overflow proof | 1 | `MAX_ABS_TOTAL` below `Long.MAX_VALUE`, and exact at both caps |
| Inertness | 1 | No state, no boundary types, no decision change |
| Tolerant syntax | 1 | Whitespace and reordered keys |
| Documented sniffing | 1 | Leading bracket selects JSON |

Active suite: **144** = 119 preserved + 25 new, with no test removed or weakened.

Local harness, run against these exact bytes before the push: `STANDALONE_TEST_RUN passed=123 failed=0` over 12 classes. Tests requiring the Android framework still run only in CI.

## Repository recovery during this division

This division hit the recurring local Git-metadata reset **twice more** (occurrences four and five), destroying two commits after local verification. Both times:

- the push had not happened, so **nothing partial was uploaded**;
- recovery followed the documented path — safety tarball, `git fetch`, `git reset --mixed`, `git add -A`, `git write-tree`;
- the rebuilt tree matched `7e7378953d519e16c18cdea593f1d455cf9e50c0` **exactly**, and all seven files were verified byte-identical against the pre-reset snapshot;
- a new commit ID was created and pushed only after confirming the tree hash.

The commit chain for this division was `c75842f` → destroyed → `bd9d89d` → destroyed → `c9522e0` (pushed). All three carried the identical tree, so the approved content never changed; only the commit identity did.

The `/tmp` toolchain was also wiped by the same environmental event, so the local harness could not be re-run afterwards. That is stated plainly rather than papered over: the harness result describes these exact bytes — proven unchanged by both the content-addressed tree hash and per-file SHA-256 — but no fresh local run was performed after the last recovery. CI on a clean checkout is the authoritative verification, and it passed.

## Document status

`android/LOCAL_ANALYSIS.md` records this division's design and its CI section reads “Pending”, because this follow-up commit is restricted to root Markdown. This receipt is the authoritative supersession of that section; no Android source or workflow file changed after the CI-verified commit.

Earlier divisions’ `android/*.md` files (`FILE_HANDOFF.md`, `KNOWLEDGE_LIBRARY.md`, `OFFLINE_DRAFTS.md`, `TAKEOVER_LEASE.md`, `SCOPE_CONTRACT.md`, `SELECTED_APP_CAPABILITIES.md`) likewise still show their own “Pending” sections. Their root receipts supersede them. Only a source-touching division can correct the files themselves.

## Boundaries

- No emulator, real-device, permission-granted, production-signing, Play Store, or live-service claim.
- No device-sized performance measurement. The caps bound the work, but nothing here claims a measured runtime on a phone.
- No claim that this reads real CSVs from the wild — it reads the documented subset and refuses everything else.
- No UI wiring: nothing in the app calls the analyser yet.
- No encryption of the local database.
- Division 19 — Persistent waiting queue has not started.
