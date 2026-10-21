package io.github.asagnc.aurora.runtime

import android.content.Context
import android.os.PowerManager
import android.os.Process

/** A command owns one bounded lease; model transport and idle Agent time own none. */
internal class CommandWakeLock(context: Context, private val backend: String) {
    private val application = context.applicationContext ?: context
    private val power by lazy { application.getSystemService(PowerManager::class.java) }

    fun acquire(budgetMs: Long): Lease {
        // The application-side Root client holds the lease, attributed to the application UID.
        if (Process.myUid() == 0) return Lease(null)
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Aurora:$backend")
        lock.setReferenceCounted(false)
        lock.acquire(budgetMs.coerceIn(1_000, 315_000))
        return Lease(lock)
    }

    class Lease(private val lock: PowerManager.WakeLock?) : AutoCloseable {
        val held get() = lock?.isHeld == true
        override fun close() { lock?.release() }
    }
}
