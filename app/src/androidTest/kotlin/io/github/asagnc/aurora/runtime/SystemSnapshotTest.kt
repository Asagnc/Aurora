package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemSnapshotTest {
    @Test fun ordinaryApisProduceIntervalAndExplicitAvailabilityWithoutRequestingRoot() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tools = SystemSnapshotTool(context) { "ordinary" }
        val result = WireJson.parseToJsonElement(tools.execute(ToolCall("owned", "system_snapshot", JsonObject(emptyMap())))).jsonObject
        assertEquals(false, result["atomic"]!!.jsonPrimitive.boolean)
        assertTrue(result.number("finished")!! >= result.number("started")!!)
        val sections = result["sections"]!!.jsonObject
        assertEquals(setOf("hardware", "memory", "storage", "battery", "root_connection"), sections.keys)
        assertEquals(false, sections["root_connection"]!!.jsonObject["value"]!!.jsonObject["connectionRequested"]!!.jsonPrimitive.boolean)
        sections.values.forEach { section -> assertTrue(section.jsonObject.containsKey("available")) }
        assertFalse(result.toString().contains("serial", ignoreCase = true)); assertFalse(result.toString().contains("android_id"))
    }
    @Test fun duplicateUnknownAndEmptySectionsAreRejectedBeforeAnySampling() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tools = SystemSnapshotTool(context) { error("Must not request Root") }
        for (sections in listOf(emptyList(), listOf("root_connection", "root_connection"), listOf("unknown"))) {
            try {
                tools.execute(ToolCall("owned", "system_snapshot", objectOf("sections" to JsonArray(sections.map(::string)))))
                fail("Invalid request accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
