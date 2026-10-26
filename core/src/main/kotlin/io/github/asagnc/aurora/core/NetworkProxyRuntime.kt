package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.*

/** Immutable APK-owned scripts inside the same readonly shared bundle and Linux task leases. */
object NetworkProxyRuntime {
    const val launcherPath = "/opt/mitmdump/aurora-proxy"
    const val probePath = "/opt/mitmdump/aurora-proxy-probe"
    const val readPath = "/opt/mitmdump/aurora-proxy-read"
    const val probeMarker = "aurora-proxy-probe-ok"
    val profileSha256 = mapOf(
        "aurora-proxy" to "c63d27ae75b5dd51d230a83a02e1400b23ed1558eedda38bb79c80c22122890b",
        "aurora-proxy-probe" to "8828bb80292cbbfd15d11c0bbf9ed42257835c3ed6d5d69a4a2282905a5f5aab",
        "aurora-proxy-read" to "0a47d9077df8a992a77e82c4d8317bb2699503f0e9110d3e744f4f626a49aecc",
        "aurora_proxy.py" to "c03e3e9416195fff3862fd951a8d27508cb177d864f477874346fa304edb2f71",
        "aurora_proxy_probe.py" to "f72cc52cc9ef4091c035dc3e463702129db58cf86f783869f9c6879e2b232165",
        "aurora_proxy_read.py" to "3c7dc315305525c50a265cbe7f9fe5fc3153b7a171ae559ba984428b3bf2cb44",
        "aurora_proxy_rules.py" to "cee883fc014a823baf8d6729361d8ee9c032ef4b3c81f75afb165781471f9db1",
        "mitmproxy-LICENSE" to "cc4ab04ed7180af6c0ede7147f9ec2aa05212c088d7b3c8e38bbffe91c9c8f82")
    private val revision get() = "proxy1-" + digest(profileSha256.toSortedMap().toString().toByteArray(Charsets.UTF_8))
    val catalog get() = SharedToolCatalog("network-proxy-v1", revision, listOf(NetworkProxyPreparation.component), 800L * 1024 * 1024)
    val bundleId: String get() = catalog.bundleId

    fun publishProfiles(stage: Path, profiles: Map<String, ByteArray>, mode: (Path, Int) -> Unit) {
        require(profiles.keys == profileSha256.keys)
        val directory = stage.resolve("mitmdump")
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        for ((name, expected) in profileSha256) {
            val bytes = profiles.getValue(name)
            require(bytes.size <= 128 * 1024 && digest(bytes) == expected) { "Proxy profile digest mismatch" }
            val target = directory.resolve(name)
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS)
            mode(target, if (name.startsWith("aurora-")) 493 else 420)
        }
    }

    fun probeOptions(port: Int) = NetworkProxyOptions(port, listOf(
        NetworkProxyRule("request", "127.0.0.1", headers = mapOf("X-Aurora-Fixture" to "modified")),
        NetworkProxyRule("response", "127.0.0.1", pathPrefix = "/owned-http", headers = mapOf("X-Aurora-Response" to "modified"),
            bodyBase64 = Base64.getEncoder().encodeToString("owned-modified".toByteArray(Charsets.UTF_8))),
        NetworkProxyRule("websocket", "127.0.0.1", pathPrefix = "/owned-ws", direction = "server",
            bodyBase64 = Base64.getEncoder().encodeToString("owned-ws-modified".toByteArray(Charsets.UTF_8)))), durationSeconds = 180, maxFlows = 8)

    fun probePassed(context: StaticReverseProbeContext, proof: JsonObject): Boolean {
        val result = proof["result"] as? JsonObject ?: return false
        val output = (result["stdout"] as? JsonObject)?.text("text").orEmpty()
        return closedToolPassed(context, proof) && output.lineSequence().count { it.trimEnd() == probeMarker } == 1
    }

    fun closedToolPassed(context: StaticReverseProbeContext, proof: JsonObject): Boolean {
        val done = proof["completion"] as? JsonObject ?: return false
        return proof.number("protocol") == 1L && proof.text("runId") == context.runId && proof.text("generation") == context.generation &&
            proof.text("toolBundle") == bundleId && proof["stopped"] == JsonPrimitive(true) && done.number("exitCode") == 0L &&
            done["scopeRemoved"] == JsonPrimitive(true) && done["interrupted"] == JsonPrimitive(false) && done["timedOut"] == JsonPrimitive(false)
    }

    fun finalProbePassed(id: String, options: NetworkProxyOptions, value: JsonObject): Boolean {
        val seen = value.number("seenFlows") ?: return false
        val completed = value.number("completedFlows") ?: return false
        val stored = value.number("storedBytes") ?: return false
        val events = value.number("events") ?: return false
        val expected = digest(Base64.getDecoder().decode(options.encoded()))
        return value.number("protocol") == 1L && value.text("id") == id && value.number("port") == options.port.toLong() &&
            value["probe"] == JsonPrimitive(true) && value.text("configSha256") == expected && value.text("reason") == "probe_complete" &&
            seen in 3..options.maxFlows.toLong() && completed in 3..seen && stored in 1..(options.maxStoredBytes - 1024) && events in 4..1024 && value["storedBytesExcludesFinalRecord"] == JsonPrimitive(true)
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
