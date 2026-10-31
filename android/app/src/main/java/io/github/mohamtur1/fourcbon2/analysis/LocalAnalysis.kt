package io.github.mohamtur1.fourcbon2.analysis

import java.io.File

/**
 * Small local analysis routine (Division 18).
 *
 * One bounded CSV/JSON routine with explicit validation. Invalid fixtures
 * are rejected with a clear error — no silent partial results.
 */
object LocalAnalysis {

    data class ColumnStats(
        val name: String,
        val count: Int,
        val sum: Double,
        val mean: Double,
    )

    data class CsvAnalysis(
        val header: List<String>,
        val rows: Int,
        val numericColumns: List<ColumnStats>,
    )

    data class JsonAnalysis(
        val topLevelKeys: List<String>,
        val objectCount: Int,
    )

    /** Parse a small CSV with a header row. Returns per-column stats for numeric values. */
    fun analyzeCsv(file: File): CsvAnalysis {
        require(file.exists()) { "CSV file does not exist: ${file.name}" }
        val lines = file.readLines().filter { it.isNotBlank() }
        require(lines.size >= 2) { "CSV needs a header and at least one row." }
        val header = lines[0].split(",").map { it.trim() }
        require(header.all { it.isNotBlank() }) { "CSV header contains blank names." }
        val rows = lines.drop(1)
        val numericColumns = header.map { name ->
            val values = rows.mapNotNull { row ->
                row.split(",").map { it.trim() }.getOrNull(header.indexOf(name))?.toDoubleOrNull()
            }
            if (values.isEmpty()) {
                ColumnStats(name, 0, 0.0, 0.0)
            } else {
                val sum = values.sum()
                ColumnStats(name, values.size, sum, sum / values.size)
            }
        }.filter { it.count > 0 }
        return CsvAnalysis(header, rows.size, numericColumns)
    }

    /** Validate a JSON document structurally and extract basic facts. */
    fun analyzeJson(file: File): JsonAnalysis {
        require(file.exists()) { "JSON file does not exist: ${file.name}" }
        val text = file.readText()
        require(isValidJson(text)) { "JSON is not well-formed." }
        return JsonAnalysis(topLevelKeys(text), countObjects(text))
    }

    /** Structural validation: balanced delimiters and quoted strings. */
    fun isValidJson(text: String): Boolean = try {
        JsonStructure.validate(text)
        true
    } catch (e: Exception) {
        false
    }

    private fun topLevelKeys(text: String): List<String> {
        JsonStructure.validate(text)
        return JsonStructure.topLevelKeys(text)
    }

    private fun countObjects(text: String): Int = JsonStructure.countObjects(text)

    /** Minimal structural JSON scanner: delimiter balance, string escaping, key shape. */
    private object JsonStructure {
        fun validate(text: String) {
            val stack = ArrayDeque<Char>()
            var i = 0
            var inString = false
            while (i < text.length) {
                val ch = text[i]
                if (inString) {
                    if (ch == '\\') {
                        i += 2
                        continue
                    }
                    if (ch == '"') inString = false
                } else {
                    when (ch) {
                        '"' -> inString = true
                        '{', '[' -> stack.addLast(ch)
                        '}' -> {
                            if (stack.removeLastOrNull() != '{') {
                                throw IllegalArgumentException("Unbalanced '}' at $i")
                            }
                        }
                        ']' -> {
                            if (stack.removeLastOrNull() != '[') {
                                throw IllegalArgumentException("Unbalanced ']' at $i")
                            }
                        }
                        else -> {}
                    }
                }
                i++
            }
            if (inString) throw IllegalArgumentException("Unterminated string.")
            if (stack.isNotEmpty()) throw IllegalArgumentException("Unclosed delimiter: ${stack.last()}")
        }

        fun topLevelKeys(text: String): List<String> {
            validate(text)
            val keys = mutableListOf<String>()
            var i = skipWhitespace(text, 0)
            if (i >= text.length || text[i] != '{') {
                return emptyList()
            }
            i++
            i = skipWhitespace(text, i)
            if (i < text.length && text[i] == '}') return emptyList()
            while (i < text.length) {
                i = skipWhitespace(text, i)
                if (text[i] != '"') throw IllegalArgumentException("Expected object key at $i")
                val keyStart = i + 1
                i++
                while (i < text.length && text[i] != '"') {
                    if (text[i] == '\\') i++
                    i++
                }
                if (i >= text.length) throw IllegalArgumentException("Unterminated key.")
                keys.add(text.substring(keyStart, i))
                i++
                i = skipWhitespace(text, i)
                if (i >= text.length || text[i] != ':') {
                    throw IllegalArgumentException("Expected ':' after key at $i")
                }
                i++
                i = skipWhitespace(text, i)
                i = skipValue(text, i)
                i = skipWhitespace(text, i)
                if (i >= text.length) throw IllegalArgumentException("Unterminated object.")
                if (text[i] == '}') break
                if (text[i] != ',') throw IllegalArgumentException("Expected ',' or '}' at $i")
                i++
            }
            return keys
        }

        fun countObjects(text: String): Int {
            validate(text)
            var count = 0
            for (ch in text) {
                if (ch == '{') count++
            }
            return count
        }

        private fun skipWhitespace(text: String, i: Int): Int {
            var j = i
            while (j < text.length && text[j].isWhitespace()) j++
            return j
        }

        private fun skipValue(text: String, start: Int): Int {
            var i = skipWhitespace(text, start)
            if (i >= text.length) throw IllegalArgumentException("Unexpected end of JSON.")
            return when (text[i]) {
                '{' -> skipContainer(text, i, '{', '}')
                '[' -> skipContainer(text, i, '[', ']')
                '"' -> {
                    i++
                    while (i < text.length) {
                        if (text[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (text[i] == '"') return i + 1
                        i++
                    }
                    throw IllegalArgumentException("Unterminated string.")
                }
                else -> {
                    while (i < text.length && !text[i].isWhitespace() && text[i] !in listOf(',', '}', ']')) {
                        i++
                    }
                    i
                }
            }
        }

        private fun skipContainer(text: String, start: Int, open: Char, close: Char): Int {
            var i = start
            var depth = 0
            var inString = false
            while (i < text.length) {
                val ch = text[i]
                if (inString) {
                    if (ch == '\\') {
                        i += 2
                        continue
                    }
                    if (ch == '"') inString = false
                } else {
                    if (ch == '"') inString = true
                    else if (ch == open) depth++
                    else if (ch == close) {
                        depth--
                        if (depth == 0) return i + 1
                    }
                }
                i++
            }
            throw IllegalArgumentException("Unterminated container starting at $start")
        }
    }
}
