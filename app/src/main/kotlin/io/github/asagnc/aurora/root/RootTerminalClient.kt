package io.github.asagnc.aurora.root

import android.os.*
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.terminal.*
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal class RootTerminalClient(private val remote: IRootControl) {
    suspend fun open(id: String, owner: String, request: PtyRequest): PtyEndpoint = openRemote(id,
        { callback -> remote.openTerminal(id, owner, RootTerminalWire.encode(request), callback) }, { PtyOutcome.decode(it) })

    suspend fun openLinux(ticket: LinuxRunTicket, request: PtyRequest,
        confirm: suspend (JsonObject) -> Unit): PtyEndpoint {
        ticket.validate()
        val encoded = WireJson.encodeToString(ticket).also { require(it.length <= 262144) }
        val bundle = Bundle().apply { putString("ticket", encoded); putBundle("terminal", RootTerminalWire.encode(request)) }
        return openRemote(ticket.runId, { callback -> remote.openLinuxTerminal(ticket.runId, bundle, callback) }) { value ->
            val result = LinuxTerminalResult.decode(ticket, value)
            confirm(result.proof)
            PtyOutcome(result.exitCode, result.state, result.reason)
        }
    }
    private suspend fun openRemote(id: String, connect: (IRootResult) -> Bundle,
        decode: suspend (String) -> PtyOutcome): PtyEndpoint = withContext(Dispatchers.IO) {
        val exit = CompletableDeferred<PtyOutcome>()
        val binder = remote.asBinder()
        var socket: ParcelFileDescriptor? = null
        var opened: PtyEndpoint? = null
        val death = IBinder.DeathRecipient { exit.completeExceptionally(IllegalStateException("Root terminal service died"))
            socket?.let { runCatching { Os.shutdown(it.fileDescriptor, OsConstants.SHUT_RDWR) } } }
        binder.linkToDeath(death, 0)
        try {
            val result = connect(object : IRootResult.Stub() {
                override fun complete(value: String) {
                    // Durable lease confirmation must not block a Binder callback thread.
                    cleanup.launch {
                        try { exit.complete(decode(value)) }
                        catch (failure: Exception) { exit.completeExceptionally(failure) }
                    }
                }
            })
            require(result.getInt("protocol") == 1)
            val channel = requireNotNull(result.getParcelable("socket", ParcelFileDescriptor::class.java))
            socket = channel
            val input = ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(channel.fileDescriptor))
            val output = ParcelFileDescriptor.AutoCloseOutputStream(channel)
            val endpoint = PtyEndpoint(input, output, exit) {
                runCatching { Os.shutdown(channel.fileDescriptor, OsConstants.SHUT_RDWR) }
                cleanup.launch { runCatching { remote.stopTerminal(id) } }
            }
            opened = endpoint
            exit.invokeOnCompletion { runCatching { binder.unlinkToDeath(death, 0) } }
            remote.activateTerminal(id)
            currentCoroutineContext().ensureActive()
            endpoint
        } catch (failure: Throwable) {
            socket?.let { runCatching { Os.shutdown(it.fileDescriptor, OsConstants.SHUT_RDWR) }; it.close() }
            opened?.closeStreams()
            runCatching { remote.stopTerminal(id) }
            runCatching { binder.unlinkToDeath(death, 0) }
            throw failure
        }
    }
    companion object { private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
