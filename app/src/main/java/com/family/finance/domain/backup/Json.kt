package com.family.finance.domain.backup

/**
 * Minimal JSON for backup files: objects, arrays, strings, whole numbers (Long), booleans
 * and null. Decimal numbers are rejected on purpose, so money can only ever be read as
 * whole paise (CLAUDE.md). Strict: trailing garbage, bad escapes or nesting beyond
 * [MAX_DEPTH] are errors, never silently ignored.
 */
object Json {

    const val MAX_DEPTH = 32

    class ParseException(message: String) : Exception(message)

    fun write(value: Any?): String = StringBuilder().also { it.appendValue(value) }.toString()

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean -> append(value.toString())
            is Int, is Long -> append(value.toString())
            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(',')
                    appendString(k as String)
                    append(':')
                    appendValue(v)
                }
                append('}')
            }
            is List<*> -> {
                append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) append(',')
                    appendValue(v)
                }
                append(']')
            }
            else -> throw IllegalArgumentException("Cannot write ${value::class.simpleName} as backup JSON")
        }
    }

    /** Non-ASCII text (Tamil) is written as-is in UTF-8; only quotes, backslashes and control characters are escaped. */
    private fun StringBuilder.appendString(s: String) {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWhitespace()
        val value = p.value(0)
        p.skipWhitespace()
        if (!p.atEnd) throw ParseException("Unexpected text after the end at ${p.pos}")
        return value
    }

    private class Parser(private val s: String) {
        var pos = 0
        val atEnd get() = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && s[pos] in " \t\r\n") pos++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ParseException("Nested too deeply")
            if (atEnd) throw ParseException("Unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw ParseException("Unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("Bad literal at $pos")
            pos += word.length
            return result
        }

        private fun number(): Long {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && s[pos].isDigit()) pos++
            if (pos < s.length && s[pos] in ".eE") throw ParseException("Decimal numbers are not allowed (at $start)")
            return s.substring(start, pos).toLongOrNull() ?: throw ParseException("Bad number at $start")
        }

        private fun string(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd) throw ParseException("Unterminated text")
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd) throw ParseException("Unterminated escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Bad \\u escape")
                                val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: throw ParseException("Bad \\u escape")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Bad escape \\$e")
                        }
                    }
                    c < ' ' -> throw ParseException("Control character in text")
                    else -> sb.append(c)
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            pos++
            val out = mutableListOf<Any?>()
            skipWhitespace()
            if (!atEnd && s[pos] == ']') { pos++; return out }
            while (true) {
                skipWhitespace()
                out += value(depth + 1)
                skipWhitespace()
                if (atEnd) throw ParseException("Unterminated list")
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw ParseException("Expected , or ] at ${pos - 1}")
                }
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            pos++
            val out = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (!atEnd && s[pos] == '}') { pos++; return out }
            while (true) {
                skipWhitespace()
                if (atEnd || s[pos] != '"') throw ParseException("Expected a name at $pos")
                val key = string()
                if (key in out) throw ParseException("Repeated name \"$key\"")
                skipWhitespace()
                if (atEnd || s[pos++] != ':') throw ParseException("Expected : after \"$key\"")
                skipWhitespace()
                out[key] = value(depth + 1)
                skipWhitespace()
                if (atEnd) throw ParseException("Unterminated object")
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw ParseException("Expected , or } at ${pos - 1}")
                }
            }
        }
    }
}
