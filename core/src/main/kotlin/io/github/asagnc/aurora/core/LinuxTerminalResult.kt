package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** A transport exit is not sufficient: match the durable Linux stopped proof. */
data class LinuxTerminalResult(val exitCode: Int?, val state: String, val reason: String?, val proof: JsonObject) {
    companion object {
        fun decode(ticket: LinuxRunTicket, value: String): LinuxTerminalResult {
            require(value.length <= 262144)
            val result = WireJson.parseToJsonElement(value).jsonObject
            val proof = requireNotNull(result["linuxProof"]).jsonObject
            require(ticket.confirms(proof)) { "Linux stopped proof unavailable; lease retained" }
            require(JsonObject(result - "linuxProof").toString().length <= 4096)
            val state = requireNotNull(result.text("state"))
            require(state in setOf("exited", "timed_out", "interrupted", "failed"))
            val code = result.number("exitCode")?.let(Math::toIntExact)
            require(code == null || code in 0..255)
            if (state == "exited") {
                val completion = requireNotNull(proof["completion"]).jsonObject
                require(code != null && completion.number("exitCode") == code.toLong() &&
                    completion["scopeRemoved"] == JsonPrimitive(true) && completion["interrupted"] == JsonPrimitive(false) &&
                    completion["timedOut"] != JsonPrimitive(true)) {
                    "Terminal exit is inconsistent with Linux completion"
                }
            }
            return LinuxTerminalResult(code, state, result.text("reason"), proof)
        }
    }
}
