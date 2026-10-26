package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.MessageDigest

/** Prepares only the installer's private stage; never executes an imported SDK binary. */
object AndroidBuildPreparation {
    fun prepare(component: StaticReverseComponent, archive: Path, target: Path, mode: (Path, Int) -> Unit) {
        require(component in AndroidBuildTools.components)
        verifyStaticReverseArchive(component, archive)
        var parent = target.toAbsolutePath().normalize().parent
        while (parent != null) {
            require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
            parent = parent.parent
        }
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
        try {
            if (component.id in setOf("jdk", "android-host")) {
                val prefix = if (component.id == "jdk") "jdk-25.0.5+8" else "android-host"
                val unpacker = RootfsArchive(RootfsBudget(expanded = 1024L * 1024 * 1024, entries = 30000))
                val plan = unpacker.scan(archive)
                require(plan.all { it.path == prefix || it.path.startsWith("$prefix/") })
                unpacker.extract(archive, target, plan, mode = mode)
            } else {
                val executable = SkillArchive(SkillArchiveBudget(compressed = 512L * 1024 * 1024,
                    expanded = 768L * 1024 * 1024, file = 256L * 1024 * 1024, entries = 30000)).extract(archive, target)
                executable.forEach { mode(target.resolve(it), 493) }
            }
            verifyStaticReverseArchive(component, archive)
        } catch (failure: Throwable) {
            try { removeSkillTree(target) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    fun finish(stage: Path, metadata: Map<String, ByteArray>, mode: (Path, Int) -> Unit) {
        val hostContainer = stage.resolve("android-host")
        val host = hostContainer.resolve("android-host")
        require(Files.isDirectory(host, LinkOption.NOFOLLOW_LINKS))
        val sdk = Files.createDirectory(stage.resolve("sdk"))
        moveSdk(stage.resolve("platform/android-37.0"), sdk, "platforms/android-37.0")
        moveSdk(stage.resolve("build-tools/android-16"), sdk, "build-tools/36.0.0")
        moveSdk(host.resolve("ndk"), sdk, "ndk/30.0.16248370")
        moveSdk(host.resolve("cmake"), sdk, "cmake/4.1.2")
        val buildTools = sdk.resolve("build-tools/36.0.0")
        rejectForeignExecutables(buildTools, mode)
        for (name in listOf("aapt2", "aidl", "zipalign")) {
            val source = host.resolve("resources/$name")
            requireSharedArm64Tool(source)
            Files.copy(source, buildTools.resolve(name), StandardCopyOption.REPLACE_EXISTING)
            mode(buildTools.resolve(name), 493)
        }
        writeMetadata(sdk, metadata)
        requireSharedArm64Tool(stage.resolve("jdk/jdk-25.0.5+8/bin/java"))
        requireSharedArm64Tool(sdk.resolve("cmake/4.1.2/bin/cmake"))
        requireSharedArm64Tool(sdk.resolve("ndk/30.0.16248370/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"))
        require(Files.isRegularFile(sdk.resolve("platforms/android-37.0/android.jar"), LinkOption.NOFOLLOW_LINKS))
        require(Files.isRegularFile(buildTools.resolve("lib/apksigner.jar"), LinkOption.NOFOLLOW_LINKS))
        val bin = Files.createDirectory(host.resolve("bin"))
        writeExecutable(bin.resolve("aurora-gradle"), AndroidBuildRuntime.launcher, mode)
        writeExecutable(bin.resolve("aurora-apk-probe"), AndroidBuildRuntime.probe, mode)
        Files.newDirectoryStream(host).use { children ->
            children.forEach { Files.move(it, hostContainer.resolve(it.fileName)) }
        }
        Files.delete(host)
        removeSkillTree(stage.resolve("platform")); removeSkillTree(stage.resolve("build-tools"))
    }

    private fun moveSdk(source: Path, sdk: Path, relative: String) {
        require(Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) { "Missing SDK component $relative" }
        val target = sdk.resolve(relative)
        Files.createDirectories(target.parent)
        Files.move(source, target)
    }

    private fun writeMetadata(sdk: Path, metadata: Map<String, ByteArray>) {
        require(metadata.keys == AndroidBuildTools.metadataSha256.keys)
        for ((name, expected) in AndroidBuildTools.metadataSha256) {
            val bytes = metadata.getValue(name)
            require(bytes.size <= 128 * 1024)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            require(digest == expected) { "SDK metadata digest mismatch" }
            val target = if (name == "platform-package.xml") "platforms/android-37.0" else "build-tools/36.0.0"
            Files.write(sdk.resolve("$target/package.xml"), bytes)
        }
    }

    private fun rejectForeignExecutables(root: Path, mode: (Path, Int) -> Unit) {
        Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.forEach { path ->
            val header = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(20) }
            if (isElf(header)) writeExecutable(path, AndroidBuildRuntime.rejection, mode)
        } }
    }

    private fun isElf(header: ByteArray) = header.size == 20 && header.take(4) == listOf(127, 69, 76, 70).map(Int::toByte)

    private fun writeExecutable(path: Path, text: String, mode: (Path, Int) -> Unit) {
        Files.write(path, text.toByteArray(Charsets.UTF_8)); mode(path, 493)
    }
}
