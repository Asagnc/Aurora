package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Build
import java.nio.file.Path

/** APK installation owns this immutable version. A request cannot select executable paths/digests. */
internal class RootManagedBundle(private val context: Context) {
    suspend fun guard(): RootManagedExecutable {
        return executable("guard", "libaurora_managed_guard.so")
    }

    suspend fun worker(): RootManagedExecutable = executable("worker", "libaurora_frida.so")

    private suspend fun executable(kind: String, name: String): RootManagedExecutable {
        val abi = Build.SUPPORTED_ABIS.first()
        require(abi in setOf("arm64-v8a", "x86_64"))
        val digest = context.assets.open("managed/$abi.$kind.sha256").use {
            val bytes = it.readNBytes(65); require(bytes.size == 64); bytes.toString(Charsets.US_ASCII)
        }
        return RootManagedExecutable.open(Path.of(context.applicationInfo.nativeLibraryDir, name), digest)
    }
}
