package iad1tya.echo.music.widget

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that starts music recognition (Shazam) without opening the app. It simply fires the
 * SAME broadcast the recognizer widget's mic button uses, so it reuses the existing permission handling +
 * foreground-service start path in [MusicRecognizerWidgetReceiver].
 */
class RecognitionTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MusicRecognizerWidgetReceiver::class.java).apply {
            action = MusicRecognizerWidgetReceiver.ACTION_START_RECOGNITION
        }
        // unlockAndRun so it still works from the lock screen; falls back to a direct send otherwise.
        runCatching { unlockAndRun { sendBroadcast(intent) } }
            .onFailure { sendBroadcast(intent) }
    }
}
