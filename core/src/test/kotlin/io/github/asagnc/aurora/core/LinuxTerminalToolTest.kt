package io.github.asagnc.aurora.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class LinuxTerminalToolTest {
    @Test fun defaultsAndLiteralArgumentsReachExactlyOneStart() = runBlocking {
        val requests = mutableListOf<LinuxTerminalOptions>()
        val tool = LinuxTerminalTool { requests += it; objectOf("id" to string("session"), "state" to string("running")) }
        assertEquals("session", WireJson.parseToJsonElement(tool.execute(ToolCall("a", "terminal_start_linux", objectOf()))).jsonObject.text("id"))
        assertEquals(LinuxTerminalOptions(listOf("/bin/sh"), 80, 24, 270000, 0), requests.single())
        tool.execute(ToolCall("b", "terminal_start_linux", objectOf(
            "argv" to JsonArray(listOf("/bin/sh", "-c", "printf '\$HOME'").map(::string)),
            "timeoutMs" to JsonPrimitive(2000), "awakeMs" to JsonPrimitive(1000))))
        assertEquals(2, requests.size)
        assertEquals("printf '\$HOME'", requests.last().argv.last())
        assertEquals(1000L, requests.last().awakeMs)
    }

    @Test fun invalidRequestsNeverStart() = runBlocking {
        val tool = LinuxTerminalTool { error("Must not dispatch") }
        val invalid = listOf(
            objectOf("argv" to JsonArray(emptyList())),
            objectOf("argv" to JsonArray(listOf(string("sh")))),
            objectOf("argv" to JsonArray(listOf(string("/bin/sh\u0000")))),
            objectOf("timeoutMs" to JsonPrimitive(270001)),
            objectOf("timeoutMs" to JsonPrimitive(100), "awakeMs" to JsonPrimitive(101)),
            objectOf("columns" to JsonPrimitive(Long.MAX_VALUE)),
            objectOf("timeoutMs" to string("1000")), objectOf("awakeMs" to JsonNull),
            objectOf("rows" to JsonPrimitive(1)), objectOf("environment" to objectOf()))
        for (args in invalid) assertFailsWith<IllegalArgumentException> {
            tool.execute(ToolCall("invalid", "terminal_start_linux", args))
        }
    }
}
