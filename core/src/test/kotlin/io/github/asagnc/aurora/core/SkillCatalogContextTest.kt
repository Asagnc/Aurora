package io.github.asagnc.aurora.core

import kotlin.test.*

class SkillCatalogContextTest {
    private fun item(version: String) = objectOf("id" to string("example"), "version" to string(version),
        "name" to string("example"), "description" to string("Read files"))
    @Test fun unchangedCatalogIsNotRepeatedForAnyProtocol() {
        Protocol.entries.forEach { protocol ->
            val context = SkillCatalogContext.message(listOf(item("first")))
            val first = SkillCatalogContext.append(protocol, emptyList(), context)
            assertEquals(1, first.size)
            assertTrue(SkillCatalogContext.append(protocol, first, context).isEmpty())
        }
    }
    @Test fun updatesAndRollbacksAppendWithoutChangingPriorEntries() {
        Protocol.entries.forEach { protocol ->
            val one = SkillCatalogContext.message(listOf(item("one")))
            val two = SkillCatalogContext.message(listOf(item("two")))
            val initial = SkillCatalogContext.append(protocol, emptyList(), one)
            val updated = initial + SkillCatalogContext.append(protocol, initial, two)
            assertEquals(initial, updated.take(initial.size))
            assertEquals(1, SkillCatalogContext.append(protocol, updated, one).size)
        }
    }
}
