package io.github.asagnc.aurora.core

import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.*
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink

internal data class HttpReplayRequest(val request: Request, val proxy: Proxy, val timeoutMs: Long,
    val maxResponseBytes: Int, val bodySha256: String?) {
    companion object {
        suspend fun parse(arguments: JsonObject, files: OwnedFiles): HttpReplayRequest {
            require(arguments.keys.all { it in setOf("url", "method", "headers", "bodyBase64", "bodyPath",
                "bodySha256", "route", "proxyHost", "proxyPort", "timeoutMs", "maxResponseBytes") })
            val url = arguments.requiredText("url"); require(url.length <= 16384)
            val address = url.toHttpUrl()
            require(address.username.isEmpty() && address.password.isEmpty() && address.fragment == null)
            val method = arguments.requiredText("method")
            require(method.matches(Regex("[A-Z][A-Z0-9_-]{0,31}")))
            val headers = headers(arguments)
            val bytes = body(arguments, files)
            require(bytes == null || method !in setOf("GET", "HEAD"))
            val payload = bytes ?: if (method in setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")) byteArrayOf() else null
            val request = Request.Builder().url(address).headers(headers).method(method,
                payload?.let { oneShot(it, headers["Content-Type"]) }).build()
            return HttpReplayRequest(request, proxy(arguments), arguments.bounded("timeoutMs", 30_000, 1..60_000),
                arguments.bounded("maxResponseBytes", 1024 * 1024, 1..8 * 1024 * 1024).toInt(),
                payload?.let(::httpBodyDigest))
        }

        private suspend fun body(arguments: JsonObject, files: OwnedFiles): ByteArray? {
            val encoded = arguments.optionalText("bodyBase64")
            val path = arguments.optionalText("bodyPath")
            require(encoded == null || path == null)
            val expected = arguments.optionalText("bodySha256")
            require(expected == null || expected.matches(Regex("[a-f0-9]{64}")))
            val bytes = when {
                path != null -> { require(expected != null); files.readBinary(path, 8 * 1024 * 1024, allowEmpty = true) }
                encoded != null -> {
                    require(encoded.length <= 349528)
                    Base64.getDecoder().decode(encoded).also { require(it.size <= 256 * 1024) }
                }
                else -> { require(expected == null); return null }
            }
            require(expected == null || httpBodyDigest(bytes) == expected) { "Request body differs from expected SHA256" }
            return bytes
        }

        private fun headers(arguments: JsonObject): Headers {
            val entries = arguments["headers"]?.let { require(it is JsonArray); it } ?: JsonArray(emptyList())
            require(entries.size <= 64)
            var total = 0
            return Headers.Builder().apply { for (entry in entries) {
                require(entry is JsonObject && entry.keys == setOf("name", "value"))
                val name = entry.requiredText("name"); val value = entry.requiredText("value")
                require(name.lowercase() !in setOf("content-length", "transfer-encoding", "connection", "upgrade",
                    "keep-alive", "te", "trailer", "proxy-authorization", "proxy-connection"))
                total += name.length + value.length; require(total <= 32768)
                add(name, value)
            } }.build()
        }

        private fun proxy(arguments: JsonObject): Proxy {
            val route = arguments.requiredText("route")
            if (route == "direct") {
                require("proxyHost" !in arguments && "proxyPort" !in arguments)
                return Proxy.NO_PROXY
            }
            require(route in setOf("http", "socks"))
            val host = arguments.requiredText("proxyHost")
            require(host.length in 1..253 && host.matches(Regex("[A-Za-z0-9_.:%-]+")))
            val port = arguments.bounded("proxyPort", 0, 1..65535).toInt()
            return Proxy(if (route == "http") Proxy.Type.HTTP else Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved(host, port))
        }

        private fun oneShot(bytes: ByteArray, mime: String?): RequestBody = object : RequestBody() {
            override fun contentType() = mime?.toMediaType()
            override fun contentLength() = bytes.size.toLong()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
        }
    }
}

internal fun JsonObject.requiredText(key: String): String = requireNotNull(optionalText(key)) { "Missing $key" }
internal fun JsonObject.optionalText(key: String): String? = this[key]?.let {
    require(it is JsonPrimitive && it.isString); it.content
}
private fun JsonObject.bounded(key: String, default: Long, range: IntRange): Long {
    val value = this[key]?.let { require(it is JsonPrimitive && !it.isString); requireNotNull(it.longOrNull) } ?: default
    require(value in range.first.toLong()..range.last.toLong()); return value
}
internal fun httpBodyDigest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
