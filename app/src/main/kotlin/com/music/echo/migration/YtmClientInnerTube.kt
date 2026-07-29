package iad1tya.echo.music.migration

import com.aura.migration.model.YtmCandidate
import com.aura.migration.resolver.YtmClient
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem

/**
 * Real [YtmClient] over Aura's own InnerTube client. This is the ONE piece the migration module left as
 * a .template — it lives in :app (not :migration) so the module stays backend-agnostic and testable with
 * a fake, exactly as its design intends.
 *
 * The song filter is Aura's OWN battle-tested constant (YouTube.SearchFilter.FILTER_SONG), not the
 * template's hardcoded base64 — the template explicitly says to use the app's proven one.
 */
class YtmClientInnerTube : YtmClient {

    override suspend fun searchSongs(query: String, limit: Int): List<YtmCandidate> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items.orEmpty()
            .filterIsInstance<SongItem>()
            .take(limit)
            .map { it.toCandidate(isSong = true) }

    override suspend fun searchAll(query: String, limit: Int): List<YtmCandidate> =
        // Last resort (module penalizes non-songs): the VIDEO filter surfaces items the Songs tab omits.
        // They resolve as SongItem carrying a musicVideoType; flag isSong=false so the scorer discounts
        // them, since this path is only reached when the song search found nothing.
        YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
            ?.items.orEmpty()
            .filterIsInstance<SongItem>()
            .take(limit)
            .map { it.toCandidate(isSong = false) }

    override suspend fun createPlaylist(name: String, description: String?): String =
        // Aura's createPlaylist takes only a title (it wraps InnerTube.createPlaylist); description is
        // not part of the create call. runBlocking inside is the innertube signature — called off-Main.
        YouTube.createPlaylist(name)

    override suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) {
        // Batched, one id at a time is what Aura's addToPlaylist exposes; a huge single request fails
        // silently on YTM. Order is preserved because we append in list order. A single failure must not
        // tumble the whole import (module rule), so each add is isolated.
        videoIds.forEach { videoId ->
            runCatching { YouTube.addToPlaylist(playlistId, videoId).getOrThrow() }
        }
    }

    private fun SongItem.toCandidate(isSong: Boolean) = YtmCandidate(
        videoId = id,
        title = title,
        artists = artists.map { it.name },
        album = album?.name,
        // SongItem.duration is SECONDS (Int) — the scorer's duration bands are in ms.
        durationMs = duration?.times(1000L),
        explicit = explicit,
        isSong = isSong,
        thumbnailUrl = thumbnail,
    )
}
