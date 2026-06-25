package iad1tya.echo.music.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import iad1tya.echo.music.MainActivity

/**
 * Quick Settings tile that starts music recognition. It opens the app straight to the Recognition screen,
 * which auto-starts recording + Shazam matching. We launch the activity (not a background microphone
 * foreground service) because Android 14+ heavily restricts starting a mic service from the background —
 * opening the screen is reliable on every Android version.
 */
class RecognitionTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_RECOGNITION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
