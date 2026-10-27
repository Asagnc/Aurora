plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}
kotlin { jvmToolchain(25) }
dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.snakeyaml:snakeyaml-engine:3.2")
    implementation("org.apache.commons:commons-compress:1.28.0")
    constraints {
        implementation("commons-io:commons-io:2.22.0")
        implementation("commons-codec:commons-codec:1.23.0")
        implementation("org.apache.commons:commons-lang3:3.21.0")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
tasks.test {
    useJUnitPlatform()
    systemProperty("aurora.repository", rootProject.projectDir.absolutePath)
    inputs.files(rootProject.fileTree("tools/apk-build/sdk-metadata") { include("*.xml") })
    val androidInputs = providers.environmentVariable("AURORA_ANDROID_BUILD_INPUTS").orNull
    inputs.property("androidBuildInputs", androidInputs ?: "not-supplied")
    inputs.property("androidBuildDownload", providers.environmentVariable("AURORA_ANDROID_BUILD_DOWNLOAD").orElse("disabled"))
    val proxyImport = providers.environmentVariable("AURORA_PROXY_IMPORT").orNull
    inputs.property("proxyImport", proxyImport ?: "not-supplied")
    if (proxyImport != null) inputs.file(file(proxyImport))
    inputs.file(rootProject.file("tools/network-proxy/catalog.json"))
    if (androidInputs != null) inputs.files(listOf("jdk", "gradle", "platform", "build-tools", "android-host").map { file("$androidInputs/$it.archive") })
    val reverseSamples = providers.environmentVariable("AURORA_REVERSE_SAMPLES").orNull
    inputs.property("reverseSamples", reverseSamples ?: "not-supplied")
    if (reverseSamples != null) inputs.files(listOf(
        "OpenJDK27U-jre_aarch64_linux_hotspot_27_35.tar.gz", "jadx-1.5.6.zip", "apktool_3.0.3.jar"
    ).map { file("$reverseSamples/$it") })
    systemProperty("aurora.browser.scriptTest", rootProject.file("scripts/verify-browser-javascript.mjs").absolutePath)
    inputs.file(rootProject.file("scripts/verify-browser-javascript.mjs"))
    systemProperty("aurora.browser.transferTest", rootProject.file("scripts/verify-browser-transfer.mjs").absolutePath)
    inputs.file(rootProject.file("scripts/verify-browser-transfer.mjs"))
}
