package io.github.asagnc.aurora.root

import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import java.nio.file.*
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** NONBLOCK + fstat prevent guest FIFOs/devices from blocking a Binder worker. */
internal object RootLinuxFileReads {
    fun resolve(root: Path, relative: String): Path {
        val parts = Path.of(relative)
        require(relative.isNotBlank() && !parts.isAbsolute && parts.none { it.toString() in setOf("..", ".aurora-transactions") })
        require(parts.first().toString() in setOf("home", "tmp", "project"))
        var path = root
        for (part in parts) { path = path.resolve(part); require(!Files.isSymbolicLink(path)) }
        require(path.normalize().startsWith(root))
        return path
    }
    suspend fun batch(root: Path, requests: JsonArray): JsonObject {
        require(requests.size in 1..64)
        var remaining = 32768 - requests.sumOf { requireNotNull(it.jsonObject.text("path")).toByteArray(Charsets.UTF_8).size }
        require(remaining >= requests.size) { "Root batch path metadata exceeds the wire budget" }
        val results = requests.mapIndexed { index, entry ->
            val item = entry.jsonObject
            val name = requireNotNull(item.text("path"))
            val limit = Math.toIntExact(item.number("limit") ?: 65536).also { require(it in 1..65536) }
            val budget = minOf(limit, remaining / (requests.size - index))
            val value = read(resolve(root, name), name, item.number("offset") ?: 0, budget)
            remaining -= budget
            value
        }
        return objectOf("files" to JsonArray(results))
    }
    private suspend fun read(path: Path, name: String, offset: Long, limit: Int): JsonObject = try {
        require(offset >= 0)
        val file = Os.open(path.toString(), OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or
            OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
        try {
            val info = Os.fstat(file)
            require(OsConstants.S_ISREG(info.st_mode) && offset <= info.st_size)
            val bytes = ByteArray(minOf(limit.toLong(), info.st_size - offset).toInt())
            fill(file, bytes, offset)
            val version = if (info.st_size <= OwnedFiles.MAX_EDIT_BYTES) {
                val entire = ByteArray(info.st_size.toInt()); fill(file, entire, 0)
                MessageDigest.getInstance("SHA-256").digest(entire).joinToString("") { "%02x".format(it) }
            } else null
            val after = Os.fstat(file)
            require(after.st_size == info.st_size && after.st_mtime == info.st_mtime && after.st_ctime == info.st_ctime && after.st_mtim.tv_nsec == info.st_mtim.tv_nsec && after.st_ctim.tv_nsec == info.st_ctim.tv_nsec) { "File changed during read" }
            val decoded = decodeUtf8FileSlice(bytes, offset + bytes.size < info.st_size)
            val text = decoded.text
            val next = offset + decoded.consumed
            objectOf("path" to string(name), "text" to (text?.let(::string) ?: JsonNull), "size" to JsonPrimitive(info.st_size),
                "bytesRead" to JsonPrimitive(decoded.consumed), "version" to (version?.let(::string) ?: JsonNull),
                "next" to if (next < info.st_size) JsonPrimitive(next) else JsonNull,
                "encoding" to string(if (text == null) "binary-or-partial-UTF8" else "UTF-8"), "error" to JsonNull)
        } finally { Os.close(file) }
    } catch (cancel: CancellationException) { throw cancel }
    catch (failure: Exception) { objectOf("path" to string(name), "error" to string(failure.javaClass.simpleName)) }
    private suspend fun fill(file: java.io.FileDescriptor, bytes: ByteArray, offset: Long) {
        var read = 0
        while (read < bytes.size) {
            currentCoroutineContext().ensureActive()
            val count = Os.pread(file, bytes, read, bytes.size - read, offset + read)
            require(count > 0) { "File changed during read" }
            read += count
        }
    }
}
