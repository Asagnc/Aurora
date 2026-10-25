package io.github.asagnc.aurora.root

import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.UUID

/** Reads identities only. Never signals termination, replays injection or treats permission errors as exit. */
internal object RootManagedTargetCheck {
    fun exited(state: RootManagedNativeState): RootManagedTargetExitProof? {
        check(Process.myUid() == 0) { "Target recovery observation requires Root" }
        val boot = text("/proc/sys/kernel/random/boot_id", 38).trim()
        require(UUID.fromString(boot).toString() == boot)
        if (boot != state.originalRoot.bootId) return state.targetExitProof(boot, null)
        val target = state.target ?: return null
        val ticks = try { linuxProcessStartTicks(text("/proc/${target.pid}/stat", 65_537)) }
        catch (missing: NoSuchFileException) {
            try { Os.kill(target.pid, 0) }
            catch (failure: ErrnoException) {
                if (failure.errno == OsConstants.ESRCH) return state.targetExitProof(boot, null)
                throw failure
            }
            return null // Hidden /proc or a racing replacement is not confirmed absence.
        }
        return state.targetExitProof(boot, RootTargetIdentity(target.pid, ticks))
    }

    private fun text(path: String, bound: Int): String = Files.newInputStream(Path.of(path)).use {
        val bytes = it.readNBytes(bound)
        require(bytes.isNotEmpty() && bytes.size < bound) { "Process identity record is empty or truncated" }
        bytes.toString(Charsets.UTF_8)
    }
}
