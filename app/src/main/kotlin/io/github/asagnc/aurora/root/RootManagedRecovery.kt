package io.github.asagnc.aurora.root

import android.content.Context
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/** Recovery never replays script bytes or requires the old worker version to remain installed. */
internal class RootManagedRecovery(context: Context, identity: () -> LinuxRootIdentity) {
    private val files = RootManagedFiles(context)
    private val bundle = RootManagedBundle(context)
    private val runner = RootManagedProcessRunner(identity)

    suspend fun recover(ticket: RootManagedRunTicket): String = withContext(NonCancellable + Dispatchers.IO) {
        val result = bundle.guard().use { guard ->
            val state = files.state(ticket)
            files.open(state).use { directory ->
                val exit = runner.recover(ticket, state, guard)
                if (exit.code == null || exit.forced) uncertain(ticket)
                else {
                    val records = RootManagedRecords.finished(directory, ticket)
                    val cleanup = records.normalCleanupProof()
                    val retirement = if (cleanup == null) records.neverDispatchedProof() else null
                    val target = if (cleanup == null && retirement == null) RootManagedTargetCheck.exited(records) else null
                    RootManagedResult(ticket.runId, ticket.digest(), cleanup, retirement, target,
                        records.exitCode.takeIf { it >= 0 }, records.workerStatus,
                        if (cleanup == null && retirement == null && target == null) "managed_outcome_uncertain"
                        else if (records.exitCode != 0 || records.workerStatus != 0) "managed_execution_failed" else null)
                }
            }
        }
        result.validate(ticket)
        WireJson.encodeToString(result)
    }

    private fun uncertain(ticket: RootManagedRunTicket) = RootManagedResult(ticket.runId, ticket.digest(),
        error = "managed_outcome_uncertain")
}
