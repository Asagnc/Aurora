package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.ChatMessage
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageTimestampTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun schema10HistoryKeepsUnknownTimeAndNewMessagesKeepExactTimeAfterReopen() = runBlocking {
        val name = "message-time-${UUID.randomUUID()}.db"
        try {
            val owner = AuroraDatabase(context, name).use { original ->
                val store = ConversationStore(original)
                val id = store.create("preserved")
                store.appendMessage(id, ChatMessage(id = "legacy", role = "user", text = "old"))
                original.writableDatabase.apply {
                    execSQL("ALTER TABLE messages DROP COLUMN created_at")
                    version = 10
                }
                id
            }
            val recorded = ChatMessage(id = "new", role = "assistant", text = "new", createdAt = 1794225000000L)
            AuroraDatabase(context, name).use { upgraded ->
                val store = ConversationStore(upgraded)
                assertEquals(11, upgraded.readableDatabase.version)
                assertEquals("legacy", store.messages(owner).single().message.id)
                assertNull(store.messages(owner).single().message.createdAt)
                store.appendMessage(owner, recorded)
            }
            AuroraDatabase(context, name).use { reopened ->
                val store = ConversationStore(reopened)
                val rows = store.messages(owner, limit = 1)
                assertEquals(recorded, rows.single().message)
                val older = store.messages(owner, before = rows.single().sequence).single().message
                assertEquals("old", older.text)
                assertNull(older.createdAt)
                assertEquals("preserved", store.header(owner)?.title)
            }
        } finally { context.deleteDatabase(name) }
    }
}
