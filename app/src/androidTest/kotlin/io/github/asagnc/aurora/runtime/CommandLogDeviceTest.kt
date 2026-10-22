package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.*
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandLogDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication

    @Test fun observerFindsDirectoriesCreatedAfterSubscriptionAndStopsOnCancel() = runBlocking {
        fixture { owner -> coroutineScope {
            val directory = "runs/${UUID.randomUUID()}"
            val observer = CommandLogObserver(app)
            val updates = Channel<CommandLogSnapshot>(Channel.UNLIMITED)
            val watch = launch { observer.observe(owner, directory, true).collect { updates.send(it) } }
            try {
                assertTrue(withTimeout(5000) { updates.receive() }.pending)
                val path = Files.createDirectories(app.spaces.commandRoot(owner).resolve(directory))
                Files.write(path.resolve("stdout.bin"), "observed".toByteArray())
                val actual = withTimeout(5000) { var value = updates.receive(); while (value.stdout.text != "observed") value = updates.receive(); value }
                assertEquals("observed", actual.stdout.text)
            } finally { watch.cancelAndJoin(); updates.close() }
            assertEquals(0, observer.registeredWatches)
        } }
    }

    @Test fun nativeOutputIsVisibleBeforeProcessExits() = runBlocking {
        fixture { owner -> coroutineScope {
            val root = app.spaces.commandRoot(owner)
            val id = UUID.randomUUID().toString()
            val observer = CommandLogObserver(app)
            val ready = CompletableDeferred<Unit>()
            val watch = launch { observer.observe(owner, "runs/$id", true).collect { if (it.stdout.text.contains("ready")) ready.complete(Unit) } }
            val command = async(Dispatchers.IO) { app.commands.execute(root, CommandRequest(listOf("/system/bin/sh", "-c",
                "printf ready; while [ ! -f release ]; do sleep 0.05; done; printf done"), timeoutMs = 10000), id) }
            try {
                withTimeout(5000) { ready.await() }
                assertTrue(command.isActive)
                Files.write(root.resolve("release"), byteArrayOf(1))
                assertEquals(0L, command.await().number("exitCode"))
            } finally { command.cancelAndJoin(); watch.cancelAndJoin() }
            assertEquals(0, observer.registeredWatches)
        } }
    }

    @Test fun artifactMappingSurvivesFinishAndDatabaseReopen() = runBlocking {
        val name = "log-mapping-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(app, name)
        try {
            val conversations = ConversationStore(database)
            val owner = conversations.create("fixture")
            val store = ToolRunStore(conversations)
            val mappings = listOf("command_execute", "command_execute_root", "command_execute_linux", "linux_environment").associateWith { tool ->
                val id = store.start(owner, ToolCall(tool, tool, objectOf()))
                val directory = "runs/${UUID.randomUUID()}"
                assertEquals(directory, store.attachArtifact(owner, tool, directory).artifactDirectory)
                assertTrue(runCatching { store.attachArtifact(owner, tool, "runs/../secret") }.isFailure)
                assertTrue(runCatching { store.attachArtifact(owner, tool, "runs/${UUID.randomUUID()}") }.isFailure)
                assertEquals(directory, store.finish(id, "{}").artifactDirectory)
                directory
            }
            val image = store.start(owner, ToolCall("image", "images_publish", objectOf()))
            assertTrue(runCatching { store.attachArtifact(owner, "image", "runs/${UUID.randomUUID()}") }.isFailure)
            store.finish(image, "{}")
            database.close()
            val reopened = AuroraDatabase(app, name)
            try {
                val records = ToolRunStore(ConversationStore(reopened)).recent(owner).associateBy { it.tool }
                mappings.forEach { (tool, directory) -> assertEquals(directory, records[tool]!!.artifactDirectory) }
                assertNull(records["images_publish"]!!.artifactDirectory)
            }
            finally { reopened.close() }
        } finally { database.close(); app.deleteDatabase(name) }
    }

    @Test fun versionTwoMigrationPreservesMessagesAndToolResults() = runBlocking {
        val name = "log-migration-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(app, name)
        try {
            val conversations = ConversationStore(database)
            val owner = conversations.create("fixture")
            conversations.appendMessage(owner, ChatMessage(role = "user", text = "preserve"))
            val store = ToolRunStore(conversations)
            store.finish(store.start(owner, ToolCall("old", "command_execute", objectOf())), "{\"exitCode\":0}")
            database.writableDatabase.apply {
                execSQL("ALTER TABLE tool_runs DROP COLUMN artifact_directory")
                execSQL("ALTER TABLE tool_runs DROP COLUMN presentation")
                version = 2
            }
            database.close()
            val upgraded = AuroraDatabase(app, name)
            try {
                val saved = ConversationStore(upgraded)
                assertEquals("preserve", saved.messages(owner).single().message.text)
                val tool = ToolRunStore(saved).recent(owner).single()
                assertEquals("{\"exitCode\":0}", tool.result)
                assertNull(tool.artifactDirectory)
            } finally { upgraded.close() }
        } finally { database.close(); app.deleteDatabase(name) }
    }

    @Test fun deletionCancelsObserverBeforeRemovingFiles() = runBlocking {
        fixture { owner -> coroutineScope {
            val observer = CommandLogObserver(app)
            val ready = CompletableDeferred<Unit>()
            val root = app.spaces.commandRoot(owner)
            val watch = launch { observer.observe(owner, "runs/${UUID.randomUUID()}", true).collect { ready.complete(Unit) } }
            withTimeout(5000) { ready.await() }
            app.spaces.delete(owner, app.tasks::quiesce)
            watch.join()
            assertEquals(0, observer.registeredWatches)
            assertFalse(Files.exists(root))
        } }
    }

    private suspend fun fixture(block: suspend (String) -> Unit) {
        app.ready.await()
        val owner = app.conversations.create("log-test")
        try { block(owner) } finally {
            if (app.conversations.header(owner) != null) app.spaces.delete(owner, app.tasks::quiesce)
        }
    }
}
