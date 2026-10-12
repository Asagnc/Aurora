package io.github.asagnc.aurora.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import io.github.asagnc.aurora.core.SecretCipher
import io.github.asagnc.aurora.core.WireJson

@Serializable private data class VaultEnvelope(val revision: String, val payload: String)
data class Credential(val revision: String, val token: String) {
    override fun toString(): String = "Credential(revision=$revision, token=<redacted>)"
}

class CredentialVault(context: Context) {
    private val directory = File(context.noBackupFilesDir, "credentials")

    suspend fun put(connectionId: String, token: String): String = withContext(Dispatchers.IO) {
        lock.withLock {
            require(token.isNotBlank() && token.length <= 8192 && token.none { it == '\r' || it == '\n' })
            val file = record(connectionId)
            readRecord(connectionId)?.takeIf { it.token == token }?.let { return@withLock it.revision }
            val revision = UUID.randomUUID().toString()
            val plain = token.toByteArray()
            val encrypted = try { SecretCipher.encrypt(key(), "$connectionId:$revision", plain) } finally { plain.fill(0) }
            val envelope = VaultEnvelope(revision, Base64.getEncoder().encodeToString(encrypted))
            val stream = file.startWrite()
            try {
                stream.write(WireJson.encodeToString(VaultEnvelope.serializer(), envelope).toByteArray())
                file.finishWrite(stream)
            } catch (failure: Exception) { file.failWrite(stream); throw failure }
            revision
        }
    }

    suspend fun get(connectionId: String): Credential? = withContext(Dispatchers.IO) {
        lock.withLock { readRecord(connectionId) }
    }

    private fun readRecord(connectionId: String): Credential? {
        val file = record(connectionId)
        val encoded = try { file.openRead().use { input -> input.readNBytes(16 * 1024 + 1) } }
            catch (_: FileNotFoundException) { return null }
        require(encoded.size <= 16 * 1024) { "Credential record exceeded its budget" }
        val envelope = WireJson.decodeFromString(VaultEnvelope.serializer(), encoded.toString(Charsets.UTF_8))
        UUID.fromString(envelope.revision)
        val token = SecretCipher.decrypt(key(), "$connectionId:${envelope.revision}", Base64.getDecoder().decode(envelope.payload))
        return try { Credential(envelope.revision, token.toString(Charsets.UTF_8)) } finally { token.fill(0) }
    }

    suspend fun remove(connectionId: String): Unit = withContext(Dispatchers.IO) {
        lock.withLock { record(connectionId).delete() }
    }

    private fun record(id: String): AtomicFile {
        require(UUID.fromString(id).toString() == id)
        check(directory.isDirectory || directory.mkdirs())
        return AtomicFile(File(directory, "$id.json"))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    companion object {
        private const val ALIAS = "aurora.credentials.v1"
        private val lock = Mutex()
    }
}
