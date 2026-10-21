package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.*
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit Root probes using only freshly created test files/task scopes. */
@RunWith(AndroidJUnit4::class)
class RootLinuxCapabilityDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @RootRequired @Test fun rootCanCreateAppOwnedRootfsHardlinks() = runBlocking { fixture { client, owner, directory ->
        val original = directory.resolve("link-original")
        val target = directory.resolve("link-alias")
        Files.write(original, byteArrayOf(42))
        val result = execute(client, owner, listOf("/system/bin/ln", original.toString(), target.toString()))
        assertEquals(result.toString(), 0L, result.number("exitCode"))
        assertTrue(Files.isSameFile(original, target))
        assertEquals(context.applicationInfo.uid, android.system.Os.lstat(target.toString()).st_uid)
    } }

    @RootRequired @Test fun ownCgroupSupportsAdmissionKillAndEmptyObservation() = runBlocking { fixture { client, owner, _ ->
        val result = execute(client, owner, listOf("/system/bin/sh", "-c", WORKLOAD, "aurora-probe", owner))
        assertEquals(result.toString(), 0L, result.number("exitCode"))
        assertEquals("aurora-cgroup-primitives-v1\n", result["stdout"]!!.jsonObject.text("text"))
    } }

    @RootRequired @Test fun privateMountNamespaceWorksWithoutPidNamespace() = runBlocking { fixture { client, owner, _ ->
        suspend fun identity() = execute(client, owner, listOf("/system/bin/readlink", "/proc/self/ns/mnt"))["stdout"]!!.jsonObject.text("text")
        val before = identity()
        val helper = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_linux_namespace.so").toString()
        val result = execute(client, owner, listOf(helper, "--probe-mount"))
        assertEquals(result.toString(), 0L, result.number("exitCode"))
        val observation = WireJson.parseToJsonElement(result["stdout"]!!.jsonObject.text("text")!!).jsonObject
        assertEquals(JsonPrimitive(true), observation["privateMountNamespace"])
        assertEquals(before, identity())
    } }

    private suspend fun execute(client: RootCommandClient, owner: String, argv: List<String>) =
        WireJson.parseToJsonElement(client.execute(owner, CommandRequest(argv, timeoutMs = 10_000))).jsonObject

    private suspend fun fixture(block: suspend (RootCommandClient, String, Path) -> Unit) {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        val owner = UUID.randomUUID().toString()
        val directory = context.filesDir.toPath().resolve("spaces/conversations/$owner")
        Files.createDirectories(directory)
        val controller = RootController(context)
        try {
            controller.connect()
            assertEquals(controller.state.value.detail, RootPhase.READY, controller.state.value.phase)
            block(requireNotNull(controller.commandClient), owner, directory)
        } finally { controller.disconnect(); removeSkillTree(directory) }
    }

    private companion object {
        const val WORKLOAD = """set -eu
base=${'$'}(sed -n 's/^0:://p' /proc/self/cgroup)
case "${'$'}base" in /*) ;; *) exit 80 ;; esac
case "${'$'}base" in *..*|*\\*) exit 81 ;; esac
scope="/sys/fs/cgroup${'$'}base/aurora-probe-${'$'}1"
mkdir "${'$'}scope"
cleanup() {
    if [ -d "${'$'}scope" ]; then
        printf 1 > "${'$'}scope/cgroup.kill"
        rmdir "${'$'}scope"
    fi
}
trap cleanup EXIT
sleep 60 &
worker=${'$'}!
printf '%s' "${'$'}worker" > "${'$'}scope/cgroup.procs"
grep -q "aurora-probe-${'$'}1" "/proc/${'$'}worker/cgroup"
printf 1 > "${'$'}scope/cgroup.kill"
wait "${'$'}worker" || true
grep -qx 'populated 0' "${'$'}scope/cgroup.events"
rmdir "${'$'}scope"
trap - EXIT
printf 'aurora-cgroup-primitives-v1\n'
"""
    }
}
