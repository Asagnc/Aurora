package io.github.asagnc.aurora.runtime

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit two-process emulator check, excluded from the normal suite. */
@RunWith(AndroidJUnit4::class)
class AgentRunRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as AuroraApplication
    private val preferences get() = app.getSharedPreferences("agent-recovery-fixture", 0)

    @Test fun prepare() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("auroraRecoveryPhase") == "prepare")
        instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        instrumentation.waitForIdleSync()
        app.ready.await()
        val owner = app.conversations.create("process death fixture")
        val run = UUID.randomUUID().toString()
        app.agentRuns.queue(run, owner)
        preferences.edit().putString("owner", owner).putString("run", run).commit()
        val root = app.spaces.commandRoot(owner)
        val started = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            assertTrue(app.agentTasks.start(owner, {
                app.tasks.run(owner) {
                    app.agentRuns.running(run)
                    val tool = app.toolRuns.recording(owner, CommandToolExecutor(app.commands, root))
                    started.complete(Unit)
                    tool.execute(ToolCall("death-fixture", "command_execute", objectOf(
                        "argv" to JsonArray(listOf("/system/bin/sh", "-c", "echo once >> invocation-count; echo ready > running-marker; sleep 240").map(::JsonPrimitive)),
                        "timeoutMs" to JsonPrimitive(300000))))
                }
            }, {}, run))
        }
        withTimeout(10_000) {
            started.await()
            while (!withContext(Dispatchers.IO) { Files.exists(root.resolve("running-marker")) }) delay(20)
        }
        assertEquals("running", app.agentRuns.latest(owner)!!.state)
        assertEquals("started", app.toolRuns.list(owner).single().state)
    }

    @Test fun verifyAfterProcessDeath() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("auroraRecoveryPhase") == "verify")
        app.ready.await()
        val owner = requireNotNull(preferences.getString("owner", null))
        try {
            assertEquals("interrupted", app.agentRuns.latest(owner)!!.state)
            assertEquals("interrupted", app.conversations.header(owner)!!.runState)
            assertEquals("uncertain", app.toolRuns.list(owner).single().state)
            assertFalse(withContext(Dispatchers.Main) { app.agentTasks.busy })
            val count = withContext(Dispatchers.IO) { Files.readAllLines(app.spaces.commandRoot(owner).resolve("invocation-count")) }
            assertEquals(listOf("once"), count)
        } finally {
            app.spaces.delete(owner, app.tasks::quiesce, app.browser::deleteOwner)
            preferences.edit().clear().commit()
        }
    }
}
