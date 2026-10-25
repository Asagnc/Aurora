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

    suspend fun packageInfo(identity: LinuxRootIdentity): FridaManagedPackage = withContext(Dispatchers.IO) {
        val snapshot = remote.snapshot()
        require(snapshot.getInt("uid", -1) == 0 && snapshot.getInt("pid", -1) == identity.pid && snapshot.getInt("managedRunProtocol") == 1)
        val encoded = requireNotNull(snapshot.getString("fridaPackage")) { "ARM64 Frida package unavailable; reconnect the current Root service" }
        require(encoded.length <= 8192)
        WireJson.decodeFromString<FridaManagedPackage>(encoded).also { it.validate() }
    }

    suspend fun recover(ticket: RootManagedRunTicket): RootManagedResult = invoke(ticket, null)

    /** Original snapshots stay with the caller; only per-call duplicate handles cross Binder. */
    suspend fun start(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>): RootManagedResult = invoke(ticket, snapshots)

    private suspend fun invoke(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>?): RootManagedResult = withContext(Dispatchers.IO) {
        ticket.validate()
        val request = Bundle().apply { putString("ticket", WireJson.encodeToString(ticket).also { require(it.length <= 8192) }) }
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.completeExceptionally(IllegalStateException("Root service exited")) }
        val budget = if (snapshots == null) 55_000L else ticket.timeoutMs + 40_000
        val lease = awake.acquire(budget + 5_000)
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
            WireJson.decodeFromString<RootManagedResult>(encoded).also { it.validate(ticket) }
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.cancelAndWait(ticket.runId) } }
                runCatching { binder.unlinkToDeath(death, 0) }
            } finally { lease.close() }
        }
    }
}

internal data class FridaManagedConnection(val identity: LinuxRootIdentity, val client: RootManagedClient)
