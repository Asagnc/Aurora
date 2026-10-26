package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.MessageDigest

/** Compiled trust catalogue. Updates create a different bundle; active tasks keep their pinned version. */
data class StaticReverseComponent(val id: String, val version: String, val url: String,
    val sha256: String, val compressedBytes: Long, val localOnly: Boolean = false) {
    init {
        require(id in setOf("jre", "jadx", "apktool", "jdk", "gradle", "platform", "build-tools", "android-host", "mitmdump"))
        if (localOnly) require(id == "android-host" && url.isEmpty())
        else {
            val address = runCatching { java.net.URI(url) }.getOrElse { throw IllegalArgumentException("Invalid tool archive address") }
            require(address.scheme == "https" && address.host in setOf("github.com", "dl.google.com") &&
                address.userInfo == null && address.rawQuery == null && address.fragment == null && address.port in setOf(-1, 443))
        }
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(compressedBytes in 1..(if (id in setOf("jre", "jadx", "apktool")) 128L else 512L) * 1024 * 1024)
    }
}

object StaticReverseTools {
    val components = listOf(
        StaticReverseComponent("jre", "27+35",
            "https://github.com/adoptium/temurin27-binaries/releases/download/jdk-27%2B35/OpenJDK27U-jre_aarch64_linux_hotspot_27_35.tar.gz",
            "a41b54098373f1ca8f75ee344db19b93ac39eca6c529c3ee9dc50f4b3e7857de", 60962125),
        StaticReverseComponent("jadx", "1.5.6",
            "https://github.com/skylot/jadx/releases/download/v1.5.6/jadx-1.5.6.zip",
            "545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974", 72646741),
        StaticReverseComponent("apktool", "3.0.3",
            "https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar",
            "dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423", 15478013))
    val bundleId: String = MessageDigest.getInstance("SHA-256")
        .digest(components.joinToString("\n") { "${it.id}:${it.version}:${it.sha256}:${it.compressedBytes}" }.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val compressedBytes: Long get() = components.sumOf { it.compressedBytes }
    val catalog get() = SharedToolCatalog("static-reverse-v1", "legacy", components, 800L * 1024 * 1024)
}

/** IO-thread operation; no following links, partial files, or trusting an adjacent digest file. */
fun verifyStaticReverseArchive(component: StaticReverseComponent, archive: Path) {
    val absolute = archive.toAbsolutePath().normalize()
    var current = absolute.root
    for (part in absolute) {
        current = current.resolve(part)
        require(!Files.isSymbolicLink(current)) { "Tool archive path contains a link" }
    }
    val before = Files.readAttributes(absolute, java.nio.file.attribute.BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    require(before.isRegularFile && before.size() == component.compressedBytes) { "Tool archive size mismatch" }
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    Files.newInputStream(absolute, LinkOption.NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Tool verification cancelled")
            val count = input.read(buffer); if (count < 0) break
            total += count; require(total <= component.compressedBytes)
            digest.update(buffer, 0, count)
        }
    }
    val after = Files.readAttributes(absolute, java.nio.file.attribute.BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    require(total == component.compressedBytes && before.fileKey() == after.fileKey() &&
        before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime()) { "Tool archive changed" }
    require(digest.digest().joinToString("") { "%02x".format(it) } == component.sha256) { "Tool archive digest mismatch" }
}
