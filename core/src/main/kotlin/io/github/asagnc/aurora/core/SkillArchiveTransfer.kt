package io.github.asagnc.aurora.core

import java.io.InputStream
import java.nio.file.Path

/** Ordinary Skills retain their original budget and stream cancellation behavior. */
object SkillArchiveTransfer {
    suspend fun copy(target: Path, open: () -> InputStream, cancelOpen: () -> Unit = {}) =
        OwnedArchiveTransfer.copy(target, 64L * 1024 * 1024, open, cancelOpen)
}
