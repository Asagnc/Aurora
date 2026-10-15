package io.github.asagnc.aurora.browser

import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.AuroraApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserAgentTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val url = "https://agent.fixture/"
    private val html = """<html><head><title>Agent fixture</title></head><body><h1>Local fixture</h1>
        <input id="name"><button id="apply" onclick="document.querySelector('#result').textContent=document.querySelector('#name').value">Apply</button>
        <p id="result">Waiting</p></body></html>"""

    private fun browser(gate: CountDownLatch? = null, requested: CountDownLatch? = null): BrowserController {
        runBlocking { (instrumentation.targetContext.applicationContext as AuroraApplication).ready.await() }
        lateinit var result: BrowserController
        instrumentation.runOnMainSync {
            result = BrowserController(instrumentation.targetContext, saveProxy = false, intercept = { request ->
                if (request.url.path == "/slow") { requested?.countDown(); gate?.await(10, TimeUnit.SECONDS) }
                if (request.url.host == "agent.fixture") WebResourceResponse("text/html", "UTF-8", html.byteInputStream()) else null
            })
        }
        return result
    }
    private suspend fun call(executor: ToolExecutor, name: String, vararg fields: Pair<String, JsonElement>): JsonObject =
        WireJson.parseToJsonElement(executor.execute(ToolCall(UUID.randomUUID().toString(), name, objectOf(*fields)))).jsonObject

    @Test fun agentLoopOpensFillsClicksAndReadsActualDom() = runBlocking {
        val browser = browser()
        val owner = UUID.randomUUID().toString()
        val tools = BrowserToolExecutor(browser, owner)
        var step = 0
        var page = ""
        var revision = JsonPrimitive(0)
        val transport = object : ModelTransport {
            override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
                val result = request.history.lastOrNull()?.text("content")?.let { runCatching { WireJson.parseToJsonElement(it).jsonObject }.getOrNull() }
                val call = when (step++) {
                    0 -> ToolCall("open", "browser_open", objectOf("url" to string(url)))
                    1 -> { page = requireNotNull(result?.text("page")); revision = result.getValue("revision").jsonPrimitive; ToolCall("fill", "browser_action", objectOf("page" to string(page),
                        "url" to string(url), "revision" to revision, "selector" to string("#name"), "action" to string("fill"), "text" to string("Aurora fixture"))) }
                    2 -> ToolCall("click", "browser_action", objectOf("page" to string(page), "url" to string(url), "revision" to revision,
                        "selector" to string("#apply"), "action" to string("click")))
                    else -> {
                        assertTrue(request.history.last().toString().contains("Aurora fixture"))
                        return ModelTurn("verified", emptyList(), emptyList(), "stop", Usage())
                    }
                }
                return ModelTurn("", emptyList(), listOf(call), "tool_calls", Usage())
            }
        }
        try {
            val result = AgentLoop(transport, tools).run(ModelRequest(Connection(name = "fixture", baseUrl = url, model = "fixture", protocol = Protocol.CHAT),
                "fixture", emptyList(), tools.definitions), {}, {})
            assertEquals("verified", result.text)
            assertEquals(4, step)
        } finally { browser.deleteOwner(owner) }
    }

    @Test fun ownershipStaleActionsScriptErrorsAndOutputBounds() = runBlocking {
        val browser = browser()
        val owner = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val tools = BrowserToolExecutor(browser, owner)
        try {
            val opened = call(tools, "browser_open", "url" to string(url))
            val page = requireNotNull(opened.text("page"))
            val revision = opened.getValue("revision")
            assertEquals("untrusted_web_content", opened.text("trust"))
            val rejected = call(BrowserToolExecutor(browser, other), "browser_read", "page" to string(page))
            assertNotNull(rejected.text("error"))
            val stale = call(tools, "browser_action", "page" to string(page), "url" to string("https://stale.fixture/"),
                "selector" to string("#apply"), "action" to string("click"))
            assertTrue(stale.text("error").orEmpty().contains("changed"))
            val scriptError = call(tools, "browser_evaluate", "page" to string(page), "url" to string(url), "revision" to revision, "script" to string("throw Error('fixture error')"))
            assertNotNull(scriptError.text("error"))
            val large = call(tools, "browser_evaluate", "page" to string(page), "url" to string(url), "revision" to revision, "script" to string("'x'.repeat(100000)"))
            assertTrue(large.getValue("result").jsonObject.getValue("truncated").jsonPrimitive.boolean)
            assertTrue(large.toString().length < 40_000)
            call(tools, "browser_close", "page" to string(page))
            assertNotNull(call(tools, "browser_read", "page" to string(page)).text("error"))
        } finally { browser.deleteOwner(owner); browser.deleteOwner(other) }
    }

    @Test fun batchOperationsKeepOrderAndFailuresDoNotDiscardSuccessfulPages() = runBlocking {
        val browser = browser()
        val owner = UUID.randomUUID().toString()
        val tools = BrowserToolExecutor(browser, owner)
        try {
            val batch = call(tools, "browser_open_batch", "urls" to JsonArray(listOf(
                string(url + "first"), string("file:///rejected"), string(url + "second"))))
            val results = batch.getValue("results").jsonArray
            assertEquals(url + "first", results[0].jsonObject.text("url"))
            assertNotNull(results[1].jsonObject.text("error"))
            assertEquals(url + "second", results[2].jsonObject.text("url"))
            val ids = listOf(results[2].jsonObject.getValue("page"), results[0].jsonObject.getValue("page"))
            val read = call(tools, "browser_read_batch", "pages" to JsonArray(ids)).getValue("results").jsonArray
            assertEquals(ids, read.map { it.jsonObject.getValue("page") })
            assertEquals(2, call(tools, "browser_list").getValue("pages").jsonArray.size)
        } finally { browser.deleteOwner(owner) }
    }

    @Test fun cancellingNavigationReleasesAgentLeaseAndKeepsOtherToolsUsable() = runBlocking {
        val gate = CountDownLatch(1)
        val requested = CountDownLatch(1)
        val browser = browser(gate, requested)
        val owner = UUID.randomUUID().toString()
        val tools = BrowserToolExecutor(browser, owner)
        try {
            val running = launch { call(tools, "browser_open", "url" to string(url + "slow")) }
            assertTrue(withContext(Dispatchers.IO) { requested.await(5, TimeUnit.SECONDS) })
            running.cancelAndJoin()
            assertTrue(running.isCancelled)
            val pages = call(tools, "browser_list").getValue("pages").jsonArray
            assertFalse(pages.single().jsonObject.getValue("loading").jsonPrimitive.boolean)
        } finally { gate.countDown(); browser.deleteOwner(owner) }
    }
}
