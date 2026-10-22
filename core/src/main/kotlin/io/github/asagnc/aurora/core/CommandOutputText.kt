package io.github.asagnc.aurora.core

/** Preserve binary classification; only a valid incomplete UTF-8 tail may be omitted. */
fun commandOutputText(bytes: ByteArray, truncated: Boolean): String? {
    if (bytes.any { val value = it.toInt(); value in 0..31 && value != 9 && value != 10 && value != 13 && value != 27 }) return null
    return decodeUtf8FileSlice(bytes, allowPartialEnd = truncated).text
}
