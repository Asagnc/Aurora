package io.github.asagnc.aurora.runtime

import android.os.FileObserver
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.nio.file.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.conflate

/** One viewer owns at most three inotify watches; none survive cancellation or leaving the page. */
internal class CommandLogObserver(private val app: AuroraApplication) {
    private val active = AtomicInteger()
    internal val registeredWatches get() = active.get()
    fun observe(owner: String, directory: String, live: Boolean) = flow {
        require(commandArtifactId(directory) != null)
        app.tasks.run(owner) {
            require(app.conversations.header(owner) != null)
            val root = app.spaces.commandRoot(owner)
            val reader = CommandLogTail(root, directory)
            if (!live) { emit(reader.snapshot(complete = true)); return@run }
            val signals = Channel<Unit>(Channel.CONFLATED)
            val watches = mutableMapOf<Path, FileObserver>()
            val structural = AtomicBoolean(false)
            fun stopWatches() {
                watches.values.forEach { it.stopWatching(); active.decrementAndGet() }
                watches.clear()
            }
            fun arm() {
                if (structural.getAndSet(false)) stopWatches()
                for (path in listOf(root, root.resolve("runs"), root.resolve(directory))) {
                    require(!Files.isSymbolicLink(path))
                    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) break
                    if (path !in watches) {
                        val watcher = object : FileObserver(path.toFile(), FileObserver.CREATE or FileObserver.MODIFY or FileObserver.CLOSE_WRITE or
                            FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.DELETE or FileObserver.DELETE_SELF or FileObserver.ATTRIB) {
                            override fun onEvent(event: Int, path: String?) {
                                if (event and (CREATE or MOVED_TO or MOVED_FROM or DELETE or DELETE_SELF) != 0) structural.set(true)
                                signals.trySend(Unit)
                            }
                        }
                        watches[path] = watcher
                        active.incrementAndGet()
                        watcher.startWatching()
                    }
                }
            }
            try {
                arm()
                signals.trySend(Unit)
                for (signal in signals) {
                    arm()
                    emit(reader.snapshot())
                    // Coalesce only after a real event; idle viewers schedule no recurring reads.
                    delay(100)
                }
            } finally {
                stopWatches()
                signals.close()
            }
        }
    }.conflate().flowOn(Dispatchers.IO)
}
