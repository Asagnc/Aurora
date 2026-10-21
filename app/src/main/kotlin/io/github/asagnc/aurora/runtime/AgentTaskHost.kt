package io.github.asagnc.aurora.runtime

import android.content.Context
import android.content.Intent
import android.os.Looper
import java.util.UUID
import kotlinx.coroutines.*

/** Single admission lane. All state transitions are serialized on the main dispatcher. */
internal class AgentTaskHost(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private data class Active(val id: String, val owner: String, val ready: CompletableDeferred<Unit>, val job: Job)
    private var active: Active? = null
    val busy get() = active != null
    internal val activeId get() = active?.id
    internal val owner get() = active?.owner

    fun start(owner: String, execute: suspend () -> Unit, finished: suspend (Throwable?) -> Unit, id: String = UUID.randomUUID().toString()): Boolean {
        checkMain()
        if (busy) return false
        val ready = CompletableDeferred<Unit>()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var failure: Throwable? = null
            try {
                check(withTimeoutOrNull(10_000) { ready.await(); true } == true) { "Task service startup timed out" }
                execute()
            }
            catch (caught: Throwable) { failure = caught }
            finally {
                withContext(NonCancellable) {
                    try { finished(failure) }
                    finally {
                        if (active?.id == id) {
                            active = null
                            AgentTaskService.refresh(context)
                        }
                    }
                }
            }
        }
        active = Active(id, owner, ready, job)
        // Start the coroutine before any stop can cancel it, so its cleanup is always installed.
        job.start()
        try {
            context.startForegroundService(Intent(context, AgentTaskService::class.java).putExtra("run", id))
        } catch (failure: Exception) { ready.completeExceptionally(failure) }
        return true
    }

    internal fun serviceReady(id: String): Boolean {
        checkMain()
        val task = active?.takeIf { it.id == id } ?: return false
        return task.ready.complete(Unit) || task.ready.isCompleted
    }

    internal fun serviceFailed(id: String, failure: Exception) {
        checkMain()
        active?.takeIf { it.id == id }?.ready?.completeExceptionally(failure)
    }

    internal fun serviceLost(id: String) {
        checkMain()
        if (active?.id == id) active?.job?.cancel(CancellationException("Task service stopped"))
    }

    fun stop(id: String? = null) {
        checkMain()
        active?.takeIf { id == null || it.id == id }?.job?.cancel()
    }

    suspend fun stopAndJoin() {
        checkMain()
        active?.job?.let { it.cancelAndJoin() }
    }

    private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) }
}
