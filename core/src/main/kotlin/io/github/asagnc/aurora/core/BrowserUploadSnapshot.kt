package io.github.asagnc.aurora.core

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.UUID

/** Stable bytes after copy, not an atomic snapshot of a concurrently modified source. Call on IO. */
class BrowserUploadSnapshot(root: Path, epoch: String, private val budget: Long = 512L*1024*1024) : AutoCloseable {
    data class Entry(val path: Path, val name: String)
    val directory: Path
    private val selected = mutableListOf<Entry>()
    val entries: List<Entry> get() = selected
    var bytes = 0L
        private set
    private var closed = false
    init {
        require(budget in 1..512L*1024*1024 && UUID.fromString(epoch).toString() == epoch)
        val parent = OwnedFiles(root).resolve(".aurora-browser-uploads/$epoch")
        Files.createDirectories(parent)
        directory = Files.createDirectory(parent.resolve(UUID.randomUUID().toString()))
        Files.writeString(directory.resolve("lease"), MARKER, StandardOpenOption.CREATE_NEW)
    }
    fun add(name: String, input: InputStream, extensionHint: String? = null, checkpoint: () -> Unit = {}) : Entry {
        check(!closed)
        require(entries.size < 8)
        val display = name.replace(Regex("""[\\/\p{Cc}]"""), "_").take(120).ifBlank { "upload" }
        val extension = (extensionHint ?: display.substringAfterLast('.', "")).takeIf { it.length in 1..16 && it.all { c -> c.isLetterOrDigit() && c.code < 128 } }
        val target = directory.resolve(entries.size.toString() + (extension?.let { ".$it" } ?: ""))
        val partial = target.resolveSibling(target.fileName.toString()+".partial")
        var written = 0L
        try {
            FileChannel.open(partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    checkpoint()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(count > 0) { "Source made no progress" }
                    require(count.toLong() <= budget-bytes-written) { "Upload snapshot exceeds budget" }
                    val chunk = ByteBuffer.wrap(buffer,0,count)
                    while(chunk.hasRemaining()) output.write(chunk)
                    written += count
                }
                output.force(true)
            }
            checkpoint()
            Files.move(partial,target,StandardCopyOption.ATOMIC_MOVE)
            Files.getFileAttributeView(target,java.nio.file.attribute.PosixFileAttributeView::class.java,LinkOption.NOFOLLOW_LINKS)
                ?.setPermissions(setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ))
            return Entry(target,display).also { selected += it; bytes += written }
        } finally { Files.deleteIfExists(partial) }
    }
    fun add(files: OwnedFiles, relative: String, checkpoint: () -> Unit = {}): Entry {
        val source = files.resolve(relative)
        require(Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))
        return Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS).use { add(source.fileName.toString(),it,checkpoint=checkpoint) }
    }
    override fun close() {
        if (closed) return
        check(removeLease(directory)) { "Upload snapshot contains unknown data; retained for inspection" }
        closed = true
        try { Files.delete(directory.parent) } catch (_: DirectoryNotEmptyException) { } catch (_: NoSuchFileException) { }
    }
    companion object {
        private const val MARKER = "Aurora browser upload snapshot v1\n"
        private val LEAF = Regex("""[0-7](?:\.[A-Za-z0-9]{1,16})?(?:\.partial)?""")
        /** Only old app-process epochs; bounded explicit maintenance on the next upload selection. */
        fun recoverOld(root: Path, currentEpoch: String, limit: Int = 32): Int {
            require(limit in 1..128 && UUID.fromString(currentEpoch).toString() == currentEpoch)
            val base = root.resolve(".aurora-browser-uploads")
            if (!Files.isDirectory(base,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) return 0
            var recovered = 0
            var examined = 0
            Files.list(base).use { epochs ->
                for (epoch in epochs.limit(128).toList()) {
                    val name = epoch.fileName.toString()
                    if(name == currentEpoch || !uuid(name) || !Files.isDirectory(epoch,LinkOption.NOFOLLOW_LINKS)) continue
                    Files.list(epoch).use { leases ->
                        for(lease in leases.limit(limit.toLong()).toList()) {
                            if(examined++ >= limit) return recovered
                            if(uuid(lease.fileName.toString()) && Files.isDirectory(lease,LinkOption.NOFOLLOW_LINKS) && removeLease(lease)) recovered++
                        }
                    }
                    try { Files.delete(epoch) } catch (_: DirectoryNotEmptyException) { }
                }
            }
            return recovered
        }
        private fun uuid(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
        private fun removeLease(directory: Path): Boolean {
            if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)) return true
            if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)) return false
            val marker = directory.resolve("lease")
            if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS) || Files.size(marker) > 128 || Files.readString(marker) != MARKER) return false
            val files = Files.list(directory).use { it.limit(19).toList() }
            if(files.size > 18 || files.any { !Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS) ||
                (it.fileName.toString() != "lease" && !LEAF.matches(it.fileName.toString())) }) return false
            files.filter { it != marker }.forEach { Files.delete(it) }
            Files.delete(marker);Files.delete(directory)
            return true
        }
    }
}
