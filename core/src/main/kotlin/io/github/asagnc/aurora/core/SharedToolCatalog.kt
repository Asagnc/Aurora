package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/** Extends the existing one-store installation/lease lifecycle; no second tool database or task manager. */
data class SharedToolCatalog(val family: String, val revision: String, val components: List<StaticReverseComponent>,
    val headroomBytes: Long, val finish: (Path) -> Unit = {}) {
    init {
        require(family in SharedToolLayout.families && revision.matches(Regex("[a-zA-Z0-9_.+-]{1,128}")))
        require(components.isNotEmpty() && components.size <= 16 && components.map { it.id }.distinct().size == components.size)
        require(headroomBytes in 800L * 1024 * 1024..4L * 1024 * 1024 * 1024)
    }
    val compressedBytes: Long get() = components.sumOf { it.compressedBytes }
    val bundleId: String get() {
        val records = components.joinToString("\n") { "${it.id}:${it.version}:${it.sha256}:${it.compressedBytes}" }
        val identity = if (family == "static-reverse-v1" && revision == "legacy") records else "$family:$revision\n$records"
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

object SharedToolLayout {
    val families = setOf("static-reverse-v1", "android-build-v1", "network-proxy-v1")
    private val required = mapOf("static-reverse-v1" to listOf("jre", "jadx", "apktool"),
        "android-build-v1" to listOf("jdk", "gradle", "sdk", "android-host"), "network-proxy-v1" to listOf("mitmdump"))

    fun family(directory: Path): String? {
        val marker = directory.resolve("family")
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return "static-reverse-v1"
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) !in 1..64) return null
        return Files.readAllBytes(marker).toString(Charsets.UTF_8).takeIf { it in families }
    }

    fun complete(directory: Path): Boolean {
        val family = family(directory) ?: return false
        return required.getValue(family).all { Files.isDirectory(directory.resolve(it), LinkOption.NOFOLLOW_LINKS) }
    }
}
