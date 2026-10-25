package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.system.Os
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object RootCaptureMemory {
    suspend fun read(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>): RootCaptureConfiguration = withContext(Dispatchers.IO) {
        ticket.validate(); require(ticket.kind == "capture" && snapshots.size == 1)
        val fd = snapshots.single().fileDescriptor
        RootScriptMemory.verify(fd, ticket.script)
        val data = ByteArray(ticket.script.bytes.toInt()); var offset = 0
        while (offset < data.size) {
            val count = Os.pread(fd, data, offset, data.size - offset, offset.toLong())
            require(count > 0); offset += count
        }
        RootCaptureConfiguration.decode(data).also { it.validateTicket(ticket) }
    }
}
