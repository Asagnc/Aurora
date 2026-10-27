package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One app-process owner; production reservations/removals bind to the same durable Linux task records. */
class StaticReverseStore(private val root: Path,
    private val inUse: suspend (String) -> Boolean = { false },
    private val prepare: (StaticReverseComponent, Path, Path) -> Unit = { component, archive, target ->
        StaticReversePreparation.prepare(component, archive, target)
    }) {
    @Volatile var cleanupFailed: Boolean = false
        private set
    private val operations = Mutex()
    private val staging get() = root.resolve("staging")
    private val versions get() = root.resolve("versions")
    private val marker = "aurora-static-reverse-stage-v1"

    private fun initialize() {
        require(root.isAbsolute)
        var ancestor: Path? = root
        while (ancestor != null) {
            require(!Files.isSymbolicLink(ancestor))
            ancestor = ancestor.parent
        }
        Files.createDirectories(root)
        for (path in listOf(staging, versions)) {
            require(!Files.isSymbolicLink(path)); Files.createDirectories(path)
        }
    }

    suspend fun installed(): List<String> = withContext(Dispatchers.IO) { operations.withLock {
        initialize()
        Files.newDirectoryStream(versions).use { entries -> entries.filter { complete(it) }.map { it.fileName.toString() }.sorted() }
    } }

    suspend fun install(fetch: suspend (StaticReverseComponent, Path) -> Unit): String = install(StaticReverseTools.catalog, fetch)

    suspend fun install(catalog: SharedToolCatalog,
        fetch: suspend (StaticReverseComponent, Path) -> Unit): String = withContext(Dispatchers.IO) { operations.withLock {
        val selected = catalog.copy(components = catalog.components.toList())
        initialize()
        val id = selected.bundleId
        val published = versions.resolve(id)
        if (complete(published)) return@withLock id
        require(!Files.exists(published, LinkOption.NOFOLLOW_LINKS)) { "Incomplete published bundle: remove the catalogBundle id before reinstalling" }
        // Includes compressed, conservative expanded maxima and per-file metadata headroom before any download.
        // Android's NIO provider rejects getFileStore; File delegates this
        // filesystem-space query to statvfs. Zero/unavailable still fails closed.
        require(root.toFile().usableSpace >= selected.compressedBytes + selected.headroomBytes) { "Insufficient tool installation space" }
        val stage = Files.createDirectory(staging.resolve(UUID.randomUUID().toString()))
        var original: Throwable? = null
        try {
            durable(stage.resolve("owner"), marker)
            val prepared = Files.createDirectory(stage.resolve("prepared"))
            for (component in selected.components) {
                currentCoroutineContext().ensureActive()
                val archive = stage.resolve(component.id + ".archive")
                fetch(component, archive)
                runInterruptible { prepare(component, archive, prepared.resolve(component.id)) }
                Files.delete(archive)
            }
            runInterruptible { selected.finish(prepared) }
            durable(prepared.resolve("family"), selected.family)
            require(SharedToolLayout.complete(prepared)) { "Prepared tool bundle layout is incomplete" }
            durable(prepared.resolve("bundle"), id)
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) { Files.move(prepared, published, StandardCopyOption.ATOMIC_MOVE) }
            id
        } catch (failure: Throwable) { original = failure; throw failure }
        finally { withContext(NonCancellable) {
            try { removeStage(stage) } catch (cleanup: Throwable) {
                if (original != null) {
                    original.addSuppressed(cleanup)
                    if (original !is CancellationException)
                        throw java.io.IOException("Tool installation failed; staging cleanup remains pending", original)
                } else throw cleanup
            }
        } }
    } }

    /** The callback commits the durable dispatch record while deletion is excluded by this same lock. */
    suspend fun <T> reserve(id: String, record: suspend () -> T): T = withContext(Dispatchers.IO) { operations.withLock {
        initialize(); require(id.matches(Regex("[a-f0-9]{64}")))
        require(complete(versions.resolve(id))) { "Install the requested tool bundle first" }
        record()
    } }

    /** Persistent task lookup runs under the same lock as dispatch reservation. */
    suspend fun remove(id: String): Boolean = withContext(Dispatchers.IO) { operations.withLock {
        initialize(); require(id.matches(Regex("[a-f0-9]{64}")))
        val version = versions.resolve(id)
        if (!Files.exists(version, LinkOption.NOFOLLOW_LINKS)) return@withLock false
        require(ownedVersion(version)) { "Unrecognized tool version retained" }
        check(!inUse(id)) { "Tool bundle has an unconfirmed Linux task; recover its exit before removing" }
        val stage = Files.createDirectory(staging.resolve(UUID.randomUUID().toString()))
        durable(stage.resolve("owner"), marker)
        Files.move(version, stage.resolve("removed"), StandardCopyOption.ATOMIC_MOVE)
        removeStage(stage); true
    } }

    suspend fun cleanup(): Int = withContext(Dispatchers.IO) { operations.withLock {
        initialize(); var removed = 0
        Files.newDirectoryStream(staging).use { entries -> entries.take(64).forEach { stage ->
            currentCoroutineContext().ensureActive()
            val name = stage.fileName.toString()
            val uuid = runCatching { UUID.fromString(name).toString() == name }.getOrDefault(false)
            val owner = stage.resolve("owner")
            if (uuid && Files.isDirectory(stage, LinkOption.NOFOLLOW_LINKS) &&
                Files.newDirectoryStream(stage).use { !it.iterator().hasNext() }) {
                Files.delete(stage); removed++; return@forEach
            }
            if (uuid && Files.isDirectory(stage, LinkOption.NOFOLLOW_LINKS) &&
                Files.isRegularFile(owner, LinkOption.NOFOLLOW_LINKS) && Files.size(owner) == marker.length.toLong() &&
                Files.readAllBytes(owner).toString(Charsets.UTF_8) == marker) { removeStage(stage); removed++ }
        } }
        cleanupFailed = false
        removed
    } }

    // Keep the ownership proof until the last unlink so an interrupted cleanup can be retried.
    private fun removeStage(stage: Path) {
        try {
            Files.newDirectoryStream(stage).use { entries -> entries.filter { it.fileName.toString() != "owner" }.forEach(::removeSkillTree) }
            Files.deleteIfExists(stage.resolve("owner"))
            Files.delete(stage)
        } catch (failure: Exception) {
            cleanupFailed = true
            throw java.io.IOException("Tool staging cleanup remains pending; retry shared_toolchain cleanup", failure)
        }
    }

    private fun complete(directory: Path): Boolean = ownedVersion(directory) && SharedToolLayout.complete(directory)

    private fun ownedVersion(directory: Path): Boolean {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return false
        val id = directory.fileName.toString()
        val file = directory.resolve("bundle")
        return id.matches(Regex("[a-f0-9]{64}")) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(file) == 64L && Files.readAllBytes(file).toString(Charsets.UTF_8) == id
    }

    private fun durable(path: Path, text: String) {
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val bytes = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
    }
}
