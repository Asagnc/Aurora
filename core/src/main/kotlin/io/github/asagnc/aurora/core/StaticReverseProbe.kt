package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

data class StaticReverseProbeContext(val step: Int, val runId: String, val generation: String)

/** Startup qualification only; not proof that arbitrary APKs can be decompiled completely. */
fun staticReverseProbePassed(context: StaticReverseProbeContext, proof: JsonObject): Boolean {
    val done = proof["completion"] as? JsonObject ?: return false
    val result = proof["result"] as? JsonObject ?: return false
    val text = (result["stdout"] as? JsonObject)?.text("text").orEmpty().trim()
    val version = when (context.step) {
        0 -> text.lineSequence().any { it.startsWith("openjdk 27 ") || it.startsWith("openjdk 27+") }
        1 -> text == "1.5.6"
        2 -> text == "3.0.3"
        else -> false
    }
    return proof.number("protocol") == 1L && proof.text("runId") == context.runId && proof.text("generation") == context.generation &&
        proof.text("toolBundle") == StaticReverseTools.bundleId && proof["stopped"] == JsonPrimitive(true) &&
        done.number("exitCode") == 0L && done["scopeRemoved"] == JsonPrimitive(true) &&
        done["interrupted"] == JsonPrimitive(false) && done["timedOut"] == JsonPrimitive(false) && version
}
