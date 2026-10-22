package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.json.*
import kotlin.test.*

class LinuxTerminalResultTest {
    private val ticket = LinuxRunTicket(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "a".repeat(64),
        LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString()), 10000, listOf("/bin/sh"))
    private fun proof() = objectOf("protocol" to JsonPrimitive(1), "runId" to string(ticket.runId),
        "generation" to string(ticket.generation), "stopped" to JsonPrimitive(true), "completion" to objectOf(
            "exitCode" to JsonPrimitive(37), "scopeRemoved" to JsonPrimitive(true), "interrupted" to JsonPrimitive(false)))
    private fun outcome(proof: JsonElement = proof(), state: String = "exited", code: JsonElement = JsonPrimitive(37)) =
        objectOf("exitCode" to code, "state" to string(state), "reason" to JsonNull, "linuxProof" to proof)

    @Test fun guestNonzeroExitHasMatchingStoppedProof() {
        val result = LinuxTerminalResult.decode(ticket, outcome().toString())
        assertEquals(37, result.exitCode)
        assertEquals("exited", result.state)
        assertEquals(proof(), result.proof)
    }
    @Test fun transportOutcomeCannotReplaceOrMismatchProof() {
        assertFails { LinuxTerminalResult.decode(ticket, JsonObject(outcome() - "linuxProof").toString()) }
        for ((name, value) in listOf("runId" to string(UUID.randomUUID().toString()), "generation" to string("b".repeat(64)),
            "stopped" to JsonPrimitive(false), "protocol" to JsonPrimitive(2))) {
            assertFails { LinuxTerminalResult.decode(ticket, outcome(JsonObject(proof() + (name to value))).toString()) }
        }
    }
    @Test fun cleanupWithoutCompletionMayBeInterruptedButCannotClaimNormalExit() {
        val recovered = JsonObject(proof() + ("completion" to JsonNull))
        assertEquals("interrupted", LinuxTerminalResult.decode(ticket, outcome(recovered, "interrupted", JsonNull).toString()).state)
        assertFails { LinuxTerminalResult.decode(ticket, outcome(recovered).toString()) }
        assertFails { LinuxTerminalResult.decode(ticket, outcome(code = JsonPrimitive(0)).toString()) }
        val interrupted = JsonObject(proof() + ("completion" to JsonObject(proof()["completion"]!!.jsonObject + ("interrupted" to JsonPrimitive(true)))))
        assertFails { LinuxTerminalResult.decode(ticket, outcome(interrupted).toString()) }
        val timedOut = JsonObject(proof() + ("completion" to JsonObject(proof()["completion"]!!.jsonObject + ("timedOut" to JsonPrimitive(true)))))
        assertFails { LinuxTerminalResult.decode(ticket, outcome(timedOut).toString()) }
    }
    @Test fun rejectsMalformedStateAndUnboundedMetadata() {
        assertFails { LinuxTerminalResult.decode(ticket, outcome(state = "success-maybe").toString()) }
        assertFails { LinuxTerminalResult.decode(ticket, outcome(code = JsonPrimitive(Long.MAX_VALUE)).toString()) }
        assertFails { LinuxTerminalResult.decode(ticket, JsonObject(outcome() + ("reason" to string("x".repeat(4097)))).toString()) }
        assertFails { LinuxTerminalResult.decode(ticket, " ".repeat(262145)) }
    }
}
