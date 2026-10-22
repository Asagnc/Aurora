package io.github.asagnc.aurora.root

import android.content.Context
import android.os.IBinder
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandWakeLock
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class RootLinuxClient(private val remote: IRootControl, val identity: LinuxRootIdentity, context: Context,
    val protocol: Int = 1) {
    val supportsRootImages get() = protocol >= 2
    private val awake = CommandWakeLock(context, "LinuxPreparation")

    /** onStopped is called only after the Root worker has been synchronously joined. */
    suspend fun prepare(operation: String, onStopped: () -> Unit): JsonObject = withContext(Dispatchers.IO) {
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.completeExceptionally(IllegalStateException("Root preparation service exited")) }
        val lease = awake.acquire(310_000)
        try {
            binder.linkToDeath(death, 0)
            remote.prepareLinux(operation, object : IRootResult.Stub() {
                override fun complete(value: String) { result.complete(value) }
            })
            val encoded = withTimeout(300_000) { result.await() }.also { require(it.length <= 4096) }
            val response = WireJson.parseToJsonElement(encoded).jsonObject
            require(response.number("protocol") == 1L && response["prepared"] == JsonPrimitive(true)) {
                "Root preparation failed: ${response.text("reason") ?: response.text("error") ?: "invalid response"}"
            }
            response
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    // Failure leaves the on-disk ticket in quarantine. Do not pretend
                    // Binder cancellation was acknowledged after transport failure.
                    runCatching { remote.cancelAndWait(operation) }.onSuccess { onStopped() }
                }
                runCatching { binder.unlinkToDeath(death, 0) }
            } finally { if (lease.held) lease.close() }
        }
    }

    suspend fun discard(operation: String) = withContext(Dispatchers.IO) {
        val lease = awake.acquire(30_000)
        try { remote.discardLinux(operation) } finally { if (lease.held) lease.close() }
    }
}
