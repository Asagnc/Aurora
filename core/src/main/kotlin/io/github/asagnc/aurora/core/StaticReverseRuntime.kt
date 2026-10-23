package io.github.asagnc.aurora.core

/** Shared by startup probes and analysis so device qualification uses the same JVM profile. */
fun staticReverseJava(heapMiB: Int = 768, threads: Int = 4): List<String> {
    require(heapMiB in 128..2048 && threads in 1..8)
    return listOf("/opt/jre/jdk-27+35-jre/bin/java", "-Djava.awt.headless=true",
        "--enable-native-access=ALL-UNNAMED", "-Xms32m", "-Xmx${heapMiB}m", "-XX:ActiveProcessorCount=$threads")
}

fun staticReverseStartupCommands(): List<List<String>> {
    val java = staticReverseJava(256, 4)
    return listOf(java + "--version",
        java + listOf("-Djdk.util.zip.disableZip64ExtraFieldValidation=true", "-cp", "/opt/jadx/lib/jadx-1.5.6-all.jar", "jadx.cli.JadxCLI", "--version"),
        java + listOf("-jar", "/opt/apktool/apktool.jar", "--version"))
}
