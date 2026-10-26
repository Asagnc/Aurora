package io.github.asagnc.aurora.core

import java.io.InputStream
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout

/** Only the compiled reviewed native SDK input can use this larger document-transfer budget. */
object AndroidNativeArchiveTransfer {
    suspend fun copy(target: Path, open: () -> InputStream, cancelOpen: () -> Unit = {}) = withTimeout(180_000) {
        val component = AndroidBuildTools.components.single { it.localOnly }
        OwnedArchiveTransfer.copy(target, component.compressedBytes, open, cancelOpen)
        runInterruptible(Dispatchers.IO) { verifyStaticReverseArchive(component, target) }
    }
}
