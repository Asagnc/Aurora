package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable data class LinuxRunTicket(val runId: String, val owner: String, val generation: String,
    val identity: LinuxRootIdentity, val appUid: Int, val argv: List<String>, val timeoutMs: Long = 60_000,
    val protocol: Int = 1) {
    fun validate() {
        require(protocol == 1 && appUid > 0 && timeoutMs in 1..270_000)
        require(UUID.fromString(runId).toString() == runId && UUID.fromString(owner).toString() == owner)
        require(generation.matches(Regex("[a-f0-9]{64}")))
        identity.validate()
        require(argv.size in 1..128 && argv.first().startsWith("/"))
        require(argv.all { '\u0000' !in it } && argv.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1 } <= 65536)
        require(WireJson.encodeToString(this).length <= 262144)
    }
    fun confirms(response: JsonObject): Boolean = response.number("protocol") == 1L &&
        response.text("runId") == runId && response.text("generation") == generation &&
        response["stopped"] == JsonPrimitive(true)
}
