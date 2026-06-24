/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.spotifyimport

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.SpotifyAccessTokenExpiresAtKey
import iad1tya.echo.music.constants.SpotifyAccessTokenKey
import iad1tya.echo.music.constants.SpotifyAccountAvatarUrlKey
import iad1tya.echo.music.constants.SpotifyAccountNameKey
import iad1tya.echo.music.constants.SpotifySpDcKey
import iad1tya.echo.music.constants.SpotifySpKeyKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.AlbumEntity
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import iad1tya.echo.music.spotify.models.SpotifyAlbum
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.spotify.Spotify
import iad1tya.echo.music.spotify.SpotifyAuth
import iad1tya.echo.music.spotify.SpotifyMapper
import iad1tya.echo.music.spotify.models.SpotifyArtist
import iad1tya.echo.music.spotify.models.SpotifyPlaylist
import iad1tya.echo.music.spotify.models.SpotifyPlaylistTracksRef
import iad1tya.echo.music.spotify.models.SpotifyTrack
import iad1tya.echo.music.utils.clearWebAuthSession
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.reportException
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SpotifyImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: iad1tya.echo.music.utils.SyncUtils,
) {
    private val mapperMutex = Mutex()

    suspend fun restoreSession(): SpotifyImportSession =
        withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            val token = prefs[SpotifyAccessTokenKey].orEmpty()
            val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            val accountName = prefs[SpotifyAccountNameKey].orEmpty()
            val avatarUrl = prefs[SpotifyAccountAvatarUrlKey]

            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                Spotify.accessToken = token
                return@withContext SpotifyImportSession(
                    isAuthenticated = true,
                    accountName = accountName,
                    accountAvatarUrl = avatarUrl,
                )
            }

            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) {
                return@withContext SpotifyImportSession()
            }

            refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty())
                .fold(
                    onSuccess = {
                        val refreshed = context.dataStore.data.first()
                        SpotifyImportSession(
                            isAuthenticated = true,
                            accountName = refreshed[SpotifyAccountNameKey].orEmpty(),
                            accountAvatarUrl = refreshed[SpotifyAccountAvatarUrlKey],
                        )
                    },
                    onFailure = {
                        if (it is CancellationException) throw it
                        reportException(it)
                        SpotifyImportSession()
                    },
                )
        }

    suspend fun connectWithCookies(
        spDc: String,
        spKey: String,
    ): SpotifyImportSession =
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[SpotifySpDcKey] = spDc
                if (spKey.isNotBlank()) {
                    prefs[SpotifySpKeyKey] = spKey
                } else {
                    prefs.remove(SpotifySpKeyKey)
                }
            }
            refreshAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
            val prefs = context.dataStore.data.first()
            SpotifyImportSession(
                isAuthenticated = true,
                accountName = prefs[SpotifyAccountNameKey].orEmpty(),
                accountAvatarUrl = prefs[SpotifyAccountAvatarUrlKey],
            )
        }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs.remove(SpotifySpDcKey)
                prefs.remove(SpotifySpKeyKey)
                prefs.remove(SpotifyAccessTokenKey)
                prefs.remove(SpotifyAccessTokenExpiresAtKey)
                prefs.remove(SpotifyAccountNameKey)
                prefs.remove(SpotifyAccountAvatarUrlKey)
            }
            Spotify.accessToken = null
            runCatching { clearWebAuthSession(context) }
                .onFailure(::reportException)
        }
    }

    suspend fun loadSources(): List<SpotifyImportSource> =
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            refreshProfile()

            val likedSongs = spotifyCallWithTokenRetry {
                Spotify.likedSongs(limit = 1, offset = 0).getOrThrow()
            }
            val followedArtistCount = spotifyCallWithTokenRetry {
                Spotify.myArtists(limit = 1, offset = 0).getOrThrow()
            }.total
            val savedAlbumCount = runCatching {
                spotifyCallWithTokenRetry { Spotify.mySavedAlbums(limit = 1, offset = 0).getOrThrow() }.total
            }.getOrDefault(0)
            val playlists = fetchAllPlaylists()

            buildList {
                add(
                    SpotifyImportSource.LikedSongs(
                        title = context.getString(R.string.spotify_liked_songs),
                        trackCount = likedSongs.total,
                    ),
                )
                if (followedArtistCount > 0) {
                    add(
                        SpotifyImportSource.FollowedArtists(
                            title = context.getString(R.string.spotify_followed_artists),
                            artistCount = followedArtistCount,
                        ),
                    )
                }
                if (savedAlbumCount > 0) {
                    add(
                        SpotifyImportSource.SavedAlbums(
                            title = context.getString(R.string.spotify_saved_albums),
                            albumCount = savedAlbumCount,
                        ),
                    )
                }
                playlists.forEach { playlist ->
                    if (playlist.id.isNotBlank()) {
                        add(SpotifyImportSource.Playlist(playlist))
                    }
                }
            }
        }

    suspend fun importSources(
        sources: List<SpotifyImportSource>,
        onProgress: (SpotifyImportProgressUi) -> Unit,
    ): SpotifyImportSummaryUi =
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val summaries = ArrayList<SpotifyImportSourceSummaryUi>(sources.size)

            sources.forEachIndexed { sourceIndex, source ->
                onProgress(
                    SpotifyImportProgressUi(
                        sourceTitle = source.title,
                        completedSources = sourceIndex,
                        totalSources = sources.size,
                        matchedTracks = 0,
                        totalTracks = source.trackCount ?: 0,
                        percent = progressPercent(sourceIndex, sources.size, 0, source.trackCount ?: 0),
                    ),
                )

                if (source is SpotifyImportSource.FollowedArtists) {
                    summaries += importFollowedArtists(
                        sourceIndex = sourceIndex,
                        sourceCount = sources.size,
                        source = source,
                        onProgress = onProgress,
                    )
                    return@forEachIndexed
                }

                if (source is SpotifyImportSource.SavedAlbums) {
                    summaries += importSavedAlbums(
                        sourceIndex = sourceIndex,
                        sourceCount = sources.size,
                        source = source,
                        onProgress = onProgress,
                    )
                    return@forEachIndexed
                }

                val tracks = fetchAllTracks(source)
                if (tracks.isEmpty()) {
                    mirrorPlaylist(source, emptyList())
                    summaries += SpotifyImportSourceSummaryUi(
                        title = source.title,
                        totalTracks = 0,
                        importedTracks = 0,
                        failedTracks = 0,
                    )
                    onProgress(
                        SpotifyImportProgressUi(
                            sourceTitle = source.title,
                            completedSources = sourceIndex + 1,
                            totalSources = sources.size,
                            matchedTracks = 0,
                            totalTracks = 0,
                            percent = progressPercent(sourceIndex + 1, sources.size, 0, 0),
                        ),
                    )
                    return@forEachIndexed
                }

                val matched = matchTracks(
                    sourceIndex = sourceIndex,
                    sourceCount = sources.size,
                    sourceTitle = source.title,
                    tracks = tracks,
                    onProgress = onProgress,
                )

                mirrorPlaylist(source, matched.map { it.metadata })
                summaries += SpotifyImportSourceSummaryUi(
                    title = source.title,
                    totalTracks = tracks.size,
                    importedTracks = matched.size,
                    failedTracks = tracks.size - matched.size,
                )
                onProgress(
                    SpotifyImportProgressUi(
                        sourceTitle = source.title,
                        completedSources = sourceIndex + 1,
                        totalSources = sources.size,
                        matchedTracks = matched.size,
                        totalTracks = tracks.size,
                        percent = progressPercent(sourceIndex + 1, sources.size, 0, 0),
                    ),
                )
            }

            // Follow EVERY artist brought in by the import (also the song artists from imported
            // playlists/albums, not only the explicitly-followed Spotify artists), so the home and
            // recommendations reflect them. Additive — never removes follows.
            runCatching { database.followArtistsWithContent(java.time.LocalDateTime.now()) }

            // Mirror the freshly-imported likes + library to YouTube Music (when signed in), so the
            // Spotify import also lands in the user's YT Music library. Fire-and-forget (sync queue).
            if (com.music.innertube.YouTube.cookie != null) {
                runCatching {
                    syncUtils.syncLikedSongs()
                    syncUtils.syncLibrarySongs()
                }
            }

            SpotifyImportSummaryUi(summaries)
        }

    private suspend fun ensureAuthenticated() {
        val prefs = context.dataStore.data.first()
        val token = prefs[SpotifyAccessTokenKey].orEmpty()
        val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
        if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
            Spotify.accessToken = token
            return
        }

        val spDc = prefs[SpotifySpDcKey].orEmpty()
        if (spDc.isBlank()) {
            throw IllegalStateException(context.getString(R.string.spotify_not_connected))
        }
        refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
    }

    private suspend fun refreshAccessToken(
        spDc: String,
        spKey: String,
    ): Result<Unit> =
        SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey)
            .mapCatching { token ->
                Spotify.accessToken = token.accessToken
                context.dataStore.edit { prefs ->
                    prefs[SpotifyAccessTokenKey] = token.accessToken
                    prefs[SpotifyAccessTokenExpiresAtKey] = token.accessTokenExpirationTimestampMs
                }
                refreshProfile()
            }

    private suspend fun refreshProfile() {
        Spotify.me()
            .onSuccess { user ->
                context.dataStore.edit { prefs ->
                    prefs[SpotifyAccountNameKey] = user.displayName.orEmpty()
                    user.images.firstOrNull()?.url?.let { prefs[SpotifyAccountAvatarUrlKey] = it }
                        ?: prefs.remove(SpotifyAccountAvatarUrlKey)
                }
            }
            .onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
            }
    }

    private suspend fun fetchAllPlaylists(): List<SpotifyPlaylist> {
        val playlists = ArrayList<SpotifyPlaylist>()
        var offset = 0
        val limit = 50

        while (true) {
            val page = spotifyCallWithTokenRetry {
                Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
            }
            if (page.items.isEmpty()) break
            playlists += enrichPlaylistTrackCounts(page.items)
            offset += page.items.size
            if (offset >= page.total || page.items.size < limit) break
        }

        return playlists
    }

    private suspend fun enrichPlaylistTrackCounts(playlists: List<SpotifyPlaylist>): List<SpotifyPlaylist> =
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_SPOTIFY_COUNT_REQUESTS)
            playlists.map { playlist ->
                async {
                    if (playlist.tracks?.total != null) {
                        playlist
                    } else {
                        semaphore.withPermit {
                            playlistTrackCount(playlist.id)
                                ?.let { count -> playlist.copy(tracks = SpotifyPlaylistTracksRef(total = count)) }
                                ?: playlist
                        }
                    }
                }
            }.awaitAll()
        }

    private suspend fun playlistTrackCount(playlistId: String): Int? =
        try {
            spotifyCallWithTokenRetry {
                Spotify.playlistTracks(
                    playlistId = playlistId,
                    limit = 1,
                    offset = 0,
                ).getOrThrow()
            }.total
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            reportException(error)
            null
        }

    private suspend fun fetchAllTracks(source: SpotifyImportSource): List<SpotifyTrack> {
        val tracks = ArrayList<SpotifyTrack>()
        var offset = 0
        val limit = 100

        while (true) {
            val page =
                when (source) {
                    is SpotifyImportSource.LikedSongs -> {
                        val paging = spotifyCallWithTokenRetry {
                            Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
                        }
                        SpotifyTrackPage(
                            items = paging.items.map { it.track },
                            total = paging.total,
                        )
                    }
                    is SpotifyImportSource.Playlist -> {
                        val paging = spotifyCallWithTokenRetry {
                            Spotify.playlistTracks(
                                playlistId = source.spotifyId,
                                limit = limit,
                                offset = offset,
                            ).getOrThrow()
                        }
                        SpotifyTrackPage(
                            items = paging.items.mapNotNull { it.track },
                            total = paging.total,
                        )
                    }
                    // Followed artists are imported via importFollowedArtists() in the
                    // importSources() loop and never reach this track-based path.
                    is SpotifyImportSource.FollowedArtists -> return emptyList()
                    // Saved albums are imported via importSavedAlbums() likewise.
                    is SpotifyImportSource.SavedAlbums -> return emptyList()
                }

            if (page.items.isEmpty()) break
            tracks += page.items.filter { it.name.isNotBlank() }
            offset += page.items.size
            if (offset >= page.total || page.items.size < limit) break
        }

        return tracks
    }

    private suspend fun <T> spotifyCallWithTokenRetry(block: suspend () -> T): T =
        runCatching { block() }
            .getOrElse { error ->
                if ((error as? Spotify.SpotifyException)?.statusCode != 401) {
                    throw error
                }
                val prefs = context.dataStore.data.first()
                val spDc = prefs[SpotifySpDcKey].orEmpty()
                if (spDc.isBlank()) {
                    throw error
                }
                refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
                block()
            }

    private suspend fun matchTracks(
        sourceIndex: Int,
        sourceCount: Int,
        sourceTitle: String,
        tracks: List<SpotifyTrack>,
        onProgress: (SpotifyImportProgressUi) -> Unit,
    ): List<MatchedTrack> =
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_MATCHES)
            val completed = AtomicInteger(0)

            tracks.mapIndexed { index, track ->
                async {
                    semaphore.withPermit {
                        val matched =
                            try {
                                matchTrack(track, index)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                reportException(error)
                                null
                            }
                        val completedCount = completed.incrementAndGet()
                        onProgress(
                            SpotifyImportProgressUi(
                                sourceTitle = sourceTitle,
                                completedSources = sourceIndex,
                                totalSources = sourceCount,
                                matchedTracks = completedCount,
                                totalTracks = tracks.size,
                                percent = progressPercent(sourceIndex, sourceCount, completedCount, tracks.size),
                            ),
                        )
                        matched
                    }
                }
            }.awaitAll()
                .filterNotNull()
                .sortedBy { it.index }
        }

    private suspend fun matchTrack(
        track: SpotifyTrack,
        index: Int,
    ): MatchedTrack? {
        val searchResult = YouTube.search(
            query = SpotifyMapper.buildSearchQuery(track),
            filter = YouTube.SearchFilter.FILTER_SONG,
        ).getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            return null
        }
        val candidates = searchResult.items
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }

        val best = mapperMutex.withLock {
            candidates.maxByOrNull { candidate ->
                SpotifyMapper.matchScore(
                    spotifyTitle = track.name,
                    spotifyArtist = track.artists.joinToString(" ") { it.name },
                    spotifyDurationMs = track.durationMs,
                    candidateTitle = candidate.title,
                    candidateArtist = candidate.artists.joinToString(" ") { it.name },
                    candidateDurationSec = candidate.duration,
                )
            }
        } ?: return null

        return MatchedTrack(index = index, metadata = best.toMediaMetadata())
    }

    // ── Followed artists ────────────────────────────────────────────────

    /**
     * Imports the user's followed Spotify artists: for each one, finds the best
     * matching YouTube Music artist, bookmarks it locally and subscribes the
     * channel on YouTube. Reports progress per artist and returns a summary where
     * "imported" = successfully matched + bookmarked artists.
     */
    private suspend fun importFollowedArtists(
        sourceIndex: Int,
        sourceCount: Int,
        source: SpotifyImportSource.FollowedArtists,
        onProgress: (SpotifyImportProgressUi) -> Unit,
    ): SpotifyImportSourceSummaryUi {
        val artists = fetchAllFollowedArtists()
        if (artists.isEmpty()) {
            onProgress(
                SpotifyImportProgressUi(
                    sourceTitle = source.title,
                    completedSources = sourceIndex + 1,
                    totalSources = sourceCount,
                    matchedTracks = 0,
                    totalTracks = 0,
                    percent = progressPercent(sourceIndex + 1, sourceCount, 0, 0),
                ),
            )
            return SpotifyImportSourceSummaryUi(
                title = source.title,
                totalTracks = 0,
                importedTracks = 0,
                failedTracks = 0,
            )
        }

        val completed = AtomicInteger(0)
        val imported = AtomicInteger(0)
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_MATCHES)
            artists.map { artist ->
                async {
                    semaphore.withPermit {
                        val followed =
                            try {
                                matchAndFollowArtist(artist)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                reportException(error)
                                false
                            }
                        if (followed) imported.incrementAndGet()
                        val completedCount = completed.incrementAndGet()
                        onProgress(
                            SpotifyImportProgressUi(
                                sourceTitle = source.title,
                                completedSources = sourceIndex,
                                totalSources = sourceCount,
                                matchedTracks = completedCount,
                                totalTracks = artists.size,
                                percent = progressPercent(sourceIndex, sourceCount, completedCount, artists.size),
                            ),
                        )
                    }
                }
            }.awaitAll()
        }

        onProgress(
            SpotifyImportProgressUi(
                sourceTitle = source.title,
                completedSources = sourceIndex + 1,
                totalSources = sourceCount,
                matchedTracks = imported.get(),
                totalTracks = artists.size,
                percent = progressPercent(sourceIndex + 1, sourceCount, 0, 0),
            ),
        )

        return SpotifyImportSourceSummaryUi(
            title = source.title,
            totalTracks = artists.size,
            importedTracks = imported.get(),
            failedTracks = artists.size - imported.get(),
        )
    }

    private suspend fun fetchAllFollowedArtists(): List<SpotifyArtist> {
        val artists = ArrayList<SpotifyArtist>()
        var offset = 0
        val limit = 50

        while (true) {
            val page = spotifyCallWithTokenRetry {
                Spotify.myArtists(limit = limit, offset = offset).getOrThrow()
            }
            if (page.items.isEmpty()) break
            artists += page.items.filter { it.name.isNotBlank() }
            offset += page.items.size
            if (offset >= page.total || page.items.size < limit) break
        }

        return artists
    }

    /**
     * Searches YouTube Music for [spotifyArtist], picks the first result whose
     * name likely matches, then upserts it as a bookmarked [ArtistEntity] and
     * subscribes the channel on YouTube. Returns true when the artist was matched
     * and bookmarked locally (the YouTube subscribe is best-effort).
     */
    private suspend fun matchAndFollowArtist(spotifyArtist: SpotifyArtist): Boolean {
        val searchResult = YouTube.search(
            query = spotifyArtist.name,
            filter = YouTube.SearchFilter.FILTER_ARTIST,
        ).getOrElse { error ->
            if (error is CancellationException) throw error
            return false
        }

        val match = searchResult.items
            .filterIsInstance<ArtistItem>()
            .firstOrNull { ArtistNameMatching.isLikelyMatch(spotifyArtist.name, it.title) }
            ?: return false

        // Resolve the real channel id the same way SyncUtils does: search-result
        // ArtistItems carry channelId == null and a browse id that is often not a
        // channel id, so subscribeChannel() would silently no-op on it. Prefer the
        // explicit channelId, then a browse id that already looks like a channel id
        // ("UC..."), otherwise resolve it via YouTube.getChannelId (returns "" on
        // failure). Stay tolerant: on any failure we still bookmark locally below.
        val channelId = match.channelId
            ?: if (match.id.startsWith("UC")) {
                match.id
            } else {
                runCatching { YouTube.getChannelId(match.id) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: ""
            }
        val thumbnail = match.thumbnail
        val existing = database.artist(match.id).first()?.artist

        database.withTransaction {
            val now = LocalDateTime.now()
            if (existing == null) {
                insert(
                    ArtistEntity(
                        id = match.id,
                        name = match.title,
                        thumbnailUrl = thumbnail,
                        channelId = channelId,
                        bookmarkedAt = now,
                    ),
                )
            } else {
                update(
                    existing.copy(
                        name = match.title,
                        thumbnailUrl = thumbnail ?: existing.thumbnailUrl,
                        channelId = channelId,
                        bookmarkedAt = existing.bookmarkedAt ?: now,
                        lastUpdateTime = now,
                    ),
                )
            }
        }

        if (channelId.isNotEmpty()) {
            runCatching { YouTube.subscribeChannel(channelId, true) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                }
        }

        return true
    }

    private suspend fun importSavedAlbums(
        sourceIndex: Int,
        sourceCount: Int,
        source: SpotifyImportSource.SavedAlbums,
        onProgress: (SpotifyImportProgressUi) -> Unit,
    ): SpotifyImportSourceSummaryUi {
        val albums = fetchAllSavedAlbums()
        if (albums.isEmpty()) {
            onProgress(
                SpotifyImportProgressUi(
                    sourceTitle = source.title,
                    completedSources = sourceIndex + 1,
                    totalSources = sourceCount,
                    matchedTracks = 0,
                    totalTracks = 0,
                    percent = progressPercent(sourceIndex + 1, sourceCount, 0, 0),
                ),
            )
            return SpotifyImportSourceSummaryUi(source.title, 0, 0, 0)
        }

        val completed = AtomicInteger(0)
        val imported = AtomicInteger(0)
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_MATCHES)
            albums.map { album ->
                async {
                    semaphore.withPermit {
                        val ok =
                            try {
                                matchAndBookmarkAlbum(album)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                reportException(error)
                                false
                            }
                        if (ok) imported.incrementAndGet()
                        val completedCount = completed.incrementAndGet()
                        onProgress(
                            SpotifyImportProgressUi(
                                sourceTitle = source.title,
                                completedSources = sourceIndex,
                                totalSources = sourceCount,
                                matchedTracks = completedCount,
                                totalTracks = albums.size,
                                percent = progressPercent(sourceIndex, sourceCount, completedCount, albums.size),
                            ),
                        )
                    }
                }
            }.awaitAll()
        }

        onProgress(
            SpotifyImportProgressUi(
                sourceTitle = source.title,
                completedSources = sourceIndex + 1,
                totalSources = sourceCount,
                matchedTracks = imported.get(),
                totalTracks = albums.size,
                percent = progressPercent(sourceIndex + 1, sourceCount, 0, 0),
            ),
        )

        return SpotifyImportSourceSummaryUi(
            title = source.title,
            totalTracks = albums.size,
            importedTracks = imported.get(),
            failedTracks = albums.size - imported.get(),
        )
    }

    private suspend fun fetchAllSavedAlbums(): List<SpotifyAlbum> {
        val albums = ArrayList<SpotifyAlbum>()
        var offset = 0
        val limit = 50
        while (true) {
            val page = spotifyCallWithTokenRetry {
                Spotify.mySavedAlbums(limit = limit, offset = offset).getOrThrow()
            }
            if (page.items.isEmpty()) break
            albums += page.items.filter { it.name.isNotBlank() }
            offset += page.items.size
            if (offset >= page.total || page.items.size < limit) break
        }
        return albums
    }

    /**
     * Searches YouTube Music for [spotifyAlbum] (album + artist), picks the first close title match,
     * and upserts it as a bookmarked [AlbumEntity] so it appears under Favorite Albums.
     */
    private suspend fun matchAndBookmarkAlbum(spotifyAlbum: SpotifyAlbum): Boolean {
        val artistName = spotifyAlbum.artists.firstOrNull()?.name.orEmpty()
        val query = listOf(spotifyAlbum.name, artistName).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return false

        val searchResult = YouTube.search(
            query = query,
            filter = YouTube.SearchFilter.FILTER_ALBUM,
        ).getOrElse { error ->
            if (error is CancellationException) throw error
            return false
        }

        val match = searchResult.items
            .filterIsInstance<AlbumItem>()
            .firstOrNull { item ->
                item.title.contains(spotifyAlbum.name, ignoreCase = true) ||
                    spotifyAlbum.name.contains(item.title, ignoreCase = true)
            } ?: return false

        val existing = database.album(match.id).first()?.album

        database.withTransaction {
            val now = LocalDateTime.now()
            if (existing == null) {
                insert(
                    AlbumEntity(
                        id = match.id,
                        playlistId = match.playlistId,
                        title = match.title,
                        thumbnailUrl = match.thumbnail,
                        songCount = 0,
                        duration = 0,
                        explicit = match.explicit,
                        lastUpdateTime = now,
                        bookmarkedAt = now,
                    ),
                )
            } else {
                update(
                    existing.copy(
                        bookmarkedAt = existing.bookmarkedAt ?: now,
                        lastUpdateTime = now,
                    ),
                )
            }
        }

        return true
    }

    private suspend fun mirrorPlaylist(
        source: SpotifyImportSource,
        tracks: List<MediaMetadata>,
    ) {
        database.withTransaction {
            val existing = getPlaylistById(source.localPlaylistId)
            val now = LocalDateTime.now()
            val entity =
                existing?.playlist?.copy(
                    name = source.title,
                    bookmarkedAt = existing.playlist.bookmarkedAt ?: now,
                    lastUpdateTime = now,
                    thumbnailUrl = source.thumbnailUrl,
                    isEditable = true,
                ) ?: PlaylistEntity(
                    id = source.localPlaylistId,
                    name = source.title,
                    bookmarkedAt = now,
                    lastUpdateTime = now,
                    thumbnailUrl = source.thumbnailUrl,
                    isEditable = true,
                )

            if (existing == null) {
                insert(entity)
            } else {
                update(entity)
            }

            val libraryNow = LocalDateTime.now()
            // Spotify "Liked Songs" → mark each matched track as liked so they land in the app's
            // Liked Songs (not only in a mirror playlist).
            val markLiked = source is SpotifyImportSource.LikedSongs
            tracks.forEach { metadata ->
                // Read once: branch on existence to avoid a redundant SELECT per song.
                val existing = getSongByIdBlocking(metadata.id)?.song
                if (existing == null) {
                    // New song: insert (also links artists) with inLibrary already set.
                    insert(metadata) { song ->
                        song.copy(
                            inLibrary = libraryNow,
                            liked = song.liked || markLiked,
                            likedDate = if (markLiked && !song.liked) libraryNow else song.likedDate,
                        )
                    }
                } else if (existing.inLibrary == null || (markLiked && !existing.liked)) {
                    // Stamp inLibrary and/or mark liked.
                    update(
                        existing.copy(
                            inLibrary = existing.inLibrary ?: libraryNow,
                            liked = existing.liked || markLiked,
                            likedDate = if (markLiked && !existing.liked) libraryNow else existing.likedDate,
                        )
                    )
                }
            }

            clearPlaylist(source.localPlaylistId)
            tracks.forEachIndexed { index, metadata ->
                insert(
                    PlaylistSongMap(
                        playlistId = source.localPlaylistId,
                        songId = metadata.id,
                        position = index,
                        setVideoId = metadata.setVideoId,
                    ),
                )
            }
            update(entity.copy(lastUpdateTime = now))
        }
    }

    private fun progressPercent(
        completedSources: Int,
        totalSources: Int,
        completedTracks: Int,
        totalTracks: Int,
    ): Int {
        if (totalSources <= 0) return 0
        val sourceProgress =
            if (totalTracks <= 0) {
                0f
            } else {
                completedTracks.toFloat() / totalTracks.toFloat()
            }
        return (((completedSources + sourceProgress) / totalSources.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private data class SpotifyTrackPage(
        val items: List<SpotifyTrack>,
        val total: Int,
    )

    private data class MatchedTrack(
        val index: Int,
        val metadata: MediaMetadata,
    )

    companion object {
        // Keep YouTube matching gentle: a big library (4000+ liked songs = 4000 searches) at high
        // concurrency trips YouTube's IP rate-limiter, which then also throttles live playback's
        // stream resolution — the song-change "hangs for minutes" bug. 2 is a safe, polite ceiling.
        private const val MAX_CONCURRENT_MATCHES = 2
        private const val MAX_CONCURRENT_SPOTIFY_COUNT_REQUESTS = 4
        private const val TOKEN_EXPIRY_GRACE_MS = 60_000L
    }
}

data class SpotifyImportSession(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
)

sealed interface SpotifyImportSource {
    val id: String
    val title: String
    val subtitle: String
    val thumbnailUrl: String?
    val trackCount: Int?
    val localPlaylistId: String
    val type: SpotifyImportSourceType

    data class Playlist(
        val playlist: SpotifyPlaylist,
    ) : SpotifyImportSource {
        val spotifyId: String = playlist.id
        override val id: String = "playlist:${playlist.id}"
        override val title: String = playlist.name
        override val subtitle: String = playlist.owner?.displayName.orEmpty()
        override val thumbnailUrl: String? = SpotifyMapper.getPlaylistThumbnail(playlist)
        override val trackCount: Int? = playlist.tracks?.total
        override val localPlaylistId: String = "SPOTIFY_PLAYLIST_${playlist.id}"
        override val type: SpotifyImportSourceType = SpotifyImportSourceType.PLAYLIST
    }

    data class LikedSongs(
        override val title: String,
        override val trackCount: Int,
    ) : SpotifyImportSource {
        override val id: String = "liked_songs"
        override val subtitle: String = ""
        override val thumbnailUrl: String? = null
        override val localPlaylistId: String = "SPOTIFY_LIKED_SONGS"
        override val type: SpotifyImportSourceType = SpotifyImportSourceType.LIKED_SONGS
    }

    /**
     * The artists the user follows on Spotify. Imported by matching each one to a
     * YouTube Music artist channel, then bookmarking it locally and subscribing on
     * YouTube. [trackCount] carries the number of followed artists (reused by the
     * generic progress/summary UI as the item total). [localPlaylistId] is unused
     * for this source — followed artists are not mirrored into a playlist.
     */
    data class FollowedArtists(
        override val title: String,
        val artistCount: Int,
    ) : SpotifyImportSource {
        override val id: String = "followed_artists"
        override val subtitle: String = ""
        override val thumbnailUrl: String? = null
        override val trackCount: Int = artistCount
        override val localPlaylistId: String = "SPOTIFY_FOLLOWED_ARTISTS"
        override val type: SpotifyImportSourceType = SpotifyImportSourceType.ARTISTS
    }

    /**
     * The albums the user saved on Spotify. Imported by matching each one to a YouTube Music album
     * and bookmarking it locally, so they show up under Favorite Albums. [trackCount] carries the
     * album count (reused by the generic progress/summary UI). Not mirrored into a playlist.
     */
    data class SavedAlbums(
        override val title: String,
        val albumCount: Int,
    ) : SpotifyImportSource {
        override val id: String = "saved_albums"
        override val subtitle: String = ""
        override val thumbnailUrl: String? = null
        override val trackCount: Int = albumCount
        override val localPlaylistId: String = "SPOTIFY_SAVED_ALBUMS"
        override val type: SpotifyImportSourceType = SpotifyImportSourceType.SAVED_ALBUMS
    }
}
