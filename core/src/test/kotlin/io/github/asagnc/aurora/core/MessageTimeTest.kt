package io.github.asagnc.aurora.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageTimeTest {
    @Test fun oldMessagesDoNotAcquireAnInventedTime() {
        val message = WireJson.decodeFromString(ChatMessage.serializer(),
            """{"id":"old","role":"user","text":"kept","complete":true}""")
        assertNull(message.createdAt)
    }

    @Test fun recordedTimeSurvivesSerialization() {
        val message = ChatMessage(role = "user", text = "kept", createdAt = 1794225000000L)
        val decoded = WireJson.decodeFromString(ChatMessage.serializer(),
            WireJson.encodeToString(ChatMessage.serializer(), message))
        assertEquals(message, decoded)
    }
}
