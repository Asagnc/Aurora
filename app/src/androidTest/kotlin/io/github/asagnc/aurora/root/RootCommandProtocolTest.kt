package io.github.asagnc.aurora.root

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootCommandProtocolTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun fixture(block: suspend (IRootControl, String, Path) -> Unit) {
        val ready = CompletableDeferred<IRootControl>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { ready.complete(IRootControl.Stub.asInterface(binder)) }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        val intent = Intent().setClassName(context, "io.github.asagnc.aurora.root.RootProtocolFixtureService")
        check(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        val id = UUID.randomUUID().toString()
        val directory = context.filesDir.toPath().resolve("root-protocol-fixture").resolve(id)
        var remote: IRootControl? = null
        try {
            remote = withTimeout(10_000) { ready.await() }
            assertNotEquals(Process.myPid(), remote.snapshot().getInt("pid"))
            assertEquals(Process.myUid(), remote.snapshot().getInt("uid"))
            block(remote, id, directory)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { remote?.closeCommands() } }
            context.unbindService(connection)
            if (Files.exists(directory)) Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun realBinderReturnsNativeCommandOutput() = runBlocking { fixture { remote, id, _ ->
        val result = WireJson.parseToJsonElement(RootCommandClient(remote, context).execute(id,
            CommandRequest(listOf("/system/bin/printf", "%s", "literal; $(unchanged)")))).jsonObject
        assertEquals(0L, result.number("exitCode"))
        assertEquals("literal; $(unchanged)", result["stdout"]!!.jsonObject.text("text"))
        // Fixture UID must never be presented as verified Root.
        assertEquals(Process.myUid().toLong(), result.number("uid"))
    } }

    @Test fun cancellationWaitsForRemoteProcessCleanup() = runBlocking { fixture { remote, id, directory ->
        val task = launch(Dispatchers.IO) { RootCommandClient(remote, context).execute(id, CommandRequest(listOf("/system/bin/sh", "-c",
            "trap 'echo flushed > stopped' TERM; echo ready > started; while :; do sleep 1; done"))) }
        withTimeout(10_000) { while (!Files.exists(directory.resolve("started"))) delay(20) }
        task.cancelAndJoin()
        assertEquals("flushed", String(Files.readAllBytes(directory.resolve("stopped"))).trim())
    } }

    @Test fun closeCancelsActiveCommandsAndRejectsFurtherStarts() = runBlocking { fixture { remote, id, directory ->
        val task = async(Dispatchers.IO) { RootCommandClient(remote, context).execute(id, CommandRequest(listOf("/system/bin/sh", "-c",
            "trap 'echo flushed > stopped' TERM; echo ready > started; while :; do sleep 1; done"))) }
        withTimeout(10_000) { while (!Files.exists(directory.resolve("started"))) delay(20) }
        withContext(Dispatchers.IO) { remote.closeCommands() }
        assertTrue(withTimeout(3000) { task.await() }.contains("command_outcome_uncertain"))
        assertTrue(Files.exists(directory.resolve("stopped")))
        val rejected = RootCommandClient(remote, context).execute(id, CommandRequest(listOf("/system/bin/true")))
        assertTrue(rejected.contains("command_outcome_uncertain"))
    } }

    @Test fun wireRejectsOversizedRequestsAndPreview() {
        fun rejected(request: CommandRequest) {
            try { RootCommandWire.decode(RootCommandWire.encode(request)); fail("Oversized wire accepted") }
            catch (_: IllegalArgumentException) {}
        }
        rejected(CommandRequest(listOf("x".repeat(40000))))
        rejected(CommandRequest(listOf("/system/bin/true"), outputLimit = 8193))
        rejected(CommandRequest(listOf("/system/bin/true"), timeoutMs = Long.MAX_VALUE))
        assertEquals(listOf("/system/bin/true"), RootCommandWire.decode(RootCommandWire.encode(CommandRequest(listOf("/system/bin/true")))).argv)
    }

    @Test fun serviceDeathReturnsUncertainAndNativeSupervisorCleansUp() = runBlocking { fixture { remote, id, directory ->
        val task = async(Dispatchers.IO) { RootCommandClient(remote, context).execute(id, CommandRequest(listOf("/system/bin/sh", "-c",
            "echo ready > started; sleep 60"))) }
        withTimeout(10_000) { while (!Files.exists(directory.resolve("started"))) delay(20) }
        Process.killProcess(remote.snapshot().getInt("pid"))
        assertTrue(withTimeout(3000) { task.await() }.contains("command_outcome_uncertain"))
        withTimeout(5000) {
            while (true) {
                val records = Files.list(directory.resolve("runs")).use { paths -> paths.toList().map { it.resolve("result.json") } }
                if (records.isNotEmpty() && records.all { Files.isRegularFile(it) }) {
                    val result = WireJson.parseToJsonElement(String(Files.readAllBytes(records.single()))).jsonObject
                    assertEquals(JsonPrimitive(true), result["stopped"])
                    break
                }
                delay(20)
            }
        }
    } }

    @Test fun sessionBoundsParallelCommandsAndRejectsDuplicateIds() = runBlocking {
        val session = RootCommandSession()
        val callback = object : IRootResult.Stub() { override fun complete(result: String) {} }
        val first = UUID.randomUUID().toString()
        try {
            session.start(first, callback) { awaitCancellation() }
            try { session.start(first, callback) { "unexpected" }; fail("Duplicate accepted") }
            catch (_: IllegalStateException) {}
            session.start(UUID.randomUUID().toString(), callback) { awaitCancellation() }
            try { session.start(UUID.randomUUID().toString(), callback) { "unexpected" }; fail("Unbounded launch accepted") }
            catch (_: IllegalStateException) {}
        } finally { session.close() }
    }

    @Test fun callbackDeathCancelsAndJoinsOwnedJob() = runBlocking {
        var death: IBinder.DeathRecipient? = null
        val token = object : Binder() {
            override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { death = recipient }
            override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = true
        }
        val callback = object : IRootResult.Stub() {
            override fun asBinder(): IBinder = token
            override fun complete(result: String) {}
        }
        val session = RootCommandSession()
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val id = UUID.randomUUID().toString()
        try {
            session.start(id, callback) {
                try { started.complete(Unit); awaitCancellation() }
                finally { cleaned.complete(Unit) }
            }
            withTimeout(3000) { started.await() }
            requireNotNull(death).binderDied()
            session.cancelAndWait(id)
            assertTrue(cleaned.isCompleted)
        } finally { session.close() }
        deadBinderRegistrationRemainsAnUncertainResult()
    }

    private suspend fun deadBinderRegistrationRemainsAnUncertainResult() {
        val token = object : Binder() {
            override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { throw DeadObjectException() }
            override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean { throw NoSuchElementException() }
        }
        val callback = object : IRootResult.Stub() {
            override fun asBinder(): IBinder = token
            override fun complete(result: String) {}
        }
        val session = RootCommandSession()
        try {
            try { session.start(UUID.randomUUID().toString(), callback) { error("Dead client must not execute") }; fail("Dead client accepted") }
            catch (_: DeadObjectException) {}
        } finally { session.close() }
        val dead = object : IRootControl.Stub() {
            override fun asBinder(): IBinder = token
            override fun snapshot() = Bundle()
            override fun start(id: String, conversation: String, request: Bundle, callback: IRootResult) { error("Must not start") }
            override fun cancelAndWait(id: String) {}
            override fun closeCommands() {}
        }
        assertTrue(RootCommandClient(dead, context).execute(UUID.randomUUID().toString(),
            CommandRequest(listOf("/system/bin/true"))).contains("command_outcome_uncertain"))
    }
}
