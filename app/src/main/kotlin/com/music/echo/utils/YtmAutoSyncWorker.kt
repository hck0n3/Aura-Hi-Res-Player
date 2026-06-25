package iad1tya.echo.music.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.innertube.utils.parseCookieString
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import iad1tya.echo.music.constants.InnerTubeCookieKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Scheduled YouTube Music sync — the YT-Music counterpart to [iad1tya.echo.music.spotifyimport.SpotifyAutoSyncWorker].
 * On a fixed cadence it re-runs a full sync (liked songs/albums, artists, subscriptions, playlists, library,
 * uploads) so the library stays current without manual action. The cadence is set from the YTM sync screen.
 *
 * On a real, completed sync it stamps [YtmLastSyncKey] so the screen can show "last synced X ago" — making a
 * silently-skipped run (e.g. signed out, or no network) VISIBLE instead of a placebo that claims success.
 */
class YtmAutoSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface YtmAutoSyncEntryPoint {
        fun syncUtils(): SyncUtils
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ctx = applicationContext
            val cookie = ctx.dataStore.get(InnerTubeCookieKey, "")
            if (cookie.isBlank() || "SAPISID" !in parseCookieString(cookie)) {
                // Signed out → nothing to sync. Don't stamp last-sync, so the UI keeps showing the real last
                // time something synced (and can warn that the session is gone) instead of a fake "just now".
                Timber.tag(TAG).w("Scheduled YTM sync skipped: not signed in")
                return@withContext Result.success()
            }
            val sync = EntryPointAccessors
                .fromApplication(ctx, YtmAutoSyncEntryPoint::class.java)
                .syncUtils()
            // performFullSyncSuspend stamps the real completion time itself (only when it actually runs to
            // completion while logged in), so we don't fake a "just now" here on a no-op.
            sync.performFullSyncSuspend()
            Timber.tag(TAG).i("Scheduled YTM sync finished")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Scheduled YTM sync failed; will retry")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "YtmAutoSyncWorker"
        const val WORK_NAME = "ytm_auto_sync"

        /**
         * (Re)schedule the periodic sync. [freqDays] <= 0 cancels it. UPDATE so changing the cadence (or
         * re-asserting at app start) replaces the existing schedule without piling up duplicates.
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
            val request = PeriodicWorkRequestBuilder<YtmAutoSyncWorker>(freqDays.toLong(), TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
