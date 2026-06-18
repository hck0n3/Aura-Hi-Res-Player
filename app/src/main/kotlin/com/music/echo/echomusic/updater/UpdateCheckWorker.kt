package iad1tya.echo.music.echomusic.updater

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import iad1tya.echo.music.MainActivity
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.LastUpdateNotifiedTagKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Weekly background worker that checks GitHub for a newer release and posts a notification when one
 * is found. Reuses [checkForUpdate] so the "is there an update" decision stays in one place (and the
 * no-subscription build's gate is respected — it reports no update). Notifies once per version via
 * [LastUpdateNotifiedTagKey] so the same pending update isn't re-announced every week.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val deferred = CompletableDeferred<Pair<Boolean, String>>()
            checkForUpdate(
                applicationContext,
                onSuccess = { tag, isAvailable, _, _, _, _, _, _ ->
                    deferred.complete(isAvailable to tag)
                },
                onError = { deferred.complete(false to "") },
            )
            val (available, tag) = deferred.await()

            if (available && tag.isNotBlank()) {
                val last = applicationContext.dataStore.get(LastUpdateNotifiedTagKey, "")
                if (tag != last) {
                    postNotification(tag)
                    applicationContext.dataStore.edit { it[LastUpdateNotifiedTagKey] = tag }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Update check failed")
            Result.retry()
        }
    }

    private fun postNotification(tag: String) {
        val context = applicationContext
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones de la app",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, launchIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_nobg)
            .setContentTitle("Actualización disponible")
            .setContentText("Aura Hi-Res Player $tag ya está disponible. Toca para actualizar.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "update_check_weekly"
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 2003

        /**
         * Schedule the weekly update check. Safe to call on every app start: KEEP preserves the
         * existing weekly cadence instead of resetting the timer each launch. First check runs a few
         * hours after install/update so it doesn't fire during first-run onboarding.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
