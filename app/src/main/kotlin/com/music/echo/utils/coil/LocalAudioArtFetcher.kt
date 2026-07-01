package iad1tya.echo.music.utils.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.Uri as CoilUri

/**
 * Coil fetcher that renders the EMBEDDED cover art of a local audio file.
 *
 * Local songs set their `thumbnailUrl` to [uriFor]`(mediaContentUri)` — a private `localaudioart:` scheme
 * wrapping the song's own MediaStore media URI. We use a DEDICATED scheme (instead of pointing Coil straight
 * at the `content://…/audio/media/{id}` URI) so this fetcher is the ONLY component that can ever claim it —
 * Coil's built-in ContentUriFetcher matches all `content://` URIs and could otherwise intercept it first and
 * fail to decode the audio bytes as an image. The old `content://…/audio/albumart/{albumId}` scheme returned
 * null/blank on Android 10+ and was only per-album, which is why local thumbnails often didn't show.
 *
 * On API 29+ we use `ContentResolver.loadThumbnail` (fast, decoded/cached by the platform); older devices
 * fall back to `MediaMetadataRetriever.embeddedPicture`. Returns null (→ Coil shows the placeholder) when the
 * file has no embedded art, so it never crashes or blocks on art-less songs. All artwork in the app — Compose
 * lists/player AND the media3 notification/lockscreen (via CoilBitmapLoader) — flows through this loader.
 */
class LocalAudioArtFetcher(
    private val context: Context,
    private val uri: android.net.Uri,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bitmap = loadEmbeddedArt() ?: return null
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    private fun loadEmbeddedArt(): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On success this returns the art; on failure runCatching swallows it and we fall through to the
            // MediaMetadataRetriever path below (a stale/permission-denied URI still gets a second chance).
            runCatching {
                return context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory : Fetcher.Factory<CoilUri> {
        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val raw = data.toString()
            if (!raw.startsWith(SCHEME_PREFIX)) return null
            val realUri = runCatching { android.net.Uri.parse(raw.removePrefix(SCHEME_PREFIX)) }.getOrNull()
                ?: return null
            if (realUri.scheme != "content") return null
            return LocalAudioArtFetcher(options.context.applicationContext, realUri)
        }
    }

    companion object {
        /** Private scheme so ONLY this fetcher claims these models (never Coil's ContentUriFetcher). */
        const val SCHEME_PREFIX = "localaudioart:"

        /** Wrap a local song's MediaStore media content URI as a thumbnail model this fetcher will handle. */
        fun uriFor(mediaContentUri: String): String = SCHEME_PREFIX + mediaContentUri
    }
}
