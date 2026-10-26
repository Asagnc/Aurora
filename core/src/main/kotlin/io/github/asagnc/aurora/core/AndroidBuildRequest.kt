package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** Literal Gradle argv inside the conversation's already supervised Root Linux workspace. */
data class AndroidBuildRequest(val project: String, val tasks: List<String>) {
    init {
        require(project.length in 1..512 && !project.startsWith('/') &&
            project.split('/').all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it !in setOf(".", "..") })
        require(tasks.size in 1..8 && tasks.all { it.length in 1..120 && it.matches(Regex(":?[A-Za-z][A-Za-z0-9_-]*(?::[A-Za-z][A-Za-z0-9_-]*)*")) })
    }
    val argv get() = listOf(AndroidBuildRuntime.launcherPath, "/mnt/$project") + tasks
}

fun androidBuildProbePassed(context: StaticReverseProbeContext, proof: JsonObject): Boolean {
    val done = proof["completion"] as? JsonObject ?: return false
    val result = proof["result"] as? JsonObject ?: return false
    val output = (result["stdout"] as? JsonObject)?.text("text").orEmpty()
    return proof.number("protocol") == 1L && proof.text("runId") == context.runId && proof.text("generation") == context.generation &&
        proof.text("toolBundle") == AndroidBuildTools.bundleId && proof["stopped"] == JsonPrimitive(true) &&
        done.number("exitCode") == 0L && done["scopeRemoved"] == JsonPrimitive(true) &&
        done["interrupted"] == JsonPrimitive(false) && done["timedOut"] == JsonPrimitive(false) &&
        output.lineSequence().lastOrNull { it.isNotBlank() } == AndroidBuildRuntime.probeMarker
}
