package io.github.asagnc.aurora.core

import kotlinx.serialization.Serializable

@Serializable enum class LinuxArchitecture(val abi: String, val imageName: String, val elfMachine: Int) {
    ARM64("arm64-v8a", "arm64", 183), AMD64("x86_64", "amd64", 62);
    companion object {
        fun fromAbi(abi: String) = entries.singleOrNull { it.abi == abi } ?: error("Unsupported Linux ABI: $abi")
    }
}
@Serializable data class LinuxImage(val distribution: String, val version: String, val architecture: LinuxArchitecture,
    val url: String, val sha256: String)
@Serializable enum class LinuxBackend { PROOT, CHROOT }
@Serializable data class LinuxRootfsManifest(val image: LinuxImage, val entries: Int, val expandedBytes: Long,
    val backend: LinuxBackend = LinuxBackend.PROOT, val ownershipVersion: Int = if (backend == LinuxBackend.CHROOT) 1 else 0) {
    fun generationId(): String = linuxGenerationId(image.sha256, backend).also {
        require(ownershipVersion == if (backend == LinuxBackend.CHROOT) 1 else 0)
        require(entries in 0..50000 && expandedBytes in 0..2L * 1024 * 1024 * 1024)
    }
}

/** Preserve ordinary legacy IDs; Root ownership is a distinct immutable generation. */
fun linuxGenerationId(image: String, backend: LinuxBackend): String {
    require(image.matches(Regex("[a-f0-9]{64}")))
    if (backend == LinuxBackend.PROOT) return image
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest("aurora-linux-chroot-ownership-v1:$image".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Digests checked against the official SHA256SUMS signed by the pinned Ubuntu CD Image key. */
object UbuntuBaseImages {
    const val signer = "843938DF228D22F7B3742BC0D94AA3F0EFE21092"
    fun current(architecture: LinuxArchitecture): LinuxImage {
        val hash = when (architecture) {
            LinuxArchitecture.ARM64 -> "8b7ea4ed71a92873f7f4e260a3defe0d9ed85cb69c0155b12414208f79fa0f2d"
            LinuxArchitecture.AMD64 -> "ff50c868fa972e956b6406d7efc6e3e47746247f5b301a7c29465e609788c3fc"
        }
        return LinuxImage("Ubuntu Base", "26.10", architecture,
            "https://cdimage.ubuntu.com/ubuntu-base/releases/26.10/release/ubuntu-base-26.10-base-${architecture.imageName}.tar.gz", hash)
    }
}
