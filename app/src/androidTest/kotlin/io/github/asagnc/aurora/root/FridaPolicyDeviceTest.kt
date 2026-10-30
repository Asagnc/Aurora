package io.github.asagnc.aurora.root

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Explicit policy operation only; never part of the ordinary device suite. */
@RunWith(AndroidJUnit4::class)
class FridaPolicyDeviceTest {
    @RootRequired @Test fun explicitOwnedAdaptationResetsProbesAndReconnectNeedsNewQualification() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (key in listOf("auroraRootTest", "auroraFridaPolicyTest"))
            check(InstrumentationRegistry.getArguments().getString(key) == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await()
        check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        FridaDeviceForeground(app).use {
            app.root.connect()
            val owner = app.conversations.create("Aurora Frida policy qualification fixture")
            try {
                val connection = requireNotNull(app.root.fridaConnection)
                assertFalse(connection.client.policy(connection.identity, false)["policyQualified"]!!.jsonPrimitive.boolean)
                try {
                    connection.client.policy(connection.identity, true)
                    fail("Unqualified public activation was accepted")
                } catch (_: IllegalArgumentException) { }
                try {
                    connection.client.policy(connection.identity.copy(epoch = UUID.randomUUID().toString()), true, qualification = true)
                    fail("Another service identity was accepted")
                } catch (_: IllegalArgumentException) { }
                val enabled = connection.client.policy(connection.identity, true, qualification = true)
                assertTrue(enabled.toString(), enabled["policyQualified"]!!.jsonPrimitive.boolean)
                assertTrue(enabled["enforcing"]!!.jsonPrimitive.boolean)
                val tools = FridaDeviceTools(app, owner)
                assertTrue(tools.frida(objectOf("action" to string("probe"), "java" to JsonPrimitive(true)))["probeSucceeded"]!!.jsonPrimitive.boolean)
                assertTrue(connection.client.packageInfo(connection.identity).javaProbePassed)
                assertTrue(connection.client.policy(connection.identity, true, qualification = true)["policyQualified"]!!.jsonPrimitive.boolean)
                val reset = connection.client.packageInfo(connection.identity)
                assertFalse(reset.nativeProbePassed); assertFalse(reset.javaProbePassed)
                assertTrue(connection.client.policy(connection.identity, false)["policyQualified"]!!.jsonPrimitive.boolean)
                app.root.disconnect(); app.root.connect()
                val next = requireNotNull(app.root.fridaConnection)
                assertNotEquals(connection.identity, next.identity)
                assertFalse(next.client.policy(next.identity, false)["policyQualified"]!!.jsonPrimitive.boolean)
                assertFalse(next.client.packageInfo(next.identity).nativeProbePassed)
                assertTrue(next.client.policy(next.identity, true, qualification = true)["policyQualified"]!!.jsonPrimitive.boolean)
            } finally {
                withContext(NonCancellable) {
                    try {
                        check(app.managedRuns.recover(owner) == 0)
                        app.spaces.delete(owner, app.tasks::quiesce)
                    } finally { app.root.disconnect() }
                }
            }
        }
    }
}
