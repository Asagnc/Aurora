package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandWakeLock
import kotlinx.coroutines.*

internal class RootPacketClient(private val remote: IRootControl, context: Context) {
    private val awake = CommandWakeLock(context, "PacketCapture")
    suspend fun execute(owner: String, id: String, request: PacketCaptureRequest): String = withContext(Dispatchers.IO) {
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.complete(uncertain("Root service exited")) }
        val lease = awake.acquire(request.timeoutMs + 15_000)
        try {
            binder.linkToDeath(death, 0)
            remote.capturePackets(id, owner, Bundle().apply { putString("arguments", request.arguments().toString()) },
                object : IRootResult.Stub() { override fun complete(value: String) {
                    if (value.length <= 32768) result.complete(value)
                    else result.complete(uncertain("Root response exceeds budget"))
                } })
            withTimeoutOrNull(request.timeoutMs + 10_000) { result.await() } ?: uncertain("Root response deadline exceeded")
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { uncertain(failure.javaClass.simpleName) }
        finally {
            try { withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.cancelAndWait(id) } } }
            finally { runCatching { binder.unlinkToDeath(death, 0) }; lease.close() }
        }
    }
    private fun uncertain(reason: String) = objectOf("error" to string("capture_outcome_uncertain"),
        "backend" to string("android-root"), "reason" to string(reason)).toString()
}
