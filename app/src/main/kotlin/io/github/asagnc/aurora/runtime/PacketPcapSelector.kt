package io.github.asagnc.aurora.runtime

import android.content.Context
import io.github.asagnc.aurora.core.*
import java.io.IOException
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Ordinary-UID isolated parsing. Only a private stdin snapshot is consumed; no Root or network opens. */
internal class PacketPcapSelector(context: Context) {
    private val binary = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_packet_inject.so")

    suspend fun select(input: ByteArray, request: PacketReplayRequest): JsonObject = withContext(Dispatchers.IO) { coroutineScope {
        require(input.size in 24..32 * 1024 * 1024)
        val process = ProcessBuilder(binary.toString(), "--select", input.size.toString(),
            request.firstPacket.toString(), request.count.toString(), request.injection.bytes.toString())
            .apply { environment().clear(); environment()["LC_ALL"] = "C" }.start()
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { process.destroy() }
        }
        var failure: Throwable? = null
        try {
            withTimeoutOrNull(15000) { runInterruptible(Dispatchers.IO) {
                try { process.outputStream.use { it.write(input) } } catch (_: IOException) { /* Read the bounded native rejection if available. */ }
                val output = process.inputStream.use { it.readNBytes(65537) }
                val error = process.errorStream.use { it.readNBytes(4097) }
                val code = process.waitFor()
                require(output.size <= 65536 && error.size <= 4096) { "Selector output exceeds budget" }
                val value = packetReplayRecord(output.toString(Charsets.UTF_8))
                if (code == 0) require(value["error"] == null) else require(value.text("error") in errors)
                value
            } } ?: objectOf("error" to string("selector_timeout"))
        } catch (cancel: CancellationException) { failure = cancel; throw cancel }
        catch (_: Exception) { objectOf("error" to string("selector_failed")) }
        finally { withContext(NonCancellable + Dispatchers.IO) {
            cancellation.cancelAndJoin()
            if (process.isAlive) process.destroyForcibly()
            var interrupted = Thread.interrupted()
            try {
                while (true) try { process.waitFor(); break } catch (_: InterruptedException) { interrupted = true }
            } finally {
                var cleanup: Throwable? = null
                for (stream in listOf(process.outputStream, process.inputStream, process.errorStream)) {
                    try { stream.close() } catch (caught: Exception) {
                        if (cleanup == null) cleanup = caught else cleanup.addSuppressed(caught)
                    }
                }
                if (interrupted) Thread.currentThread().interrupt()
                cleanup?.let { if (failure != null) failure.addSuppressed(it) else throw it }
            }
        } }
    } }

    companion object {
        private val errors = setOf("snapshot_failed", "classic_pcap_required", "pcap_invalid", "ethernet_without_fcs_required",
            "selection_incomplete", "complete_bounded_frame_required")
    }
}
