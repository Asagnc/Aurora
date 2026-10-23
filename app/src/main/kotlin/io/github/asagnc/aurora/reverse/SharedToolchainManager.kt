package io.github.asagnc.aurora.reverse

import android.os.Build
import android.system.Os
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException

/** Manages shared installed bytes; runtime readiness is separate from installation completion. */
internal class SharedToolchainManager(private val app: AuroraApplication) {
    private val network = SkillNetwork()
    private val store = StaticReverseStore(app.filesDir.toPath().resolve("spaces/resources/static-reverse")) { component, archive, target ->
        StaticReversePreparation.prepare(component, archive, target) { path, mode -> Os.chmod(path.toString(), mode) }
    }
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
    suspend fun remove(id: String) = store.remove(id)
    suspend fun cleanup(): Int {
        try { return store.cleanup().also { cleanupPending = false } }
        catch (failure: Exception) { cleanupPending = true; throw failure }
    }
    suspend fun status(): JsonObject = objectOf(
        "catalogBundle" to string(StaticReverseTools.bundleId),
        "installed" to JsonArray(store.installed().map(::string)),
        "components" to JsonArray(StaticReverseTools.components.map { objectOf("id" to string(it.id), "version" to string(it.version)) }),
        "runtimeReady" to JsonPrimitive(false),
        "cleanupPending" to JsonPrimitive(cleanupPending),
        "reason" to string("Installed bytes are managed; Root readonly mount and durable execution leases are not connected yet"),
        "updatePolicy" to string("install/update selects the current compiled trusted catalogue; no unverified latest URL"))
}
