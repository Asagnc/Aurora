package io.github.asagnc.aurora.reverse

import android.os.Bundle
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.LinkOption
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.buffer
import okio.source

/** Explicit transport fixtures only. Production still enforces every catalog size/hash.
 * Offline bytes qualify installation/runtime, never official-network or TLS access. */
internal fun toolInstallationFixture(app: AuroraApplication, args: Bundle): SharedToolchainManager {
    val offline = args.getString("auroraToolOfflineInputs") == "true"
    val port = args.getString("auroraToolDownloadProxyPort")?.toInt()?.also { require(it in 1024..65535) }
    if (!offline && port == null) return app.sharedToolchain
    val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
    if (port != null) client.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
    if (offline) client.addInterceptor { chain ->
        val request = chain.request()
        val inputs = StaticReverseTools.components + AndroidBuildTools.components + NetworkProxyPreparation.component
        val component = inputs.single { !it.localOnly && it.url == request.url.toString() }
        require(request.method == "GET")
        val archive = app.cacheDir.toPath().resolve("tool-device-inputs/${component.id}.archive")
        require(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && Files.size(archive) == component.compressedBytes)
        val body = object : ResponseBody() {
            private val input = archive.toFile().source().buffer()
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = component.compressedBytes
            override fun source() = input
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Explicit fixed offline fixture")
            .header("Content-Length", component.compressedBytes.toString()).body(body).build()
    }
    return SharedToolchainManager(app, SkillNetwork(client.build()))
}
