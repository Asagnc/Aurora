package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxDurability
import java.nio.file.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Fixed-image hardlinks and separate ownership finalization; labels are retained by lchown. */
internal class RootLinuxPreparation(private val context: Context) {
    val identity = LinuxRootIdentity(Process.myPid(), startTicks(Process.myPid()), bootId(), UUID.randomUUID().toString())
        .also { it.validate() }
    private val uid get() = context.applicationInfo.uid

    suspend fun prepare(operation: String): String = runInterruptible(Dispatchers.IO) {
        val stage = stage(operation)
        val ticket = ticket(stage)
        require(ticket.identity == identity && ticket.appUid == uid && ticket.operation == operation)
        if (ticket.purpose == "root_removal") {
            val generation = linuxGenerationId(ticket.image, LinuxBackend.CHROOT)
            RootLinuxVersionFiles(context).remove(generation)
            return@runInterruptible objectOf("protocol" to JsonPrimitive(1), "prepared" to JsonPrimitive(true),
                "ownership" to string(ticket.purpose), "generation" to string(generation)).toString()
        }
        val image = UbuntuBaseImages.current(LinuxArchitecture.fromAbi(Build.SUPPORTED_ABIS.first()))
        require(ticket.image == image.sha256)
        val archive = stage.resolve("base.tar.gz")
        verifyDigest(archive, image.sha256)
        val unpacker = RootfsArchive()
        val plan = unpacker.scan(archive)
        val prepared = stage.resolve("prepared")
        require(Files.isDirectory(prepared, LinkOption.NOFOLLOW_LINKS))
        require(Os.lstat(prepared.toString()).st_uid == uid)
        val rootfs = prepared.resolve("rootfs")
        if (ticket.purpose == "root_ownership") {
            RootLinuxOwners.restore(rootfs, plan, uid)
            LinuxElf.validateRootfs(rootfs, image.architecture)
        } else unpacker.materializeHardlinks(rootfs, plan, inspect = { path ->
            var current = prepared
            require(path.startsWith(rootfs))
            for (part in prepared.relativize(path)) {
                current = current.resolve(part)
                require(!Files.isSymbolicLink(current) && Os.lstat(current.toString()).st_uid == uid)
            }
        })
        objectOf("protocol" to JsonPrimitive(1), "prepared" to JsonPrimitive(true), "image" to string(image.sha256),
            "entries" to JsonPrimitive(plan.size), "bytes" to JsonPrimitive(plan.sumOf { it.bytes }),
            "ownership" to string(ticket.purpose), "generation" to string(linuxGenerationId(image.sha256,
                if (ticket.purpose == "root_ownership") LinuxBackend.CHROOT else LinuxBackend.PROOT))).toString()
    }

    /** Caller must cancel/join the same-epoch operation before invoking discard. */
    fun discard(operation: String) {
        val stage = stage(operation)
        val ticket = ticket(stage)
        require(ticket.operation == operation && ticket.appUid == uid)
        if (ticket.identity != identity) {
            val previous = ticket.identity
            val gone = if (previous.bootId != identity.bootId) true else try {
                startTicks(previous.pid) != previous.startTicks
            } catch (missing: NoSuchFileException) {
                try { Os.kill(previous.pid, 0); false }
                catch (failure: android.system.ErrnoException) {
                    if (failure.errno == android.system.OsConstants.ESRCH) true else throw failure
                }
            }
            require(gone) { "Previous Root process is still alive; preparation retained" }
        }
        val canonical = stage.toRealPath()
        val mounted = Files.readAllLines(Path.of("/proc/self/mountinfo")).mapNotNull { it.split(' ').getOrNull(4) }
        require(mounted.none { Path.of(it.replace("\\040", " ").replace("\\011", "\t").replace("\\134", "\\")).startsWith(canonical) })
        Files.newDirectoryStream(stage).use { children -> children.forEach { path ->
            if (path.fileName.toString() != ".root-preparation.json") removeSkillTree(path)
        } }
        LinuxDurability.directory(stage)
        Files.delete(stage.resolve(".root-preparation.json")); Files.delete(stage)
        LinuxDurability.directory(stage.parent)
    }

    fun belongsToCurrent(operation: String) = ticket(stage(operation)).identity == identity

    private fun stage(operation: String): Path {
        require(UUID.fromString(operation).toString() == operation)
        var path = context.filesDir.toPath()
        listOf("spaces", "resources", ".linux-staging", operation).forEach { name ->
            path = path.resolve(name)
            require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
            require(Os.lstat(path.toString()).st_uid == uid)
        }
        return path
    }
    private fun ticket(stage: Path): LinuxPreparationTicket {
        val path = stage.resolve(".root-preparation.json")
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) in 1..4096)
        return WireJson.decodeFromString<LinuxPreparationTicket>(String(Files.readAllBytes(path), Charsets.UTF_8)).also { it.validate() }
    }
    private fun verifyDigest(path: Path, expected: String) {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) in 1..64L * 1024 * 1024)
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == expected)
    }
    private fun procText(path: String): String = Files.newInputStream(Path.of(path)).use { input ->
        val bytes = input.readNBytes(65537); require(bytes.size <= 65536); String(bytes, Charsets.UTF_8)
    }
    private fun startTicks(pid: Int) = linuxProcessStartTicks(procText("/proc/$pid/stat"))
    private fun bootId() = procText("/proc/sys/kernel/random/boot_id").trim()
}
