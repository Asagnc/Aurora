package io.github.asagnc.aurora.root

import android.os.Process
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal class CaptureRotationTool(private val app: AuroraApplication, private val owner: String,
    private val prepared: suspend (String, String) -> Unit) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("packet_capture_rotate_root",
        "Root-only continuous libpcap capture with whole-packet segment rotation, no VPN or route changes. action=status/probe/start. " +
            "probe explicitly opens loopback for one second with less-1-byte filter, then verifies sealing, publication and actual worker/domain exit. " +
            "A matching probe must pass in the current Root service before start. Numeric BPF addresses/ports avoid resolver side effects. " +
            "start: interface defaults any, filter literal BPF; seconds1..1800 default300, segmentBytes40..67108864 default16777216, " +
            "bytes up to536870912 default67108864, segments1..64 default64, packets1..10000000. Headers count toward byte budgets. " +
            "A packet too large for a segment stops capture without splitting it. Captured bytes do not imply zero kernel loss. " +
            "Completed segments and capture-manifest.json belong to runs/<runId>/segments. Cancellation/crash recovers only registered closed segments. " +
            "No automatic replay. Unconfirmed ownership or failed publication retains an indexed intent; use root_managed_tasks recover. " +
            "Probe scope is this service and loopback, not device-wide throughput/background qualification.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "action" to objectOf("type" to string("string"), "enum" to JsonArray(listOf("status", "probe", "start").map(::string))),
            "interface" to objectOf("type" to string("string")), "filter" to objectOf("type" to string("string")),
            "seconds" to integer(1, 1800), "segmentBytes" to integer(40, 67108864), "bytes" to integer(40, 536870912),
            "segments" to integer(1, 64), "packets" to integer(1, 10000000)),
            "required" to JsonArray(listOf(string("action"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "packet_capture_rotate_root")
        val action = call.arguments.text("action")
        require(action in setOf("status", "probe", "start"))
        if (action != "start") require(call.arguments.keys == setOf("action"))
        if (action == "status") return status().toString()
        val connection = requireNotNull(app.root.managedConnection) { "Connect Root before capture" }
        val installed = connection.client.capturePackage(connection.identity)
        val probe = action == "probe"
        check(probe || installed.probePassed) { "Run packet_capture_rotate_root probe in this Root service first" }
        val options = if (probe) RootCaptureProbe.options else CaptureRotationOptions.parse(JsonObject(call.arguments - "action"))
        val config = RootCaptureConfiguration(CaptureRunBinding(UUID.randomUUID().toString(), owner, connection.identity), options)
        val ticket = RootManagedRunTicket(config.binding.runId, owner, connection.identity, Process.myUid(), installed.appBuild,
            installed.workerSha256, null, config.snapshot(), observeMs = options.seconds * 1000, protocol = 3,
            kind = "capture", action = if (probe) "capture_probe" else "capture")
        config.validateTicket(ticket)
        RootScriptMemory.capture(ByteArrayInputStream(config.encode()), ticket.script).use { snapshot ->
            prepared(call.id, "runs/${ticket.runId}")
            val result = app.managedRuns.execute(ticket, listOf(snapshot), connection.client, app.toolRuns.activeId(owner, call.id))
            return JsonObject(WireJson.parseToJsonElement(WireJson.encodeToString(result)).jsonObject + mapOf(
                "workerSucceeded" to JsonPrimitive(result.successful), "artifactReady" to JsonPrimitive(result.captureArtifact != null))).toString()
        }
    }

    private suspend fun status(): JsonObject {
        val connection = app.root.managedConnection
        val installed = connection?.let { it.client.capturePackage(it.identity) }
        return objectOf("rootConnected" to JsonPrimitive(connection != null), "serviceProbePassed" to JsonPrimitive(installed?.probePassed == true),
            "pending" to JsonPrimitive(app.managedRuns.pendingCount(owner)))
    }

    private fun integer(minimum: Long, maximum: Long) = objectOf("type" to string("integer"),
        "minimum" to JsonPrimitive(minimum), "maximum" to JsonPrimitive(maximum))
}
