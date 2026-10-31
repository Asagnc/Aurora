package io.github.asagnc.aurora.core

import java.nio.file.Path

/** Fixed reviewed inputs. Native archive is locally selected until a public tool release is explicitly published. */
object AndroidBuildTools {
    val components = listOf(
        StaticReverseComponent("jdk", "25.0.5+8", "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.5_8.tar.gz", "bb5b2ded252f21c6090cf780b0a408ad0d83bbb557e702955643557f13231880", 140178170),
        StaticReverseComponent("gradle", "9.8.2", "https://github.com/gradle/gradle-distributions/releases/download/v9.8.2/gradle-9.8.2-bin.zip", "21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607", 151620138),
        StaticReverseComponent("platform", "37.0-r02", "https://dl.google.com/android/repository/platform-37.0_r02.zip", "840b23e827f96e64aea4c89a1194aac3dc5f6bad37edb231c5c795d890330e8d", 67281901),
        StaticReverseComponent("build-tools", "36.0.0", "https://dl.google.com/android/repository/build-tools_r36_linux.zip", "5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b", 63737259),
        StaticReverseComponent("android-host", "20261031-f60e350b", "",
            "f60e350b11aa1a970219a9300d0bd64ba9c4e26d307846cd854a0cbaaa073055", 484215713, localOnly = true))
    val metadataSha256 = mapOf(
        "build-tools-package.xml" to "4bda9eb99979d0d0af13821b250479a94fa021cf4cfdf284a207068e36dddadc",
        "platform-package.xml" to "9efedaa8306dc2b690b3ed328b417f19dde7537000701fc761241c6b6ed94c32")
    fun catalog(finish: (Path) -> Unit) = SharedToolCatalog("android-build-v1", AndroidBuildRuntime.revision, components,
        2L * 1024 * 1024 * 1024, finish)
    val bundleId: String get() = catalog {}.bundleId
}
