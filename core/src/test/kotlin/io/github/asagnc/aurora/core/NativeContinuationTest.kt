package io.github.asagnc.aurora.core

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class NativeContinuationTest {
    private val connection = Connection(name = "OpenAI", baseUrl = "https://api.openai.com/v1", protocol = Protocol.RESPONSES,
        model = "gpt-6.1-sol", effort = "low", credentialRevision = "key-one")
    private val original = listOf(MessageProjection.message(Protocol.RESPONSES, "user", "initial"),
        objectOf("type" to string("reasoning"), "encrypted_content" to string("opaque")))
    private fun wire(entries: List<JsonObject> = original) = WireHistory(connection.id, connection.model, connection.protocol,
        "low", entries, connection.credentialRevision, connection.baseUrl, "prefix")

    @Test fun updatePreservesOriginalPrefixAndRequestEffort() {
        val next = connection.copy(effort = "high")
        val continued = NativeContinuation.append(wire(), next, "prefix", "follow-up")
        val encoded = RequestEncoder.encode(ModelRequest(continued.connection, "system", continued.history, emptyList()))
        assertEquals("low", encoded["reasoning"]!!.jsonObject.text("effort"))
        assertEquals(original, continued.history.take(original.size))
        assertEquals("configuration_update", continued.appended.first().text("type"))
        assertEquals("high", continued.appended.first()["reasoning"]!!.jsonObject.text("effort"))
        assertEquals("user", continued.appended.last().text("role"))
        assertEquals(CacheImpact.PREFIX_PRESERVING, CachePolicy.impact(connection, next))
    }

    @Test fun unchangedEffectiveEffortDoesNotAppendDuplicateUpdate() {
        val first = NativeContinuation.append(wire(), connection.copy(effort = "high"), "prefix", "one")
        val second = NativeContinuation.append(wire(first.history), connection.copy(effort = "high"), "prefix", "two")
        assertEquals(1, second.appended.size)
        assertEquals(first.history, second.history.dropLast(1))
        assertEquals("low", second.connection.effort)
    }

    @Test fun revertingExplicitEffortAppendsUpdateAtNewTurnOnly() {
        val high = NativeContinuation.append(wire(), connection.copy(effort = "high"), "prefix", "one")
        val low = NativeContinuation.append(wire(high.history), connection, "prefix", "two")
        assertEquals("low", low.appended.first()["reasoning"]!!.jsonObject.text("effort"))
        assertEquals(high.history, low.history.take(high.history.size))
        assertFalse(low.history.zipWithNext().any { (a, b) -> a.text("type") == "configuration_update" && b.text("type") == "configuration_update" })
    }

    @Test fun autoGatewayProtocolAndModelChangesCannotUseUpdateShortcut() {
        assertFalse(CachePolicy.canReuse(wire(), connection.copy(effort = "auto"), "prefix"))
        val gateway = connection.copy(baseUrl = "https://gateway.example/v1", official = true)
        assertEquals(CacheImpact.UNKNOWN, CachePolicy.impact(gateway, gateway.copy(effort = "high")))
        assertFalse(CachePolicy.supportsEffortUpdates(gateway))
        assertFalse(CachePolicy.canReuse(wire(), connection.copy(protocol = Protocol.CHAT), "prefix"))
        assertFalse(CachePolicy.canReuse(wire(), connection.copy(model = "gpt-6-luna"), "prefix"))
        assertFalse(CachePolicy.canReuse(wire(), connection.copy(authentication = Authentication.BEARER), "prefix"))
        assertFalse(CachePolicy.canReuse(wire(), connection.copy(credentialRevision = "key-two"), "prefix"))
        assertFalse(CachePolicy.canReuse(wire(), connection, "changed"))
    }

    @Test fun unknownThinkingChangeAndInterruptedRunProjectFactsInsteadOfOpaqueState() {
        val conversation = Conversation(wire = wire(), messages = listOf(ChatMessage(role = "user", text = "facts")))
        val interrupted = MessageProjection.history(conversation.copy(interrupted = true), connection, "prefix")
        assertEquals("facts", interrupted.single().array("content").single().jsonObject.text("text"))
        val claude = connection.copy(baseUrl = "https://api.anthropic.com/v1", protocol = Protocol.ANTHROPIC, model = "claude-opus-5-5")
        val history = wire().copy(baseUrl = claude.baseUrl, protocol = claude.protocol, model = claude.model)
        assertFalse(CachePolicy.canReuse(history, claude.copy(effort = "high"), "prefix"))
        assertEquals(CacheImpact.THINKING_INVALIDATION, CachePolicy.impact(claude, claude.copy(effort = "high")))
    }

    @Test fun storedUpdateSurvivesSerializationAndOmittedInitialEffort() {
        val automatic = wire().copy(effort = "auto")
        val continued = NativeContinuation.append(automatic, connection.copy(effort = "high"), "prefix", "one")
        val saved = automatic.copy(entries = continued.history)
        val reopened = WireJson.decodeFromString(WireHistory.serializer(), WireJson.encodeToString(WireHistory.serializer(), saved))
        val next = NativeContinuation.append(reopened, connection.copy(effort = "high"), "prefix", "two")
        assertEquals("high", CachePolicy.effectiveEffort(reopened))
        assertNull(RequestEncoder.encode(ModelRequest(next.connection, "system", next.history, emptyList()))["reasoning"])
        assertEquals(1, next.appended.size)
    }

    @Test fun toolLoopKeepsFrozenBaselineAndExecutesEachCallOnce() = runTest {
        val continued = NativeContinuation.append(wire(), connection.copy(effort = "high"), "prefix", "follow-up")
        val requests = mutableListOf<ModelRequest>()
        var executions = 0
        val transport = object : ModelTransport {
            override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
                requests += request.copy(history = request.history.toList())
                return if (requests.size == 1) ModelTurn("", listOf(objectOf("type" to string("function_call"),
                    "call_id" to string("once"), "name" to string("read"), "arguments" to string("{}"))),
                    listOf(ToolCall("once", "read", objectOf())), "completed", Usage())
                else ModelTurn("done", listOf(MessageProjection.message(Protocol.RESPONSES, "assistant", "done")), emptyList(), "completed", Usage())
            }
        }
        val executor = object : ToolExecutor {
            override val definitions = emptyList<ToolDefinition>()
            override suspend fun execute(call: ToolCall): String { executions++; return "actual result" }
        }
        val checkpoints = mutableListOf<List<JsonObject>>()
        AgentLoop(transport, executor).run(ModelRequest(continued.connection, "system", continued.history, emptyList()), {}, { checkpoints += it })
        assertEquals(1, executions)
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.connection.effort == "low" })
        assertTrue(requests.all { request -> request.history.count { it.text("type") == "configuration_update" } == 1 })
        assertEquals(continued.history, requests.last().history.take(continued.history.size))
        assertEquals("function_call_output", requests.last().history.last().text("type"))
        assertEquals(2, checkpoints.size)
    }
}
