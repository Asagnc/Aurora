package io.github.asagnc.aurora.core

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class CacheRequestContractTest {
    @Test fun anthropicMarksStableToolSystemAndMessageWithoutMutatingHistory() {
        val history = listOf(MessageProjection.message(Protocol.ANTHROPIC, "user", "question"))
        val tools = listOf(ToolDefinition("read", "Read", objectOf()), ToolDefinition("write", "Write", objectOf()))
        val encoded = RequestEncoder.encode(request(Protocol.ANTHROPIC).copy(history = history, tools = tools))
        assertNull(encoded["tools"]!!.jsonArray.first().jsonObject["cache_control"])
        assertNotNull(encoded["tools"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNotNull(encoded["system"]!!.jsonArray.single().jsonObject["cache_control"])
        assertNotNull(encoded["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.single().jsonObject["cache_control"])
        assertEquals("question", history.single().text("content"))
    }

    @Test fun anthropicNeverMarksThinkingEmptyTextOrUnknownBlocks() {
        for (block in listOf(objectOf("type" to string("thinking"), "thinking" to string("opaque")),
            objectOf("type" to string("redacted_thinking"), "data" to string("opaque")),
            objectOf("type" to string("text"), "text" to string("")), objectOf("type" to string("future_type")))) {
            val history = listOf(objectOf("role" to string("assistant"), "content" to JsonArray(listOf(block))))
            assertEquals(history, RequestEncoder.encode(request(Protocol.ANTHROPIC).copy(history = history))["messages"]!!.jsonArray.map { it.jsonObject })
        }
    }

    @Test fun routerSessionIsStableOpaqueAndIsolatedFromAnotherScope() {
        val original = request(Protocol.CHAT, "https://openrouter.ai/api/v1").copy(cacheScope = "private-local-scope")
        val first = RequestEncoder.encode(original).text("session_id")!!
        assertEquals(71, first.length)
        assertEquals(first, RequestEncoder.encode(original.copy(connection = original.connection.copy(baseUrl = "HTTPS://OPENROUTER.AI/api/v1"))).text("session_id"))
        assertFalse(first.contains("private-local-scope"))
        assertEquals(first, RequestEncoder.encode(original.copy(connection = original.connection.copy(name = "Renamed"))).text("session_id"))
        assertNotEquals(first, RequestEncoder.encode(original.copy(cacheScope = "another-scope")).text("session_id"))
        assertNull(RequestEncoder.encode(original.copy(cacheScope = null))["session_id"])
    }

    @Test fun routerOnlyEnablesClaudeCachingOnKnownOfficialRoute() {
        for (protocol in listOf(Protocol.CHAT, Protocol.RESPONSES)) {
            val original = request(protocol, "https://openrouter.ai/api/v1", "anthropic/claude-sonnet-5.5").copy(cacheScope = "scope")
            assertNotNull(RequestEncoder.encode(original)["cache_control"])
            assertNull(RequestEncoder.encode(original.copy(connection = original.connection.copy(model = "openai/gpt-6.1-sol")))["cache_control"])
            for (base in listOf("https://openrouter.ai.example/v1", "https://example.com/v1", "https://user@openrouter.ai/api/v1", "http://openrouter.ai/api/v1", "https://openrouter.ai:444/api/v1")) {
                val encoded = RequestEncoder.encode(original.copy(connection = original.connection.copy(baseUrl = base)))
                assertNull(encoded["cache_control"])
                assertNull(encoded["session_id"])
            }
        }
    }

    @Test fun toolLoopKeepsOneRoutingScopeAndNativePrefix() = runTest {
        val seen = mutableListOf<ModelRequest>()
        val first = objectOf("role" to string("assistant"), "content" to string(""))
        val transport = object : ModelTransport {
            override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
                seen += request
                return if (seen.size == 1) ModelTurn("", listOf(first), listOf(ToolCall("call", "read", objectOf())), "tool_calls", Usage())
                    else ModelTurn("done", emptyList(), emptyList(), "stop", Usage())
            }
        }
        val executor = object : ToolExecutor {
            override val definitions = emptyList<ToolDefinition>()
            override suspend fun execute(call: ToolCall) = "result"
        }
        val original = request(Protocol.CHAT, "https://openrouter.ai/api/v1").copy(cacheScope = "scope",
            history = listOf(MessageProjection.message(Protocol.CHAT, "user", "read")))
        AgentLoop(transport, executor).run(original, {}, {})
        assertEquals(2, seen.size)
        assertEquals(seen[0].cacheScope, seen[1].cacheScope)
        assertEquals(seen[0].history, seen[1].history.take(seen[0].history.size))
        assertEquals(RequestEncoder.encode(seen[0])["session_id"], RequestEncoder.encode(seen[1])["session_id"])
    }

    @Test fun compatibleUsageAliasesRemainUnknownWhenMissingAndCanonicalWins() {
        for (field in listOf("cache_read_input_tokens", "cached_tokens", "prompt_cache_hit_tokens")) {
            val assembler = StreamAssembler(Protocol.CHAT)
            assembler.accept(SseEvent(null, """{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"$field":60}}"""))
            assembler.accept(SseEvent(null, "[DONE]"))
            assertEquals(60L, assembler.finish().usage.cacheRead)
        }
        val assembler = StreamAssembler(Protocol.CHAT)
        assembler.accept(SseEvent(null, """{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"cached_tokens":1,"prompt_tokens_details":{"cached_tokens":80}}}"""))
        assembler.accept(SseEvent(null, "[DONE]"))
        assertEquals(80L, assembler.finish().usage.cacheRead)
        val absent = StreamAssembler(Protocol.CHAT)
        absent.accept(SseEvent(null, """{"choices":[{"delta":{},"finish_reason":"stop"}]}"""))
        absent.accept(SseEvent(null, "[DONE]"))
        assertNull(absent.finish().usage.cacheRead)
    }

    @Test fun invalidOrUnreportedUsageNeverBecomesAFabricatedCachePercentage() {
        assertNull(Usage(input = 100, cacheRead = 101).cacheShare)
        assertNull(Usage(input = 100, cacheRead = -1).cacheShare)
        assertNull(Usage(input = 0, cacheRead = 0).cacheShare)
        assertNull(Usage(input = 100).cacheShare)
        assertEquals(0.0, Usage(input = 100, cacheRead = 0).cacheShare)
        assertEquals(1.0, Usage(input = 100, cacheRead = 100).cacheShare)
    }

    private fun request(protocol: Protocol, base: String = "https://example.com/v1", model: String = "test-model") =
        ModelRequest(Connection(name = "Test", baseUrl = base, protocol = protocol, model = model), "system", emptyList(), emptyList())
}
