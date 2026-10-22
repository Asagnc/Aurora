package io.github.asagnc.aurora.root

import android.system.Os
import io.github.asagnc.aurora.core.*
import java.nio.file.Path

internal object RootLinuxOwners {
    fun restore(root: Path, plan: List<RootfsEntry>, appUid: Int) {
        val entries = plan.associateBy { root.resolve(it.path) }
        val aliases = plan.filter { it.kind == "hardlink" }.groupingBy {
            root.resolve(it.target.trimStart('/')).normalize()
        }.eachCount()
        RootfsOwnership.restore(root, plan, apply = { path, entry ->
            Os.lchown(path.toString(), entry.uid.toInt(), entry.gid.toInt())
            if (entry.kind != "symlink") Os.chmod(path.toString(), entry.mode)
            val stat = Os.lstat(path.toString())
            require(stat.st_uid.toUInt().toLong() == entry.uid && stat.st_gid.toUInt().toLong() == entry.gid)
            if (entry.kind != "symlink") require(stat.st_mode and 0x3ff == entry.mode)
        }, inspect = { path ->
            val stat = Os.lstat(path.toString())
            require(stat.st_uid == appUid)
            val entry = entries[path]
            if (entry?.kind in setOf("file", "hardlink")) {
                val source = if (entry!!.kind == "hardlink") root.resolve(entry.target.trimStart('/')).normalize() else path
                require(stat.st_nlink == 1L + (aliases[source] ?: 0)) { "Unexpected rootfs hardlink alias" }
            }
            if (entry?.kind == "symlink") require(stat.st_nlink == 1L)
        })
    }
}
