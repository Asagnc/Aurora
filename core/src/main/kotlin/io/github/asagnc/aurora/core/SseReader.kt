package io.github.asagnc.aurora.core

import java.io.BufferedReader
import java.io.Reader

data class SseEvent(val event: String?, val data: String)

class SseReader(reader: Reader, private val maxEventChars: Int = 2 * 1024 * 1024) {
    private val source = if (reader is BufferedReader) reader else reader.buffered()
    private var firstLine = true

    private fun readBoundedLine(): String? {
        val line = StringBuilder()
        while (true) {
            val value = source.read()
            if (value == -1) return line.takeIf { it.isNotEmpty() }?.toString()
            if (value == 10) return line.toString()
            if (value == 13) {
                source.mark(1)
                if (source.read() != 10) source.reset()
                return line.toString()
            }
            require(line.length < maxEventChars) { "SSE line exceeded the response budget" }
            line.append(value.toChar())
        }
    }

    fun next(): SseEvent? {
        var name: String? = null
        val parts = mutableListOf<String>()
        var size = 0
        while (true) {
            var line = readBoundedLine() ?: return if (parts.isEmpty()) null else SseEvent(name, parts.joinToString("\n"))
            if (firstLine) { line = line.removePrefix("\uFEFF"); firstLine = false }
            if (line.isEmpty()) {
                if (parts.isNotEmpty()) return SseEvent(name, parts.joinToString("\n"))
                name = null
                continue
            }
            if (line.startsWith(":")) continue
            val key = line.substringBefore(':')
            val value = line.substringAfter(':', "").removePrefix(" ")
            when (key) {
                "event" -> name = value
                "data" -> {
                    size += value.length + 1
                    require(size <= maxEventChars) { "SSE event exceeded the response budget" }
                    parts += value
                }
            }
        }
    }
}
