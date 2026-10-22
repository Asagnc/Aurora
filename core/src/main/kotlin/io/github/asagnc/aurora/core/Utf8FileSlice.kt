package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

data class Utf8FileSlice(val text: String?, val consumed: Int)

/** A single decoding pass leaves an incomplete trailing character for the next slice. */
fun decodeUtf8FileSlice(bytes: ByteArray, allowPartialEnd: Boolean = true): Utf8FileSlice {
    if (bytes.isEmpty()) return Utf8FileSlice("", 0)
    val input = ByteBuffer.wrap(bytes)
    val output = CharBuffer.allocate(bytes.size)
    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val result = decoder.decode(input, output, !allowPartialEnd)
    if (result.isError || result.isOverflow || input.position() == 0) return Utf8FileSlice(null, bytes.size)
    if (!allowPartialEnd && decoder.flush(output).isError) return Utf8FileSlice(null, bytes.size)
    output.flip()
    return Utf8FileSlice(output.toString(), input.position())
}
