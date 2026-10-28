package io.github.asagnc.aurora.core

import java.util.concurrent.TimeUnit

/** Coordinator transport outcome only. Neither its exit nor termination proves target-script cleanup. */
data class RootManagedProcessExit(val code: Int?, val cancelled: Boolean, val forced: Boolean)

/** Blocking owner-thread barrier. The creator must not leave its OS thread while native cleanup runs. */
object RootManagedProcessWait {
    private const val CANCEL_BUDGET_MS = 26_000L
    private const val KILL_ACK_MS = 1_000L

    fun await(process: Process, budgetMs: Long): RootManagedProcessExit {
        require(budgetMs in 1..275_000)
        return wait(process, budgetMs)
    }

    /** Capture keeps its approved 30-minute duration plus native cleanup allowance. */
    fun awaitCapture(process: Process, budgetMs: Long): RootManagedProcessExit {
        require(budgetMs in 1..1_825_000)
        return wait(process, budgetMs)
    }

    private fun wait(process: Process, budgetMs: Long): RootManagedProcessExit {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs)
        var interrupted = Thread.interrupted()
        var cancelled = false
        var forced = false
        try {
            while (true) {
                if (interrupted && !cancelled) {
                    cancelled = true; process.destroy()
                    deadline = minOf(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CANCEL_BUDGET_MS))
                }
                if (!process.isAlive) return RootManagedProcessExit(process.exitValue(), cancelled, forced)
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) {
                    if (forced) return RootManagedProcessExit(null, cancelled, true)
                    // Native deadlines normally finish first. A frozen coordinator is uncertain;
                    // native recover must run before task records can be used as completion facts.
                    forced = true; process.destroyForcibly()
                    deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(KILL_ACK_MS)
                    continue
                }
                try {
                    if (process.waitFor(remaining, TimeUnit.NANOSECONDS))
                        return RootManagedProcessExit(process.exitValue(), cancelled, forced)
                } catch (_: InterruptedException) { interrupted = true }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }
}
