package iad1tya.echo.music.utils

import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import android.os.Handler
import android.os.Looper
import timber.log.Timber

/**
 * Deterministic main-thread guard for the media3 `ForegroundServiceStartNotAllowedException` crash.
 *
 * media3's `DefaultMediaNotificationProvider` loads the notification cover art asynchronously; when the
 * bitmap lands and the app is backgrounded with no foreground-service exemption (Android 12+, worst on
 * Android 16 / MIUI), it calls `MediaNotificationManager.startForeground()` on the MAIN looper, which throws
 * `ForegroundServiceStartNotAllowedException`. That async path is NOT routed to the
 * `MediaSessionService.Listener`, so the exception crashes the process uncaught.
 *
 * This installs the well-known "safe Looper" wrapper: it re-runs `Looper.loop()` inside a try/catch that
 * SWALLOWS ONLY that specific FGS exception family — and only when the stack actually came through media3's
 * notification / `startForeground` path — and RE-THROWS everything else, so real bugs still crash normally
 * and reach [CrashHandler].
 */
object MainThreadCrashGuard {

    fun install() {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (t: Throwable) {
                    if (!t.isSwallowableForegroundServiceCrash()) {
                        // Not our target — restore normal crash behavior (reaches the uncaught handler).
                        throw t
                    }
                    reportException(t)
                    Timber.w(t, "Swallowed media3 ForegroundService start crash on main looper")
                    // Re-enter loop() to keep the main thread alive.
                }
            }
        }
    }

    /**
     * True only for the foreground-service-start exception family AND only when the throw unwound through
     * a framework path that legitimately starts a foreground service on our behalf — so an FGS exception
     * raised by unrelated app code is never masked. Matched by exact type / framework class name, not by
     * message text.
     *
     * Two such paths exist:
     *  - media3's notification / startForeground path (the original case), and
     *  - GMS Cast, which starts its own ReconnectionService/CastRemoteDisplay foreground service when a
     *    session is established or resumed. Its stack contains NO media3 frames, so it used to be
     *    re-thrown here, and the uncaught handler then swallowed it WITHOUT re-entering the looper —
     *    killing the main thread and closing the app silently, with no CrashActivity and no report.
     */
    fun Throwable.isSwallowableForegroundServiceCrash(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val isFgsType = this is ForegroundServiceStartNotAllowedException ||
            javaClass.name == "android.app.ForegroundServiceDidNotStartInTimeException"
        if (!isFgsType) return false
        return stackTrace.any { frame ->
            val cn = frame.className
            cn.startsWith("androidx.media3.session.MediaNotificationManager") ||
                cn.startsWith("androidx.media3.session.MediaSessionService") ||
                (cn.startsWith("androidx.media3.session.") &&
                    frame.methodName.contains("startForeground", ignoreCase = true)) ||
                // Both the public Cast surface and the minified internal one: R8 rewrites the GMS Cast
                // internals to com.google.android.gms.internal.cast.zz*, so matching only the public
                // package would leave the whitelist unfired on exactly the release builds users run.
                cn.startsWith("com.google.android.gms.cast.") ||
                cn.startsWith("com.google.android.gms.internal.cast.")
        }
    }
}
