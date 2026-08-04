plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.music.innertube"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    implementation(libs.newpipeextractor)
    // Diagnostics only. Timber's planted trees are process-global, so a failure logged here reaches the
    // app's AppLogger file tree and therefore the log the USER can send — which is the whole point: the
    // cipher/signature deobfuscation that breaks when YouTube rotates player.js lives in THIS module,
    // and it used to fail with a comment that said "caller handles errors" and no evidence anywhere.
    // Pure logging facade, no Firebase, no transitive Google dependency — safe for the foss flavor.
    implementation(libs.timber)
    testImplementation(libs.junit)

    coreLibraryDesugaring(libs.desugaring)
}
