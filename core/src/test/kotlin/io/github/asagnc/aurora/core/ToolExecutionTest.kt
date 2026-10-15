package io.github.asagnc.aurora.core

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ToolExecutionTest {
    @Test fun combinedUsageIncludesAllRequestsAndDoesNotInventMissingCounters() {
        val result = Usage(100, 10, 80, null) + Usage(150, 20, 100, 0)
        assertEquals(250L, result.input)
        assertEquals(30L, result.output)
        assertEquals(180L, result.cacheRead)
        assertNull(result.cacheWrite)
    }
    @Test fun repeatedCallIdAcrossModelRoundsDoesNotRepeatAction() = runTest {
        var executed = 0
        val tools = object : ToolExecutor {
            override val definitions = emptyList<ToolDefinition>()
            override suspend fun execute(call: ToolCall): String { executed++; return "ok" }
        }
        val transport = object : ModelTransport {
            override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit) =
                ModelTurn("", emptyList(), listOf(ToolCall("same", "act", objectOf())), "tool_calls", Usage())
        }
        assertFailsWith<IllegalArgumentException> {
            AgentLoop(transport, tools).run(ModelRequest(Connection(name = "fixture", baseUrl = "https://fixture.invalid",
                protocol = Protocol.CHAT, model = "fixture"), "", emptyList(), emptyList()), {}, {})
        }
        assertEquals(1, executed)
    }

    @Test fun toolNamesMustHaveOneOwnerAndUnknownCallsDoNotReachExecutor() = runTest {
        var executed = false
        val tools = object : ToolExecutor {
            override val definitions = listOf(ToolDefinition("one", "", objectOf()))
            override suspend fun execute(call: ToolCall): String { executed = true; return "ok" }
        }
        assertFailsWith<IllegalArgumentException> { ToolRouter(listOf(tools, tools)) }
        assertFailsWith<IllegalArgumentException> { ToolRouter(listOf(tools)).execute(ToolCall("id", "absent", objectOf())) }
        assertFalse(executed)
    }
}
