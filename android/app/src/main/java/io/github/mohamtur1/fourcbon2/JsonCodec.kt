package io.github.mohamtur1.fourcbon2

/**
 * Deterministic, dependency-free RFC-8259 JSON parser and serializer that runs identically on
 * Android devices and in pure-JVM unit tests (avoiding Android's stubbed org.json classes).
 */
sealed interface JsonValue {
    data object Null : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data class Num(val value: Double, val raw: String = formatDouble(value)) : JsonValue {
        fun toLongOrNull(): Long? = raw.toLongOrNull() ?: value.toLong().takeIf { it.toDouble() == value }
        fun toIntOrNull(): Int? = toLongOrNull()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    }
    data class Str(val value: String) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue {
        fun string(key: String): String? = (entries[key] as? Str)?.value
        fun stringOrDefault(key: String, default: String = ""): String = string(key) ?: default
        fun bool(key: String, default: Boolean = false): Boolean = (entries[key] as? Bool)?.value ?: default
        fun long(key: String, default: Long = 0L): Long = (entries[key] as? Num)?.toLongOrNull() ?: default
        fun int(key: String, default: Int = 0): Int = (entries[key] as? Num)?.toIntOrNull() ?: default
        fun double(key: String, default: Double = 0.0): Double = (entries[key] as? Num)?.value ?: default
        fun array(key: String): List<JsonValue> = (entries[key] as? Arr)?.items ?: emptyList()
        fun stringList(key: String): List<String> = array(key).mapNotNull { (it as? Str)?.value }
        fun obj(key: String): Obj? = entries[key] as? Obj
    }

    companion object {
        private fun formatDouble(d: Double): String {
            val asLong = d.toLong()
            return if (asLong.toDouble() == d) asLong.toString() else d.toString()
        }
    }
}

class JsonParseException(message: String, val offset: Int = -1) : IllegalArgumentException(
    if (offset >= 0) "$message (at offset $offset)" else message,
)

object JsonCodec {
    fun stringify(value: JsonValue): String {
        val sb = StringBuilder()
        writeValue(sb, value)
        return sb.toString()
    }

    fun parse(input: String): JsonValue {
        if (input.isBlank()) throw JsonParseException("JSON input is empty.", 0)
        val parser = Parser(input)
        val result = parser.parseValue(depth = 0)
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw JsonParseException("Unexpected trailing content.", parser.pos)
        }
        return result
    }

    fun parseObject(input: String): JsonValue.Obj =
        parse(input) as? JsonValue.Obj ?: throw JsonParseException("Expected top-level JSON object.")

    private fun writeValue(sb: StringBuilder, value: JsonValue) {
        when (value) {
            JsonValue.Null -> sb.append("null")
            is JsonValue.Bool -> sb.append(if (value.value) "true" else "false")
            is JsonValue.Num -> {
                if (!value.value.isFinite()) {
                    sb.append("null")
                } else {
                    sb.append(value.raw)
                }
            }
            is JsonValue.Str -> writeEscapedString(sb, value.value)
            is JsonValue.Arr -> {
                sb.append('[')
                value.items.forEachIndexed { idx, item ->
                    if (idx > 0) sb.append(',')
                    writeValue(sb, item)
                }
                sb.append(']')
            }
            is JsonValue.Obj -> {
                sb.append('{')
                var first = true
                for ((k, v) in value.entries) {
                    if (!first) sb.append(',')
                    first = false
                    writeEscapedString(sb, k)
                    sb.append(':')
                    writeValue(sb, v)
                }
                sb.append('}')
            }
        }
    }

    private fun writeEscapedString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }

    private class Parser(private val src: String) {
        var pos: Int = 0

        fun isAtEnd(): Boolean = pos >= src.length

        fun skipWhitespace() {
            while (pos < src.length) {
                when (src[pos]) {
                    ' ', '\t', '\r', '\n' -> pos++
                    else -> return
                }
            }
        }

        fun parseValue(depth: Int): JsonValue {
            if (depth > MAX_NESTING_DEPTH) {
                throw JsonParseException("JSON exceeds maximum nesting depth ($MAX_NESTING_DEPTH).", pos)
            }
            skipWhitespace()
            if (isAtEnd()) throw JsonParseException("Unexpected end of JSON.", pos)
            return when (val ch = src[pos]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> JsonValue.Str(parseString())
                't' -> parseLiteral("true", JsonValue.Bool(true))
                'f' -> parseLiteral("false", JsonValue.Bool(false))
                'n' -> parseLiteral("null", JsonValue.Null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw JsonParseException("Unexpected character '$ch'.", pos)
            }
        }

        private fun parseLiteral(expected: String, result: JsonValue): JsonValue {
            if (!src.startsWith(expected, pos)) {
                throw JsonParseException("Expected '$expected'.", pos)
            }
            pos += expected.length
            return result
        }

        private fun parseObject(depth: Int): JsonValue.Obj {
            pos++ // consume '{'
            skipWhitespace()
            if (isAtEnd()) throw JsonParseException("Unterminated JSON object.", pos)
            val map = LinkedHashMap<String, JsonValue>()
            if (src[pos] == '}') {
                pos++
                return JsonValue.Obj(map)
            }
            while (true) {
                skipWhitespace()
                if (isAtEnd() || src[pos] != '"') {
                    throw JsonParseException("Expected quoted object key.", pos)
                }
                val key = parseString()
                skipWhitespace()
                if (isAtEnd() || src[pos] != ':') {
                    throw JsonParseException("Expected ':' after object key.", pos)
                }
                pos++
                val value = parseValue(depth)
                map[key] = value
                skipWhitespace()
                if (isAtEnd()) throw JsonParseException("Unterminated JSON object.", pos)
                when (src[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonValue.Obj(map)
                    }
                    else -> throw JsonParseException("Expected ',' or '}' in object.", pos)
                }
            }
        }

        private fun parseArray(depth: Int): JsonValue.Arr {
            pos++ // consume '['
            skipWhitespace()
            if (isAtEnd()) throw JsonParseException("Unterminated JSON array.", pos)
            val list = ArrayList<JsonValue>()
            if (src[pos] == ']') {
                pos++
                return JsonValue.Arr(list)
            }
            while (true) {
                list.add(parseValue(depth))
                skipWhitespace()
                if (isAtEnd()) throw JsonParseException("Unterminated JSON array.", pos)
                when (src[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonValue.Arr(list)
                    }
                    else -> throw JsonParseException("Expected ',' or ']' in array.", pos)
                }
            }
        }

        private fun parseString(): String {
            pos++ // consume opening '"'
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when {
                    ch == '"' -> return sb.toString()
                    ch == '\\' -> {
                        if (pos >= src.length) throw JsonParseException("Unterminated escape sequence.", pos)
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) {
                                    throw JsonParseException("Incomplete unicode escape.", pos)
                                }
                                val hex = src.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonParseException("Invalid unicode escape '\\u$hex'.", pos)
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonParseException("Invalid escape character '\\$esc'.", pos - 1)
                        }
                    }
                    ch.code < 0x20 -> throw JsonParseException("Unescaped control character in string.", pos - 1)
                    else -> sb.append(ch)
                }
            }
            throw JsonParseException("Unterminated JSON string.", pos)
        }

        private fun parseNumber(): JsonValue.Num {
            val start = pos
            if (src[pos] == '-') pos++
            if (pos >= src.length || src[pos] !in '0'..'9') {
                throw JsonParseException("Invalid number.", start)
            }
            if (src[pos] == '0') {
                pos++
            } else {
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && src[pos] == '.') {
                pos++
                if (pos >= src.length || src[pos] !in '0'..'9') {
                    throw JsonParseException("Invalid fractional number.", start)
                }
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                if (pos >= src.length || src[pos] !in '0'..'9') {
                    throw JsonParseException("Invalid exponent in number.", start)
                }
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val raw = src.substring(start, pos)
            val parsed = raw.toDoubleOrNull()
                ?: throw JsonParseException("Number out of range.", start)
            if (!parsed.isFinite()) {
                throw JsonParseException("Non-finite number.", start)
            }
            return JsonValue.Num(parsed, raw)
        }

        companion object {
            private const val MAX_NESTING_DEPTH = 64
        }
    }
}
