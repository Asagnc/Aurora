package io.github.asagnc.aurora.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*

class HttpModelTransportTest {
    @Test fun routesChatAndReadsUsage() = runBlocking {
        Fixture { exchange ->
            assertEquals("/v1/chat/completions", exchange.requestURI.path)
            assertEquals("Bearer fixture-secret", exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("test-model", WireJson.parseToJsonElement(exchange.requestBody.reader().readText()).let {
                (it as kotlinx.serialization.json.JsonObject).text("model")
            })
            exchange.sse("""data: {"choices":[{"delta":{"content":"hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"completion_tokens":2,"prompt_tokens_details":{"cached_tokens":80}}}

data: [DONE]

""")
        }.use { fixture ->
            val chunks = mutableListOf<String>()
            val turn = transport().generate(fixture.request()) { chunks += it }
            assertEquals("hello", turn.text)
            assertEquals(listOf("hello"), chunks)
            assertEquals(0.8, turn.usage.cacheShare)
        }
    }

    @Test fun routesAnthropicWithVersionAndApiKey() = runBlocking {
        Fixture { exchange ->
            assertEquals("/v1/messages", exchange.requestURI.path)
            assertEquals("fixture-secret", exchange.requestHeaders.getFirst("x-api-key"))
            assertEquals("2023-06-01", exchange.requestHeaders.getFirst("anthropic-version"))
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            exchange.sse("""data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}

data: {"type":"message_stop"}

""")
        }.use { fixture -> assertEquals("end_turn", transport().generate(fixture.request(Protocol.ANTHROPIC)) {}.stopReason) }
    }

    @Test fun routesResponsesAndAllowsExplicitGatewayAuthentication() = runBlocking {
        Fixture { exchange ->
            assertEquals("/v1/responses", exchange.requestURI.path)
            assertEquals("fixture-secret", exchange.requestHeaders.getFirst("x-api-key"))
            exchange.sse("""data: {"type":"response.completed","response":{"status":"completed","output":[]}}

""")
        }.use { fixture ->
            val request = fixture.request(Protocol.RESPONSES)
            val turn = transport().generate(request.copy(connection = request.connection.copy(authentication = Authentication.API_KEY))) {}
            assertEquals("completed", turn.stopReason)
        }
    }

    @Test fun errorsNeverExposeResponseBodyOrCredentials() = runBlocking {
        Fixture { exchange ->
            val bytes = "fixture-secret".toByteArray()
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }.use { fixture ->
            val failure = assertFailsWith<ModelNetworkException> { transport().generate(fixture.request()) {} }
            assertEquals(401, failure.status)
            assertFalse(failure.toString().contains("fixture-secret"))
            assertNull(failure.cause)
        }
    }

    @Test fun redirectsNeverForwardCredentials() = runBlocking {
        val destinationRequests = AtomicInteger()
        Fixture { exchange -> destinationRequests.incrementAndGet(); exchange.sse("") }.use { destination ->
            Fixture { exchange ->
                exchange.responseHeaders.add("Location", destination.url)
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }.use { origin ->
                assertEquals(302, assertFailsWith<ModelNetworkException> { transport().generate(origin.request()) {} }.status)
                assertEquals(0, destinationRequests.get())
            }
        }
    }

    @Test fun cancellationClosesStalledStreamWithoutWaitingForReadTimeout() = runBlocking {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        Fixture { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write(": started\n\n".toByteArray())
            exchange.responseBody.flush()
            arrived.countDown()
            release.await(10, TimeUnit.SECONDS)
            exchange.close()
        }.use { fixture ->
            try {
                val operation = async(Dispatchers.Default) { transport().generate(fixture.request()) {} }
                assertTrue(withContext(Dispatchers.IO) { arrived.await(5, TimeUnit.SECONDS) })
                withTimeout(3000) { operation.cancelAndJoin() }
                assertTrue(operation.isCancelled)
            } finally { release.countDown() }
        }
    }

    @Test fun productionEndpointsRejectCleartextAndEmbeddedSecrets() {
        val connection = Connection(name = "test", baseUrl = "https://example.com/v1/", protocol = Protocol.CHAT, model = "test")
        assertEquals("https://example.com/v1/chat/completions", HttpModelTransport.endpoint(connection).toString())
        listOf("http://127.0.0.1/v1", "https://user:secret@example.com/v1", "https://example.com/v1?api_key=secret").forEach { url ->
            assertFailsWith<IllegalArgumentException> { HttpModelTransport.endpoint(connection.copy(baseUrl = url)) }
        }
    }

    private fun transport() = HttpModelTransport.loopbackTest { "fixture-secret" }
}

private class Fixture(handler: (HttpExchange) -> Unit) : AutoCloseable {
    private val workers = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = workers
        createContext("/") { exchange ->
            try { handler(exchange) } catch (_: Exception) { exchange.close() }
        }
        start()
    }
    val url get() = "http://127.0.0.1:${server.address.port}/v1"
    fun request(protocol: Protocol = Protocol.CHAT) = ModelRequest(
        Connection(name = "test", baseUrl = url, protocol = protocol, model = "test-model"), "system", emptyList(), emptyList())
    override fun close() { server.stop(0); workers.shutdownNow() }
}

private fun HttpExchange.sse(body: String) {
    val bytes = body.toByteArray()
    responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
    sendResponseHeaders(200, if (bytes.isEmpty()) -1 else bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
