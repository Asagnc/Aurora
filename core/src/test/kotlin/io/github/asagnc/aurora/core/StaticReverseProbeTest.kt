package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*
import kotlin.test.*

class StaticReverseProbeTest {
    private val context = StaticReverseProbeContext(1, "run", "a".repeat(64))
    private fun proof(version: String = "1.5.6") = objectOf("protocol" to JsonPrimitive(1), "runId" to string(context.runId),
        "generation" to string(context.generation), "toolBundle" to string(StaticReverseTools.bundleId), "stopped" to JsonPrimitive(true),
        "completion" to objectOf("exitCode" to JsonPrimitive(0), "scopeRemoved" to JsonPrimitive(true),
            "interrupted" to JsonPrimitive(false), "timedOut" to JsonPrimitive(false)),
        "result" to objectOf("stdout" to objectOf("text" to string(version))))
    @Test fun startupRequiresExpectedVersionAndExactTaskProof() {
        assertTrue(staticReverseProbePassed(context, proof()))
        assertTrue(staticReverseProbePassed(context.copy(step = 0), proof("openjdk 27 2026-09-15\nTemurin")))
        assertTrue(staticReverseProbePassed(context.copy(step = 2), proof("3.0.3")))
        assertFalse(staticReverseProbePassed(context, proof("1.5.5")))
        for ((key, value) in listOf("protocol" to JsonPrimitive(2), "runId" to string("another"),
            "generation" to string("b".repeat(64)), "toolBundle" to string("b".repeat(64)), "stopped" to JsonPrimitive(false)))
            assertFalse(staticReverseProbePassed(context, JsonObject(proof() + (key to value))))
    }
    @Test fun timeoutInterruptedUnconfirmedAndMalformedOutputsNeverQualify() {
        val proof = proof()
        val done = proof.getValue("completion").jsonObject
        for ((key, value) in listOf("exitCode" to JsonPrimitive(1), "scopeRemoved" to JsonPrimitive(false),
            "interrupted" to JsonPrimitive(true), "timedOut" to JsonPrimitive(true))) {
            assertFalse(staticReverseProbePassed(context, JsonObject(proof + ("completion" to JsonObject(done + (key to value))))))
        }
        for (key in listOf("completion", "result"))
            assertFalse(staticReverseProbePassed(context, JsonObject(proof + (key to JsonNull))))
        assertFalse(staticReverseProbePassed(context, JsonObject(proof + ("result" to objectOf("stdout" to JsonNull)))))
    }
}
