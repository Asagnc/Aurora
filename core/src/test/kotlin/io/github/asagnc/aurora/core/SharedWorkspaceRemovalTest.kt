package io.github.asagnc.aurora.core

import java.nio.file.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SharedWorkspaceRemovalTest {
    @Test fun workspaceRemovalChecksObservedSizeAndLeavesSiblingsAndDirectories() = runTest {
        val root = Files.createTempDirectory("aurora-shared-remove-")
        try {
            Files.write(root.resolve("one"), byteArrayOf(1, 2)); Files.writeString(root.resolve("keep"), "keep")
            Files.createDirectory(root.resolve("dir"))
            val files = OwnedFiles(root)
            assertFails { files.removeFile("one", 3) }; assertTrue(Files.exists(root.resolve("one")))
            assertFails { files.removeFile("dir", 0) }; assertFails { files.removeFile("../escape", 0) }
            files.removeFile("one", 2)
            assertFalse(Files.exists(root.resolve("one"))); assertTrue(Files.exists(root.resolve("keep")))
            assertFails { OwnedFiles(root, writable = false).removeFile("keep", 4) }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun agentCanRemoveSharedWorkspaceButCannotUseThisEntryOnManagedResources() = runTest {
        val root = Files.createTempDirectory("aurora-shared-tool-")
        try {
            Files.writeString(root.resolve("file"), "data")
            val tool = FileToolExecutor(mapOf("workspace" to OwnedFiles(root), "resources" to OwnedFiles(root, false)))
            fun call(space: String) = ToolCall("fixture", "files_remove", objectOf("space" to string(space),
                "path" to string("file"), "expectedSize" to JsonPrimitive(4)))
            assertFails { tool.execute(call("resources")) }
            assertEquals(true, WireJson.parseToJsonElement(tool.execute(call("workspace"))).jsonObject["removed"]?.jsonPrimitive?.boolean)
        } finally { root.toFile().deleteRecursively() }
    }
}
