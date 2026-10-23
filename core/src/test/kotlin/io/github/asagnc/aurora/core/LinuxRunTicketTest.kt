package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class LinuxRunTicketTest {
    private fun ticket() = LinuxRunTicket(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "a".repeat(64),
        LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString()), 10000,
        listOf("/bin/sh", "-c", "printf '极光'"))
    @Test fun roundTripKeepsDispatcherIdentityAndExactArguments() {
        val ticket = ticket().also { it.validate() }
        assertEquals(ticket, WireJson.decodeFromString<LinuxRunTicket>(WireJson.encodeToString(ticket)))
    }
    @Test fun rejectsUnboundedOrMalformedDispatch() {
        val ticket = ticket()
        listOf(ticket.copy(argv = emptyList()), ticket.copy(argv = listOf("sh")),
            ticket.copy(argv = listOf("/bin/sh", "bad\u0000argument")), ticket.copy(argv = listOf("/bin/sh", "a".repeat(65536))),
            ticket.copy(timeoutMs = 0), ticket.copy(timeoutMs = 270001), ticket.copy(appUid = 0),
            ticket.copy(runId = "../other"), ticket.copy(generation = "bad"), ticket.copy(protocol = 2))
            .forEach { assertFails { it.validate() } }
    }
    @Test fun toolBundleIsOptionalButMustMatchTheExactStoppedProof() {
        val task = ticket().copy(toolBundle = "a".repeat(64)); task.validate()
        assertEquals(task, WireJson.decodeFromString(LinuxRunTicket.serializer(), WireJson.encodeToString(LinuxRunTicket.serializer(), task)))
        val proof = objectOf("protocol" to JsonPrimitive(1), "runId" to string(task.runId),
            "generation" to string(task.generation), "stopped" to JsonPrimitive(true))
        assertFalse(task.confirms(proof))
        assertTrue(task.confirms(JsonObject(proof + ("toolBundle" to string(requireNotNull(task.toolBundle))))))
        assertFalse(task.confirms(JsonObject(proof + ("toolBundle" to string("b".repeat(64))))))
        assertFails { task.copy(toolBundle = "../bundle").validate() }
        val legacy = WireJson.decodeFromString(LinuxRunTicket.serializer(), WireJson.encodeToString(LinuxRunTicket.serializer(), ticket()))
        assertNull(legacy.toolBundle)
    }

    @Test fun escapedWireBudgetIsCheckedBeforeReservingALease() {
        assertFails { ticket().copy(argv = listOf("/bin/sh", "\u0001".repeat(60000))).validate() }
    }
    @Test fun onlyExactStoppedRunCanReleaseItsLease() {
        val ticket = ticket()
        val response = objectOf("protocol" to JsonPrimitive(1), "runId" to string(ticket.runId),
            "generation" to string(ticket.generation), "stopped" to JsonPrimitive(true))
        assertTrue(ticket.confirms(response))
        for ((key, value) in listOf("runId" to string(UUID.randomUUID().toString()), "generation" to string("b".repeat(64)),
            "protocol" to JsonPrimitive(2), "stopped" to JsonPrimitive(false))) {
            assertFalse(ticket.confirms(JsonObject(response + (key to value))))
        }
        assertFalse(ticket.confirms(objectOf("exitCode" to JsonPrimitive(0))))
    }
}
