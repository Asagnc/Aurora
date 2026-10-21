package io.github.asagnc.aurora.root

import android.os.*
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.terminal.*
import kotlinx.coroutines.*

internal class RootTerminalClient(private val remote: IRootControl) {
    suspend fun open(id: String, owner: String, request: PtyRequest): PtyEndpoint = withContext(Dispatchers.IO) {
        val exit = CompletableDeferred<PtyOutcome>()
        val binder = remote.asBinder()
        var socket: ParcelFileDescriptor? = null
        var opened: PtyEndpoint? = null
        val death = IBinder.DeathRecipient { exit.completeExceptionally(IllegalStateException("Root terminal service died"))
            socket?.let { runCatching { Os.shutdown(it.fileDescriptor, OsConstants.SHUT_RDWR) } } }
        binder.linkToDeath(death, 0)
        try {
            val result = remote.openTerminal(id, owner, RootTerminalWire.encode(request), object : IRootResult.Stub() {
                override fun complete(value: String) {
                    try { exit.complete(PtyOutcome.decode(value)) }
                    catch (failure: Exception) { exit.completeExceptionally(failure) }
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
