package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.Serializable

/** Original target no longer exists; this never claims that its script was explicitly unloaded. */
@Serializable
class RootManagedTargetExitProof private constructor(
    val runId: String, val identity: LinuxRootIdentity, val ticketSha256: String,
    val observedBootId: String, val observedTarget: RootTargetIdentity?,
    val workerExited: Boolean, val domainRemoved: Boolean, val protocol: Int = 1,
) {
    fun matches(ticket: RootManagedRunTicket): Boolean {
        if (protocol != ticket.protocol || runId != ticket.runId || identity != ticket.identity ||
            ticketSha256 != ticket.digest() || !workerExited || !domainRemoved) return false
        if (!runCatching { UUID.fromString(observedBootId).toString() == observedBootId }.getOrDefault(false)) return false
        if (observedBootId != ticket.identity.bootId) return observedTarget == null
        return observedTarget == null || (observedTarget.pid == ticket.target.pid &&
            observedTarget.startTicks > 0 && observedTarget.startTicks != ticket.target.startTicks)
    }

    companion object {
        internal fun confirmed(ticket: RootManagedRunTicket, bootId: String, observed: RootTargetIdentity?) =
            RootManagedTargetExitProof(ticket.runId, ticket.identity, ticket.digest(), bootId, observed, true, true)
    }
}
