package io.github.asagnc.aurora.reverse

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import dalvik.system.DexClassLoader
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxAgentEnvironment
import io.github.asagnc.aurora.linux.LinuxToolRun
import io.github.asagnc.aurora.root.RootRequired
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit fixed archive/provider and actual ARM64 Java/Compose/JNI probe.
 * FileProvider delivery is not a visual SAF chooser or persisted-grant test. */
@RunWith(AndroidJUnit4::class)
class AndroidBuildDeviceTest {
    @RootRequired @Test fun fixedNativeArchiveThroughProviderThenRealReadonlyBuildProbe() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        for (name in listOf("auroraRootTest", "auroraAndroidBuildTest")) check(args.getString(name) == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val keep = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val wasKept = activity.window.attributes.flags and keep != 0
        instrumentation.runOnMainSync { activity.window.addFlags(keep) }
        instrumentation.waitForIdleSync(); app.root.connect()
        val owner = args.getString("auroraBuildOwner")?.also {
            require(java.util.UUID.fromString(it).toString() == it)
            require(app.conversations.header(it)?.title == "Android build qualification fixture")
        } ?: app.conversations.create("Android build qualification fixture")
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_APK_FIXTURE_OWNER=$owner\n") })
        var original: Throwable? = null
        try {
            check(app.root.linuxExecution != null && app.linux.runs.pendingCount() == 0)
            install(app, owner, args)
            probe(app, owner, args)
        } catch (failure: Throwable) { original = failure; throw failure }
        finally { withContext(NonCancellable) {
            try {
                check(app.linux.runs.pendingCount(owner) == 0) { "Unconfirmed Android build fixture retained: $owner" }
                if (original == null || args.getString("auroraRetainBuildFailure") != "true")
                    app.spaces.delete(owner, app.tasks::quiesce)
            } catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
            finally {
                app.root.disconnect()
                if (!wasKept) instrumentation.runOnMainSync { activity.window.clearFlags(keep) }
            }
        } }
    }

    /** Reuses only this fixture's generated project; never grants runtime qualification. */
    private suspend fun diagnostic(app: AuroraApplication, owner: String, project: String): Nothing {
        require(java.util.UUID.fromString(project).toString() == project)
        val environment = LinuxAgentEnvironment(app.linux, owner)
        try {
            val generation = environment.generation()
            val identity = requireNotNull(app.root.linuxExecution?.identity)
            val id = java.util.UUID.randomUUID().toString()
            val result = app.linux.runs.executeTools(owner, LinuxToolRun(listOf(AndroidBuildRuntime.launcherPath,
                "/mnt/.aurora-apk-probes/$project", "--info", "assembleRelease"), AndroidBuildTools.bundleId,
                270_000, id, identity), generation)
            error("Diagnostic only; runtime qualification not granted. logs=runs/$id completion=${result["completion"]}")
        } finally { environment.close() }
    }

    private suspend fun install(app: AuroraApplication, owner: String, args: Bundle) {
        if (app.sharedToolchain.status(app.linux.registry.current(), "android-build").array("installed").contains(string(AndroidBuildTools.bundleId))) {
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "AURORA_APK_INSTALLED_BUNDLE_REUSED\n") })
            return
        }
        val files = app.spaces.commandRoot(owner)
        val native = app.cacheDir.toPath().resolve("android-build-device/android-host-arm64.tar.gz")
        check(Files.isRegularFile(native, LinkOption.NOFOLLOW_LINKS)) { "Stage the fixed native input in the owned cache first" }
        val selected = files.resolve("android-host-arm64.tar.gz")
        Files.move(native, selected, StandardCopyOption.ATOMIC_MOVE)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.images", selected.toFile())
        assertEquals(AndroidBuildTools.bundleId, withTimeout(900_000) { toolInstallationFixture(app, args).installAndroidArchive(uri) })
    }

    private suspend fun probe(app: AuroraApplication, owner: String, args: Bundle) {
        val environment = LinuxAgentEnvironment(app.linux, owner)
        try {
            val generation = environment.generation()
            args.getString("auroraToolDownloadProxyPort")?.toInt()?.also { port ->
                require(port in 1024..65535)
                val settings = "systemProp.https.proxyHost=127.0.0.1\nsystemProp.https.proxyPort=$port\n"
                val encoded = java.util.Base64.getEncoder().encodeToString(settings.toByteArray())
                val setup = app.linux.runs.execute(owner, listOf("/bin/sh", "-c",
                    "mkdir -p /mnt/.gradle-cache; printf %s '$encoded' | base64 -d > /mnt/.gradle-cache/gradle.properties"))
                assertEquals(setup.toString(), JsonPrimitive(true), setup["stopped"])
                assertEquals(0L, setup["completion"]?.jsonObject?.number("exitCode"))
            }
            args.getString("auroraBuildDiagnosticProject")?.let { diagnostic(app, owner, it) }
            val result = app.sharedToolchain.probeAndroid(owner, generation) { }
            assertEquals(result.toString(), JsonPrimitive(true), result["qualified"])
            assertEquals(JsonPrimitive(true), app.sharedToolchain.status(generation, "android-build")["runtimeReady"])
            val id = requireNotNull(result.text("logDirectory")).substringAfterLast('/')
            val exported = requireNotNull(app.root.linuxFileClient).invoke(owner, "export", objectOf("path" to string(
                "project/.aurora-apk-probes/$id/build/outputs/apk/release/aurora-sdk-probe-release-unsigned.apk")))
            val apk = app.spaces.commandRoot(owner).resolve(requireNotNull(exported.text("path")))
            Files.newInputStream(apk).use { input -> assertArrayEquals(byteArrayOf(80, 75, 3, 4), input.readNBytes(4)) }
            verifyJni(app, apk)
        } finally { environment.close() }
    }

    internal fun verifyJni(app: AuroraApplication, apk: Path) {
        val temporary = Files.createTempDirectory(app.cacheDir.toPath(), "owned-sdk-jni-")
        try {
            val selected = temporary.resolve("owned.apk")
            Files.copy(apk, selected)
            check(selected.toFile().setReadOnly())
            ZipFile(selected.toFile()).use { archive ->
                val libraries = archive.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("lib/arm64-v8a/") }.toList()
                require(libraries.size in 1..8)
                for (entry in libraries) extractLibrary(archive, entry, temporary)
            }
            val loader = DexClassLoader(selected.toString(), app.codeCacheDir.path, temporary.toString(), app.classLoader)
            assertEquals("owned-42", loader.loadClass("io.github.asagnc.aurora.sdkprobe.Owned").getMethod("nativeMarker").invoke(null))
        } finally { removeSkillTree(temporary) }
    }

    private fun extractLibrary(archive: ZipFile, entry: ZipEntry, directory: Path) {
        val name = entry.name.removePrefix("lib/arm64-v8a/")
        require(name.matches(Regex("[A-Za-z0-9_+.-]+\\.so")) && entry.size in 1..33554432)
        val target = directory.resolve(name)
        archive.getInputStream(entry).use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= entry.size); output.write(buffer, 0, count)
                }
                require(total == entry.size)
            }
        }
        check(target.toFile().setReadOnly())
    }

}
