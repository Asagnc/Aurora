package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.os.Process
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID

internal class FridaAttachment(private val app: AuroraApplication, private val owner: String,
    private val prepared: suspend (String, String) -> Unit) {
    suspend fun run(callId: String, request: FridaAttachRequest?, javaProbe: Boolean = false): RootManagedResult {
        val connection = requireNotNull(app.root.fridaConnection) { "Connect Root before using Frida" }
        val installed = connection.client.packageInfo(connection.identity)
        val java = request?.java ?: javaProbe
        if (request != null) check(if (java) installed.javaProbePassed else installed.nativeProbePassed) {
            "Run frida probe with the matching java setting in this Root service first"
        }
        val bytes = request?.let { selected(it) } ?: FridaManagedProbe.bytes(java)
        val script = if (request == null) FridaManagedProbe.snapshot(java) else
            RootScriptSnapshot("selected-script-v1", request.scriptSha256, bytes.size.toLong())
        val ticket = RootManagedRunTicket(UUID.randomUUID().toString(), owner, connection.identity, Process.myUid(),
            installed.appBuild, installed.workerSha256,
            if (request == null) RootTargetIdentity(connection.identity.pid, connection.identity.startTicks) else request.target, script,
            if (java) installed.bridge else null, request?.observeMs ?: 10_000,
            protocol = if (request?.spawnPackage != null) 2 else 1,
            action = if (request?.spawnPackage != null) "spawn" else "attach", spawnPackage = request?.spawnPackage)
        ticket.validate()
        val descriptors = mutableListOf<ParcelFileDescriptor>()
        try {
            descriptors.add(RootScriptMemory.capture(ByteArrayInputStream(bytes), ticket.script))
            ticket.bridge?.let { descriptors.add(RootScriptMemory.capture(app.assets.open("frida/java-bridge.js"), it)) }
            prepared(callId, "runs/${ticket.runId}")
            return app.managedRuns.execute(ticket, descriptors, connection.client, app.toolRuns.activeId(owner, callId))
        } finally { RootManagedWire.close(descriptors) }
    }

    private suspend fun selected(request: FridaAttachRequest): ByteArray {
        val files = when (request.source) {
            "conversation" -> app.spaces.conversation(owner)
            "resources" -> app.spaces.sharedResources()
            "workspace" -> app.spaces.sharedWorkspace()
            else -> error("Unknown script namespace")
        }
        return files.readBinary(request.path, 1_048_576).also { bytes ->
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            require(sha == request.scriptSha256) { "Selected script changed; read its current version before dispatch" }
        }
    }
}
