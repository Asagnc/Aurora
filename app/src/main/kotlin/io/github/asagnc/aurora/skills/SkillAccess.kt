package io.github.asagnc.aurora.skills

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SkillEntry
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

internal suspend fun SkillManager.list(): List<SkillEntry> = registry.entries()
internal suspend fun SkillManager.snapshot(owner: String): List<SkillEntry> = catalog.withLock {
    registry.entries().filter { it.enabled }.also { registry.pin(owner, it) }
}
internal suspend fun SkillManager.read(entry: SkillEntry, relative: String, offset: Long = 0): JsonObject = catalog.withLock {
    runInterruptible(Dispatchers.IO) {
        val bytes = store.read(entry.hash, relative, offset)
        val manifest = store.manifest(entry.hash)
        val expected = manifest.files.single { it.path == relative }
        val text = runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()
        objectOf("skill" to entry.summary(), "path" to string(relative), "text" to (text?.let(::string) ?: JsonNull),
            "next" to JsonPrimitive(offset + bytes.size), "size" to JsonPrimitive(expected.size), "complete" to JsonPrimitive(offset + bytes.size == expected.size),
            "baseDirectory" to string(store.directory(entry.hash).resolve("content").toString()),
            "files" to JsonArray(manifest.files.take(128).map { string(it.path) }),
            "filesTruncated" to JsonPrimitive(manifest.files.size > 128),
            "encoding" to string(if (text == null) "binary-or-partial-UTF8" else "UTF-8"))
    }
}
internal suspend fun SkillManager.pin(owner: String, entry: SkillEntry) = catalog.withLock { registry.pin(owner, listOf(entry)) }
internal suspend fun SkillManager.resolve(owner: String, id: String, hash: String): SkillEntry = catalog.withLock {
    requireNotNull(registry.versions(id).firstOrNull { it.hash == hash }) { "Unknown installed skill version" }.also {
        registry.pin(owner, listOf(it))
    }
}
internal suspend fun SkillManager.enable(id: String, enabled: Boolean) = catalog.withLock { registry.enabled(id, enabled); version.update { it + 1 } }
internal suspend fun SkillManager.rollback(id: String, hash: String) = catalog.withLock { registry.select(id, hash); version.update { it + 1 } }
internal suspend fun SkillManager.remove(id: String): List<String> = catalog.withLock { registry.remove(id).also { version.update { it + 1 } } }
internal suspend fun SkillManager.cleanup(): Long = catalog.withLock { withContext(Dispatchers.IO) {
    var bytes = 0L
    registry.garbage().forEach { (hash, size) ->
        val operation = UUID.randomUUID().toString()
        withContext(NonCancellable) {
            registry.reserveRemoval(operation, hash); store.remove(hash); registry.clearOperation(operation)
        }
        bytes += size
    }
    version.update { it + 1 }; bytes
} }
