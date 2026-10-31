package io.github.asagnc.aurora.reverse

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.LinuxRunRegistry
import io.github.asagnc.aurora.linux.LinuxToolRun
import io.github.asagnc.aurora.root.RootRequired
import io.github.asagnc.aurora.runtime.CommandRequest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Host signals only the exact Root identity from this run's durable Linux ticket. */
@RunWith(AndroidJUnit4::class)
class AndroidBuildFaultDeviceTest {
    @RootRequired @Test fun rootServiceDeathRetiresSdkLeaseWithoutReplayingTheGuest() = runBlocking<Unit> {
        installedAndroidSdkFixture("auroraAndroidBuildRootDeathTest") { app, owner, generation -> supervisorScope {
            val qualified = app.sharedToolchain.probeAndroid(owner, generation) { }
            assertEquals(qualified.toString(), JsonPrimitive(true), qualified["qualified"])
            val identity = requireNotNull(app.root.linuxExecution).identity
            val runId = UUID.randomUUID().toString()
            val command = "set -eu; printf ready > /mnt/owned-sdk-fault-ready; /bin/sleep 60; touch /mnt/owned-sdk-must-not-replay"
            val operation = async { app.linux.runs.executeTools(owner, LinuxToolRun(listOf("/bin/sh", "-c", command),
                AndroidBuildTools.bundleId, 120_000, runId, identity), generation) }
            try {
                waitReady(app, owner, operation)
                val ticket = LinuxRunRegistry(app.conversations).pending(owner).single()
                assertEquals(runId, ticket.runId); assertEquals(identity, ticket.identity)
                assertEquals(AndroidBuildTools.bundleId, ticket.toolBundle)
                assertTrue(app.linux.runs.usesToolBundle(AndroidBuildTools.bundleId))
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("stream", "AURORA_SDK_ROOT_DEATH_READY=${identity.pid}:${identity.startTicks}\n")
                })
                val outcome = withTimeout(35_000) { runCatching { operation.await() } }
                assertFalse("Interrupted guest reported ordinary success", outcome.getOrNull()?.get("completion")
                    ?.takeUnless { it == JsonNull }?.jsonObject?.number("exitCode") == 0L)
                app.root.disconnect(); app.root.connect()
                assertNotEquals(identity, requireNotNull(app.root.linuxExecution).identity)
                assertEquals(JsonPrimitive(false), app.sharedToolchain.status(generation, "android-build")["runtimeReady"])
                val recovered = app.linux.runs.recover(owner)
                assertEquals(recovered.toString(), 0L, recovered.number("retained"))
                assertEquals(0, app.linux.runs.pendingCount(owner))
                assertFalse(app.linux.runs.usesToolBundle(AndroidBuildTools.bundleId))
                val proof = app.linux.runs.execute(owner, listOf("/bin/test", "!", "-e", "/mnt/owned-sdk-must-not-replay"), generation = generation)
                assertEquals(proof.toString(), 0L, proof.getValue("completion").jsonObject.number("exitCode"))
                assertTrue(app.sharedToolchain.status(generation, "android-build").array("installed").contains(string(AndroidBuildTools.bundleId)))
            } finally { operation.cancelAndJoin() }
        } }
    }

    private suspend fun waitReady(app: AuroraApplication, owner: String, operation: Deferred<JsonObject>) = withTimeout(20_000) {
        val file = app.spaces.commandRoot(owner).resolve(".aurora-linux/project/owned-sdk-fault-ready")
        while (true) {
            check(!operation.isCompleted) { "Owned guest exited before fault admission" }
            val response = requireNotNull(app.root.commandClient).execute(owner, CommandRequest(listOf("/system/bin/test", "-f", file.toString()), timeoutMs = 2000))
            if (WireJson.parseToJsonElement(response).jsonObject.number("exitCode") == 0L) break
            delay(50)
        }
    }
}
