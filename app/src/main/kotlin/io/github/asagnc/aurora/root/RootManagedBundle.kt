package io.github.asagnc.aurora.root

import android.content.Context
import android.os.Build
import io.github.asagnc.aurora.core.RootScriptSnapshot
import io.github.asagnc.aurora.core.WireJson
import io.github.asagnc.aurora.core.FridaManagedPackage
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** APK installation owns this immutable version. A request cannot select executable paths/digests. */
internal class RootManagedBundle(private val context: Context) {
    suspend fun guard(): RootManagedExecutable {
        return executable("guard", "libaurora_managed_guard.so")
    }

    suspend fun worker(): RootManagedExecutable {
        check(Build.SUPPORTED_ABIS.first() == "arm64-v8a") { "Frida worker is packaged for ARM64 only" }
        return executable("worker", "libaurora_frida.so")
    }

    suspend fun bridgeSnapshot(): RootScriptSnapshot = withContext(Dispatchers.IO) {
        context.assets.open("managed/java-bridge.json").use {
            val bytes = it.readNBytes(1025); require(bytes.size in 1..1024)
            WireJson.decodeFromString<RootScriptSnapshot>(bytes.toString(Charsets.UTF_8)).also { snapshot -> snapshot.validate() }
        }
    }

    suspend fun metadata(nativePassed: Boolean, javaPassed: Boolean): FridaManagedPackage = withContext(Dispatchers.IO) {
        check(Build.SUPPORTED_ABIS.first() == "arm64-v8a")
        FridaManagedPackage(context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString(),
            digest("worker"), bridgeSnapshot(), nativePassed, javaPassed).also { it.validate() }
    }

    private suspend fun executable(kind: String, name: String): RootManagedExecutable {
        val abi = Build.SUPPORTED_ABIS.first()
        require(abi in setOf("arm64-v8a", "x86_64"))
        val digest = digest(kind)
        return RootManagedExecutable.open(Path.of(context.applicationInfo.nativeLibraryDir, name), digest)
    }

    private fun digest(kind: String): String {
        val abi = Build.SUPPORTED_ABIS.first()
        return context.assets.open("managed/$abi.$kind.sha256").use {
            val bytes = it.readNBytes(65); require(bytes.size == 64); bytes.toString(Charsets.US_ASCII)
        }.also { require(it.matches(Regex("[a-f0-9]{64}"))) }
    }
}
