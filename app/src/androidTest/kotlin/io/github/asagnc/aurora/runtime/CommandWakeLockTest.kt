package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommandWakeLockTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun overlappingCommandsOwnIndependentLeases() {
        val awake = CommandWakeLock(context, "LeaseTest")
        val first = awake.acquire(5_000)
        val second = awake.acquire(5_000)
        try {
            assertTrue(first.held)
            assertTrue(second.held)
            first.close()
            assertFalse(first.held)
            assertTrue(second.held)
        } finally { first.close(); second.close() }
        assertFalse(second.held)
    }

    @Test fun systemTimeoutReleasesAbandonedLease() = runBlocking {
        val lease = CommandWakeLock(context, "LeaseTimeoutTest").acquire(1_000)
        try {
            assertTrue(lease.held)
            withTimeout(5_000) { while (lease.held) delay(20) }
            assertFalse(lease.held)
        } finally { lease.close() }
    }
}
