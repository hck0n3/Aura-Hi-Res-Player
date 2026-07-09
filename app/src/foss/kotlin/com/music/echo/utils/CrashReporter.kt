package iad1tya.echo.music.utils

/**
 * FOSS (F-Droid) build: no-op crash reporter.
 *
 * The FOSS flavor ships WITHOUT any Firebase / Google dependency, so there is no telemetry backend
 * to record to here. Every method is intentionally empty. The shared code in [reportException] and
 * [CrashHandler] calls into this object, and the GMS flavor provides the real Firebase Crashlytics
 * implementation via its parallel copy of this file (src/gms/.../CrashReporter.kt).
 *
 * IMPORTANT: keep the public API of this object identical to the GMS copy — the shared `main`
 * source set is compiled against whichever flavor is active.
 */
object CrashReporter {
    /** No-op: no crash backend in the FOSS build. */
    fun record(throwable: Throwable) {
        // Intentionally empty — local logging (printStackTrace/Timber/AppLogger) is done by callers.
    }

    /** No-op: no breadcrumb log backend in the FOSS build. */
    fun log(message: String) {
        // Intentionally empty.
    }

    /** No-op: no custom-key backend in the FOSS build. */
    fun setKey(key: String, value: String) {
        // Intentionally empty.
    }
}
