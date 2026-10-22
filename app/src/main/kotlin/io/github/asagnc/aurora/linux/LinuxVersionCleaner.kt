package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootLinuxClient
import java.nio.file.*
import java.util.UUID

/** Called under the manager's catalog lock after its lease/current checks. */
internal class LinuxVersionCleaner(private val app: AuroraApplication, private val root: Path,
    private val client: () -> RootLinuxClient? = { app.root.linuxClient }) {
    fun available(id: String): Boolean = metadata(id)?.backend != LinuxBackend.CHROOT || client()?.supportsRootImages == true
    private fun metadata(id: String): LinuxRootfsManifest? {
        require(id.matches(Regex("[a-f0-9]{64}")) && !Files.isSymbolicLink(root))
        val directory = root.resolve(id)
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return null
        require(!Files.isSymbolicLink(directory))
        val metadata = directory.resolve("manifest.json")
        if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) {
            Files.newDirectoryStream(directory).use { require(!it.iterator().hasNext()) }
            return null
        }
        require(Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS) && Files.size(metadata) in 1..4096)
        val manifest = WireJson.decodeFromString<LinuxRootfsManifest>(String(Files.readAllBytes(metadata), Charsets.UTF_8))
        require(manifest.generationId() == id)
        return manifest
    }
    suspend fun remove(id: String): Boolean {
        val manifest = metadata(id) ?: run { Files.deleteIfExists(root.resolve(id)); return true }
        if (manifest.backend == LinuxBackend.CHROOT) {
            val endpoint = client()?.takeIf { it.supportsRootImages } ?: return false
            val staging = app.filesDir.toPath().resolve("spaces/resources/.linux-staging")
            require(Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(staging))
            val stage = Files.createDirectory(staging.resolve(UUID.randomUUID().toString()))
            LinuxRootRemovalBatch(endpoint, stage, manifest, app.applicationInfo.uid).complete()
        } else removeSkillTree(root.resolve(id))
        return true
    }
}
