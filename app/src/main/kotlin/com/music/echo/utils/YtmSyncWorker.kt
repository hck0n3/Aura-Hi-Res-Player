package iad1tya.echo.music.utils

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
 * Runs a YouTube Music sync in the background via WorkManager so it **survives the app being closed**
 * and **retries until it completes** (network-constrained, with backoff). The syncs are additive
 * (insert/upsert), so a retry after an interruption simply continues filling the library — nothing is
 * lost or duplicated destructively. This is what makes "Sincronizar todo" reliable for big libraries.
 */
class YtmSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface YtmSyncEntryPoint {
        fun syncUtils(): SyncUtils
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ctx = applicationContext
            val cookie = ctx.dataStore.get(InnerTubeCookieKey, "")
            if (cookie.isBlank() || "SAPISID" !in parseCookieString(cookie)) {
                // Not signed in → nothing to sync (don't retry forever).
                return@withContext Result.success()
            }
            val sync = EntryPointAccessors
                .fromApplication(ctx, YtmSyncEntryPoint::class.java)
                .syncUtils()

            when (inputData.getString(KEY_TYPE)) {
                TYPE_LIKED_SONGS -> sync.syncLikedSongsSuspend()
                TYPE_LIKED_ALBUMS -> sync.syncLikedAlbumsSuspend()
                TYPE_ARTISTS -> sync.syncArtistsSubscriptionsSuspend()
                TYPE_PLAYLISTS -> sync.syncSavedPlaylistsSuspend()
                TYPE_LIBRARY -> sync.syncLibrarySongsSuspend()
                TYPE_UPLOADS -> {
                    sync.syncUploadedSongsSuspend()
                    sync.syncUploadedAlbumsSuspend()
                }
                else -> sync.performFullSyncSuspend()
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Interrupted / network error / app closed → reschedule and continue (additive, safe).
            Timber.tag(TAG).e(e, "YTM sync failed; will retry")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "YtmSyncWorker"
        const val KEY_TYPE = "type"
        const val TYPE_ALL = "all"
        const val TYPE_LIKED_SONGS = "liked_songs"
        const val TYPE_LIKED_ALBUMS = "liked_albums"
        const val TYPE_ARTISTS = "artists"
        const val TYPE_PLAYLISTS = "playlists"
        const val TYPE_LIBRARY = "library"
        const val TYPE_UPLOADS = "uploads"

        /** Enqueue a background, restart-surviving, auto-retrying sync of [type]. */
        fun enqueue(context: Context, type: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<YtmSyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_TYPE to type))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            // KEEP: if a sync of this type is already queued/running, don't pile up duplicates — the
            // running one already covers it (and WorkManager will keep retrying it to completion).
            WorkManager.getInstance(context)
                .enqueueUniqueWork("ytm_sync_$type", ExistingWorkPolicy.KEEP, request)
        }
    }
}
