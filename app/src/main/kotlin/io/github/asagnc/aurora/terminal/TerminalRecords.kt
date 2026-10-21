package io.github.asagnc.aurora.terminal

import io.github.asagnc.aurora.core.*
import java.nio.file.*
import java.util.UUID
import kotlinx.serialization.json.*

internal data class TerminalRecord(val id: String, val owner: String, val backend: String, val state: String,
    val started: Long, val exitCode: Int? = null, val bytes: Long = 0, val stored: Long = 0, val reason: String? = null,
    val bytesExact: Boolean = true) {
    fun json(): JsonObject = objectOf("id" to string(id), "owner" to string(owner), "backend" to string(backend),
        "state" to string(state), "started" to JsonPrimitive(started), "exitCode" to (exitCode?.let(::JsonPrimitive) ?: JsonNull),
        "bytes" to JsonPrimitive(bytes), "bytesExact" to JsonPrimitive(bytesExact), "storedBytes" to JsonPrimitive(stored),
        "logTruncated" to if (bytesExact) JsonPrimitive(bytes > stored) else JsonNull,
        "artifact" to string("terminals/$id/output.bin"), "reason" to (reason?.let(::string) ?: JsonNull))
}

internal class TerminalRecords(private val root: Path) {
    fun directory(owner: String, id: String, create: Boolean = false): Path {
        listOf(owner, id).forEach { require(UUID.fromString(it).toString() == it) }
        var path = root
        for (part in listOf("spaces", "conversations", owner, "terminals", id)) {
            path = path.resolve(part)
            require(!Files.isSymbolicLink(path))
            if (create && !Files.exists(path)) Files.createDirectory(path)
        }
        return path
    }

    fun save(record: TerminalRecord) {
        val directory = directory(record.owner, record.id)
        val staged = directory.resolve("state.partial")
        java.nio.channels.FileChannel.open(staged, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val buffer = java.nio.ByteBuffer.wrap(record.json().toString().toByteArray())
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        Files.move(staged, directory.resolve("state.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun load(owner: String, id: String): TerminalRecord {
        val path = directory(owner, id).resolve("state.json")
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 4096)
        val item = WireJson.parseToJsonElement(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { String(it.readNBytes(4097)) }).jsonObject
        require(item.text("owner") == owner && item.text("id") == id)
        return TerminalRecord(id, owner, requireNotNull(item.text("backend")), requireNotNull(item.text("state")),
            requireNotNull(item.number("started")), item.number("exitCode")?.toInt(), item.number("bytes") ?: 0,
            item.number("storedBytes") ?: 0, item.text("reason"), item["bytesExact"]?.jsonPrimitive?.booleanOrNull ?: true)
    }

    fun list(owner: String): List<TerminalRecord> {
        val path = directory(owner, "00000000-0000-0000-0000-000000000000").parent
        if (!Files.exists(path)) return emptyList()
        return Files.newDirectoryStream(path).use { stream -> stream.mapNotNull { child ->
            runCatching { load(owner, child.fileName.toString()) }.getOrNull()
        }.sortedByDescending { it.started }.take(100) }
    }

    fun recover() {
        val owners = root.resolve("spaces/conversations")
        if (!Files.isDirectory(owners, LinkOption.NOFOLLOW_LINKS)) return
        Files.newDirectoryStream(owners).use { paths -> paths.forEach { owner ->
            if (!Files.isSymbolicLink(owner)) runCatching { recoverOwner(owner.fileName.toString()) }
        } }
    }

    private fun recoverOwner(owner: String) {
        val path = directory(owner, "00000000-0000-0000-0000-000000000000").parent
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.newDirectoryStream(path).use { entries -> entries.forEach { entry -> runCatching {
            val record = load(owner, entry.fileName.toString())
            if (record.state in setOf("starting", "running")) {
                val log = directory(owner, record.id).resolve("output.bin")
                val stored = if (Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS)) Files.size(log) else 0
                save(record.copy(state = "interrupted", bytes = stored, stored = stored, bytesExact = false,
                    reason = "Application process ended; total received bytes unknown; input was not replayed"))
            }
        } } }
    }

    fun read(record: TerminalRecord, cursor: Long, limit: Int): TerminalBytes.Slice {
        require(cursor >= 0 && limit in 1..65536)
        val path = directory(record.owner, record.id).resolve("output.bin")
        if (!Files.exists(path)) return TerminalBytes.Slice(byteArrayOf(), 0, 0, 0, cursor > 0)
        return java.nio.channels.FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { file ->
            val end = file.size()
            require(cursor <= end) { "Historical log cursor exceeds stored bytes; log may have been truncated" }
            val buffer = java.nio.ByteBuffer.allocate(minOf(limit.toLong(), end - cursor).toInt())
            while (buffer.hasRemaining() && file.read(buffer, cursor + buffer.position()) > 0) { }
            TerminalBytes.Slice(buffer.array().copyOf(buffer.position()), cursor, cursor + buffer.position(), end, false)
        }
    }
}
