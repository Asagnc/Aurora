package io.github.asagnc.aurora.core

import kotlinx.serialization.Serializable

/** Small authenticated Binder result. Transport completion never substitutes for a cleanup proof. */
@Serializable
data class RootManagedResult(
    val runId: String, val ticketSha256: String,
    val cleanup: RootManagedExitProof? = null,
    val retirement: RootManagedRetirementProof? = null,
    val targetExit: RootManagedTargetExitProof? = null,
    val exitCode: Int? = null, val workerStatus: Int? = null,
    val error: String? = null, val protocol: Int = 1,
    val artifact: String? = null, val artifactError: String? = null,
    val probeObserved: Boolean? = null,
) {
    fun validate(ticket: RootManagedRunTicket) {
        ticket.validate()
        require(protocol == 1 && runId == ticket.runId && ticketSha256 == ticket.digest())
        require(listOfNotNull(cleanup, retirement, targetExit).size <= 1)
        require(cleanup == null || ticket.confirms(cleanup))
        require(retirement == null || retirement.matches(ticket))
        require(targetExit == null || targetExit.matches(ticket))
        require(exitCode == null || exitCode in 0..255)
        require(workerStatus == null || workerStatus in 0..255)
        require(error == null || error in setOf("managed_outcome_uncertain", "managed_execution_failed"))
        require(artifact == null || artifact == "runs/${ticket.runId}/frida.log")
        require(artifactError == null || artifactError == "frida_log_unavailable")
        require(probeObserved == null || (ticket.script == FridaManagedProbe.snapshot(ticket.bridge != null) &&
            ticket.target == RootTargetIdentity(ticket.identity.pid, ticket.identity.startTicks) && ticket.observeMs == 10_000L))
        require(probeObserved != true || successful)
    }

    val confirmed: Boolean get() = cleanup != null || retirement != null || targetExit != null
    val successful: Boolean get() = cleanup != null && exitCode == 0 && workerStatus == 0 && error == null
}
