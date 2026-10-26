package io.github.asagnc.aurora.core

import java.util.UUID
import kotlin.test.*

class NetworkProxyCacheTicketTest {
    private val id = UUID.randomUUID().toString()
    private val owner = UUID.randomUUID().toString()
    private fun ticket() = LinuxRunTicket(id, owner, "a".repeat(64),
        LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString()), 10000,
        listOf(NetworkProxyRuntime.launcherPath, id, "18080", "fixed-config"), toolBundle = "b".repeat(64))

    @Test fun existingTicketSelectsOnlyItsFixedPrivateNamespace() {
        assertEquals(".aurora-proxy-private", networkProxyPrivatePrefix(ticket(), owner, 10000))
        assertEquals(".aurora-proxy-read-private", networkProxyPrivatePrefix(ticket().copy(
            argv = listOf(NetworkProxyRuntime.readPath, id, UUID.randomUUID().toString(), "0", "5")), owner, 10000))
    }
    @Test fun otherOwnersUnleasedOrAmbiguousCommandsCannotOwnTheCache() {
        val original = ticket()
        for (value in listOf(original.copy(owner = UUID.randomUUID().toString()), original.copy(appUid = 10001),
            original.copy(toolBundle = null), original.copy(argv = listOf("/bin/sh", "-c", "true")),
            original.copy(argv = listOf(NetworkProxyRuntime.launcherPath, UUID.randomUUID().toString(), "18080", "fixed")),
            original.copy(argv = original.argv + "extra")))
            assertFails { networkProxyPrivatePrefix(value, owner, 10000) }
    }
}
