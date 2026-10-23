package io.github.asagnc.aurora.root

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString

/** One launch only. Owns duplicated snapshots and sockets; never owns or stops the target. */
internal class RootScriptChannel private constructor(
    private val ticket: RootManagedRunTicket, private val snapshots: List<ParcelFileDescriptor>,
    private val server: LocalServerSocket, val nonce: String, val name: String,
) : Closeable {
    private val codec = RootFdFrame(nonce, ticket.digest(), snapshots.size)
    private var client: LocalSocket? = null
    private var closed = false
    private var claimed = false
    private var deadline = 0L
    val arguments: List<String> get() = listOf("attach-channel", name, nonce, WireJson.encodeToString(ticket))

    suspend fun handoff(worker: RootTargetIdentity) {
        worker.validate()
        synchronized(this) {
            check(!closed && !claimed) { "Script channel is closed or already claimed" }
            claimed = true; deadline = SystemClock.elapsedRealtime() + 5_000
        }
        withTimeout(5_000) {
            coroutineScope {
                val guard = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { close() }
                }
                try { withContext(Dispatchers.IO) { transfer(worker) } }
                finally { guard.cancelAndJoin(); close() }
            }
        }
    }
    private fun transfer(worker: RootTargetIdentity) {
        server.accept().use { socket ->
            synchronized(this) { check(!closed); client = socket }
            val peer = socket.peerCredentials
            require(peer.uid == Process.myUid() && peer.pid == worker.pid) { "Unexpected script worker" }
            requireStart(worker)
            require(codec.matches(1, read(socket, 72))) { "Script handshake mismatch" }
            requireStart(worker)
            socket.soTimeout = remaining()
            socket.setFileDescriptorsForSend(snapshots.map { it.fileDescriptor }.toTypedArray())
            try { socket.outputStream.write(codec.encode(2)) }
            finally { socket.setFileDescriptorsForSend(emptyArray()) }
            socket.shutdownOutput()
            require(codec.matches(3, read(socket, 72))) { "Script acknowledgement mismatch" }
            require(read(socket, 1).isEmpty()) { "Unexpected script channel tail" }
        }
    }
    private fun requireStart(worker: RootTargetIdentity) {
        val stat = Files.newInputStream(Path.of("/proc/${worker.pid}/stat")).use { it.readNBytes(65_537).toString(Charsets.UTF_8) }
        require(linuxProcessStartTicks(stat) == worker.startTicks) { "Script worker identity changed" }
    }
    private fun read(socket: LocalSocket, length: Int): ByteArray {
        val bytes = ByteArray(length); var offset = 0
        while (offset < length) {
            socket.soTimeout = remaining()
            val count = socket.inputStream.read(bytes, offset, length - offset)
            val extra = socket.ancillaryFileDescriptors.orEmpty()
            extra.forEach { runCatching { Os.close(it) } }
            require(extra.isEmpty()) { "Worker sent unexpected descriptors" }
            if (count < 0) break
            require(count > 0); offset += count
        }
        return bytes.copyOf(offset)
    }
    private fun remaining(): Int = (deadline - SystemClock.elapsedRealtime()).also { require(it > 0) }.toInt()
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        client?.let { runCatching { it.shutdownInput() }; runCatching { it.shutdownOutput() }; runCatching { it.close() } }
        runCatching { Os.shutdown(server.fileDescriptor, OsConstants.SHUT_RDWR) }
        runCatching { server.close() }
        snapshots.forEach { runCatching { it.close() } }
    }
    companion object {
        suspend fun create(ticket: RootManagedRunTicket, sources: List<ParcelFileDescriptor>): RootScriptChannel {
            var channel: RootScriptChannel? = null
            val owned = mutableListOf<ParcelFileDescriptor>()
            var listener: LocalServerSocket? = null
            try {
                return withContext(Dispatchers.IO) {
                    ticket.validate()
                    val metadata = listOfNotNull(ticket.script, ticket.bridge)
                    require(sources.size == metadata.size)
                    sources.forEachIndexed { index, source ->
                        val copy = ParcelFileDescriptor.dup(source.fileDescriptor); owned.add(copy)
                        RootScriptMemory.verify(copy.fileDescriptor, metadata[index])
                    }
                    require(owned.map { Os.fstat(it.fileDescriptor).let { stat -> stat.st_dev to stat.st_ino } }.distinct().size == owned.size)
                    val name = "aurora-fd-${UUID.randomUUID().toString().replace("-", "")}"
                    val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
                    val bound = LocalServerSocket(name).also { listener = it }
                    RootScriptChannel(ticket, owned.toList(), bound, nonce, name).also { channel = it }
                }
            } catch (failure: Throwable) {
                channel?.close(); runCatching { listener?.close() }; owned.forEach { runCatching { it.close() } }
                throw failure
            }
        }
    }
}
