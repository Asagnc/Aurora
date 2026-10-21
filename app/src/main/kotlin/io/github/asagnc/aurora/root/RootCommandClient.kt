package io.github.asagnc.aurora.root

import android.os.IBinder
import android.content.Context
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import io.github.asagnc.aurora.runtime.CommandWakeLock
import java.util.UUID
import kotlinx.coroutines.*

internal class RootCommandClient(private val remote: IRootControl, context: Context) {
    private val awake = CommandWakeLock(context, "RootCommand")
    suspend fun execute(conversation: String, request: CommandRequest): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val wire = RootCommandWire.encode(request)
        RootCommandWire.decode(wire) // Reject over-budget input before a Binder transaction.
        val result = CompletableDeferred<String>()
        val death = IBinder.DeathRecipient { result.complete(uncertain("Root service exited")) }
        val binder = remote.asBinder()
        val lease = awake.acquire(request.timeoutMs + 15_000)
        try {
            binder.linkToDeath(death, 0)
            remote.start(id, conversation, wire, object : IRootResult.Stub() {
                override fun complete(value: String) { result.complete(value) }
            })
            withTimeoutOrNull(request.timeoutMs + 10_000) { result.await() }
                ?: uncertain("Root response deadline exceeded")
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { uncertain(failure.javaClass.simpleName) }
        finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.cancelAndWait(id) } }
                runCatching { binder.unlinkToDeath(death, 0) }
            } finally { lease.close() }
        }
    }

    private fun uncertain(reason: String) = objectOf("error" to string("command_outcome_uncertain"),
        "backend" to string("android-root"), "reason" to string(reason)).toString()
}
