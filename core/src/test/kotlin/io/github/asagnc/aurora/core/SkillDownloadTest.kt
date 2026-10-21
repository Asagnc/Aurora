package io.github.asagnc.aurora.core

import java.nio.file.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import kotlin.test.*

class SkillDownloadTest {
    private fun fixture(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("aurora-skill-download")
        try { block(root) } finally { removeSkillTree(root) }
    }
    private fun client(reply: (Request) -> Response) = SkillNetwork(OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).addInterceptor { reply(it.request()) }.build())
    private fun response(request: Request, code: Int, location: String? = null): Response = Response.Builder()
        .request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("fixture").body("archive".toResponseBody())
        .apply { if (location != null) header("Location", location) }.build()

    @Test fun validatesEachRedirectBeforeSendingNextRequest() = fixture { root ->
        listOf("http://example.com/file.zip", "https://user:secret@example.com/file.zip").forEachIndexed { index, location ->
            var requests = 0
            val network = client { request -> requests++; response(request, 302, location) }
            val target = root.resolve("download-$index")
            assertFailsWith<IllegalArgumentException> { network.download("https://example.org/start", target) }
            assertEquals(1, requests); assertFalse(Files.exists(target))
        }
    }
    @Test fun publicHttpsRedirectDoesNotAddAuthentication() = fixture { root ->
        val hosts = mutableListOf<String>()
        val network = client { request ->
            assertNull(request.header("Authorization")); assertNull(request.header("Cookie"))
            hosts += request.url.host
            if (hosts.size == 1) response(request, 302, "https://cdn.example.com/fixed.zip") else response(request, 200)
        }
        val target = root.resolve("download")
        network.download("https://example.org/start", target)
        assertEquals(listOf("example.org", "cdn.example.com"), hosts)
        assertEquals("archive", Files.readString(target))
    }
    @Test fun redirectLoopStopsBeforeCreatingTarget() = fixture { root ->
        var requests = 0
        val network = client { request -> requests++; response(request, 302, "/again") }
        val target = root.resolve("download")
        assertFails { network.download("https://example.org/start", target) }
        assertEquals(6, requests); assertFalse(Files.exists(target))
    }
    @Test fun oversizedDeclaredBodyIsRejectedWithoutOpeningOutput() = fixture { root ->
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = 64L * 1024 * 1024 + 1
            override fun source() = Buffer()
        }
        val network = client { request -> response(request, 200).newBuilder().body(body).build() }
        val target = root.resolve("download")
        assertFailsWith<IllegalArgumentException> { network.download("https://example.org/archive.zip", target) }
        assertFalse(Files.exists(target))
    }
    @Test fun cancellationReportedAsSocketIoErrorStillCancelsCallerCleanly() = fixture { root -> coroutineScope {
        val entered = CompletableDeferred<Unit>()
        val stream = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                entered.complete(Unit)
                try { java.util.concurrent.CountDownLatch(1).await() }
                catch (interrupted: InterruptedException) { throw java.io.IOException("socket closed", interrupted) }
                return -1
            }
            override fun timeout() = Timeout.NONE
            override fun close() { }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = -1L
            override fun source() = stream
        }
        val network = client { request -> response(request, 200).newBuilder().body(body).build() }
        val task = launch { network.download("https://example.org/archive.zip", root.resolve("download")) }
        withTimeout(5000) { entered.await(); task.cancelAndJoin() }
        assertTrue(task.isCancelled)
        Files.delete(root.resolve("download"))
    } }
}
