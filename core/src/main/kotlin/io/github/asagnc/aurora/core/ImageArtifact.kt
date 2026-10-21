package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable data class ImageArtifact(val id: String, val mime: String, val width: Int, val height: Int,
    val bytes: Long, val sha256: String, val description: String) {
    val link get() = "aurora-image:$id"
    val extension get() = when (mime) {
        "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"
        "image/avif" -> "avif"; "image/heif" -> "heif"; "image/heic" -> "heic"; "image/bmp", "image/x-ms-bmp" -> "bmp"
        else -> "img"
    }
}

fun imageArtifactId(link: String): String? {
    if (!link.startsWith("aurora-image:")) return null
    val id = link.removePrefix("aurora-image:")
    return runCatching { UUID.fromString(id).toString().takeIf { it == id } }.getOrNull()
}

/** Labels cannot break out of a Markdown image's alt-text and inject a different source. */
fun imageMarkdown(artifact: ImageArtifact): String {
    val label = artifact.description.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
        .replace('\n', ' ').replace('\r', ' ')
    return "![$label](${artifact.link})"
}
