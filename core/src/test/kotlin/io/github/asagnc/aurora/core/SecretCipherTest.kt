package io.github.asagnc.aurora.core

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import kotlin.test.*

class SecretCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun ciphertextUsesFreshNonceAndRoundTrips() {
        val key = key()
        val plain = "fixture-api-key".toByteArray()
        val first = SecretCipher.encrypt(key, "connection:revision", plain)
        val second = SecretCipher.encrypt(key, "connection:revision", plain)
        assertFalse(first.contentEquals(second))
        assertContentEquals(plain, SecretCipher.decrypt(key, "connection:revision", first))
    }

    @Test fun swappingConnectionIdentityIsRejected() {
        val key = key()
        val ciphertext = SecretCipher.encrypt(key, "connection-a:revision", "secret".toByteArray())
        assertFailsWith<AEADBadTagException> { SecretCipher.decrypt(key, "connection-b:revision", ciphertext) }
    }

    @Test fun tamperingWithCiphertextIsRejected() {
        val key = key()
        val ciphertext = SecretCipher.encrypt(key, "connection:revision", "secret".toByteArray())
        ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
        assertFailsWith<AEADBadTagException> { SecretCipher.decrypt(key, "connection:revision", ciphertext) }
    }
}
