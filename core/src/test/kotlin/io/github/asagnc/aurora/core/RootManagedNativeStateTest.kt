package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.encodeToString
import kotlin.test.*

class RootManagedNativeStateTest {
    private val ticket = RootManagedRunTicket("00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002", LinuxRootIdentity(10, 20,
            "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004"),
        10000, "test", "a".repeat(64), RootTargetIdentity(90, 900), RootScriptSnapshot("test", "b".repeat(64), 1))

    private fun ready() = ByteBuffer.allocate(164).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("ARW1${ticket.runId}${ticket.digest()}".toByteArray(Charsets.US_ASCII))
        putInt(30); putLong(300); putInt(40); putLong(400); put(ticket.identity.bootId.toByteArray(Charsets.US_ASCII))
    }.array()

    private fun report(flags: Int = 127, status: Int = 0) = ByteBuffer.allocate(52).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("AEX1".toByteArray(Charsets.US_ASCII)); put(1); put(flags.toByte()); put(status.toByte()); put(0)
        put(ticket.digest().chunked(2).map { it.toInt(16).toByte() }.toByteArray()); putInt(40); putLong(400)
    }.array()

    private fun stopped(phase: Int = 15, worker: Int = 1, domain: Int = 1) = ByteBuffer.allocate(123)
        .order(ByteOrder.LITTLE_ENDIAN).apply {
            put("ARI1\n${ticket.runId}\n${ticket.digest()}\n".toByteArray(Charsets.US_ASCII))
            put(phase.toByte()); put(worker.toByte()); put(domain.toByte()); put(0)
            putInt(if (worker == 1) 0 else -1); putInt(0); putInt(0)
        }.array()

    @Test fun exactRecordsYieldCleanupForCompletionAndCooperativeCancellation() {
        val admission = RootManagedAdmission.decode(ticket, ready())
        assertEquals(ticket.digest(), admission.ticketSha256)
        assertEquals(RootTargetIdentity(30, 300), admission.guard)
        assertEquals(RootTargetIdentity(40, 400), admission.worker)
        val finished = RootManagedNativeState.decode(ticket, ready(), report(), stopped())
        assertEquals(RootTargetIdentity(40, 400), finished.worker)
        assertTrue(ticket.confirms(assertNotNull(finished.normalCleanupProof())))
        val cancelled = RootManagedNativeState.decode(ticket, ready(), report(63, 1), stopped().apply {
            ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(111, 1).putInt(115, 2)
        })
        assertTrue(ticket.confirms(assertNotNull(cancelled.normalCleanupProof())))
        assertEquals(1, cancelled.workerStatus)
    }

    @Test fun missingReportOrDetachCannotReleaseInjectionIntent() {
        assertNull(RootManagedNativeState.decode(ticket, ready(), null, stopped()).normalCleanupProof())
        assertNull(RootManagedNativeState.decode(ticket, ready(), report(43, 1), stopped()).normalCleanupProof())
        assertNull(RootManagedNativeState.decode(ticket, null, null, stopped(8, 2, 2)).normalCleanupProof())
        assertNull(RootManagedNativeState.decode(ticket, null, null, stopped(9, 0, 1)).normalCleanupProof())
    }

    @Test fun retirementBeforeDispatchUsesDistinctProofWithoutInventingWorkerExit() {
        val state = RootManagedNativeState.decode(ticket, null, null, stopped(8, 2, 2))
        val proof = assertNotNull(state.neverDispatchedProof())
        assertNull(state.normalCleanupProof())
        assertTrue(proof.matches(ticket))
        assertTrue(WireJson.decodeFromString<RootManagedRetirementProof>(WireJson.encodeToString(proof)).matches(ticket))
        assertFalse(proof.matches(ticket.copy(appBuild = "other")))
        assertFalse(proof.matches(ticket.copy(identity = ticket.identity.copy(startTicks = 21))))
        assertNull(RootManagedNativeState.decode(ticket, null, null, stopped(9, 0, 1)).neverDispatchedProof())
        assertNull(RootManagedNativeState.decode(ticket, ready(), report(), stopped()).neverDispatchedProof())
    }

    @Test fun foreignTaskWorkerBootAndPartialRecordsAreRejected() {
        listOf(ready().copyOf(163), ready() + 0.toByte(), ready().apply { this[4] = '1'.code.toByte() },
            ready().apply { this[128] = '1'.code.toByte() }, ready().apply { this[116] = 30 },
            ready().apply { this[119] = 0xff.toByte() }).forEach {
            assertFails { RootManagedNativeState.decode(ticket, it, report(), stopped()) }
        }
        listOf(report().copyOf(51), report() + 0.toByte(), report().apply { this[8] = (this[8].toInt() xor 1).toByte() },
            report().apply { this[40] = 41 }, report().apply { this[44] = 0 }, report().apply { this[7] = 1 },
            report(125, 0), report(127, 1), report(57, 0), report(0xff, 0)).forEach {
            assertFails { RootManagedNativeState.decode(ticket, ready(), it, stopped()) }
        }
    }

    @Test fun impossibleStagesUnknownResourcesAndMalformedStatusAreRejected() {
        listOf(stopped().copyOf(122), stopped() + 0.toByte(), stopped().apply { this[5] = '1'.code.toByte() },
            stopped(7), stopped(12), stopped(15, 0), stopped(15, 1, 2), stopped().apply { this[110] = 1 },
            stopped().apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(111, 256) },
            stopped().apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(115, 6) },
            stopped().apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(119, 4096) }).forEach {
            assertFails { RootManagedNativeState.decode(ticket, ready(), report(), it) }
        }
        assertFails { RootManagedNativeState.decode(ticket, null, report(), stopped(9, 0)) }
        assertFails { RootManagedNativeState.decode(ticket, ready(), report(), stopped(9)) }
    }
}
