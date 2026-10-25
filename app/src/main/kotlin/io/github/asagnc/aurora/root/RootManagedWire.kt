package io.github.asagnc.aurora.root

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*

internal object RootManagedWire {
    fun ticket(id: String, request: Bundle, uid: Int): RootManagedRunTicket {
        require(request.keySet() == setOf("ticket") || request.keySet() == setOf("ticket", "discardCapture"))
        val encoded = requireNotNull(request.getString("ticket")).also { require(it.length <= 8192) }
        return WireJson.decodeFromString<RootManagedRunTicket>(encoded).also {
            it.validate(); require(it.runId == id && it.appUid == uid)
            if (request.containsKey("discardCapture")) require(it.kind == "capture" && request.getBoolean("discardCapture"))
        }
    }

    /** Duplicates before asynchronous admission; the Binder receiver closes its received handles. */
    fun snapshots(ticket: RootManagedRunTicket, script: ParcelFileDescriptor, bridge: ParcelFileDescriptor?): List<ParcelFileDescriptor> {
        require((ticket.bridge != null) == (bridge != null))
        val owned = mutableListOf<ParcelFileDescriptor>()
        try {
            for (source in listOfNotNull(script, bridge)) {
                val copy = ParcelFileDescriptor.dup(source.fileDescriptor)
                owned.add(copy)
                Os.fcntlInt(copy.fileDescriptor, OsConstants.F_SETFD, OsConstants.FD_CLOEXEC)
            }
            return owned
        } catch (failure: Throwable) { close(owned); throw failure }
    }

    fun close(snapshots: List<ParcelFileDescriptor>) { snapshots.forEach { runCatching { it.close() } } }
}
