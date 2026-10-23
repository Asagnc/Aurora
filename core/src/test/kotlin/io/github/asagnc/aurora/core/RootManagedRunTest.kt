package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlin.test.*

class RootManagedRunTest {
    private fun ticket() = RootManagedRunTicket(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString()),
        10000, "0.1.40", "a".repeat(64), RootTargetIdentity(789, 1000),
        RootScriptSnapshot("selected-v1", "b".repeat(64), 42))
    private fun proof(ticket: RootManagedRunTicket) = RootManagedExitProof(ticket.runId, ticket.identity,
        ticket.digest(), true, true, true, true, true)

    @Test fun immutableIntentRoundTripAndCleanupBudget() {
        val ticket = ticket()
        assertEquals(ticket, WireJson.decodeFromString<RootManagedRunTicket>(WireJson.encodeToString(ticket)))
        assertEquals(ticket.digest(), ticket.copy().digest())
        assertEquals(270_000L, ticket.copy(observeMs = 250_000).timeoutMs)
        assertEquals(64, ticket.digest().length)
    }
    @Test fun workerAndDomainDeathDoNotProveInjectionCleanup() {
        val ticket = ticket(); val proof = proof(ticket)
        assertTrue(ticket.confirms(proof))
        listOf(proof.copy(scriptUnloaded = false), proof.copy(sessionDetached = false),
            proof.copy(managerClosed = false), proof.copy(workerExited = false), proof.copy(domainRemoved = false))
            .forEach { assertFalse(ticket.confirms(it)) }
    }
    @Test fun proofCannotReleaseDifferentIntentOrRootEpoch() {
        val ticket = ticket(); val proof = proof(ticket)
        listOf(ticket.copy(owner = UUID.randomUUID().toString()), ticket.copy(appBuild = "other"),
            ticket.copy(workerSha256 = "c".repeat(64)), ticket.copy(target = RootTargetIdentity(789, 1001)),
            ticket.copy(script = ticket.script.copy(sha256 = "d".repeat(64))), ticket.copy(observeMs = 2),
            ticket.copy(bridge = ticket.script)).forEach { assertFalse(it.confirms(proof)) }
        assertFalse(ticket.confirms(proof.copy(identity = ticket.identity.copy(epoch = UUID.randomUUID().toString()))))
        assertFalse(ticket.confirms(proof.copy(runId = UUID.randomUUID().toString())))
        assertFalse(ticket.confirms(proof.copy(protocol = 2)))
    }
    @Test fun malformedInputsCannotReserveTasksOrExhaustCleanupWindow() {
        val ticket = ticket()
        listOf(ticket.copy(observeMs = 0), ticket.copy(observeMs = 250_001), ticket.copy(action = "spawn"),
            ticket.copy(kind = "shell"), ticket.copy(protocol = 2), ticket.copy(runId = "../x"),
            ticket.copy(owner = "bad"), ticket.copy(appUid = 0), ticket.copy(appBuild = "bad\n"),
            ticket.copy(workerSha256 = "bad"), ticket.copy(target = RootTargetIdentity(0, 1)),
            ticket.copy(script = ticket.script.copy(bytes = 1_048_577)),
            ticket.copy(bridge = ticket.script.copy(bytes = 0)), ticket.copy(script = ticket.script.copy(sha256 = "bad")))
            .forEach { assertFails { it.validate() } }
    }
}

