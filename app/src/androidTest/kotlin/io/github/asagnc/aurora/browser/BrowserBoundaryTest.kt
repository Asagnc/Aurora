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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserBoundaryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val url = "https://boundary.fixture/"

    @Test fun sameOriginFrameIsReadableAndCrossOriginFrameRetainsBrowserSecurityBoundary() = runBlocking { fixture { _, tools, page, _ ->
        val result = evaluate(tools, page, """new Promise(resolve=>{
            const ready=()=>{
                if(!Array.from(document.querySelectorAll('iframe')).every(f=>f.dataset.ready==='1'))return;
                removeEventListener('ownedFrameReady',ready);
                const same=document.querySelector('#same').contentDocument.body.textContent;
                let cross;try{cross=document.querySelector('#other').contentWindow.document.body.textContent}catch(e){cross=e.name}
                resolve({same,cross});
            };addEventListener('ownedFrameReady',ready);ready();
        })""", true)
        assertNull(result.toString(), result["error"])
        val value = result["result"]!!.jsonObject["value"]!!.jsonObject
        assertTrue(value.text("same").orEmpty().contains("owned same frame"))
        assertEquals("SecurityError", value.text("cross"))
        assertEquals("untrusted_web_content", result.text("trust"))
    } }

    @Test fun rendererTerminationClosesOwnedPageAndEndsPendingResultWithoutReplay() = runBlocking {
        assumeTrue("Explicit browser crash gate required", InstrumentationRegistry.getArguments().getString("auroraBrowserCrashTest") == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await()
        assumeTrue("Close actual Aurora pages before renderer crash test", withContext(Dispatchers.Main) { app.browser.tabs.isEmpty() })
        fixture { browser, tools, page, dispatched -> coroutineScope {
            val pending = async { evaluate(tools, page, "new Promise(()=>{fetch('/ready')})", true) }
            try {
                assertTrue(withContext(Dispatchers.IO) { dispatched.await(5, TimeUnit.SECONDS) })
                withContext(Dispatchers.Main) {
                    val renderer = browser.tabs.single().view.webViewRenderProcess
                    assumeTrue("A separate renderer is required", renderer != null)
                    assertTrue(requireNotNull(renderer).terminate())
                }
                val result = withTimeout(10000) { pending.await() }
                assertNotNull(result.toString(), result["error"])
                assertTrue(withContext(Dispatchers.Main) { browser.tabs.isEmpty() })
            } finally { pending.cancelAndJoin() }
        } }
    }

    private suspend fun evaluate(tools: ToolExecutor, page: JsonObject, script: String, asynchronous: Boolean) = call(tools, "browser_evaluate",
        "page" to page.getValue("page"), "url" to page.getValue("url"), "revision" to page.getValue("revision"),
        "script" to string(script), "awaitPromise" to JsonPrimitive(asynchronous), "timeoutMs" to JsonPrimitive(20000))

    private suspend fun call(tools: ToolExecutor, name: String, vararg args: Pair<String, JsonElement>) =
        WireJson.parseToJsonElement(tools.execute(ToolCall(UUID.randomUUID().toString(), name, objectOf(*args)))).jsonObject

    private suspend fun fixture(block: suspend (BrowserController, ToolExecutor, JsonObject, CountDownLatch) -> Unit) {
        (instrumentation.targetContext.applicationContext as AuroraApplication).ready.await()
        val owner = UUID.randomUUID().toString(); val dispatched = CountDownLatch(1)
        val browser = withContext(Dispatchers.Main) { BrowserController(instrumentation.targetContext, saveProxy = false, intercept = { request ->
            val html = when {
                request.url.host !in setOf("boundary.fixture", "other-boundary.fixture") -> null
                request.url.path == "/ready" -> { dispatched.countDown(); "owned ready" }
                request.url.host == "other-boundary.fixture" -> "<html><body>owned cross frame</body></html>"
                request.url.path == "/same" -> "<html><body>owned same frame</body></html>"
                else -> """<html><body>owned root<iframe id="same" src="/same" onload="this.dataset.ready='1';dispatchEvent(new Event('ownedFrameReady'))"></iframe><iframe id="other" src="https://other-boundary.fixture/frame" onload="this.dataset.ready='1';dispatchEvent(new Event('ownedFrameReady'))"></iframe></body></html>"""
            }
            html?.let { WebResourceResponse("text/html", "UTF-8", it.byteInputStream()) }
        }) }
        val tools = BrowserToolExecutor(browser, owner)
        try {
            val page = call(tools, "browser_open", "url" to string(url)); assertNull(page.toString(), page["error"])
            block(browser, tools, page, dispatched)
        } finally { browser.deleteOwner(owner) }
    }
}
