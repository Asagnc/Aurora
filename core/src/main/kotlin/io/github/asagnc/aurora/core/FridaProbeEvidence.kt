package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** Positive self-test observations only. Cleanup/exit must already have independent native proof. */
object FridaProbeEvidence {
    fun observed(ticket: RootManagedRunTicket, log: String): Boolean {
        val java = ticket.bridge != null
        if (log.length > 4_259_840 || ticket.script != FridaManagedProbe.snapshot(java) ||
            ticket.target != RootTargetIdentity(ticket.identity.pid, ticket.identity.startTicks)) return false
        var native = 0; var art = 0
        for (line in log.lineSequence()) {
            if (line.length > 131072) return false
            val event = parse(line) ?: continue
            if (event.text("kind") != "message" || event.number("protocol") != 1L) continue
            val message = parse(event.text("message") ?: continue) ?: continue
            if (message.text("type") != "send") continue
            val payload = message["payload"] as? JsonObject ?: continue
            when (payload.text("aurora")) {
                "native_hook_observed", "java_call_observed" -> {
                    val pid = payload["pid"] as? JsonPrimitive ?: return false
                    if (pid.isString || pid.intOrNull != ticket.identity.pid) return false
                    if (payload.text("aurora") == "native_hook_observed") native++ else art++
                    if (native > 1 || art > 1) return false
                }
            }
        }
        return native == 1 && art == (if (java) 1 else 0)
    }

    private fun parse(value: String): JsonObject? = runCatching { WireJson.parseToJsonElement(value) as? JsonObject }.getOrNull()
}
