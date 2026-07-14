package iad1tya.echo.music.utils

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Playlist "completion / repair": brings the artist-discography quality engine
 * (ArtistItemsViewModel.buildCompleteDiscography) to ONLINE playlists. YouTube playlists routinely carry
 * tracks that are region-blocked, deleted, or otherwise unplayable; those come back from the innertube
 * parser with NO duration (`duration == null`). This pass detects those tracks and swaps each for a
 * playable equivalent found via a YouTube song search — matched by normalized title (reusing
 * [iTunesDiscography.normalizeTitle]) AND a shared artist credit (mirroring the credited()/titleMatch()
 * gate in ArtistItemsViewModel) — preserving the ORIGINAL order and NEVER dropping a track (if no good
 * alternate is found the original is kept in place).
 *
 * Bounded for thermal/battery safety exactly like the artist engine: a hard cap on how many tracks are
 * repaired per playlist, a [Semaphore] of 6 on concurrent network calls, and a per-lookup timeout. Runs
 * entirely off the UI thread (hops to [Dispatchers.IO]).
 */
object PlaylistCompletion {

    // Mirror ArtistItemsViewModel: at most 6 concurrent YouTube calls (more trips YouTube throttling,
    // which then times out lookups and silently drops good hits), a 12s cap per lookup, and a hard cap on
    // how many tracks we repair per playlist so a huge broken playlist can't turn this into a long
    // network/CPU burst (battery/heat). Values mirror ArtistItemsViewModel.kt:238,244,265,273.
    private const val MAX_CONCURRENCY = 6
    private const val LOOKUP_TIMEOUT_MS = 12_000L
    private const val MAX_REPAIRS = 40

    /**
     * Return [songs] with unplayable tracks (`duration == null`) swapped for playable equivalents. Order is
     * preserved; unrepaired/unmatched tracks are kept as-is (never dropped). The returned list is the SAME
     * reference as [songs] when there was nothing to do, so callers can cheaply skip a no-op publish.
     * Safe to call from any dispatcher — it hops to IO internally.
     */
    suspend fun completePlaylist(songs: List<SongItem>): List<SongItem> = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext songs
        // "Needs repair" = no duration => the track failed to resolve to a playable stream on YouTube.
        // Hard-cap the number of repairs so a mostly-broken playlist can't spawn hundreds of searches.
        val flagged = songs.indices.filter { songs[it].duration == null }.take(MAX_REPAIRS)
        if (flagged.isEmpty()) return@withContext songs

        val semaphore = Semaphore(MAX_CONCURRENCY)
        // Guard against introducing a duplicate id (a downstream distinctBy would then DROP a track, which
        // would violate the never-drop invariant). Seeded with every current id, grown as we accept swaps.
        val existingIds = songs.mapTo(HashSet()) { it.id }

        val repairs: List<Pair<Int, SongItem?>> = coroutineScope {
            flagged.map { idx ->
                async {
                    val alt = semaphore.withPermit {
                        // Bound each lookup so one slow/throttled search can't stall the whole repair pass.
                        withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { resolveAlternate(songs[idx]) }
                    }
                    idx to alt
                }
            }.awaitAll()
        }

        val result = songs.toMutableList()
        for ((idx, alt) in repairs) {
            // Never drop a track: only SWAP when we found a different, playable, non-duplicate equivalent.
            if (alt != null && alt.id !in existingIds) {
                result[idx] = alt
                existingIds.add(alt.id)
            }
        }
        result
    }

    /**
     * Resolve one broken [song] to a playable equivalent via YouTube song search. Accept the first hit that
     * is itself playable (carries a duration), is a DIFFERENT id than the broken original, and matches the
     * original by normalized title AND a shared artist credit. Null if none qualifies (caller keeps the
     * original). Mirrors the album lookup in ArtistItemsViewModel but for individual songs.
     */
    private suspend fun resolveAlternate(song: SongItem): SongItem? {
        val target = iTunesDiscography.normalizeTitle(song.title)
        if (target.isBlank()) return null
        val artistName = song.artists.firstOrNull()?.name.orEmpty()
        val query = "${song.title} $artistName".trim()
        if (query.isBlank()) return null

        return YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items
            ?.filterIsInstance<SongItem>()
            ?.firstOrNull { cand ->
                cand.id != song.id &&
                    cand.duration != null &&
                    titleMatch(cand.title, target) &&
                    artistMatch(song, cand)
            }
    }

    /**
     * normalizeTitle-based title match, mirroring ArtistItemsViewModel.titleMatch: an EXACT normalized
     * match, or (only for titles long enough that a substring can't collide with a different song) a fuzzy
     * containment either way.
     */
    private fun titleMatch(candidateTitle: String, target: String): Boolean {
        val yt = iTunesDiscography.normalizeTitle(candidateTitle)
        return yt == target || (target.length >= 6 && (yt.contains(target) || target.contains(yt)))
    }

    /**
     * Shared-artist-credit gate, mirroring ArtistItemsViewModel.credited: the candidate qualifies if any of
     * its artists matches any of the original's artists by id or by a name containment either way. If the
     * original carries no artist metadata at all, accept (the title match already held).
     */
    private fun artistMatch(original: SongItem, candidate: SongItem): Boolean {
        val originals = original.artists
        if (originals.isEmpty()) return true
        return candidate.artists.any { c ->
            originals.any { o ->
                (o.id != null && o.id == c.id) ||
                    c.name.contains(o.name, ignoreCase = true) ||
                    o.name.contains(c.name, ignoreCase = true)
            }
        }
    }
}
