plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}
kotlin { jvmToolchain(25) }
dependencies {
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
    systemProperty("aurora.browser.scriptTest", rootProject.file("scripts/verify-browser-javascript.mjs").absolutePath)
    inputs.file(rootProject.file("scripts/verify-browser-javascript.mjs"))
    systemProperty("aurora.browser.transferTest", rootProject.file("scripts/verify-browser-transfer.mjs").absolutePath)
    inputs.file(rootProject.file("scripts/verify-browser-transfer.mjs"))
}
