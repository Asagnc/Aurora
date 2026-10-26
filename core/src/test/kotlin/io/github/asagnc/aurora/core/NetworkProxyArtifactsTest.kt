package io.github.asagnc.aurora.core

import java.security.MessageDigest
import kotlin.test.*

class NetworkProxyArtifactsTest {
    private val id = "11111111-2222-4333-8444-555555555555"

    @Test fun onlyCanonicalSessionIdsReachTheOwnedProjectArtifacts() {
        assertEquals("project/.aurora-proxy/$id", networkProxyDirectory(id))
        assertEquals(listOf(NetworkProxyRuntime.readPath, id, id, "3", "2"), NetworkProxyRead(id, 3, 2).argv(id))
        for (value in listOf("../other", "/etc", "1-1-1-1-1", id + "/extra"))
            assertFails { NetworkProxyRead(value) }
        assertFails { NetworkProxyRead(id, -1) }; assertFails { NetworkProxyRead(id, limit = 11) }
    }

    @Test fun actualOwnedPublicCaIsAcceptedAndPrivateOrExtraBlocksAreRejected() {
        // Public certificate from owned loopback CI40294444272; no signing key or private material is included.
        val pem = requireNotNull(javaClass.getResourceAsStream("/proxy-owned-ca.pem")).use { it.readBytes() }
        val expected = MessageDigest.getInstance("SHA-256").digest(pem).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, publicProxyCaSha256(pem))
        for (invalid in listOf(pem + "-----BEGIN PRIVATE KEY-----\nowned\n-----END PRIVATE KEY-----\n".toByteArray(),
            pem + pem, "-----BEGIN CERTIFICATE-----\nYWJj\n-----END CERTIFICATE-----\n".toByteArray(), ByteArray(16385)))
            assertFails { publicProxyCaSha256(invalid) }
    }
}
