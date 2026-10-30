package io.github.asagnc.aurora.root

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import io.github.asagnc.aurora.runtime.NativeCommandRunner
import io.github.asagnc.aurora.runtime.SystemSnapshotTool
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run only after the operator has denied Aurora in the actual Root manager. */
@RunWith(AndroidJUnit4::class)
class RootAuthorizationDeniedDeviceTest {
    @RootRequired @Test fun deniedRootCreatesNoPrivilegedEndpointAndOrdinaryFeaturesStillWork() = runBlocking<Unit> {
        check(InstrumentationRegistry.getArguments().getString("auroraRootDeniedTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        FridaDeviceForeground(app).use {
            app.root.disconnect()
            val root = Files.createTempDirectory(app.cacheDir.toPath(), "root-denied-owned-")
            try {
                app.root.connect()
                assertEquals(app.root.state.value.detail, RootPhase.UNAVAILABLE, app.root.state.value.phase)
                assertNull(app.root.commandClient); assertNull(app.root.fridaConnection)
                assertNull(app.root.linuxClient); assertNull(app.root.packetClient)
                assertTrue(Process.myUid() > 10000)
                val result = NativeCommandRunner(app).execute(root,
                    CommandRequest(listOf("/system/bin/echo", "aurora-owned-root-denied")), UUID.randomUUID().toString())
                assertNull(result.toString(), result["error"])
                assertEquals("android-native", result.text("backend"))
                assertEquals(Process.myUid().toLong(), result.number("uid"))
                assertTrue(result["stdout"]!!.jsonObject.text("text")!!.contains("aurora-owned-root-denied"))
                val snapshot = WireJson.parseToJsonElement(SystemSnapshotTool(app) { "unavailable" }
                    .execute(ToolCall("owned", "system_snapshot", JsonObject(emptyMap())))).jsonObject
                assertFalse(snapshot["sections"]!!.jsonObject["root_connection"]!!.jsonObject["value"]!!
                    .jsonObject["connectionRequested"]!!.jsonPrimitive.boolean)
                assertNull(app.root.fridaConnection)
            } finally {
                app.root.disconnect()
                Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }
    }
}
