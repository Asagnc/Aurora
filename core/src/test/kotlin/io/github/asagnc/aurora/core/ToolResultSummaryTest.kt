package io.github.asagnc.aurora.core

import kotlin.test.*

class ToolResultSummaryTest {
    @Test fun errorDoesNotDependOnFieldOrder() {
        val summary = assertNotNull(summarizeToolResult("""{"uid":0,"exitCode":2,"error":"command_exit"}"""))
        assertEquals("command_exit", summary.error)
        assertEquals(2L, summary.exitCode)
    }
    @Test fun incompleteResultsRemainUnknown() {
        assertNull(summarizeToolResult("""{"error":"command_exit","stdout":"""))
        assertNull(summarizeToolResult("x".repeat(8001)))
        assertNull(summarizeToolResult("[".repeat(65)))
    }
    @Test fun outputAndTruncationArePreserved() {
        val summary = assertNotNull(summarizeToolResult("""{"exitCode":0,"stdout":{"text":"done\n","artifact":"runs/id/stdout.bin","previewTruncated":true},"stderr":{"text":null}}"""))
        assertEquals("done\n", summary.stdout?.text)
        assertEquals("runs/id/stdout.bin", summary.stdout?.artifact)
        assertEquals(true, summary.stdout?.truncated)
        assertNull(summary.stderr?.text)
    }
    @Test fun untrustedArtifactPathsAreNotNavigationTargets() {
        for (path in listOf("/etc/passwd", "runs/../secret", "runs//stdout", "runs/./stdout")) {
            val summary = summarizeToolResult("""{"stdout":{"artifact":"$path"}}""")
            assertNull(summary?.stdout?.artifact)
        }
    }
    @Test fun unknownOutcomeIsNotOrdinaryFailure() {
        assertTrue(assertNotNull(summarizeToolResult("""{"exitCode":null,"error":"command_outcome_uncertain"}""")).uncertain)
    }
}
