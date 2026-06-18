package iad1tya.echo.music.spotifyimport

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import iad1tya.echo.music.constants.SpotifyAutoSyncSourceIdsKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Scheduled Spotify sync: re-imports the user-chosen Spotify sources (playlists / liked / albums)
 * on a fixed cadence so they stay up to date without manual action. The set of sources and the
 * cadence are configured from the Spotify import screen.
 *
 * Reuses the singleton [SpotifyImportRepository] (session + source listing) and
 * [SpotifyImportManager] (the actual import, which posts its own progress/finish notification).
 */
class SpotifyAutoSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SpotifyAutoSyncEntryPoint {
        fun repository(): SpotifyImportRepository
        fun importManager(): SpotifyImportManager
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ctx = applicationContext
            val csv = ctx.dataStore.get(SpotifyAutoSyncSourceIdsKey, "")
            val ids = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (ids.isEmpty()) return@withContext Result.success()

            val ep = EntryPointAccessors.fromApplication(ctx, SpotifyAutoSyncEntryPoint::class.java)
            val repository = ep.repository()
            val importManager = ep.importManager()

            if (importManager.isRunning) return@withContext Result.success()

            // Spotify session lives in saved cookies; if it expired the user must re-connect in-app.
            val session = runCatching { repository.restoreSession() }.getOrNull()
            if (session?.isAuthenticated != true) {
                Timber.tag(TAG).w("Spotify session not authenticated; skipping scheduled sync")
                return@withContext Result.success()
            }

            val sources = runCatching { repository.loadSources() }.getOrDefault(emptyList())
            val toSync = sources.filter { it.id in ids }
            if (toSync.isNotEmpty()) {
                importManager.start(toSync)
            }
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Scheduled Spotify sync failed")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SpotifyAutoSyncWorker"
        const val WORK_NAME = "spotify_auto_sync"

        /**
         * (Re)schedule the periodic sync. [freqDays] <= 0 cancels it. UPDATE so changing the cadence
         * replaces the existing schedule.
         */
        fun schedule(context: Context, freqDays: Int) {
            val wm = WorkManager.getInstance(context)
            if (freqDays <= 0) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SpotifyAutoSyncWorker>(freqDays.toLong(), TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
