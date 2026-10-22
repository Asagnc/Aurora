package io.github.asagnc.aurora.root

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootConnectionTest {
    @RootRequired @Test fun authorizedServiceConnectsDisconnectsAndReconnects() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true") {
            "Run explicitly after authorizing Aurora in the root manager"
        }
        val root = RootController(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals(RootPhase.ORDINARY, root.state.value.phase)
        try {
            repeat(2) {
                root.connect()
                assertEquals(root.state.value.detail, RootPhase.READY, root.state.value.phase)
                assertTrue(root.state.value.detail.contains("UID 0"))
                assertFalse("Root status must be safe for Android accessibility/XML", root.state.value.detail.contains('\u0000'))
                val captured = requireNotNull(root.linuxExecution)
                val identity = requireNotNull(root.linuxClient).identity
                assertEquals(identity, captured.identity)
                assertNotNull(captured.terminal)
                root.disconnect()
                assertEquals(RootPhase.ORDINARY, root.state.value.phase)
                assertNull(root.linuxExecution)
                assertNull(root.linuxClient)
                assertNull(root.commandClient)
                assertNull(root.metadataClient)
                assertEquals("Disconnect must not mutate an already captured identity", identity, captured.identity)
            }
        } finally { root.disconnect() }
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RootRequired
