package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecretCipher {
    fun encrypt(key: SecretKey, identity: String, plaintext: ByteArray): ByteArray {
        require(plaintext.size in 1..8192)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(identity.toByteArray(Charsets.UTF_8))
        require(cipher.iv.size == 12)
        val encrypted = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size).put(1).put(cipher.iv).put(encrypted).array()
    }

    fun decrypt(key: SecretKey, identity: String, envelope: ByteArray): ByteArray {
        require(envelope.size in 30..8221 && envelope[0] == 1.toByte()) { "Invalid credential envelope" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(identity.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(envelope, 13, envelope.size - 13)
    }
}
