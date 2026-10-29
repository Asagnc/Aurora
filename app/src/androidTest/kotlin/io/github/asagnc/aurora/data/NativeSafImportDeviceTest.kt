package io.github.asagnc.aurora.data

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.text
import io.github.asagnc.aurora.skills.*
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit host-driven system picker selection; no fabricated URI result or FileProvider substitute. */
@RunWith(AndroidJUnit4::class)
class NativeSafImportDeviceTest {
    @Test fun realPickerCancellationThenOwnedZipImportUsesTheProductionUi() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("auroraSafUiTest") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        val identity = UUID.randomUUID().toString()
        val name = "aurora-saf-$identity"
        val file = "/sdcard/Download/$name.zip"
        val previous = app.skills.list().map { it.id }.toSet()
        var original: Throwable? = null
        try {
            stage(file, name)
            instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            clickApp("会话抽屉"); clickApp("Skills"); clickApp("导入 ZIP")
            awaitPicker(app)
            cancelPicker(app)
            clickApp("导入 ZIP")
            assertEquals(previous, app.skills.list().map { it.id }.toSet())
            awaitPicker(app)
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_NATIVE_SAF_SELECT=$name.zip\n") })
            val entry = withTimeout(180_000) {
                while (true) {
                    app.skills.list().singleOrNull { it.metadata.name == name && it.id !in previous }?.let { return@withTimeout it }
                    delay(100)
                }
                error("unreachable")
            }
            assertTrue(entry.source.input.startsWith("content://"))
            assertTrue(app.skills.read(entry, "SKILL.md").text("text")?.contains("Owned native picker qualification") == true)
            shell("rm -f -- '$file'")
            assertTrue(app.skills.read(entry, "SKILL.md").text("text")?.contains(name) == true)
        } catch (failure: Throwable) { original = failure; throw failure }
        finally { withContext(NonCancellable) {
            try {
                for (entry in app.skills.list().filter { it.metadata.name == name && it.id !in previous }) removeOwned(app, entry)
                shell("rm -f -- '$file'")
            } catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
        } }
    }

    private fun stage(file: String, name: String) {
        require(file == "/sdcard/Download/$name.zip" && name.matches(Regex("aurora-saf-[a-f0-9-]{36}")))
        val bytes = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("owned/SKILL.md"))
            zip.write("---\nname: $name\ndescription: Owned native picker qualification\n---\nOwned native picker qualification. Do not execute commands.\n".toByteArray())
            zip.closeEntry()
        } }.toByteArray()
        val encoded = Base64.getEncoder().encodeToString(bytes)
        assertTrue(shell("test ! -e '$file' && test ! -L '$file' && printf %s '$encoded' | base64 -d > '$file' && echo staged").contains("staged"))
    }

    private suspend fun removeOwned(app: AuroraApplication, entry: SkillEntry) {
        app.skills.remove(entry.id)
        app.skills.catalog.withLock {
            if (app.skills.registry.garbage().none { it.first == entry.hash }) return@withLock
            val operation = UUID.randomUUID().toString()
            app.skills.registry.reserveRemoval(operation, entry.hash)
            withContext(Dispatchers.IO) { app.skills.store.remove(entry.hash) }
            app.skills.registry.clearOperation(operation)
        }
    }

    private suspend fun awaitPicker(app: AuroraApplication) = withTimeout(15_000) {
        val picker = pickerPackage(app)
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "AURORA_SAF_PICKER_PACKAGE=$picker observed=${InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName}\n")
        })
        while (InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString() != picker) delay(100)
    }

    private suspend fun cancelPicker(app: AuroraApplication) = withTimeout(10_000) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val picker = pickerPackage(app)
        while (automation.rootInActiveWindow?.packageName?.toString() != app.packageName) {
            if (automation.rootInActiveWindow?.packageName?.toString() == picker)
                assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            delay(250)
        }
    }

    private fun pickerPackage(app: AuroraApplication): String = InstrumentationRegistry.getArguments()
        .getString("auroraSafPickerPackage")?.also { require(it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) }
        ?: requireNotNull(app.packageManager.resolveActivity(ActivityResultContracts.OpenDocument()
            .createIntent(app, arrayOf("application/zip", "application/octet-stream")), PackageManager.MATCH_DEFAULT_ONLY)).activityInfo.packageName

    private suspend fun clickApp(label: String) = withTimeout(15_000) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        while (true) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() == instrumentation.targetContext.packageName) {
                val node = clickable(find(root, label))
                if (node?.isEnabled == true && node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return@withTimeout
            }
            delay(100)
        }
    }

    private fun clickable(selected: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var node = selected
        repeat(5) { if (node != null && !node!!.isClickable) node = node!!.parent }
        return node
    }

    private fun find(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 1024) {
            val node = queue.removeFirst()
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun shell(command: String): String {
        val descriptors = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("sh")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { output ->
            ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { it.write((command + "\nexit\n").toByteArray()) }
            output.readNBytes(4096).toString(Charsets.UTF_8)
        }
    }
}
