package io.github.asagnc.aurora.core

/** Raw PTY byte cursors survive redraws. Screen snapshots have separate semantics. */
class TerminalBytes(private val capacity: Int = 65536) {
    init { require(capacity in 4096..1048576) }
    private val bytes = ByteArray(capacity)
    private var end = 0L
    val sequence: Long @Synchronized get() = end

    @Synchronized fun append(source: ByteArray, count: Int) {
        require(count in 0..source.size)
        require(end <= Long.MAX_VALUE - count)
        val first = maxOf(0, count - capacity)
        for (index in first until count) bytes[((end + index) % capacity).toInt()] = source[index]
        end += count
    }

    @Synchronized fun read(cursor: Long, limit: Int = 8192): Slice {
        require(cursor in 0..end && limit in 1..65536)
        val start = maxOf(cursor, end - capacity)
        val count = minOf(limit.toLong(), end - start).toInt()
        val result = ByteArray(count) { bytes[((start + it) % capacity).toInt()] }
        return Slice(result, start, start + count, end, start != cursor)
    }

    data class Slice(val bytes: ByteArray, val start: Long, val next: Long, val end: Long, val dropped: Boolean)
}
