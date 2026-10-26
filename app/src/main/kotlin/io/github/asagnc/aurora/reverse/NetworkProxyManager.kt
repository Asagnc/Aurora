package io.github.asagnc.aurora.reverse

import android.os.Build
import android.system.Os
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.*
import io.github.asagnc.aurora.terminal.*
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Uses the existing installer, qualification cache, terminal host and durable Linux leases. */
internal class NetworkProxyManager(private val app: AuroraApplication, private val store: StaticReverseStore,
    private val qualifications: StaticReverseQualifications) {
    private fun scope(generation: String) = StaticReverseQualifications.Scope(generation,
        requireNotNull(app.root.linuxExecution?.identity) { "Connect Root for the proxy runtime" }, NetworkProxyRuntime.bundleId)

    suspend fun install(): String {
        check(Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") { "Proxy runtime requires ARM64" }
        check(app.root.linuxExecution != null) { "Connect Root before installing the proxy runtime" }
        val profiles = NetworkProxyRuntime.profileSha256.keys.associateWith { app.assets.open("network-proxy/$it").use { input -> input.readBytes() } }
        val selected = NetworkProxyRuntime.catalog.copy(finish = { stage ->
            NetworkProxyRuntime.publishProfiles(stage, profiles) { path, bits -> Os.chmod(path.toString(), bits) }
        })
        return store.install(selected) { component, target -> SkillNetwork().downloadTool(component, target) }
    }

    suspend fun probe(owner: String, generation: String, port: Int, prepared: suspend (String) -> Unit): JsonObject {
        val current = scope(generation); val attempt = qualifications.begin(current)
        val options = NetworkProxyRuntime.probeOptions(port); val id = UUID.randomUUID().toString(); prepared(id)
        val argv = listOf(NetworkProxyRuntime.probePath, id, port.toString(), options.encoded())
        val result = app.linux.runs.executeTools(owner, LinuxToolRun(argv, current.bundle, 270000, id, current.identity), generation)
        val closed = NetworkProxyRuntime.probePassed(StaticReverseProbeContext(0, id, generation), result)
        val final = if (closed) readFinal(owner, id) else null
        val passed = closed && final != null && NetworkProxyRuntime.finalProbePassed(id, options, final) && app.root.linuxExecution?.identity == current.identity
        val cleanup = if (result["stopped"] == JsonPrimitive(true)) cleanup(owner, id) else objectOf("pending" to JsonPrimitive(true))
        val ready = passed && cleanup["pending"] == JsonPrimitive(false) && app.root.linuxExecution?.identity == current.identity && qualifications.complete(attempt)
        return objectOf("qualified" to JsonPrimitive(ready), "result" to result, "final" to (final ?: JsonNull),
            "logDirectory" to string("runs/$id"), "cleanup" to cleanup, "linuxArtifacts" to string("linux:${networkProxyDirectory(id)}"))
    }

    private suspend fun readFinal(owner: String, id: String): JsonObject? {
        val response = requireNotNull(app.root.linuxFileClient).invoke(owner, "files_read_batch", objectOf("files" to JsonArray(listOf(
            objectOf("path" to string("${networkProxyDirectory(id)}/final.json"), "limit" to JsonPrimitive(4096))))))
        val file = response.array("files").single().jsonObject
        val text = file.text("text") ?: return null
        val size = file.number("size") ?: return null
        if (file["error"] != JsonNull || size !in 1L..4096L || file["next"] != JsonNull) return null
        return WireJson.parseToJsonElement(text) as? JsonObject
    }

    suspend fun start(owner: String, generation: String, options: NetworkProxyOptions, prepared: suspend (String) -> Unit): JsonObject {
        val current = scope(generation)
        check(qualifications.ready(current)) { "Run shared_toolchain family=network-proxy probe for this Root/environment first" }
        val encoded = options.encoded(); val id = UUID.randomUUID().toString()
        val metadata = objectOf("kind" to string("proxy-session"), "session" to string(id), "terminal" to string(id),
            "generation" to string(generation), "bundle" to string(current.bundle), "port" to JsonPrimitive(options.port),
            "configSha256" to string(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(encoded)).joinToString("") { "%02x".format(it.toInt() and 255) }),
            "authority" to string("Runtime state belongs to the terminal and Linux exit proof; this artifact is only a session reference"))
        app.spaces.conversation(owner).editBatch(listOf(FileEdit("runs/$id/proxy-session.json", null, metadata.toString())))
        prepared(id)
        val timeout = options.durationSeconds * 1000L + 30000
        val request = PtyRequest(argv = listOf(NetworkProxyRuntime.launcherPath, id, options.port.toString(), encoded), timeoutMs = timeout, awakeMs = timeout)
        try {
            app.terminals.startLinuxTools(owner, LinuxToolTerminal(id, LinuxTerminalRun(request, generation, current.bundle, current.identity)) {
                if (cleanup(owner, id)["pending"] == JsonPrimitive(true)) "Proxy private cache cleanup remains pending for $id" else null
            })
            val ready = awaitReady(owner, id, options.port)
            check(app.root.linuxExecution?.identity == current.identity) { "Root identity changed during proxy startup" }
            return objectOf("session" to string(id), "running" to JsonPrimitive(true), "address" to string("127.0.0.1:${options.port}"),
                "terminal" to ready, "durationSeconds" to JsonPrimitive(options.durationSeconds),
                "linuxArtifacts" to string("linux:${networkProxyDirectory(id)}"), "ca" to string("linux:${networkProxyDirectory(id)}/ca-cert.pem"),
                "policy" to string("Explicit loopback proxy only; no global route or system CA changes. Stop before offline flow inspection."))
        } catch (failure: Throwable) {
            withContext(NonCancellable) { runCatching { app.terminals.stop(owner, id) }.onFailure { failure.addSuppressed(it) } }
            throw failure
        }
    }

    private suspend fun awaitReady(owner: String, id: String, port: Int): JsonObject = withTimeout(30000) {
        var cursor = 0L; var tail = ""
        val marker = "aurora-proxy-ready:$id:127.0.0.1:$port"
        while (true) {
            val update = app.terminals.read(owner, id, TerminalRead(cursor, 8192, 1000))
            tail = (tail + update.text("text").orEmpty()).takeLast(8192)
            check(update["stopping"] != JsonPrimitive(true)) { "Proxy startup was cancelled" }
            check(update.text("state") in setOf("starting", "running")) { "Proxy stopped before confirming its bound loopback address" }
            if (tail.lineSequence().any { it.trimEnd() == marker }) {
                check(update.text("state") == "running")
                return@withTimeout update
            }
            cursor = requireNotNull(update.number("next"))
        }
        @Suppress("UNREACHABLE_CODE") error("Proxy readiness was not confirmed")
    }

    suspend fun stop(owner: String, id: String): JsonObject {
        networkProxyDirectory(id)
        val terminal = app.terminals.stop(owner, id)
        val recovery = app.linux.runs.recoverRun(owner, id)
        val cleanup = if (recovery["pending"] == JsonPrimitive(false)) cleanup(owner, id) else objectOf("pending" to JsonPrimitive(true))
        return objectOf("session" to string(id), "terminal" to terminal.json(), "linuxRecovery" to recovery, "cleanup" to cleanup,
            "error" to if (recovery["pending"] == JsonPrimitive(true) || cleanup["pending"] == JsonPrimitive(true)) string("proxy_cleanup_unconfirmed") else JsonNull)
    }

    suspend fun inspect(owner: String, generation: String, request: NetworkProxyRead, prepared: suspend (String) -> Unit): JsonObject {
        check(app.linux.runs.pendingCount(owner) == 0) { "Stop and recover the proxy before offline inspection; use file tools for the live index" }
        val current = scope(generation); val id = UUID.randomUUID().toString(); prepared(id)
        val result = app.linux.runs.executeTools(owner, LinuxToolRun(request.argv(id), current.bundle, 60000, id, current.identity), generation)
        val stdout = ((result["result"] as? JsonObject)?.get("stdout") as? JsonObject)?.text("text").orEmpty()
        val frames = stdout.lineSequence().filter { it.startsWith("aurora-proxy-read:") }.toList()
        val complete = NetworkProxyRuntime.closedToolPassed(StaticReverseProbeContext(0, id, generation), result)
        val inspection = if (complete && frames.size == 1) WireJson.parseToJsonElement(frames.single().removePrefix("aurora-proxy-read:")) as? JsonObject else null
        val cleanup = if (result["stopped"] == JsonPrimitive(true)) cleanup(owner, id) else objectOf("pending" to JsonPrimitive(true))
        return objectOf("result" to result, "inspection" to (inspection ?: JsonNull), "logDirectory" to string("runs/$id"),
            "cleanup" to cleanup,
            "error" to if (inspection == null || inspection["error"] != null || inspection["parseError"] == JsonPrimitive(true)) string("proxy_inspection_incomplete") else JsonNull)
    }

    suspend fun cleanup(owner: String, id: String): JsonObject {
        networkProxyDirectory(id)
        return try {
            requireNotNull(app.root.linuxFileClient).invoke(owner, "proxy_cleanup", objectOf("session" to string(id)))
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { objectOf("pending" to JsonPrimitive(true), "session" to string(id), "reason" to string("Root cache cleanup is unconfirmed; original files are retained")) }
    }

    suspend fun exportCa(owner: String, id: String): JsonObject {
        val client = requireNotNull(app.root.linuxFileClient) { "Connect Root to export the public proxy certificate" }
        val path = "${networkProxyDirectory(id)}/ca-cert.pem"
        val read = client.invoke(owner, "files_read_batch", objectOf("files" to JsonArray(listOf(objectOf("path" to string(path), "limit" to JsonPrimitive(16384))))))
        val file = read.array("files").single().jsonObject
        val size = requireNotNull(file.number("size"))
        require(file["error"] == JsonNull && file["next"] == JsonNull && size in 1L..16384L)
        val hash = publicProxyCaSha256(requireNotNull(file.text("text")).toByteArray(Charsets.UTF_8))
        require(hash == file.text("version"))
        val exported = client.invoke(owner, "export", objectOf("path" to string(path), "maxBytes" to JsonPrimitive(16384), "sha256" to string(hash), "extension" to string("pem")))
        require(exported.text("sha256") == hash) { "Root did not confirm the selected public certificate bytes" }
        return JsonObject(exported + mapOf("publicOnly" to JsonPrimitive(true), "mime" to string("application/x-pem-file"), "suggestedName" to string("aurora-proxy-ca.pem")))
    }
}
