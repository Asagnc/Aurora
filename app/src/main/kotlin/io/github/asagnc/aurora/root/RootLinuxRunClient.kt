package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandWakeLock
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal class RootLinuxRunClient(private val remote: IRootControl, context: Context) {
    private val awake = CommandWakeLock(context, "LinuxRun")
    suspend fun invoke(ticket: LinuxRunTicket, recover: Boolean): JsonObject = withContext(Dispatchers.IO) {
        ticket.validate()
        val encoded = WireJson.encodeToString(ticket).also { require(it.length <= 262144) }
        val request = Bundle().apply { putString("ticket", encoded) }
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.completeExceptionally(IllegalStateException("Root Linux service exited")) }
        val lease = awake.acquire(320_000)
        try {
            binder.linkToDeath(death, 0)
            val callback = object : IRootResult.Stub() { override fun complete(value: String) { result.complete(value) } }
            if (recover) remote.recoverLinuxRun(ticket.runId, request, callback)
            else remote.startLinuxRun(ticket.runId, request, callback)
            val value = withTimeout(310_000) { result.await() }.also { require(it.length <= 262144) }
            WireJson.parseToJsonElement(value).jsonObject
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.cancelAndWait(ticket.runId) } }
                runCatching { binder.unlinkToDeath(death, 0) }
            } finally { lease.close() }
        }
    }
}
