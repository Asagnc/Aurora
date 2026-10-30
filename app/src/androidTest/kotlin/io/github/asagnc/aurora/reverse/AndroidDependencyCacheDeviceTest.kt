package io.github.asagnc.aurora.reverse

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxAgentEnvironment
import io.github.asagnc.aurora.linux.LinuxToolRun
import io.github.asagnc.aurora.root.RootRequired
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit mechanism qualification only: never installs a shared version or grants runtimeReady. */
@RunWith(AndroidJUnit4::class)
class AndroidDependencyCacheDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @RootRequired @Test fun twoFreshPrivateCachesBuildOfflineAgainstAnImmutablePublicSnapshot() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        for (key in listOf("auroraRootTest", "auroraAndroidDependencyCacheTest")) check(args.getString(key) == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        val directory = app.cacheDir.toPath().resolve("apk-public-cache-qualification")
        val archive = directory.resolve("public-apk-dependency-cache.tar.gz")
        val manifest = directory.resolve("manifest.json")
        val archiveSha = expected(args, "auroraDependencyArchiveSha256")
        val manifestSha = expected(args, "auroraDependencyManifestSha256")
        assertEquals(archiveSha, hash(archive)); assertEquals(manifestSha, hash(manifest))
        val checks = inventory(manifest)
        val entries = withContext(Dispatchers.IO) {
            RootfsArchive(RootfsBudget(compressed = 2L shl 30, expanded = 2L shl 30,
                file = 512L shl 20, entries = 64001)).scan(archive)
        }
        assertEquals(checks.keys + "dependency-cache/manifest.json", entries.map { it.path }.toSet())
        check(entries.all { it.kind == "file" && it.uid == 0L && it.gid == 0L })
        val checksum = directory.resolve("checksums.txt")
        Files.newOutputStream(checksum).bufferedWriter().use { output ->
            output.append("$manifestSha  /mnt/dependency-cache/manifest.json\n")
            checks.forEach { (name, sha) -> output.append("$sha  /mnt/$name\n") }
        }
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val keep = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val wasKept = activity.window.attributes.flags and keep != 0
        instrumentation.runOnMainSync { activity.window.addFlags(keep) }
        try {
            app.root.connect(); check(app.root.linuxExecution != null && app.linux.runs.pendingCount() == 0)
            for (index in 0..1) qualify(app, archive, checksum, archiveSha, index)
        } finally { withContext(NonCancellable) {
            app.root.disconnect(); Files.deleteIfExists(checksum)
            if (!wasKept) instrumentation.runOnMainSync { activity.window.clearFlags(keep) }
        } }
    }

    private fun expected(args: Bundle, name: String): String = requireNotNull(args.getString(name)).also {
        require(it.matches(Regex("[a-f0-9]{64}")))
    }

    private fun hash(path: Path): String {
        check(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
        val sha = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; sha.update(buffer, 0, count) }
        }
        return sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun inventory(path: Path): Map<String, String> {
        require(Files.size(path) in 1..(32L shl 20))
        val manifest = Files.newInputStream(path).use { WireJson.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
        require(manifest.number("format") == 1L && manifest.text("gradleVersion") == "9.8.2")
        val seed = manifest.getValue("seed").jsonObject
        val probeSha = MessageDigest.getInstance("SHA-256").digest(AndroidBuildRuntime.probe.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(seed.text("probeSha256") == probeSha)
        require(seed.text("nativeSdkSha256") == AndroidBuildTools.components.single { it.id == "android-host" }.sha256)
        val files = manifest.getValue("files").jsonObject
        require(files.size in 1..64000 && manifest.number("fileCount") == files.size.toLong())
        return files.mapKeys { (name, _) ->
            require(name.startsWith("modules-2/") && name.matches(Regex("[A-Za-z0-9_./+@=-]+")))
            require(name.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
            "dependency-cache/$name"
        }.mapValues { (_, record) -> requireNotNull(record.jsonObject.text("sha256")).also {
            require(it.matches(Regex("[a-f0-9]{64}")))
        } }
    }

    private suspend fun qualify(app: AuroraApplication, archive: Path, checksum: Path, sha: String, index: Int) {
        val owner = app.conversations.create("Public APK cache qualification fixture")
        val environment = LinuxAgentEnvironment(app.linux, owner)
        var original: Throwable? = null
        try {
            val generation = environment.generation()
            check(app.sharedToolchain.status(generation, "android-build").array("installed").contains(string(AndroidBuildTools.bundleId)))
            assertExit(app.linux.runs.execute(owner, listOf("/bin/true"), generation = generation))
            val project = app.spaces.commandRoot(owner).resolve(".aurora-linux/project")
            for (source in listOf(archive, checksum)) {
                val result = requireNotNull(app.root.commandClient).execute(owner,
                    CommandRequest(listOf("/system/bin/cp", source.toString(), project.resolve(source.fileName).toString())))
                assertEquals(0L, WireJson.parseToJsonElement(result).jsonObject.number("exitCode"))
            }
            assertExit(tools(app, owner, generation, """
                set -eu
                test ! -e /mnt/.gradle-cache
                printf '%s\n' '$sha  /mnt/public-apk-dependency-cache.tar.gz' | sha256sum -c --status
                tar -xzf /mnt/public-apk-dependency-cache.tar.gz -C /mnt
                sha256sum -c --status /mnt/checksums.txt
            """.trimIndent(), UUID.randomUUID().toString(), 120_000))
            val id = UUID.randomUUID().toString()
            val script = """
                set -eu
                test ! -e /mnt/.gradle-cache
                mount --bind /mnt/dependency-cache /mnt/dependency-cache
                mount -o remount,bind,ro /mnt/dependency-cache
                if touch /mnt/dependency-cache/must-not-write 2>/dev/null; then exit 71; fi
                sha256sum -c --status /mnt/checksums.txt
                sed 's/--quiet assembleRelease/--offline --quiet assembleRelease/' /opt/android-host/bin/aurora-apk-probe > /mnt/offline-probe.sh
                export GRADLE_RO_DEP_CACHE=/mnt/dependency-cache
                /bin/sh /mnt/offline-probe.sh '$id'
                sha256sum -c --status /mnt/checksums.txt
                printf '%s\n' '$owner' > /mnt/.gradle-cache/owned-private-marker
                printf '%s\n' aurora-readonly-cache-probe-ok
            """.trimIndent()
            val result = tools(app, owner, generation, script, id, 270_000)
            assertExit(result)
            assertTrue(result.getValue("result").jsonObject.getValue("stdout").jsonObject.text("text")!!.trimEnd().endsWith("aurora-readonly-cache-probe-ok"))
            val exported = requireNotNull(app.root.linuxFileClient).invoke(owner, "export", objectOf("path" to string(
                "project/.aurora-apk-probes/$id/build/outputs/apk/release/aurora-sdk-probe-release-unsigned.apk")))
            AndroidBuildDeviceTest().verifyJni(app, app.spaces.commandRoot(owner).resolve(requireNotNull(exported.text("path"))))
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_PUBLIC_CACHE_FRESH_PASSED=$index\n") })
        } catch (failure: Throwable) { original = failure; throw failure }
        finally { withContext(NonCancellable) {
            try {
                environment.close()
                check(app.linux.runs.pendingCount(owner) == 0) { "Unconfirmed cache qualification retained: $owner" }
                app.spaces.delete(owner, app.tasks::quiesce)
            } catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
        } }
    }

    private suspend fun tools(app: AuroraApplication, owner: String, generation: String, script: String,
        id: String, timeout: Long): JsonObject = app.linux.runs.executeTools(owner,
        LinuxToolRun(listOf("/bin/sh", "-c", script), AndroidBuildTools.bundleId, timeout, id,
            requireNotNull(app.root.linuxExecution).identity), generation)

    private fun assertExit(result: JsonObject) {
        assertEquals(result.toString(), JsonPrimitive(true), result["stopped"])
        val completion = result.getValue("completion").jsonObject
        assertEquals(result.toString(), 0L, completion.number("exitCode"))
        assertEquals(JsonPrimitive(true), completion["scopeRemoved"])
        assertEquals(JsonPrimitive(false), completion["timedOut"])
        assertEquals(JsonPrimitive(false), completion["interrupted"])
    }
}
