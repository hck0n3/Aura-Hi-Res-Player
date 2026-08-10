package iad1tya.echo.music.utils

import android.content.Context
import com.music.innertube.pages.ArtistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Small best-effort on-disk JSON cache of the last-known [ArtistPage] per artistId. It lets a COLD first
 * entry to an artist render its shelves (e.g. "Canciones más escuchadas") instantly from the previous
 * session while a fresh copy is fetched from YouTube in the background — instead of showing nothing until
 * the slow live fetch returns (which is why the top-songs shelf used to only appear on the 2nd entry).
 *
 * Every read/write/parse is wrapped so a corrupt or stale file can never crash or block the artist screen.
 */
object ArtistPageCache {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    // Show a persisted page as an instant placeholder for up to this long; older copies are ignored (and
    // pruned) so a very stale page is never shown. The live fetch refreshes it on every entry anyway.
    private const val TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

    // v2: "Aparece en" now merges iTunes songs + multi-storefront + YT feat. searches. Bumping the
    // folder invalidates pages that cached the older, thinner guest shelf for up to 30 days.
    private fun dir(context: Context): File =
        File(context.cacheDir, "artist_pages_v2").apply { mkdirs() }

    private fun fileFor(context: Context, artistId: String): File =
        File(dir(context), artistId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    /** Last-known page for [artistId], or null if none is cached / it is too old / it fails to parse. */
    suspend fun load(context: Context, artistId: String): ArtistPage? = withContext(Dispatchers.IO) {
        runCatching {
            val f = fileFor(context, artistId)
            if (!f.exists()) return@runCatching null
            if (System.currentTimeMillis() - f.lastModified() > TTL_MS) {
                f.delete()
                return@runCatching null
            }
            json.decodeFromString(ArtistPage.serializer(), f.readText())
        }.getOrNull()
    }

    /** Persist [page] for [artistId] so a cold first entry next session can show it immediately. */
    suspend fun save(context: Context, artistId: String, page: ArtistPage) {
        withContext(Dispatchers.IO) {
            runCatching {
                fileFor(context, artistId).writeText(json.encodeToString(ArtistPage.serializer(), page))
            }
        }
    }
}
