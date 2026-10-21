package io.github.asagnc.aurora.core

/** Bound the in-chat surface; the separate reader retains the complete source. */
fun replyPreviewEnd(text: String): Int {
    var end = minOf(text.length, 24000)
    var search = 0
    repeat(5) { index ->
        val image = text.indexOf("![", search)
        if (image < 0 || image >= end) return@repeat
        if (index == 4) end = image else search = image + 2
    }
    if (end > 0 && end < text.length && text[end - 1].isHighSurrogate()) end--
    return end
}
