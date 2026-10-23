package io.github.asagnc.aurora.core

import java.security.MessageDigest

/** Fixed bounded control metadata only. Script bytes travel as sealed descriptors. */
class RootFdFrame(nonce: String, ticketSha256: String, private val count: Int) {
    private val nonceBytes = decode(nonce)
    private val ticketBytes = decode(ticketSha256)
    init { require(count in 1..2) }
    fun encode(role: Int): ByteArray {
        require(role in 1..3)
        return ByteArray(72).apply {
            "AFD1".toByteArray(Charsets.US_ASCII).copyInto(this)
            this[4] = role.toByte(); this[5] = count.toByte()
            nonceBytes.copyInto(this, 8); ticketBytes.copyInto(this, 40)
        }
    }
    fun matches(role: Int, frame: ByteArray): Boolean = MessageDigest.isEqual(encode(role), frame)
    private fun decode(value: String): ByteArray {
        require(value.matches(Regex("[a-f0-9]{64}")))
        return ByteArray(32) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
