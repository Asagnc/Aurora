package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*
import kotlin.test.*

class SkillFilePageTest {
    private val manifest = SkillManifest(SkillMetadata("example", "Read files"), (0..130).map {
        SkillFile("references/item-$it.txt", it.toLong(), "a".repeat(64))
    })
    @Test fun paginationReachesResourcesBeyondInitialPreviewWithoutDuplicates() {
        val pages = listOf(0, 64, 128).map { skillFilePage(manifest, offset = it) }
        val paths = pages.flatMap { it["files"]!!.jsonArray.map { item -> item.jsonObject.text("path") } }
        assertEquals(131, paths.size); assertEquals(131, paths.distinct().size)
        assertEquals(false, pages.last()["more"]!!.jsonPrimitive.boolean)
        assertEquals(131, pages.last()["next"]!!.jsonPrimitive.int)
    }
    @Test fun filteringIsCaseInsensitiveAndCursorAppliesToFilteredResults() {
        val result = skillFilePage(manifest, "ITEM-12", 1)
        assertEquals(11, result["total"]!!.jsonPrimitive.int)
        assertEquals(10, result["files"]!!.jsonArray.size)
        assertFails { skillFilePage(manifest, "missing", 1) }
        assertFails { skillFilePage(manifest, offset = -1) }
    }
    @Test fun emptyMatchesAndEndCursorAreExplicitlyComplete() {
        assertEquals(0, skillFilePage(manifest, "missing")["total"]!!.jsonPrimitive.int)
        assertTrue(skillFilePage(manifest, offset = 131)["files"]!!.jsonArray.isEmpty())
        assertFails { skillFilePage(manifest, "a".repeat(201)) }
    }
}
