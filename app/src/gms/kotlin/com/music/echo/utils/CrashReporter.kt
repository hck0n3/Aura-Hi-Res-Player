package iad1tya.echo.music.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * GMS build: routes crash telemetry to Firebase Crashlytics.
 *
 * This file lives ONLY in the `gms` flavor source set, so the FOSS flavor never sees the
 * FirebaseCrashlytics symbol (its parallel no-op copy is in src/foss/.../CrashReporter.kt).
 * Firebase Crashlytics is added unconditionally via `gmsImplementation` in app/build.gradle.kts,
 * so the symbol is always on the gms classpath.
 *
 * Every call is wrapped defensively: if Firebase was never initialized (e.g. a gms build without a
 * google-services.json), FirebaseCrashlytics.getInstance() can throw — we must never let crash
 * reporting itself crash the app, so all failures are swallowed.
 *
 * IMPORTANT: keep the public API of this object identical to the FOSS copy — the shared `main`
 * source set is compiled against whichever flavor is active.
 */
object CrashReporter {
    private val crashlytics: FirebaseCrashlytics?
        get() = runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()

    /** Records [throwable] to Crashlytics as a non-fatal event. */
    fun record(throwable: Throwable) {
        runCatching { crashlytics?.recordException(throwable) }
    }

    /** Adds a breadcrumb [message] to the next Crashlytics report. */
    fun log(message: String) {
        runCatching { crashlytics?.log(message) }
    }

    /** Attaches a custom key/value pair to subsequent Crashlytics reports. */
    fun setKey(key: String, value: String) {
        runCatching { crashlytics?.setCustomKey(key, value) }
    }
}
