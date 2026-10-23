package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.UUID

/** Call on IO under the conversation task lease. Incomplete files are never published. */
class BrowserDownloadFile(root: Path, private val maxBytes: Long) : AutoCloseable {
    val relative = "runs/${UUID.randomUUID()}/download.bin"
    private val target = OwnedFiles(root).resolve(relative)
    private val temporary = target.resolveSibling(target.fileName.toString() + ".partial")
    private val channel: FileChannel
    var bytes = 0L
        private set
    private var published = false
    init {
        require(maxBytes in 1..512L * 1024 * 1024)
        Files.createDirectories(target.parent)
        require(!Files.isSymbolicLink(target.parent))
        channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS)
    }
    fun append(chunk: ByteArray) {
        check(!published && channel.isOpen)
        require(chunk.size in 1..65536 && chunk.size.toLong() <= maxBytes - bytes) { "Download chunk exceeds byte budget" }
        val buffer = ByteBuffer.wrap(chunk)
        while (buffer.hasRemaining()) channel.write(buffer)
        bytes += chunk.size
    }
    fun publish(reportedBytes: Long): String {
        check(!published && channel.isOpen)
        require(reportedBytes == bytes) { "Incomplete download" }
        channel.force(true)
        check(channel.size() == bytes) { "Download size changed" }
        channel.close()
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        published = true
        return relative
    }
    override fun close() {
        try { channel.close() } finally { if (!published) Files.deleteIfExists(temporary) }
    }
}
