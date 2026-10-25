package io.github.asagnc.aurora.core

import java.nio.file.Path
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.ProtocolException
import javax.net.ssl.SSLException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Reuses the conversation task lease, ledger artifact directory and complete binary publisher. */
class HttpReplayTool(private val root: Path, private val prepared: suspend (String, String) -> Unit = { _, _ -> }) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("http_request",
        "Send one explicitly selected HTTP(S) request without Root. route must explicitly choose direct, http proxy or socks proxy; " +
            "proxyHost/proxyPort are required for proxies. No inherited browser/model cookies, credentials, cache, redirects or automatic retry. " +
            "TLS uses normal certificate/hostname verification. Ordered headers preserve duplicates; framing/hop-by-hop headers are rejected. " +
            "Optional bodyBase64 is at most 256 KiB, or bodyPath is a conversation file at most 8 MiB with required bodySha256. " +
            "GET/HEAD bodies are rejected. Response body (OkHttp decoded) is saved in the conversation, at most maxResponseBytes (default 1 MiB, max 8 MiB). " +
            "A small text preview and bounded headers are returned; HTTP errors retain their response. Timeout/cancellation/failed response does not undo " +
            "remote side effects: inspect before explicitly replaying an uncertain request. No WebView login context or raw TCP replay.",
        objectOf("type" to string("object"), "properties" to JsonObject(linkedMapOf(
            "url" to text(), "method" to text(), "route" to objectOf("type" to string("string"),
                "enum" to JsonArray(listOf("direct", "http", "socks").map(::string))),
            "proxyHost" to text(), "proxyPort" to integer(1, 65535),
            "headers" to objectOf("type" to string("array"), "maxItems" to JsonPrimitive(64),
                "items" to objectOf("type" to string("object"), "properties" to objectOf("name" to text(), "value" to text()),
                    "required" to JsonArray(listOf(string("name"), string("value"))), "additionalProperties" to JsonPrimitive(false))),
            "bodyBase64" to text(), "bodyPath" to text(), "bodySha256" to text(),
            "timeoutMs" to integer(1, 60000), "maxResponseBytes" to integer(1, 8 * 1024 * 1024))),
            "required" to JsonArray(listOf("url", "method", "route").map(::string)), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        require(call.name == "http_request")
        val options = HttpReplayRequest.parse(call.arguments, OwnedFiles(root))
        val artifact = UUID.randomUUID().toString()
        prepared(call.id, "runs/$artifact")
        val response = try { HttpReplayClient().execute(options) }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: IOException) { return@withContext uncertain(failure, artifact).toString() }
        try {
            currentCoroutineContext().ensureActive()
            val path = BrowserDownloadFile(root, options.maxResponseBytes.toLong(), artifact).use { file ->
                var offset = 0
                while (offset < response.bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val end = minOf(offset + 65536, response.bytes.size)
                    file.append(response.bytes.copyOfRange(offset, end)); offset = end
                }
                currentCoroutineContext().ensureActive(); file.publish(response.bytes.size.toLong())
            }
            result(response, options, path, artifact).toString()
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            JsonObject(result(response, options, null, artifact) + mapOf("error" to string("http_publication_failed"),
                "reason" to string(failure.javaClass.simpleName))).toString()
        }
    }

    private fun uncertain(failure: IOException, artifact: String): JsonObject {
        val reason = when (failure) {
            is HttpResponseLimitExceeded -> "response_byte_budget"
            is SSLException -> "tls_failed"
            is InterruptedIOException -> "timeout"
            is UnknownHostException -> "dns_failed"
            is ConnectException -> "connect_failed"
            is ProtocolException -> "protocol_failed"
            else -> "io_failed"
        }
        return objectOf("error" to string("http_outcome_uncertain"), "reason" to string(reason),
            "artifactDirectory" to string("runs/$artifact"), "bodyPublished" to JsonPrimitive(false),
            "remoteOutcome" to string("unknown; inspect before explicitly replaying"))
    }

    private fun result(response: HttpReplayResponse, options: HttpReplayRequest, path: String?, artifact: String): JsonObject {
        var count = 0
        val headers = response.headers.toList().take(128).takeWhile { (name, value) ->
            count += name.length + value.length; count <= 32768
        }
        return objectOf("status" to JsonPrimitive(response.status), "httpSuccessful" to JsonPrimitive(response.status in 200..299),
            "protocol" to string(response.protocol), "artifactDirectory" to string("runs/$artifact"),
            "bodyPath" to (path?.let(::string) ?: JsonNull), "bodyBytes" to JsonPrimitive(response.bytes.size),
            "bodySha256" to string(httpBodyDigest(response.bytes)), "requestBodySha256" to (options.bodySha256?.let(::string) ?: JsonNull),
            "bodyComplete" to JsonPrimitive(true), "bodyEncoding" to string("okhttp-decoded"),
            "preview" to string(response.bytes.copyOfRange(0, minOf(2048, response.bytes.size)).toString(Charsets.UTF_8)),
            "headers" to JsonArray(headers.map { (name, value) -> objectOf("name" to string(name), "value" to string(value)) }),
            "headersClipped" to JsonPrimitive(headers.size != response.headers.size))
    }

    companion object {
        private fun text() = objectOf("type" to string("string"))
        private fun integer(minimum: Int, maximum: Int) = objectOf("type" to string("integer"),
            "minimum" to JsonPrimitive(minimum), "maximum" to JsonPrimitive(maximum))
    }
}
