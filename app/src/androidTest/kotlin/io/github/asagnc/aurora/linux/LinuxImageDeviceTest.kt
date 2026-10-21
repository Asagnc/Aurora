package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinuxImageDeviceTest {
    @LinuxImageRequired @Test fun officialImageRunsFixedOrdinaryProotWorkload() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("auroraLinuxImageTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await()
        app.linux.install()
        val result = app.linux.probe()
        assertEquals(result.toString(), JsonPrimitive(true), result["workloadPassed"])
        assertEquals(JsonPrimitive(false), result["executionReady"])
    }
}
