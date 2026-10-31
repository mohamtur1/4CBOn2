package io.github.mohamtur1.fourcbon2.local

/**
 * Minimal line-delimited record codec.
 *
 * Each record is one line with fields separated by a unit-separator control
 * character. Values escape backslash, newline, carriage return, and the
 * field separator as `\uXXXX` escapes so arbitrary text round-trips safely.
 *
 * Kept dependency-free so the store runs in local JVM unit tests.
 */
object RecordCodec {
    const val FIELD_SEP = '\u001f'

    fun escape(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\u005c")
                '\n' -> sb.append("\\u000a")
                '\r' -> sb.append("\\u000d")
                FIELD_SEP -> sb.append("\\u001f")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun unescape(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 6 < value.length) {
                sb.append(value.substring(i + 1, i + 7).toInt(16).toChar())
                i += 7
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    fun join(fields: List<String>): String =
        fields.joinToString(FIELD_SEP.toString()) { escape(it) }

    fun split(line: String): List<String> =
        line.split(FIELD_SEP).map { unescape(it) }

    fun joinMap(map: Map<String, String>): String =
        map.entries.joinToString(separator = ";") { (k, v) -> "$k=$v" }

    fun splitMap(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        return text.split(";").associate { entry ->
            val idx = entry.indexOf('=')
            require(idx > 0) { "Invalid map entry: $entry" }
            entry.substring(0, idx) to entry.substring(idx + 1)
        }
    }

    fun joinList(items: List<String>): String = items.joinToString(separator = ";")

    fun splitList(text: String): List<String> =
        if (text.isBlank()) emptyList() else text.split(";")
}
