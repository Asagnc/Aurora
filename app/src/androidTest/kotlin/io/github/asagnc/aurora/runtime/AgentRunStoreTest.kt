package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.ChatMessage
import io.github.asagnc.aurora.data.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentRunStoreTest {
    @Test fun versionOneMigrationPreservesHistoryAndAddsRecoverableTaskRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val initial = AuroraDatabase(context, name)
        try {
            val oldStore = ConversationStore(initial)
            val owner = oldStore.create("preserve me")
            oldStore.appendMessage(owner, ChatMessage(role = "user", text = "saved history"))
            initial.writableDatabase.apply { execSQL("DROP TABLE agent_runs"); version = 1 }
            initial.close()
            val upgraded = AuroraDatabase(context, name)
            try {
                val store = ConversationStore(upgraded)
                val runs = AgentRunStore(store)
                assertEquals("saved history", store.messages(owner).single().message.text)
                val id = UUID.randomUUID().toString()
                runs.queue(id, owner)
                try { runs.queue(UUID.randomUUID().toString(), owner); fail("duplicate active owner accepted") }
                catch (_: android.database.sqlite.SQLiteConstraintException) { }
                runs.running(id)
                store.recoverInterruptedRuns()
                assertEquals("interrupted", runs.latest(owner)!!.state)
                assertEquals("interrupted", store.header(owner)!!.runState)
                val next = UUID.randomUUID().toString()
                runs.queue(next, owner)
                runs.running(next)
                runs.finish(next, "completed")
                assertEquals("completed", runs.latest(owner)!!.state)
                assertEquals("saved history", store.messages(owner).single().message.text)
            } finally { upgraded.close() }
        } finally { initial.close(); context.deleteDatabase(name) }
    }
}
