@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package io.github.asagnc.aurora.core

import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.encodeToString

@Serializable data class RootScriptSnapshot(val version: String, val sha256: String, val bytes: Long) {
    fun validate() {
        require(version.isNotBlank() && version.length <= 128 && version.none { it.isISOControl() })
        require(sha256.matches(Regex("[a-f0-9]{64}")) && bytes in 1..1_048_576)
    }
}

@Serializable data class RootTargetIdentity(val pid: Int, val startTicks: Long) {
    fun validate() { require(pid > 0 && startTicks > 0) }
}

/** Immutable intent; script bytes and mutable execution phases never belong in this record. */
@Serializable data class RootManagedRunTicket(
    val runId: String, val owner: String, val identity: LinuxRootIdentity,
    val appUid: Int, val appBuild: String, val workerSha256: String,
    val target: RootTargetIdentity?, val script: RootScriptSnapshot,
    val bridge: RootScriptSnapshot? = null, val observeMs: Long = 60_000,
    val protocol: Int = 1, val kind: String = "frida", val action: String = "attach",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val spawnPackage: String? = null,
) {
    fun validate() {
        require(kind == "frida")
        require(if (action == "attach") protocol == 1 && target != null && spawnPackage == null else
            action == "spawn" && protocol == 2 && target == null && spawnPackage != null &&
                spawnPackage.length <= 255 && spawnPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")))
        require(UUID.fromString(runId).toString() == runId && UUID.fromString(owner).toString() == owner)
        identity.validate(); target?.validate(); script.validate(); bridge?.validate()
        require(appUid > 0 && appBuild.isNotBlank() && appBuild.length <= 128 && appBuild.none { it.isISOControl() })
        require(workerSha256.matches(Regex("[a-f0-9]{64}")))
        require(observeMs in 1..250_000)
    }
    val timeoutMs: Long get() = observeMs + 20_000
    fun digest(): String {
        validate()
        return MessageDigest.getInstance("SHA-256").digest(WireJson.encodeToString(this).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun confirms(proof: RootManagedExitProof): Boolean =
        proof.protocol == protocol && proof.runId == runId && proof.identity == identity &&
            proof.ticketSha256 == digest() && proof.scriptUnloaded && proof.sessionDetached &&
            proof.managerClosed && proof.workerExited && proof.domainRemoved &&
            (action != "spawn" || proof.spawnTarget?.let { runCatching { it.validate() }.isSuccess } == true)
}

/** Worker death / empty cgroup alone cannot prove cleanup inside an existing target. */
@Serializable data class RootManagedExitProof(
    val runId: String, val identity: LinuxRootIdentity, val ticketSha256: String,
    val scriptUnloaded: Boolean, val sessionDetached: Boolean, val managerClosed: Boolean,
    val workerExited: Boolean, val domainRemoved: Boolean, val protocol: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val spawnTarget: RootTargetIdentity? = null,
)
