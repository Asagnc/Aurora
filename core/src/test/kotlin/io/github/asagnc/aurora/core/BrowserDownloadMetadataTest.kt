package io.github.asagnc.aurora.core

import kotlin.test.*

class BrowserDownloadMetadataTest {
    @Test fun displayHintsCannotBecomePathsOrDirectionalControls() {
        assertEquals("report.pdf", browserDownloadMetadata("../folder/report.pdf", null).displayName)
        assertEquals("report.pdf", browserDownloadMetadata("C:\\temp\\report.pdf", null).displayName)
        assertEquals("evil.exe", browserDownloadMetadata("\u202Eevil\u0000.exe\n", null).displayName)
        for (name in listOf(null, "", ".", "..", "\n\u202E"))
            assertEquals("download.bin", browserDownloadMetadata(name, null).displayName)
        val clipped = browserDownloadMetadata("x".repeat(119) + "😀tail", null).displayName
        assertEquals(119, clipped.length)
        assertFalse(clipped.last().isSurrogate())
    }

    @Test fun mimeIsAnUntrustedNormalizedHintWithoutParametersOrWildcards() {
        assertEquals("text/plain", browserDownloadMetadata(null, " Text/Plain; charset=UTF-8 ").mimeType)
        for (mime in listOf(null, "", "text/*", "application", "a/b\nsecret", "a/" + "b".repeat(128)))
            assertEquals("application/octet-stream", browserDownloadMetadata(null, mime).mimeType)
    }

    @Test fun resultProjectionOnlyLinksCanonicalDownloadArtifacts() {
        val path = "runs/12345678-1234-1234-1234-123456789abc/download.bin"
        val result = """{"path":"$path","bytes":42,"displayName":"test.txt","mimeType":"text/plain"}"""
        assertEquals(path, browserDownloadArtifact(result)?.path)
        assertEquals(42L, browserDownloadArtifact(result)?.bytes)
        for (bad in listOf(result.replace(path, "../secret"), result.replace("42", "-1"),
            "[".repeat(65) + "0" + "]".repeat(65), "x".repeat(8001), "null"))
            assertNull(browserDownloadArtifact(bad))
    }

    @Test fun downloadReadyHasStrictBoundedStringFieldsAndDoesNotChangeUploadReady() {
        val ready = """{"kind":"ready","mime":"text/plain","disposition":"attachment; filename=test.txt"}"""
        assertEquals("text/plain", decodeBrowserTransferControl(ready, "download_ready").text("mime"))
        assertFails { decodeBrowserTransferControl(ready, "ready") }
        for (raw in listOf("""{"kind":"ready"}""", """{"kind":"ready","mime":4,"disposition":""}""",
            """{"kind":"ready","mime":"","disposition":"","url":"secret"}""",
            """{"kind":"ready","mime":"${"a".repeat(129)}","disposition":""}"""))
            assertFails { decodeBrowserTransferControl(raw, "download_ready") }
    }
}
