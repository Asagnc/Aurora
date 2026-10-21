package io.github.asagnc.aurora.core

import java.io.InputStream
import java.nio.file.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*

/** Closing the owned stream complements interruption for providers whose read ignores thread interrupts. */
object SkillArchiveTransfer {
    suspend fun copy(target: Path, open: () -> InputStream, cancelOpen: () -> Unit = {}) = coroutineScope {
        val input = AtomicReference<InputStream?>()
        val cancelled = AtomicBoolean(false)
        val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally {
                cancelled.set(true)
                runCatching { cancelOpen() }
                runCatching { input.getAndSet(null)?.close() }
            }
        }
        try {
            runInterruptible(Dispatchers.IO) {
                open().use { source ->
                    input.set(source)
                    if (cancelled.get()) throw InterruptedException("Skill import cancelled")
                    Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { output ->
                        val bytes = ByteArray(65536); var total = 0L
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = source.read(bytes); if (count < 0) break
                            total += count; require(total <= 64L * 1024 * 1024) { "Skill ZIP exceeds 64 MiB" }
                            output.write(bytes, 0, count)
                        }
                    }
                }
            }
        } catch (failure: Exception) {
            // Some streams report an IOException when closed to cancel a blocked read.
            currentCoroutineContext().ensureActive()
            throw failure
        } finally { withContext(NonCancellable) { closer.cancelAndJoin() } }
    }
}
