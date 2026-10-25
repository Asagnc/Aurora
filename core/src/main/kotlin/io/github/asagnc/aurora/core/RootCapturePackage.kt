package io.github.asagnc.aurora.core

import kotlinx.serialization.Serializable

@Serializable data class RootCapturePackage(val appBuild: String, val workerSha256: String, val probePassed: Boolean, val protocol: Int = 1) {
    fun validate() {
        require(protocol == 1 && appBuild.matches(Regex("[0-9]{1,20}")) && workerSha256.matches(Regex("[a-f0-9]{64}")))
    }
}

/** Explicit one-second self-probe: opens only loopback, captures no normal packets, and proves the full seal/publication path. */
object RootCaptureProbe {
    val options = CaptureRotationOptions("lo", "less 1", 1, 4096, 4096, 1, 1)
    fun matches(ticket: RootManagedRunTicket, config: RootCaptureConfiguration): Boolean {
        config.validateTicket(ticket)
        return ticket.action == "capture_probe" && config.options == options
    }
}
