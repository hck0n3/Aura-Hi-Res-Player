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
import androidx.datastore.preferences.core.edit
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
import iad1tya.echo.music.constants.SpotifyAccessTokenExpiresAtKey
import iad1tya.echo.music.constants.SpotifyAccessTokenKey
import iad1tya.echo.music.constants.SpotifySpDcKey
import iad1tya.echo.music.constants.SpotifySpKeyKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ReleaseRadarItem
import iad1tya.echo.music.spotify.Spotify
import iad1tya.echo.music.spotify.SpotifyAuth
import iad1tya.echo.music.spotify.models.SpotifyAlbum
import iad1tya.echo.music.spotify.models.SpotifyArtist
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Weekly background worker that rebuilds the in-app Release Radar to mirror Spotify's real mechanics.
 *
 * Model = STRICT 7-DAY WEEKLY WIPE (non-cumulative): every Friday the table is fully cleared and re-seeded
 * from scratch with ONLY releases whose EXACT Spotify release date falls in the last 7 days (Friday-to-Friday).
 * There is no 28-day retention and no drop-on-play — each run is a fresh weekly drop.
 *
 * Sources (when a Spotify session exists): artists the user FOLLOWS (Spotify follows ∪ app-bookmarked artists,
 * priority) plus artists they LISTEN to (Spotify top artists ∪ local most-played). Exact dates come from
 * [Spotify.artistDiscography] (`date.isoString`) with [Spotify.newReleases] as a supplementary pool; each
 * Spotify release is resolved to a playable YouTube Music album via [YouTube.search]. The list is deduped,
 * hard-capped to one release per artist, ordered followed-first then by release date desc, and capped to ~30.
 *
 * When no Spotify session is present the worker falls back to the YouTube-only path (bookmarked YT artists),
 * which only exposes a release YEAR — so it keeps this year's releases as a best-effort drop.
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
            val today = LocalDate.now()
            val windowStart = today.minusDays(WINDOW_DAYS)

            val items: List<ReleaseRadarItem>
            // Whether an empty result is a trustworthy "no releases this week" vs a transient fetch failure.
            // Guards the wipe so a flaky network never nukes a good list down to empty.
            val emptyIsTrusted: Boolean

            if (ensureSpotifyToken()) {
                val gathered = gatherSpotify(database, today, windowStart)
                items = gathered.items
                emptyIsTrusted = gathered.feedOk
            } else {
                // FALLBACK: Spotify not connected → YouTube-only (release YEAR, no exact dates).
                items = gatherYouTubeFallback(database, today)
                emptyIsTrusted = items.isNotEmpty()
            }

            // Snapshot BEFORE wiping so the notification only counts genuinely new releases vs last week.
            val existingIds = database.releasesByDateDesc().first().map { it.id }.toSet()

            // STRICT WEEKLY WIPE: rebuild from scratch with this week's drop only. Only wipe when we have
            // items, or when the feed reliably confirmed an empty week — otherwise keep last week's list.
            if (items.isNotEmpty() || emptyIsTrusted) {
                database.clearReleases()
                if (items.isNotEmpty()) database.insertNewReleasesIgnore(items)
                val newCount = items.count { it.id !in existingIds }
                if (newCount > 0) postNotification(newCount)
            }

            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Release radar run failed")
            Result.retry()
        }
    }

    // ── Spotify path (exact dates) ───────────────────────────────────────

    private data class SpotifyGather(val items: List<ReleaseRadarItem>, val feedOk: Boolean)

    private data class SpotifyReleaseHit(
        val album: SpotifyAlbum,
        val artistName: String,
        val date: LocalDate,
        val followed: Boolean,
    )

    private suspend fun gatherSpotify(
        database: MusicDatabase,
        today: LocalDate,
        windowStart: LocalDate,
    ): SpotifyGather {
        // 1. Source artists. FOLLOWED (priority) = Spotify follows ∪ app-bookmarked artists.
        //    LISTENED = Spotify top artists ∪ local most-played artists.
        val followedSpotify = fetchFollowedSpotifyArtists()
        val listenedSpotify = runCatching {
            Spotify.topArtists(timeRange = "medium_term", limit = 50).getOrThrow().items
        }.getOrDefault(emptyList())

        val bookmarked = database.artistsBookmarkedByNameAsc().first().map { it.artist }
        val localPlayed = runCatching {
            database.mostPlayedArtists(fromTimeStamp = 0L, limit = 30).first().map { it.artist }
        }.getOrDefault(emptyList())

        val followedNames = (followedSpotify.map { it.name } + bookmarked.map { it.name })
            .map { it.lowercase() }.toSet()
        val listenedNames = (listenedSpotify.map { it.name } + localPlayed.map { it.name })
            .map { it.lowercase() }.toSet()
        val allNames = followedNames + listenedNames

        val semaphore = Semaphore(MAX_CONCURRENCY)

        // 2. Per-artist discography (exact `date.isoString`). Followed first, capped so a huge library
        //    doesn't fan out into hundreds of requests.
        val scanArtists = (followedSpotify.map { it to true } + listenedSpotify.map { it to false })
            .distinctBy { it.first.id }
            .filter { it.first.id.isNotBlank() }
            .take(MAX_ARTISTS_SCAN)

        val discoReleases = coroutineScope {
            scanArtists.map { (artist, followed) ->
                async {
                    semaphore.withPermit {
                        runCatching { Spotify.artistDiscography(artist.id).getOrThrow() }
                            .onFailure { Timber.tag(TAG).w(it, "Discography failed for ${artist.name}") }
                            .getOrDefault(emptyList())
                            .toReleaseHits(artist.name, followed, today, windowStart)
                    }
                }
            }.awaitAll().flatten()
        }

        // 3. "What's new" feed (exact dates) as a supplementary pool, filtered to followed/listened artists.
        val feedResult = Spotify.newReleases(limit = 50)
        val feedReleases = feedResult.getOrNull()?.albums?.items.orEmpty().mapNotNull { album ->
            val artistName = album.artists.firstOrNull()?.name ?: return@mapNotNull null
            if (artistName.lowercase() !in allNames) return@mapNotNull null
            val date = parseExactDate(album.releaseDate) ?: return@mapNotNull null
            if (date.isBefore(windowStart) || date.isAfter(today)) return@mapNotNull null
            SpotifyReleaseHit(album, artistName, date, followed = artistName.lowercase() in followedNames)
        }

        // 4. Resolve each Spotify release → a playable YouTube Music album browseId (bounded concurrency).
        val allHits = (discoReleases + feedReleases).distinctBy { it.album.id }
        val candidates = coroutineScope {
            allHits.map { hit ->
                async {
                    semaphore.withPermit {
                        val ytMatch = runCatching { matchSpotifyToYoutubeAlbum(hit.album.name, hit.artistName) }
                            .getOrNull()
                        ytMatch?.let {
                            ReleaseCandidate(
                                title = hit.album.name,
                                artist = hit.artistName,
                                date = hit.date,
                                source = "spotify",
                                artworkUri = hit.album.images.firstOrNull()?.url ?: it.thumbnail,
                                playId = it.id,
                                followed = hit.followed,
                            )
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val ranked = ReleaseRadarMatching.rankForRadar(
            ReleaseRadarMatching.onePerArtist(ReleaseRadarMatching.dedupe(candidates)),
            cap = MAX_ITEMS,
        )
        val items = ranked.map { it.toEntity(artistIdFor(it, bookmarked)) }
        return SpotifyGather(items = items, feedOk = feedResult.isSuccess)
    }

    /** Pages the user's followed Spotify artists (bounded by [MAX_ARTISTS_SCAN]). */
    private suspend fun fetchFollowedSpotifyArtists(): List<SpotifyArtist> {
        val out = mutableListOf<SpotifyArtist>()
        var offset = 0
        val limit = 50
        while (out.size < MAX_ARTISTS_SCAN) {
            val page = runCatching { Spotify.myArtists(limit = limit, offset = offset).getOrThrow() }
                .getOrNull() ?: break
            if (page.items.isEmpty()) break
            out += page.items
            offset += page.items.size
            if (offset >= page.total || page.items.size < limit) break
        }
        return out
    }

    /** Keeps only releases whose exact date is inside the [windowStart]..[today] 7-day window. */
    private fun List<SpotifyAlbum>.toReleaseHits(
        artistNameFallback: String,
        followed: Boolean,
        today: LocalDate,
        windowStart: LocalDate,
    ): List<SpotifyReleaseHit> = mapNotNull { album ->
        val date = parseExactDate(album.releaseDate) ?: return@mapNotNull null
        if (date.isBefore(windowStart) || date.isAfter(today)) return@mapNotNull null
        val artistName = album.artists.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: artistNameFallback
        SpotifyReleaseHit(album, artistName, date, followed)
    }

    /**
     * Searches YouTube Music for a Spotify release and returns a STRICT album match for playback.
     *
     * A candidate is only accepted when BOTH hold (normalized: lowercase, accent- and punctuation-stripped,
     * feat./ft. markers dropped):
     *  - ARTIST matches: one of the candidate's artists equals, contains, or is contained in the expected
     *    Spotify [artistName] (covers collaborations / "Artist & Guest" listings).
     *  - TITLE matches forward only: the candidate title equals or CONTAINS the wanted album name. The old
     *    reverse check (`albumName.contains(item.title)`) is gone, so a short YT title ("Love") no longer
     *    false-matches a longer query ("Love Story").
     *
     * When nothing passes both checks it returns null and the caller DROPS the release (see [gatherSpotify]:
     * `ytMatch?.let { … }` + `filterNotNull()`), so a wrong/similar release is never stored in `playId`.
     */
    private suspend fun matchSpotifyToYoutubeAlbum(albumName: String, artistName: String): AlbumItem? {
        val query = listOf(albumName, artistName).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull() ?: return null

        val wantedTitle = normalizeForMatch(albumName)
        val wantedArtist = normalizeForMatch(artistName)
        // Without a usable title AND artist we cannot verify a match — drop rather than guess.
        if (wantedTitle.isBlank() || wantedArtist.isBlank()) return null

        return result.items
            .filterIsInstance<AlbumItem>()
            .firstOrNull { item ->
                val candidateTitle = normalizeForMatch(item.title)
                // Forward-only: candidate title equals or contains the wanted album name.
                val titleOk = candidateTitle.isNotBlank() &&
                    (candidateTitle == wantedTitle || candidateTitle.contains(wantedTitle))
                if (!titleOk) return@firstOrNull false

                // Artist verification: at least one candidate artist must line up with the Spotify artist.
                item.artists.orEmpty().any { a ->
                    val candidateArtist = normalizeForMatch(a.name)
                    candidateArtist.isNotBlank() && (
                        candidateArtist == wantedArtist ||
                            candidateArtist.contains(wantedArtist) ||
                            wantedArtist.contains(candidateArtist)
                        )
                }
            }
    }

    /**
     * Normalizes a title/artist for tolerant-but-safe comparison: strips diacritics (é→e), lowercases,
     * removes feat./ft./featuring markers, and reduces punctuation to single spaces. Used by
     * [matchSpotifyToYoutubeAlbum] so "Beyoncé" ≈ "Beyonce" and "Album (Deluxe)" ≈ "album deluxe".
     */
    private fun normalizeForMatch(raw: String): String {
        val decomposed = java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
        return decomposed
            .replace(DIACRITICS_REGEX, "")
            .replace(FEAT_REGEX, " ")
            .replace(NON_ALNUM_REGEX, " ")
            .trim()
    }

    /**
     * Parses a Spotify release-date string to an exact [LocalDate]. Accepts full ISO datetimes
     * ("2024-06-14T00:00:00Z"), plain dates ("2024-06-14"). Year-only values ("2024") yield null, so
     * non-exact releases are windowed out (they can't be confidently placed in a 7-day window).
     */
    private fun parseExactDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull()
            ?: runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
    }

    // ── YouTube fallback (release YEAR only) ─────────────────────────────

    private suspend fun gatherYouTubeFallback(
        database: MusicDatabase,
        today: LocalDate,
    ): List<ReleaseRadarItem> {
        // Only YouTube artists have a browseId we can fetch.
        val artists = database.artistsBookmarkedByNameAsc().first()
            .map { it.artist }
            .filter { it.isYouTubeArtist }
        if (artists.isEmpty()) return emptyList()

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

        // YouTube only exposes release YEAR (pinned to Jan 1), so a strict 7-day window can't apply. Keep
        // this year's releases (bookmarked artists are all "followed") as the best-effort weekly drop, still
        // hard-capped to one release per artist and ~30 total.
        val recent = ReleaseRadarMatching.dedupe(candidates).filter { it.date.year >= today.year }
        val ranked = ReleaseRadarMatching.rankForRadar(
            ReleaseRadarMatching.onePerArtist(recent),
            cap = MAX_ITEMS,
        )
        return ranked.map { it.toEntity(artistIdFor(it, artists)) }
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
                    followed = true,
                )
            }
        }
    }

    // ── Auth + persistence helpers ───────────────────────────────────────

    /**
     * Ensures [Spotify.accessToken] is set for this run. Reuses a still-valid cached token, otherwise
     * refreshes it from the stored sp_dc cookie (same flow the import repository uses). Returns false when
     * no Spotify session exists (or a refresh fails) so the caller uses the YouTube fallback.
     */
    private suspend fun ensureSpotifyToken(): Boolean = runCatching {
        val prefs = context.dataStore.data.first()
        val token = prefs[SpotifyAccessTokenKey].orEmpty()
        val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
        if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_GRACE_MS) {
            Spotify.accessToken = token
            return@runCatching true
        }
        val spDc = prefs[SpotifySpDcKey].orEmpty()
        if (spDc.isBlank()) return@runCatching false
        val fresh = SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
        Spotify.accessToken = fresh.accessToken
        context.dataStore.edit { p ->
            p[SpotifyAccessTokenKey] = fresh.accessToken
            p[SpotifyAccessTokenExpiresAtKey] = fresh.accessTokenExpirationTimestampMs
        }
        true
    }.getOrDefault(false)

    private fun ReleaseCandidate.toEntity(artistId: String) = ReleaseRadarItem(
        id = ReleaseRadarMatching.dedupeKey(this),
        artistId = artistId,
        title = title,
        artist = artist,
        type = "Release",
        // Exact Spotify date lands in the EXISTING releaseDate column (year-only for the YT fallback).
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

        /** Hard cap on the visible list (Spotify Release Radar shows ~30 tracks). */
        private const val MAX_ITEMS = 30

        /** Upper bound on how many artists we fan out discography requests to per weekly run. */
        private const val MAX_ARTISTS_SCAN = 80

        /** Rolling entry window: only releases from the last 7 days (Friday-to-Friday). */
        private const val WINDOW_DAYS = 7L

        /** Re-use a cached Spotify token only if it stays valid at least this long. */
        private const val TOKEN_GRACE_MS = 60_000L

        /** One-time initial-seed guard so a fresh install seeds once, not on every launch. */
        private const val SEED_ONCE_KEY = "release_radar_seeded_once"

        // Normalization regexes for strict Spotify→YouTube album matching (see matchSpotifyToYoutubeAlbum).
        /** Combining diacritical marks left after NFD decomposition (é→e). */
        private val DIACRITICS_REGEX = Regex("\\p{M}+")
        /** "feat"/"ft"/"featuring" markers, dropped so collaborations still match. */
        private val FEAT_REGEX = Regex("\\b(?:feat|ft|featuring)\\b")
        /** Any run of non-alphanumeric chars → a single space. */
        private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")

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
         * Enqueues a one-time run immediately (network required). Used by the manual refresh action and by
         * [seedOnceIfNeeded]. Safe to call while the weekly periodic work is also scheduled — a distinct
         * unique work name keeps the two from interfering.
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
         * Runs the radar ONCE per install so a fresh install isn't empty before the first Friday drop.
         * Gated by a persisted flag so it does NOT fire on every app launch (real Release Radar only
         * refreshes weekly). After this, the Friday periodic worker takes over.
         */
        fun seedOnceIfNeeded(context: Context) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean(SEED_ONCE_KEY, false)) return
            prefs.edit().putBoolean(SEED_ONCE_KEY, true).apply()
            runNow(context)
        }

        /**
         * Start of the current weekly window: the most recent Friday at 00:00 local time. The screen
         * ([iad1tya.echo.music.viewmodels.ReleaseRadarViewModel]) keys its "this week's drop" filter off this.
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
