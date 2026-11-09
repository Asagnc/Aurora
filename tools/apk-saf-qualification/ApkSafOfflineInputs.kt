package io.github.asagnc.aurora.qualification

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.reverse.SharedToolchainManager
import java.nio.file.Files
import java.nio.file.LinkOption
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.buffer
import okio.source

/** Identical fixed offline transport boundary to the existing tool installation fixture. */
internal fun fixedOfflineManager(app: AuroraApplication): SharedToolchainManager {
    val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
        val request = chain.request()
        val component = AndroidBuildTools.components.single { !it.localOnly && it.url == request.url.toString() }
        require(request.method == "GET")
        val archive = app.cacheDir.toPath().resolve("tool-device-inputs/${component.id}.archive")
        require(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && Files.size(archive) == component.compressedBytes)
        val body = object : ResponseBody() {
            private val input = archive.toFile().source().buffer()
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = component.compressedBytes
            override fun source() = input
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
            .message("Explicit fixed offline fixture")
            .header("Content-Length", component.compressedBytes.toString()).body(body).build()
    }.build()
    return SharedToolchainManager(app, SkillNetwork(client))
}
