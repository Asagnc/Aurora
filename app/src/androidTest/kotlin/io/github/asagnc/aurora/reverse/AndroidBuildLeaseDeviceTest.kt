package io.github.asagnc.aurora.reverse

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.LinuxRunRegistry
import io.github.asagnc.aurora.linux.LinuxToolRun
import io.github.asagnc.aurora.root.RootRequired
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit actual installed SDK checks; no shared version is removed by these fixtures. */
@RunWith(AndroidJUnit4::class)
class AndroidBuildLeaseDeviceTest {
    @RootRequired @Test fun installedPublicBaselineMountIsReadOnlyAndHasThePinnedManifest() = runBlocking<Unit> {
        installedAndroidSdkFixture("auroraAndroidBuildLeaseTest") { app, owner, generation ->
            val command = "set -eu; printf '%s\\n' '${AndroidDependencyCache.manifestSha256}  ${AndroidDependencyCache.guestPath}/manifest.json' | sha256sum -c --status; " +
                "if touch '${AndroidDependencyCache.guestPath}/owned-must-not-write' 2>/dev/null; then exit 71; fi"
            assertExit(tools(app, owner, generation, listOf("/bin/sh", "-c", command)))
        }
    }

    @RootRequired @Test fun durableIntentBlocksVersionDeletionAndRecoveryDoesNotReplayItsCommand() = runBlocking<Unit> {
        installedAndroidSdkFixture("auroraAndroidBuildLeaseTest") { app, owner, generation ->
            app.spaces.commandRoot(owner)
            val ticket = LinuxRunTicket(UUID.randomUUID().toString(), owner, generation,
                requireNotNull(app.root.linuxExecution).identity, Process.myUid(),
                listOf("/bin/touch", "/mnt/owned-intent-must-not-replay"), toolBundle = AndroidBuildTools.bundleId)
            val records = LinuxRunRegistry(app.conversations)
            app.sharedToolchain.reserve(AndroidBuildTools.bundleId) { records.begin(ticket) }
            assertTrue(app.linux.runs.usesToolBundle(AndroidBuildTools.bundleId))
            try {
                app.sharedToolchain.remove(AndroidBuildTools.bundleId)
                fail("An unconfirmed durable SDK intent did not block deletion")
            } catch (_: IllegalStateException) { }
            val result = app.linux.runs.recover(owner)
            assertEquals(result.toString(), 1L, result.number("recovered")); assertEquals(0L, result.number("retained"))
            assertFalse(app.linux.runs.usesToolBundle(AndroidBuildTools.bundleId))
            assertExit(tools(app, owner, generation, listOf("/bin/test", "!", "-e", "/mnt/owned-intent-must-not-replay")))
        }
    }

    @RootRequired @Test fun cancellingAnSdkTaskReleasesOnlyItsConfirmedPersistentLease() = runBlocking<Unit> {
        installedAndroidSdkFixture("auroraAndroidBuildLeaseTest") { app, owner, generation -> coroutineScope {
            val task = async { tools(app, owner, generation, listOf("/bin/sleep", "30")) }
            try {
                withTimeout(10_000) { while (app.linux.runs.pendingCount(owner) == 0) { check(!task.isCompleted); delay(25) } }
                assertTrue(app.linux.runs.usesToolBundle(AndroidBuildTools.bundleId))
            } finally { task.cancelAndJoin() }
            assertTrue(task.isCancelled); assertEquals(0, app.linux.runs.pendingCount(owner))
            assertFalse(app.linux.runs.usesToolBundle(AndroidBuildTools.bundleId))
            assertTrue(app.sharedToolchain.status(generation, "android-build").array("installed").contains(string(AndroidBuildTools.bundleId)))
        } }
    }

    private suspend fun tools(app: AuroraApplication, owner: String, generation: String, argv: List<String>) =
        app.linux.runs.executeTools(owner, LinuxToolRun(argv, AndroidBuildTools.bundleId, 60_000,
            UUID.randomUUID().toString(), requireNotNull(app.root.linuxExecution).identity), generation)

    private fun assertExit(result: JsonObject) {
        assertEquals(result.toString(), JsonPrimitive(true), result["stopped"])
        val completion = result.getValue("completion").jsonObject
        assertEquals(result.toString(), 0L, completion.number("exitCode"))
        assertEquals(JsonPrimitive(true), completion["scopeRemoved"])
        assertEquals(JsonPrimitive(false), completion["timedOut"])
    }

}
