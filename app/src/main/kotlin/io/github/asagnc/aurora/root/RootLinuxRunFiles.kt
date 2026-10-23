package io.github.asagnc.aurora.root

import android.content.Context
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxDurability
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import kotlinx.serialization.encodeToString

/** Root journals are separate from App-owned installation staging. */
internal class RootLinuxRunFiles(private val context: Context) {
    private val uid get() = context.applicationInfo.uid
    fun resources(): Path = privatePath(listOf("spaces", "resources"))
    private fun privatePath(parts: List<String>): Path {
        // Android's trusted Context path may contain /data/user/0 -> /data/data.
        // Canonicalize that base once; still reject every link below it.
        var path = context.filesDir.toPath().toRealPath()
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
    fun tools(bundle: String): Path {
        require(bundle.matches(Regex("[a-f0-9]{64}")))
        val path = privatePath(listOf("spaces", "resources", "static-reverse", "versions", bundle))
        require(text(path.resolve("bundle"), 64) == bundle)
        for (name in listOf("jre", "jadx", "apktool"))
            require(Files.isDirectory(path.resolve(name), LinkOption.NOFOLLOW_LINKS))
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
    fun conversation(owner: String) = privatePath(listOf("spaces", "conversations", owner))
    fun workspace(ownerId: String, claimRun: String? = null, allowRetired: Boolean = false): Path {
        if (!allowRetired) require(!Files.exists(retirement(ownerId), LinkOption.NOFOLLOW_LINKS)) { "Conversation Root files are retired" }
        val parent = conversation(ownerId)
        val work = parent.resolve(".aurora-linux")
        if (!Files.exists(work, LinkOption.NOFOLLOW_LINKS)) {
            Os.mkdir(work.toString(), 448); Os.chown(work.toString(), uid, uid)
        }
        require(Files.isDirectory(work, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(work))
        val owner = work.resolve(".workspace-owner")
        if (!Files.exists(owner, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.newDirectoryStream(work).use { !it.iterator().hasNext() }) { "Unrecognized Linux workspace retained" }
            write(owner, ownerId)
        }
        require(text(owner, 64) == ownerId)
        val active = work.resolve(".active-run")
        if (claimRun != null) {
            require(!Files.exists(active, LinkOption.NOFOLLOW_LINKS)) { "Conversation Linux workspace needs recovery" }
            write(active, claimRun)
            Os.chown(work.toString(), 0, 0); Os.chmod(work.toString(), 448)
            LinuxDurability.directory(work)
        }
        for (name in listOf("view", "home", "tmp", "project")) {
            val child = work.resolve(name)
            if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) Os.mkdir(child.toString(), 448)
            require(Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(child))
        }
        return work
    }
    private fun retirement(owner: String) = privatePath(listOf("spaces", "resources")).resolve("linux-file-retired").resolve(owner)
    fun retire(owner: String) {
        val resources = privatePath(listOf("spaces", "resources"))
        val directory = rootDirectory(resources, "linux-file-retired")
        val marker = directory.resolve(owner)
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) write(marker, owner)
    }
    /** O(1) lifecycle metadata only; guest ownership is deliberately retained. */
    fun settle(ticket: LinuxRunTicket) {
        val work = workspace(ticket.owner)
        val active = work.resolve(".active-run")
        if (Files.exists(active, LinkOption.NOFOLLOW_LINKS)) {
            require(text(active, 64) == ticket.runId)
            Files.delete(active); LinuxDurability.directory(work)
        }
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
