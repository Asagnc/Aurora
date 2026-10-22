package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

internal class LinuxEnvironmentTool(private val manager: LinuxEnvironmentManager, private val owner: String,
    private val prepared: suspend (String, String) -> Unit) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("linux_environment",
        "Inspect, install, probe or clean shared Linux base images. action=status/install/probe/probe_root/recover_root/recover_runs/cleanup. install backend=auto/root/proot selects ownership; auto prefers an explicitly connected Root service. Root and PRoot generations are shared separately; opting into both uses additional storage. probe runs a fixed offline PRoot workload. probe_root qualifies connected Root task cgroup/mount/pidfd/subreaper/exit events. recover_root recovers only verified Root-owned scope records whose guard has exited; live or ambiguous records are retained. recover_runs joins or proves old dispatchers gone, recovers per-run scopes, restores conversation file access and releases only confirmed run leases. It never replays guest commands. No resource policy changes. PID namespaces are optional. Root batch execution uses command_execute_linux; installation and primitive probes alone do not guarantee device capability.",
        objectOf("type" to string("object"), "properties" to objectOf("action" to objectOf("type" to string("string"),
            "enum" to JsonArray(listOf("status", "install", "probe", "probe_root", "recover_root", "recover_runs", "cleanup").map(::string))),
            "backend" to objectOf("type" to string("string"), "enum" to JsonArray(listOf("auto", "root", "proot").map(::string)))), "required" to JsonArray(listOf(string("action"))),
            "additionalProperties" to JsonPrimitive(false))))
    override suspend fun execute(call: ToolCall): String = try {
        require(call.name == "linux_environment")
        when (call.arguments.text("action")) {
            "install" -> {
                val backend = when (call.arguments.text("backend")) {
                    null, "auto" -> null; "root" -> LinuxBackend.CHROOT; "proot" -> LinuxBackend.PROOT
                    else -> error("Unknown Linux backend")
                }
                manager.install(backend); status()
            }
            "cleanup" -> objectOf("removedVersions" to JsonPrimitive(manager.cleanup()))
            "probe" -> manager.probe(owner)
            "probe_root", "recover_root" -> {
                val artifact = java.util.UUID.randomUUID().toString()
                prepared(call.id, "runs/$artifact")
                if (call.arguments.text("action") == "recover_root") manager.recoverRoot(owner, artifact)
                else manager.probeRoot(owner, artifact)
            }
            "recover_runs" -> manager.runs.recover()
            "status" -> status()
            else -> error("Unknown Linux environment action")
        }.toString()
    } catch (cancel: CancellationException) { throw cancel }
    catch (failure: Exception) { objectOf("error" to string(if (failure is LinuxInstallCapabilityException)
            "linux_hardlinks_unavailable" else "linux_environment_unavailable"),
        "hint" to string(if (failure is LinuxInstallCapabilityException)
            "This UID cannot create rootfs hardlinks. Explicitly connect Root before installing; no image was downloaded or copied as a substitute."
            else "Inspect the requested backend, Root connection/protocol, ABI, storage and image integrity. Installed base images do not imply an executable Linux backend.")).toString() }
    private suspend fun status(): JsonObject = objectOf("stage" to string(manager.status.value), "executionTool" to string("command_execute_linux"), "executionRequires" to string("Connected Root, published Root generation and supported native task scopes; each dispatch validates capabilities"),
        "pendingRuns" to JsonPrimitive(manager.runs.pendingCount()),
        "current" to (manager.registry.current()?.let(::string) ?: JsonNull),
        "versions" to JsonArray(manager.registry.versions().map { version -> objectOf("id" to string(version.id),
            "distribution" to string(version.manifest.image.distribution), "version" to string(version.manifest.image.version),
            "abi" to string(version.manifest.image.architecture.abi), "backend" to string(if (version.manifest.backend == LinuxBackend.CHROOT) "root" else "proot"),
            "rootRequiredForCleanup" to JsonPrimitive(version.manifest.backend == LinuxBackend.CHROOT),
            "bytes" to JsonPrimitive(version.manifest.expandedBytes)) }))
}
