package io.github.asagnc.aurora.root

import io.github.asagnc.aurora.core.linuxMountPoints
import java.nio.file.Files
import java.nio.file.Path

internal object RootLinuxMountSafety {
    fun requireUnmounted(directory: Path) {
        val bytes = Files.newInputStream(Path.of("/proc/self/mountinfo")).use { it.readNBytes(4 * 1024 * 1024 + 1) }
        require(bytes.size <= 4 * 1024 * 1024)
        val root = directory.toRealPath()
        require(linuxMountPoints(bytes.toString(Charsets.UTF_8)).none { Path.of(it).startsWith(root) }) {
            "Mounted privileged files are retained"
        }
    }
}
