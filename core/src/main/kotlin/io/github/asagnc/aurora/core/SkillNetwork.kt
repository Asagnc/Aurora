package io.github.asagnc.aurora.core

import java.nio.file.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class SkillSource(val input: String, val selection: String = "", val label: String = "", val revision: String = "")
data class ResolvedSkillSource(val source: SkillSource, val archiveUrl: String)
class SkillHttpStatus(val code: Int) : java.io.IOException("Skill source HTTP $code")

/** Public downloads have no model credentials, cookies, automatic retry or cross-request authentication. */
class SkillNetwork(private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
    .retryOnConnectionFailure(false).callTimeout(45, TimeUnit.SECONDS).build()) {
    suspend fun resolve(source: SkillSource): ResolvedSkillSource {
        val url = checked(source.input)
        if (url.host != "github.com" || url.pathSegments.size < 2 || url.pathSegments.contains("archive") || url.pathSegments.contains("releases"))
            return ResolvedSkillSource(source, url.toString())
        val owner = url.pathSegments[0]; val repo = url.pathSegments[1].removeSuffix(".git")
        require(owner.matches(Regex("[A-Za-z0-9_.-]+")) && repo.matches(Regex("[A-Za-z0-9_.-]+")))
        require(url.pathSegments.drop(2).all { it.isEmpty() } || url.pathSegments.getOrNull(2) == "tree") {
            "Use a GitHub repository or tree URL, not a blob/settings page"
        }
        val api = "https://api.github.com/repos/$owner/$repo"
        var selection = source.selection
        val ref = if (url.pathSegments.size > 3 && url.pathSegments[2] == "tree") {
            require(url.pathSegments.size <= 16)
            selection = source.selection.ifBlank { url.pathSegments.drop(4).joinToString("/") }
            url.pathSegments[3]
        } else {
            try { requireNotNull(json("$api/releases/latest").text("tag_name")) }
            catch (missing: SkillHttpStatus) { if (missing.code != 404) throw missing; requireNotNull(json(api).text("default_branch")) }
        }
        val sha = requireNotNull(json("$api/commits/${java.net.URLEncoder.encode(ref, Charsets.UTF_8)}").text("sha"))
        require(sha.matches(Regex("[a-f0-9]{40}")))
        return ResolvedSkillSource(SkillSource(source.input, selection, "$owner/$repo · $ref", sha),
            "https://codeload.github.com/$owner/$repo/zip/$sha")
    }

    suspend fun download(url: String, target: Path) = withTimeout(60_000) {
        response(url) { result ->
            require(result.body.contentLength() <= 64L * 1024 * 1024)
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { output ->
                result.body.byteStream().use { input ->
                    val bytes = ByteArray(65536); var count = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val size = input.read(bytes); if (size < 0) break
                        count += size; require(count <= 64L * 1024 * 1024) { "Skill download exceeds 64 MiB" }
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

    private suspend fun <T> response(address: String, block: (Response) -> T): T = withTimeout(60_000) {
        var url = checked(address)
        repeat(6) {
            val call = client.newCall(Request.Builder().url(url).header("User-Agent", "Aurora-Skills").build())
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
