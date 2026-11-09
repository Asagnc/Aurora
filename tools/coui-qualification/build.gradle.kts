plugins {
    id("com.android.application") version "9.4.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}
android {
    namespace = "io.github.asagnc.aurora.uiqualification"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.asagnc.aurora.uiqualification"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-alpha9-qualification"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildTypes { release { isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    } }
}
dependencies {
    // Isolated COUI profile: stable-host attempt was rejected; never applied to main.
    implementation(platform("androidx.compose:compose-bom-alpha:2026.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("io.github.itoseo:coui:0.0.1-alpha9")
    implementation("io.github.itoseo:coui-material3:0.0.1-alpha9")
}

val runtimeArtifacts = provider { configurations.getByName("debugRuntimeClasspath") }
tasks.register("verifyRuntimeVersions") {
    doLast {
        val artifacts = runtimeArtifacts.get().resolvedConfiguration.resolvedArtifacts
        val resolved = artifacts.map { it.moduleVersion.id }.distinct().sortedBy { it.toString() }
        val unexpected = resolved.filter { it.group.startsWith("androidx.compose") &&
            listOf("alpha", "beta", "rc").any { marker -> marker in it.version } &&
            !(it.group == "androidx.compose.material3" && it.version == "1.5.0-rc01") &&
            it.version != "1.13.0-alpha02" }
        check(unexpected.isEmpty()) { "Unapproved Compose prerelease versions: $unexpected" }
        val output = layout.buildDirectory.file("qualification-runtime.txt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(resolved.joinToString("\n") { it.toString() } + "\n")
        println("Qualified dependency selection: ${resolved.size} modules; ${output.name}")
    }
}
