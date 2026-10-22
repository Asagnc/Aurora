package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

data class Utf8FileSlice(val text: String?, val consumed: Int)

/** Frame the incomplete tail explicitly: Android ICU may consume it into decoder state. */
fun decodeUtf8FileSlice(bytes: ByteArray, allowPartialEnd: Boolean = true): Utf8FileSlice {
    if (bytes.isEmpty()) return Utf8FileSlice("", 0)
    val end = if (allowPartialEnd) completeUtf8Prefix(bytes) else bytes.size
    if (end == 0) return Utf8FileSlice(null, bytes.size)
    val input = ByteBuffer.wrap(bytes, 0, end)
    val output = CharBuffer.allocate(end)
    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val result = decoder.decode(input, output, true)
    if (result.isError || result.isOverflow || decoder.flush(output).isError) return Utf8FileSlice(null, bytes.size)
    output.flip()
    return Utf8FileSlice(output.toString(), end)
}

/** Only trim a tail that can still become a valid RFC3629 scalar, never invalid binary. */
private fun completeUtf8Prefix(bytes: ByteArray): Int {
    var lead = bytes.lastIndex
    var continuations = 0
    while (lead >= 0 && bytes[lead].toInt() and 255 in 0x80..0xbf && continuations < 3) {
        lead--; continuations++
    }
    if (lead < 0) return bytes.size
    val first = bytes[lead].toInt() and 255
    val width = when (first) { in 0xc2..0xdf -> 2; in 0xe0..0xef -> 3; in 0xf0..0xf4 -> 4; else -> 1 }
    if (bytes.size - lead >= width) return bytes.size
    if (continuations > 0) {
        val second = bytes[lead + 1].toInt() and 255
        if ((first == 0xe0 && second < 0xa0) || (first == 0xed && second >= 0xa0) ||
            (first == 0xf0 && second < 0x90) || (first == 0xf4 && second >= 0x90)) return bytes.size
    }
    return lead
}
