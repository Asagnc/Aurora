package io.github.asagnc.aurora.core

import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Only the documented official router accepts these hints; no probing or paid warming. */
internal object ProviderCacheRouting {
    fun apply(request: ModelRequest, fields: MutableMap<String, JsonElement>) {
        val endpoint = runCatching { URI(request.connection.baseUrl) }.getOrNull() ?: return
        if (!endpoint.scheme.orEmpty().equals("https", true) || !endpoint.host.orEmpty().equals("openrouter.ai", true) ||
            endpoint.userInfo != null || endpoint.port !in setOf(-1, 443)) return
        request.cacheScope?.takeIf { it.isNotBlank() }?.let { scope ->
            val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
            fields["session_id"] = string("aurora-" + digest.joinToString("") { "%02x".format(it) })
        }
        val model = request.connection.model.removePrefix("~").lowercase(java.util.Locale.ROOT)
        if (request.connection.protocol in setOf(Protocol.CHAT, Protocol.RESPONSES) && model.startsWith("anthropic/claude-"))
            fields["cache_control"] = objectOf("type" to string("ephemeral"))
    }
}
