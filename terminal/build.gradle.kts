plugins { id("com.android.library") }
android {
    namespace = "io.github.asagnc.aurora.terminal"
    compileSdk = 37
    defaultConfig { minSdk = 36 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}
