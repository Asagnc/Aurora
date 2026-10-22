package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

data class FileRead(val path: String, val offset: Long = 0, val limit: Int = 64 * 1024)
data class FileSlice(val path: String, val text: String?, val bytes: ByteArray,
    val size: Long, val next: Long?, val version: String?, val error: String? = null)
@Serializable data class FileEdit(val path: String, val expectedVersion: String?, val text: String)
@Serializable internal data class FileBackup(val path: String, val existed: Boolean, val oldVersion: String?, val newVersion: String)
@Serializable internal data class EditJournal(val files: List<FileBackup>)

/** This namespace is app-owned. It is not a sandbox for arbitrary privileged programs. */
class OwnedFiles(private val root: Path, private val writable: Boolean = true) {
    private val mutations = Mutex()
    private val reads = Semaphore(4)
    private val transactions = root.resolve(".aurora-transactions")

    init {
        require(root.isAbsolute)
        Files.createDirectories(root)
        require(!Files.isSymbolicLink(root))
    }

    internal fun resolve(relative: String): Path {
        val path = Path.of(relative)
        require(relative.isNotBlank() && !path.isAbsolute && path.none { it.toString() in setOf("..", ".aurora-transactions") }) {
            "Path must stay inside its owned namespace"
        }
        val target = root.resolve(path).normalize()
        require(target.startsWith(root) && target != root)
        var cursor = root
        for (part in root.relativize(target)) {
            cursor = cursor.resolve(part)
            require(!Files.isSymbolicLink(cursor)) { "Symbolic links are not file-tool targets" }
        }
        return target
    }

    suspend fun readBatch(requests: List<FileRead>): List<FileSlice> = withContext(Dispatchers.IO) {
        require(requests.size in 1..64)
        coroutineScope { requests.map { request -> async { reads.withPermit { read(request) } } }.awaitAll() }
    }

    suspend fun readBinary(relative: String, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        require(limit in 1..32 * 1024 * 1024)
        reads.withPermit {
            val path = resolve(relative)
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                val size = channel.size()
                require(size in 1..limit.toLong()) { "Binary file exceeds read budget" }
                val buffer = ByteBuffer.allocate(size.toInt())
                while (buffer.hasRemaining()) {
                    currentCoroutineContext().ensureActive()
                    check(channel.read(buffer) >= 0) { "File changed during read" }
                }
                check(channel.size() == size) { "File changed during read" }
                buffer.array()
            }
        }
    }

    suspend fun list(relative: String = "", limit: Int = 200): List<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        require(limit in 1..200)
        val directory = if (relative.isEmpty()) root else resolve(relative)
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        Files.list(directory).use { entries -> entries.filter { it.fileName.toString() != ".aurora-transactions" }
            .sorted().limit(limit.toLong()).map { root.relativize(it).toString() to Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }.toList() }
    }

    private fun read(request: FileRead): FileSlice = try {
        require(request.offset >= 0 && request.limit in 1..65536)
        val path = resolve(request.path)
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val size = channel.size()
            require(request.offset <= size)
            val buffer = ByteBuffer.allocate(minOf(request.limit.toLong(), size - request.offset).toInt())
            channel.position(request.offset)
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            val bytes = buffer.array().copyOf(buffer.position())
            val decoded = decodeUtf8FileSlice(bytes, request.offset + bytes.size < size)
            val end = request.offset + decoded.consumed
            FileSlice(request.path, decoded.text, if (decoded.consumed == bytes.size) bytes else bytes.copyOf(decoded.consumed), size, end.takeIf { it < size },
                if (size <= MAX_EDIT_BYTES) digest(Files.readAllBytes(path)) else null)
        }
    } catch (failure: Exception) {
        FileSlice(request.path, null, byteArrayOf(), 0, null, null, failure.javaClass.simpleName)
    }

    suspend fun editBatch(edits: List<FileEdit>): Unit = withContext(Dispatchers.IO) {
        mutations.withLock {
            require(writable) { "Shared resources are read-only to tasks" }
            recoverLocked()
            require(edits.size in 1..64 && edits.map { it.path }.distinct().size == edits.size)
            val backups = preflight(edits)
            val tx = transactions.resolve(UUID.randomUUID().toString())
            Files.createDirectories(tx)
            try {
                edits.forEachIndexed { index, edit ->
                    val target = resolve(edit.path)
                    if (backups[index].existed) durableWrite(tx.resolve("$index.old"), Files.readAllBytes(target))
                    durableWrite(tx.resolve("$index.new"), edit.text.toByteArray())
                }
                durableWrite(tx.resolve("journal.json"), WireJson.encodeToString(EditJournal.serializer(), EditJournal(backups)).toByteArray())
                edits.forEachIndexed { index, edit ->
                    val target = resolve(edit.path)
                    require(version(target) == backups[index].oldVersion) { "File changed after preflight" }
                    Files.createDirectories(target.parent)
                    Files.move(tx.resolve("$index.new"), target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }
                durableWrite(tx.resolve("committed"), byteArrayOf(1))
                removeTransaction(tx)
            } catch (failure: Exception) {
                if (!Files.exists(tx.resolve("committed"))) {
                    try { rollback(tx) } catch (recovery: Exception) { failure.addSuppressed(recovery) }
                }
                throw failure
            }
        }
    }

    private fun preflight(edits: List<FileEdit>): List<FileBackup> {
        require(edits.sumOf { it.text.toByteArray().size.toLong() } <= 16 * 1024 * 1024)
        val targets = edits.map { resolve(it.path) }
        require(targets.distinct().size == targets.size) { "Duplicate file targets" }
        require(targets.none { candidate -> targets.any { it != candidate && candidate.startsWith(it) } }) {
            "File targets cannot contain another target"
        }
        return edits.map { edit ->
            val target = resolve(edit.path)
            require(edit.text.toByteArray().size <= MAX_EDIT_BYTES)
            require(!Files.exists(target) || Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            val oldVersion = version(target)
            require(oldVersion == edit.expectedVersion) { "File version conflict: ${edit.path}" }
            FileBackup(edit.path, oldVersion != null, oldVersion, digest(edit.text.toByteArray()))
        }
    }

    suspend fun recover(): Unit = withContext(Dispatchers.IO) { mutations.withLock { recoverLocked() } }

    private fun recoverLocked() {
        if (!Files.exists(transactions)) return
        require(!Files.isSymbolicLink(transactions))
        Files.list(transactions).use { entries -> entries.forEach { tx ->
            require(Files.isDirectory(tx, LinkOption.NOFOLLOW_LINKS))
            if (Files.exists(tx.resolve("committed"))) removeTransaction(tx) else rollback(tx)
        } }
    }

    private fun rollback(tx: Path) {
        val journalPath = tx.resolve("journal.json")
        if (!Files.exists(journalPath)) { removeTransaction(tx); return }
        val journal = WireJson.decodeFromString(EditJournal.serializer(), Files.readAllBytes(journalPath).decodeToString(throwOnInvalidSequence = true))
        journal.files.forEachIndexed { index, backup ->
            val target = resolve(backup.path)
            val current = version(target)
            require(current == backup.oldVersion || current == backup.newVersion) { "Recovery conflict: ${backup.path}" }
            if (current == backup.oldVersion) return@forEachIndexed
            if (backup.existed) {
                val original = Files.readAllBytes(tx.resolve("$index.old"))
                require(digest(original) == backup.oldVersion) { "Backup integrity failure" }
                val staged = tx.resolve("$index.restore")
                durableWrite(staged, original)
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } else Files.deleteIfExists(target)
        }
        removeTransaction(tx)
    }

    private fun version(path: Path): String? {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        require(Files.size(path) <= MAX_EDIT_BYTES) { "File exceeds edit budget" }
        return digest(Files.readAllBytes(path))
    }

    private fun removeTransaction(tx: Path) {
        Files.list(tx).use { entries -> entries.forEach { Files.delete(it) } }
        Files.delete(tx)
    }

    companion object {
        const val MAX_EDIT_BYTES = 2 * 1024 * 1024
        internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        private fun durableWrite(path: Path, bytes: ByteArray) {
            FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { file ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) file.write(buffer)
                file.force(true)
            }
        }
    }
}
