package iad1tya.echo.music.reco

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import iad1tya.echo.music.api.AiPlaylistService
import iad1tya.echo.music.constants.AiProviderKey
import iad1tya.echo.music.constants.AiRecommendedPlaylistKey
import iad1tya.echo.music.constants.OpenRouterApiKey
import iad1tya.echo.music.constants.OpenRouterBaseUrlKey
import iad1tya.echo.music.constants.OpenRouterModelKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playlistimport.AiPlaylistGenerator
import iad1tya.echo.music.playlistimport.SongResolver
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Daily background worker that rebuilds the ONE persistent "Recomendado para ti (IA)" playlist from the
 * user's listening history (most-played + liked songs, fully on-device input). It ONLY runs when the
 * opt-in toggle ([AiRecommendedPlaylistKey], default OFF) is ON; otherwise it is a no-op. Mirrors
 * [LastFmTasteWorker]'s scheduling shape (daily, network required, schedule-always / gate-in-doWork).
 *
 * The AI ask goes through the exact same keyless chain as "Lista AI" ([AiPlaylistService]: user key →
 * Aura Worker /ai → Pollinations), bounded by [AiPlaylistGenerator.AI_BUDGET_MS] so a stuck cascade can
 * never hold the modem for minutes (battery/heat rule). If the whole chain fails — or nothing resolves —
 * the LAST GOOD playlist is kept untouched, silently: this feature never surfaces an error and never
 * deletes the user's previous recommendations.
 *
 * Persistence invariants (learned the hard way — see the regression registry):
 *  - The playlist has a FIXED id ([PLAYLIST_ID]) and is always found BY ID, never by name (a rename
 *    must not fork a duplicate playlist).
 *  - Every refresh updates `lastUpdateTime` (upstream Echo-Music forgot this — its "Last updated" label
 *    froze at creation time forever).
 *  - `bookmarkedAt` is set on creation: every Library query filters `WHERE bookmarkedAt IS NOT NULL`,
 *    so without it the playlist would be invisible.
 *  - The database comes from the Hilt singleton via an EntryPoint — NEVER `newInstance` (that dead
 *    second-instance path has no migrations and caused the 0.6.117 universal crash).
 */
class AutoRecoPlaylistWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    /** Hilt entry point so a plain CoroutineWorker can reach the singleton [MusicDatabase]. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AutoRecoEntryPoint {
        fun database(): MusicDatabase
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.dataStore.data.first()
            if (prefs[AiRecommendedPlaylistKey] != true) return@withContext Result.success()

            val database = EntryPointAccessors
                .fromApplication(context.applicationContext, AutoRecoEntryPoint::class.java)
                .database()

            refresh(database, prefs)
            Result.success()
        } catch (e: Exception) {
            // Best-effort refresh: a failure must never crash, retry-storm, or touch the last good
            // playlist (the daily run retries by itself tomorrow).
            Timber.tag(TAG).w(e, "AI recommended playlist refresh failed")
            Result.success()
        }
    }

    /** One full refresh: taste → AI → resolve → persist. Keeps the last good playlist on any failure. */
    private suspend fun refresh(
        database: MusicDatabase,
        prefs: Preferences,
    ) {
        // 1. TASTE INPUT — fully on-device: the user's most-played songs plus their most recent likes
        //    (the same DB signals the Home/AffinityEngine recommendations are built from).
        val mostPlayed = runCatching {
            database.mostPlayedSongs(fromTimeStamp = 0L, limit = TASTE_SONGS_PER_SOURCE).first()
        }.getOrDefault(emptyList())
        val liked = runCatching {
            database.likedSongs(SongSortType.CREATE_DATE, descending = true).first()
                .take(TASTE_SONGS_PER_SOURCE)
        }.getOrDefault(emptyList())

        val tasteSongs = (mostPlayed + liked).distinctBy { it.id }
        if (tasteSongs.isEmpty()) return // Nothing to learn from yet — keep whatever exists.
        val tasteIds = tasteSongs.map { it.id }.toSet()
        val tasteLines = tasteSongs.map { song ->
            "${song.song.title} — ${song.artists.joinToString(", ") { it.name }}"
        }

        // 2. AI ASK — the shared keyless chain (user key override → Aura Worker → Pollinations), with
        //    the same over-generate-then-take-N padding "Lista AI" uses (SongResolver silently drops
        //    tracks with no match). Bounded by the shared AI budget; on timeout/failure spec stays null
        //    and we keep the last good playlist (NO non-AI fallback here: unlike a user-requested
        //    playlist, a silent skip is strictly better than filling their recommendations with noise).
        val prompt = "Canciones NUEVAS para descubrir, del mismo estilo y gusto de alguien que escucha " +
            "estas canciones: ${tasteLines.joinToString("; ")}. Variadas pero coherentes con ese gusto. " +
            "NO incluyas ninguna de las canciones listadas ni otras versiones de ellas."
        val requestCount = (TARGET_SONGS * 3 + 1) / 2
        val spec = withTimeoutOrNull(AiPlaylistGenerator.AI_BUDGET_MS) {
            AiPlaylistService.generate(
                prompt = prompt,
                count = requestCount,
                provider = prefs[AiProviderKey] ?: "OpenRouter",
                apiKey = prefs[OpenRouterApiKey] ?: "",
                baseUrl = prefs[OpenRouterBaseUrlKey] ?: "",
                model = prefs[OpenRouterModelKey] ?: "",
            ).getOrNull()
        } ?: return

        // 3. RESOLVE — same shared resolver as the importers; short-circuits at the target so the
        //    padded tail never costs extra network calls. Songs the user already plays are excluded
        //    (this playlist is for DISCOVERY; the prompt asks too, but the model can't be trusted).
        val resolved = ArrayList<MediaMetadata>(TARGET_SONGS)
        for (track in spec.tracks) {
            if (resolved.size >= TARGET_SONGS) break
            val metadata = SongResolver.resolve(database, track.title, track.artist) ?: continue
            if (metadata.id in tasteIds) continue
            if (resolved.any { it.id == metadata.id }) continue
            resolved += metadata
        }
        if (resolved.isEmpty()) return // AI answered but nothing usable — keep the last good playlist.

        // 4. PERSIST — one transaction: upsert the fixed-id playlist entity (ALWAYS bumping
        //    lastUpdateTime), wipe the old mapping, insert the new songs in order.
        val now = LocalDateTime.now()
        val existing = database.getPlaylistById(PLAYLIST_ID)?.playlist
        database.withTransaction {
            if (existing == null) {
                insert(
                    PlaylistEntity(
                        id = PLAYLIST_ID,
                        name = PLAYLIST_NAME,
                        lastUpdateTime = now,
                        isEditable = false,
                        bookmarkedAt = now,
                    ),
                )
            } else {
                update(
                    existing.copy(
                        name = PLAYLIST_NAME,
                        lastUpdateTime = now,
                        // Re-bookmark if the user somehow un-bookmarked it, or it vanishes from Library.
                        bookmarkedAt = existing.bookmarkedAt ?: now,
                    ),
                )
            }
            clearPlaylist(PLAYLIST_ID)
            resolved.forEachIndexed { index, metadata ->
                insert(metadata)
                insert(
                    PlaylistSongMap(
                        playlistId = PLAYLIST_ID,
                        songId = metadata.id,
                        position = index,
                    ),
                )
            }
        }
        Timber.tag(TAG).d("AI recommended playlist refreshed with ${resolved.size} songs")
    }

    companion object {
        private const val TAG = "AutoRecoPlaylistWorker"
        private const val WORK_NAME = "ai_reco_playlist_daily"
        private const val WORK_NAME_NOW = "ai_reco_playlist_now"

        /** FIXED id of the single persistent playlist — always looked up by id, never by name. */
        const val PLAYLIST_ID = "AURA_AI_RECS"

        /** Display name (the entity's name is re-asserted on every refresh). */
        const val PLAYLIST_NAME = "Recomendado para ti (IA)"

        /** Size of the rebuilt playlist. */
        private const val TARGET_SONGS = 20

        /** How many songs each taste source (most-played / liked) contributes to the prompt. */
        private const val TASTE_SONGS_PER_SOURCE = 20

        /**
         * Schedules the daily run (network required). Safe to call on every app start:
         * [ExistingPeriodicWorkPolicy.UPDATE] keeps a single unique work item.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoRecoPlaylistWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Enqueues a one-time run immediately (network required). Used when the user flips the toggle
         * ON (so the playlist appears without waiting a day) and by the "Refrescar ahora" settings row.
         * Safe alongside the periodic work — a distinct unique work name keeps the two from interfering.
         */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AutoRecoPlaylistWorker>()
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
