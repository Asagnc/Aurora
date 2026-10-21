package io.github.asagnc.aurora.core

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import kotlin.test.*

class ImageDownloadTest {
    @Test fun imageRequestDoesNotInheritModelCredentials() = runTest {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            assertNull(chain.request().header("Authorization"))
            response(chain.request(), byteArrayOf(1, 2).toResponseBody())
        }.build()
        assertContentEquals(byteArrayOf(1, 2), ImageDownload(client, 8).read("https://example.com/image.png?signature=private"))
        assertEquals(1, calls.get())
    }
    @Test fun invalidSchemesAndEmbeddedCredentialsNeverReachNetwork() = runTest {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { calls.incrementAndGet(); error("Unexpected network") }.build()
        for (url in listOf("http://example.com/image", "https://user:secret@example.com/image", "file:///image"))
            assertFailsWith<IllegalArgumentException> { ImageDownload(client).read(url) }
        assertEquals(0, calls.get())
    }
    @Test fun unknownLengthResponseCannotExceedBudget() = runTest {
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = -1L
            override fun source() = Buffer().write(ByteArray(20))
        }
        val client = OkHttpClient.Builder().addInterceptor { response(it.request(), body) }.build()
        assertFailsWith<IllegalArgumentException> { ImageDownload(client, 8).read("https://example.com/image") }
    }
    @Test fun httpFailureDoesNotExposeSignedUrl() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { response(it.request(), ByteArray(0).toResponseBody(), 403) }.build()
        val failure = assertFailsWith<IllegalArgumentException> { ImageDownload(client).read("https://example.com/image?signature=private") }
        assertFalse(failure.message.orEmpty().contains("private"))
    }

    private fun response(request: Request, body: ResponseBody, code: Int = 200) = Response.Builder()
        .request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("fixture").body(body).build()
}
