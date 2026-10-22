package io.github.asagnc.aurora.root

import android.content.Context
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxDurability
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.serialization.encodeToString

/** Root journals are separate from App-owned installation staging. */
internal class RootLinuxRunFiles(private val context: Context) {
    private val uid get() = context.applicationInfo.uid
    private fun privatePath(parts: List<String>): Path {
        var path = context.filesDir.toPath()
        for (part in parts) {
            path = path.resolve(part)
            require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
        }
        return path
    }
    private fun rootDirectory(parent: Path, name: String): Path {
        val path = parent.resolve(name)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Os.mkdir(path.toString(), 448); LinuxDurability.directory(parent)
        }
        val info = Os.lstat(path.toString())
        require(OsConstants.S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0)
        return path
    }
    fun state(ticket: LinuxRunTicket): Path {
        val resources = privatePath(listOf("spaces", "resources"))
        return rootDirectory(rootDirectory(resources, "linux-runtime"), ticket.runId)
    }
    fun bind(state: Path, ticket: LinuxRunTicket, starting: Boolean) {
        val path = state.resolve("ticket.json")
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            require(!starting) { "Linux run IDs cannot be replayed" }
            require(WireJson.decodeFromString<LinuxRunTicket>(text(path, 262144)) == ticket)
        } else write(path, WireJson.encodeToString(ticket))
        if (!starting && !Files.exists(state.resolve("retired"), LinkOption.NOFOLLOW_LINKS))
            write(state.resolve("retired"), ticket.runId)
    }
    fun rootfs(ticket: LinuxRunTicket): Path {
        val version = privatePath(listOf("spaces", "resources", "linux", ticket.generation))
        val manifest = WireJson.decodeFromString<LinuxRootfsManifest>(text(version.resolve("manifest.json"), 4096))
        require(manifest.backend == LinuxBackend.CHROOT && manifest.generationId() == ticket.generation)
        val root = version.resolve("rootfs")
        require(Os.lstat(root.toString()).st_uid == 0)
        LinuxElf.validateRootfs(root, manifest.image.architecture)
        return root
    }
    fun conversation(ticket: LinuxRunTicket) = privatePath(listOf("spaces", "conversations", ticket.owner))
    fun workspace(ticket: LinuxRunTicket, claim: Boolean): Path {
        val parent = conversation(ticket)
        val work = parent.resolve(".aurora-linux")
        if (!Files.exists(work, LinkOption.NOFOLLOW_LINKS)) {
            Os.mkdir(work.toString(), 448); Os.chown(work.toString(), uid, uid)
        }
        require(Files.isDirectory(work, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(work))
        val owner = work.resolve(".workspace-owner")
        if (!Files.exists(owner, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.newDirectoryStream(work).use { !it.iterator().hasNext() }) { "Unrecognized Linux workspace retained" }
            write(owner, ticket.owner)
        }
        require(text(owner, 64) == ticket.owner)
        val active = work.resolve(".active-run")
        if (claim) {
            require(!Files.exists(active, LinkOption.NOFOLLOW_LINKS)) { "Conversation Linux workspace needs recovery" }
            write(active, ticket.runId)
            Os.chown(work.toString(), 0, 0); Os.chmod(work.toString(), 448)
            for (name in listOf("view", "home", "tmp", "project")) {
                val child = work.resolve(name)
                if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) Os.mkdir(child.toString(), 448)
                require(Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(child))
            }
            LinuxDurability.directory(work)
        }
        return work
    }
    fun restore(ticket: LinuxRunTicket) {
        val work = workspace(ticket, false)
        val active = work.resolve(".active-run")
        if (Files.exists(active, LinkOption.NOFOLLOW_LINKS)) require(text(active, 64) == ticket.runId)
        else {
            require(Files.newDirectoryStream(work).use { !it.iterator().hasNext() } || Os.lstat(work.toString()).st_uid == uid)
            Os.chown(work.toString(), uid, uid); Os.chmod(work.toString(), 448); return
        }
        val mounted = Files.readAllLines(Path.of("/proc/self/mountinfo")).mapNotNull { it.split(' ').getOrNull(4) }
        require(mounted.none { Path.of(it.replace("\\040", " ").replace("\\011", "\t").replace("\\134", "\\")).startsWith(work) })
        Files.walkFileTree(work, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (file != active) {
                    Os.lchown(file.toString(), uid, uid)
                    if (!attrs.isSymbolicLink) Os.chmod(file.toString(), Os.lstat(file.toString()).st_mode and 511 or 256)
                }
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Os.chown(dir.toString(), uid, uid); Os.chmod(dir.toString(), Os.lstat(dir.toString()).st_mode and 511 or 448)
                return FileVisitResult.CONTINUE
            }
        })
        Files.delete(active); LinuxDurability.directory(work)
    }
    fun text(path: Path, limit: Long): String {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) in 1..limit)
        return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val bytes = input.readNBytes(limit.toInt() + 1); require(bytes.size <= limit)
            String(bytes, Charsets.UTF_8)
        }
    }
    private fun write(path: Path, value: String) {
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { file ->
            val bytes = ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8))
            while (bytes.hasRemaining()) file.write(bytes)
            file.force(true)
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
        LinuxDurability.directory(path.parent)
    }
}
