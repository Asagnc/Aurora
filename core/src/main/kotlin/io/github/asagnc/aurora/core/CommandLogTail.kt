package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

fun commandArtifactId(directory: String): String? {
    val id = directory.removePrefix("runs/")
    return runCatching { UUID.fromString(id).toString().takeIf { directory == "runs/$it" } }.getOrNull()
}

data class LogStreamTail(val text: String = "", val bytes: Long = 0, val previewClipped: Boolean = false, val reset: Boolean = false)
data class CommandLogSnapshot(val stdout: LogStreamTail = LogStreamTail(), val stderr: LogStreamTail = LogStreamTail(),
    val pending: Boolean = false, val error: String? = null)

/** Single-consumer, event-triggered reader. Bytes on disk are never polled or rewritten here. */
class CommandLogTail(private val root: Path, directory: String) {
    private val id = requireNotNull(commandArtifactId(directory))
    private val output = Stream()
    private val errors = Stream()

    init { require(root.isAbsolute) }

    fun snapshot(complete: Boolean = false): CommandLogSnapshot {
        var path = root
        require(!Files.isSymbolicLink(path))
        for (part in listOf("runs", id)) {
            path = path.resolve(part)
            require(!Files.isSymbolicLink(path)) { "Log directories cannot follow links" }
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return CommandLogSnapshot(pending = true)
            require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        }
        return CommandLogSnapshot(output.read(path.resolve("stdout.bin"), complete), errors.read(path.resolve("stderr.bin"), complete))
    }

    private class Stream {
        private var cursor = 0L
        private var tail = byteArrayOf()
        private var key: String? = null
        private var clipped = false
        private var replaced = false

        fun read(path: Path, complete: Boolean): LogStreamTail {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return LogStreamTail()
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            val nextKey = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).fileKey()?.toString()
            FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                val size = channel.size()
                if (size < cursor || (key != null && key != nextKey)) {
                    cursor = 0; tail = byteArrayOf(); clipped = false; replaced = true
                }
                key = nextKey
                if (size - cursor > LIMIT) { cursor = size - LIMIT; tail = byteArrayOf(); clipped = true }
                val buffer = ByteBuffer.allocate((size - cursor).toInt())
                channel.position(cursor)
                while (buffer.hasRemaining()) { if (channel.read(buffer) <= 0) break }
                val chunk = buffer.array().copyOf(buffer.position())
                cursor += chunk.size
                if (tail.size + chunk.size > LIMIT) clipped = true
                if (chunk.isNotEmpty()) {
                    val combined = tail + chunk
                    tail = combined.copyOfRange(maxOf(0, combined.size - LIMIT), combined.size)
                }
                return LogStreamTail(decode(tail, complete), cursor, clipped, replaced)
            }
        }

        private fun decode(bytes: ByteArray, complete: Boolean): String {
            var start = 0
            if (clipped) while (start < minOf(3, bytes.size) && (bytes[start].toInt() and 0xc0) == 0x80) start++
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
            val chars = CharBuffer.allocate(bytes.size)
            decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start), chars, complete)
            chars.flip()
            return chars.toString()
        }
    }

    companion object { const val LIMIT = 65536 }
}
