package io.github.asagnc.aurora.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BrowserDocumentStateTest {
    @Test fun loadCompletesFromEventsAndClosedPagesFailImmediately() = runTest {
        val state = MutableStateFlow(BrowserDocumentState(loading = true))
        val loading = async { awaitBrowserLoad(state) }
        testScheduler.runCurrent()
        assertFalse(loading.isCompleted)
        state.value = BrowserDocumentState(revision = 1)
        loading.await()
        state.value = BrowserDocumentState(loading = true, failure = "HTTP 503")
        assertFailsWith<IllegalStateException> { awaitBrowserLoad(state) }
        state.value = BrowserDocumentState(closed = true)
        assertFailsWith<IllegalStateException> { awaitBrowserLoad(state) }
    }

    @Test fun localDeadlineAndParentCancellationRemainDistinct() = runTest {
        val state = MutableStateFlow(BrowserDocumentState(loading = true))
        assertFailsWith<TimeoutCancellationException> { awaitBrowserLoad(state, 100) }
        val cancelled = launch { awaitBrowserLoad(state) }
        testScheduler.runCurrent()
        cancelled.cancelAndJoin()
        assertTrue(cancelled.isCancelled)
    }

    @Test fun navigationCloseAndLoadFailureCancelThePendingResult() = runTest {
        for (replacement in listOf(BrowserDocumentState(revision = 1), BrowserDocumentState(loading = true),
            BrowserDocumentState(closed = true), BrowserDocumentState(failure = "renderer exited"))) {
            supervisorScope {
                val state = MutableStateFlow(BrowserDocumentState())
                var released = false
                val pending = async { awaitBrowserDocumentResult(state, 0) {
                    try { awaitCancellation() } finally { released = true }
                } }
                testScheduler.runCurrent()
                state.value = replacement
                assertFailsWith<IllegalStateException> { pending.await() }
                assertTrue(released)
            }
        }
    }

    @Test fun oldRevisionCannotDispatchAndLateResultsCannotWinNavigation() = runTest {
        val state = MutableStateFlow(BrowserDocumentState(revision = 2))
        var dispatched = false
        assertFailsWith<IllegalStateException> { awaitBrowserDocumentResult(state, 1) { dispatched = true } }
        assertFalse(dispatched)
        assertFailsWith<IllegalStateException> { awaitBrowserDocumentResult(state, 2) {
            state.value = BrowserDocumentState(revision = 3); "late"
        } }
    }

    @Test fun externalDeadlineReleasesLocalWaiterAndNextDocumentStillWorks() = runTest {
        val state = MutableStateFlow(BrowserDocumentState())
        var released = false
        assertFailsWith<TimeoutCancellationException> { withTimeout(100) {
            awaitBrowserDocumentResult(state, 0) { try { awaitCancellation() } finally { released = true } }
        } }
        assertTrue(released)
        assertEquals(42, awaitBrowserDocumentResult(state, 0) { 42 })
    }
}
