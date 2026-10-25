package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*
import kotlin.test.*

class FridaProbeEvidenceTest {
    private val root = LinuxRootIdentity(10, 20, "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004")
    private fun ticket(java: Boolean) = RootManagedRunTicket("00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002", root, 10000, "41", "a".repeat(64), RootTargetIdentity(10, 20),
        FridaManagedProbe.snapshot(java), if (java) RootScriptSnapshot("bridge", "b".repeat(64), 100) else null, 10_000)
    private fun message(kind: String, pid: JsonPrimitive = JsonPrimitive(10)): String {
        val message = objectOf("type" to string("send"), "payload" to objectOf("aurora" to string(kind), "pid" to pid))
        return objectOf("protocol" to JsonPrimitive(1), "kind" to string("message"), "message" to string(message.toString())).toString()
    }

    @Test fun bridgePostOrWorkerSuccessAloneDoesNotQualifyTheActualProbe() {
        val native = message("native_hook_observed")
        val java = message("java_call_observed")
        assertTrue(FridaProbeEvidence.observed(ticket(false), native))
        assertTrue(FridaProbeEvidence.observed(ticket(true), "$native\n$java"))
        assertFalse(FridaProbeEvidence.observed(ticket(true), native))
        assertFalse(FridaProbeEvidence.observed(ticket(false), message("user_script_evaluated")))
        assertFalse(FridaProbeEvidence.observed(ticket(false), "{\"success\":true}"))
    }

    @Test fun duplicateForeignIdentityAndMutableUserScriptEvidenceAreRejected() {
        val native = message("native_hook_observed")
        assertFalse(FridaProbeEvidence.observed(ticket(false), "$native\n$native"))
        assertFalse(FridaProbeEvidence.observed(ticket(false), message("native_hook_observed", JsonPrimitive(11))))
        assertFalse(FridaProbeEvidence.observed(ticket(false), message("native_hook_observed", string("10"))))
        assertFalse(FridaProbeEvidence.observed(ticket(false).copy(script = RootScriptSnapshot("selected", "c".repeat(64), 1)), native))
        assertFalse(FridaProbeEvidence.observed(ticket(false), "x".repeat(131073)))
    }
}
