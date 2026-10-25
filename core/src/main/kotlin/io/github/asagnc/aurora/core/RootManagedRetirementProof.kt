package io.github.asagnc.aurora.core

import kotlinx.serialization.Serializable

/** Durable retirement without injection: before GO or with an exact final no-attach worker report.
 * Any created worker/domain is reclaimed; this does not claim an explicit unload/detach. */
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
        internal fun fromNoInjection(ticket: RootManagedRunTicket) =
            RootManagedRetirementProof(ticket.runId, ticket.identity, ticket.digest())
    }
}
