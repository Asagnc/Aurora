package io.github.asagnc.aurora.core

import kotlinx.serialization.encodeToString
import kotlin.test.*

class RootManagedResultTest {
    private val ticket = RootManagedRunTicket("00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002", LinuxRootIdentity(10, 20,
            "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004"),
        10000, "test", "a".repeat(64), RootTargetIdentity(90, 900), RootScriptSnapshot("test", "b".repeat(64), 1))
    private val proof = RootManagedExitProof(ticket.runId, ticket.identity, ticket.digest(), true, true, true, true, true)

    @Test fun successfulTransportWithoutCleanupDoesNotReleaseIntent() {
        val result = RootManagedResult(ticket.runId, ticket.digest(), exitCode = 0, workerStatus = 0)
        result.validate(ticket)
        assertFalse(result.confirmed); assertFalse(result.successful)
        assertFailsWith<IllegalArgumentException> { result.copy(cleanup = proof.copy(sessionDetached = false)).validate(ticket) }
    }

    @Test fun failedExecutionCanHaveConfirmedCleanupWithoutClaimingSuccess() {
        val result = RootManagedResult(ticket.runId, ticket.digest(), cleanup = proof, exitCode = 1, workerStatus = 1,
            error = "managed_execution_failed")
        val decoded = WireJson.decodeFromString<RootManagedResult>(WireJson.encodeToString(result))
        decoded.validate(ticket); assertTrue(decoded.confirmed); assertFalse(decoded.successful)
        assertTrue(result.copy(exitCode = 0, workerStatus = 0, error = null).successful)
    }

    @Test fun mismatchedAndContradictoryProofsCannotCrossBinderBoundary() {
        val result = RootManagedResult(ticket.runId, ticket.digest(), cleanup = proof)
        assertFailsWith<IllegalArgumentException> { result.validate(ticket.copy(appBuild = "other")) }
        assertFailsWith<IllegalArgumentException> { result.copy(cleanup = proof.copy(ticketSha256 = "c".repeat(64))).validate(ticket) }
        assertFailsWith<IllegalArgumentException> {
            result.copy(retirement = RootManagedRetirementProof.fromNeverDispatched(ticket)).validate(ticket)
        }
        assertFailsWith<IllegalArgumentException> { result.copy(error = "arbitrary target output").validate(ticket) }
        assertFailsWith<IllegalArgumentException> { result.copy(exitCode = -1).validate(ticket) }
        assertFailsWith<IllegalArgumentException> { result.copy(artifact = "runs/foreign/frida.log").validate(ticket) }
        assertFailsWith<IllegalArgumentException> { result.copy(artifactError = "arbitrary source text").validate(ticket) }
    }
}
