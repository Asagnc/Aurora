package io.github.asagnc.aurora.root

import android.content.Context
import android.system.Os
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxDurability
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/** App catalog must reserve an unused, unleased version before requesting this.
 * The service accepts only Root-generation IDs inside its private Linux store. */
internal class RootLinuxVersionFiles(private val context: Context) {
    fun remove(id: String) {
        require(id.matches(Regex("[a-f0-9]{64}")))
        var directory = context.filesDir.toPath()
        for (part in listOf("spaces", "resources", "linux", id)) {
            directory = directory.resolve(part)
            if (part == id && !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return
            require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory))
            require(Os.lstat(directory.toString()).st_uid == context.applicationInfo.uid)
        }
        val metadata = directory.resolve("manifest.json")
        if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) {
            Files.newDirectoryStream(directory).use { require(!it.iterator().hasNext()) }
            Files.delete(directory); return
        }
        require(Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS) && Files.size(metadata) in 1..4096)
        val manifest = WireJson.decodeFromString<LinuxRootfsManifest>(String(Files.readAllBytes(metadata), Charsets.UTF_8))
        require(manifest.backend == LinuxBackend.CHROOT && manifest.generationId() == id)
        RootLinuxMountSafety.requireUnmounted(directory)
        // Keep the manifest until all privileged contents are removed. An
        // interrupted deletion remains identifiable and safe to retry.
        Files.newDirectoryStream(directory).use { children -> children.forEach { path ->
            if (path.fileName.toString() != "manifest.json") removeTree(path)
        } }
        LinuxDurability.directory(directory)
        Files.delete(metadata); Files.delete(directory)
        LinuxDurability.directory(directory.parent)
    }
    private fun removeTree(path: Path) {
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkInterrupted(); Files.delete(file); return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                checkInterrupted(); Files.delete(dir); return FileVisitResult.CONTINUE
            }
        })
    }
    private fun checkInterrupted() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
}
