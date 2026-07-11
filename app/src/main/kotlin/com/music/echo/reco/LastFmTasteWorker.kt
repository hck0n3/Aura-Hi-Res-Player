package iad1tya.echo.music.reco

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import iad1tya.echo.music.constants.LastFMUsernameKey
import iad1tya.echo.music.constants.UseLastFmTasteKey
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Daily background worker that refreshes the cached Last.fm taste signal ([LastFmTasteSource]). It ONLY runs
 * the network fetch when the opt-in toggle is ON and a Last.fm username exists; otherwise it is a no-op. This
 * is the ONLY place Last.fm taste data is fetched — the recommendation path (buildProfile / orderedByTaste)
 * never hits the network, so battery/heat are unaffected. Mirrors ReleaseRadarWorker's scheduling shape.
 */
class LastFmTasteWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.dataStore.data.first()
            val enabled = prefs[UseLastFmTasteKey] ?: false
            val username = prefs[LastFMUsernameKey].orEmpty()
            if (enabled && username.isNotBlank()) {
                LastFmTasteSource.fetchAndCache(applicationContext, username)
            }
            Result.success()
        } catch (e: Exception) {
            // Best-effort taste refresh: a failure must never crash or retry-storm (the daily run retries anyway).
            Timber.tag(TAG).w(e, "Last.fm taste refresh failed")
            Result.success()
        }
    }

    companion object {
        private const val TAG = "LastFmTasteWorker"
        private const val WORK_NAME = "lastfm_taste_daily"
        private const val WORK_NAME_NOW = "lastfm_taste_now"

        /**
         * Schedules the daily run (network required). Safe to call on every app start:
         * [ExistingPeriodicWorkPolicy.UPDATE] keeps a single unique work item.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<LastFmTasteWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Enqueues a one-time run immediately (network required). Used when the user logs into Last.fm or flips
         * the taste toggle ON, so the cache warms without waiting for the daily run. Safe alongside the periodic
         * work — a distinct unique work name keeps the two from interfering.
         */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<LastFmTasteWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
