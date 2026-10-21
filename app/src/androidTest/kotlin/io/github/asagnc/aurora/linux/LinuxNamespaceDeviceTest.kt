package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.*
import io.github.asagnc.aurora.runtime.*
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinuxNamespaceDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val helper get() = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_linux_namespace.so").toString()

    @Test fun ordinaryUidCannotRunRootNamespaceProbe() = runBlocking {
        val directory = Files.createDirectory(context.cacheDir.toPath().resolve("namespace-${UUID.randomUUID()}"))
        try {
            val result = NativeCommandRunner(context).execute(directory, CommandRequest(listOf(helper, "--probe")))
            assertEquals(126L, result.number("exitCode"))
            val error = WireJson.parseToJsonElement(result["stderr"]!!.jsonObject.text("text")!!).jsonObject
            assertEquals("root_required", error.text("operation"))
        } finally { removeSkillTree(directory) }
    }

    @RootRequired @Test fun rootNamespaceProbeAndTimeoutPreserveCallerMountNamespace() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        org.junit.Assume.assumeTrue("Optional PID namespaces are absent on this kernel", Files.exists(Path.of("/proc/self/ns/pid")))
        val owner = UUID.randomUUID().toString()
        val directory = context.filesDir.toPath().resolve("spaces/conversations/$owner")
        Files.createDirectories(directory)
        val controller = RootController(context)
        try {
            controller.connect()
            assertEquals(controller.state.value.detail, RootPhase.READY, controller.state.value.phase)
            val client = requireNotNull(controller.commandClient)
            suspend fun execute(arguments: List<String>, timeout: Long = 10_000) = WireJson.parseToJsonElement(
                client.execute(owner, CommandRequest(arguments, timeoutMs = timeout))).jsonObject
            suspend fun mountIdentity() = execute(listOf("/system/bin/readlink", "/proc/self/ns/mnt"))["stdout"]!!.jsonObject.text("text")
            val before = mountIdentity()
            val result = execute(listOf(helper, "--probe"))
            assertEquals(result.toString(), 0L, result.number("exitCode"))
            val observed = WireJson.parseToJsonElement(result["stdout"]!!.jsonObject.text("text")!!).jsonObject
            assertEquals(JsonPrimitive(true), observed["namespaceInitReaped"])
            assertEquals(before, mountIdentity())
            val stopped = execute(listOf(helper, "--probe", "--hold-ms", "5000"), 1000)
            assertEquals(stopped.toString(), "command_timeout", stopped.text("error"))
            assertEquals(before, mountIdentity())
        } finally { controller.disconnect(); removeSkillTree(directory) }
    }
}
