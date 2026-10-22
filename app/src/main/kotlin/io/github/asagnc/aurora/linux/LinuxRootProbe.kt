package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.Path
import kotlinx.serialization.json.*

/** Root capabilities only; no rootfs bind, chroot, or business command is attempted. */
internal class LinuxRootProbe(private val app: AuroraApplication) {
    suspend fun inspect(owner: String, artifact: String): JsonObject {
        val client = requireNotNull(app.root.commandClient) { "Connect Root before probing task scopes" }
        val helper = Path.of(app.applicationInfo.nativeLibraryDir, "libaurora_linux_scope.so")
        val result = WireJson.parseToJsonElement(client.execute(owner,
            CommandRequest(listOf(helper.toString(), "--probe"), timeoutMs = 20_000), artifact)).jsonObject
        val observed = result["stdout"]?.jsonObject?.text("text")?.let { text ->
            runCatching { WireJson.parseToJsonElement(text).jsonObject }.getOrNull()
        }
        val passed = result.number("exitCode") == 0L && result["error"] == null && observed?.number("protocol") == 1L &&
            listOf("privateMountNamespace", "taskCgroup", "pidfdObserved", "subreaperDrained", "populatedThenEmpty", "scopeRemoved")
                .all { observed[it] == JsonPrimitive(true) }
        return objectOf("backend" to string("root-task-scope"), "workloadPassed" to JsonPrimitive(passed),
            "pidNamespaceRequired" to JsonPrimitive(false),
            "scope" to string("Fixed task-scope qualification only. Guard-force-kill recovery, chroot and arbitrary Linux execution are not connected."),
            "executionReady" to JsonPrimitive(false), "observation" to (observed ?: JsonNull), "result" to result)
    }
}
