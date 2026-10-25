package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class RootSpawnStateTest {
    private val ticket = RootManagedRunTicket("00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002", LinuxRootIdentity(10, 20,
            "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004"),
        10000, "test", "a".repeat(64), null, RootScriptSnapshot("test", "b".repeat(64), 1),
        protocol = 2, action = "spawn", spawnPackage = "io.aurora.fixture")
    private fun record(phase: Int, pid: Int = 90, ticks: Long = 900): ByteArray =
        ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("ASP2".toByteArray()); put(phase.toByte()); position(8)
            put(ticket.digest().chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            putInt(if (phase == 1) 0 else pid); putLong(if (phase == 1) 0 else ticks)
        }.array()
    private fun state(vararg phases: Int) = RootSpawnState.decode(ticket, (1..5).map { if (it in phases) record(it) else null })

    @Test fun missingCaptureAndResumeIntentAreUnsettled() {
        assertFalse(state(1).settled)
        assertNull(state(1).target)
        assertFalse(state(1, 2).settled)
        assertFalse(state(1, 2, 3).settled)
        assertTrue(state(1, 2, 3, 5).settled)
        assertTrue(state(1, 2, 4).settled)
    }
    @Test fun reorderedForeignOrPartialRecordsNeverGrantCleanup() {
        assertFails { state(2) }; assertFails { state(1, 3) }; assertFails { state(1, 2, 5) }
        assertFails { state(1, 2, 3, 4) }
        assertFails { RootSpawnState.decode(ticket, listOf(record(1), record(2), record(3, 91), null, null)) }
        assertFails { RootSpawnState.decode(ticket, listOf(record(1).copyOf(63), null, null, null, null)) }
        assertFails { RootSpawnState.decode(ticket.copy(observeMs = 1), listOf(record(1), null, null, null, null)) }
    }
    @Test fun legacyAttachBytesAndDigestDoNotGainANewDefaultField() {
        val old = ticket.copy(protocol = 1, action = "attach", target = RootTargetIdentity(90, 900), spawnPackage = null)
        val json = WireJson.parseToJsonElement(WireJson.encodeToString(old)).jsonObject
        assertEquals(setOf("runId", "owner", "identity", "appUid", "appBuild", "workerSha256", "target", "script",
            "bridge", "observeMs", "protocol", "kind", "action"), json.keys)
        assertEquals(old.digest(), WireJson.decodeFromString<RootManagedRunTicket>(json.toString()).digest())
        assertFails { old.copy(action = "spawn").validate() }
        assertFails { ticket.copy(target = old.target).validate() }
    }
    @Test fun packageLaunchCannotSmuggleAnExistingTargetOrShellArguments() {
        val args = objectOf("action" to string("spawn"), "package" to string("io.aurora.fixture"),
            "source" to string("conversation"), "path" to string("hook.js"), "scriptSha256" to string("a".repeat(64)))
        assertNull(FridaAttachRequest.parse(args).target)
        for ((key, value) in listOf("pid" to JsonPrimitive(90), "package" to string("/system/bin/sh"), "package" to string("io.fixture;id")))
            assertFails { FridaAttachRequest.parse(JsonObject(args + (key to value))) }
    }
}
