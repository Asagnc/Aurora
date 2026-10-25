package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class RootCaptureRotationTest {
    private fun uuid() = UUID.randomUUID().toString()
    private fun binding() = CaptureRunBinding(uuid(), uuid(), LinuxRootIdentity(123, 456, uuid(), uuid()))
    private fun options() = CaptureRotationOptions("lo", "udp port 1234", 30, 128, 256, 2, 10)
    private fun seal(ordinal: Int = 0) = CaptureSegmentSeal(ordinal, 1, ordinal + 10L, 84, 1, 0, "a".repeat(64), 1,
        5 + ordinal * 10L, 10 + ordinal * 10L, CaptureKernelCounters(10, 2, null))

    private fun ticket(config: RootCaptureConfiguration) = RootManagedRunTicket(config.binding.runId, config.binding.owner,
        config.binding.identity, 10000, "0.1.40", "a".repeat(64), null, config.snapshot(), observeMs = config.options.seconds * 1000,
        protocol = 3, kind = "capture", action = "capture")

    @Test fun onlyTheExactLoopbackSelfProbeCanClaimQualification() {
        val config = RootCaptureConfiguration(binding(), RootCaptureProbe.options)
        val ticket = ticket(config).copy(action = "capture_probe")
        assertTrue(RootCaptureProbe.matches(ticket, config))
        assertFalse(RootCaptureProbe.matches(ticket.copy(action = "capture"), config))
        for (options in listOf(config.options.copy(device = "any"), config.options.copy(filter = "udp"), config.options.copy(seconds = 2))) {
            val changed = config.copy(options = options)
            assertFalse(RootCaptureProbe.matches(ticket(changed).copy(action = "capture_probe"), changed))
        }
        val proof = RootManagedExitProof(ticket.runId, ticket.identity, ticket.digest(), false, false, false, true, true, 3)
        val result = RootManagedResult(ticket.runId, ticket.digest(), cleanup = proof, exitCode = 0, workerStatus = 0, probeObserved = true,
            captureArtifact = "runs/${ticket.runId}/segments/capture-manifest.json")
        result.validate(ticket)
        assertFailsWith<IllegalArgumentException> { result.copy(captureArtifact = null).validate(ticket) }
        assertFailsWith<IllegalArgumentException> { result.copy(captureArtifact = "runs/another/segments/capture-manifest.json").validate(ticket) }
        result.copy(probeObserved = null, captureArtifact = null, artifactError = "capture_publication_unavailable").validate(ticket)
        result.copy(probeObserved = null, captureArtifact = null, captureDiscarded = true).validate(ticket)
        assertFailsWith<IllegalArgumentException> { result.copy(captureDiscarded = true).validate(ticket) }
    }

    @Test fun captureTicketBindsConfigurationAndKeepsTheFridaBudgetSeparate() {
        val config = RootCaptureConfiguration(binding(), options().copy(seconds = 1800)); val ticket = ticket(config)
        config.validateTicket(ticket); assertEquals(1_820_000, ticket.timeoutMs)
        for (changed in listOf(ticket.copy(protocol = 1), ticket.copy(action = "attach"), ticket.copy(kind = "frida"),
            ticket.copy(target = RootTargetIdentity(1, 2)), ticket.copy(bridge = ticket.script), ticket.copy(spawnPackage = "a.b"),
            ticket.copy(observeMs = 1_800_001), ticket.copy(script = ticket.script.copy(version = "not-capture"))))
            assertFailsWith<IllegalArgumentException> { changed.validate() }
        for (changed in listOf(ticket.copy(owner = uuid()), ticket.copy(runId = uuid()), ticket.copy(observeMs = 1000),
            ticket.copy(identity = ticket.identity.copy(epoch = uuid())), ticket.copy(script = ticket.script.copy(sha256 = "b".repeat(64)))))
            assertFailsWith<IllegalArgumentException> { config.validateTicket(changed) }
    }

    @Test fun captureCleanupUsesActualWorkerDomainFactsWithoutInventingFridaCleanup() {
        val ticket = ticket(RootCaptureConfiguration(binding(), options()))
        val proof = RootManagedExitProof(ticket.runId, ticket.identity, ticket.digest(), false, false, false, true, true, 3)
        assertTrue(ticket.confirms(proof))
        for (changed in listOf(proof.copy(scriptUnloaded = true), proof.copy(sessionDetached = true), proof.copy(managerClosed = true),
            proof.copy(workerExited = false), proof.copy(domainRemoved = false), proof.copy(protocol = 1),
            proof.copy(spawnTarget = RootTargetIdentity(1, 2)))) assertFalse(ticket.confirms(changed))
    }

    @Test fun nativeAndJvmUseTheSameFixedConfigurationGoldenVector() {
        fun fixed(i: Int) = "00000000-0000-0000-0000-%012d".format(java.util.Locale.ROOT, i)
        val value = RootCaptureConfiguration(CaptureRunBinding(fixed(1), fixed(2), LinuxRootIdentity(123, 456, fixed(3), fixed(4))), options())
        assertEquals(227, value.encode().size)
        assertEquals("970c1efce8ba1376e0224202d9cff50eb0c7a29ddf214bdb790ce2fb49e735b7", value.snapshot().sha256)
    }

    @Test fun sealedConfigurationRoundTripsAndBindsEveryOwnerEpochAndBudget() {
        val config = RootCaptureConfiguration(binding(), options().copy(filter = "tcp # 自有编解码夹具"))
        assertEquals(config, RootCaptureConfiguration.decode(config.encode()))
        val binding = config.binding
        for (changed in listOf(config.copy(binding = binding.copy(runId = uuid())), config.copy(binding = binding.copy(owner = uuid())),
            config.copy(binding = binding.copy(identity = binding.identity.copy(epoch = uuid()))),
            config.copy(binding = binding.copy(identity = binding.identity.copy(bootId = uuid()))),
            config.copy(binding = binding.copy(identity = binding.identity.copy(startTicks = 457))),
            config.copy(options = config.options.copy(seconds = 31)), config.copy(options = config.options.copy(bytes = 257))))
            assertNotEquals(config.snapshot().sha256, changed.snapshot().sha256)
        assertEquals(config.encode().size.toLong(), config.snapshot().bytes)
    }

    @Test fun corruptOrOversizedConfigurationIsRejectedBeforeAllocationOrDispatch() {
        val valid = RootCaptureConfiguration(binding(), options()).encode()
        for ((offset, value) in listOf(0 to 0, 4 to 2, 8 to 211, 12 to -1, 204 to Int.MAX_VALUE, 208 to -1)) {
            val data = valid.copyOf(); ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
            assertFails { RootCaptureConfiguration.decode(data) }
        }
        assertFails { RootCaptureConfiguration.decode(valid + byteArrayOf(0)) }
        assertFails { RootCaptureConfiguration.decode(valid.copyOf(211)) }
        assertFails { RootCaptureConfiguration.decode(valid.copyOf().also { it[it.lastIndex] = 0xff.toByte() }) }
    }

    @Test fun approvedCapsRemainIndependentAndUnboundedOrCoercedRequestsCannotReserveRuns() {
        options().copy(seconds = 1800, bytes = 536870912, segments = 64).validate()
        for (bad in listOf(options().copy(seconds = 1801), options().copy(segmentBytes = 39), options().copy(bytes = 127),
            options().copy(bytes = 536870913), options().copy(segments = 65), options().copy(filter = "x\u0000y"),
            options().copy(filter = "\ud800"), options().copy(device = "lo;id"))) assertFails { bad.validate() }
        assertFails { CaptureRotationOptions.parse(objectOf("seconds" to string("30"))) }
        assertFails { CaptureRotationOptions.parse(objectOf("unknown" to JsonPrimitive(1))) }
        assertEquals(65536, CaptureRotationOptions.MAX_MANIFEST_BYTES)
    }

    @Test fun registeredPrefixIsIdempotentButCannotBeRewrittenReorderedOrSharedByInode() {
        val binding = binding(); val options = options(); val first = RootCaptureManifest(binding, "b".repeat(64), 1).append(seal(), options)
        assertSame(first, first.append(seal(), options))
        val both = first.append(seal(1), options); both.validate(binding, "b".repeat(64), options)
        assertEquals("capture-001.pcap", both.segments[1].fileName)
        for (bad in listOf(seal().copy(sha256 = "c".repeat(64)), seal(2), seal(1).copy(inode = 10),
            seal(1).copy(linkType = 276), seal(1).copy(sealedBootMs = 5), seal(1).copy(bytes = 129)))
            assertFails { first.append(bad, options) }
        assertFails { both.validate(binding.copy(owner = uuid()), "b".repeat(64), options) }
        assertFails { both.validate(binding, "c".repeat(64), options) }
        assertFails { both.validate(binding, "b".repeat(64), options.copy(bytes = 128)) }
        assertFails { both.validate(binding, "b".repeat(64), options.copy(packets = 1)) }
    }

    @Test fun emptyAndTruncatedSegmentsStillRequireWholePcapRecordAccounting() {
        seal().copy(packets = 0, bytes = 24, snapshotTruncated = 0).validate(options())
        for (bad in listOf(seal().copy(packets = 0), seal().copy(bytes = 39), seal().copy(snapshotTruncated = 2),
            seal().copy(sha256 = "bad"), seal().copy(device = -1), seal().copy(inode = 0))) assertFails { bad.validate(options()) }
    }

    @Test fun rawCountersNeverInventWraparoundDeltasOrFillUnknownFields() {
        val previous = CaptureKernelCounters(0xfffffffe, 10, null)
        val next = CaptureKernelCounters(2, 12, 3)
        assertEquals(CaptureKernelCounters(null, 2, null), next.since(previous))
        assertEquals(CaptureKernelCounters(), CaptureKernelCounters().since(previous))
        assertFails { CaptureKernelCounters(0x100000000).validate() }
    }

    @Test fun fullBoundedManifestRoundTripsWithinTheSeparateMetadataBudget() {
        val binding = binding(); val options = options().copy(segments = 64, bytes = 536870912, packets = 100)
        val manifest = RootCaptureManifest(binding, "b".repeat(64), 1, (0 until 64).map { seal(it) })
        manifest.validate(binding, "b".repeat(64), options)
        val encoded = WireJson.encodeToString(manifest)
        assertTrue(encoded.toByteArray(Charsets.UTF_8).size <= CaptureRotationOptions.MAX_MANIFEST_BYTES)
        assertEquals(manifest, WireJson.decodeFromString<RootCaptureManifest>(encoded))
    }

    @Test fun singleSealRecordBindsItsIntentAndFitsAtomicRecordBudget() {
        val binding = binding(); val options = options()
        val first = RootCaptureSealRecord(binding, "b".repeat(64), 1, seal())
        val empty = RootCaptureManifest(binding, "b".repeat(64), 1)
        assertEquals(listOf(seal()), empty.appendRecord(first, options).segments)
        for (bad in listOf(first.copy(ticketSha256 = "c".repeat(64)), first.copy(binding = binding.copy(owner = uuid())),
            first.copy(startedBootMs = 2), first.copy(protocol = 2))) assertFails { empty.appendRecord(bad, options) }
        val bigOptions = options.copy(segmentBytes = 67108864, bytes = 536870912, segments = 64, packets = 10000000)
        val root = binding.copy(identity = binding.identity.copy(pid = Int.MAX_VALUE, startTicks = Long.MAX_VALUE))
        val big = RootCaptureSealRecord(root, "b".repeat(64), Long.MAX_VALUE - 7000,
            CaptureSegmentSeal(63, Long.MAX_VALUE, Long.MAX_VALUE, 67108864, 4194302, 4194302, "a".repeat(64), 0xffffffffL,
                Long.MAX_VALUE - 6000, Long.MAX_VALUE - 5000, CaptureKernelCounters(0xffffffffL, 0xffffffffL, 0xffffffffL)))
        big.validate(root, "b".repeat(64), bigOptions)
        assertTrue(WireJson.encodeToString(big).toByteArray(Charsets.UTF_8).size <= 1024)
    }
}
