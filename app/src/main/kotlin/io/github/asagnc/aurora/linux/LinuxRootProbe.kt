package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.Path
import kotlinx.serialization.json.*

/** Root capabilities only; no rootfs bind, chroot, or business command is attempted. */
internal class LinuxRootProbe(private val app: AuroraApplication) {
    suspend fun inspect(owner: String, artifact: String): JsonObject {
        val client = requireNotNull(app.root.commandClient) { "Connect Root before probing namespaces" }
        val helper = Path.of(app.applicationInfo.nativeLibraryDir, "libaurora_linux_namespace.so")
        val result = WireJson.parseToJsonElement(client.execute(owner,
            CommandRequest(listOf(helper.toString(), "--probe"), timeoutMs = 10_000), artifact)).jsonObject
        val observed = result["stdout"]?.jsonObject?.text("text")?.let { text ->
            runCatching { WireJson.parseToJsonElement(text).jsonObject }.getOrNull()
        }
        val passed = result.number("exitCode") == 0L && result["error"] == null && observed?.number("protocol") == 1L &&
            listOf("privateMountNamespace", "privatePidNamespace", "detachedWorkerStarted", "namespaceInitReaped")
                .all { observed[it] == JsonPrimitive(true) }
        return objectOf("backend" to string("root-namespaces"), "workloadPassed" to JsonPrimitive(passed),
            "executionReady" to JsonPrimitive(false), "observation" to (observed ?: JsonNull), "result" to result)
    }
}
