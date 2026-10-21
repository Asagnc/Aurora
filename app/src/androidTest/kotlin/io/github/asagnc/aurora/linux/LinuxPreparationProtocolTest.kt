package io.github.asagnc.aurora.linux

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.*
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Ordinary-UID protocol fixtures; these do not simulate Root filesystem privileges. */
@RunWith(AndroidJUnit4::class)
class LinuxPreparationProtocolTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val identity get() = LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString())

    @Test fun successfulReplyWaitsForWorkerJoin() = runBlocking {
        val endpoint = Endpoint()
        var stopped = false
        RootLinuxClient(endpoint, identity, context).prepare(UUID.randomUUID().toString()) {
            assertTrue(endpoint.joined); stopped = true
        }
        assertTrue(stopped)
    }
    @Test fun cancellationJoinsWorkerBeforeReturning() = runBlocking {
        val endpoint = Endpoint(reply = false)
        var stopped = false
        val work = launch { RootLinuxClient(endpoint, identity, context).prepare(UUID.randomUUID().toString()) { stopped = true } }
        endpoint.entered.await()
        work.cancelAndJoin()
        assertTrue(work.isCancelled && endpoint.joined && stopped)
    }
    @Test fun lostStopAcknowledgementRetainsTicketAndBlocksFilesystemCleanup() = runBlocking {
        val stage = Files.createTempDirectory(context.cacheDir.toPath(), "linux-unconfirmed-")
        try {
            val root = Files.createDirectory(stage.resolve("rootfs")); Files.write(root.resolve("value"), byteArrayOf(42))
            val image = UbuntuBaseImages.current(LinuxArchitecture.ARM64)
            val ticket = LinuxPreparationTicket(UUID.randomUUID().toString(), identity, image.sha256, context.applicationInfo.uid)
            val endpoint = Endpoint(acknowledge = false).apply {
                response = """{"protocol":1,"prepared":true,"image":"${image.sha256}","entries":1,"bytes":1}"""
            }
            val hooks = LinuxRootLinkBatch(RootLinuxClient(endpoint, ticket.identity, context), stage,
                ticket, LinuxRootfsManifest(image, 1, 1)).hooks()
            val failure = runCatching { hooks.hardlinks!!.invoke() }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure!!.message.orEmpty().contains("acknowledged"))
            assertTrue(LinuxPreparationState.pending(stage))
            hooks.cleanup(root)
            assertTrue(Files.isRegularFile(root.resolve("value")))
        } finally { removeSkillTree(stage) }
    }
    @Test fun startupRetainsUnconfirmedRootStagesAndRemovesUnstartedStages() = runBlocking {
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "linux-recovery-")
        try {
            val ticket = LinuxPreparationTicket(UUID.randomUUID().toString(), identity, "a".repeat(64), context.applicationInfo.uid)
            val retained = Files.createDirectory(staging.resolve(ticket.operation))
            LinuxPreparationState.mark(retained, ticket)
            Files.createDirectory(staging.resolve(UUID.randomUUID().toString()))
            val unstarted = Files.createDirectory(staging.resolve(UUID.randomUUID().toString()))
            Files.write(unstarted.resolve(".root-preparation.tmp"), byteArrayOf(42))
            assertEquals(1, LinuxPreparationState.recover(staging, null))
            Files.newDirectoryStream(staging).use { assertEquals(listOf(retained), it.toList()) }
        } finally { removeSkillTree(staging) }
    }

    private class Endpoint(private val reply: Boolean = true, private val acknowledge: Boolean = true) : IRootControl.Stub() {
        val entered = CompletableDeferred<Unit>()
        var joined = false
        var response = """{"protocol":1,"prepared":true}"""
        override fun prepareLinux(operation: String, callback: IRootResult) {
            entered.complete(Unit)
            if (reply) callback.complete(response)
        }
        override fun cancelAndWait(id: String) {
            if (!acknowledge) error("Binder transport lost")
            joined = true
        }
        override fun discardLinux(operation: String) { error("Not a Root filesystem fixture") }
        override fun snapshot() = Bundle()
        override fun start(id: String, conversation: String, request: Bundle, callback: IRootResult) { error("Not a command fixture") }
        override fun closeCommands() {}
        override fun openTerminal(id: String, conversation: String, request: Bundle, callback: IRootResult) = Bundle()
        override fun activateTerminal(id: String) {}
        override fun stopTerminal(id: String) {}
    }
}
