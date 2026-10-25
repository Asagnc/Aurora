package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes exact records obtained by a trusted Root reader, never arbitrary App paths or stdout. */
class RootManagedNativeState private constructor(
    private val ticket: RootManagedRunTicket,
    val guard: RootTargetIdentity?, val worker: RootTargetIdentity?,
    stopped: Stopped, facts: Pair<Int, Int>?,
    val spawn: RootSpawnState?,
) {
    val phase = stopped.phase
    val workerState = stopped.worker
    val domainState = stopped.domain
    val exitCode = stopped.exitCode
    val reason = stopped.reason
    val error = stopped.error
    val workerFlags = facts?.first
    val workerStatus = facts?.second
    val originalRoot: LinuxRootIdentity get() = ticket.identity
    val target: RootTargetIdentity? get() = ticket.target ?: spawn?.target

    private data class Stopped(val phase: Int, val worker: Int, val domain: Int, val exitCode: Int, val reason: Int, val error: Int)

    fun neverDispatchedProof(): RootManagedRetirementProof? =
        if (phase and 4 == 0 && workerState in 1..2 && domainState in 1..2 &&
            (workerFlags == null || workerFlags and ATTEMPTED == 0))
            RootManagedRetirementProof.fromNeverDispatched(ticket) else null

    /** GO may have run, but a strict final worker record can still prove no attach was attempted. */
    fun noInjectionProof(): RootManagedRetirementProof? =
        if (ticket.kind == "frida" && phase and 4 != 0 && worker != null && workerState == CLEAR && domainState == CLEAR &&
            (spawn == null || !spawn.requested || spawn.retired) &&
            workerFlags == (FINAL or CLEAN) && workerStatus != null && workerStatus != 0)
            RootManagedRetirementProof.fromNoInjection(ticket) else null

    /** Only a trusted Root observer may supply a boot change, exact replacement identity or confirmed ESRCH (null). */
    fun targetExitProof(bootId: String, observed: RootTargetIdentity?): RootManagedTargetExitProof? {
        if (ticket.kind != "frida") return null
        // A confirmed new boot independently ends all old-boot processes and cgroups.
        if (bootId == ticket.identity.bootId && (workerState != CLEAR || domainState != CLEAR)) return null
        val proof = RootManagedTargetExitProof.confirmed(ticket, bootId, observed, spawn?.target)
        return proof.takeIf { it.matches(ticket) }
    }

    /** Resource reclamation and script cleanup remain independent facts, including on cancellation. */
    fun normalCleanupProof(): RootManagedExitProof? {
        if (ticket.kind == "capture") {
            if (worker == null || workerState != CLEAR || domainState != CLEAR) return null
            return RootManagedExitProof(ticket.runId, ticket.identity, ticket.digest(), false, false, false, true, true, ticket.protocol)
        }
        val flags = workerFlags ?: return null
        if (spawn != null && !spawn.settled) return null
        if (worker == null || workerState != CLEAR || domainState != CLEAR || flags and CLEAN != CLEAN || flags and ATTEMPTED == 0) return null
        return RootManagedExitProof(ticket.runId, ticket.identity, ticket.digest(),
            scriptUnloaded = true, sessionDetached = true, managerClosed = true,
            workerExited = true, domainRemoved = true, protocol = ticket.protocol, spawnTarget = spawn?.target)
    }

    companion object {
        private const val CLEAR = 1
        private const val FINAL = 1
        private const val ATTEMPTED = 2
        private const val LOADED = 4
        private const val CLEAN = 8 or 16 or 32
        private const val SUCCESS = 64

        fun decode(ticket: RootManagedRunTicket, ready: ByteArray?, report: ByteArray?, stopped: ByteArray,
            spawn: RootSpawnState? = null): RootManagedNativeState {
            ticket.validate()
            require((ticket.action == "spawn") == (spawn != null))
            val digest = ticket.digest()
            val admission = ready?.let { RootManagedAdmission.decode(ticket, it) }
            val completion = stopped(ticket, digest, stopped, admission != null)
            val facts = report?.let { worker(digest, requireNotNull(admission).worker, it, ticket.kind == "capture") }
            return RootManagedNativeState(ticket, admission?.guard, admission?.worker, completion, facts, spawn)
        }

        private fun stopped(ticket: RootManagedRunTicket, digest: String, bytes: ByteArray, admitted: Boolean): Stopped {
            require(bytes.size == 123)
            require(text(bytes, 0, 107) == "ARI1\n${ticket.runId}\n$digest\n")
            val phase = unsigned(bytes[107]); val worker = unsigned(bytes[108]); val domain = unsigned(bytes[109])
            val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val code = input.getInt(111); val reason = input.getInt(115); val error = input.getInt(119)
            require(bytes[110] == 0.toByte() && phase in listOf(8, 9, 11, 15))
            require((phase and 2 != 0) == admitted && worker in 0..2 && domain in 1..2)
            require(code in -1..255 && reason in 0..5 && error in 0..4095 && (worker == 1 || code == -1))
            require(!admitted || (worker == 1 && domain == 1))
            require(phase and 1 != 0 || (worker == 2 && domain == 2))
            return Stopped(phase, worker, domain, code, reason, error)
        }

        private fun worker(digest: String, identity: RootTargetIdentity, bytes: ByteArray, capture: Boolean): Pair<Int, Int> {
            require(bytes.size == 52 && text(bytes, 0, 4) == (if (capture) "AEX2" else "AEX1") &&
                bytes[4] == (if (capture) 2 else 1).toByte() && bytes[7] == 0.toByte())
            val flags = unsigned(bytes[5]); val status = unsigned(bytes[6])
            require(bytes.copyOfRange(8, 40).joinToString("") { "%02x".format(unsigned(it)) } == digest)
            val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(input.getInt(40) == identity.pid && input.getLong(44) == identity.startTicks)
            if (capture) {
                require(flags in setOf(129, 193) && ((flags == 193) == (status == 0)))
                return flags to status
            }
            require(flags in 1..127 && flags and FINAL != 0 && (flags and LOADED == 0 || flags and ATTEMPTED != 0))
            val success = flags and SUCCESS != 0
            require(if (success) status == 0 && flags and CLEAN == CLEAN && flags and LOADED != 0 else status != 0)
            return flags to status
        }

        private fun text(bytes: ByteArray, start: Int, size: Int) = bytes.copyOfRange(start, start + size).toString(Charsets.US_ASCII)
        private fun unsigned(byte: Byte) = byte.toInt() and 255
    }
}
