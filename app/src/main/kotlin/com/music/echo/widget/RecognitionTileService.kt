package iad1tya.echo.music.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import iad1tya.echo.music.recognition.RecognitionLaunchActivity

/**
 * Quick Settings tile that starts music recognition. It launches the transparent trampoline
 * (RecognitionLaunchActivity): with the mic permission already granted, recognition runs headless in
 * the microphone foreground service (live notification, no need to open the app); without it, the app
 * opens on the Recognition screen with auto-start so a single grant is enough. Starting the mic
 * service from a user-initiated activity launch is allowed on every Android version (14+ included).
 */
class RecognitionTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, RecognitionLaunchActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: startActivityAndCollapse requires a PendingIntent.
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching { startActivityAndCollapse(pi) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { startActivityAndCollapse(intent) }
        }
    }
}
