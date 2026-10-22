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
class LinuxScopeDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val helper get() = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_linux_scope.so").toString()

    @Test fun ordinaryUidCannotCreateRootTaskScope() = runBlocking {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "scope-ordinary-")
        try {
            val result = NativeCommandRunner(context).execute(directory,
                CommandRequest(listOf(helper, "--probe", "--state-dir", directory.resolve("state").toString())))
            assertEquals(126L, result.number("exitCode"))
            val error = WireJson.parseToJsonElement(result["stderr"]!!.jsonObject.text("text")!!).jsonObject
            assertEquals("root_required", error.text("operation"))
        } finally { removeSkillTree(directory) }
    }

    @RootRequired @Test fun rootTaskScopeKillsDetachedWorkerAndPreservesCallerNamespace() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        val owner = UUID.randomUUID().toString()
        val directory = context.filesDir.toPath().resolve("spaces/conversations/$owner")
        Files.createDirectories(directory)
        val controller = RootController(context)
        try {
            controller.connect()
            val client = requireNotNull(controller.commandClient)
            suspend fun execute(argv: List<String>) = WireJson.parseToJsonElement(
                client.execute(owner, CommandRequest(argv, timeoutMs = 20_000))).jsonObject
            suspend fun identity() = execute(listOf("/system/bin/readlink", "/proc/self/ns/mnt"))["stdout"]!!.jsonObject.text("text")
            val before = identity()
            val resources = context.filesDir.toPath().resolve("spaces/resources")
            Files.createDirectories(resources)
            val state = resources.toRealPath().resolve("linux-task-state").toString()
            val result = execute(listOf(helper, "--probe", "--state-dir", state))
            assertEquals(result.toString(), 0L, result.number("exitCode"))
            val observation = WireJson.parseToJsonElement(result["stdout"]!!.jsonObject.text("text")!!).jsonObject
            listOf("privateMountNamespace", "taskCgroup", "pidfdObserved", "subreaperDrained", "populatedThenEmpty", "scopeRemoved")
                .forEach { assertEquals(it, JsonPrimitive(true), observation[it]) }
            assertEquals(before, identity())
            val recovered = execute(listOf(helper, "--recover", "--state-dir", state))
            assertEquals(recovered.toString(), 0L, recovered.number("exitCode"))
        } finally { controller.disconnect(); removeSkillTree(directory) }
    }
}
