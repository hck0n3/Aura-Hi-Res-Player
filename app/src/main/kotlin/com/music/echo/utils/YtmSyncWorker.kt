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
        fun libraryUploadSync(): LibraryUploadSync
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ctx = applicationContext
            val cookie = ctx.dataStore.get(InnerTubeCookieKey, "")
            if (cookie.isBlank() || "SAPISID" !in parseCookieString(cookie)) {
                // Not signed in → nothing to sync (don't retry forever).
                return@withContext Result.success()
            }
            val entryPoint = EntryPointAccessors
                .fromApplication(ctx, YtmSyncEntryPoint::class.java)
            val sync = entryPoint.syncUtils()

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
                TYPE_UPLOAD_LIBRARY -> runUploadPass(ctx, entryPoint.libraryUploadSync())
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

    /**
     * One bounded upload pass, plus — only if the pass ran out of its request budget with work still
     * pending — ONE delayed continuation. This is what makes a whole-library backfill finish without
     * ever becoming a burst: each pass is capped at [LibraryUploadSync.MAX_REQUESTS_PER_RUN] writes,
     * and passes are spaced [UPLOAD_CONTINUATION_DELAY_MIN] minutes apart. The chain stops when the
     * library is fully synced, when nothing moved, or after [MAX_UPLOAD_CONTINUATIONS] hops — it can
     * never turn into an unbounded background loop, and nothing here runs at app start.
     */
    private suspend fun runUploadPass(context: Context, uploader: LibraryUploadSync) {
        uploader.restorePersistedProgress()
        val done = uploader.runUploadPass()
        if (done) return

        val hop = inputData.getInt(KEY_UPLOAD_HOP, 0)
        val state = uploader.progress.value
        // Only continue when there really is more to do AND the pass was cut short by its own budget.
        // A pass that stopped because the user is offline/signed out/opted out just waits for the next
        // explicit request instead of burning wakeups.
        if (!state.moreWorkPending || state.stoppedReason != null) return
        if (hop >= MAX_UPLOAD_CONTINUATIONS) {
            Timber.tag(TAG).i("Upload continuation limit reached; the rest waits for the next sync")
            return
        }
        enqueueUploadContinuation(context, hop + 1)
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

        /** Push the local library UP to the YouTube account (playlists, follows, likes, albums). */
        const val TYPE_UPLOAD_LIBRARY = "upload_library"

        private const val KEY_UPLOAD_HOP = "upload_hop"

        /** Upper bound on chained upload passes. 20 hops x 400 writes covers a very large library. */
        private const val MAX_UPLOAD_CONTINUATIONS = 20

        /** Spacing between chained passes — keeps the whole backfill a trickle, not a burst. */
        private const val UPLOAD_CONTINUATION_DELAY_MIN = 15L

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

        /**
         * Continue a library upload that ran out of budget. REPLACE (not KEEP) because the previous
         * hop has finished by the time this is called, and the unique name keeps the chain single —
         * two overlapping upload chains could double the request cost.
         * Battery-friendly by construction: network-constrained, not expedited, and delayed.
         */
        private fun enqueueUploadContinuation(context: Context, hop: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<YtmSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(UPLOAD_CONTINUATION_DELAY_MIN, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_TYPE to TYPE_UPLOAD_LIBRARY, KEY_UPLOAD_HOP to hop))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "ytm_sync_$TYPE_UPLOAD_LIBRARY",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }
}
