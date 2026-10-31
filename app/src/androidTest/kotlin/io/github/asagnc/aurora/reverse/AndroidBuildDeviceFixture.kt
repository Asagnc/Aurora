package io.github.asagnc.aurora.reverse

import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxAgentEnvironment
import io.github.asagnc.aurora.root.FridaDeviceForeground
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal suspend fun installedAndroidSdkFixture(flag: String, block: suspend (AuroraApplication, String, String) -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString(flag) == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        FridaDeviceForeground(app).use {
            app.root.connect()
            check(app.root.linuxExecution != null && app.linux.registry.current() != null && app.linux.runs.pendingCount() == 0)
            val owner = app.conversations.create("Android SDK device qualification fixture")
            val environment = LinuxAgentEnvironment(app.linux, owner)
            var original: Throwable? = null
            try {
                val generation = environment.generation()
                check(app.sharedToolchain.status(generation, "android-build").array("installed").contains(string(AndroidBuildTools.bundleId)))
                block(app, owner, generation)
            } catch (failure: Throwable) { original = failure; throw failure }
            finally { withContext(NonCancellable) {
                try {
                    environment.close()
                    check(app.linux.runs.pendingCount(owner) == 0) { "Unconfirmed owned SDK lease fixture retained: $owner" }
                    app.spaces.delete(owner, app.tasks::quiesce)
                } catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
                finally { app.root.disconnect() }
            } }
        }
    }
