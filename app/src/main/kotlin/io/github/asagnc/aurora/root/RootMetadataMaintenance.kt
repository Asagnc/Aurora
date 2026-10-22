package io.github.asagnc.aurora.root

import android.content.Context
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.*
import java.nio.file.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** One bounded, expiring inventory cursor; no timer or directory scan on command dispatch. */
internal class RootMetadataMaintenance(private val context: Context, private val identity: () -> LinuxRootIdentity) {
    private val files = RootLinuxRunFiles(context)
    private val gate = Mutex()
    private var scan: Scan? = null
    private data class Metadata(val ticket: LinuxRunTicket?, val digest: String)
    private class Scan(val token: String, val roots: Iterator<Path>) {
        var stream: DirectoryStream<Path>? = null
        var entries: Iterator<Path>? = null
        var touched = SystemClock.elapsedRealtime()
        fun close() { stream?.close(); stream = null; entries = null }
        fun next(): Path? {
            while (true) {
                val cursor = entries
                if (cursor != null && cursor.hasNext()) return cursor.next()
                close()
                if (!roots.hasNext()) return null
                val path = roots.next()
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue
                ownedDirectory(path)
                stream = Files.newDirectoryStream(path); entries = stream!!.iterator()
            }
        }
    }

    suspend fun invoke(args: JsonObject): String = withContext(Dispatchers.IO) { gate.withLock {
        if (scan?.let { SystemClock.elapsedRealtime() - it.touched > 30_000 } == true) discardScan()
        when (args.text("action")) {
            "begin" -> {
                check(scan == null) { "Metadata inventory is already open" }
                val resources = files.resources()
                val next = Scan(UUID.randomUUID().toString(), listOf(resources.resolve("linux-runtime"), resources.resolve("linux-runtime-gc")).iterator())
                scan = next; page(next)
            }
            "next" -> page(requireNotNull(scan).also { require(it.token == args.text("token")) })
            "collect" -> collect(args)
            "close" -> { if (scan?.token == args.text("token")) discardScan(); objectOf("closed" to JsonPrimitive(true)) }
            else -> error("Unknown metadata maintenance action")
        }.toString()
    } }

    private fun page(current: Scan): JsonObject = try { readPage(current) }
    catch (failure: Throwable) {
        if (scan === current) scan = null
        runCatching { current.close() }.onFailure { failure.addSuppressed(it) }
        throw failure
    }

    private fun readPage(current: Scan): JsonObject {
        current.touched = SystemClock.elapsedRealtime()
        val candidates = mutableListOf<JsonObject>()
        var retained = 0
        var done = false
        repeat(16) {
            val path = current.next()
            if (path == null) { done = true; return@repeat }
            if (path.fileName.toString() == ".run.lock") return@repeat
            try {
                val id = path.fileName.toString(); require(UUID.fromString(id).toString() == id)
                ownedDirectory(path)
                val trash = path.parent.fileName.toString() == "linux-runtime-gc"
                val metadata = readMetadata(path)
                require(trash || metadata.ticket != null && oldEpochDead(metadata.ticket.identity))
                candidates += objectOf("id" to string(id), "digest" to string(metadata.digest), "trash" to JsonPrimitive(trash))
            } catch (_: Exception) { retained++ }
        }
        return objectOf("token" to string(current.token), "done" to JsonPrimitive(done),
            "candidates" to JsonArray(candidates), "retained" to JsonPrimitive(retained))
    }

    private suspend fun collect(args: JsonObject): JsonObject {
        val current = requireNotNull(scan) { "Metadata inventory expired" }
        require(current.token == args.text("token")) { "Metadata inventory expired" }
        current.touched = SystemClock.elapsedRealtime()
        val id = requireNotNull(args.text("id")); require(UUID.fromString(id).toString() == id)
        val trash = args["trash"] == JsonPrimitive(true)
        val resources = files.resources()
        val parent = resources.resolve(if (trash) "linux-runtime-gc" else "linux-runtime")
        ownedDirectory(parent)
        val path = parent.resolve(id); ownedDirectory(path)
        val metadata = readMetadata(path)
        require(metadata.digest == args.text("digest")) { "Metadata changed after inventory" }
        if (!trash) require(oldEpochDead(requireNotNull(metadata.ticket).identity)) { "Root epoch is not retired" }
        val target = resources.resolve("linux-runtime-gc")
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Os.mkdir(target.toString(), 448)
        ownedDirectory(target)
        val helper = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_linux_scope.so")
        val argv = listOf(helper.toString(), if (trash) "--gc-trash" else "--gc", "--state-dir", path.toString()) +
            if (trash) emptyList() else listOf("--trash-dir", target.toString())
        val output = Files.createTempDirectory(context.cacheDir.toPath(), "linux-gc-")
        var failure: Throwable? = null
        try {
            val result = NativeCommandRunner(context, context.applicationInfo.uid).execute(output, CommandRequest(argv, timeoutMs = 5000))
            val proof = result["stdout"]?.jsonObject?.text("text")?.let { runCatching { WireJson.parseToJsonElement(it).jsonObject }.getOrNull() }
            return objectOf("collected" to JsonPrimitive(result.number("exitCode") == 0L && result["error"] == null &&
                proof?.number("protocol") == 1L && proof["collected"] == JsonPrimitive(true)))
        } catch (caught: Throwable) { failure = caught; throw caught
        } finally { withContext(NonCancellable) {
            try { removeSkillTree(output) }
            catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        } }
    }

    private fun readMetadata(path: Path): Metadata {
        val record = path.resolve("ticket.json")
        if (!Files.exists(record, LinkOption.NOFOLLOW_LINKS)) return Metadata(null, "quarantined-partial")
        val info = Os.lstat(record.toString())
        require(OsConstants.S_ISREG(info.st_mode) && info.st_uid == 0 && info.st_nlink == 1L && info.st_mode and 18 == 0)
        val encoded = files.text(record, 262144)
        val ticket = WireJson.decodeFromString<LinuxRunTicket>(encoded).also {
            it.validate(); require(it.runId == path.fileName.toString() && it.appUid == context.applicationInfo.uid)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        return Metadata(ticket, digest)
    }

    private fun oldEpochDead(previous: LinuxRootIdentity): Boolean {
        val current = identity()
        if (previous == current) return false
        if (previous.bootId != current.bootId) return true
        return try {
            val stat = Files.newInputStream(Path.of("/proc/${previous.pid}/stat")).use { String(it.readNBytes(65537), Charsets.UTF_8) }
            linuxProcessStartTicks(stat) != previous.startTicks
        } catch (_: NoSuchFileException) {
            try { Os.kill(previous.pid, 0); false }
            catch (failure: android.system.ErrnoException) { if (failure.errno == OsConstants.ESRCH) true else throw failure }
        }
    }
    private fun discardScan() { val old = scan; scan = null; old?.close() }
    suspend fun close() = gate.withLock { discardScan() }

    companion object {
        private fun ownedDirectory(path: Path) {
            val info = Os.lstat(path.toString())
            require(OsConstants.S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0)
        }
    }
}
