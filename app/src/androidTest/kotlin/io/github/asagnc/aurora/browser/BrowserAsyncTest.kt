package io.github.asagnc.aurora.browser

import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real WebView tests, compiled on host and executed at final Android acceptance. */
@RunWith(AndroidJUnit4::class)
class BrowserAsyncTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val url = "https://async.fixture/"

    @Test fun promisesPlainValuesAndErrorsUseTheObservedPage() = runBlocking { fixture { _, tools, page ->
        val success = evaluate(tools, page, "Promise.resolve({answer:42})")
        assertEquals(42L, success["result"]!!.jsonObject["value"]!!.jsonObject.number("answer"))
        assertEquals("untrusted_web_content", success.text("trust"))
        assertEquals(42L, evaluate(tools, page, "42")["result"]!!.jsonObject.number("value"))
        assertNotNull(evaluate(tools, page, "Promise.reject(Error('fixture rejection'))").text("error"))
        assertNotNull(evaluate(tools, page, "Promise.resolve(1)", await = false).text("error"))
        assertNotNull(evaluate(tools, page, "Promise.resolve().then(()=>{const x={};x.self=x;return x})").text("error"))
    } }

    @Test fun escapedLargeValuesStayWithinEnvelopeAndRemovePageReceiver() = runBlocking { fixture { _, tools, page ->
        val result = evaluate(tools, page, "Promise.resolve('\\\"'.repeat(100000))")
        assertNull(result.toString(), result["error"])
        assertTrue(result.getValue("result").jsonObject["truncated"]!!.jsonPrimitive.boolean)
        assertTrue(result.toString().length < 40000)
        assertClean(tools, page)
    } }

    @Test fun localTimeoutAndOuterDeadlineReleaseTheChannelWithoutSwallowingCancellation() = runBlocking { fixture { _, tools, page ->
        val timedOut = evaluate(tools, page, "new Promise(()=>{})", timeout = 100)
        assertNotNull(timedOut.text("error"))
        assertNotNull(timedOut.text("outcome"))
        assertClean(tools, page)
        try {
            withTimeout(100) { evaluate(tools, page, "new Promise(()=>{})", timeout = 10000) }
            fail("Outer deadline was converted into a normal tool result")
        } catch (_: TimeoutCancellationException) { }
        assertClean(tools, page)
        assertEquals(42L, evaluate(tools, page, "42", await = false)["result"]!!.jsonObject.number("value"))
    } }

    @Test fun navigationAndSameUrlReloadRejectOldDocumentResults() = runBlocking { fixture { _, tools, first ->
        var page = first
        for (navigation in listOf("location.href='/next'", "location.reload()")) {
            val changed = withTimeout(5000) { evaluate(tools, page,
                "new Promise(()=>{setTimeout(()=>{$navigation},20)})", timeout = 10000) }
            assertTrue(changed.toString(), changed.text("error").orEmpty().contains("changed", ignoreCase = true))
            val refreshed = call(tools, "browser_read", "page" to page.getValue("page"))
            assertNull(refreshed.toString(), refreshed["error"])
            assertTrue(requireNotNull(refreshed.number("revision")) > requireNotNull(page.number("revision")))
            page = refreshed
        }
    } }

    @Test fun closingPageEndsPendingWaitImmediately() = runBlocking {
        val dispatched = CountDownLatch(1)
        fixture(dispatched) { browser, tools, page -> coroutineScope {
            val pending = async { evaluate(tools, page,
                "new Promise(()=>{fetch('/ready')})", timeout = 10000) }
            try {
                assertTrue(withContext(Dispatchers.IO) { dispatched.await(5, TimeUnit.SECONDS) })
                withContext(Dispatchers.Main) { browser.close(browser.tabs.single()) }
                val result = withTimeout(2000) { pending.await() }
                assertNotNull(result.text("error"))
                assertTrue(withContext(Dispatchers.Main) { browser.tabs.isEmpty() })
            } finally { pending.cancelAndJoin() }
        } }
    }

    @Test fun batchOuterDeadlineIsNotRecordedAsSuccessfulPerPageResults() = runBlocking {
        val dispatched = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture(dispatched, release) { _, tools, _ ->
            try {
                withTimeout(100) { call(tools, "browser_open_batch", "urls" to JsonArray(listOf(string(url + "slow")))) }
                fail("Batch swallowed the parent deadline")
            } catch (_: TimeoutCancellationException) { }
            finally { release.countDown() }
            val pages = call(tools, "browser_list").getValue("pages").jsonArray
            assertTrue(pages.all { !it.jsonObject["loading"]!!.jsonPrimitive.boolean })
        }
    }

    private suspend fun assertClean(tools: ToolExecutor, page: JsonObject) {
        val clean = evaluate(tools, page, "Object.getOwnPropertyNames(window).filter(x=>x.startsWith('__aurora_async_')).length", await = false)
        assertEquals(clean.toString(), 0L, clean["result"]!!.jsonObject.number("value"))
    }

    private suspend fun evaluate(tools: ToolExecutor, page: JsonObject, script: String, await: Boolean = true, timeout: Long = 2000) =
        call(tools, "browser_evaluate", "page" to page.getValue("page"), "url" to page.getValue("url"),
            "revision" to page.getValue("revision"), "script" to string(script), "awaitPromise" to JsonPrimitive(await),
            "timeoutMs" to JsonPrimitive(timeout))

    private suspend fun call(tools: ToolExecutor, name: String, vararg arguments: Pair<String, JsonElement>) =
        WireJson.parseToJsonElement(tools.execute(ToolCall(UUID.randomUUID().toString(), name, objectOf(*arguments)))).jsonObject

    private suspend fun fixture(dispatched: CountDownLatch? = null, release: CountDownLatch? = null,
        block: suspend (BrowserController, ToolExecutor, JsonObject) -> Unit) {
        (instrumentation.targetContext.applicationContext as AuroraApplication).ready.await()
        val owner = UUID.randomUUID().toString()
        val browser = withContext(Dispatchers.Main) { BrowserController(instrumentation.targetContext, saveProxy = false, intercept = { request ->
            if (request.url.path == "/ready") dispatched?.countDown()
            if (request.url.path == "/slow") { dispatched?.countDown(); release?.await(5, TimeUnit.SECONDS) }
            if (request.url.host == "async.fixture") WebResourceResponse("text/html", "UTF-8", "<html><body>Async fixture</body></html>".byteInputStream()) else null
        }) }
        val tools = BrowserToolExecutor(browser, owner)
        try {
            val page = call(tools, "browser_open", "url" to string(url))
            assertNull(page.toString(), page["error"])
            block(browser, tools, page)
        } finally { release?.countDown(); browser.deleteOwner(owner) }
    }
}
