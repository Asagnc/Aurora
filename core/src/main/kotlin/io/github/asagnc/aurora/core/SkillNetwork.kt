package io.github.asagnc.aurora.core

import java.nio.file.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class SkillSource(val input: String, val selection: String = "", val label: String = "", val revision: String = "", val ref: String = "")
data class ResolvedSkillSource(val source: SkillSource, val archiveUrl: String)
class SkillHttpStatus(val code: Int) : java.io.IOException("Skill source HTTP $code")

/** Public downloads have no model credentials, cookies, automatic retry or cross-request authentication. */
class SkillNetwork(private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
    .retryOnConnectionFailure(false).callTimeout(45, TimeUnit.SECONDS).build()) {
    suspend fun resolve(source: SkillSource): ResolvedSkillSource {
        val url = checked(source.input)
        if (source.selection.isNotBlank()) skillRelativePath(source.selection)
        val location = skillGithubLocation(url, source) ?: return ResolvedSkillSource(source, url.toString())
        val owner = location.owner; val repo = location.repo
        val api = "https://api.github.com/repos/$owner/$repo"
        var kind = "引用"
        val ref = location.ref ?: run {
            try { kind = "正式发布"; requireNotNull(json("$api/releases/latest").text("tag_name")) }
            catch (missing: SkillHttpStatus) {
                if (missing.code != 404) throw missing
                kind = "分支快照"; requireNotNull(json(api).text("default_branch"))
            }
        }
        val endpoint = "$api/commits".toHttpUrl().newBuilder().addPathSegment(ref).build()
        val sha = requireNotNull(json(endpoint.toString()).text("sha"))
        require(sha.matches(Regex("[a-f0-9]{40}")))
        return ResolvedSkillSource(source.copy(selection = location.selection, label = "$owner/$repo · $kind $ref", revision = sha),
            "https://codeload.github.com/$owner/$repo/zip/$sha")
    }

    suspend fun download(url: String, target: Path, maxBytes: Long = 64L * 1024 * 1024) = withTimeout(60_000) {
        require(maxBytes in 1..128L * 1024 * 1024)
        downloadBounded(url, target, maxBytes, 60_000)
    }

    /** Only fixed compiled tool inputs get the larger transport budget; ordinary Skills keep their original limits. */
    suspend fun downloadTool(component: StaticReverseComponent, target: Path) = withTimeout(180_000) {
        require(!component.localOnly && (component in StaticReverseTools.components || component in AndroidBuildTools.components || component == NetworkProxyPreparation.component))
        downloadBounded(component.url, target, component.compressedBytes, 180_000)
        runInterruptible(Dispatchers.IO) { verifyStaticReverseArchive(component, target) }
    }

    private suspend fun downloadBounded(url: String, target: Path, maxBytes: Long, timeoutMs: Long) {
        response(url, timeoutMs) { result ->
            require(result.body.contentLength() <= maxBytes)
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { output ->
                result.body.byteStream().use { input ->
                    val bytes = ByteArray(65536); var count = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val size = input.read(bytes); if (size < 0) break
                        count += size; require(count <= maxBytes) { "Download exceeds byte budget" }
                        output.write(bytes, 0, size)
                    }
                }
            }
        }
    }

    private suspend fun json(url: String): JsonObject = response(url) { response ->
        val bytes = response.body.byteStream().use { it.readNBytes(1024 * 1024 + 1) }
        require(bytes.size <= 1024 * 1024)
        val text = String(bytes, Charsets.UTF_8); require(jsonWithinDepth(text))
        WireJson.parseToJsonElement(text).jsonObject
    }

    private suspend fun <T> response(address: String, timeoutMs: Long = 60_000, block: (Response) -> T): T = withTimeout(timeoutMs) {
        var url = checked(address)
        repeat(6) {
            val call = client.newCall(Request.Builder().url(url).header("User-Agent", "Aurora-Skills").build())
            if (timeoutMs > 60_000) call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            val next = coroutineScope {
                val cancel = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
                try {
                    runInterruptible(Dispatchers.IO) { call.execute().use { response ->
                        if (response.code in setOf(301, 302, 303, 307, 308)) {
                            val location = requireNotNull(response.header("Location"))
                            requireNotNull(url.resolve(location)).also { checked(it.toString()) } to null
                        } else {
                            if (!response.isSuccessful) throw SkillHttpStatus(response.code)
                            null to block(response)
                        }
                    } }
                } catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    throw failure
                } finally { withContext(NonCancellable) { cancel.cancelAndJoin() } }
            }
            if (next.first == null) { @Suppress("UNCHECKED_CAST") return@withTimeout next.second as T }
            url = requireNotNull(next.first)
        }
        error("Too many HTTPS redirects")
    }

    private fun checked(address: String): HttpUrl = address.toHttpUrl().also {
        require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null) {
            "Skill source must be HTTPS without embedded credentials or a fragment"
        }
    }
}
