package io.github.asagnc.aurora.browser

import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import java.net.URI

/** Uses the same conversation-owned pages that the user can inspect in BrowserPage. */
internal class BrowserAutomation(private val browser: BrowserController, private val owner: String) {
    private val operation = Mutex()

    suspend fun execute(action: String, args: JsonObject): JsonObject = operation.withLock {
        withContext(Dispatchers.Main) {
            browser.beginAgent(owner)
            try {
                when (action) {
                    "browser_list" -> objectOf("pages" to JsonArray(browser.tabs.filter { it.owner == owner }.map(::describe)))
                    "browser_open" -> open(args)
                    "browser_open_batch" -> batch(args.array("urls")) { open(objectOf("url" to it)) }
                    "browser_read_batch" -> batch(args.array("pages")) { read(page(objectOf("page" to it))) }
                    "browser_close" -> { browser.close(page(args)); objectOf("closed" to JsonPrimitive(true)) }
                    "browser_read" -> read(page(args))
                    "browser_action" -> act(page(args), args)
                    "browser_evaluate" -> evaluate(page(args), args)
                    else -> error("Unknown browser operation")
                }
            } finally { browser.endAgent(owner) }
        }
    }

    private fun page(args: JsonObject): BrowserTab = requireNotNull(browser.tabs.firstOrNull {
        it.owner == owner && it.id == args.text("page")
    }) { "Page does not belong to this conversation or was closed" }

    private suspend fun open(args: JsonObject): JsonObject {
        val url = requireNotNull(args.text("url"))
        val uri = runCatching { URI(if ("://" in url) url else "https://$url") }.getOrNull()
        require(url.length <= 8192 && uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null) { "Supply an HTTPS URL without credentials" }
        val tab = args.text("page")?.let { page(args) } ?: requireNotNull(browser.open(owner)) { browser.error ?: "Page unavailable" }
        try {
            require(browser.navigate(tab, url)) { browser.error ?: "Navigation rejected" }
            browser.resume(tab)
            awaitLoad(tab)
            return read(tab)
        } catch (cancel: CancellationException) {
            tab.view.stopLoading(); tab.loading = false; tab.failure = "Navigation cancelled or timed out"; tab.changed(); throw cancel
        }
    }

    private suspend fun batch(values: List<JsonElement>, action: suspend (JsonElement) -> JsonObject): JsonObject {
        require(values.size in 1..8) { "Batch requires 1 to 8 items" }
        // The page budget already caps renderer count. Concurrency is limited to two active loads.
        val limiter = kotlinx.coroutines.sync.Semaphore(2)
        val results = coroutineScope { values.map { value -> async {
            limiter.acquire()
            try { action(value) }
            catch (timeout: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); objectOf("error" to string("Timed out; inspect page before retrying")) }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { objectOf("error" to string(failure.message?.take(500) ?: "Browser operation failed")) }
            finally { limiter.release() }
        } }.awaitAll() }
        return objectOf("results" to JsonArray(results))
    }

    private suspend fun awaitLoad(tab: BrowserTab) {
        check(tab in browser.tabs) { "Page was closed" }
        awaitBrowserLoad(tab.document)
        check(tab in browser.tabs) { "Page was closed" }
    }

    private suspend fun read(tab: BrowserTab): JsonObject {
        awaitLoad(tab)
        val document = javascript(tab, BrowserScripts.snapshot)
        return JsonObject(describe(tab) + mapOf("document" to document, "trust" to string("untrusted_web_content")))
    }

    private suspend fun act(tab: BrowserTab, args: JsonObject): JsonObject {
        val expected = requireNotNull(args.text("url")) { "Supply the exact URL from the latest observation" }
        require(expected == tab.view.url && args.number("revision") == tab.revision && !tab.loading) { "Page changed; read it again before acting" }
        val selector = requireNotNull(args.text("selector")).also { require(it.length in 1..2048) }
        val action = requireNotNull(args.text("action"))
        require(action in setOf("click", "fill", "scroll")) { "Unsupported browser action" }
        val value = args.text("text").orEmpty().also { require(it.length <= 16_384) }
        val result = javascript(tab, BrowserScripts.action(selector, action, value))
        // A click can begin navigation after evaluateJavascript returns. Observe after the event loop.
        delay(100)
        awaitLoad(tab)
        return objectOf("action" to result, "observation" to read(tab))
    }

    private suspend fun evaluate(tab: BrowserTab, args: JsonObject): JsonObject {
        require(args.text("url") == tab.view.url && args.number("revision") == tab.revision && !tab.loading) { "Page changed; read it again before evaluating" }
        val script = requireNotNull(args.text("script")).also { require(it.length in 1..16_384) }
        require(args.keys.all { it in setOf("page", "url", "revision", "script", "awaitPromise", "timeoutMs") })
        val asynchronous = args["awaitPromise"]?.let {
            require(it is JsonPrimitive && !it.isString); requireNotNull(it.booleanOrNull)
        } ?: false
        val timeout = args["timeoutMs"]?.let {
            require(it is JsonPrimitive && !it.isString); requireNotNull(it.longOrNull)
        } ?: 10000L
        require(timeout in 1..30000)
        val result = if (asynchronous) {
            browser.resume(tab)
            val encoded = BrowserAsyncJavascript.evaluate(tab, script, timeout)
            withContext(Dispatchers.Default) { decodeBrowserScriptResult(encoded) }
        } else javascript(tab, script, timeout)
        check(tab in browser.tabs && !tab.loading && tab.revision == args.number("revision") && tab.view.url == args.text("url")) {
            "Page changed; script outcome unknown"
        }
        return objectOf("page" to string(tab.id), "result" to result, "trust" to string("untrusted_web_content"))
    }

    private suspend fun javascript(tab: BrowserTab, script: String, timeoutMs: Long = 10000): JsonElement {
        browser.resume(tab)
        val wrapped = BrowserScripts.wrap(script)
        val revision = tab.revision
        val address = tab.view.url
        val raw = withTimeout(timeoutMs) { awaitBrowserDocumentResult(tab.document, revision) {
            suspendCancellableCoroutine<String> { continuation ->
                tab.view.evaluateJavascript(wrapped) { result -> if (continuation.isActive) continuation.resume(result ?: "null") }
            }
        } }
        check(tab in browser.tabs && tab.view.url == address) { "Page changed; script outcome unknown" }
        val decoded = withContext(Dispatchers.Default) { decodeBrowserJavascriptCallback(raw) }
        check(tab in browser.tabs && !tab.loading && tab.revision == revision && tab.view.url == address) {
            "Page changed; script outcome unknown"
        }
        return decoded
    }

    private fun describe(tab: BrowserTab) = objectOf("page" to string(tab.id), "url" to string(tab.view.url ?: tab.address),
        "title" to string(tab.title), "loading" to JsonPrimitive(tab.loading), "revision" to JsonPrimitive(tab.revision))
}
