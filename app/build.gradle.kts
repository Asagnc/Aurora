import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "io.github.asagnc.aurora"
    compileSdk = 37
    ndkVersion = "30.0.16248370"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" } }
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { targets += listOf("aurora_runner", "aurora_pty", "aurora_proot", "aurora_linux_namespace", "aurora_linux_scope", "aurora_tcpdump", "aurora_packet_worker", "aurora_managed_guard") } }
        applicationId = "io.github.asagnc.aurora"
        minSdk = 36
        targetSdk = 37
        versionCode = 41
        versionName = "0.1.40"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notAnnotation"] = "io.github.asagnc.aurora.root.RootRequired,io.github.asagnc.aurora.linux.LinuxImageRequired"
    }
    buildFeatures { compose = true; aidl = true }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
dependencies {
    implementation(project(":terminal"))
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.46.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:service:6.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

abstract class VerifyDeviceResults : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reports: ConfigurableFileCollection

    @TaskAction fun verify() {
        val documents = reports.files.map { file ->
            javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }.newDocumentBuilder().parse(file).documentElement
        }
        check(documents.isNotEmpty()) { "No device test reports were produced" }
        check(documents.all { it.getAttribute("tests").toInt() - it.getAttribute("skipped").ifEmpty { "0" }.toInt() >= 8 }) {
            "Expected at least eight persistence tests per device report"
        }
        check(documents.all { it.getAttribute("failures").toInt() == 0 && it.getAttribute("errors").toInt() == 0 }) {
            "Device tests failed"
        }
    }
}

tasks.register<VerifyDeviceResults>("verifyDevicePersistence") {
    dependsOn("connectedDebugAndroidTest")
    reports.from(fileTree(layout.buildDirectory.dir("outputs/androidTest-results/connected/debug")) { include("**/*.xml") })
}

abstract class StageNativeRunner : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaries: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:javax.inject.Inject abstract val fileOperations: FileSystemOperations
    @TaskAction fun stage() {
        check(binaries.get().asFile.walkTopDown().any { it.isFile && it.name == "aurora_runner" }) { "Native runner was not built" }
        fileOperations.sync {
            from(binaries) {
                include("*/aurora_runner", "*/aurora_pty", "*/aurora_proot", "*/aurora_proot_loader", "*/aurora_linux_namespace", "*/aurora_linux_scope", "*/aurora_tcpdump", "*/aurora_packet_worker", "*/aurora_managed_guard")
                eachFile { path = "${file.parentFile.name}/lib${file.name}.so" }
                includeEmptyDirs = false
            }
            into(outputDirectory)
        }
    }
}

// Immutable APK native-library directory; never execute downloaded files from writable app data.
androidComponents.onVariants { variant ->
    val capital = variant.name.replaceFirstChar { it.uppercase() }
    val kind = if (variant.buildType == "debug") "Debug" else "RelWithDebInfo"
    val stage = tasks.register<StageNativeRunner>("stage${capital}Runner") {
        dependsOn("externalNativeBuild$capital")
        binaries.set(layout.buildDirectory.dir("native-runner/$kind"))
        outputDirectory.set(layout.buildDirectory.dir("generated/runner/${variant.name}"))
    }
    variant.sources.jniLibs?.addGeneratedSourceDirectory(stage, StageNativeRunner::outputDirectory)
    val manifest = tasks.register<StageManagedManifest>("stage${capital}ManagedManifest") {
        dependsOn("strip${capital}DebugSymbols")
        binaries.set(layout.buildDirectory.dir("intermediates/stripped_native_libs/${variant.name}/strip${capital}DebugSymbols/out/lib"))
        outputDirectory.set(layout.buildDirectory.dir("generated/managed-manifest/${variant.name}"))
    }
    variant.sources.assets?.addGeneratedSourceDirectory(manifest, StageManagedManifest::outputDirectory)
}

/** Digests of the actual APK payload, after symbol stripping; never hashes a caller-supplied path. */
abstract class StageManagedManifest : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaries: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun stage() {
        val output = outputDirectory.get().asFile.resolve("managed")
        output.mkdirs()
        for (abi in listOf("arm64-v8a", "x86_64")) {
            val guard = binaries.get().asFile.resolve("$abi/libaurora_managed_guard.so")
            check(guard.isFile)
            val digest = MessageDigest.getInstance("SHA-256")
            guard.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
            }
            output.resolve("$abi.guard.sha256").writeText(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        }
    }
}
