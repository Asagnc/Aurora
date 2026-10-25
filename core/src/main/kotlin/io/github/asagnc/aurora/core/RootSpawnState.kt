package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Trusted Root-private phase records; a requested call with no captured PID remains unknown. */
data class RootSpawnState(val requested: Boolean, val target: RootTargetIdentity?, val resumeRequested: Boolean,
    val retired: Boolean, val resumed: Boolean) {
    val settled: Boolean get() = target != null && (resumed || retired)
    companion object {
        fun decode(ticket: RootManagedRunTicket, phases: List<ByteArray?>): RootSpawnState {
            require(ticket.action == "spawn" && phases.size == 5)
            val targets = phases.mapIndexed { index, bytes -> bytes?.let { record(ticket, index + 1, it) } }
            require(phases[0] != null || phases.all { it == null })
            require((phases[2] == null && phases[3] == null && phases[4] == null) || phases[1] != null)
            require(phases[4] == null || phases[2] != null)
            require(phases[2] == null || phases[3] == null)
            for (index in 2..4) require(phases[index] == null || targets[index] == targets[1])
            return RootSpawnState(phases[0] != null, targets[1], phases[2] != null, phases[3] != null, phases[4] != null)
        }
        private fun record(ticket: RootManagedRunTicket, phase: Int, bytes: ByteArray): RootTargetIdentity? {
            require(bytes.size == 64 && String(bytes, 0, 4, Charsets.US_ASCII) == "ASP2" && bytes[4].toInt() == phase)
            require((5..7).all { bytes[it] == 0.toByte() } && (52..63).all { bytes[it] == 0.toByte() })
            require(bytes.copyOfRange(8, 40).joinToString("") { "%02x".format(it.toInt() and 255) } == ticket.digest())
            val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val pid = input.getInt(40); val ticks = input.getLong(44)
            if (phase == 1) { require(pid == 0 && ticks == 0L); return null }
            return RootTargetIdentity(pid, ticks).also { it.validate() }
        }
    }
}
