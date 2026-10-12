package io.github.asagnc.aurora.core

import kotlinx.serialization.json.JsonObject

data class NativeContinuation(
    val connection: Connection,
    val history: List<JsonObject>,
    val appended: List<JsonObject>,
) {
    companion object {
        fun append(previous: WireHistory, next: Connection, fingerprint: String, text: String): NativeContinuation {
            require(CachePolicy.canReuse(previous, next, fingerprint)) { "Native history is outside the reuse scope" }
            val update = if (CachePolicy.effectiveEffort(previous) != next.effort) listOf(objectOf(
                "type" to string("configuration_update"), "reasoning" to objectOf("effort" to string(next.effort))
            )) else emptyList()
            val appended = update + MessageProjection.message(next.protocol, "user", text)
            // Request-level effort stays fixed; replay updates at their original positions.
            return NativeContinuation(next.copy(effort = previous.effort), previous.entries + appended, appended)
        }
    }
}
