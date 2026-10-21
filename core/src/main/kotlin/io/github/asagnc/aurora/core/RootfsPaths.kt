package io.github.asagnc.aurora.core

import java.nio.file.*

internal fun rootfsName(value: String, absolute: Boolean = false): String {
    require(value.length <= 4096 && value.none { it == '\\' || it.isISOControl() }) { "Invalid rootfs path" }
    require(absolute || !value.startsWith('/')) { "Absolute archive entry" }
    val parts = value.split('/').filter { it.isNotEmpty() && it != "." }
    require(parts.size <= 64 && parts.none { it == ".." } && !value.matches(Regex("^[A-Za-z]:.*"))) { "Rootfs path escapes its root" }
    return parts.joinToString("/")
}
internal fun rootfsLinkDestination(path: String, target: String, symbolic: Boolean): String {
    require(target.isNotEmpty() && target.length <= 4096 && target.none { it == '\\' || it.isISOControl() })
    val resolved = if (symbolic && !target.startsWith('/')) path.split('/').dropLast(1).toMutableList() else mutableListOf()
    target.split('/').forEach { part -> when (part) {
        "", "." -> Unit
        ".." -> { require(resolved.isNotEmpty()) { "Rootfs link escapes guest root" }; resolved.removeAt(resolved.lastIndex) }
        else -> resolved.add(part)
    } }
    require(resolved.size <= 64)
    return resolved.joinToString("/")
}

/** Absolute guest symlinks are resolved against this guest root, never against the host's /. */
fun resolveRootfsPath(root: Path, guest: String): Path {
    require(root.isAbsolute && !Files.isSymbolicLink(root))
    var pending = rootfsName(guest, absolute = true).split('/').filter(String::isNotEmpty)
    var resolved = mutableListOf<String>()
    var links = 0
    while (pending.isNotEmpty()) {
        val part = pending.first(); pending = pending.drop(1)
        val path = root.resolve((resolved + part).joinToString("/"))
        if (Files.isSymbolicLink(path)) {
            require(++links <= 40) { "Rootfs symlink cycle" }
            val name = (resolved + part).joinToString("/")
            pending = rootfsLinkDestination(name, Files.readSymbolicLink(path).toString(), true).split('/').filter(String::isNotEmpty) + pending
            resolved = mutableListOf()
        } else resolved.add(part)
    }
    return root.resolve(resolved.joinToString("/"))
}
