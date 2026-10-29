package io.github.asagnc.aurora.root

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.RootManagedRunRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit UUIDs from this acceptance run only; unknown Root state is retained, never forcibly discarded. */
@RunWith(AndroidJUnit4::class)
class FixtureRecoveryDeviceTest {
    @RootRequired @Test fun recoverExactRetainedQualificationOwners() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("auroraRootTest") == "true")
        val owners = requireNotNull(arguments.getString("auroraFixtureRecovery")).split(',')
        require(owners.size in 1..16 && owners.all { UUID.fromString(it).toString() == it })
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
        val names = setOf("Owned socket snapshot qualification", "Owned loopback injection qualification", "Aurora capture rotation qualification fixture",
            "Aurora long capture qualification fixture", "Aurora ordinary App attach qualification fixture", "Aurora owned spawn qualification",
            "Aurora Frida qualification fixture", "Aurora Frida cancellation qualification fixture", "Aurora capture budget qualification fixture")
        try {
            for (owner in owners) {
                val header = app.conversations.header(owner) ?: continue
                require(header.title in names) { "Owner is not a recognized qualification fixture" }
                app.spaces.conversation(owner)
                for (ticket in RootManagedRunRegistry(app.conversations).pending(owner)) {
                    val result = requireNotNull(app.root.managedClient).recover(ticket)
                    println("owned-fixture-recovery $owner " + WireJson.encodeToString(result))
                }
                val remaining = app.managedRuns.recover(owner)
                assertEquals("Unconfirmed fixture retained: $owner", 0, remaining)
                check(app.linux.runs.pendingCount(owner) == 0)
                app.spaces.delete(owner, app.tasks::quiesce)
            }
        } finally { app.root.disconnect() }
    }
}
