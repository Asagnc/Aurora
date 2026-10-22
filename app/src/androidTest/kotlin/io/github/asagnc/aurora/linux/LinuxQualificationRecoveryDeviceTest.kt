package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.data.LinuxRunRegistry
import io.github.asagnc.aurora.root.RootRequired
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit retry for retained diagnostic owners, never a blanket database reset. */
@RunWith(AndroidJUnit4::class)
class LinuxQualificationRecoveryDeviceTest {
    @RootRequired @LinuxImageRequired @Test fun recoverOnlyRetainedQualificationOwnersAfterProcessProof() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraLinuxImageTest") == "true" &&
            args.getString("auroraQualificationRecovery") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); app.root.connect()
        try {
            val client = requireNotNull(app.root.linuxRunClient)
            val records = LinuxRunRegistry(app.conversations)
            for (ticket in records.pending()) {
                if (app.conversations.header(ticket.owner)?.title !in setOf("Linux qualification", "Linux PTY qualification")) continue
                val response = client.invoke(ticket, recover = true)
                assertTrue(response.toString(), ticket.confirms(response))
                records.finish(ticket, response)
                app.terminals.forget(ticket.owner)
                app.spaces.delete(ticket.owner, app.tasks::quiesce)
            }
        } finally { app.root.disconnect() }
    }
}
