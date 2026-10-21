package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlin.test.*

class LinuxPreparationTest {
    @Test fun parsesStartTicksAfterComplexProcCommand() {
        val tail = listOf("S") + (4..21).map { it.toString() } + listOf("123456", "0", "0")
        assertEquals(123456L, linuxProcessStartTicks("123 (Root (worker) name)) ${tail.joinToString(" ")}\n"))
    }
    @Test fun rejectsTruncatedNegativeAndOversizedProcIdentity() {
        listOf("", "123 (worker) S 1", "-1 (worker) " + List(20) { "1" }.joinToString(" "),
            "123 (worker) " + (List(19) { "1" } + "-1").joinToString(" "), "x".repeat(65537))
            .forEach { assertFails { linuxProcessStartTicks(it) } }
    }
    @Test fun preparationTicketRoundTripsExactServiceEpochAndImage() {
        val identity = LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val ticket = LinuxPreparationTicket(UUID.randomUUID().toString(), identity, "a".repeat(64), 10000)
        ticket.validate()
        assertEquals(ticket, WireJson.decodeFromString<LinuxPreparationTicket>(WireJson.encodeToString(ticket)))
        assertNotEquals(identity, identity.copy(epoch = UUID.randomUUID().toString()))
        assertNotEquals(identity, identity.copy(startTicks = 457))
    }
    @Test fun rejectsMalformedPreparationTickets() {
        val identity = LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val ticket = LinuxPreparationTicket(UUID.randomUUID().toString(), identity, "a".repeat(64), 10000)
        listOf(ticket.copy(operation = "../outside"), ticket.copy(appUid = 0), ticket.copy(image = "unknown"),
            ticket.copy(protocol = 2), ticket.copy(identity = identity.copy(pid = -1)),
            ticket.copy(identity = identity.copy(bootId = "1-1-1-1-1")))
            .forEach { assertFails { it.validate() } }
    }
}
