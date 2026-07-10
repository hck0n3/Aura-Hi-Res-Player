package iad1tya.echo.music.recognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import iad1tya.echo.music.MainActivity

/**
 * Transparent trampoline for the Quick Settings recognition tile (ported from upstream v5.2.5).
 * With the mic permission already granted it goes straight to the microphone foreground service
 * (recognition runs headless with a live notification); otherwise it opens the app on the
 * Recognition screen with auto-start so the user only has to grant the permission once.
 */
class RecognitionLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRecognitionLaunch()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleRecognitionLaunch()
    }

    private fun handleRecognitionLaunch() {
        if (hasRecordPermission()) {
            startRecognitionService()
        } else {
            openRecognitionPermissionFlow()
        }
        finish()
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openRecognitionPermissionFlow() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RECOGNITION
                putExtra(MainActivity.EXTRA_AUTO_START_RECOGNITION, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        startActivity(intent)
    }

    private fun startRecognitionService() {
        if (!hasRecordPermission()) return

        val serviceIntent = Intent(this, RecognitionForegroundService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
