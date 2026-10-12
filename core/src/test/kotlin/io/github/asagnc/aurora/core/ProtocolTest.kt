package io.github.asagnc.aurora.core

import java.io.StringReader
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class ProtocolTest {
    @Test fun sseHandlesBomCommentsMixedNewlinesAndFinalEvent() {
        val reader = SseReader(StringReader("\uFEFF: hello\r\nevent: delta\rdata: first\ndata: second\r\n\r\ndata: last"))
        assertEquals(SseEvent("delta", "first\nsecond"), reader.next())
        assertEquals(SseEvent(null, "last"), reader.next())
        assertNull(reader.next())
    }

    @Test fun sseRejectsUnterminatedOversizedLine() {
        assertFailsWith<IllegalArgumentException> { SseReader(StringReader("data: " + "x".repeat(100)), 32).next() }
    }

    @Test fun sseRejectsAggregateEventBudget() {
        assertFailsWith<IllegalArgumentException> {
            SseReader(StringReader("data: 1234567890\ndata: 1234567890\n\n"), 20).next()
        }
    }

    @Test fun chatRejectsDisconnectedStreamEvenWithValidPartialCall() {
        val parser = StreamAssembler(Protocol.CHAT)
        parser.feed("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c","function":{"name":"read","arguments":"{}"}}]}}]}""")
        assertFailsWith<IllegalStateException> { parser.finish() }
    }

    @Test fun chatAssemblesArgumentsAndKeepsReasoning() {
        val parser = StreamAssembler(Protocol.CHAT)
        parser.feed("""{"choices":[{"delta":{"reasoning_content":"reason","tool_calls":[{"index":0,"id":"c","function":{"name":"read","arguments":"{\"path\":"}}]}}]}""")
        parser.feed("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"a\"}"}}]},"finish_reason":"tool_calls"}]}""")
        parser.accept(SseEvent(null, "[DONE]"))
        val turn = parser.finish()
        assertEquals("a", turn.calls.single().arguments.text("path"))
        assertEquals("reason", turn.nativeEntries.single().text("reasoning_content"))
    }

    @Test fun anthropicKeepsOpaqueSignatureAndCountsCacheInputs() {
        val parser = StreamAssembler(Protocol.ANTHROPIC)
        parser.feed("""{"type":"message_start","message":{"usage":{"input_tokens":20,"cache_read_input_tokens":80,"cache_creation_input_tokens":100}}}""")
        parser.feed("""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""")
        parser.feed("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hidden"}}""")
        parser.feed("""{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"opaque"}}""")
        parser.feed("""{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":4}}""")
        parser.feed("""{"type":"message_stop"}""")
        val turn = parser.finish()
        assertEquals(200L, turn.usage.input)
        assertEquals(0.4, turn.usage.cacheShare)
        assertEquals("opaque", turn.nativeEntries.single().array("content").single().jsonObject.text("signature"))
    }

    @Test fun responsesPreservesReasoningItemsAndFunctionCallIds() {
        val parser = StreamAssembler(Protocol.RESPONSES)
        parser.feed("""{"type":"response.completed","response":{"status":"completed","output":[{"type":"reasoning","encrypted_content":"opaque"},{"type":"function_call","call_id":"c","name":"read","arguments":"{}"}],"usage":{"input_tokens":100,"output_tokens":4,"input_tokens_details":{"cached_tokens":80}}}}""")
        val turn = parser.finish()
        assertEquals("opaque", turn.nativeEntries.first().text("encrypted_content"))
        assertEquals("c", turn.calls.single().id)
        assertEquals(0.8, turn.usage.cacheShare)
    }

    @Test fun truncatedToolTurnNeverExecutes() = runTest {
        var executed = false
        val executor = executor { executed = true; "ok" }
        val transport = transport { ModelTurn("", emptyList(), listOf(ToolCall("c", "read", objectOf())), "length", Usage()) }
        assertFailsWith<IllegalArgumentException> { AgentLoop(transport, executor).run(request(), {}, {}) }
        assertFalse(executed)
    }

    @Test fun cancellationIsNeverEncodedAsToolFailure() = runTest {
        var checkpointed = false
        val executor = executor { throw CancellationException("cancel") }
        val transport = transport { ModelTurn("", emptyList(), listOf(ToolCall("c", "read", objectOf())), "tool_calls", Usage()) }
        assertFailsWith<CancellationException> {
            AgentLoop(transport, executor).run(request(), {}, { checkpointed = true })
        }
        assertFalse(checkpointed)
    }

    @Test fun identicalConfigDoesNotInvalidateCache() {
        val connection = request().connection
        assertEquals(CacheImpact.UNCHANGED, CachePolicy.impact(connection, connection.copy()))
        assertEquals(CacheImpact.NEW_SCOPE, CachePolicy.impact(connection, connection.copy(model = "other")))
    }

    private fun StreamAssembler.feed(data: String) = accept(SseEvent(null, data))
    private fun request() = ModelRequest(Connection(name = "test", baseUrl = "https://example.com/v1", protocol = Protocol.CHAT, model = "test"), "system", emptyList(), emptyList())
    private fun executor(action: suspend () -> String) = object : ToolExecutor {
        override val definitions = emptyList<ToolDefinition>()
        override suspend fun execute(call: ToolCall) = action()
    }
    private fun transport(action: () -> ModelTurn) = object : ModelTransport {
        override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit) = action()
    }
}
