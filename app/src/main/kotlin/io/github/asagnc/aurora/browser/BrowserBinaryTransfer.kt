package io.github.asagnc.aurora.browser

import android.net.Uri
import androidx.webkit.*
import io.github.asagnc.aurora.core.*
import java.net.URI
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*

/** One bounded, document-scoped channel; no global page-to-native file methods. */
internal object BrowserBinaryTransfer {
    class Session(private val port: WebMessagePortCompat, private val incoming: Channel<WebMessageCompat>) {
        suspend fun control(expected: String): JsonObject {
            val message = incoming.receive()
            check(message.type == WebMessageCompat.TYPE_STRING) { "Expected transfer control" }
            val raw = requireNotNull(message.data)
            require(raw.length <= 2048)
            val value = WireJson.parseToJsonElement(raw) as? JsonObject ?: error("Invalid transfer control")
            // Never expose arbitrary page error text: it may contain URLs or request credentials.
            check(value.text("kind") != "error") { "Page transfer failed; inspect page before retrying" }
            check(value.text("kind") == expected) { "Unexpected transfer phase" }
            return value
        }
        suspend fun receive(): WebMessageCompat = incoming.receive()
        fun acknowledge() { post(port, WebMessageCompat("ack")) }
        fun sendControl(kind: String) { post(port, WebMessageCompat(objectOf("kind" to string(kind)).toString())) }
        fun sendBytes(bytes: ByteArray) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)) post(port, WebMessageCompat(bytes))
            else error("Binary WebView messages unavailable")
        }
    }

    suspend fun <T> execute(tab: BrowserTab, request: JsonObject, timeout: Long, action: suspend Session.() -> T): T {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)) { "Update system WebView for binary transfers" }
        check(WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL))
        check(WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE))
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_POST_MESSAGE))
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_SET_MESSAGE_CALLBACK))
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_CLOSE))
        val address = requireNotNull(tab.view.url)
        val uri = URI(address)
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null)
        val origin = Uri.parse(URI(uri.scheme, null, uri.host, uri.port, null, null, null).toASCIIString())
        val revision = tab.revision
        val id = UUID.randomUUID().toString()
        val ports = if (WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL))
            WebViewCompat.createWebMessageChannel(tab.view) else error("WebView channels unavailable")
        val incoming = Channel<WebMessageCompat>(Channel.RENDEZVOUS)
        var transferred = false
        try {
            return withTimeout(timeout) { awaitBrowserDocumentResult(tab.document, revision) {
                val ready = suspendCancellableCoroutine<String> { continuation ->
                    tab.view.evaluateJavascript(BrowserTransferJavascript.setup(id, request, timeout)) { raw ->
                        if (continuation.isActive) continuation.resume(raw ?: "null")
                    }
                }
                check(ready == string(id).toString() && !tab.closed && !tab.loading && tab.revision == revision && tab.view.url == address) {
                    "Page changed before transfer dispatch"
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_SET_MESSAGE_CALLBACK)) ports[0].setWebMessageCallback(object : WebMessagePortCompat.WebMessageCallbackCompat() {
                    override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                        if (message == null) { incoming.close(IllegalStateException("Missing transfer message")); return }
                        if (!message.ports.isNullOrEmpty()) {
                            message.ports?.forEach { runCatching { close(it) } }
                            incoming.close(IllegalStateException("Unexpected transferred port")); return
                        }
                        val bounded = when (message.type) {
                            WebMessageCompat.TYPE_STRING -> message.data?.length?.let { it <= 2048 } == true
                            WebMessageCompat.TYPE_ARRAY_BUFFER -> WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER) && message.arrayBuffer.size in 1..65536
                            else -> false
                        }
                        if (!bounded) { incoming.close(IllegalStateException("Transfer message exceeded budget")); return }
                        if (!incoming.trySend(message).isSuccess) incoming.close(IllegalStateException("Transfer exceeded one-message backpressure"))
                    }
                }) else error("WebView callbacks unavailable")
                if (WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE))
                    WebViewCompat.postWebMessage(tab.view, WebMessageCompat(id, arrayOf(ports[1])), origin)
                else error("WebView dispatch unavailable")
                transferred = true
                Session(ports[0], incoming).action()
            } }
        } finally {
            incoming.cancel()
            runCatching { close(ports[0]) }
            if (!transferred) runCatching { close(ports[1]) }
            if (!tab.closed && tab.revision == revision && tab.view.url == address) {
                runCatching { tab.view.evaluateJavascript(BrowserTransferJavascript.cleanup(id), null) }
            }
        }
    }
    private fun post(port: WebMessagePortCompat, message: WebMessageCompat) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_POST_MESSAGE)) port.postMessage(message)
        else error("WebView message dispatch unavailable")
    }
    private fun close(port: WebMessagePortCompat) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_CLOSE)) port.close()
        else error("WebView port closure unavailable")
    }
}
