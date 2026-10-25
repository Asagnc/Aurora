package io.github.asagnc.aurora.core

import kotlinx.serialization.Serializable

@Serializable data class RootCaptureSealRecord(val binding: CaptureRunBinding, val ticketSha256: String,
    val startedBootMs: Long, val segment: CaptureSegmentSeal, val protocol: Int = 1) {
    fun validate(expectedBinding: CaptureRunBinding, expectedTicket: String, options: CaptureRotationOptions) {
        binding.validate(); segment.validate(options)
        require(protocol == 1 && binding == expectedBinding && ticketSha256 == expectedTicket && ticketSha256.matches(Regex("[a-f0-9]{64}")))
        require(startedBootMs >= 0 && segment.openedBootMs >= startedBootMs && segment.sealedBootMs - startedBootMs <= options.seconds * 1000 + 20000)
    }
}

/** Root-private closed-file facts. Physical inode, content and write-handle verification remains mandatory before publication. */
@Serializable data class CaptureSegmentSeal(val ordinal: Int, val device: Long, val inode: Long,
    val bytes: Long, val packets: Long, val snapshotTruncated: Long, val sha256: String, val linkType: Long,
    val openedBootMs: Long, val sealedBootMs: Long, val kernel: CaptureKernelCounters = CaptureKernelCounters()) {
    val fileName: String get() = "capture-%03d.pcap".format(java.util.Locale.ROOT, ordinal)
    fun validate(options: CaptureRotationOptions) {
        options.validate(); kernel.validate()
        require(ordinal in 0 until options.segments && device >= 0 && inode > 0)
        require(bytes in 24..options.segmentBytes && packets in 0..options.packets && snapshotTruncated in 0..packets)
        require(bytes >= 24 + packets * 16 && bytes <= 24 + packets * (16 + CaptureRotationOptions.SNAPSHOT_BYTES))
        require(sha256.matches(Regex("[a-f0-9]{64}")) && linkType in 0..0xffffffffL)
        require(openedBootMs >= 0 && sealedBootMs >= openedBootMs)
    }
}

/** Only an ordered registered prefix may be published; partial/orphan files never become a sealed entry by guessing. */
@Serializable data class RootCaptureManifest(val binding: CaptureRunBinding, val ticketSha256: String,
    val startedBootMs: Long, val segments: List<CaptureSegmentSeal> = emptyList(), val protocol: Int = 1) {
    fun validate(expectedBinding: CaptureRunBinding, expectedTicket: String, options: CaptureRotationOptions) {
        require(binding == expectedBinding && ticketSha256 == expectedTicket) { "Capture manifest belongs to another immutable intent" }
        validateContents(options)
    }

    private fun validateContents(options: CaptureRotationOptions) {
        binding.validate(); options.validate()
        require(protocol == 1 && ticketSha256.matches(Regex("[a-f0-9]{64}")) && startedBootMs >= 0)
        require(segments.size <= options.segments)
        var bytes = 0L; var packets = 0L
        val inodes = mutableSetOf<Pair<Long, Long>>()
        for ((index, segment) in segments.withIndex()) {
            segment.validate(options)
            require(segment.ordinal == index && inodes.add(segment.device to segment.inode))
            require(segment.openedBootMs >= startedBootMs && segment.sealedBootMs - startedBootMs <= options.seconds * 1000 + 20000)
            if (index > 0) {
                val previous = segments[index - 1]
                require(segment.linkType == previous.linkType && segment.openedBootMs >= previous.openedBootMs && segment.sealedBootMs >= previous.sealedBootMs)
            }
            bytes += segment.bytes; packets += segment.packets
            require(bytes <= options.bytes && packets <= options.packets)
        }
    }

    fun append(segment: CaptureSegmentSeal, options: CaptureRotationOptions): RootCaptureManifest {
        validateContents(options)
        if (segment.ordinal in segments.indices) {
            require(segments[segment.ordinal] == segment) { "A registered segment cannot change" }
            return this
        }
        require(segment.ordinal == segments.size)
        return copy(segments = segments + segment).also { it.validateContents(options) }
    }

    fun appendRecord(record: RootCaptureSealRecord, options: CaptureRotationOptions): RootCaptureManifest {
        record.validate(binding, ticketSha256, options)
        require(record.startedBootMs == startedBootMs)
        return append(record.segment, options)
    }
}
