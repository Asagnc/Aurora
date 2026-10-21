package io.github.asagnc.aurora.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

class ImageDownload(private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
    .retryOnConnectionFailure(false).callTimeout(45, TimeUnit.SECONDS).build(), private val limit: Int = 32 * 1024 * 1024) {
    init { require(limit in 1..32 * 1024 * 1024) }

    suspend fun read(source: String): ByteArray {
        val url = source.toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "Image URL must use HTTPS without embedded credentials" }
        val call = client.newCall(Request.Builder().url(url).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("Image download failed"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        require(it.isSuccessful) { "Image HTTP status ${it.code}" }
                        val body = requireNotNull(it.body)
                        require(body.contentLength() <= limit)
                        body.byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(65536)
                            while (continuation.isActive) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size().toLong() + count <= limit) { "Image exceeds download budget" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    } }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }
}
