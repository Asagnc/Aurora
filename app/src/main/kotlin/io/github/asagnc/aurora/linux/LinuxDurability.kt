package io.github.asagnc.aurora.linux

import android.system.Os
import android.system.OsConstants
import java.nio.file.Path

internal object LinuxDurability {
    fun directory(path: Path) {
        val descriptor = Os.open(path.toString(), OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or
            OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0)
        try { require(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)); Os.fsync(descriptor) }
        finally { Os.close(descriptor) }
    }
}
