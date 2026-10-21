package io.github.asagnc.aurora.core

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class CommandPresentationTest {
    @Test fun largeQuotedLogsDoNotHideFailureOrCaptureBudget() {
        val original = objectOf("stdout" to objectOf("text" to string("{\"log\":1}\n".repeat(2000)),
            "artifact" to string("runs/id/stdout.bin"), "bytes" to JsonPrimitive(9000000), "storedBytes" to JsonPrimitive(8388608),
            "artifactTruncated" to JsonPrimitive(true)), "exitCode" to JsonPrimitive(2), "error" to string("command_exit")).toString()
        assertTrue(original.length > 8000)
        val compact = assertNotNull(compactCommandResult(original))
        assertTrue(compact.length <= 8000)
        val summary = assertNotNull(summarizeToolResult(compact))
        assertEquals("command_exit", summary.error)
        assertEquals(2L, summary.exitCode)
        assertTrue(assertNotNull(summary.stdout).artifactClipped)
        assertEquals(9000000L, summary.stdout.totalBytes)
        assertTrue(summary.stdout.truncated)
        assertTrue(original.contains("log"))
    }
    @Test fun invalidOrDeepJsonIsNotParsedAsAResult() {
        assertNull(compactCommandResult("[".repeat(65) + "]".repeat(65)))
        assertNull(compactCommandResult("""{"error":"broken"""))
        assertTrue(jsonWithinDepth("""{"text":"\\\"[[[[{{{{"}"""))
    }
}
