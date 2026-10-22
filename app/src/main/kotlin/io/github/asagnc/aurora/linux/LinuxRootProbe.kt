package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Root capabilities only; no rootfs bind, chroot, or business command is attempted. */
internal class LinuxRootProbe(private val app: AuroraApplication) {
    suspend fun inspect(owner: String, artifact: String, recover: Boolean = false): JsonObject {
        val client = requireNotNull(app.root.commandClient) { "Connect Root before probing task scopes" }
        val helper = Path.of(app.applicationInfo.nativeLibraryDir, "libaurora_linux_scope.so")
        val state = withContext(Dispatchers.IO) {
            app.filesDir.toPath().resolve("spaces/resources").toRealPath().resolve("linux-task-state")
        }
        val result = WireJson.parseToJsonElement(client.execute(owner,
            CommandRequest(listOf(helper.toString(), if (recover) "--recover" else "--probe", "--state-dir", state.toString()),
                timeoutMs = 30_000), artifact)).jsonObject
        val observed = result["stdout"]?.jsonObject?.text("text")?.let { text ->
            runCatching { WireJson.parseToJsonElement(text).jsonObject }.getOrNull()
        }
        val passed = result.number("exitCode") == 0L && result["error"] == null && observed?.number("protocol") == 1L &&
            (if (recover) observed["recovered"] == JsonPrimitive(true) else
                listOf("privateMountNamespace", "taskCgroup", "pidfdObserved", "subreaperDrained", "populatedThenEmpty", "scopeRemoved")
                    .all { observed[it] == JsonPrimitive(true) })
        return objectOf("backend" to string("root-task-scope"), "workloadPassed" to JsonPrimitive(passed),
            "pidNamespaceRequired" to JsonPrimitive(false),
            "recoveryOnly" to JsonPrimitive(recover),
            "scope" to string("Fixed task-scope qualification/recovery only; guest image mounts and command execution are validated separately by command_execute_linux."),
            "executionReady" to JsonPrimitive(false), "observation" to (observed ?: JsonNull), "result" to result)
    }
}
