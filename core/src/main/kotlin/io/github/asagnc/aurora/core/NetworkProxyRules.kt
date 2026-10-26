package io.github.asagnc.aurora.core

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Literal matches and replacements only; no caller-provided regex, Python, shell or framing-header edits. */
@Serializable data class NetworkProxyRule(val stage: String, val host: String, val pathPrefix: String = "/",
    val method: String? = null, val headers: Map<String, String?> = emptyMap(), val bodyBase64: String? = null,
    val direction: String? = null) {
    fun validate() {
        require(stage in setOf("request", "response", "websocket"))
        require(host.length in 1..253 && host == host.lowercase() && host.split('.').all {
            it.length in 1..63 && it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))
        }) { "Rule host must be an exact lowercase ASCII hostname or IPv4 address" }
        require(pathPrefix.startsWith('/') && pathPrefix.toByteArray(Charsets.UTF_8).size <= 1024 &&
            pathPrefix.none { it.code < 32 || it.code == 127 })
        require(method == null || method.length in 1..32 && method.matches(Regex("[A-Z][A-Z0-9_-]*")))
        require(headers.size <= 32 && headers.keys.map { it.lowercase() }.distinct().size == headers.size)
        for ((name, value) in headers) {
            require(name.length in 1..128 && name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")))
            require(name.lowercase() !in protectedHeaders) { "Proxy framing/routing headers are managed by the backend" }
            require(value == null || value.toByteArray(Charsets.UTF_8).size <= 2048 && value.none { it.code < 32 || it.code == 127 })
        }
        require(headers.entries.sumOf { it.key.length + (it.value?.toByteArray(Charsets.UTF_8)?.size ?: 0) } <= 16384)
        if (stage == "websocket") require(direction in setOf("client", "server") && headers.isEmpty() && bodyBase64 != null)
        else require(direction == null && (headers.isNotEmpty() || bodyBase64 != null))
        bodyBase64?.let { require(decodeReplacement(it).size <= 16384) }
    }

    companion object {
        private val protectedHeaders = setOf("host", "connection", "proxy-authorization", "proxy-authenticate",
            "transfer-encoding", "content-length", "content-encoding", "upgrade", "trailer", "te", "keep-alive")

        fun decodeReplacement(value: String): ByteArray {
            require(value.length <= 21848)
            val decoded = Base64.getDecoder().decode(value)
            require(decoded.size <= 16384 && Base64.getEncoder().encodeToString(decoded) == value) { "Replacement must use canonical padded Base64" }
            return decoded
        }
    }
}

@Serializable data class NetworkProxyOptions(val port: Int, val rules: List<NetworkProxyRule> = emptyList(),
    val durationSeconds: Int = 60, val maxFlows: Int = 64, val maxStoredBytes: Long = 8L * 1024 * 1024,
    val maxBodyBytes: Int = 1024 * 1024, val protocol: Int = 1) {
    fun validate() {
        require(protocol == 1 && port in 1024..65535 && durationSeconds in 1..240)
        require(maxFlows in 1..512 && maxStoredBytes in 4096..32L * 1024 * 1024 && maxBodyBytes in 1..1024 * 1024)
        require(rules.size <= 32); rules.forEach(NetworkProxyRule::validate)
        require(rules.all { it.bodyBase64 == null || NetworkProxyRule.decodeReplacement(it.bodyBase64).size <= maxBodyBytes })
        require(WireJson.encodeToString(this).toByteArray(Charsets.UTF_8).size <= 32768)
    }
    fun encoded(): String {
        validate()
        return Base64.getEncoder().encodeToString(WireJson.encodeToString(this).toByteArray(Charsets.UTF_8))
    }
}
