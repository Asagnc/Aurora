package io.github.asagnc.aurora.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

class HttpReplayTest {
    private class Origin(handler: (HttpExchange) -> Unit) : AutoCloseable {
        val calls = AtomicInteger()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newCachedThreadPool { task -> Thread(task, "owned-http-fixture").apply { isDaemon = true } }
        val url get() = "http://127.0.0.1:${server.address.port}/owned"
        val port get() = server.address.port
        init {
            server.executor = executor
            server.createContext("/") { exchange ->
                calls.incrementAndGet()
                try { handler(exchange) } finally { exchange.close() }
            }
            server.start()
        }
        override fun close() { server.stop(0); executor.shutdownNow(); executor.awaitTermination(3, TimeUnit.SECONDS) }
    }

    private fun arguments(url: String, vararg extra: Pair<String, JsonElement>) = JsonObject(linkedMapOf(
        "url" to string(url), "method" to string("GET"), "route" to string("direct")) + extra)
    private suspend fun invoke(tool: HttpReplayTool, arguments: JsonObject) =
        WireJson.parseToJsonElement(tool.execute(ToolCall("owned", "http_request", arguments))).jsonObject
    private fun HttpExchange.reply(code: Int, body: ByteArray) {
        sendResponseHeaders(code, body.size.toLong()); responseBody.write(body)
    }
    private suspend fun <T> directory(block: suspend (Path) -> T): T {
        val root = Files.createTempDirectory("aurora-owned-http")
        try { return block(root) } finally { root.toFile().deleteRecursively() }
    }

    @Test fun binaryPostIsSentOnceAndRedirectResponseIsPublished() = runBlocking { directory { root ->
        val posted = AtomicReference<ByteArray>(); val authorization = AtomicReference<String>()
        val payload = byteArrayOf(0, 1, -1, 13, 10)
        Origin { exchange ->
            posted.set(exchange.requestBody.readBytes()); authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.responseHeaders.add("Location", "/must-not-follow")
            exchange.reply(302, payload)
        }.use { origin ->
            var registered = false
            val tool = HttpReplayTool(root) { _, path -> assertNotNull(commandArtifactId(path)); registered = true }
            val result = invoke(tool, arguments(origin.url, "method" to string("POST"),
                "headers" to JsonArray(listOf(objectOf("name" to string("Authorization"), "value" to string("Bearer owned-fixture")))),
                "bodyBase64" to string(Base64.getEncoder().encodeToString(payload))))
            assertTrue(registered); assertEquals(302L, result.number("status")); assertEquals(1, origin.calls.get())
            assertContentEquals(payload, posted.get()); assertEquals("Bearer owned-fixture", authorization.get())
            assertContentEquals(payload, Files.readAllBytes(root.resolve(result.text("bodyPath"))))
            assertEquals(httpBodyDigest(payload), result.text("requestBodySha256")); assertTrue(result["bodyComplete"]!!.jsonPrimitive.boolean)
        }
    } }

    @Test fun retryAfterZeroNeverReplaysGetAndOriginalHeaderSurvives() = runBlocking { directory { root ->
        Origin { exchange -> exchange.responseHeaders.add("Retry-After", "0"); exchange.reply(503, "owned-busy".toByteArray()) }.use { origin ->
            val result = invoke(HttpReplayTool(root), arguments(origin.url))
            assertEquals(503L, result.number("status")); assertEquals(1, origin.calls.get())
            assertTrue(result.array("headers").any { it.jsonObject.text("name")?.equals("Retry-After", true) == true && it.jsonObject.text("value") == "0" })
            assertEquals("owned-busy", result.text("preview"))
        }
    } }

    @Test fun fileBodyRequiresExactHashAndInvalidArgumentsNeverRegisterOrSend() = runBlocking { directory { root ->
        Files.write(root.resolve("request.bin"), byteArrayOf(0, -1, 9))
        Origin { exchange -> exchange.reply(200, exchange.requestBody.readBytes()) }.use { origin ->
            val registrations = AtomicInteger(); val tool = HttpReplayTool(root) { _, _ -> registrations.incrementAndGet() }
            val valid = arguments(origin.url, "method" to string("PUT"), "bodyPath" to string("request.bin"),
                "bodySha256" to string(httpBodyDigest(byteArrayOf(0, -1, 9))))
            val invalid = listOf(valid + ("bodySha256" to string("0".repeat(64))), valid - "bodySha256",
                valid + ("bodyPath" to string("../outside")), valid + ("bodyBase64" to string("AA==")),
                arguments(origin.url, "timeoutMs" to string("10")), arguments(origin.url, "route" to string("automatic")),
                arguments(origin.url, "unknown" to JsonPrimitive(true)), arguments(origin.url, "bodyBase64" to string("AA==")),
                arguments(origin.url, "headers" to JsonArray(listOf(objectOf("name" to string("Content-Length"), "value" to string("0"))))))
            for (item in invalid) assertFailsWith<IllegalArgumentException> { invoke(tool, JsonObject(item)) }
            assertEquals(0, registrations.get()); assertEquals(0, origin.calls.get())
            val result = invoke(tool, valid)
            assertContentEquals(byteArrayOf(0, -1, 9), Files.readAllBytes(root.resolve(result.text("bodyPath"))))
        }
    } }

    @Test fun compressedResponseBudgetAppliesToDecodedBytesWithoutPartialPublication() = runBlocking { directory { root ->
        val packed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(ByteArray(100000)) } }.toByteArray()
        Origin { exchange -> exchange.responseHeaders.add("Content-Encoding", "gzip"); exchange.reply(200, packed) }.use { origin ->
            val tool = HttpReplayTool(root)
            val result = invoke(tool, arguments(origin.url, "maxResponseBytes" to JsonPrimitive(1024)))
            assertEquals("http_outcome_uncertain", result.text("error")); assertEquals("response_byte_budget", result.text("reason"))
            assertTrue(summarizeToolResult(result.toString())!!.uncertain)
            assertEquals(1, origin.calls.get()); assertFalse(Files.exists(root.resolve("runs")))
        }
    } }

    @Test fun publicationFailureRetainsKnownResponseWithoutRepeatingRequest() = runBlocking { directory { root ->
        Files.write(root.resolve("runs"), "owned-conflict".toByteArray())
        Origin { exchange -> exchange.reply(200, "owned-completed".toByteArray()) }.use { origin ->
            val result = invoke(HttpReplayTool(root), arguments(origin.url))
            assertEquals("http_publication_failed", result.text("error")); assertEquals(200L, result.number("status"))
            assertEquals("owned-completed", result.text("preview")); assertEquals(JsonNull, result["bodyPath"])
            assertEquals(1, origin.calls.get()); assertEquals("owned-conflict", Files.readAllBytes(root.resolve("runs")).toString(Charsets.UTF_8))
        }
    } }

    @Test fun cancellationClosesActualSlowResponseAndNeverPublishes() = runBlocking { directory { root ->
        val started = CountDownLatch(1); val disconnected = CountDownLatch(1)
        Origin { exchange ->
            exchange.sendResponseHeaders(200, 0)
            try {
                exchange.responseBody.write(byteArrayOf(1)); exchange.responseBody.flush(); started.countDown()
                repeat(1000) { Thread.sleep(20); exchange.responseBody.write(ByteArray(1024)); exchange.responseBody.flush() }
            } catch (_: IOException) { disconnected.countDown() }
            catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }.use { origin ->
            val job = async { invoke(HttpReplayTool(root), arguments(origin.url)) }
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertTrue(withContext(Dispatchers.IO) { disconnected.await(3, TimeUnit.SECONDS) })
            assertFalse(Files.exists(root.resolve("runs"))); assertEquals(1, origin.calls.get())
        }
    } }

    @Test fun deadlineStopsSlowResponseWithoutReplay() = runBlocking { directory { root ->
        Origin { exchange ->
            exchange.sendResponseHeaders(200, 0); exchange.responseBody.write(byteArrayOf(1)); exchange.responseBody.flush()
            try { Thread.sleep(5000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }.use { origin ->
            val result = withTimeout(4000) {
                invoke(HttpReplayTool(root), arguments(origin.url, "timeoutMs" to JsonPrimitive(300)))
            }
            assertEquals("http_outcome_uncertain", result.text("error")); assertEquals("timeout", result.text("reason"))
            assertEquals(1, origin.calls.get()); assertFalse(Files.exists(root.resolve("runs")))
        }
    } }

    @Test fun explicitHttpProxyReceivesAbsoluteTargetWithoutDirectFallback() = runBlocking { directory { root ->
        val target = AtomicReference<String>()
        Origin { exchange -> target.set(exchange.requestURI.toString()); exchange.reply(200, "owned-proxy".toByteArray()) }.use { proxy ->
            val result = invoke(HttpReplayTool(root), arguments("http://owned-never-resolve.invalid/replay", "route" to string("http"),
                "proxyHost" to string("127.0.0.1"), "proxyPort" to JsonPrimitive(proxy.port)))
            assertEquals("owned-proxy", result.text("preview")); assertEquals(1, proxy.calls.get())
            assertEquals("http://owned-never-resolve.invalid/replay", target.get())
        }
    } }

    @Test fun cookiesAreNotInheritedAndAuthenticationChallengeIsNotRetried() = runBlocking { directory { root ->
        val cookies = mutableListOf<String?>()
        Origin { exchange ->
            synchronized(cookies) { cookies.add(exchange.requestHeaders.getFirst("Cookie")) }
            exchange.responseHeaders.add("Set-Cookie", "owned=must-not-propagate; Path=/")
            exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=owned")
            exchange.reply(401, "owned-auth".toByteArray())
        }.use { origin ->
            val tool = HttpReplayTool(root)
            repeat(2) { assertEquals(401L, invoke(tool, arguments(origin.url)).number("status")) }
            assertEquals(2, origin.calls.get()); assertEquals(listOf<String?>(null, null), cookies)
        }
    } }
}
