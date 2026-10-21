package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.CharBuffer

data class SkillTextSlice(val text: String?, val consumed: Int)

/** A byte cursor stops before an incomplete UTF-8 codepoint, so the next read can decode it intact. */
fun decodeSkillText(bytes: ByteArray, endOfFile: Boolean): SkillTextSlice {
    val input = ByteBuffer.wrap(bytes)
    val output = CharBuffer.allocate(bytes.size)
    val decoder = Charsets.UTF_8.newDecoder()
    val result = decoder.decode(input, output, endOfFile)
    if (result.isError || result.isOverflow) return SkillTextSlice(null, bytes.size)
    if (endOfFile && decoder.flush(output).isError) return SkillTextSlice(null, bytes.size)
    output.flip()
    return SkillTextSlice(output.toString(), input.position())
}
