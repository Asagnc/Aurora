package io.github.asagnc.aurora.qualification

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApkSafDeviceTest {
    @Test fun realPickerLargeArchiveCompletesFreshProductionInstallation() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName == "io.github.asagnc.aurora.fridaqualification")
        check(InstrumentationRegistry.getArguments().getString("auroraApkSafTest") == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        val activity = instrumentation.startActivitySync(Intent(app, ApkSafQualificationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ApkSafQualificationActivity
        var passed = false
        try {
            withTimeout(30_000) { activity.ready.await() }
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_APK_SAF_READY\n") })
            val id = withTimeout(900_000) { activity.installed.await() }
            assertEquals(AndroidBuildTools.bundleId, id)
            assertNotNull(activity.selectedAuthority)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("stream", "AURORA_APK_SAF_INSTALLED authority=${activity.selectedAuthority} millis=${activity.installMillis}\n")
            })
            assertTrue(string(id) in activity.manager.status(null, "android-build")["installed"]!!.jsonArray)
            assertTrue(activity.manager.remove(id))
            assertFalse(string(id) in activity.manager.status(null, "android-build")["installed"]!!.jsonArray)
            passed = true
        } finally { withContext(NonCancellable) {
            app.root.disconnect()
            instrumentation.runOnMainSync { activity.finish() }
            if (passed) for (component in AndroidBuildTools.components.filter { !it.localOnly })
                java.nio.file.Files.delete(app.cacheDir.toPath().resolve("tool-device-inputs/${component.id}.archive"))
        } }
    }
}
