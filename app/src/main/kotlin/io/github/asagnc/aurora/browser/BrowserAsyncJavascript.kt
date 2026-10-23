package io.github.asagnc.aurora.browser

import android.net.Uri
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import io.github.asagnc.aurora.core.*
import java.net.URI
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonPrimitive

/** A short-lived data-return channel; arbitrary page work is not forcibly cancellable. */
internal object BrowserAsyncJavascript {
    suspend fun evaluate(tab: BrowserTab, script: String, timeoutMs: Long): String {
        val address = requireNotNull(tab.view.url)
        val revision = tab.revision
        val uri = URI(address)
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null)
        val origin = Uri.parse(URI(uri.scheme, null, uri.host, uri.port, null, null, null).toASCIIString())
        val id = UUID.randomUUID().toString()
        val ports = tab.view.createWebMessageChannel()
        var transferred = false
        try {
            return withTimeout(timeoutMs) { awaitBrowserDocumentResult(tab.document, revision) {
                val ready = evaluateSetup(tab, BrowserJavascript.setup(id, script, timeoutMs))
                check(ready == id && tab.view.url == address && tab.revision == revision && !tab.loading) { "Page changed before script dispatch" }
                suspendCancellableCoroutine { continuation ->
                    ports[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                        override fun onMessage(port: WebMessagePort, message: WebMessage) {
                            message.ports?.forEach { runCatching { it.close() } }
                            if (!continuation.isActive) return
                            val data = message.data
                            if (!message.ports.isNullOrEmpty() || data == null || data.length > 40000) {
                                continuation.resumeWithException(IllegalStateException("Invalid or oversized asynchronous script result"))
                            } else if (tab.closed || tab.revision != revision || tab.loading || tab.view.url != address) {
                                continuation.resumeWithException(IllegalStateException("Page changed; script outcome unknown"))
                            } else continuation.resume(data)
                        }
                    })
                    tab.view.postWebMessage(WebMessage(id, arrayOf(ports[1])), origin)
                    transferred = true
                }
            } }
        } finally {
            runCatching { ports[0].close() }
            if (!transferred) runCatching { ports[1].close() }
            if (!tab.closed && tab.revision == revision && tab.view.url == address) {
                runCatching { tab.view.evaluateJavascript(BrowserJavascript.cleanup(id), null) }
            }
        }
    }

    private suspend fun evaluateSetup(tab: BrowserTab, script: String): String = suspendCancellableCoroutine { continuation ->
        tab.view.evaluateJavascript(script) { raw ->
            if (continuation.isActive) {
                val ready = runCatching { WireJson.parseToJsonElement(raw ?: "null").jsonPrimitive.content }
                ready.fold(continuation::resume, continuation::resumeWithException)
            }
        }
    }
}
