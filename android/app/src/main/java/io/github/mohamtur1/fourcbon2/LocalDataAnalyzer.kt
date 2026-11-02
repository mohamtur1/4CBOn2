package io.github.mohamtur1.fourcbon2

import java.util.Locale
import kotlin.math.sqrt

/**
 * Division 18: Small bounded local CSV and JSON analysis routine.
 *
 * Deterministically validates input structure, enforces byte and row bounds, infers column types,
 * counts missing values, and computes summary statistics (`min`, `max`, `mean`, `median`, `stdDev`)
 * without external libraries or network access.
 */
enum class DatasetFormat { CSV, JSON }

enum class InferredColumnType { NUMERIC, BOOLEAN, TEXT, EMPTY }

data class NumericSummary(
    val count: Int,
    val min: Double,
    val max: Double,
    val mean: Double,
    val median: Double,
    val stdDev: Double,
)

data class ColumnAnalysisProfile(
    val name: String,
    val inferredType: InferredColumnType,
    val nonEmptyCount: Int,
    val missingCount: Int,
    val numericSummary: NumericSummary?,
)

data class DatasetAnalysisReport(
    val datasetName: String,
    val format: DatasetFormat,
    val rowCount: Int,
    val columnCount: Int,
    val columns: List<ColumnAnalysisProfile>,
) {
    fun toMarkdownSummary(): String = buildString {
        appendLine("### Local ${format.name} Analysis — $datasetName")
        appendLine("- Rows: $rowCount · Columns: $columnCount")
        for (col in columns) {
            append("- **${col.name}** (${col.inferredType.name.lowercase(Locale.ROOT)}): ")
            append("${col.nonEmptyCount} present, ${col.missingCount} missing")
            val stats = col.numericSummary
            if (stats != null) {
                append(
                    String.format(
                        Locale.US,
                        " · min=%.3f, median=%.3f, mean=%.3f, max=%.3f, sd=%.3f",
                        stats.min,
                        stats.median,
                        stats.mean,
                        stats.max,
                        stats.stdDev,
                    ),
                )
            }
            appendLine()
        }
    }.trimEnd()
}

sealed interface LocalAnalysisOutcome {
    data class Success(val report: DatasetAnalysisReport) : LocalAnalysisOutcome
    data class InvalidInput(val reason: String) : LocalAnalysisOutcome
}

class LocalDataAnalyzer(
    private val maxInputBytes: Int = DEFAULT_MAX_BYTES,
    private val maxRows: Int = DEFAULT_MAX_ROWS,
    private val maxColumns: Int = DEFAULT_MAX_COLUMNS,
) {
    fun analyzeCsv(rawCsv: String, datasetName: String = "dataset.csv"): LocalAnalysisOutcome {
        val byteLen = rawCsv.toByteArray(Charsets.UTF_8).size
        if (byteLen == 0 || rawCsv.isBlank()) {
            return LocalAnalysisOutcome.InvalidInput("CSV input is empty.")
        }
        if (byteLen > maxInputBytes) {
            return LocalAnalysisOutcome.InvalidInput("CSV exceeds maximum local analysis size ($maxInputBytes bytes).")
        }

        val parsedRows = try {
            parseCsvRecords(rawCsv)
        } catch (ex: CsvFormatException) {
            return LocalAnalysisOutcome.InvalidInput(ex.message ?: "Invalid CSV syntax.")
        }

        if (parsedRows.size < 2) {
            return LocalAnalysisOutcome.InvalidInput("CSV must contain a header row and at least one data row.")
        }

        val headers = parsedRows.first().map { it.trim() }
        if (headers.isEmpty() || headers.any { it.isEmpty() }) {
            return LocalAnalysisOutcome.InvalidInput("CSV header row contains blank column names.")
        }
        if (headers.size > maxColumns) {
            return LocalAnalysisOutcome.InvalidInput("CSV exceeds maximum column limit ($maxColumns).")
        }
        if (headers.distinct().size != headers.size) {
            return LocalAnalysisOutcome.InvalidInput("CSV header row contains duplicate column names.")
        }

        val dataRows = parsedRows.drop(1)
        if (dataRows.size > maxRows) {
            return LocalAnalysisOutcome.InvalidInput("CSV exceeds maximum row limit ($maxRows rows).")
        }

        for ((idx, row) in dataRows.withIndex()) {
            if (row.size != headers.size) {
                return LocalAnalysisOutcome.InvalidInput(
                    "CSV row ${idx + 2} has ${row.size} columns; expected ${headers.size}.",
                )
            }
        }

        return LocalAnalysisOutcome.Success(
            profileTabularData(
                datasetName = datasetName,
                format = DatasetFormat.CSV,
                headers = headers,
                rows = dataRows,
            ),
        )
    }

    fun analyzeJson(rawJson: String, datasetName: String = "dataset.json"): LocalAnalysisOutcome {
        val byteLen = rawJson.toByteArray(Charsets.UTF_8).size
        if (byteLen == 0 || rawJson.isBlank()) {
            return LocalAnalysisOutcome.InvalidInput("JSON input is empty.")
        }
        if (byteLen > maxInputBytes) {
            return LocalAnalysisOutcome.InvalidInput("JSON exceeds maximum local analysis size ($maxInputBytes bytes).")
        }

        val root = try {
            JsonCodec.parse(rawJson)
        } catch (ex: JsonParseException) {
            return LocalAnalysisOutcome.InvalidInput("Invalid JSON: ${ex.message}")
        }

        val objectItems: List<JsonValue.Obj> = when (root) {
            is JsonValue.Arr -> {
                if (root.items.isEmpty()) {
                    return LocalAnalysisOutcome.InvalidInput("JSON array is empty.")
                }
                root.items.map { item ->
                    item as? JsonValue.Obj
                        ?: return LocalAnalysisOutcome.InvalidInput("JSON array items must be objects for tabular analysis.")
                }
            }
            is JsonValue.Obj -> {
                val nestedRecords = (root.entries["rows"] as? JsonValue.Arr)
                    ?: (root.entries["data"] as? JsonValue.Arr)
                    ?: (root.entries["items"] as? JsonValue.Arr)
                if (nestedRecords != null) {
                    if (nestedRecords.items.isEmpty()) {
                        return LocalAnalysisOutcome.InvalidInput("JSON records array is empty.")
                    }
                    nestedRecords.items.map { item ->
                        item as? JsonValue.Obj
                            ?: return LocalAnalysisOutcome.InvalidInput("Nested JSON records must be objects.")
                    }
                } else {
                    if (root.entries.isEmpty()) {
                        return LocalAnalysisOutcome.InvalidInput("JSON object is empty.")
                    }
                    listOf(root)
                }
            }
            else -> return LocalAnalysisOutcome.InvalidInput("Top-level JSON must be an array of objects or an object.")
        }

        if (objectItems.size > maxRows) {
            return LocalAnalysisOutcome.InvalidInput("JSON exceeds maximum row limit ($maxRows rows).")
        }

        val headers = LinkedHashSet<String>()
        for (obj in objectItems) {
            headers.addAll(obj.entries.keys)
            if (headers.size > maxColumns) {
                return LocalAnalysisOutcome.InvalidInput("JSON exceeds maximum column limit ($maxColumns).")
            }
        }
        if (headers.isEmpty()) {
            return LocalAnalysisOutcome.InvalidInput("JSON objects contain no fields.")
        }

        val headerList = headers.toList()
        val rows = objectItems.map { obj ->
            headerList.map { key ->
                when (val v = obj.entries[key]) {
                    null, JsonValue.Null -> ""
                    is JsonValue.Str -> v.value
                    is JsonValue.Bool -> v.value.toString()
                    is JsonValue.Num -> v.raw
                    is JsonValue.Arr, is JsonValue.Obj -> JsonCodec.stringify(v)
                }
            }
        }

        return LocalAnalysisOutcome.Success(
            profileTabularData(
                datasetName = datasetName,
                format = DatasetFormat.JSON,
                headers = headerList,
                rows = rows,
            ),
        )
    }

    private fun profileTabularData(
        datasetName: String,
        format: DatasetFormat,
        headers: List<String>,
        rows: List<List<String>>,
    ): DatasetAnalysisReport {
        val totalRows = rows.size
        val profiles = headers.mapIndexed { colIdx, name ->
            val rawCells = rows.map { it[colIdx].trim() }
            val nonEmpty = rawCells.filter { !isMissingToken(it) }
            val missingCount = totalRows - nonEmpty.size

            if (nonEmpty.isEmpty()) {
                ColumnAnalysisProfile(
                    name = name,
                    inferredType = InferredColumnType.EMPTY,
                    nonEmptyCount = 0,
                    missingCount = missingCount,
                    numericSummary = null,
                )
            } else {
                val numericValues = nonEmpty.mapNotNull { it.toDoubleOrNull()?.takeIf(Double::isFinite) }
                if (numericValues.size == nonEmpty.size) {
                    ColumnAnalysisProfile(
                        name = name,
                        inferredType = InferredColumnType.NUMERIC,
                        nonEmptyCount = nonEmpty.size,
                        missingCount = missingCount,
                        numericSummary = summarizeNumbers(numericValues),
                    )
                } else if (nonEmpty.all { it.equals("true", ignoreCase = true) || it.equals("false", ignoreCase = true) }) {
                    ColumnAnalysisProfile(
                        name = name,
                        inferredType = InferredColumnType.BOOLEAN,
                        nonEmptyCount = nonEmpty.size,
                        missingCount = missingCount,
                        numericSummary = null,
                    )
                } else {
                    ColumnAnalysisProfile(
                        name = name,
                        inferredType = InferredColumnType.TEXT,
                        nonEmptyCount = nonEmpty.size,
                        missingCount = missingCount,
                        numericSummary = null,
                    )
                }
            }
        }

        return DatasetAnalysisReport(
            datasetName = datasetName,
            format = format,
            rowCount = totalRows,
            columnCount = headers.size,
            columns = profiles,
        )
    }

    private fun isMissingToken(cell: String): Boolean =
        cell.isEmpty() || cell.equals("na", ignoreCase = true) ||
            cell.equals("n/a", ignoreCase = true) || cell.equals("null", ignoreCase = true) ||
            cell.equals("nan", ignoreCase = true)

    private fun summarizeNumbers(values: List<Double>): NumericSummary {
        val sorted = values.sorted()
        val n = sorted.size
        val min = sorted.first()
        val max = sorted.last()
        val mean = sorted.sum() / n.toDouble()
        val median = if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        }
        val variance = if (n <= 1) {
            0.0
        } else {
            sorted.sumOf { (it - mean) * (it - mean) } / n.toDouble()
        }
        return NumericSummary(
            count = n,
            min = min,
            max = max,
            mean = mean,
            median = median,
            stdDev = sqrt(variance),
        )
    }

    private class CsvFormatException(message: String) : IllegalArgumentException(message)

    private fun parseCsvRecords(input: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var currentRow = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStartedInQuotes = false
        var i = 0

        while (i < input.length) {
            val ch = input[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                        while (i < input.length && (input[i] == ' ' || input[i] == '\t')) {
                            i++
                        }
                        if (i < input.length && input[i] != ',' && input[i] != '\n' && input[i] != '\r') {
                            throw CsvFormatException("Unexpected character after closing quote in CSV.")
                        }
                        continue
                    }
                } else {
                    field.append(ch)
                    i++
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (field.isEmpty()) {
                            inQuotes = true
                            fieldStartedInQuotes = true
                            i++
                        } else {
                            throw CsvFormatException("Unexpected quote inside unquoted CSV field.")
                        }
                    }
                    ',' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        fieldStartedInQuotes = false
                        i++
                    }
                    '\r', '\n' -> {
                        if (ch == '\r' && i + 1 < input.length && input[i + 1] == '\n') {
                            i++
                        }
                        currentRow.add(field.toString())
                        field.setLength(0)
                        if (currentRow.size > 1 || currentRow[0].isNotEmpty() || fieldStartedInQuotes) {
                            rows.add(currentRow)
                        }
                        currentRow = ArrayList()
                        fieldStartedInQuotes = false
                        i++
                    }
                    else -> {
                        field.append(ch)
                        i++
                    }
                }
            }
        }

        if (inQuotes) {
            throw CsvFormatException("Unclosed quoted field in CSV.")
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty() || fieldStartedInQuotes) {
            currentRow.add(field.toString())
            rows.add(currentRow)
        }
        return rows
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Int = 1_048_576 // 1 MiB
        const val DEFAULT_MAX_ROWS: Int = 5_000
        const val DEFAULT_MAX_COLUMNS: Int = 128
    }
}
