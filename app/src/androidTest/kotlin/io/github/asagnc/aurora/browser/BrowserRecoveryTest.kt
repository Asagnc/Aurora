package io.github.asagnc.aurora.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserRecoveryTest {
    @Test fun startupRemovesOrphanProfileMetadata() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await()
        val owners = withContext(Dispatchers.Main) { app.browser.profileOwners() }
        owners.forEach { assertNotNull("Orphan browser profile survived recovery", app.conversations.header(it)) }
    }
}
