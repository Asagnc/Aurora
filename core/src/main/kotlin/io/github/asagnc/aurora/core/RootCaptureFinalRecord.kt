package io.github.asagnc.aurora.core

import kotlinx.serialization.Serializable

/** Final observed counters, not a substitute for actual worker/domain exit proof. Missing records remain unknown. */
@Serializable data class RootCaptureFinalRecord(val binding: CaptureRunBinding, val ticketSha256: String,
    val startedBootMs: Long, val finishedBootMs: Long, val segments: Int, val bytes: Long, val packets: Long,
    val reason: String, val status: Int, val kernel: CaptureKernelCounters, val protocol: Int = 1) {
    fun validate(ticket: RootManagedRunTicket) {
        ticket.validate(); require(ticket.kind == "capture")
        context(CaptureRunBinding(ticket.runId, ticket.owner, ticket.identity), ticket.digest(), ticket.observeMs)
        require(segments in 0..64 && bytes in 0..536_870_912 && packets in 0..10_000_000)
    }

    fun validate(binding: CaptureRunBinding, ticket: String, options: CaptureRotationOptions) {
        options.validate(); context(binding, ticket, options.seconds * 1000)
        require(segments in 0..options.segments && bytes in 0..options.bytes && packets in 0..options.packets)
    }

    private fun context(expectedBinding: CaptureRunBinding, expectedTicket: String, duration: Long) {
        binding.validate(); kernel.validate()
        require(protocol == 1 && binding == expectedBinding && ticketSha256 == expectedTicket && ticketSha256.matches(Regex("[a-f0-9]{64}")))
        require(startedBootMs >= 0 && finishedBootMs >= startedBootMs && finishedBootMs - startedBootMs <= duration + 20000)
        require(segments >= 0 && packets >= 0 && bytes >= segments * 24L && bytes <= segments * 24L + packets * (16 + CaptureRotationOptions.SNAPSHOT_BYTES))
        require(segments != 0 || (bytes == 0L && packets == 0L))
        require(when (status) {
            0 -> reason in setOf("duration", "storage_budget", "packet_budget")
            2 -> reason == "cancelled"
            1 -> reason in setOf("setup_error", "capture_error", "write_error", "supervisor_error", "metadata_error")
            else -> false
        })
    }
}
