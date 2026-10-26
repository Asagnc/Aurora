package io.github.asagnc.aurora.core

import java.security.MessageDigest

/** Fixed shared guest profile. Caller passes a bounded project path and task names as literal argv, never shell code. */
object AndroidBuildRuntime {
    const val launcherPath = "/opt/android-host/bin/aurora-gradle"
    const val probePath = "/opt/android-host/bin/aurora-apk-probe"
    const val probeMarker = "aurora-apk-probe-ok"
    val rejection = "#!/bin/sh\necho 'Unsupported foreign SDK executable' >&2\nexit 126\n"
    private val environment = """
        export JAVA_HOME=/opt/jdk/jdk-25.0.5+8
        export GRADLE_HOME=/opt/gradle/gradle-9.8.2
        export ANDROID_HOME=/opt/sdk
        export ANDROID_SDK_ROOT=/opt/sdk
        export LD_LIBRARY_PATH=/opt/android-host/runtime-lib
        export PATH="${'$'}JAVA_HOME/bin:/opt/sdk/cmake/4.1.2/bin:/usr/bin:/bin"
        export GRADLE_USER_HOME=/mnt/.gradle-cache
        export GRADLE_OPTS='-Xmx1024m -XX:ActiveProcessorCount=4 -Djava.awt.headless=true'
    """.trimIndent()
    val launcher = "#!/bin/sh\nset -eu\n$environment\n" + """
        [ "${'$'}#" -ge 2 ] || exit 64
        project=${'$'}1
        shift
        [ -d "${'$'}project" ] || exit 66
        exec "${'$'}GRADLE_HOME/bin/gradle" --no-daemon --console=plain --max-workers=2 \
          -Pandroid.aapt2FromMavenOverride=/opt/sdk/build-tools/36.0.0/aapt2 \
          -Pandroid.builder.sdkDownload=false -p "${'$'}project" "${'$'}@"
    """.trimIndent() + "\n"

    val probe = "#!/bin/sh\nset -eu\n$environment\n" + """
        [ "${'$'}#" -eq 1 ] || exit 64
        case "${'$'}1" in ''|*[!a-f0-9-]*) exit 64 ;; esac
        base=/mnt/runs/${'$'}1/apk-probe
        mkdir -p "${'$'}base/src/main/java/io/github/asagnc/aurora/sdkprobe"
        cat > "${'$'}base/settings.gradle" <<'SETTINGS'
        pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
        dependencyResolutionManagement { repositories { google(); mavenCentral() } }
        rootProject.name = 'aurora-sdk-probe'
        SETTINGS
        cat > "${'$'}base/build.gradle" <<'BUILD'
        plugins {
          id 'com.android.application' version '9.4.1'
          id 'org.jetbrains.kotlin.plugin.compose' version '2.4.21'
        }
        android {
          namespace 'io.github.asagnc.aurora.sdkprobe'
          compileSdk 37
          buildToolsVersion '36.0.0'
          ndkVersion '30.0.16248370'
          defaultConfig {
            applicationId 'io.github.asagnc.aurora.sdkprobe'; minSdk 36; targetSdk 37; versionCode 1; versionName '1'
            ndk { abiFilters 'arm64-v8a', 'x86_64' }
            externalNativeBuild { cmake { arguments '-DANDROID_STL=c++_shared'; targets 'owned' } }
          }
          compileOptions { sourceCompatibility JavaVersion.VERSION_25; targetCompatibility JavaVersion.VERSION_25 }
          buildFeatures { compose = true }
          externalNativeBuild { cmake { path file('CMakeLists.txt'); version '4.1.2' } }
        }
        dependencies { implementation platform('androidx.compose:compose-bom:2026.09.00'); implementation 'androidx.compose.foundation:foundation' }
        BUILD
        printf '%s\n' 'sdk.dir=/opt/sdk' > "${'$'}base/local.properties"
        cat > "${'$'}base/src/main/AndroidManifest.xml" <<'MANIFEST'
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:hasCode="true" android:label="Aurora SDK probe"/></manifest>
        MANIFEST
        cat > "${'$'}base/src/main/java/io/github/asagnc/aurora/sdkprobe/Owned.java" <<'JAVA'
        package io.github.asagnc.aurora.sdkprobe;
        public class Owned { static { System.loadLibrary("owned"); } public static native String nativeMarker(); }
        JAVA
        cat > "${'$'}base/src/main/java/io/github/asagnc/aurora/sdkprobe/Screen.kt" <<'KOTLIN'
        package io.github.asagnc.aurora.sdkprobe
        import androidx.compose.runtime.Composable
        import androidx.compose.foundation.text.BasicText
        @Composable fun OwnedScreen() { BasicText("Aurora owned SDK probe") }
        KOTLIN
        cat > "${'$'}base/CMakeLists.txt" <<'CMAKE'
        cmake_minimum_required(VERSION 3.22)
        project(owned LANGUAGES CXX)
        add_library(owned SHARED owned.cpp)
        target_link_options(owned PRIVATE -Wl,-z,max-page-size=16384)
        CMAKE
        cat > "${'$'}base/owned.cpp" <<'CPP'
        #include <jni.h>
        #include <string>
        extern "C" JNIEXPORT jstring JNICALL Java_io_github_asagnc_aurora_sdkprobe_Owned_nativeMarker(JNIEnv *env, jclass) {
          std::string value = "owned-" + std::to_string(42); return env->NewStringUTF(value.c_str());
        }
        CPP
        /opt/android-host/bin/aurora-gradle "${'$'}base" --quiet assembleRelease
        apk=${'$'}base/build/outputs/apk/release/aurora-sdk-probe-release-unsigned.apk
        [ -s "${'$'}apk" ] || exit 70
        /opt/sdk/build-tools/36.0.0/zipalign -c -P 16 4 "${'$'}apk"
        mkdir "${'$'}base/inspect"
        cd "${'$'}base/inspect"
        "${'$'}JAVA_HOME/bin/jar" --extract --file "${'$'}apk" lib/arm64-v8a/libowned.so lib/x86_64/libowned.so
        reader=/opt/sdk/ndk/30.0.16248370/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readobj
        for abi in arm64-v8a x86_64; do
          "${'$'}reader" --file-headers --program-headers --dyn-symbols "lib/${'$'}abi/libowned.so" > "${'$'}base/${'$'}abi-elf.txt"
          grep -q 'Java_io_github_asagnc_aurora_sdkprobe_Owned_nativeMarker' "${'$'}base/${'$'}abi-elf.txt"
          case "${'$'}abi" in
            arm64-v8a) grep -q 'Machine: EM_AARCH64 ' "${'$'}base/${'$'}abi-elf.txt" ;;
            x86_64) grep -q 'Machine: EM_X86_64 ' "${'$'}base/${'$'}abi-elf.txt" ;;
          esac
          awk '/Type:/ { load = (${ '$' }2 == "PT_LOAD") } /Alignment:/ && load { count++; if (${ '$' }2 < 16384 || ${ '$' }2 % 16384 != 0) bad=1 } END { exit (bad || count == 0) }' "${'$'}base/${'$'}abi-elf.txt"
        done
        printf '%s\n' aurora-apk-probe-ok
    """.trimIndent() + "\n"

    val revision: String get() = "sdk1-" + MessageDigest.getInstance("SHA-256")
        .digest((launcher + probe + rejection + AndroidBuildTools.metadataSha256.toSortedMap().toString()).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
