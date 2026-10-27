package io.github.asagnc.aurora.root

import io.github.asagnc.aurora.core.initialUserNamespaceConfiguration
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

/** Live kernel qualification, cached only for this Root service lifetime. */
internal object RootSocketNamespace {
    val initialOnly: Boolean by lazy {
        runCatching {
            val compressed = FileInputStream("/proc/config.gz").use { it.readNBytes(524289) }
            require(compressed.size <= 524288)
            val configuration = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readNBytes(2097153) }
            initialUserNamespaceConfiguration(configuration)
        }.getOrDefault(false)
    }
}
