package io.github.asagnc.aurora.root

import android.content.Intent
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity

/** Awake qualification only; never changes system/Thanox background policy. */
internal class FridaDeviceForeground(app: AuroraApplication) : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val activity = instrumentation.startActivitySync(
        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    private var wasKept = false

    init {
        instrumentation.runOnMainSync {
            wasKept = activity.window.attributes.flags and flag != 0
            activity.window.addFlags(flag)
        }
    }

    override fun close() {
        if (!wasKept) instrumentation.runOnMainSync { activity.window.clearFlags(flag) }
    }
}
