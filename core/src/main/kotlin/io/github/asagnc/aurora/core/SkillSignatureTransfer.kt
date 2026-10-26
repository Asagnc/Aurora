package io.github.asagnc.aurora.core

import java.io.InputStream
import kotlinx.coroutines.withTimeout

/** Selected public sidecar bytes, not a key or a credential. No temporary file or network needed. */
object SkillSignatureTransfer {
    suspend fun read(open: () -> InputStream, cancelOpen: () -> Unit = {}): String = withTimeout(10_000) {
        OwnedArchiveTransfer.consume(open, cancelOpen) { input ->
            val bytes = input.readNBytes(1025)
            require(bytes.size <= 1024 && bytes.all { it.toInt() in 0..127 })
            bytes.toString(Charsets.US_ASCII)
        }
    }
}
