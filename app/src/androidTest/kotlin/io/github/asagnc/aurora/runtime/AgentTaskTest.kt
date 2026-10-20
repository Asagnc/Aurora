package io.github.asagnc.aurora.runtime

import android.app.NotificationManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentTaskTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as AuroraApplication

    @Test fun foregroundTaskRejectsDuplicatesAndStopWaitsForCleanup() = runBlocking {
        foreground()
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Throwable?>()
        withContext(Dispatchers.Main) {
            assertTrue(app.agentTasks.start("fixture", {
                try { started.complete(Unit); awaitCancellation() }
                finally { withContext(NonCancellable) { delay(80); cleaned.complete(Unit) } }
            }, { completed.complete(it) }))
            assertFalse(app.agentTasks.start("duplicate", { fail("duplicate ran") }, {}))
        }
        try {
            withTimeout(10_000) { started.await() }
            val manager = app.getSystemService(NotificationManager::class.java)
            withTimeout(5_000) { while (manager.activeNotifications.none { it.id == 1001 }) delay(20) }
            val id = withContext(Dispatchers.Main) { app.agentTasks.activeId!! }
            withContext(Dispatchers.Main) {
                app.agentTasks.stop("stale-id")
                assertTrue(app.agentTasks.busy)
                // Exercise the real notification action's service Intent path.
                app.startService(Intent(app, AgentTaskService::class.java)
                    .setAction("io.github.asagnc.aurora.STOP_AGENT").putExtra("run", id))
            }
            assertTrue(withTimeout(10_000) { completed.await() } is CancellationException)
            assertTrue(cleaned.isCompleted)
            awaitIdle()
            withTimeout(5_000) { while (manager.activeNotifications.any { it.id == 1001 }) delay(20) }
        } finally { withContext(Dispatchers.Main) { app.agentTasks.stopAndJoin() } }
    }

    @Test fun serviceLossCancelsWorkAndNextRunStartsCleanly() = runBlocking {
        foreground()
        val started = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Throwable?>()
        withContext(Dispatchers.Main) {
            assertTrue(app.agentTasks.start("fixture", { started.complete(Unit); awaitCancellation() }, { completed.complete(it) }))
        }
        try {
            withTimeout(10_000) { started.await() }
            withContext(Dispatchers.Main) {
                val oldStore = androidx.lifecycle.ViewModelStore()
                val old = androidx.lifecycle.ViewModelProvider(oldStore,
                    androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))[ChatViewModel::class.java]
                oldStore.clear()
                val newStore = androidx.lifecycle.ViewModelStore()
                val fresh = androidx.lifecycle.ViewModelProvider(newStore,
                    androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))[ChatViewModel::class.java]
                assertSame(old.state, fresh.state)
                assertTrue(app.agentTasks.busy)
                newStore.clear()
            }
            app.stopService(Intent(app, AgentTaskService::class.java))
            assertTrue(withTimeout(10_000) { completed.await() } is CancellationException)
            awaitIdle()
            foreground()
            val next = CompletableDeferred<Throwable?>()
            withContext(Dispatchers.Main) { assertTrue(app.agentTasks.start("next", {}, { next.complete(it) })) }
            assertNull(withTimeout(10_000) { next.await() })
            awaitIdle()
        } finally { withContext(Dispatchers.Main) { app.agentTasks.stopAndJoin() } }
    }

    @Test fun conversationSwitchDoesNotCancelOrLeakRunProjection() = runBlocking {
        foreground()
        app.ready.await()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = object : ModelTransport {
            override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
                onText("fixture-stream")
                started.complete(Unit)
                release.await()
                return ModelTurn("fixture-result", listOf(MessageProjection.message(Protocol.CHAT, "assistant", "fixture-result")),
                    emptyList(), "stop", Usage(10, 2))
            }
        }
        val controller = withContext(Dispatchers.Main) { ChatController(app) { transport } }
        val first = app.conversations.create("background fixture")
        val other = app.conversations.create("other fixture")
        val connection = Connection(name = "local fixture", baseUrl = "https://fixture.invalid/v1", protocol = Protocol.CHAT, model = "fixture")
        try {
            withContext(Dispatchers.Main) {
                controller.saveConnection(connection, "not-a-real-key").join()
                controller.selectConversation(first).join()
                assertTrue(controller.send("fixture request"))
                assertFalse(controller.send("duplicate"))
            }
            withTimeout(10_000) { started.await() }
            withContext(Dispatchers.Main) { controller.selectConversation(other).join() }
            assertFalse(controller.state.value.running)
            assertTrue(controller.state.value.streaming.isEmpty())
            assertTrue(controller.state.value.messages.isEmpty())
            assertTrue(withContext(Dispatchers.Main) { app.agentTasks.busy })
            // MainActivity is no longer visible; the service must still own the execution.
            app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            release.complete(Unit)
            awaitIdle()
            assertEquals(other, controller.state.value.current?.id)
            assertTrue(controller.state.value.messages.isEmpty())
            withContext(Dispatchers.Main) { controller.selectConversation(first).join() }
            assertEquals("fixture-result", controller.state.value.messages.last().text)
            assertEquals("idle", app.conversations.header(first)!!.runState)
            assertEquals("completed", app.agentRuns.latest(first)!!.state)
        } finally {
            withContext(Dispatchers.Main) {
                app.agentTasks.stopAndJoin()
                controller.delete(listOf(first, other)).join()
                controller.close()
            }
            app.credentials.remove(connection.id)
            app.conversations.write { it.delete("connections", "id = ?", arrayOf(connection.id)) }
        }
    }

    @Test fun deletingRunningConversationWaitsForCancellation() = runBlocking {
        foreground()
        app.ready.await()
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val transport = object : ModelTransport {
            override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
                try { started.complete(Unit); awaitCancellation() }
                finally { withContext(NonCancellable) { delay(80); cleaned.complete(Unit) } }
            }
        }
        val controller = withContext(Dispatchers.Main) { ChatController(app) { transport } }
        val owner = app.conversations.create("delete running fixture")
        val connection = Connection(name = "local fixture", baseUrl = "https://fixture.invalid/v1", protocol = Protocol.CHAT, model = "fixture")
        try {
            withContext(Dispatchers.Main) {
                controller.saveConnection(connection, "not-a-real-key").join()
                controller.selectConversation(owner).join()
                assertTrue(controller.send("fixture request"))
            }
            withTimeout(10_000) { started.await() }
            withTimeout(10_000) { withContext(Dispatchers.Main) { controller.delete(listOf(owner)).join() } }
            assertTrue(cleaned.isCompleted)
            assertNull(app.conversations.header(owner))
            assertNull(app.agentRuns.latest(owner))
            assertFalse(withContext(Dispatchers.Main) { app.agentTasks.busy })
        } finally {
            withContext(Dispatchers.Main) { app.agentTasks.stopAndJoin(); controller.close() }
            app.credentials.remove(connection.id)
            app.conversations.write { it.delete("connections", "id = ?", arrayOf(connection.id)) }
        }
    }

    @Test fun rejectedServiceStartupReleasesAdmissionAndDoesNotRunTools() = runBlocking {
        val deniedContext = object : android.content.ContextWrapper(app) {
            override fun startForegroundService(intent: Intent): android.content.ComponentName? {
                throw SecurityException("fixture startup denied")
            }
        }
        val host = AgentTaskHost(deniedContext)
        val completed = CompletableDeferred<Throwable?>()
        withContext(Dispatchers.Main) {
            assertTrue(host.start("fixture", { fail("tools ran before service admission") }, { completed.complete(it) }))
        }
        assertTrue(withTimeout(5_000) { completed.await() } is SecurityException)
        withTimeout(5_000) { while (withContext(Dispatchers.Main) { host.busy }) delay(20) }
    }

    private fun foreground() {
        instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        instrumentation.waitForIdleSync()
    }

    private suspend fun awaitIdle() = withTimeout(10_000) {
        while (withContext(Dispatchers.Main) { app.agentTasks.busy }) delay(20)
    }
}
