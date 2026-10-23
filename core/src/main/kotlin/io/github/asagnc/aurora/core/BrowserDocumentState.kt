package io.github.asagnc.aurora.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select

/** Navigation state, not a DOM mutation counter or proof that web content is trusted. */
data class BrowserDocumentState(val revision: Long = 0, val loading: Boolean = false,
    val failure: String? = null, val closed: Boolean = false)

suspend fun awaitBrowserLoad(states: StateFlow<BrowserDocumentState>, timeoutMs: Long = 25000) {
    withTimeout(timeoutMs) {
        val state = states.first { check(!it.closed) { "Page was closed" }; !it.loading || it.failure != null }
        check(state.failure == null) { "Page failed: ${state.failure}" }
    }
}

/** Reject late old-document callbacks and cancel their local waiters, without replaying scripts. */
suspend fun <T> awaitBrowserDocumentResult(states: StateFlow<BrowserDocumentState>, revision: Long,
    result: suspend () -> T): T = coroutineScope {
    fun current() = states.value.let { !it.closed && !it.loading && it.failure == null && it.revision == revision }
    check(current()) { "Page changed; inspect it before retrying" }
    val changed = async(start = CoroutineStart.UNDISPATCHED) {
        states.first { it.closed || it.loading || it.failure != null || it.revision != revision }
    }
    val pending = async(start = CoroutineStart.UNDISPATCHED) {
        check(current()) { "Page changed before script dispatch" }; result()
    }
    try {
        select {
            changed.onAwait { error("Page changed while script was running; outcome unknown, inspect before retrying") }
            pending.onAwait { check(current()) { "Page changed; script outcome unknown" }; it }
        }
    } finally { changed.cancel(); pending.cancel() }
}
