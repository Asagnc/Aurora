package io.github.asagnc.aurora.core

import kotlin.test.*

class SkillDocumentTest {
    private fun parse(header: String, body: String = "instructions") = SkillDocumentParser.parse("---\n$header\n---\n$body".toByteArray(), "example")
    @Test fun parsesMultilineQuotedMetadataWithoutTouchingBody() {
        val doc = parse("name: example\ndescription: >-\n  Reads: APKs\n  and files\nmetadata:\n  version: '2.0'\nlicense: MIT", "# Body\n\nkeep: exact\n")
        assertEquals("Reads: APKs and files", doc.metadata.description)
        assertEquals("2.0", doc.metadata.metadata["version"])
        assertEquals("# Body\n\nkeep: exact\n", doc.body)
    }
    @Test fun rejectsMissingDescriptionDuplicateKeysAndNonTextValues() {
        listOf("name: example", "name: example\ndescription: true", "name: example\ndescription: one\ndescription: two").forEach {
            assertFails { parse(it) }
        }
    }
    @Test fun rejectsAliasesTagsAndExcessNesting() {
        assertFails { parse("name: example\ndescription: ok\nmetadata: &m {a: b}\nextra: *m") }
        assertFails { parse("name: example\ndescription: !!java/object anything") }
        assertFails { parse("name: example\ndescription: ok\nextra: " + "[".repeat(20) + "x" + "]".repeat(20)) }
    }
    @Test fun retainsBodyAndWarnsInsteadOfRewritingForeignNames() {
        val doc = parse("name: Example\ndescription: ok\nforeign-field: whatever")
        assertEquals("Example", doc.metadata.name); assertTrue(doc.metadata.warnings.isNotEmpty())
    }
    @Test fun enforcesDocumentAndPathBudgets() {
        assertFails { parse("name: example\ndescription: ok", "x".repeat(262144)) }
        listOf("../secret", "/absolute", "a\\b", "a//b", "C:/x").forEach { assertFails { skillRelativePath(it) } }
    }
}
