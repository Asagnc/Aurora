package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** READY is admission identity only: it does not prove GO, injection or task completion. */
class RootManagedAdmission private constructor(
    val guard: RootTargetIdentity, val worker: RootTargetIdentity, val bootId: String,
    val runId: String, val ticketSha256: String,
) {
    companion object {
        fun decode(ticket: RootManagedRunTicket, bytes: ByteArray): RootManagedAdmission {
            ticket.validate()
            val digest = ticket.digest()
            require(bytes.size == 164 && text(bytes, 0, 4) == "ARW1")
            require(text(bytes, 4, 36) == ticket.runId && text(bytes, 40, 64) == digest)
            val boot = text(bytes, 128, 36)
            require(UUID.fromString(boot).toString() == boot && boot == ticket.identity.bootId)
            val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val guard = RootTargetIdentity(input.getInt(104), input.getLong(108)).also { it.validate() }
            val worker = RootTargetIdentity(input.getInt(116), input.getLong(120)).also { it.validate() }
            require(guard.pid != worker.pid)
            return RootManagedAdmission(guard, worker, boot, ticket.runId, digest)
        }

        private fun text(bytes: ByteArray, start: Int, size: Int) = bytes.copyOfRange(start, start + size).toString(Charsets.US_ASCII)
    }
}
