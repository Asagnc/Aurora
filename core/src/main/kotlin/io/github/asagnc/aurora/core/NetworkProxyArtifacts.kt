package io.github.asagnc.aurora.core

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.UUID

fun networkProxyDirectory(id: String): String {
    require(UUID.fromString(id).toString() == id)
    return "project/.aurora-proxy/$id"
}

data class NetworkProxyRead(val session: String, val offset: Int = 0, val limit: Int = 5) {
    init { networkProxyDirectory(session); require(offset in 0..512 && limit in 1..10) }
    fun argv(runId: String): List<String> {
        require(UUID.fromString(runId).toString() == runId)
        return listOf(NetworkProxyRuntime.readPath, runId, session, offset.toString(), limit.toString())
    }
}

/** Validate the exact bytes before requesting a SHA-bound Root export. No private key or extra PEM block is accepted. */
fun publicProxyCaSha256(bytes: ByteArray): String {
    require(bytes.size in 1..16384)
    val pem = bytes.toString(Charsets.US_ASCII)
    require(pem.toByteArray(Charsets.US_ASCII).contentEquals(bytes))
    require(pem.matches(Regex("-----BEGIN CERTIFICATE-----[\\r\\n]+[A-Za-z0-9+/=\\r\\n]+-----END CERTIFICATE-----[\\r\\n]*")))
    val certificates = CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(bytes))
    require(certificates.size == 1)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
