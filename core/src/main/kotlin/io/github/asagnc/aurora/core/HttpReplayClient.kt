package io.github.asagnc.aurora.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ProtocolException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import okhttp3.*

internal data class HttpReplayResponse(val status: Int, val protocol: String, val headers: Headers, val bytes: ByteArray)
internal class HttpResponseLimitExceeded : IOException("Response exceeds byte budget")

/** No model/browser credentials, cookie store, cache, redirects, transport retries or coalesced prior connections. */
internal class HttpReplayClient {
    suspend fun execute(options: HttpReplayRequest): HttpReplayResponse = coroutineScope {
        val pool = ConnectionPool(0, 1, TimeUnit.SECONDS)
        val exchanges = AtomicInteger()
        var originalRetryAfter: List<String>? = null
        val client = shared.newBuilder().connectionPool(pool).proxy(options.proxy)
            .callTimeout(options.timeoutMs, TimeUnit.MILLISECONDS).addNetworkInterceptor { chain ->
                if (exchanges.getAndIncrement() != 0) throw ProtocolException("Implicit HTTP replay rejected")
                val response = chain.proceed(chain.request())
                // OkHttp 5.5 can follow 503/Retry-After:0 even with retries disabled. Suppress only
                // its internal follow-up decision; restore the original header in the returned record.
                if (response.code == 503 && response.header("Retry-After")?.matches(Regex("0+")) == true) {
                    originalRetryAfter = response.headers.values("Retry-After")
                    response.newBuilder().header("Retry-After", "1").build()
                } else response
            }.build()
        val call = client.newCall(options.request)
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            runInterruptible(Dispatchers.IO) { call.execute().use { response ->
                val body = response.body
                if (body.contentLength() > options.maxResponseBytes) throw HttpResponseLimitExceeded()
                val bytes = body.byteStream().use { input ->
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(65536)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = input.read(buffer, 0, minOf(buffer.size, options.maxResponseBytes - output.size() + 1))
                        if (count < 0) break
                        if (count > options.maxResponseBytes - output.size()) throw HttpResponseLimitExceeded()
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val headers = originalRetryAfter?.let { values -> response.headers.newBuilder().removeAll("Retry-After")
                    .apply { values.forEach { add("Retry-After", it) } }.build() } ?: response.headers
                HttpReplayResponse(response.code, response.protocol.toString(), headers, bytes)
            } }
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive(); throw failure
        } finally {
            withContext(NonCancellable) { cancellation.cancelAndJoin() }
            pool.evictAll()
        }
    }

    companion object {
        private val shared = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .cookieJar(CookieJar.NO_COOKIES).cache(null).build()
    }
}
