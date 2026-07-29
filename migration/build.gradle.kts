plugins {
    // AGP 9.0 has built-in Kotlin support — declaring kotlin("android") here is an ERROR now.
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.aura.migration"
    // Aligned with the rest of the project (innertube/app): same compileSdk/minSdk/JDK so this module
    // never drags a second toolchain. The standalone drop shipped compileSdk 35 / minSdk 21.
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // MUST match the app's Room exactly (single Room version on the classpath; the app is the source
    // of truth via the version catalog).
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
