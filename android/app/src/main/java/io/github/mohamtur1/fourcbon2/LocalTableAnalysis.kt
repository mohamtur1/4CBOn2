package io.github.mohamtur1.fourcbon2

/**
 * Canonical integer text: `0`, or an optional minus followed by a non-zero leading digit.
 *
 * The canonical form is deliberate. `007`, `+1`, `1.0`, `1e3`, and ` 1` are all refused
 * rather than quietly reinterpreted, so the same input always means the same number.
 */
private val ANALYSIS_INTEGER = Regex("0|-?[1-9][0-9]*")

/** Hard caps for one local analysis. Nothing can widen them from the outside. */
object AnalysisBounds {
    /** Largest input this routine will look at, in characters. Bounds all parsing work. */
    const val MAX_INPUT_CHARS = 20_000

    /** Largest number of data rows. A table with more is refused, never silently reduced. */
    const val MAX_ROWS = 200

    const val MAX_COLUMNS = 8

    /** Longest accepted column name or cell. */
    const val MAX_FIELD_LENGTH = 64

    /** Largest absolute cell value kept exactly as a `Long`. */
    const val MAX_ABS_MAGNITUDE = 1_000_000_000L

    /** No refusal message may be longer than this, so a message can never carry the input back out. */
    const val MAX_MESSAGE_LENGTH = 160

    /**
     * The worst-case total: every row at the largest allowed magnitude.
     *
     * This is a proof obligation, not a guess. A test asserts it stays inside `Long`, which
     * is why [ColumnSummary.total] can never overflow.
     */
    val MAX_ABS_TOTAL: Long = MAX_ROWS.toLong() * MAX_ABS_MAGNITUDE
}

/** Which documented input shape was recognised. */
enum class AnalysisFormat {
    CSV,
    JSON,
}

/**
 * Why one analysis was refused.
 *
 * The declaration order **is** the documented evaluation order, from cheapest and most
 * general to most specific. A row that breaks two rules reports the earlier one.
 */
enum class AnalysisRefusal {
    /** Nothing but whitespace was supplied. */
    EMPTY_INPUT,

    /** More than [AnalysisBounds.MAX_INPUT_CHARS] characters. */
    INPUT_TOO_LARGE,

    /** The text is outside the accepted CSV/JSON subset. */
    MALFORMED_SYNTAX,

    TOO_MANY_COLUMNS,

    TOO_MANY_ROWS,

    FIELD_TOO_LONG,

    DUPLICATE_COLUMN,

    /** A row does not describe the same columns as the first row. */
    COLUMN_MISMATCH,

    /** A cell is not an integer in canonical form. */
    NON_NUMERIC_VALUE,

    /** A cell is an integer but outside [AnalysisBounds.MAX_ABS_MAGNITUDE]. */
    VALUE_OUT_OF_RANGE,

    /** Columns are named but no data row follows. */
    NO_DATA_ROWS,
}

/**
 * Exact, bounded statistics for one column.
 *
 * `total` is an exact sum of integers, never a rounded float, and cannot overflow while
 * [AnalysisBounds] holds.
 */
data class ColumnSummary(
    val name: String,
    val count: Int,
    val minimum: Long,
    val maximum: Long,
    val total: Long,
) {
    init {
        require(count >= 1) { "A summarised column must contain at least one value." }
        require(minimum <= maximum) { "minimum must not exceed maximum." }
    }
}

/** The whole result of one analysis. [columns] keeps the input's column order. */
data class TableSummary(
    val format: AnalysisFormat,
    val rowCount: Int,
    val columns: List<ColumnSummary>,
) {
    init {
        require(rowCount >= 1) { "A summary must cover at least one row." }
        require(columns.isNotEmpty()) { "A summary must cover at least one column." }
        require(columns.all { it.count == rowCount }) { "Every column must cover exactly rowCount values." }
    }

    val columnCount: Int get() = columns.size
}

/** The result of one analysis: either a bounded summary or one named refusal. */
sealed interface AnalysisOutcome {
    data class Summarized(val summary: TableSummary) : AnalysisOutcome

    /**
     * A refusal names exactly one cause and never repeats the input.
     *
     * Refusal records are the kind of thing that gets logged, and a table may hold private
     * values, so [message] reports shapes and counts only.
     */
    data class Refused(val reason: AnalysisRefusal, val message: String) : AnalysisOutcome
}

/**
 * One bounded local analysis: summarise a small CSV or JSON table.
 *
 * This object is inert by construction. It holds no fields, no constructor, no clock, no
 * database, no registry, no lease, and no network client; `summarize` is a pure function of
 * its argument. It cannot grant permission, start a job, or persist anything, so running it
 * can never change an owner decision.
 *
 * Accepted input is a strict subset, documented in `android/LOCAL_ANALYSIS.md`:
 *
 * - **CSV**: one header line, then data lines. Comma-separated, no quoting and no escapes,
 *   LF or CRLF endings, no blank lines, at least one column. Fields are used verbatim: a
 *   surrounding space is data, not padding.
 * - **JSON**: only an array of flat objects whose values are integer literals. No escapes,
 *   no decimals, no exponents, no booleans, no nulls, no nesting.
 *
 * A table larger than the caps is **refused**, not truncated. Silently dropping rows would
 * produce a confidently wrong average, so the cap is reported instead.
 */
object LocalTableAnalysis {
    fun summarize(text: String): AnalysisOutcome {
        if (text.isBlank()) {
            return refuse(AnalysisRefusal.EMPTY_INPUT, "Input contains no table text.")
        }
        if (text.length > AnalysisBounds.MAX_INPUT_CHARS) {
            return refuse(
                AnalysisRefusal.INPUT_TOO_LARGE,
                "Input is ${text.length} characters, above the maximum of ${AnalysisBounds.MAX_INPUT_CHARS}.",
            )
        }

        val parsed = if (text.trimStart().startsWith("[")) parseJson(text) else parseCsv(text)
        val table = when (parsed) {
            is ParseResult.Ok -> parsed.table
            is ParseResult.Bad -> return refuse(AnalysisRefusal.MALFORMED_SYNTAX, parsed.message)
        }
        return evaluate(table)
    }

    private fun evaluate(table: RawTable): AnalysisOutcome {
        val header = table.header
        if (header.size > AnalysisBounds.MAX_COLUMNS) {
            return refuse(
                AnalysisRefusal.TOO_MANY_COLUMNS,
                "${header.size} columns named, above the maximum of ${AnalysisBounds.MAX_COLUMNS}.",
            )
        }
        if (table.rows.size > AnalysisBounds.MAX_ROWS) {
            return refuse(
                AnalysisRefusal.TOO_MANY_ROWS,
                "${table.rows.size} data rows, above the maximum of ${AnalysisBounds.MAX_ROWS}.",
            )
        }

        val longest = (header + table.rows.flatMap { it.values }).maxOfOrNull { it.length } ?: 0
        if (longest > AnalysisBounds.MAX_FIELD_LENGTH) {
            return refuse(
                AnalysisRefusal.FIELD_TOO_LONG,
                "A field is $longest characters, above the maximum of ${AnalysisBounds.MAX_FIELD_LENGTH}.",
            )
        }

        val lowered = header.map { it.lowercase() }
        if (lowered.size != lowered.toSet().size) {
            // The repeated name is not quoted back: a column name is owner content.
            return refuse(AnalysisRefusal.DUPLICATE_COLUMN, "Two columns share the same name, ignoring case.")
        }

        for (row in table.rows) {
            val names = row.names
            val size = names?.size ?: row.values.size
            if (size != header.size) {
                return refuse(
                    AnalysisRefusal.COLUMN_MISMATCH,
                    "A row describes $size columns but the table names ${header.size}.",
                )
            }
            if (names != null && names.map { it.lowercase() }.toSet() != lowered.toSet()) {
                return refuse(AnalysisRefusal.COLUMN_MISMATCH, "A row does not describe the same columns as the first row.")
            }
        }

        val cells = table.rows.map { row -> cellsInColumnOrder(row, header) }

        // Two passes, not one, so a late malformed cell cannot be reported after an early
        // out-of-range one. The documented order is numeric form first, then magnitude.
        for (row in cells) {
            for (cell in row) {
                if (!ANALYSIS_INTEGER.matches(cell)) {
                    return refuse(
                        AnalysisRefusal.NON_NUMERIC_VALUE,
                        "A cell is not an integer in canonical form: no spaces, plus signs, decimals, exponents, or leading zeros.",
                    )
                }
            }
        }

        val numbers = mutableListOf<List<Long>>()
        for (row in cells) {
            numbers += row.map { cell ->
                // toLongOrNull on canonical text can only fail when the digits exceed Long,
                // which is a magnitude problem rather than a form problem.
                val value = cell.toLongOrNull()
                if (value == null) {
                    return refuse(AnalysisRefusal.VALUE_OUT_OF_RANGE, "A value has too many digits to keep exactly.")
                }
                if (value > AnalysisBounds.MAX_ABS_MAGNITUDE || value < -AnalysisBounds.MAX_ABS_MAGNITUDE) {
                    return refuse(
                        AnalysisRefusal.VALUE_OUT_OF_RANGE,
                        "A value is beyond the supported magnitude of ${AnalysisBounds.MAX_ABS_MAGNITUDE}.",
                    )
                }
                value
            }
        }

        if (numbers.isEmpty()) {
            return refuse(AnalysisRefusal.NO_DATA_ROWS, "The table names columns but carries no data rows.")
        }

        val columns = header.mapIndexed { index, name ->
            val values = numbers.map { it[index] }
            ColumnSummary(
                name = name,
                count = values.size,
                minimum = values.min(),
                maximum = values.max(),
                total = values.sum(),
            )
        }
        return AnalysisOutcome.Summarized(
            TableSummary(format = table.format, rowCount = numbers.size, columns = columns),
        )
    }

    /** Puts one row's values into header order. CSV rows are already positional. */
    private fun cellsInColumnOrder(row: RawRow, header: List<String>): List<String> {
        val names = row.names ?: return row.values
        return header.map { name ->
            val position = names.indexOfFirst { it.equals(name, ignoreCase = true) }
            row.values[position]
        }
    }

    private fun refuse(reason: AnalysisRefusal, message: String): AnalysisOutcome.Refused {
        val bounded = if (message.length <= AnalysisBounds.MAX_MESSAGE_LENGTH) {
            message
        } else {
            message.take(AnalysisBounds.MAX_MESSAGE_LENGTH)
        }
        return AnalysisOutcome.Refused(reason = reason, message = bounded)
    }

    private fun parseCsv(text: String): ParseResult {
        val lines = text.split("\n").dropLastWhile { it.isEmpty() }
        if (lines.isEmpty()) {
            return ParseResult.Bad("Input contains no table text.")
        }
        if (lines.any { it.isBlank() }) {
            return ParseResult.Bad("A blank line is not a record.")
        }

        val fields = lines.map { line -> splitCsvLine(line) ?: return ParseResult.Bad("A record contains a character this subset cannot read.") }
        val header = fields.first()
        if (header.any { it.isEmpty() }) {
            return ParseResult.Bad("Every column must be named.")
        }
        return ParseResult.Ok(
            RawTable(
                format = AnalysisFormat.CSV,
                header = header,
                rows = fields.drop(1).map { values -> RawRow(values = values, names = null) },
            ),
        )
    }

    /**
     * Splits one CSV line, or returns null when the line is outside the subset.
     *
     * Quoting is deliberately unsupported: a quote is refused rather than half-parsed,
     * because a tolerant reader is how a `1,234` value becomes two columns.
     */
    private fun splitCsvLine(line: String): List<String>? {
        val body = if (line.endsWith("\r")) line.dropLast(1) else line
        if (body.contains('\r')) return null
        if (body.contains('"')) return null
        return body.split(",")
    }

    private fun parseJson(text: String): ParseResult = try {
        ParseResult.Ok(JsonTableReader(text).readTable())
    } catch (error: AnalysisSyntaxError) {
        ParseResult.Bad(error.message ?: "Input is not in the accepted JSON subset.")
    }
}

/** A parsed table before any cap or value check has run. */
private class RawTable(
    val format: AnalysisFormat,
    val header: List<String>,
    val rows: List<RawRow>,
)

/**
 * One row's raw cells.
 *
 * [names] is null for CSV, where a row is positional and cannot carry its own names.
 */
private class RawRow(
    val values: List<String>,
    val names: List<String>?,
)

private sealed interface ParseResult {
    data class Ok(val table: RawTable) : ParseResult
    data class Bad(val message: String) : ParseResult
}

/** Control flow only: always caught inside `parseJson` and turned into a named refusal. */
private class AnalysisSyntaxError(message: String) : Exception(message)

/**
 * Reader for the strict JSON subset: an array of flat objects with integer values.
 *
 * Every rejected construct fails closed with a message that describes the rule, never the
 * offending text.
 */
private class JsonTableReader(private val text: String) {
    private var index = 0

    fun readTable(): RawTable {
        skipWhitespace()
        expect('[')
        val rows = mutableListOf<RawRow>()
        skipWhitespace()
        if (peek() == ']') {
            index++
            finish()
            return RawTable(format = AnalysisFormat.JSON, header = emptyList(), rows = rows)
        }
        while (true) {
            skipWhitespace()
            rows += readRow()
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                ']' -> {
                    index++
                    break
                }
                else -> fail("Expected ',' or ']' after an object.")
            }
        }
        finish()
        val header = rows.first().names ?: emptyList()
        if (header.isEmpty()) {
            fail("Every column must be named.")
        }
        return RawTable(format = AnalysisFormat.JSON, header = header, rows = rows)
    }

    private fun readRow(): RawRow {
        expect('{')
        val names = mutableListOf<String>()
        val values = mutableListOf<String>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return RawRow(values = values, names = names)
        }
        while (true) {
            skipWhitespace()
            val name = readKey()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            val value = readInteger()
            if (names.any { it.equals(name, ignoreCase = true) }) {
                fail("A key is repeated inside one object.")
            }
            names += name
            values += value
            skipWhitespace()
            when (peek()) {
                ',' -> index++
                '}' -> {
                    index++
                    return RawRow(values = values, names = names)
                }
                else -> fail("Expected ',' or '}' inside an object.")
            }
        }
    }

    private fun readKey(): String {
        expect('"')
        val start = index
        while (index < text.length && text[index] != '"') {
            when {
                text[index] == '\\' -> fail("Escape sequences are outside the accepted subset.")
                text[index].code < 0x20 -> fail("A key must not contain control characters.")
            }
            index++
        }
        if (index >= text.length) {
            fail("A key is not terminated.")
        }
        val name = text.substring(start, index)
        index++
        if (name.isEmpty()) {
            fail("Every column must be named.")
        }
        return name
    }

    private fun readInteger(): String {
        val start = index
        if (peek() == '-') index++
        val digitsStart = index
        while (index < text.length && text[index].isAsciiDigit()) index++
        if (index == digitsStart) {
            fail("Every value must be an integer.")
        }
        val next = peek()
        if (next == '.' || next == 'e' || next == 'E') {
            fail("Only integers are accepted; decimals and exponents are outside the subset.")
        }
        return text.substring(start, index)
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isJsonWhitespace()) index++
    }

    private fun peek(): Char? = if (index < text.length) text[index] else null

    private fun expect(character: Char) {
        if (peek() != character) {
            fail("Expected '$character'.")
        }
        index++
    }

    private fun finish() {
        skipWhitespace()
        if (index != text.length) {
            fail("Unexpected text after the table.")
        }
    }

    private fun fail(message: String): Nothing = throw AnalysisSyntaxError(message)
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun Char.isJsonWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
