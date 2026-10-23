package io.github.asagnc.aurora.core

import java.util.Locale
import kotlinx.serialization.json.*

/** Untrusted display hints, never paths, content verification or execution decisions. */
data class BrowserDownloadMetadata(val displayName: String, val mimeType: String)

fun browserDownloadMetadata(name: String?, mime: String?): BrowserDownloadMetadata {
    val leaf = name.orEmpty().substringAfterLast('/').substringAfterLast('\\')
    val cleaned = buildString {
        leaf.codePoints().forEach { point ->
            if (!Character.isISOControl(point) && Character.getType(point) != Character.FORMAT.toInt() &&
                point !in 0xD800..0xDFFF && point !in listOf(0x2028, 0x2029)) appendCodePoint(point)
        }
    }.trim().take(120).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
    val display = cleaned.takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: "download.bin"
    val type = mime.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
    val valid = Regex("[a-z0-9!#$&^_.+*-]+/[a-z0-9!#$&^_.+*-]+")
    return BrowserDownloadMetadata(display, type.takeIf { it.length <= 128 && valid.matches(it) && '*' !in it }
        ?: "application/octet-stream")
}

data class BrowserDownloadArtifact(val path: String, val metadata: BrowserDownloadMetadata, val bytes: Long)

fun browserDownloadArtifact(encoded: String?): BrowserDownloadArtifact? {
    if (encoded == null || encoded.length > 8000 || !jsonWithinDepth(encoded)) return null
    return runCatching {
        val value = WireJson.parseToJsonElement(encoded).jsonObject
        val path = requireNotNull(value.text("path"))
        require(path.endsWith("/download.bin"))
        require(commandArtifactId(path.removeSuffix("/download.bin")) != null)
        val bytes = requireNotNull(value.number("bytes")).also { require(it >= 0) }
        BrowserDownloadArtifact(path, browserDownloadMetadata(value.text("displayName"), value.text("mimeType")), bytes)
    }.getOrNull()
}
