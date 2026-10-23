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
        StaticReversePreparation.prepare(component, archive, target) { path, mode -> Os.chmod(path.toString(), mode) }
    }
    private val qualifications = StaticReverseQualifications()
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
    suspend fun <T> reserve(id: String, record: suspend () -> T): T = store.reserve(id, record)
    suspend fun remove(id: String): Boolean {
        // Invalidate before suspension: cancellation/cleanup failure must not retain old evidence.
        qualifications.invalidate(id)
        try { return store.remove(id) } finally { qualifications.invalidate(id) }
    }
    suspend fun analyze(owner: String, request: LinuxToolRun, generation: String): JsonObject {
        val identity = requireNotNull(app.root.linuxExecution?.identity) { "Connect Root before analysis" }
        check(qualifications.ready(StaticReverseQualifications.Scope(generation, identity, request.bundle))) { "Run shared_toolchain probe for this environment first" }
        return app.linux.runs.executeTools(owner, request, generation)
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
            val proof = app.linux.runs.executeTools(owner, LinuxToolRun(argv, StaticReverseTools.bundleId, 60000, id), generation)
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
    suspend fun status(generation: String?): JsonObject {
        val installed = store.installed()
        val identity = app.root.linuxExecution?.identity
        val ready = generation != null && identity != null && StaticReverseTools.bundleId in installed &&
            qualifications.ready(StaticReverseQualifications.Scope(generation, identity, StaticReverseTools.bundleId))
        return objectOf(
        "catalogBundle" to string(StaticReverseTools.bundleId),
        "generation" to (generation?.let(::string) ?: JsonNull),
        "installed" to JsonArray(installed.map(::string)),
        "components" to JsonArray(StaticReverseTools.components.map { objectOf("id" to string(it.id), "version" to string(it.version)) }),
        "runtimeReady" to JsonPrimitive(ready),
        "cleanupPending" to JsonPrimitive(cleanupPending || store.cleanupFailed),
        "reason" to string(if (ready) "CLI startup qualified for the current Root identity, Linux generation and tool bundle"
            else "Install tools and run probe; startup qualification is required for each Root/environment identity"),
        "updatePolicy" to string("install/update selects the current compiled trusted catalogue; no unverified latest URL"))
    }
}
