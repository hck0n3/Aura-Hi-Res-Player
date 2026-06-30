package iad1tya.echo.music.releaseradar

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import iad1tya.echo.music.MainActivity
import iad1tya.echo.music.R
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ReleaseRadarItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Weekly background worker (Phase 7c) that fetches recent releases from followed (bookmarked)
 * YouTube Music artists, deduplicates and date-filters them via [ReleaseRadarMatching], caches the
 * survivors in the `release_radar` table and posts a notification when new ones appear.
 *
 * Spotify discography is intentionally deferred for now (no clean per-release date helper exists on
 * [iad1tya.echo.music.spotify.Spotify]); the candidate model already supports `source = "spotify"`
 * so it can be added later without touching the matching/persistence path.
 */
class ReleaseRadarWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    /** Hilt entry point so a plain CoroutineWorker can reach the singleton [MusicDatabase]. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReleaseRadarEntryPoint {
        fun database(): MusicDatabase
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = EntryPointAccessors
            .fromApplication(context.applicationContext, ReleaseRadarEntryPoint::class.java)
            .database()

        try {
            // 1. Followed artists (bookmarked). Only YouTube artists have a browseId we can fetch.
            val artists = database.artistsBookmarkedByNameAsc().first()
                .map { it.artist }
                .filter { it.isYouTubeArtist }
            if (artists.isEmpty()) return@withContext Result.success()

            // Snapshot the current table once: drives both the first-run seed range and new-item detection.
            val existingIds = database.releasesByDateDesc().first().map { it.id }.toSet()
            val today = LocalDate.now()

            // 2. Fetch per artist with bounded concurrency; one failure must not abort the rest.
            val semaphore = Semaphore(MAX_CONCURRENCY)
            val candidates = coroutineScope {
                artists.map { artist ->
                    async {
                        semaphore.withPermit {
                            runCatching { fetchYtCandidates(artist.id, artist.name) }
                                .onFailure { Timber.tag(TAG).w(it, "Fetch failed for ${artist.name}") }
                                .getOrDefault(emptyList())
                        }
                    }
                }.awaitAll().flatten()
            }

            // 3. Dedupe (prefers "yt") then keep releases from the current year (or previous year on
            //    first run), because YouTube only exposes release YEAR and pins dates to Jan 1.
            //    A symmetric day-distance window would leave the radar empty for ~10 months/year.
            val minYear = if (existingIds.isEmpty()) today.year - 1 else today.year
            val recent = ReleaseRadarMatching.dedupe(candidates).filter { it.date.year >= minYear }
            if (recent.isEmpty()) return@withContext Result.success()

            // 4. Persist with FIRST-SEEN semantics. insertNewReleasesIgnore inserts only genuinely new
            //    releases (IGNORE keeps an already-known release's original fetchedAt), so fetchedAt marks
            //    when a release was FIRST seen. Then prune everything first seen before this week's window
            //    so previous Fridays' drops fall off — Spotify Release-Radar behavior (only this week's drop).
            val items = recent.map { it.toEntity(artistIdFor(it, artists)) }
            val newCount = items.count { it.id !in existingIds }
            database.insertNewReleasesIgnore(items)
            database.pruneReleasesBefore(currentWindowStart())

            // 5. Notify on new (previously-unseen) items.
            if (newCount > 0) postNotification(newCount)

            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Release radar run failed")
            Result.retry()
        }
    }

    /** Pull an artist's albums/singles from the artist page and map them to YT candidates. */
    private suspend fun fetchYtCandidates(browseId: String, artistName: String): List<ReleaseCandidate> {
        val page = YouTube.artist(browseId).getOrNull() ?: return emptyList()
        // Albums and singles both surface as AlbumItem across the artist sections.
        return page.sections.flatMap { section ->
            section.items.filterIsInstance<AlbumItem>().mapNotNull { album ->
                // YouTube only exposes a release YEAR here, not an exact date, so we pin to Jan 1
                // of that year. Items without any year cannot be windowed, so they are skipped.
                val year = album.year ?: return@mapNotNull null
                ReleaseCandidate(
                    title = album.title,
                    artist = album.artists?.firstOrNull()?.name ?: artistName,
                    date = LocalDate.of(year, 1, 1),
                    source = "yt",
                    artworkUri = album.thumbnail,
                    playId = album.browseId,
                )
            }
        }
    }

    private fun ReleaseCandidate.toEntity(artistId: String) = ReleaseRadarItem(
        id = ReleaseRadarMatching.dedupeKey(this),
        artistId = artistId,
        title = title,
        artist = artist,
        type = "Release",
        releaseDate = date.atStartOfDay(),
        artworkUri = artworkUri,
        source = source,
        playId = playId,
    )

    /** Best-effort mapping of a candidate back to a followed artist id (by name), for grouping.
     *  Falls back to "" on no match so a release is never mis-attributed to an arbitrary artist. */
    private fun artistIdFor(
        candidate: ReleaseCandidate,
        artists: List<iad1tya.echo.music.db.entities.ArtistEntity>,
    ): String = artists.firstOrNull { it.name.equals(candidate.artist, ignoreCase = true) }?.id
        ?: ""

    private fun postNotification(newCount: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.release_radar_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            nm.createNotificationChannel(channel)
        }

        // Plain launch intent for now; a deep link to the Release Radar screen lands in the next sub-task.
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        val pending = android.app.PendingIntent.getActivity(context, NOTIFICATION_ID, launchIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_nobg)
            .setContentTitle(context.getString(R.string.release_radar_title))
            .setContentText(context.getString(R.string.release_radar_notification))
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
        private const val TAG = "ReleaseRadarWorker"
        private const val WORK_NAME = "release_radar_weekly"
        private const val CHANNEL_ID = "release_radar"
        private const val NOTIFICATION_ID = 2002
        private const val MAX_CONCURRENCY = 4

        /**
         * Schedules the weekly run, aligned to the next Friday ~08:00 local time. Safe to call on
         * every app start: [ExistingPeriodicWorkPolicy.UPDATE] keeps a single unique work item.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ReleaseRadarWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelayToNextFridayMorning(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Enqueues a one-time run immediately (network required). Safe to call while the weekly
         * periodic work is also scheduled — uses a distinct unique work name so the two don't
         * interfere with each other.
         */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ReleaseRadarWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "release_radar_now",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Start of the current weekly window: the most recent Friday at 00:00 local time. Releases first
         * seen on/after this instant are "this week's drop"; anything older is pruned/hidden. The screen
         * ([ReleaseRadarViewModel]) and the worker's prune step both key off this so they stay in sync.
         */
        fun currentWindowStart(): LocalDateTime {
            return LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.FRIDAY))
                .atStartOfDay()
        }

        /** Milliseconds from now until the next Friday at 08:00 local time. */
        private fun initialDelayToNextFridayMorning(): Long {
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.now(zone)
            var next = now
                .with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY))
                .withHour(8).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) {
                next = next.plusWeeks(1)
            }
            val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
            val nextMillis = next.atZone(zone).toInstant().toEpochMilli()
            return (nextMillis - nowMillis).coerceAtLeast(0L)
        }
    }
}
