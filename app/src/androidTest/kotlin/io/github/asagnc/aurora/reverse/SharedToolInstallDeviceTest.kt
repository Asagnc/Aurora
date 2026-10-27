package io.github.asagnc.aurora.reverse

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootRequired
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit production downloads/install only. Installation does not grant runtime qualification. */
@RunWith(AndroidJUnit4::class)
class SharedToolInstallDeviceTest {
    @RootRequired @Test fun installFixedStaticReverseBundle() = install("static-reverse", StaticReverseTools.bundleId)
    @RootRequired @Test fun installFixedProxyBundle() = install("network-proxy", NetworkProxyRuntime.bundleId)

    private fun install(family: String, expected: String) = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("auroraRootTest") == "true" && arguments.getString("auroraToolInstallTest") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        app.root.connect()
        try {
            check(app.root.linuxExecution != null && app.linux.runs.pendingCount() == 0)
            val before = app.sharedToolchain.status(app.linux.registry.current(), family)
            if (!before.array("installed").contains(string(expected))) {
                val actual = withTimeout(600_000) {
                    if (family == "network-proxy") app.sharedToolchain.proxy.install() else app.sharedToolchain.install()
                }
                assertEquals(expected, actual)
            }
            assertTrue(app.sharedToolchain.status(app.linux.registry.current(), family).array("installed").contains(string(expected)))
        } finally { app.root.disconnect() }
    }
}
