package io.github.asagnc.aurora.core

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.*
import java.util.zip.CRC32
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile

data class SkillArchiveBudget(val compressed: Long = 64L * 1024 * 1024, val expanded: Long = 256L * 1024 * 1024,
    val file: Long = 32L * 1024 * 1024, val entries: Int = 4096)

/** Staging only. Published resources never receive unvalidated archive writes. */
class SkillArchive(private val budget: SkillArchiveBudget = SkillArchiveBudget()) {
    fun extract(archive: Path, target: Path): Set<String> {
        preflight(archive)
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
        Files.createDirectory(target)
        try {
            return ZipFile.builder().setPath(archive).get().use { zip ->
                val entries = zip.entries.asSequence().toList().also { require(it.size <= budget.entries) }
                validateEntries(entries)
                var total = 0L
                val executable = mutableSetOf<String>()
                for (entry in entries) {
                    cancelled()
                    val name = entry.name.removeSuffix("/")
                    val path = target.resolve(skillRelativePath(name))
                    if (entry.isDirectory) Files.createDirectories(path) else {
                        Files.createDirectories(path.parent)
                        val crc = CRC32()
                        var size = 0L
                        Files.newOutputStream(path, StandardOpenOption.CREATE_NEW).use { output ->
                            zip.getInputStream(entry).use { input ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    cancelled()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    size += count; total += count
                                    require(size <= budget.file && total <= budget.expanded) { "Expanded skill package exceeds byte budget" }
                                    crc.update(buffer, 0, count); output.write(buffer, 0, count)
                                }
                            }
                        }
                        require(size == entry.size && crc.value == entry.crc) { "ZIP size or CRC mismatch" }
                        if (entry.unixMode and 0b001001001 != 0) executable += name
                    }
                }
                executable
            }
        } catch (failure: Throwable) { removeSkillTree(target); throw failure }
    }

    private fun validateEntries(entries: List<ZipArchiveEntry>) {
        val paths = HashSet<String>()
        val files = HashSet<String>()
        var declared = 0L
        entries.forEach { entry ->
            val name = entry.name.removeSuffix("/")
            skillRelativePath(name)
            require(paths.add(name)) { "Duplicate ZIP path" }
            val type = entry.unixMode and 0xf000
            require(!entry.isUnixSymlink && type in setOf(0, 0x8000, 0x4000)) { "Links and special archive entries are unsupported" }
            require(type != 0x4000 || entry.isDirectory)
            require(!entry.generalPurposeBit.usesEncryption() && entry.method in setOf(0, 8)) { "Encrypted or unsupported ZIP compression" }
            require(entry.size >= 0 && entry.size <= budget.file)
            declared += entry.size; require(declared <= budget.expanded)
            if (!entry.isDirectory) files += name
        }
        paths.forEach { name ->
            var parent = name.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) { require(parent !in files) { "File/directory conflict" }; parent = parent.substringBeforeLast('/', "") }
        }
    }

    private fun preflight(path: Path) {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) in 22..budget.compressed)
        RandomAccessFile(path.toFile(), "r").use { file ->
            val count = minOf(file.length(), 65557).toInt()
            val bytes = ByteArray(count); file.seek(file.length() - count); file.readFully(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val at = (count - 22 downTo 0).firstOrNull {
                buffer.getInt(it) == 0x06054b50 && it + 22 + (buffer.getShort(it + 20).toInt() and 65535) == count
            } ?: error("Missing ZIP central directory")
            fun short(offset: Int) = buffer.getShort(at + offset).toInt() and 65535
            fun unsigned(offset: Int) = buffer.getInt(at + offset).toLong() and 0xffffffffL
            require(short(4) == 0 && short(6) == 0 && short(8) == short(10)) { "Split ZIP unsupported" }
            require(short(10) in 1..budget.entries && short(10) != 65535) { "ZIP entry budget exceeded or ZIP64 unsupported" }
            val centralSize = unsigned(12); val centralOffset = unsigned(16)
            require(centralSize in 1..(2L * 1024 * 1024) && centralOffset + centralSize <= file.length() - count + at) {
                "ZIP metadata budget exceeded or ZIP64 unsupported"
            }
        }
    }

    private fun cancelled() { if (Thread.currentThread().isInterrupted) throw InterruptedException("Skill extraction cancelled") }
}
