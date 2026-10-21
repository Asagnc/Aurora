package io.github.asagnc.aurora.linux

import android.content.Context
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.*
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Fixed, offline observation workload. Success does not enable business execution. */
internal class LinuxProotProbe(private val context: Context) {
    suspend fun inspect(rootfs: Path): JsonObject = withContext(Dispatchers.IO) {
        val native = Path.of(context.applicationInfo.nativeLibraryDir)
        val architecture = LinuxArchitecture.fromAbi(android.os.Build.SUPPORTED_ABIS.first())
        LinuxElf.validateRootfs(rootfs, architecture)
        listOf("libaurora_proot.so", "libaurora_proot_loader.so", "libaurora_talloc.so").forEach {
            require(Files.isRegularFile(native.resolve(it), LinkOption.NOFOLLOW_LINKS)) { "Packaged Linux tool missing" }
        }
        val stage = Files.createDirectory(context.cacheDir.toPath().resolve("lp-${UUID.randomUUID().toString().take(8)}"))
        var failure: Throwable? = null
        try {
            val home = Files.createDirectory(stage.resolve("home"))
            val temporary = Files.createDirectory(stage.resolve("tmp"))
            val runtime = Files.createDirectory(stage.resolve("ipc"))
            val result = NativeCommandRunner(context).execute(stage, request(native, rootfs, listOf(home, temporary, runtime)))
            val complete = result.number("exitCode") == 0L && result["error"] == null &&
                result["stdout"]?.jsonObject?.text("text") == "aurora-proot-probe-v1\n"
            objectOf("backend" to string("proot"), "version" to string("5.1.107.98"),
                "workloadPassed" to JsonPrimitive(complete), "executionReady" to JsonPrimitive(false),
                "scope" to string("Offline shell, clean environment, child exec, file IO, hardlink and HOME/tmp bindings. Lifecycle/PTY/Root qualification is separate."),
                "result" to JsonObject(result.filterKeys { it != "artifactDirectory" }.mapValues { (key, value) ->
                    if (key in setOf("stdout", "stderr")) JsonObject(value.jsonObject.filterKeys { it != "artifact" }) else value
                }))
        } catch (caught: Throwable) { failure = caught; throw caught }
        finally {
            try { removeSkillTree(stage) }
            catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        }
    }

    private fun request(native: Path, rootfs: Path, paths: List<Path>): CommandRequest {
        val (home, temporary, runtime) = paths
        val arguments = listOf(native.resolve("libaurora_proot.so").toString(), "--root-id", "--kill-on-exit", "--sysvipc",
            "-r", rootfs.toString(), "-b", "/dev", "-b", "/proc", "-b", "$home:/root", "-b", "$temporary:/tmp",
            "-w", "/tmp", "/usr/bin/env", "-i", "HOME=/root", "PATH=/usr/bin:/bin", "LANG=C", "/bin/sh", "-c", WORKLOAD)
        return CommandRequest(arguments, timeoutMs = 15_000, environment = mapOf(
            "LD_LIBRARY_PATH" to native.toString(), "PROOT_LOADER" to native.resolve("libaurora_proot_loader.so").toString(),
            "PROOT_LOADER_32" to "/aurora-unsupported-32bit", "PROOT_TMP_DIR" to runtime.toString()))
    }

    private companion object {
        const val WORKLOAD = """set -eu
test "${'$'}HOME" = /root
test -z "${'$'}{LD_LIBRARY_PATH+x}"
test -z "${'$'}{PROOT_LOADER+x}"
printf original > /tmp/aurora-source
ln /tmp/aurora-source /tmp/aurora-hardlink
printf changed > /tmp/aurora-hardlink
test "${'$'}(cat /tmp/aurora-source)" = changed
printf home > /root/aurora-home
test "${'$'}(/bin/sh -c 'cat /root/aurora-home')" = home
/bin/sh -c 'exit 7' && exit 9 || test "${'$'}?" = 7
printf 'aurora-proot-probe-v1\n'
"""
    }
}
