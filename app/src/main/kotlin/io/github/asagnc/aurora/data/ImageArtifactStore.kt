package io.github.asagnc.aurora.data

import android.graphics.BitmapFactory
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.nio.file.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Originals and their manifest live together in the conversation, never in a global image cache. */
internal class ImageArtifactStore(private val app: AuroraApplication) {
    private val downloads = ImageDownload()
    private val permits = Semaphore(2)
    private val stripes = List(16) { Mutex() }

    suspend fun publish(owner: String, path: String, description: String): ImageArtifact = owned(owner) {
        val bytes = app.spaces.conversation(owner).readBinary(path, MAX_BYTES)
        persist(owner, UUID.randomUUID().toString(), bytes, description)
    }

    suspend fun resolve(owner: String, source: String): ImageArtifact = owned(owner) {
        imageArtifactId(source)?.let { return@owned metadata(owner, it) }
        require(source.startsWith("https://")) { "Unsupported image reference" }
        val hash = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)))
        val id = UUID(hash.long, hash.long).toString()
        stripes[(id.hashCode() and Int.MAX_VALUE) % stripes.size].withLock {
            val existing = try { metadata(owner, id) } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
            existing ?: persist(owner, id, downloads.read(source), "网络图片")
        }
    }

    suspend fun file(owner: String, artifact: ImageArtifact): java.io.File = owned(owner) {
        verifiedFile(owner, artifact)
    }

    suspend fun copyTo(owner: String, artifact: ImageArtifact, output: java.io.OutputStream) = owned(owner) {
        Files.newInputStream(verifiedFile(owner, artifact).toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
    }

    private suspend fun verifiedFile(owner: String, artifact: ImageArtifact): java.io.File {
        val record = metadata(owner, artifact.id)
        require(record == artifact)
        val path = directory(owner).resolve("${record.id}.${record.extension}")
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == record.bytes)
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(65536)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= record.bytes)
                digest.update(buffer, 0, count)
            }
            require(total == record.bytes)
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == record.sha256) { "Image artifact changed" }
        return path.toFile()
    }

    private suspend fun metadata(owner: String, id: String): ImageArtifact {
        require(imageArtifactId("aurora-image:$id") == id)
        val path = directory(owner).resolve("$id.json")
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 4096)
        val text = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(4097) }.decodeToString(throwOnInvalidSequence = true)
        return WireJson.decodeFromString(ImageArtifact.serializer(), text).also {
            require(it.id == id && it.bytes in 1..MAX_BYTES.toLong() && it.width > 0 && it.height > 0 &&
                it.width.toLong() * it.height <= 64_000_000 && it.sha256.matches(Regex("[0-9a-f]{64}")) &&
                it.mime.startsWith("image/") && it.description.length <= 300)
        }
    }

    private suspend fun persist(owner: String, id: String, bytes: ByteArray, description: String): ImageArtifact {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES && description.length <= 300)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 64_000_000) {
            "Unsupported image or image dimensions exceed decode budget"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val record = ImageArtifact(id, requireNotNull(bounds.outMimeType), bounds.outWidth, bounds.outHeight, bytes.size.toLong(), digest, description)
        val directory = directory(owner)
        val target = directory.resolve("$id.${record.extension}")
        val staged = directory.resolve("$id.partial")
        val manifest = directory.resolve("$id.json")
        val manifestStage = directory.resolve("$id.json.partial")
        try {
            durableWrite(staged, bytes)
            currentCoroutineContext().ensureActive()
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE)
            durableWrite(manifestStage, WireJson.encodeToString(ImageArtifact.serializer(), record).toByteArray(Charsets.UTF_8))
            Files.move(manifestStage, manifest, StandardCopyOption.ATOMIC_MOVE)
            return record
        } catch (failure: Throwable) {
            Files.deleteIfExists(target)
            Files.deleteIfExists(manifest)
            throw failure
        } finally { Files.deleteIfExists(staged); Files.deleteIfExists(manifestStage) }
    }

    private suspend fun directory(owner: String): Path {
        require(app.conversations.header(owner) != null) { "Conversation is absent" }
        val root = app.spaces.commandRoot(owner)
        val parent = root.resolve("image-artifacts")
        require(!Files.isSymbolicLink(parent))
        Files.createDirectories(parent)
        return parent
    }

    private fun durableWrite(path: Path, bytes: ByteArray) {
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private suspend fun <T> owned(owner: String, block: suspend () -> T): T = coroutineScope {
        app.tasks.run(owner) { permits.withPermit { withContext(Dispatchers.IO) { block() } } }
    }

    companion object { const val MAX_BYTES = 32 * 1024 * 1024 }
}
