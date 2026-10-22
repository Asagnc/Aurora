package io.github.asagnc.aurora.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Counts holders and waiters so idle keys can leave memory without splitting a lock. */
class KeyedMutex {
    private class Entry(val mutex: Mutex = Mutex(), var references: Int = 0)
    private val entries = mutableMapOf<String, Entry>()
    internal val activeKeys: Int get() = synchronized(entries) { entries.size }

    suspend fun <T> withLock(key: String, action: suspend () -> T): T {
        val entry = synchronized(entries) {
            entries.getOrPut(key) { Entry() }.also { it.references++ }
        }
        try { return entry.mutex.withLock { action() } }
        finally { synchronized(entries) {
            entry.references--
            if (entry.references == 0) {
                check(entries[key] === entry)
                entries.remove(key)
            }
        } }
    }
}
