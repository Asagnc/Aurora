package io.github.asagnc.aurora.core

import java.io.FilterInputStream
import java.nio.file.*
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarConstants

data class RootfsBudget(val compressed: Long = 512L * 1024 * 1024, val expanded: Long = 2L * 1024 * 1024 * 1024,
    val file: Long = 256L * 1024 * 1024, val entries: Int = 50000)
@kotlinx.serialization.Serializable
data class RootfsEntry(val path: String, val kind: String, val bytes: Long, val mode: Int, val target: String = "",
    val uid: Long = 0, val gid: Long = 0)

/** Dedicated Linux archive contract; links are created only after all ordinary file writes finish. */
class RootfsArchive(private val budget: RootfsBudget = RootfsBudget()) {
    fun scan(archive: Path): List<RootfsEntry> {
        val result = mutableListOf<RootfsEntry>()
        val names = mutableMapOf<String, String>()
        var total = 0L; var pathBytes = 0L
        stream(archive) { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                require(entry.linkFlag in setOf(TarConstants.LF_NORMAL, TarConstants.LF_OLDNORM, TarConstants.LF_DIR,
                    TarConstants.LF_LINK, TarConstants.LF_SYMLINK) && !entry.isSparse) { "Special/sparse rootfs entries unsupported" }
                require(result.size < budget.entries)
                val path = rootfsName(entry.name)
                if (path.isEmpty()) { require(entry.isDirectory); continue }
                val kind = when {
                    entry.isSymbolicLink -> "symlink"
                    entry.isLink -> "hardlink"
                    entry.isDirectory -> "directory"
                    entry.isFile && !entry.isSparse -> "file"
                    else -> error("Special/sparse rootfs entries unsupported")
                }
                require(names.put(path, kind) == null) { "Duplicate rootfs entry" }
                require(entry.size in 0..budget.file && (kind == "file" || entry.size == 0L))
                total += entry.size; require(total <= budget.expanded)
                pathBytes += path.length + entry.linkName.length; require(pathBytes <= 8L * 1024 * 1024)
                if (kind.endsWith("link")) rootfsLinkDestination(path, entry.linkName, kind == "symlink")
                result += RootfsEntry(path, kind, entry.size, entry.mode and 0x3ff, entry.linkName,
                    entry.longUserId, entry.longGroupId)
            }
        }
        require(result.isNotEmpty())
        validate(result)
        return result
    }

    internal fun validate(result: List<RootfsEntry>) {
        require(result.isNotEmpty() && result.size <= budget.entries)
        val names = result.associate { it.path to it.kind }
        require(names.size == result.size && result.sumOf { it.bytes } <= budget.expanded)
        result.forEach { entry ->
            require(entry.path == rootfsName(entry.path) && entry.path.isNotEmpty())
            require(entry.kind in setOf("directory", "file", "symlink", "hardlink") && entry.bytes in 0..budget.file)
            require(entry.mode in 0..0x3ff)
            require(entry.uid in 0..4294967294L && entry.gid in 0..4294967294L) { "Unsupported Linux owner ID" }
            require(entry.kind == "file" || entry.bytes == 0L)
            if (entry.kind.endsWith("link")) rootfsLinkDestination(entry.path, entry.target, entry.kind == "symlink")
            var parent = entry.path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                require(names[parent] in setOf(null, "directory")) { "Rootfs entry below a link or regular file" }
                parent = parent.substringBeforeLast('/', "")
            }
            if (entry.kind == "hardlink") require(names[rootfsLinkDestination(entry.path, entry.target, false)] == "file") {
                "Hardlink must reference a declared regular file"
            }
        }
    }

    fun extract(archive: Path, target: Path, plan: List<RootfsEntry>, hooks: RootfsExtractionHooks = RootfsExtractionHooks(), mode: (Path, Int) -> Unit = { path, bits ->
        Files.setAttribute(path, "unix:mode", bits, LinkOption.NOFOLLOW_LINKS)
    }) {
        validate(plan)
        require(target.fileSystem.separator == "/") { "Rootfs extraction requires a Linux/Android filesystem" }
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
        Files.createDirectory(target)
        try {
            hooks.created(target)
            fun directory(path: Path) {
                require(path.startsWith(target))
                var current = target
                for (part in target.relativize(path)) {
                    current = current.resolve(part)
                    if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectory(current); hooks.created(current)
                    }
                    require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
                }
            }
            val indexed = plan.associateBy { it.path }
            val seen = mutableSetOf<String>()
            stream(archive) { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val name = rootfsName(entry.name)
                    if (name.isEmpty()) continue
                    val expected = requireNotNull(indexed[name])
                    require(seen.add(name) && expected.bytes == entry.size && expected.target == entry.linkName)
                    require(expected.uid == entry.longUserId && expected.gid == entry.longGroupId &&
                        expected.mode == (entry.mode and 0x3ff)) { "Rootfs metadata changed after scanning" }
                    require(entry.linkFlag in setOf(TarConstants.LF_NORMAL, TarConstants.LF_OLDNORM, TarConstants.LF_DIR,
                        TarConstants.LF_LINK, TarConstants.LF_SYMLINK) && !entry.isSparse)
                    require(when (expected.kind) {
                        "directory" -> entry.isDirectory
                        "file" -> entry.isFile && !entry.isSparse
                        "symlink" -> entry.isSymbolicLink
                        "hardlink" -> entry.isLink
                        else -> false
                    })
                    val path = target.resolve(name)
                    if (expected.kind == "directory") directory(path)
                    else if (expected.kind == "file") {
                        directory(path.parent)
                        Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { output ->
                            hooks.created(path)
                            val buffer = ByteArray(65536); var bytes = 0L
                            while (true) {
                                cancelled(); val count = tar.read(buffer); if (count < 0) break
                                bytes += count; require(bytes <= expected.bytes); output.write(buffer, 0, count)
                            }
                            require(bytes == expected.bytes)
                        }
                        mode(path, expected.mode)
                    }
                }
            }
            require(seen == indexed.keys)
            plan.filter { it.kind == "hardlink" }.forEach { directory(target.resolve(it.path).parent) }
            if (hooks.hardlinks != null) hooks.hardlinks.invoke() else materializeHardlinks(target, plan, hooks.created)
            plan.filter { it.kind == "hardlink" }.forEach { entry ->
                val path = target.resolve(entry.path)
                val original = target.resolve(rootfsLinkDestination(entry.path, entry.target, false))
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(path, original)) { "Hardlink batch incomplete" }
            }
            plan.filter { it.kind == "symlink" }.forEach { entry ->
                cancelled(); val path = target.resolve(entry.path); directory(path.parent)
                Files.createSymbolicLink(path, Path.of(entry.target))
                hooks.created(path)
            }
            plan.filter { it.kind == "directory" }.sortedByDescending { it.path.count { c -> c == '/' } }
                .forEach { mode(target.resolve(it.path), it.mode) }
        } catch (failure: Throwable) {
            try { hooks.cleanup(target) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    /** A separate privileged batch can use the same validated paths and link rules. */
    fun materializeHardlinks(root: Path, plan: List<RootfsEntry>, created: (Path) -> Unit = {}, inspect: (Path) -> Unit = {}) {
        validate(plan)
        require(root.isAbsolute && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
        plan.filter { it.kind == "hardlink" }.forEach { entry ->
            cancelled()
            val path = root.resolve(entry.path)
            val source = root.resolve(rootfsLinkDestination(entry.path, entry.target, false))
            listOf(path.parent, source).forEach { candidate ->
                var current = root
                for (part in root.relativize(candidate)) {
                    current = current.resolve(part)
                    require(!Files.isSymbolicLink(current)) { "Hardlink path contains a physical symlink" }
                }
            }
            inspect(path.parent); inspect(source)
            require(Files.isDirectory(path.parent, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
            Files.createLink(path, source); created(path)
        }
    }

    private fun stream(archive: Path, consume: (RootfsTarStream) -> Unit) {
        require(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && Files.size(archive) in 1..budget.compressed)
        GZIPInputStream(Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS), 65536).use { gzip ->
            val bounded = object : FilterInputStream(gzip) {
                var bytes = 0L
                override fun read(): Int {
                    cancelled(); val value = super.read()
                    if (value >= 0) { bytes++; require(bytes <= budget.expanded + 64L * 1024 * 1024) }
                    return value
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    cancelled(); val count = super.read(buffer, offset, length)
                    if (count > 0) { bytes += count; require(bytes <= budget.expanded + 64L * 1024 * 1024) }
                    return count
                }
                override fun skip(count: Long): Long {
                    val buffer = ByteArray(65536); var left = count
                    while (left > 0) { val read = read(buffer, 0, minOf(left, buffer.size.toLong()).toInt()); if (read < 0) break; left -= read }
                    return count - left
                }
            }
            val tar = RootfsTarStream(bounded, budget.entries)
            consume(tar)
            require(tar.eofRecords >= 2) { "Missing TAR end records" }
            // Drain the gzip trailer to verify CRC; only bounded zero TAR padding is accepted.
            val tail = ByteArray(65536); var padding = 0
            while (true) {
                cancelled(); val count = bounded.read(tail); if (count < 0) break
                padding += count; require(padding <= 1024 * 1024 && (0 until count).all { tail[it] == 0.toByte() })
            }
        }
    }
    private fun cancelled() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
}
