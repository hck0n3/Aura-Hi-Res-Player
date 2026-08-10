import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.Base64
import java.util.Collections
import java.security.KeyStore
import java.security.MessageDigest
import java.net.URL

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobufPlugin)
}

val hasGoogleServicesConfig = file("google-services.json").exists()

if (hasGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// ---------------------------------------------------------------------------------------------
// SUPERPOWERED COMMERCIAL LICENCE KEY
//
// Unlike the Last.fm / Tidal / Qobuz values above, this one is a real, paid, per-application
// credential. Superpowered's agreement lets them disable a key they see used by applications with
// no contract — "with or without notice" — and a disabled key does not fail loudly: the engine
// still initializes and still runs, it just quietly stops altering audio. A key pasted into a
// public repository is therefore a single point of failure for EVERY paying customer at once.
//
// So it is NEVER in source. It comes from local.properties (owner's machine) or the
// SUPERPOWERED_LICENSE_KEY environment variable (CI repository secret). A clone gets "" and, at
// runtime, an EQ that is cleanly reported as unavailable instead of a silent placebo.
// ---------------------------------------------------------------------------------------------
val superpoweredLicenseKey: String = run {
    val raw = localProperties.getProperty("SUPERPOWERED_LICENSE_KEY")?.takeIf { it.isNotBlank() }
        ?: System.getenv("SUPERPOWERED_LICENSE_KEY")?.takeIf { it.isNotBlank() }
        ?: ""
    val trimmed = raw.trim()
    // Every Superpowered key is Base64 text. Reject anything that could not survive being written
    // into a generated Java string literal (or reconstructed byte-for-byte at runtime) rather than
    // emitting a BuildConfig that does not compile, or one that compiles into the wrong key.
    if (trimmed.isNotEmpty() && trimmed.any { it.code !in 0x21..0x7E || it == '"' || it == '\\' }) {
        logger.warn(
            "SUPERPOWERED: the configured licence key contains characters outside printable ASCII " +
                "(or a quote/backslash) and was IGNORED. Expected the Base64 key Superpowered issued.",
        )
        ""
    } else {
        trimmed
    }
}

/**
 * SHA-256 (hex, lowercase) of the DER-encoded certificate that will sign RELEASE builds, or null
 * when it cannot be determined at build time.
 *
 * This is the ground truth for the runtime binding: the same bytes Android hands back from
 * `PackageInfo.signingInfo.apkContentsSigners[i].toByteArray()` (see `ApkSignatureVerifier`).
 * Read straight from the keystore that the release signingConfig points at, so it automatically
 * follows whatever key actually signs the build (including the workflow's fallback CI keystore).
 * `SUPERPOWERED_CERT_SHA256` is a manual override for setups where the keystore is not readable
 * during the build.
 */
val releaseSigningCertSha256: String? = run {
    val fromKeystore: String? = runCatching {
        val storeFile = file("keystore/release.keystore")
        if (!storeFile.exists()) return@runCatching null
        val storePassword = (System.getenv("STORE_PASSWORD")
            ?: localProperties.getProperty("STORE_PASSWORD"))?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val preferredAlias = (System.getenv("KEY_ALIAS")
            ?: localProperties.getProperty("KEY_ALIAS"))?.takeIf { it.isNotBlank() }
        // JDK 9+ writes PKCS12 by default but older release keystores are JKS; try both.
        var digest: String? = null
        for (type in listOf("PKCS12", "JKS")) {
            digest = runCatching {
                val ks = KeyStore.getInstance(type)
                storeFile.inputStream().use { ks.load(it, storePassword.toCharArray()) }
                val aliases = Collections.list(ks.aliases())
                val alias = preferredAlias?.takeIf { ks.containsAlias(it) && ks.getCertificate(it) != null }
                    ?: aliases.firstOrNull { ks.getCertificate(it) != null }
                    ?: return@runCatching null
                val der = ks.getCertificate(alias)?.encoded ?: return@runCatching null
                MessageDigest.getInstance("SHA-256").digest(der)
                    .joinToString("") { b -> "%02x".format(b) }
            }.getOrNull()
            if (digest != null) break
        }
        digest
    }.getOrNull()

    fromKeystore
        ?: (localProperties.getProperty("SUPERPOWERED_CERT_SHA256") ?: System.getenv("SUPERPOWERED_CERT_SHA256"))
            ?.replace(":", "")?.replace(" ", "")?.trim()?.lowercase()
            ?.takeIf { hex -> hex.length == 64 && hex.all { it in "0123456789abcdef" } }
}

/**
 * XOR the licence key with a keystream derived from the signing certificate's SHA-256.
 *
 * MUST stay byte-for-byte identical to `SuperpoweredLicense.keystream` in
 * `eq/audio/SuperpoweredLicense.kt` — that function is the only thing that can undo this.
 */
fun superpoweredBind(keyBytes: ByteArray, certDigest: ByteArray): ByteArray {
    val out = ByteArray(keyBytes.size)
    var offset = 0
    var counter = 0
    while (offset < keyBytes.size) {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("aura-sp-v1".toByteArray(Charsets.UTF_8))
        md.update(certDigest)
        md.update(
            byteArrayOf(
                (counter ushr 24).toByte(),
                (counter ushr 16).toByte(),
                (counter ushr 8).toByte(),
                counter.toByte(),
            ),
        )
        val block = md.digest()
        var i = 0
        while (i < block.size && offset < keyBytes.size) {
            out[offset] = (keyBytes[offset].toInt() xor block[i].toInt()).toByte()
            offset++
            i++
        }
        counter++
    }
    return out
}

// The transformed blob shipped in RELEASE builds, plus a 32-bit checksum of the ORIGINAL key so the
// runtime can prove it reconstructed the right thing before handing anything to the engine. 32 bits
// of SHA-256 is far too little to recover a key from, and it is never logged.
val superpoweredBoundBlob: String? = run {
    val certHex = releaseSigningCertSha256
    val key = superpoweredLicenseKey
    if (key.isEmpty() || certHex == null) return@run null
    val certDigest = ByteArray(32) { i -> certHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    Base64.getEncoder().encodeToString(superpoweredBind(key.toByteArray(Charsets.UTF_8), certDigest))
}

val superpoweredKeyChecksum: String =
    if (superpoweredLicenseKey.isEmpty()) {
        ""
    } else {
        MessageDigest.getInstance("SHA-256")
            .digest(superpoweredLicenseKey.toByteArray(Charsets.UTF_8))
            .take(4).joinToString("") { b -> "%02x".format(b) }
    }

// GOLDEN VECTOR. The exact same constants are asserted from the other side by
// `app/src/test/.../SuperpoweredLicenseTest.kt`. `superpoweredBind` above and
// `SuperpoweredLicense.keystream` are two hand-written implementations of one algorithm; if they ever
// drift, every release ships a key that reconstructs into garbage and the ENTIRE user base loses the
// EQ at once, with the app itself unable to tell that anything is wrong. So each side is pinned to
// this vector: break either one and you get a red build, not a silent dead engine.
// Deliberately fails the build (check) — a warning would be read too late.
run {
    val goldenCertHex = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
    val goldenKey = "AuraGoldenVectorKey-0123456789+/=abcdefgh"
    val goldenBlob = "jtt6qa0FokrPxZizx+797Uuw9+VKqbM6zs3zxiEXCxIE7azZl951bZ8="
    val goldenDigest = ByteArray(32) { i -> goldenCertHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    val produced = Base64.getEncoder()
        .encodeToString(superpoweredBind(goldenKey.toByteArray(Charsets.UTF_8), goldenDigest))
    check(produced == goldenBlob) {
        "SUPERPOWERED: superpoweredBind no longer matches the golden vector shared with " +
            "SuperpoweredLicenseTest. The app would not be able to reconstruct the licence key it ships. " +
            "Change both sides together or not at all."
    }
}

when {
    superpoweredLicenseKey.isEmpty() -> logger.warn(
        "SUPERPOWERED: no licence key configured (SUPERPOWERED_LICENSE_KEY in local.properties or " +
            "as an environment variable / CI secret). Release builds will ship WITHOUT the DSP engine: " +
            "audio plays untouched and the EQ screen says so. This is expected for forks and clones.",
    )
    superpoweredBoundBlob == null -> logger.warn(
        "SUPERPOWERED: licence key configured but the release signing certificate could not be read " +
            "(app/keystore/release.keystore + STORE_PASSWORD, or SUPERPOWERED_CERT_SHA256). The key will " +
            "be embedded UNBOUND — it still works, but a repackaged clone could reuse it.",
    )
}

android {
    namespace = "iad1tya.echo.music"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1+"
        }
    }


    // Private test build with the subscription gate OFF. Build with `-Pnosub=true` (debug recommended,
    // e.g. `assembleUniversalFossDebug -Pnosub=true`). Gets a distinct applicationId so it installs
    // side-by-side, never checks for updates and is NEVER published as a release — so the public
    // (paid) app's updater never sees it.
    val noSub = project.hasProperty("nosub") && project.property("nosub") == "true"

    defaultConfig {
        applicationId = if (noSub) "iad1tya.echo.music.dev" else "iad1tya.echo.music"
        if (noSub) versionNameSuffix = "-nosub"
        buildConfigField("Boolean", "REQUIRE_SUBSCRIPTION", (!noSub).toString())
        minSdk = 26
        targetSdk = 36
        // Public version reset to a fresh stable 0.0.1 for the Aura Hi-Res Player relaunch.
        // versionCode stays monotonic (never below the last shipped 673) so the in-app updater and
        // sideload-install-over-existing keep working; only the user-facing versionName resets.
        versionCode = 891
        versionName = "0.6.171"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // LastFM API keys from GitHub Secrets
//        val lastFmKey = localProperties.getProperty("LASTFM_API_KEY") ?: System.getenv("LASTFM_API_KEY") ?: ""
//        val lastFmSecret = localProperties.getProperty("LASTFM_SECRET") ?: System.getenv("LASTFM_SECRET") ?: ""
        
        // Prefer local.properties / CI secret (keeps keys out of source); fall back to the embedded
        // values so existing release builds keep working. NOTE: a client API key can never be fully
        // hidden from the APK — LastFM keys are designed to be embedded; this is low-severity.
        // takeIf { isNotBlank() } is CRITICAL: the CI workflows set LASTFM_API_KEY=${{ secrets.LASTFM_API_KEY }}.
        // When that GitHub secret is unset/empty, the env var is present but EMPTY (""), and getenv returns ""
        // (not null) — so a plain `?:` would NOT fall through and the released APK shipped an EMPTY Last.fm key
        // → auth.getMobileSession returned error 6 "invalid parameters" → Last.fm login broken for everyone.
        // Treating blank as absent falls through to the embedded working key (Last.fm keys are meant to embed).
        val lastFmKey = localProperties.getProperty("LASTFM_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("LASTFM_API_KEY")?.takeIf { it.isNotBlank() }
            ?: "694cbaa17c78202a133eac4656dff651"
        val lastFmSecret = localProperties.getProperty("LASTFM_SECRET")?.takeIf { it.isNotBlank() }
            ?: System.getenv("LASTFM_SECRET")?.takeIf { it.isNotBlank() }
            ?: "a0fdaf6060f19128c4a84f297c71e627"

        buildConfigField("String", "LASTFM_API_KEY", "\"$lastFmKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastFmSecret\"")

        // Tidal OAuth client id, embedded so the owner never re-pastes it (same pattern as the LastFM
        // keys above). A client id is NOT a secret — Tidal's Open API is PKCE-only (no client secret),
        // so the id is public by design; this is low-severity and consistent with the house rule.
        // Overridable via local.properties / a TIDAL_CLIENT_ID CI secret; blank means "no default, the
        // user pastes their own in the app". takeIf { isNotBlank() } so an empty CI env var falls through.
        // NOTE: this id is tied to the OWNER's developer.tidal.com account and its rate limits — fine for
        // the private beta; revisit (a production Tidal app) before wiring Tidal into a PUBLIC release.
        val tidalClientId = localProperties.getProperty("TIDAL_CLIENT_ID")?.takeIf { it.isNotBlank() }
            ?: System.getenv("TIDAL_CLIENT_ID")?.takeIf { it.isNotBlank() }
            ?: "nNgez049R98ji742"
        buildConfigField("String", "TIDAL_CLIENT_ID", "\"$tidalClientId\"")

        // Qobuz web-player keys, used ONLY to sign the owner's OWN Qobuz subscription requests (the
        // hi-res FLAC path in utils/qobuz + qobuz/*). These are the PUBLIC play.qobuz.com web-player
        // app_id / app_secret ("spoofbuz"), the same values the browser player ships — not a private
        // secret. Blank by default: the owner fills them in local.properties / a CI secret, OR leaves
        // them blank and the app scrapes them from play.qobuz.com bundle.js at login time (see
        // qobuz/QobuzBundleScraper.kt). takeIf { isNotBlank() } so an empty CI env var falls through.
        // A Qobuz app_secret alone streams nothing — every getFileUrl also needs the user's own
        // user_auth_token (their paid login), so shipping these is low-severity and consistent with the
        // LastFM/Tidal house rule.
        val qobuzAppId = localProperties.getProperty("QOBUZ_APP_ID")?.takeIf { it.isNotBlank() }
            ?: System.getenv("QOBUZ_APP_ID")?.takeIf { it.isNotBlank() }
            ?: ""
        val qobuzAppSecret = localProperties.getProperty("QOBUZ_APP_SECRET")?.takeIf { it.isNotBlank() }
            ?: System.getenv("QOBUZ_APP_SECRET")?.takeIf { it.isNotBlank() }
            ?: ""
        buildConfigField("String", "QOBUZ_APP_ID", "\"$qobuzAppId\"")
        buildConfigField("String", "QOBUZ_APP_SECRET", "\"$qobuzAppSecret\"")

        // Superpowered licence, DEFAULT (unbound) form — see the block above the android {} block.
        // This is what DEBUG builds get, and it is deliberate: a debug APK is signed with the Android
        // debug certificate, so the signature binding could never reconstruct anything there. Attempting
        // it would leave the owner without an EQ on every development build. `release` overrides these
        // three fields below with the certificate-bound blob.
        buildConfigField("String", "SUPERPOWERED_LICENSE", "\"$superpoweredLicenseKey\"")
        buildConfigField("boolean", "SUPERPOWERED_LICENSE_BOUND", "false")
        buildConfigField("String", "SUPERPOWERED_LICENSE_CHECK", "\"$superpoweredKeyChecksum\"")

//add nightly build label support
        val isNightly = project.hasProperty("nightly") && project.property("nightly") == "true"
        buildConfigField("Boolean", "IS_NIGHTLY", isNightly.toString())
        
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fexceptions", "-frtti")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }
    

    flavorDimensions += listOf("abi", "variant")
    productFlavors {
        // FOSS variant (default) - F-Droid compatible, no Google Play Services
        create("foss") {
            dimension = "variant"
            isDefault = true
            buildConfigField("Boolean", "CAST_AVAILABLE", "false")
        }

        // GMS variant - with Google Cast support (requires Google Play Services)
        create("gms") {
            dimension = "variant"
            buildConfigField("Boolean", "CAST_AVAILABLE", "true")
        }
        
        create("universal") {
            dimension = "abi"
            // No abiFilters -> ships every architecture (large, ~200 MB).
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            // arm64-only -> ~1/3 the size; covers virtually all modern phones.
            ndk { abiFilters += "arm64-v8a" }
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
        }
        create("armeabi") {
            dimension = "abi"
            ndk { abiFilters += "armeabi-v7a" }
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
        }
        create("x86") {
            dimension = "abi"
            ndk { abiFilters += "x86" }
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += "x86_64" }
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
        }
    }

    signingConfigs {
        create("persistentDebug") {
            storeFile = file("persistent-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = (System.getenv("STORE_PASSWORD")
                ?: localProperties.getProperty("STORE_PASSWORD"))?.takeIf { it.isNotBlank() }
            keyAlias = (System.getenv("KEY_ALIAS")
                ?: localProperties.getProperty("KEY_ALIAS"))?.takeIf { it.isNotBlank() }
            keyPassword = (System.getenv("KEY_PASSWORD")
                ?: localProperties.getProperty("KEY_PASSWORD"))?.takeIf { it.isNotBlank() }
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ARCHITECTURE", "\"release\"")

            // Superpowered licence, CERTIFICATE-BOUND form. What ships is the key XORed with a keystream
            // derived from the SHA-256 of the certificate that signs this very build; the app undoes it at
            // runtime with the certificate hash Android reports for its own package. A repackaged clone is
            // necessarily signed with a different certificate, so it reconstructs garbage — which the
            // checksum catches before the engine is ever touched, leaving the clone with no DSP.
            // When the certificate is unknown at build time, the fields inherited from defaultConfig stand
            // (unbound key) — never a broken one. A warning is logged at configuration time.
            superpoweredBoundBlob?.let { blob ->
                buildConfigField("String", "SUPERPOWERED_LICENSE", "\"$blob\"")
                buildConfigField("boolean", "SUPERPOWERED_LICENSE_BOUND", "true")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "ARCHITECTURE", "\"debug\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        checkReleaseBuilds = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn"
        )
        suppressWarnings.set(false)
    }
}

dependencies {
    testImplementation(libs.junit)

    // Firebase - GMS flavor only (excluded from F-Droid / FOSS builds)
    "gmsImplementation"(platform("com.google.firebase:firebase-bom:33.1.0"))
    "gmsImplementation"("com.google.firebase:firebase-analytics")
    "gmsImplementation"("com.google.firebase:firebase-crashlytics")

    // Google Drive Sync - GMS flavor only
    "gmsImplementation"(libs.play.services.auth)
    "gmsImplementation"(libs.google.api.client.android)
    "gmsImplementation"(libs.google.api.services.drive) {
        exclude(group = "org.apache.httpcomponents")
    }

    
    implementation(libs.haze)
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.appcompat)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation(libs.ucrop)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.okhttp)

    // Google Cast - only included in GMS flavor (not available in F-Droid/FOSS builds)
    "gmsImplementation"(libs.media3.cast)
    "gmsImplementation"(libs.mediarouter)
    "gmsImplementation"(libs.cast.framework)

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    implementation(libs.tinypinyin)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    implementation(libs.jsoup)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))
    implementation(project(":migration"))
    // Tidal OAuth tokens at rest (bridge TidalTokenStore). MUST stay on the exact same version the
    // :migration module uses, or two Tink stacks end up on the classpath.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(project(":kugou"))
    implementation(project(":lrclib"))
    implementation(project(":betterlyrics"))
    implementation(project(":simpmusic"))
    implementation(project(":youlyplus"))
    implementation(project(":canvas"))
    implementation(project(":shazamkit"))
    implementation(project(":artistvideo"))
    implementation(project(":applecanvas"))
    implementation(project(":echomusiccanvas"))
    implementation(project(":paxsenixlyrics"))
    implementation(project(":unison"))
    implementation(project(":jiosaavn"))


    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // Protobuf for message serialization (lite version for Android)
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)

    coreLibraryDesugaring(libs.desugaring)
    implementation(libs.timber)
    // Reliable cold app-restart (used after Google login) — replaces the flaky AlarmManager relaunch.
    implementation("com.jakewharton:process-phoenix:3.0.0")
    // In-app browser (Chrome Custom Tabs) for the in-app Gumroad checkout (no leaving the app).
    implementation("androidx.browser:browser:1.8.0")
    implementation(libs.smoothCorner)
    implementation(libs.lottie.compose)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.ffmpeg.kit.full)
}
