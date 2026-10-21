package io.github.asagnc.aurora.runtime

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in hardware check; briefly sleeps the display, never changes device power policy. */
@RunWith(AndroidJUnit4::class)
class AgentScreenDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as AuroraApplication

    @RootRequired @Test fun rootTaskCompletesWithDisplayAsleep() = runBlocking {
        verifyFixture(true)
    }

    @RootRequired @Test fun rootTaskCompletesInBackgroundWhileDisplayAwake() = runBlocking {
        verifyFixture(false)
    }

    private suspend fun verifyFixture(screenOff: Boolean) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("auroraScreenTest") == "true")
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        app.ready.await()
        val owner = app.conversations.create("screen-off root fixture")
        val root = RootController(app)
        try {
            app.spaces.commandRoot(owner)
            verifySleepingTask(owner, root, screenOff)
        }
        finally {
            shell("input keyevent 224")
            withContext(Dispatchers.Main) { app.agentTasks.stopAndJoin() }
            root.disconnect()
            app.spaces.delete(owner, app.tasks::quiesce, app.browser::deleteOwner)
        }
    }

    private suspend fun verifySleepingTask(owner: String, root: RootController, screenOff: Boolean) {
        root.connect()
        assertEquals(root.state.value.detail, RootPhase.READY, root.state.value.phase)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        val completed = CompletableDeferred<Throwable?>()
        withContext(Dispatchers.Main) {
            assertTrue(app.agentTasks.start(owner, {
                app.tasks.run(owner) {
                    started.complete(Unit)
                    response.complete(requireNotNull(root.commandClient).execute(owner, CommandRequest(
                        listOf("/system/bin/sh", "-c",
                            "for tick in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do printf 'tick\\n'; sleep 1; done"),
                        timeoutMs = 30_000)))
                }
            }, { completed.complete(it) }))
        }
        withTimeout(10_000) {
            started.await()
            val directory = app.spaces.commandRoot(owner)
            while (!firstTickWritten(directory)) {
                check(!response.isCompleted) { response.await() }
                delay(20)
            }
        }
        shell("input keyevent 3")
        if (screenOff) shell("input keyevent 223")
        val power = shell("dumpsys power").lineSequence().firstOrNull { "mWakefulness=" in it }.orEmpty().trim()
        println("AURORA_SCREEN_STATE=$power")
        assertTrue(power, Regex(if (screenOff) "mWakefulness=(Asleep|Dozing)" else "mWakefulness=Awake").containsMatchIn(power))
        assertNull(withTimeout(40_000) { completed.await() })
        val result = WireJson.parseToJsonElement(response.await()).jsonObject
        assertEquals(result.toString(), 0L, result.number("uid"))
        assertEquals(result.toString(), 0L, result.number("exitCode"))
        val ticks = result["stdout"]!!.jsonObject.text("text")!!.lineSequence().filter { it == "tick" }.count()
        assertEquals(15, ticks)
        assertFalse(withContext(Dispatchers.Main) { app.agentTasks.busy })
    }

    private fun firstTickWritten(directory: Path): Boolean {
        val runs = directory.resolve("runs")
        if (!Files.isDirectory(runs)) return false
        return Files.list(runs).use { paths -> paths.anyMatch {
            val output = it.resolve("stdout.bin")
            Files.isRegularFile(output) && Files.newInputStream(output).use { stream ->
                stream.readNBytes(5).toString(Charsets.UTF_8) == "tick\n"
            }
        } }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }
}
