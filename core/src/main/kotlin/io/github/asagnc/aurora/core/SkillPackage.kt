package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class SkillFile(val path: String, val size: Long, val sha256: String, val executable: Boolean = false)
@Serializable data class SkillManifest(val metadata: SkillMetadata, val files: List<SkillFile>) {
    val digest get() = skillDigest(WireJson.encodeToString(this).toByteArray())
    val bytes get() = files.sumOf { it.size }
}

fun skillDigest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun skillRelativePath(value: String): Path {
    require(value.isNotBlank() && value.length <= 4096 && value.none { it == '\\' || it == '\u0000' || it == ':' }) { "Invalid package path" }
    require(!value.startsWith('/') && value.split('/').none { it in setOf("", ".", "..") }) { "Package path escapes its root" }
    require(value.split('/').size <= 32) { "Package path nesting exceeds 32" }
    return Path.of(value).also { require(!it.isAbsolute) }
}

/** Only regular files/directories below the verified package root are ever removed. */
fun removeSkillTree(path: Path) {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): FileVisitResult {
            Files.delete(file); return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
            if (error != null) throw error
            Files.delete(dir); return FileVisitResult.CONTINUE
        }
    })
}
