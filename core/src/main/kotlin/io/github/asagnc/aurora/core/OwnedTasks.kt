package io.github.asagnc.aurora.core

import kotlinx.coroutines.*

class OwnedTasks {
    private val lock = Any()
    private val active = mutableMapOf<String, MutableSet<Job>>()
    private val closing = mutableSetOf<String>()

    suspend fun <T> run(owner: String, block: suspend () -> T): T {
        val job = currentCoroutineContext().job
        synchronized(lock) {
            require(owner !in closing) { "Conversation is closing" }
            active.getOrPut(owner) { mutableSetOf() }.add(job)
        }
        try { return block() } finally {
            synchronized(lock) {
                active[owner]?.remove(job)
                if (active[owner]?.isEmpty() == true) active.remove(owner)
            }
        }
    }

    suspend fun quiesce(owner: String) {
        val ownJob = currentCoroutineContext().job
        val jobs = synchronized(lock) {
            closing += owner
            active[owner].orEmpty().toList()
        }
        require(ownJob !in jobs) { "An owned task cannot delete its own active space" }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
    }
}
