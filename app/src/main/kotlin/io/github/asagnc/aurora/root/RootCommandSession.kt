package io.github.asagnc.aurora.root

import android.os.IBinder
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*

/** One bound client lifetime; no command survives close or callback Binder death. */
internal class RootCommandSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = mutableMapOf<String, Job>()
    private var closed = false

    @Synchronized fun start(id: String, callback: IRootResult, release: () -> Unit = {}, execute: suspend () -> String) {
        require(java.util.UUID.fromString(id).toString() == id)
        check(!closed && tasks.size < 2 && id !in tasks) { "Root session closed, busy or duplicate command" }
        val token = callback.asBinder()
        lateinit var job: Job
        val death = IBinder.DeathRecipient { job.cancel() }
        job = scope.launch(start = CoroutineStart.LAZY) {
            val result = try { execute() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                objectOf("error" to string("command_outcome_uncertain"), "reason" to string(failure.javaClass.simpleName)).toString()
            }
            runCatching { callback.complete(result) }
        }
        // Register before linking: death or cancellation may occur before launch starts.
        tasks[id] = job
        job.invokeOnCompletion { failure ->
            runCatching { release() }
            runCatching { token.unlinkToDeath(death, 0) }
            if (failure is CancellationException) runCatching {
                callback.complete(objectOf("error" to string("command_outcome_uncertain"),
                    "reason" to string("Root command cancelled; inspect artifacts before retrying")).toString())
            }
            synchronized(this) { tasks.remove(id, job) }
        }
        try { token.linkToDeath(death, 0); job.start() }
        catch (failure: Exception) { job.cancel(); runCatching { token.unlinkToDeath(death, 0) }; throw failure }
    }

    suspend fun cancelAndWait(id: String) {
        val job = synchronized(this) { tasks[id] }
        job?.cancelAndJoin()
    }

    suspend fun close() {
        val pending = synchronized(this) { closed = true; tasks.values.toList() }
        pending.forEach { it.cancel() }
        pending.joinAll()
        scope.cancel()
    }
}
