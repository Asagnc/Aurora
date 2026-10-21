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
@Serializable data class LinuxRootfsManifest(val image: LinuxImage, val entries: Int, val expandedBytes: Long)

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
