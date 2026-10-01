package com.riftdeck.core.update

import java.io.IOException

/** Android's JSONTokener accepts comments and unquoted values; reject those before typed parsing. */
internal fun validateStrictReleaseJson(text: String) {
    if (text.length > MAX_UPDATE_METADATA_BYTES) throw IOException("Release metadata exceeded limits")
    StrictReleaseJson(text).validate()
}

private class StrictReleaseJson(private val text: String) {
    private var position = 0
    fun validate() { value(0); whitespace(); if (position != text.length) invalid() }

    private fun value(depth: Int) {
        if (depth > 32) invalid()
        whitespace()
        when (peek()) {
            '{' -> objectValue(depth + 1)
            '[' -> arrayValue(depth + 1)
            '"' -> stringValue()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> numberValue()
            else -> invalid()
        }
    }

    private fun objectValue(depth: Int) {
        position++
        whitespace()
        if (consume('}')) return
        val keys = mutableSetOf<String>()
        while (true) {
            whitespace()
            if (peek() != '"' || !keys.add(stringValue())) invalid()
            whitespace()
            if (!consume(':')) invalid()
            value(depth)
            whitespace()
            if (consume('}')) return
            if (!consume(',')) invalid()
        }
    }

    private fun arrayValue(depth: Int) {
        position++
        whitespace()
        if (consume(']')) return
        while (true) {
            value(depth)
            whitespace()
            if (consume(']')) return
            if (!consume(',')) invalid()
        }
    }

    private fun stringValue(): String {
        position++
        val result = StringBuilder()
        var pendingHigh = false
        while (position < text.length) {
            var unit = text[position++]
            if (unit == '"') {
                if (pendingHigh) invalid()
                return result.toString()
            }
            if (unit.code < 32) invalid()
            if (unit == '\\') {
                if (position == text.length) invalid()
                unit = when (val escaped = text[position++]) {
                    '"', '\\', '/' -> escaped
                    'b' -> '\b'
                    'f' -> '\u000c'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'u' -> {
                        if (position + 4 > text.length) invalid()
                        val hex = text.substring(position, position + 4)
                        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) invalid()
                        position += 4
                        hex.toInt(16).toChar()
                    }
                    else -> invalid()
                }
            }
            if (pendingHigh) {
                if (!unit.isLowSurrogate()) invalid()
                pendingHigh = false
            } else {
                if (unit.isLowSurrogate()) invalid()
                pendingHigh = unit.isHighSurrogate()
            }
            result.append(unit)
        }
        invalid()
    }

    private fun numberValue() {
        val start = position
        consume('-')
        if (!consume('0')) {
            if (peek() !in '1'..'9') invalid()
            while (peek() in '0'..'9') position++
        }
        if (consume('.')) {
            if (peek() !in '0'..'9') invalid()
            while (peek() in '0'..'9') position++
        }
        if (peek() == 'e' || peek() == 'E') {
            position++
            if (peek() == '+' || peek() == '-') position++
            if (peek() !in '0'..'9') invalid()
            while (peek() in '0'..'9') position++
        }
        if (position - start > 128) invalid()
    }

    private fun literal(value: String) {
        if (!text.regionMatches(position, value, 0, value.length)) invalid()
        position += value.length
    }
    private fun whitespace() { while (peek() == ' ' || peek() == '\t' || peek() == '\r' || peek() == '\n') position++ }
    private fun peek(): Char = text.getOrNull(position) ?: '\u0000'
    private fun consume(value: Char): Boolean = if (peek() == value) { position++; true } else false
    private fun invalid(): Nothing = throw IOException("Invalid release JSON")
}
