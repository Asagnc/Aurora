package io.github.asagnc.aurora.core

import kotlin.test.*

class FridaManagedProbeTest {
    private val root = LinuxRootIdentity(10, 20, "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004")
    private val bridge = RootScriptSnapshot("bridge", "a".repeat(64), 100)
    private fun ticket(java: Boolean) = RootManagedRunTicket("00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002", root, 10000, "41", "b".repeat(64),
        RootTargetIdentity(root.pid, root.startTicks), FridaManagedProbe.snapshot(java), if (java) bridge else null, 10_000)

    @Test fun selfProbeCannotQualifyAnotherTargetOrDifferentCapturedBytes() {
        for (java in listOf(false, true)) {
            val ticket = ticket(java)
            ticket.validate(); assertTrue(FridaManagedProbe.matches(ticket, root, bridge))
            assertFalse(FridaManagedProbe.matches(ticket.copy(target = RootTargetIdentity(11, 20)), root, bridge))
            assertFalse(FridaManagedProbe.matches(ticket.copy(script = ticket.script.copy(sha256 = "c".repeat(64))), root, bridge))
            assertFalse(FridaManagedProbe.matches(ticket.copy(observeMs = 1), root, bridge))
        }
        assertFalse(FridaManagedProbe.matches(ticket(true), root, bridge.copy(sha256 = "d".repeat(64))))
        assertNotEquals(FridaManagedProbe.snapshot(false).sha256, FridaManagedProbe.snapshot(true).sha256)
    }

    @Test fun javaQualificationCannotBeClaimedWithoutItsNativePrerequisite() {
        FridaManagedPackage("41", "a".repeat(64), bridge, true, true).validate()
        assertFails { FridaManagedPackage("41", "a".repeat(64), bridge, false, true).validate() }
        assertFails { FridaManagedPackage("0", "a".repeat(64), bridge, false, false).validate() }
    }
}
