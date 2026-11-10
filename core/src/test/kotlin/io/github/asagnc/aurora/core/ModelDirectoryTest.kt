package io.github.asagnc.aurora.core

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient

class ModelDirectoryTest {
    @Test fun catalogueGetUsesAuthWithoutSendingInferenceAndPreservesExactIds() = runBlocking {
        server { exchange ->
            assertEquals("GET", exchange.requestMethod)
            assertEquals("/custom/v1/models", exchange.requestURI.path)
            assertEquals("Bearer fixture-token", exchange.requestHeaders.getFirst("Authorization"))
            """{"data":[{"id":"vendor/model-b"},{"id":"Model-A","display_name":"Friendly"},{"id":"Model-A"}]}"""
        }.use { fixture ->
            assertEquals(listOf(AvailableModel("Model-A", "Friendly"), AvailableModel("vendor/model-b", "vendor/model-b")),
                fixture.fetch())
        }
    }
    @Test fun anthropicPaginationAndHeadersAreFollowedWithoutDroppingPages() = runBlocking {
        val pages = AtomicInteger()
        server { exchange ->
            assertEquals("fixture-token", exchange.requestHeaders.getFirst("x-api-key"))
            assertEquals("2023-06-01", exchange.requestHeaders.getFirst("anthropic-version"))
            if (pages.incrementAndGet() == 1) """{"data":[{"id":"first"}],"has_more":true,"last_id":"first"}"""
            else {
                assertEquals("after_id=first", exchange.requestURI.query)
                """{"data":[{"id":"second"}],"has_more":false}"""
            }
        }.use { fixture -> assertEquals(listOf("first", "second"), fixture.fetch(Protocol.ANTHROPIC).map { it.id }) }
    }
    @Test fun repeatingPaginationFailsInsteadOfPublishingPartialModels() = runBlocking {
        server { """{"data":[{"id":"same"}],"has_more":true,"last_id":"same"}""" }.use { fixture ->
            assertFailsWith<ModelDirectoryFailure> { fixture.fetch() }
        }
        Unit
    }
    @Test fun malformedResponseAndMissingIdsAreRejected() = runBlocking {
        for (body in listOf("<html>error</html>", "{}", """{"data":[{"name":"missing id"}]}""")) {
            server { body }.use { fixture -> assertFailsWith<ModelDirectoryFailure> { fixture.fetch() } }
        }
    }
    @Test fun redirectIsNotFollowedAndErrorDoesNotExposeCredentials() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val leaks = AtomicInteger()
        server.createContext("/custom/v1/models") { e -> e.responseHeaders.add("Location", "/leak"); e.sendResponseHeaders(302, -1); e.close() }
        server.createContext("/leak") { e -> leaks.incrementAndGet(); e.sendResponseHeaders(200, -1); e.close() }
        server.start()
        try {
            val failure = assertFailsWith<ModelDirectoryFailure> { Fixture(server).fetch() }
            assertFalse(failure.message!!.contains("fixture-token")); assertEquals(0, leaks.get())
        } finally { server.stop(0) }
    }
    @Test fun cancellationClosesPendingCatalogueRequest() = runBlocking {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        server { started.countDown(); release.await(2, java.util.concurrent.TimeUnit.SECONDS); "{\"data\":[]}" }.use { fixture ->
            val request = async { fixture.fetch() }
            try {
                assertTrue(withContext(Dispatchers.IO) { started.await(2, java.util.concurrent.TimeUnit.SECONDS) })
                request.cancel()
                withTimeout(1000) { request.join() }
                assertTrue(request.isCancelled)
            } finally { release.countDown(); request.cancelAndJoin() }
        }
    }
    @Test fun selectedModelsSurviveConnectionSerializationAndSwitchKeepsTheSameProvider() {
        val models = listOf(AvailableModel("vendor/one", "First"), AvailableModel("vendor/two", "Second"))
        val connection = Connection(name = "Gateway", baseUrl = "https://example.com/v1", protocol = Protocol.CHAT,
            model = models.first().id, models = models)
        val decoded = WireJson.decodeFromString(Connection.serializer(), WireJson.encodeToString(Connection.serializer(), connection))
        assertEquals(connection, decoded)
        val switched = decoded.copy(model = models.last().id)
        assertEquals(connection.id, switched.id)
        assertEquals(models, switched.models)
        assertEquals(CacheImpact.NEW_SCOPE, CachePolicy.impact(connection, switched))
    }
    private fun server(body: (com.sun.net.httpserver.HttpExchange) -> String): Fixture {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            try { val bytes = body(e).toByteArray(); e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) } }
            finally { e.close() }
        }
        server.start(); return Fixture(server)
    }
    private class Fixture(val server: HttpServer) : AutoCloseable {
        suspend fun fetch(protocol: Protocol = Protocol.CHAT): List<AvailableModel> =
            ModelDirectory(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build(), true).fetch(
                Connection(name = "fixture", baseUrl = "http://127.0.0.1:${server.address.port}/custom/v1", protocol = protocol, model = ""), "fixture-token")
        override fun close() { server.stop(0) }
    }
}
