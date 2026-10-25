package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Ordinary-UID loader/refusal checks; does not qualify privileged cgroup or Frida execution. */
@RunWith(AndroidJUnit4::class)
class RootManagedPackageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val binary get() = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_managed_guard.so").toString()

    @Test fun packagedFridaAndBridgeMatchTheirImmutableInputs() = runBlocking { fixture { root ->
        org.junit.Assume.assumeTrue(android.os.Build.SUPPORTED_ABIS.first() == "arm64-v8a")
        val bundle = io.github.asagnc.aurora.root.RootManagedBundle(context)
        bundle.worker().use { worker ->
            worker.usePath { path ->
                val reply = kotlinx.coroutines.runBlocking {
                    NativeCommandRunner(context).execute(root, CommandRequest(listOf(path, "--version")))
                }
                assertEquals(reply.toString(), 0L, reply.number("exitCode"))
                assertEquals("17.24.3", reply["stdout"]?.jsonObject?.text("text")?.trim())
            }
        }
        val metadata = bundle.bridgeSnapshot()
        assertEquals("frida-java-bridge-7.0.13", metadata.version)
        io.github.asagnc.aurora.root.RootScriptMemory.capture(context.assets.open("frida/java-bridge.js"), metadata).use {
            io.github.asagnc.aurora.root.RootScriptMemory.verify(it.fileDescriptor, metadata)
        }
    } }

    @Test fun packagedManifestPinsTheInstalledExecutable() = runBlocking {
        io.github.asagnc.aurora.root.RootManagedBundle(context).guard().use { guard ->
            assertEquals(64, guard.sha256.length)
            guard.usePath { assertTrue(it.startsWith("/proc/${android.os.Process.myPid()}/fd/")) }
        }
    }

    @Test fun packagedGuardMetadataStartsWithoutRoot() = runBlocking { fixture { root ->
        val reply = NativeCommandRunner(context).execute(root, CommandRequest(listOf(binary, "--version")))
        assertEquals(reply.toString(), 0L, reply.number("exitCode"))
        val metadata = WireJson.parseToJsonElement(requireNotNull(reply["stdout"]?.jsonObject?.text("text"))).jsonObject
        assertEquals(1L, metadata.number("protocol")); assertEquals("aurora-managed-guard", metadata.text("kind"))
        assertEquals(JsonPrimitive(true), metadata["rootRequired"])
        assertEquals(JsonPrimitive(true), metadata["cooperativeCancel"])
        assertFalse(metadata.containsKey("fridaReady"))
    } }

    @Test fun ordinaryUidCannotCreateOrRecoverRootTaskState() = runBlocking { fixture { root ->
        assertTrue(android.os.Process.myUid() != 0)
        val state = root.resolve("native-state")
        val reply = NativeCommandRunner(context).execute(root, CommandRequest(listOf(binary, "recover",
            "--state-dir", state.toString(), "--run-id", UUID.randomUUID().toString(), "--ticket-sha", "a".repeat(64))))
        assertEquals(reply.toString(), 77L, reply.number("exitCode"))
        assertFalse(Files.exists(state))
    } }

    private suspend fun fixture(block: suspend (Path) -> Unit) {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "managed-package-")
        try { block(root) } finally { removeSkillTree(root) }
    }
}
