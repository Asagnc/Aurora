package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Owned per-boot baseline, never a persistent SELinux module or whole-policy rollback. */
internal class RootFridaPolicy(private val context: Context, private val identity: () -> LinuxRootIdentity,
    private val invalidate: () -> Unit) {
    val gate = Mutex()
    private var qualified: JsonObject? = null
    private var pinnedWorker: String? = null
    private var appType: String? = null
    private var required = false
    private val bundle = RootManagedBundle(context)

    suspend fun operation(expected: LinuxRootIdentity, type: String, activate: Boolean): String = gate.withLock {
        require(expected == identity()) { "Root service epoch changed" }
        if (activate) { required = true; invalidate() }
        val previouslyQualified = qualified
        qualified = null
        bundle.worker().use { worker ->
            val directory = directory(expected.bootId)
            val rootType = domain(Process.myPid())
            if (activate) prepare(directory, worker, rootType, type)
            if (!activate && previouslyQualified == null) return@withLock status(false).toString()
            val before = directory.resolve("before.policy")
            val candidate = directory.resolve("activation-${UUID.randomUUID()}.policy")
            val response = try {
                if (activate) invoke(worker, listOf("policy-plan", before.toString(), candidate.toString(), rootType, type))
                invoke(worker, listOf(if (activate) "policy-activate" else "policy-attest", before.toString(),
                    if (activate) candidate.toString() else "/sys/fs/selinux/policy", expected.bootId, rootType, type))
            } finally { Files.deleteIfExists(candidate) }
            require(response["policyQualified"]?.jsonPrimitive?.booleanOrNull == true)
            pinnedWorker = worker.sha256; appType = type
            qualified = response
            status(true).toString()
        }
    }

    /** Called under gate before a Frida task. Capture tasks have their independent qualification. */
    suspend fun checkCurrent() {
        val previous = qualified ?: run { check(!required) { "Explicit policy qualification is required" }; return }
        qualified = null
        bundle.worker().use { worker ->
            require(worker.sha256 == pinnedWorker) { "Frida policy worker changed" }
            val current = identity()
            val result = invoke(worker, listOf("policy-attest", directory(current.bootId).resolve("before.policy").toString(),
                "/sys/fs/selinux/policy", current.bootId, domain(Process.myPid()), requireNotNull(appType)))
            require(result["imageSha256"] == previous["imageSha256"]) { "Kernel policy changed; explicitly qualify again" }
            qualified = result
        }
    }

    private fun status(passed: Boolean): JsonObject = JsonObject((qualified ?: JsonObject(emptyMap())) + mapOf(
        "protocol" to JsonPrimitive(1), "policyQualified" to JsonPrimitive(passed),
        "identity" to WireJson.encodeToJsonElement(LinuxRootIdentity.serializer(), identity()),
        "workerSha256" to (pinnedWorker?.let(::JsonPrimitive) ?: JsonNull)))

    private suspend fun prepare(directory: Path, worker: RootManagedExecutable, root: String, app: String) {
        val before = directory.resolve("before.policy")
        if (Files.exists(before, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        val staged = directory.resolve("baseline-${UUID.randomUUID()}.policy")
        val candidate = directory.resolve("candidate-${UUID.randomUUID()}.policy")
        try {
            val input = Os.open("/sys/fs/selinux/policy", O_RDONLY or O_NOFOLLOW or O_CLOEXEC, 0)
            try {
                val output = Os.open(staged.toString(), O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
                try {
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = Os.read(input, buffer, 0, buffer.size)
                        if (count == 0) break
                        require(count > 0); total += count; require(total <= 67_108_864)
                        var offset = 0
                        while (offset < count) offset += Os.write(output, buffer, offset, count - offset).also { require(it > 0) }
                    }
                    require(total > 0); Os.fsync(output)
                } finally { Os.close(output) }
            } finally { Os.close(input) }
            invoke(worker, listOf("policy-plan", staged.toString(), candidate.toString(), root, app))
            // link publishes without replacement. A crash leaving extra links or a
            // partial baseline fails closed under the native nlink/format checks.
            Os.link(staged.toString(), before.toString())
            Files.delete(staged)
            syncDirectory(directory)
        } finally {
            Files.deleteIfExists(staged); Files.deleteIfExists(candidate)
        }
    }

    private fun directory(boot: String): Path {
        require(UUID.fromString(boot).toString() == boot)
        var path = context.filesDir.toPath().toRealPath()
        for (part in listOf("spaces", "resources")) {
            path = path.resolve(part); require(S_ISDIR(Os.lstat(path.toString()).st_mode))
        }
        for (part in listOf("root-frida-policy", boot)) {
            val parent = path; path = path.resolve(part)
            try { Os.mkdir(path.toString(), 448) }
            catch (failure: ErrnoException) { if (failure.errno != EEXIST) throw failure }
            val info = Os.lstat(path.toString())
            require(S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0)
            syncDirectory(parent)
        }
        return path
    }

    private fun syncDirectory(path: Path) {
        val fd = Os.open(path.toString(), O_RDONLY or O_NOFOLLOW or O_CLOEXEC, 0)
        try { require(S_ISDIR(Os.fstat(fd).st_mode)); Os.fsync(fd) } finally { Os.close(fd) }
    }

    private suspend fun invoke(worker: RootManagedExecutable, arguments: List<String>): JsonObject = runInterruptible(Dispatchers.IO) {
        worker.usePath { executable ->
            val process = ProcessBuilder(listOf(executable) + arguments).redirectErrorStream(true)
                .apply { environment().clear() }.start()
            try {
                val output = process.inputStream.readNBytes(4097)
                require(output.size <= 4096) { "Policy response exceeded its limit" }
                RootManagedProcessWait.await(process, 25_000)
                require(process.exitValue() == 0) { "Policy qualification failed; no rollback performed" }
                WireJson.parseToJsonElement(output.toString(Charsets.UTF_8)).jsonObject
            } finally {
                try { if (process.isAlive) { process.destroy(); RootManagedProcessWait.await(process, 25_000) } }
                finally { process.inputStream.close(); process.outputStream.close(); process.errorStream.close() }
            }
        }
    }

    companion object {
        fun domain(pid: Int): String {
            val value = Files.newInputStream(Path.of("/proc/$pid/attr/current")).use {
                it.readNBytes(4097).also { bytes -> require(bytes.size <= 4096) }.toString(Charsets.US_ASCII)
            }.substringBefore('\u0000').trim()
            val parts = value.split(':')
            require(parts.size >= 4 && parts[0] == "u" && parts[1] == "r" && parts[2].matches(Regex("[A-Za-z0-9_]{1,128}")))
            return parts[2]
        }
    }
}
