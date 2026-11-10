package io.github.asagnc.aurora.core

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

@kotlinx.serialization.Serializable
data class AvailableModel(val id: String, val name: String)
/** Editing a catalogue must not silently switch the active model and its cache scope. */
fun selectedCatalogModel(current: String, models: List<AvailableModel>): String {
    require(models.isNotEmpty()) { "Select at least one model" }
    return models.firstOrNull { it.id == current }?.id ?: models.first().id
}

class ModelDirectoryFailure(message: String) : IOException(message)

/** Explicit catalogue GET only: no inference, redirects, logging or credential persistence. */
class ModelDirectory internal constructor(private val client: OkHttpClient, private val loopback: Boolean = false) {
    constructor() : this(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build())

    suspend fun fetch(connection: Connection, token: String): List<AvailableModel> = withTimeout(60_000) {
        require(token.isNotBlank() && token.length <= 8192 && token.all { it.code in 32..126 }) { "API 密钥格式无效" }
        val base = connection.baseUrl.toHttpUrl()
        require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) { "请填写不含凭据或参数的 API 基础地址" }
        require(base.isHttps || (loopback && base.host == "127.0.0.1")) { "API 基础地址必须使用 HTTPS" }
        val endpoint = base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/models").build()
        val models = linkedMapOf<String, AvailableModel>()
        val cursors = mutableSetOf<String>()
        var after: String? = null
        repeat(32) {
            val url = endpoint.newBuilder().apply { after?.let { addQueryParameter("after_id", it) } }.build()
            val request = Request.Builder().url(url).header("Accept", "application/json")
            val auth = if (connection.authentication == Authentication.AUTOMATIC)
                if (connection.protocol == Protocol.ANTHROPIC) Authentication.API_KEY else Authentication.BEARER
                else connection.authentication
            request.header(when (auth) { Authentication.API_KEY -> "x-api-key"; Authentication.API_KEY_HEADER -> "api-key"; else -> "Authorization" },
                if (auth == Authentication.BEARER) "Bearer $token" else token)
            if (connection.protocol == Protocol.ANTHROPIC) request.header("anthropic-version", "2023-06-01")
            val page = page(request.build())
            val entries = page["data"] as? JsonArray ?: throw ModelDirectoryFailure("服务返回的模型目录格式不正确")
            entries.forEach { entry ->
                val item = entry as? JsonObject ?: throw ModelDirectoryFailure("服务返回的模型条目格式不正确")
                val id = (item["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?.takeIf { it.isNotBlank() && it.length <= 512 && it.none(Char::isISOControl) }
                    ?: throw ModelDirectoryFailure("服务返回的模型标识无效")
                val name = ((item["display_name"] ?: item["name"]) as? JsonPrimitive)?.contentOrNull?.filterNot(Char::isISOControl)?.take(200) ?: id
                models.putIfAbsent(id, AvailableModel(id, name))
            }
            if (models.size > 4096) throw ModelDirectoryFailure("模型目录过大，暂不支持完整显示")
            val more = page["has_more"]?.let { (it as? JsonPrimitive)?.booleanOrNull
                ?: throw ModelDirectoryFailure("服务返回的分页状态不正确") } ?: false
            if (!more) return@withTimeout models.values.sortedBy { it.id.lowercase(java.util.Locale.ROOT) }
            after = (page["last_id"] as? JsonPrimitive)?.contentOrNull ?: entries.lastOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            if (after.isNullOrBlank() || !cursors.add(after!!)) throw ModelDirectoryFailure("模型目录分页没有继续，未显示不完整结果")
        }
        throw ModelDirectoryFailure("模型目录分页过多，未显示不完整结果")
    }

    private suspend fun page(request: Request): JsonObject = coroutineScope {
        val call = client.newCall(request)
        val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw ModelDirectoryFailure(when (response.code) {
                        401, 403 -> "密钥无效或无权读取模型目录（HTTP ${response.code}）"
                        404, 405 -> "此服务不提供模型目录，可使用手动添加"
                        429 -> "请求过于频繁，请稍后重试"
                        in 300..399 -> "模型目录发生重定向，请核对基础地址"
                        else -> "获取模型失败（HTTP ${response.code}）"
                    })
                    val bytes = response.body.byteStream().use { it.readNBytes(1_048_577) }
                    if (bytes.size > 1_048_576) throw ModelDirectoryFailure("模型目录响应过大")
                    try { WireJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
                        ?: throw ModelDirectoryFailure("模型目录不是 JSON 对象") }
                    catch (failure: ModelDirectoryFailure) { throw failure }
                    catch (_: Exception) { throw ModelDirectoryFailure("模型目录不是有效 JSON") }
                }
            }
        } catch (failure: IOException) {
            currentCoroutineContext().ensureActive()
            if (failure is ModelDirectoryFailure) throw failure
            throw ModelDirectoryFailure("网络连接失败或超时，请核对地址后重试")
        } finally { cancellation.cancel(); call.cancel() }
    }
}
