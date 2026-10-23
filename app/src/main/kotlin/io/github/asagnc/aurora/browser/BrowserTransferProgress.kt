package io.github.asagnc.aurora.browser

import android.os.SystemClock

internal data class BrowserTransferProgress(val owner: String, val page: String, val direction: String, val bytes: Long)

/** Main-thread updates, coalesced without timers or polling. Final result persists in the tool ledger. */
internal class BrowserProgress(private val browser: BrowserController, private val id: String,
    private val initial: BrowserTransferProgress) : AutoCloseable {
    private val started = SystemClock.elapsedRealtime()
    private var last = started
    init { browser.transfers[id] = initial }
    fun update(bytes: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now-last >= 200) { browser.transfers[id] = initial.copy(bytes = bytes); last = now }
    }
    val elapsedMs: Long get() = SystemClock.elapsedRealtime()-started
    override fun close() { browser.transfers.remove(id) }
}
