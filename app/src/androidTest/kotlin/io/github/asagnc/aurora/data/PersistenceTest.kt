package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.util.UUID
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun appendAndPaginationSurviveDatabaseReopen() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val store = ConversationStore(database)
            val id = store.create("fixture")
            repeat(5) { store.appendMessage(id, ChatMessage(role = "user", text = "message-$it")) }
            store.setRunState(id, "running")
            database.close()
            val reopened = AuroraDatabase(context, name)
            try {
                val saved = ConversationStore(reopened)
                saved.recoverInterruptedRuns()
                assertEquals("interrupted", saved.header(id)?.runState)
                val tail = saved.messages(id, limit = 2)
                assertEquals(listOf("message-3", "message-4"), tail.map { it.message.text })
                assertEquals(listOf("message-1", "message-2"), saved.messages(id, before = tail.first().sequence, limit = 2).map { it.message.text })
            } finally { reopened.close() }
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun nativeAppendConflictsDoNotRewriteExistingPrefix() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val conversations = ConversationStore(database)
            val native = NativeHistoryStore(conversations)
            val id = conversations.create()
            val initial = MessageProjection.message(Protocol.CHAT, "user", "initial")
            val history = WireHistory("connection", "model", Protocol.CHAT, "auto", listOf(initial))
            val scope = native.begin(id, history)
            val appended = MessageProjection.message(Protocol.CHAT, "assistant", "answer")
            native.append(id, scope, 1, listOf(appended))
            var rejected = false
            try { native.append(id, scope, 1, listOf(appended)) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(listOf(initial, appended), native.load(id, scope)?.entries)
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun keystoreSecretsSurviveRecreationAndSameKeyKeepsRevision() = runBlocking {
        val id = UUID.randomUUID().toString()
        val vault = CredentialVault(context)
        try {
            val revision = vault.put(id, "fixture-secret")
            val stored = java.io.File(context.noBackupFilesDir, "credentials/$id.json").readText()
            assertFalse(stored.contains("fixture-secret"))
            assertEquals(revision, vault.put(id, "fixture-secret"))
            val recovered = CredentialVault(context).get(id)
            assertEquals("fixture-secret", recovered?.token)
            assertEquals(revision, recovered?.revision)
            assertFalse(recovered.toString().contains("fixture-secret"))
            assertNotEquals(revision, vault.put(id, "fixture-changed"))
        } finally { vault.remove(id) }
    }

    @Test fun deletingConversationNeverFollowsLinkIntoSharedWorkspace() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        val sharedPath = context.filesDir.toPath().resolve("spaces/workspace/shared-${UUID.randomUUID()}")
        try {
            val store = ConversationStore(database)
            val spaces = SpaceStore(context, store)
            val id = store.create()
            spaces.conversation(id).editBatch(listOf(FileEdit("owned", null, "discard")))
            Files.createDirectories(sharedPath.parent)
            Files.writeString(sharedPath, "retain")
            val ownerPath = context.filesDir.toPath().resolve("spaces/conversations/$id")
            Files.createSymbolicLink(ownerPath.resolve("shared-link"), sharedPath)
            spaces.delete(id) {}
            assertNull(store.header(id))
            assertFalse(Files.exists(ownerPath))
            assertEquals("retain", Files.readString(sharedPath))
        } finally { database.close(); context.deleteDatabase(name); Files.deleteIfExists(sharedPath) }
    }

    @Test fun interruptedDeletionResumesFromPersistentFlag() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val store = ConversationStore(database)
            val spaces = SpaceStore(context, store)
            val id = store.create()
            spaces.conversation(id).editBatch(listOf(FileEdit("owned", null, "discard")))
            store.write { db -> db.update("conversations", values("deleting" to 1), "id = ?", arrayOf(id)) }
            spaces.recoverDeletes()
            assertNull(store.header(id))
            assertFalse(Files.exists(context.filesDir.toPath().resolve("spaces/conversations/$id")))
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun interruptedToolsBecomeUncertainWithoutReplayingCompletedTools() = runBlocking {
        val name = "test-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val store = ConversationStore(database)
            val tools = ToolRunStore(store)
            val id = store.create()
            val completed = tools.start(id, ToolCall("a", "write", objectOf()))
            tools.finish(completed, "committed")
            tools.start(id, ToolCall("b", "write", objectOf()))
            store.recoverInterruptedRuns()
            assertEquals(listOf("completed", "uncertain"), tools.list(id).map { it.state })
            assertEquals("committed", tools.list(id).first().result)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
