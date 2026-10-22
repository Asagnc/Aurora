package io.github.asagnc.aurora.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class LinuxFileToolRouterTest {
    private class Ordinary : ToolExecutor {
        var calls = 0
        override val definitions = listOf(ToolDefinition("files_read_batch", "Read files", JsonObject(emptyMap())))
        override suspend fun execute(call: ToolCall): String {
            calls++
            return objectOf("files" to JsonArray(call.arguments.array("files").map { objectOf("path" to string(requireNotNull(it.jsonObject.text("path")))) })).toString()
        }
    }
    private fun call(name: String, vararg paths: String) = ToolCall("call", name, objectOf("space" to string("conversation"),
        (if (name == "files_edit_batch") "edits" else "files") to JsonArray(paths.map { objectOf("path" to string(it)) })))
    @Test fun mixedReadPreservesOrderAndRoutesEachDomainAsOneBatch() = runTest {
        val ordinary = Ordinary(); var rootCalls = 0
        val router = LinuxFileToolRouter(ordinary) { _, arguments ->
            rootCalls++
            assertEquals(listOf("project/a", "home/b"), arguments.array("files").map { it.jsonObject.text("path") })
            objectOf("files" to JsonArray(arguments.array("files").map { objectOf("path" to it.jsonObject["path"]!!) }))
        }
        val paths = arrayOf("plain", ".aurora-linux/project/a", ".aurora-linux-other/file", ".aurora-linux/home/b")
        val result = WireJson.parseToJsonElement(router.execute(call("files_read_batch", *paths))).jsonObject
        assertEquals(paths.toList(), result.array("files").map { it.jsonObject.text("path") })
        assertEquals(1, ordinary.calls); assertEquals(1, rootCalls)
    }
    @Test fun mixedEditsFailBeforeEitherBackendCanWrite() = runTest {
        val ordinary = Ordinary(); var rootCalls = 0
        val router = LinuxFileToolRouter(ordinary) { _, _ -> rootCalls++; JsonObject(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { router.execute(call("files_edit_batch", "plain", ".aurora-linux/project/a")) }
        assertEquals(0, ordinary.calls); assertEquals(0, rootCalls)
    }
    @Test fun incompleteRootResponseIsNeverFilledWithFabricatedResults() = runTest {
        val router = LinuxFileToolRouter(Ordinary()) { _, _ -> objectOf("files" to JsonArray(emptyList())) }
        assertFailsWith<IllegalArgumentException> { router.execute(call("files_read_batch", ".aurora-linux/project/a")) }
    }
    @Test fun rootDirectoryListingRestoresConversationRelativePaths() = runTest {
        val router = LinuxFileToolRouter(Ordinary()) { name, arguments ->
            assertEquals("files_list", name); assertEquals("", arguments.text("path"))
            objectOf("entries" to JsonArray(listOf(objectOf("path" to string("project"), "directory" to JsonPrimitive(true)))))
        }
        val result = router.execute(ToolCall("list", "files_list", objectOf("space" to string("conversation"), "path" to string(".aurora-linux"))))
        assertEquals(".aurora-linux/project", WireJson.parseToJsonElement(result).jsonObject.array("entries").single().jsonObject.text("path"))
    }
    @Test fun schemasStayStableWhenRootAvailabilityChanges() {
        val ordinary = Ordinary()
        val connected = LinuxFileToolRouter(ordinary) { _, _ -> JsonObject(emptyMap()) }
        val disconnected = LinuxFileToolRouter(ordinary) { _, _ -> error("Root disconnected") }
        assertEquals(connected.definitions, disconnected.definitions)
        assertFalse(LinuxFileToolRouter.isLinux(".aurora-linux-other"))
    }
}
