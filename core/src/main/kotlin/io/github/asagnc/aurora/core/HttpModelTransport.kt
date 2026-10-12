package io.github.asagnc.aurora.core

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class NetworkProblem { HTTP, CONNECTION, INVALID_STREAM }
class ModelNetworkException(val problem: NetworkProblem, val status: Int? = null) :
    IOException(when (problem) {
        NetworkProblem.HTTP -> "服务返回 HTTP $status"
        NetworkProblem.CONNECTION -> "连接失败或超时；请求可能已被服务接收，请检查后再重试"
        NetworkProblem.INVALID_STREAM -> "模型流不完整或不符合所选协议"
    })

class HttpModelTransport private constructor(
    private val client: OkHttpClient,
    private val credential: suspend (Connection) -> String,
    private val allowLoopbackHttp: Boolean,
) : ModelTransport {
    constructor(credential: suspend (Connection) -> String) : this(sharedClient, credential, false)

    override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
        val token = credential(request.connection)
        require(token.isNotBlank() && token.length <= 8192 && token.none { it == '\r' || it == '\n' }) { "API 密钥格式无效" }
        val outgoing = withContext(Dispatchers.IO) { buildRequest(request, token) }
        val call = client.newCall(outgoing)
        return coroutineScope {
            val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw ModelNetworkException(NetworkProblem.HTTP, response.code)
                        require(response.body.contentType()?.type == "text" && response.body.contentType()?.subtype == "event-stream") {
                            "Expected text/event-stream"
                        }
                        readStream(request.connection.protocol, response.body.charStream(), onText)
                    }
                }
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                when (failure) {
                    is CancellationException -> throw failure
                    is ModelNetworkException -> throw failure
                    is IOException -> throw ModelNetworkException(NetworkProblem.CONNECTION)
                    else -> throw ModelNetworkException(NetworkProblem.INVALID_STREAM)
                }
            } finally { cancellation.cancel(); call.cancel() }
        }
    }

    private suspend fun readStream(protocol: Protocol, source: java.io.Reader, onText: suspend (String) -> Unit): ModelTurn {
        val reader = SseReader(source)
        val assembler = StreamAssembler(protocol)
        var chars = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val event = reader.next() ?: break
            chars += event.data.length
            require(chars <= 32 * 1024 * 1024) { "Model turn exceeded the response budget" }
            if (event.data.isBlank()) continue
            val delta = assembler.accept(event)
            if (delta.isNotEmpty()) onText(delta)
        }
        return assembler.finish()
    }

    private fun buildRequest(request: ModelRequest, token: String): Request {
        val connection = request.connection
        val builder = Request.Builder().url(endpoint(connection, allowLoopbackHttp))
            .header("Accept", "text/event-stream")
            .post(RequestEncoder.encode(request).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        val authentication = when (connection.authentication) {
            Authentication.AUTOMATIC -> if (connection.protocol == Protocol.ANTHROPIC) Authentication.API_KEY else Authentication.BEARER
            else -> connection.authentication
        }
        if (authentication == Authentication.API_KEY) builder.header("x-api-key", token)
        else builder.header("Authorization", "Bearer $token")
        if (connection.protocol == Protocol.ANTHROPIC) builder.header("anthropic-version", "2023-06-01")
        return builder.build()
    }

    companion object {
        private val sharedClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

        fun endpoint(connection: Connection): HttpUrl = endpoint(connection, false)
        private fun endpoint(connection: Connection, allowLoopbackHttp: Boolean): HttpUrl {
            val url = requireNotNull(connection.baseUrl.toHttpUrlOrNull()) { "网关地址格式无效" }
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "网关地址不能包含凭据、查询参数或片段"
            }
            require(url.isHttps || (allowLoopbackHttp && url.host == "127.0.0.1")) { "网关必须使用 HTTPS" }
            val suffix = when (connection.protocol) {
                Protocol.CHAT -> "chat/completions"
                Protocol.RESPONSES -> "responses"
                Protocol.ANTHROPIC -> "messages"
            }
            return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/" + suffix).build()
        }

        internal fun loopbackTest(credential: suspend (Connection) -> String): HttpModelTransport =
            HttpModelTransport(sharedClient, credential, true)
    }
}
