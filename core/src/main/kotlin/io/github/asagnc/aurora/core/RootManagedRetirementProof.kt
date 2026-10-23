package io.github.asagnc.aurora.core

import kotlinx.serialization.Serializable

/** Durable retirement before GO; any created worker and task domain are already reclaimed.
 * This is not an injection cleanup report and never asserts workerExited. */
@Serializable
class RootManagedRetirementProof private constructor(
    val runId: String, val identity: LinuxRootIdentity, val ticketSha256: String,
    val protocol: Int = 1,
) {
    fun matches(ticket: RootManagedRunTicket): Boolean = protocol == ticket.protocol &&
        runId == ticket.runId && identity == ticket.identity && ticketSha256 == ticket.digest()

    companion object {
        internal fun fromNeverDispatched(ticket: RootManagedRunTicket) =
            RootManagedRetirementProof(ticket.runId, ticket.identity, ticket.digest())
    }
}
