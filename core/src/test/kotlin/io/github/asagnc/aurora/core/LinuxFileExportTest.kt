package io.github.asagnc.aurora.core

import kotlin.test.*

class LinuxFileExportTest {
    @Test fun largeApkTransportDoesNotWidenOrdinaryFileOrInlineReadBudgets() {
        assertEquals(32L * 1024 * 1024, LinuxFileExport("project/owned.bin").maxBytes)
        assertEquals(512L * 1024 * 1024, LinuxFileExport("project/app-release.apk", apk = true).maxBytes)
        assertFails { LinuxFileExport("project/owned.apk", maxBytes = 32L * 1024 * 1024 + 1) }
        assertFails { LinuxFileExport("project/owned.apk", apk = true, maxBytes = 512L * 1024 * 1024 + 1) }
        assertFails { LinuxFileExport("project/owned.bin", apk = true) }
    }
    @Test fun namespaceTraversalPrivateCachesAndMalformedSelectionAreRejected() {
        for (path in listOf("/etc/private.apk", "project/../other.apk", "project//bad.apk", "project/./bad.apk", ".aurora-proxy-private/owned.apk",
            "project/a\\b.apk", "project/a\n.apk")) assertFails { LinuxFileExport(path, apk = true) }
        assertFails { LinuxFileExport("project/owned.apk", apk = true, sha256 = "wrong") }
        LinuxFileExport("project/owned.apk", apk = true, sha256 = "a".repeat(64))
    }
}
