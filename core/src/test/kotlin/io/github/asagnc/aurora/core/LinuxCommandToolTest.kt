package io.github.asagnc.aurora.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class LinuxCommandToolTest {
    private fun proof(code: Int = 0, stopped: Boolean = true, timeout: Boolean = false) = objectOf(
        "protocol" to JsonPrimitive(1), "runId" to string("run"), "stopped" to JsonPrimitive(stopped),
        "completion" to objectOf("exitCode" to JsonPrimitive(code), "scopeRemoved" to JsonPrimitive(true),
            "interrupted" to JsonPrimitive(false), "timedOut" to JsonPrimitive(timeout)))
    private val tool = LinuxCommandTool({ _, _, _ -> error("Unexpected dispatch") }, { _, _ -> })

    @Test fun cleanupDoesNotInventCommandSuccess() {
        assertEquals("exited", tool.project("run", proof(37)).text("state"))
        assertEquals(37L, tool.project("run", proof(37)).number("exitCode"))
        assertEquals(JsonPrimitive(false), tool.project("run", proof(37))["success"])
        assertEquals(JsonPrimitive(true), tool.project("run", proof())["success"])
        assertEquals("timed_out", tool.project("run", proof(timeout = true)).text("state"))
        assertEquals("uncertain", tool.project("run", proof(stopped = false)).text("state"))
        val recovered = JsonObject(proof() + ("completion" to JsonNull))
        assertEquals("outcome_unknown", tool.project("run", recovered).text("state"))
        assertEquals(JsonNull, tool.project("run", recovered)["exitCode"])
        assertFails { tool.project("another", proof()) }
    }

    @Test fun validationHappensBeforeArtifactsOrDispatch() = runBlocking {
        for (args in listOf(objectOf("argv" to JsonArray(listOf(string("sh")))),
            objectOf("argv" to JsonArray(listOf(string("/bin/sh"))), "timeoutMs" to JsonPrimitive(270001)))) {
            assertFails { tool.execute(ToolCall("call", "command_execute_linux", args)) }
        }
    }

    @Test fun dispatchUsesThePreviouslyAttachedArtifactIdentity() = runBlocking {
        var attached: String? = null
        var dispatches = 0
        val executor = LinuxCommandTool({ id, argv, timeout ->
            assertEquals("runs/$id", attached)
            assertEquals(listOf("/bin/sh", "-c", "printf hello"), argv)
            assertEquals(1234L, timeout)
            dispatches++
            JsonObject(proof(37) + ("runId" to string(id)))
        }, { call, path -> assertEquals("call", call); attached = path })
        val result = WireJson.parseToJsonElement(executor.execute(ToolCall("call", "command_execute_linux",
            objectOf("argv" to JsonArray(listOf("/bin/sh", "-c", "printf hello").map(::string)),
                "timeoutMs" to JsonPrimitive(1234))))).jsonObject
        assertEquals(1, dispatches)
        assertEquals(37L, result.number("exitCode"))
        assertEquals(JsonPrimitive(false), result["replaySafe"])
    }
}
