package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.LinuxRunRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Explicit maintenance only; native guard and old-epoch checks remain authoritative. */
internal class LinuxMetadataCleaner(private val app: AuroraApplication) {
    private val gate = Mutex()
    suspend fun collect(only: Set<String>? = null): JsonObject = gate.withLock {
        val client = requireNotNull(app.root.metadataClient) { "Reconnect Root for metadata maintenance" }
        var token: String? = null
        var collected = 0
        var retained = 0
        try {
            var page = client.invoke(objectOf("action" to string("begin")))
            token = requireNotNull(page.text("token"))
            while (true) {
                currentCoroutineContext().ensureActive()
                val candidates = page.array("candidates").map { it.jsonObject }
                require(candidates.size <= 16)
                retained += Math.toIntExact(page.number("retained") ?: 0)
                val protected = LinuxRunRegistry(app.conversations).pendingIds(candidates.map { requireNotNull(it.text("id")) })
                for (candidate in candidates) {
                    currentCoroutineContext().ensureActive()
                    if (candidate.text("id") in protected || only != null && candidate.text("id") !in only) { retained++; continue }
                    val result = client.invoke(JsonObject(candidate + mapOf("action" to string("collect"), "token" to string(token))))
                    if (result["collected"] == JsonPrimitive(true)) collected++ else retained++
                }
                if (page["done"] == JsonPrimitive(true)) break
                page = client.invoke(objectOf("action" to string("next"), "token" to string(token)))
            }
            objectOf("collected" to JsonPrimitive(collected), "retained" to JsonPrimitive(retained))
        } finally { token?.let { cursor -> withContext(NonCancellable) {
            runCatching { client.invoke(objectOf("action" to string("close"), "token" to string(cursor))) }
        } } }
    }
}
