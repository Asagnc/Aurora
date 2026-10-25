package io.github.asagnc.aurora.root

import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.asagnc.aurora.core.*
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootManagedWireTest {
    private val bytes = "send('owned');".toByteArray()
    private val ticket = RootManagedRunTicket(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        LinuxRootIdentity(10, 20, UUID.randomUUID().toString(), UUID.randomUUID().toString()),
        Process.myUid(), "41", "a".repeat(64), RootTargetIdentity(90, 900),
        RootScriptSnapshot("fixture", MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }, bytes.size.toLong()))

    @Test fun receivedHandleClosureDoesNotInvalidateOwnedSnapshot() = runBlocking {
        RootScriptMemory.capture(ByteArrayInputStream(bytes), ticket.script).use { source ->
            val received = ParcelFileDescriptor.dup(source.fileDescriptor)
            val owned = RootManagedWire.snapshots(ticket, received, null)
            received.close()
            try { RootScriptMemory.verify(owned.single().fileDescriptor, ticket.script) }
            finally { RootManagedWire.close(owned) }
            RootScriptMemory.verify(source.fileDescriptor, ticket.script)
        }
    }

    @Test fun malformedMetadataAndDescriptorShapeAreRejected() = runBlocking {
        val request = Bundle().apply { putString("ticket", WireJson.encodeToString(ticket)) }
        assertEquals(ticket, RootManagedWire.ticket(ticket.runId, request, Process.myUid()))
        try { RootManagedWire.ticket(UUID.randomUUID().toString(), request, Process.myUid()); fail("Wrong id") }
        catch (_: IllegalArgumentException) { }
        request.putString("path", "/arbitrary")
        try { RootManagedWire.ticket(ticket.runId, request, Process.myUid()); fail("Extra executable path accepted") }
        catch (_: IllegalArgumentException) { }
        RootScriptMemory.capture(ByteArrayInputStream(bytes), ticket.script).use { source ->
            try { RootManagedWire.snapshots(ticket, source, source); fail("Unselected bridge accepted") }
            catch (_: IllegalArgumentException) { }
            RootScriptMemory.verify(source.fileDescriptor, ticket.script)
        }
    }

    @Test fun discardIsExplicitAndAvailableOnlyForCaptureRecovery() {
        val capture = ticket.copy(kind = "capture", action = "capture", protocol = 3, target = null, observeMs = 1000,
            script = RootScriptSnapshot("capture-rotation-v1", "b".repeat(64), 227))
        val request = Bundle().apply { putString("ticket", WireJson.encodeToString(capture)); putBoolean("discardCapture", true) }
        assertEquals(capture, RootManagedWire.ticket(capture.runId, request, Process.myUid()))
        request.putBoolean("discardCapture", false)
        try { RootManagedWire.ticket(capture.runId, request, Process.myUid()); fail("Implicit discard marker accepted") }
        catch (_: IllegalArgumentException) { }
        request.putBoolean("discardCapture", true); request.putString("ticket", WireJson.encodeToString(ticket))
        try { RootManagedWire.ticket(ticket.runId, request, Process.myUid()); fail("Frida discard accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectedDeadCallbackReleasesSnapshotsBeforeExecution() = runBlocking {
        val released = AtomicInteger()
        val token = object : Binder() {
            override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { throw DeadObjectException() }
            override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int) = true
        }
        val callback = object : IRootResult.Stub() {
            override fun asBinder(): IBinder = token
            override fun complete(result: String) { }
        }
        val session = RootCommandSession()
        try {
            try { session.start(ticket.runId, callback, { released.incrementAndGet() }) { error("Must not execute") }; fail("Dead callback admitted") }
            catch (_: DeadObjectException) { }
        } finally { session.close() }
        assertEquals(1, released.get())
    }

    @Test fun cancellationReleasesResourcesAfterWorkerCleanup() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cleaned = AtomicInteger()
        val released = AtomicInteger()
        val session = RootCommandSession()
        val callback = object : IRootResult.Stub() { override fun complete(result: String) { } }
        try {
            session.start(ticket.runId, callback, { released.set(cleaned.get()) }) {
                try { started.complete(Unit); awaitCancellation() }
                finally { withContext(NonCancellable) { yield(); cleaned.set(1) } }
            }
            withTimeout(3000) { started.await() }
            session.cancelAndWait(ticket.runId)
            assertEquals(1, released.get())
        } finally { session.close() }
    }
}
