package io.github.asagnc.aurora.reverse

import android.os.Build
import android.system.Os
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import java.util.UUID
import io.github.asagnc.aurora.linux.LinuxToolRun

/** Manages shared installed bytes; runtime readiness is separate from installation completion. */
internal class SharedToolchainManager(private val app: AuroraApplication) {
    private val network = SkillNetwork()
    private val store = StaticReverseStore(app.filesDir.toPath().toRealPath().resolve("spaces/resources/static-reverse"),
        inUse = { app.linux.runs.usesToolBundle(it) }) { component, archive, target ->
        if (component in AndroidBuildTools.components)
            AndroidBuildPreparation.prepare(component, archive, target) { path, mode -> Os.chmod(path.toString(), mode) }
        else if (component == NetworkProxyPreparation.component)
            NetworkProxyPreparation.prepare(component, archive, target) { path, mode -> Os.chmod(path.toString(), mode) }
        else StaticReversePreparation.prepare(component, archive, target) { path, mode -> Os.chmod(path.toString(), mode) }
    }
    private val qualifications = StaticReverseQualifications()
    val proxy by lazy { NetworkProxyManager(app, store, qualifications) }
    @Volatile private var cleanupPending = false
    suspend fun recover() {
        try { store.cleanup(); cleanupPending = false }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { cleanupPending = true }
    }
    suspend fun install(): String {
        check(Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") { "Static reverse runtime requires ARM64" }
        check(app.root.linuxExecution != null) { "Connect Root before installing the Root toolchain" }
        return store.install { component, target -> network.download(component.url, target, component.compressedBytes) }
    }
    suspend fun installAndroid(nativeSource: OwnedFiles, nativePath: String): String {
        check(Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") { "Android build runtime requires ARM64" }
        check(app.root.linuxExecution != null) { "Connect Root before installing the Root toolchain" }
        val metadata = AndroidBuildTools.metadataSha256.keys.associateWith { app.assets.open("apk-build/$it").use { input -> input.readBytes() } }
        val catalog = AndroidBuildTools.catalog { stage ->
            AndroidBuildPreparation.finish(stage, metadata) { path, mode -> Os.chmod(path.toString(), mode) }
        }
        return store.install(catalog) { component, target ->
            if (component.localOnly) nativeSource.copyVerifiedArchive(nativePath, target, component)
            else network.downloadTool(component, target)
        }
    }
    suspend fun installAndroid(owner: String, source: String, path: String): String {
        val files = when (source) {
            "conversation" -> app.spaces.conversation(owner)
            "resources" -> app.spaces.sharedResources()
            "workspace" -> app.spaces.sharedWorkspace()
            else -> error("Unknown native archive namespace")
        }
        return installAndroid(files, path)
    }
    suspend fun probeAndroid(owner: String, generation: String, prepared: suspend (String) -> Unit): JsonObject {
        val identity = requireNotNull(app.root.linuxExecution?.identity) { "Reconnect Root for tool mounts" }
        val scope = StaticReverseQualifications.Scope(generation, identity, AndroidBuildTools.bundleId)
        val attempt = qualifications.begin(scope)
        val id = UUID.randomUUID().toString(); prepared(id)
        val result = app.linux.runs.executeTools(owner, LinuxToolRun(listOf(AndroidBuildRuntime.probePath, id),
            AndroidBuildTools.bundleId, 270000, id, identity), generation)
        val passed = androidBuildProbePassed(StaticReverseProbeContext(0, id, generation), result) && app.root.linuxExecution?.identity == identity
        val ready = passed && qualifications.complete(attempt)
        return objectOf("qualified" to JsonPrimitive(ready), "result" to result, "logDirectory" to string("runs/$id"),
            "linuxProject" to string("linux:project/.aurora-apk-probes/$id"))
    }
    suspend fun buildAndroid(owner: String, generation: String, request: AndroidBuildRequest, id: String): JsonObject {
        val identity = requireNotNull(app.root.linuxExecution?.identity) { "Connect Root before building" }
        check(qualifications.ready(StaticReverseQualifications.Scope(generation, identity, AndroidBuildTools.bundleId))) {
            "Run shared_toolchain family=android-build probe for this environment first"
        }
        return app.linux.runs.executeTools(owner, LinuxToolRun(request.argv, AndroidBuildTools.bundleId, 270000, id, identity), generation)
    }
    suspend fun exportAndroid(owner: String, path: String): JsonObject {
        val selected = LinuxFileExport(path.removePrefix("linux:"), apk = true)
        val result = requireNotNull(app.root.linuxFileClient) { "Connect Root to export the selected APK" }.invoke(owner, "export_apk",
            objectOf("path" to string(selected.path), "maxBytes" to JsonPrimitive(selected.maxBytes)))
        require(result.text("sha256")?.matches(Regex("[a-f0-9]{64}")) == true)
        val name = selected.path.substringAfterLast('/').take(100).replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
        return JsonObject(result + mapOf("mime" to string("application/vnd.android.package-archive"), "suggestedName" to string(name),
            "installed" to JsonPrimitive(false), "signatureVerified" to JsonPrimitive(false)))
    }
    suspend fun <T> reserve(id: String, record: suspend () -> T): T = store.reserve(id, record)
    suspend fun remove(id: String): Boolean {
        // Invalidate before suspension: cancellation/cleanup failure must not retain old evidence.
        qualifications.invalidate(id)
        try { return store.remove(id) } finally { qualifications.invalidate(id) }
    }
    suspend fun analyze(owner: String, request: LinuxToolRun, generation: String): JsonObject {
        val identity = requireNotNull(app.root.linuxExecution?.identity) { "Connect Root before analysis" }
        check(qualifications.ready(StaticReverseQualifications.Scope(generation, identity, request.bundle))) { "Run shared_toolchain probe for this environment first" }
        return app.linux.runs.executeTools(owner, request.copy(expectedIdentity = identity), generation)
    }
    suspend fun probe(owner: String, generation: String, prepared: suspend (String) -> Unit): JsonObject {
        val identity = requireNotNull(app.root.linuxExecution?.identity) { "Reconnect Root for tool mounts" }
        val attempt = qualifications.begin(StaticReverseQualifications.Scope(generation, identity, StaticReverseTools.bundleId))
        val commands = staticReverseStartupCommands()
        val results = mutableListOf<JsonElement>()
        val logs = mutableListOf<JsonElement>()
        for ((index, argv) in commands.withIndex()) {
            val id = UUID.randomUUID().toString(); prepared(id)
            logs += string("runs/$id")
            val proof = app.linux.runs.executeTools(owner, LinuxToolRun(argv, StaticReverseTools.bundleId, 60000, id, identity), generation)
            results += proof
            val passed = staticReverseProbePassed(StaticReverseProbeContext(index, id, generation), proof) &&
                app.root.linuxExecution?.identity == identity
            if (!passed) return objectOf("error" to string("tool_probe_failed"), "qualified" to JsonPrimitive(false), "failedStep" to JsonPrimitive(index),
                "results" to JsonArray(results), "logDirectories" to JsonArray(logs))
        }
        val accepted = qualifications.complete(attempt)
        return objectOf("qualified" to JsonPrimitive(accepted), "results" to JsonArray(results), "logDirectories" to JsonArray(logs),
            "reason" to string(if (accepted) "startup_qualified" else "qualification_superseded"))
    }
    suspend fun cleanup(): Int {
        try { return store.cleanup().also { cleanupPending = false } }
        catch (failure: Exception) { cleanupPending = true; throw failure }
    }
    suspend fun status(generation: String?, family: String = "static-reverse"): JsonObject {
        val installed = store.installed()
        val catalog = when (family) {
            "static-reverse" -> StaticReverseTools.catalog
            "android-build" -> AndroidBuildTools.catalog {}
            "network-proxy" -> NetworkProxyRuntime.catalog
            else -> error("Unknown shared tool family")
        }
        val bundle = catalog.bundleId
        val components = catalog.components
        val identity = app.root.linuxExecution?.identity
        val ready = generation != null && identity != null && bundle in installed &&
            qualifications.ready(StaticReverseQualifications.Scope(generation, identity, bundle))
        return objectOf(
        "catalogBundle" to string(bundle),
        "family" to string(family),
        "nativeArchiveRequired" to JsonPrimitive(family == "android-build"),
        "generation" to (generation?.let(::string) ?: JsonNull),
        "installed" to JsonArray(installed.map(::string)),
        "components" to JsonArray(components.map { objectOf("id" to string(it.id), "version" to string(it.version)) }),
        "runtimeReady" to JsonPrimitive(ready),
        "cleanupPending" to JsonPrimitive(cleanupPending || store.cleanupFailed),
        "reason" to string(if (ready) "Tool runtime qualified for the current Root identity, Linux generation and tool bundle"
            else "Install tools and run probe; startup qualification is required for each Root/environment identity"),
        "updatePolicy" to string("install/update selects the current compiled trusted catalogue; no unverified latest URL"))
    }
}
