package io.github.asagnc.aurora.root

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.asagnc.aurora.core.*
import java.io.ByteArrayInputStream
import java.io.FileDescriptor
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootScriptChannelTest {
    private val bytes = "send('owned')".toByteArray()
    private fun metadata() = RootScriptSnapshot("fixture", MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }, bytes.size.toLong())
    private fun worker() = RootTargetIdentity(Process.myPid(), linuxProcessStartTicks(Files.readString(Path.of("/proc/self/stat"))))
    private fun ticket() = worker().let { peer -> RootManagedRunTicket(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        LinuxRootIdentity(peer.pid, peer.startTicks, UUID.randomUUID().toString(), UUID.randomUUID().toString()),
        Process.myUid(), "fixture", "a".repeat(64), peer, metadata()) }
    private suspend fun fixture(block: suspend (RootManagedRunTicket, ParcelFileDescriptor, RootScriptChannel) -> Unit) {
        val ticket = ticket()
        RootScriptMemory.capture(ByteArrayInputStream(bytes), ticket.script).use { source ->
            RootScriptChannel.create(ticket, listOf(source)).use { channel -> block(ticket, source, channel) }
        }
    }
    private fun receive(socket: LocalSocket): Pair<ByteArray, List<FileDescriptor>> {
        val bytes = ByteArray(72); var offset = 0
        val files = mutableListOf<FileDescriptor>()
        try {
            while (offset < bytes.size) {
                val count = socket.inputStream.read(bytes, offset, bytes.size - offset)
                files.addAll(socket.ancillaryFileDescriptors.orEmpty())
                require(count > 0); offset += count
            }
            return bytes to files
        } catch (failure: Throwable) { files.forEach { runCatching { Os.close(it) } }; throw failure }
    }
    private fun references(source: ParcelFileDescriptor): Long {
        val expected = Os.fstat(source.fileDescriptor)
        return Files.list(Path.of("/proc/self/fd")).use { paths -> paths.filter { path ->
            try { Os.stat(path.toString()).let { it.st_dev == expected.st_dev && it.st_ino == expected.st_ino } }
            catch (_: android.system.ErrnoException) { false }
        }.count() }
    }
    @Test fun fragmentedHandshakeTransfersSealedSnapshotAndPreservesSource() = runBlocking { fixture { ticket, source, channel ->
        supervisorScope {
            val transfer = async(Dispatchers.IO) { channel.handoff(worker()) }
            withContext(Dispatchers.IO) {
                LocalSocket().use { socket ->
                    socket.soTimeout = 2_000; socket.connect(LocalSocketAddress(channel.name))
                    val codec = RootFdFrame(channel.nonce, ticket.digest(), 1)
                    codec.encode(1).toList().chunked(7).forEach { socket.outputStream.write(it.toByteArray()) }
                    val (frame, descriptors) = receive(socket)
                    try {
                        assertTrue(codec.matches(2, frame)); assertEquals(1, descriptors.size)
                        RootScriptMemory.verify(descriptors.single(), ticket.script)
                        assertEquals(-1, socket.inputStream.read())
                        socket.outputStream.write(codec.encode(3)); socket.shutdownOutput()
                    } finally { descriptors.forEach { Os.close(it) } }
                }
            }
            transfer.await()
            RootScriptMemory.verify(source.fileDescriptor, ticket.script)
            try { channel.handoff(worker()); fail("Channel reused") } catch (_: IllegalStateException) { }
        }
    } }
    @Test fun unexpectedDescriptorsOrNonceAbortBeforeOfferingFiles() = runBlocking {
        for (extra in listOf(false, true)) fixture { ticket, source, channel -> supervisorScope {
            val transfer = async(Dispatchers.IO) { channel.handoff(worker()) }
            withContext(Dispatchers.IO) {
                LocalSocket().use { socket ->
                    socket.soTimeout = 2_000; socket.connect(LocalSocketAddress(channel.name))
                    val frame = RootFdFrame(channel.nonce, ticket.digest(), 1).encode(1)
                    if (extra) socket.setFileDescriptorsForSend(arrayOf(source.fileDescriptor)) else frame[8] = (frame[8].toInt() xor 1).toByte()
                    socket.outputStream.write(frame); socket.setFileDescriptorsForSend(emptyArray())
                    assertEquals(-1, socket.inputStream.read())
                    assertTrue(socket.ancillaryFileDescriptors.isNullOrEmpty())
                }
            }
            try { transfer.await(); fail("Bad handshake accepted") } catch (_: IllegalArgumentException) { }
            RootScriptMemory.verify(source.fileDescriptor, ticket.script)
        } }
    }
    @Test fun cancellationClosesBlockedAcceptAndRetainsCallerDescriptor() = runBlocking { fixture { ticket, source, channel ->
        val transfer = launch(start = CoroutineStart.UNDISPATCHED) { channel.handoff(worker()) }
        withTimeout(3_000) { transfer.cancelAndJoin() }
        RootScriptMemory.verify(source.fileDescriptor, ticket.script)
        try { channel.handoff(worker()); fail("Cancelled channel reused") } catch (_: IllegalStateException) { }
    } }
    @Test fun alreadyCancelledHandoffCannotLeaveListenerOrOwnedSnapshotsOpen() = runBlocking { fixture { ticket, source, channel ->
        assertEquals(2L, references(source))
        val transfer = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            channel.handoff(worker())
        }
        withTimeout(3_000) { transfer.join() }
        assertEquals(1L, references(source))
        withContext(Dispatchers.IO) {
            LocalSocket().use { socket ->
                try { socket.connect(LocalSocketAddress(channel.name)); fail("Cancelled offer still accepts connections") }
                catch (_: java.io.IOException) { }
            }
        }
        RootScriptMemory.verify(source.fileDescriptor, ticket.script)
    } }
    @Test fun workerCredentialMismatchClosesConnection() = runBlocking { fixture { _, _, channel -> supervisorScope {
        val transfer = async(Dispatchers.IO) { channel.handoff(RootTargetIdentity(Process.myPid() + 1, 1)) }
        withContext(Dispatchers.IO) {
            LocalSocket().use { socket ->
                socket.soTimeout = 2_000; socket.connect(LocalSocketAddress(channel.name))
                assertEquals(-1, socket.inputStream.read())
            }
        }
        try { transfer.await(); fail("Unexpected worker accepted") } catch (_: IllegalArgumentException) { }
    } } }
    @Test fun wrongSnapshotOrDuplicateDescriptorsCannotCreateOffer() = runBlocking {
        val ticket = ticket()
        RootScriptMemory.capture(ByteArrayInputStream(bytes), ticket.script).use { source ->
            try { RootScriptChannel.create(ticket.copy(script = ticket.script.copy(sha256 = "b".repeat(64))), listOf(source)).close(); fail("Wrong snapshot accepted") }
            catch (_: IllegalArgumentException) { }
            try { RootScriptChannel.create(ticket.copy(bridge = ticket.script), listOf(source, source)).close(); fail("Duplicate snapshots accepted") }
            catch (_: IllegalArgumentException) { }
            RootScriptMemory.verify(source.fileDescriptor, ticket.script)
        }
    }
}
