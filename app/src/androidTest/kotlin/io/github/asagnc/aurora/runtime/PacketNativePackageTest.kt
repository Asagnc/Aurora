package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Offline ordinary-UID checks do not qualify Root live capture. */
@RunWith(AndroidJUnit4::class)
class PacketNativePackageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val binary get() = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_tcpdump.so").toString()

    @Test fun packagedVersionsStartWithoutRoot() = runBlocking { fixture { root ->
        val result = NativeCommandRunner(context).execute(root, CommandRequest(listOf(binary, "--version")))
        assertEquals(result.toString(), 0L, result.number("exitCode"))
        val text = result["stdout"]!!.jsonObject.text("text").orEmpty()
        assertTrue(text, text.contains("tcpdump version 4.99.7"))
        assertTrue(text, text.contains("libpcap version 1.10.7"))
    } }

    @Test fun offlineFilterRetainsPacketBytesAndRejectsWrongPort() = runBlocking { fixture { root ->
        val packet = "00000000000000000000000008004500001c00000000401100007f0000017f00000104d210e100080000"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val capture = ByteBuffer.allocate(40 + packet.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0xa1b2c3d4.toInt()).putShort(2).putShort(4).putInt(0).putInt(0).putInt(65535).putInt(1)
            .putInt(1).putInt(0).putInt(packet.size).putInt(packet.size).put(packet).array()
        Files.write(root.resolve("input.pcap"), capture)
        for ((port, expected) in listOf(4321 to packet.size, 123 to 0)) {
            val name = "filtered-$port.pcap"
            val result = NativeCommandRunner(context).execute(root, CommandRequest(listOf(binary,
                "-nn", "-r", "input.pcap", "-w", name, "udp", "dst", "port", port.toString())))
            assertEquals(result.toString(), 0L, result.number("exitCode"))
            val output = Files.readAllBytes(root.resolve(name))
            assertEquals(if (expected == 0) 24 else 40 + expected, output.size)
            if (expected > 0) assertArrayEquals(packet, output.copyOfRange(40, output.size))
        }
        val tool = PacketInspectionTool(context, root)
        val inspected = WireJson.parseToJsonElement(tool.execute(ToolCall("inspect", "packet_inspect",
            objectOf("path" to string("input.pcap"), "count" to kotlinx.serialization.json.JsonPrimitive(1),
                "filter" to string("udp dst port 4321"))))).jsonObject
        assertEquals(inspected.toString(), 0L, inspected.number("exitCode"))
        assertTrue(inspected.toString(), inspected["stdout"]!!.jsonObject.text("text").orEmpty().contains(".4321"))
        val literal = WireJson.parseToJsonElement(tool.execute(ToolCall("literal", "packet_inspect",
            objectOf("path" to string("input.pcap"), "filter" to string("--version"))))).jsonObject
        assertEquals(literal.toString(), "command_exit", literal.text("error"))
    } }

    @Test fun offlineToolRejectsTraversalBeforeDispatch() = runBlocking { fixture { root ->
        var dispatched = false
        val tool = PacketInspectionTool(context, root, prepared = { _, _ -> dispatched = true })
        try {
            tool.execute(ToolCall("escape", "packet_inspect", objectOf("path" to string("../outside.pcap"))))
            fail("Packet tool accepted conversation traversal")
        } catch (_: IllegalArgumentException) { }
        assertFalse(dispatched)
    } }

    private suspend fun fixture(block: suspend (Path) -> Unit) {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "packet-offline-")
        try { block(root) } finally { removeSkillTree(root) }
    }
}
