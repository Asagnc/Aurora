package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandWakeLock
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString

internal class RootManagedClient(private val remote: IRootControl, context: Context) {
    private val awake = CommandWakeLock(context, "ManagedRecovery")

    /** Explicit native settings/test entry only; Agent definitions expose no policy mutation. */
    suspend fun policy(identity: LinuxRootIdentity, activate: Boolean, qualification: Boolean = false): kotlinx.serialization.json.JsonObject = withContext(Dispatchers.IO) {
        identity.validate()
        require(!activate || FridaPolicyAvailability.releaseQualified || qualification) { "策略启用仍在设备验收，尚未开放" }
        val snapshot = remote.snapshot()
        require(snapshot.getInt("uid", -1) == 0 && snapshot.getInt("pid", -1) == identity.pid &&
            snapshot.getInt("fridaPolicyProtocol") == 1) { "请重新连接支持策略验证的当前 Root 服务" }
        val id = java.util.UUID.randomUUID().toString()
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.completeExceptionally(IllegalStateException("Root service exited")) }
        val lease = awake.acquire(90_000)
        try {
            binder.linkToDeath(death, 0)
            remote.fridaPolicy(id, Bundle().apply {
                putString("identity", WireJson.encodeToString(identity)); putBoolean("activate", activate)
                putBoolean("qualification", qualification)
            }, object : IRootResult.Stub() { override fun complete(value: String) { result.complete(value) } })
            val value = withTimeout(80_000) { result.await() }.also { require(it.length <= 8192) }
            val response = WireJson.parseToJsonElement(value) as kotlinx.serialization.json.JsonObject
            require(response["error"] == null) { "策略资格未确认；未回滚全局策略，请检查当前状态" }
            require(WireJson.decodeFromJsonElement(LinuxRootIdentity.serializer(), requireNotNull(response["identity"])) == identity)
            require(packageInfo(identity).workerSha256 == (response["workerSha256"] as? kotlinx.serialization.json.JsonPrimitive)?.content || !activate)
            response
        } finally {
            try { withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.cancelAndWait(id) } } }
            finally { runCatching { binder.unlinkToDeath(death, 0) }; lease.close() }
        }
    }

    suspend fun packageInfo(identity: LinuxRootIdentity): FridaManagedPackage = withContext(Dispatchers.IO) {
        val snapshot = remote.snapshot()
        require(snapshot.getInt("uid", -1) == 0 && snapshot.getInt("pid", -1) == identity.pid && snapshot.getInt("managedRunProtocol") == 1)
        val encoded = requireNotNull(snapshot.getString("fridaPackage")) { "ARM64 Frida package unavailable; reconnect the current Root service" }
        require(encoded.length <= 8192)
        WireJson.decodeFromString<FridaManagedPackage>(encoded).also { it.validate() }
    }

    suspend fun recover(ticket: RootManagedRunTicket, discardCapture: Boolean = false): RootManagedResult = invoke(ticket, null, discardCapture)

    suspend fun capturePackage(identity: LinuxRootIdentity): RootCapturePackage = withContext(Dispatchers.IO) {
        val snapshot = remote.snapshot()
        require(snapshot.getInt("uid", -1) == 0 && snapshot.getInt("pid", -1) == identity.pid && snapshot.getInt("managedRunProtocol") == 1)
        val encoded = requireNotNull(snapshot.getString("capturePackage")) { "Packaged rotation worker unavailable in this Root service" }
        require(encoded.length <= 1024)
        WireJson.decodeFromString<RootCapturePackage>(encoded).also { it.validate() }
    }

    /** Original snapshots stay with the caller; only per-call duplicate handles cross Binder. */
    suspend fun start(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>): RootManagedResult = invoke(ticket, snapshots)

    private suspend fun invoke(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>?, discardCapture: Boolean = false): RootManagedResult = withContext(Dispatchers.IO) {
        ticket.validate()
        val request = Bundle().apply { putString("ticket", WireJson.encodeToString(ticket).also { require(it.length <= 8192) }) }
        if (discardCapture) { require(snapshots == null && ticket.kind == "capture"); request.putBoolean("discardCapture", true) }
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.completeExceptionally(IllegalStateException("Root service exited")) }
        val budget = if (snapshots == null) 55_000L else ticket.timeoutMs + 40_000
        val lease = if (ticket.kind == "capture") awake.acquireCapture(budget + 5_000) else awake.acquire(budget + 5_000)
        try {
            binder.linkToDeath(death, 0)
            val callback = object : IRootResult.Stub() {
                override fun complete(value: String) { result.complete(value) }
            }
            if (snapshots == null) remote.recoverManagedRun(ticket.runId, request, callback)
            else {
                require(snapshots.size == if (ticket.bridge == null) 1 else 2)
                val owned = RootManagedWire.snapshots(ticket, snapshots.first(), snapshots.getOrNull(1))
                try { remote.startManagedRun(ticket.runId, request, owned.first(), owned.getOrNull(1), callback) }
                finally { RootManagedWire.close(owned) }
            }
            val encoded = withTimeout(budget) { result.await() }.also { require(it.length <= 8192) }
            decodeRootManagedResponse(encoded, ticket)
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.cancelAndWait(ticket.runId) } }
                runCatching { binder.unlinkToDeath(death, 0) }
            } finally { lease.close() }
        }
    }
}

internal data class RootManagedConnection(val identity: LinuxRootIdentity, val client: RootManagedClient)
