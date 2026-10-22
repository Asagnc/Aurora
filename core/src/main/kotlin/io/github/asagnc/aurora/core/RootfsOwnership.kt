package io.github.asagnc.aurora.core

import java.nio.file.*

/** Full preflight before any mutation. Failure during apply requires privileged
 * stage cleanup, not a claim of ownership rollback. Hardlink headers never
 * override their source inode; symlink ownership is applied without chmod. */
object RootfsOwnership {
    fun restore(root: Path, plan: List<RootfsEntry>, apply: (Path, RootfsEntry) -> Unit, inspect: (Path) -> Unit = {}) {
        cancelled()
        RootfsArchive().validate(plan)
        require(root.isAbsolute && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root))
        val directories = linkedMapOf("" to RootfsEntry("", "directory", 0, 0x1ed))
        plan.forEach { entry ->
            var parent = entry.path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                directories.putIfAbsent(parent, RootfsEntry(parent, "directory", 0, 0x1ed))
                parent = parent.substringBeforeLast('/', "")
            }
            if (entry.kind == "directory") directories[entry.path] = entry
        }
        val ordered = plan.filter { it.kind != "directory" } + directories.values
        ordered.forEach { entry ->
            cancelled()
            val path = root.resolve(entry.path)
            var current = root
            for (part in root.relativize(if (path == root) root else path.parent)) {
                current = current.resolve(part)
                require(current.startsWith(root) && Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
            }
            require(when (entry.kind) {
                "directory" -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                "file" -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == entry.bytes
                "symlink" -> Files.isSymbolicLink(path) && Files.readSymbolicLink(path).toString() == entry.target
                "hardlink" -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(path,
                    root.resolve(rootfsLinkDestination(entry.path, entry.target, false)))
                else -> false
            }) { "Rootfs ownership preflight mismatch: ${entry.path}" }
            inspect(path)
        }
        val mutations = plan.filter { it.kind in setOf("file", "symlink") } +
            directories.values.sortedByDescending { it.path.count { c -> c == '/' } * 2 + if (it.path.isEmpty()) 0 else 1 }
        mutations.forEach { entry -> cancelled(); apply(root.resolve(entry.path), entry) }
    }
    private fun cancelled() { if (Thread.currentThread().isInterrupted) throw InterruptedException() }
}
