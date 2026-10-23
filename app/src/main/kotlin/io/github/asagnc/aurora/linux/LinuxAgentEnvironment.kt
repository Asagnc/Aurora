package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.core.LinuxBackend
import io.github.asagnc.aurora.data.LinuxRegistry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Lazy per-Agent-run pin; each dispatched command/PTY also owns its durable run lease. */
internal class LinuxAgentEnvironment(private val registry: LinuxRegistry, private val catalog: Mutex, private val owner: String) {
    constructor(manager: LinuxEnvironmentManager, owner: String) : this(manager.registry, manager.catalog, owner)
    private val lock = Mutex()
    private var pin: Pair<String, String>? = null
    private var closed = false

    /** Observation does not acquire a lease; execution must still call generation(). */
    suspend fun selectedGeneration(): String? = lock.withLock {
        check(!closed)
        pin?.first ?: registry.current()
    }

    suspend fun generation(): String = lock.withLock {
        check(!closed)
        pin?.first ?: withContext(NonCancellable) { catalog.withLock {
            val id = requireNotNull(registry.current()) { "Install a Root Linux generation first" }
            check(registry.versions().any { it.id == id && it.manifest.backend == LinuxBackend.CHROOT }) {
                "Selected generation is not a Root Linux environment"
            }
            val token = registry.lease(owner, id)
            pin = id to token
            id
        } }
    }

    suspend fun close() = withContext(NonCancellable) { lock.withLock {
        if (!closed) {
            pin?.let { registry.release(it.second) }
            pin = null
            closed = true
        }
    } }
}
