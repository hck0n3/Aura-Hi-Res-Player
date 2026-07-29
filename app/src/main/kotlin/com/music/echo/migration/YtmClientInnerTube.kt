package iad1tya.echo.music.migration

import com.aura.migration.model.YtmCandidate
import com.aura.migration.resolver.YtmClient
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.delay
import timber.log.Timber

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
        // Aura's addToPlaylist is ONE request per video, so the module's "50 per call" warning becomes
        // "50 calls, then a longer breather". The small per-item delay keeps a 5000-track import from
        // hammering the endpoint back-to-back (thermal/rate discipline); order is preserved because we
        // append in list order. A single failed add must never tumble the batch (module rule) — the
        // post-import playlist sync reconciles whatever really landed remotely.
        videoIds.chunked(ADD_CHUNK).forEachIndexed { chunkIndex, chunk ->
            if (chunkIndex > 0) delay(ADD_CHUNK_PAUSE_MS)
            chunk.forEachIndexed { i, videoId ->
                if (chunkIndex > 0 || i > 0) delay(ADD_ITEM_DELAY_MS)
                YouTube.addToPlaylist(playlistId, videoId).onFailure {
                    Timber.tag("MigrationYtm").w(it, "addToPlaylist failed for %s", videoId)
                }
            }
        }
    }

    private companion object {
        const val ADD_CHUNK = 50
        const val ADD_ITEM_DELAY_MS = 80L
        const val ADD_CHUNK_PAUSE_MS = 1_500L
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
