# Division 18 — Small local analysis

`LocalTableAnalysis` is one bounded, pure function: it reads a small CSV or JSON table
from text the owner already holds and returns exact per-column statistics, or one named
refusal. It adds no persistence, no permission, and no network call.

**Verification status: local JVM only.** The 25 new tests ran in a standalone Kotlin
harness against the same sources; approved no-artifact CI is **pending** (see the CI
section at the end).

## Why this exists

Division 16 modelled a file the owner picked. Division 17 stored what was learned. This
division adds the first thing that actually *looks at data* — so it is also the first
place where a wrong answer would look plausible. A silently dropped row produces a
confident, wrong total, which is worse than a refusal. The whole design follows from
that: **refuse, never adjust**.

## Accepted input

A single `String`. `summarize` treats leading whitespace as irrelevant when choosing a
shape: text whose first non-space character is `[` is read as JSON, everything else is
read as CSV. Nothing else is inferred, and no file, stream, or path is accepted.

### CSV subset

```text
first,second
1,2
3,4
```

- One header line, then data lines. Comma-separated.
- **No quoting and no escapes.** A `"` anywhere in a record is refused. A tolerant
  reader is exactly how `1,234` silently becomes two columns.
- LF or CRLF line endings. A stray `\r` inside a record is refused.
- No blank lines, including at the start. A single trailing newline is fine.
- Fields are used **verbatim**: a surrounding space is data, not padding, so ` 1` is
  refused as a non-canonical number rather than trimmed into `1`.

### JSON subset

```text
[{"first":1,"second":2},{"first":3,"second":4}]
```

- One array of flat objects. Values must be integer literals.
- Refused: escapes, decimals, exponents, booleans, `null`, nested arrays or objects,
  unquoted keys, trailing text, a top-level object, and a key repeated inside one object.
- Whitespace between tokens is tolerated, and later objects may list the same keys in a
  different order — columns follow the **first** object's order.
- This is a strict subset of JSON, not a JSON parser. Saying so is the point: anything
  outside the subset fails closed instead of being half-understood.

## Caps

| Cap | Value | Why |
| --- | --- | --- |
| `MAX_INPUT_CHARS` | 20 000 | Bounds all parsing work up front |
| `MAX_ROWS` | 200 | Bounds the summary size |
| `MAX_COLUMNS` | 8 | Bounds the summary width |
| `MAX_FIELD_LENGTH` | 64 | Bounds one name or cell |
| `MAX_ABS_MAGNITUDE` | 1 000 000 000 | Keeps every value exact as a `Long` |
| `MAX_MESSAGE_LENGTH` | 160 | A refusal can never carry the input back out |

Because every value is bounded, the worst-case total is `200 × 1 000 000 000 = 2 × 10¹¹`,
which fits inside a `Long` with room to spare. `AnalysisBounds.MAX_ABS_TOTAL` states that
product, and a test asserts it stays below `Long.MAX_VALUE` and that a table built at
both caps sums exactly. **Overflow is impossible by construction, not by luck.**

## Refusal order

The enum declaration order *is* the evaluation order. A table that breaks two rules
reports the earlier one, and a test pins the four interesting collisions.

| # | Reason | Meaning |
| --- | --- | --- |
| 1 | `EMPTY_INPUT` | Nothing but whitespace |
| 2 | `INPUT_TOO_LARGE` | Over 20 000 characters |
| 3 | `MALFORMED_SYNTAX` | Outside the CSV/JSON subset above |
| 4 | `TOO_MANY_COLUMNS` | More than 8 named columns |
| 5 | `TOO_MANY_ROWS` | More than 200 data rows |
| 6 | `FIELD_TOO_LONG` | A name or value over 64 characters |
| 7 | `DUPLICATE_COLUMN` | Two columns whose names match ignoring case |
| 8 | `COLUMN_MISMATCH` | A row that does not describe the same columns |
| 9 | `NON_NUMERIC_VALUE` | A cell not in canonical integer form |
| 10 | `VALUE_OUT_OF_RANGE` | An integer outside ±1 000 000 000 |
| 11 | `NO_DATA_ROWS` | Columns named, but no data row |

Two honest notes about this order:

- `COLUMN_MISMATCH` covers both shapes deliberately. A ragged CSV row has no names of its
  own, so it cannot match the header's field count; a JSON row carries names, so its key
  set must match. Both are "this row is not the same table", so both report one reason.
- `NO_DATA_ROWS` is last. It can only be reached when every earlier check is vacuous, so
  its position never hides a more specific fault.

Numbers are checked in **two passes** — form first, magnitude second — so a malformed
cell late in the table cannot be reported after an out-of-range cell early in it. The
order is a property of the rule, not of where the scan happened to stop.

## Canonical numbers

Accepted: `0`, `-0` is **not**, `7`, `-7`, `1000000000`, `-1000000000`.
Refused: `007`, `+1`, `1.0`, `1e3`, `1 000`, `1_000`, `0x10`, ` 1`, `1 `, `-0`.

Rejecting `007` and `+1` is deliberate. Accepting them would mean the tool silently
decides what an ambiguous token means; refusing them means the owner sees the problem.

## Shapes

```kotlin
sealed interface AnalysisOutcome {
    data class Summarized(val summary: TableSummary) : AnalysisOutcome
    data class Refused(val reason: AnalysisRefusal, val message: String) : AnalysisOutcome
}
```

`TableSummary(format, rowCount, columns)` keeps the input's column order and exposes
`columnCount`. Each `ColumnSummary(name, count, minimum, maximum, total)` is exact
integer arithmetic — no floating point, so no rounding to disagree about later.

A refusal message reports **shapes and counts only**. It never repeats a cell or a column
name, because a refusal record is exactly the kind of thing that gets logged and a table
may hold private values. A test drives three sentinel values through three refusal paths
and asserts none of them comes back out.

## What it cannot do

- **No persistence.** `LocalTableAnalysis` is a Kotlin `object` with no instance fields.
  Division 17's knowledge library is the intended place to save a result, and this
  division does not touch it, so the schema stays at version 2.
- **No boundary handles.** A reflection test asserts that no method accepts or returns a
  database, registry, lease, or scope type, and that `summarize` takes a `String` and
  returns an `AnalysisOutcome` — nothing else can be threaded through it.
- **No owner decision change.** A test runs an analysis with an empty capability registry
  and a `STOPPED` lease and asserts both are unchanged.
- **No network, no credential, no account, no real data.** Tests use synthetic fixtures.
- **No UI wiring.** Nothing in the app calls this yet.

## Tests — 25, all synthetic

| Area | Tests |
| --- | --- |
| Exact statistics and ordering | `csvSummaryComputesCountMinimumMaximumAndExactTotal`, `csvKeepsTheHeaderColumnOrder`, `negativeValuesSummariseExactly` |
| Shape independence and determinism | `csvAndJsonAgreeOnTheSameTable`, `repeatedAnalysisOfTheSameInputIsIdentical` |
| Empty and size | `blankInputIsRefusedAsEmpty`, `oversizedInputIsRefusedBeforeItIsParsed` |
| CSV syntax | `csvWithoutQuotingSupportRefusesAQuote`, `csvBlankLineIsRefusedAsMalformed` |
| JSON subset (16 fixtures) | `jsonConstructsOutsideTheSubsetAreRefused` |
| Caps | `tooManyColumnsIsRefused`, `tooManyRowsIsRefused`, `overlongFieldIsRefusedBeforeItsValue` |
| Column rules | `duplicateColumnIsRefusedIgnoringCase`, `csvRaggedRowIsRefusedAsColumnMismatch`, `jsonMissingOrExtraKeyIsRefusedAsColumnMismatch` |
| Number rules | `nonCanonicalNumbersAreRefused`, `magnitudeBeyondTheCapIsRefused` |
| Empty table | `columnsWithoutDataRowsAreRefused` |
| Refusal hygiene | `refusalsAreBoundedAndNeverEchoTheInput` |
| Ordering | `checksRunInTheDocumentedOrder` |
| Overflow proof | `totalCannotOverflowALong` |
| Inertness | `analysisIsInertAndHoldsNoState` |
| Tolerant syntax | `jsonToleratesWhitespaceAndReorderedKeys` |
| Documented sniffing | `inputStartingWithABracketIsReadAsJson` |

Active suite: **144** tests (119 preserved plus these 25).

Local harness result: `STANDALONE_TEST_RUN passed=123 failed=0` over 12 classes — the 25
new tests plus the 98 pure-JVM tests Division 17 already ran. Tests that need the Android
framework still run only in CI.

## CI

**Pending.** This division's approved no-artifact CI run has not executed yet. When it
does, the workflow's exact total moves from 119 to 144 in all five places (inventory
guard, XML assertion, step label, totals check, and notice).

## Boundaries

- No emulator, real-device, permission-granted, or live-service evidence.
- No device-sized performance measurement: the caps bound the work, but nothing here
  claims a measured runtime on a phone.
- No claim that this reads real CSVs from the wild — it reads the documented subset and
  refuses everything else.
