package io.github.asagnc.aurora.core

/** Braces inside quoted logs are data, not JSON nesting. The parser still validates the grammar. */
internal fun jsonWithinDepth(text: String, limit: Int = 64): Boolean {
    var depth = 0
    var quoted = false
    var escaped = false
    for (char in text) {
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else when (char) {
            '"' -> quoted = true
            '{', '[' -> { depth++; if (depth > limit) return false }
            '}', ']' -> { depth--; if (depth < 0) return false }
        }
    }
    return depth == 0 && !quoted
}
