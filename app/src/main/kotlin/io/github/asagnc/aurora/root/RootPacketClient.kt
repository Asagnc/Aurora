package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandWakeLock
import kotlinx.coroutines.*

internal class RootPacketClient(private val remote: IRootControl, context: Context) {
    private val awake = CommandWakeLock(context, "PacketCapture")
    suspend fun execute(owner: String, id: String, request: PacketCaptureRequest) = dispatch(owner, id, request.arguments(), request.timeoutMs, false)
    suspend fun inject(owner: String, id: String, request: PacketInjectionRequest) = dispatch(owner, id, request.arguments(), request.timeoutMs, true)

    private suspend fun dispatch(owner: String, id: String, arguments: kotlinx.serialization.json.JsonObject,
        timeoutMs: Long, injection: Boolean): String = withContext(Dispatchers.IO) {
        val error = if (injection) "packet_injection_outcome_uncertain" else "capture_outcome_uncertain"
        val result = CompletableDeferred<String>()
        val binder = remote.asBinder()
        val death = IBinder.DeathRecipient { result.complete(uncertain("Root service exited", error)) }
        val lease = awake.acquire(timeoutMs + 15_000)
        try {
            binder.linkToDeath(death, 0)
            val request = Bundle().apply { putString("arguments", arguments.toString()) }
            val callback = object : IRootResult.Stub() { override fun complete(value: String) {
                    if (value.length <= 32768) result.complete(value)
                    else result.complete(uncertain("Root response exceeds budget", error))
                } }
            if (injection) remote.injectPackets(id, owner, request, callback) else remote.capturePackets(id, owner, request, callback)
            withTimeoutOrNull(timeoutMs + 10_000) { result.await() } ?: uncertain("Root response deadline exceeded", error)
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { uncertain(failure.javaClass.simpleName, error) }
        finally {
            try { withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.cancelAndWait(id) } } }
            finally { runCatching { binder.unlinkToDeath(death, 0) }; lease.close() }
        }
    }
    private fun uncertain(reason: String, code: String) = objectOf("error" to string(code),
        "backend" to string("android-root"), "reason" to string(reason)).toString()
}
