package io.github.asagnc.aurora.core

import kotlinx.coroutines.*
import kotlin.test.*

class KeyedMutexTest {
    @Test fun cancelledWaiterDoesNotSplitTheHeldKeyAndOtherKeysProceed() = runBlocking {
        val locks = KeyedMutex()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) {
            locks.withLock("same") { entered.complete(Unit); release.await() }
        }
        entered.await()
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            locks.withLock("same") { error("Cancelled waiter acquired lock") }
        }
        cancelled.cancelAndJoin()
        assertEquals(1, locks.activeKeys)
        locks.withLock("other") { assertEquals(2, locks.activeKeys) }
        val next = CompletableDeferred<Unit>()
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) { locks.withLock("same") { next.complete(Unit) } }
        assertFalse(next.isCompleted)
        release.complete(Unit)
        holder.join(); waiter.join()
        assertTrue(next.isCompleted)
        assertEquals(0, locks.activeKeys)
    }

    @Test fun completedFailedAndCancelledHoldersReleaseTheirRegistryEntries() = runBlocking {
        val locks = KeyedMutex()
        repeat(1000) { number -> locks.withLock(number.toString()) { assertEquals(1, locks.activeKeys) } }
        assertEquals(0, locks.activeKeys)
        assertFailsWith<IllegalStateException> { locks.withLock("failed") { error("fixture") } }
        assertEquals(0, locks.activeKeys)
        val holder = launch(start = CoroutineStart.UNDISPATCHED) { locks.withLock("cancelled") { awaitCancellation() } }
        holder.cancelAndJoin()
        assertEquals(0, locks.activeKeys)
        locks.withLock("cancelled") { assertEquals(1, locks.activeKeys) }
        assertEquals(0, locks.activeKeys)
    }

    @Test fun concurrentThreadsKeepOneLockPerKeyWhileEntriesComeAndGo() = runBlocking {
        val locks = KeyedMutex()
        val counters = IntArray(7)
        List(8) { worker -> async(Dispatchers.Default) {
            repeat(500) { index ->
                val key = (worker + index) % counters.size
                locks.withLock(key.toString()) {
                    val before = counters[key]
                    yield()
                    counters[key] = before + 1
                }
            }
        } }.awaitAll()
        assertEquals(4000, counters.sum())
        assertEquals(0, locks.activeKeys)
    }
}
