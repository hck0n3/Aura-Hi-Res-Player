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
 * wrapping the song's MediaStore / SAF content URI (or a `file://` path). We use a DEDICATED scheme
 * (instead of pointing Coil straight at `content://…`) so this fetcher is the ONLY component that can
 * claim it — Coil's built-in ContentUriFetcher matches all `content://` URIs and would otherwise try to
 * decode audio bytes as an image.
 *
 * **APIC first.** On API 29+, `ContentResolver.loadThumbnail` often returns a generic / empty MediaStore
 * glyph for newly scanned MP3s even when the file has a real ID3 cover. Returning that success early
 * (pre-0.6.160) meant Aura never read the embedded picture that other apps showed. We decode
 * `MediaMetadataRetriever.embeddedPicture` first and only fall back to `loadThumbnail` when there is
 * no APIC (folder art / album-level MediaStore thumb).
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
        decodeEmbeddedPicture(uri)?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            }.getOrNull()?.takeIf { it.width > 1 && it.height > 1 }?.let { return it }
        }
        return null
    }

    private fun decodeEmbeddedPicture(uri: android.net.Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val opened = openRetriever(retriever, uri)
            if (!opened) return null
            val bytes = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.takeIf { it.width > 1 && it.height > 1 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun openRetriever(retriever: MediaMetadataRetriever, uri: android.net.Uri): Boolean {
        when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return false
                retriever.setDataSource(path)
                return true
            }
            "content" -> {
                runCatching {
                    retriever.setDataSource(context, uri)
                    return true
                }
                // Some OEM / SAF document URIs reject setDataSource(Context, Uri) but accept a FD.
                return context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    true
                } == true
            }
            else -> return false
        }
    }

    class Factory : Fetcher.Factory<CoilUri> {
        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val realUri = parseModelUri(data.toString()) ?: return null
            return LocalAudioArtFetcher(options.context.applicationContext, realUri)
        }
    }

    companion object {
        /** Private scheme so ONLY this fetcher claims these models (never Coil's ContentUriFetcher). */
        const val SCHEME_PREFIX = "localaudioart:"

        /**
         * Wrap a local audio content/file URI as a thumbnail model this fetcher will handle.
         * Optional `#apic1` busts Coil disk entries that cached pre-0.6.160 MediaStore blanks for the
         * same media URI (one-shot cache clear in App also covers upgrades).
         */
        fun uriFor(mediaContentUri: String): String = SCHEME_PREFIX + mediaContentUri + "#apic1"

        /**
         * Strip the private scheme (+ optional `#apic1` cache bust) and return the inner URI string,
         * or null if [raw] is not a local-audio-art model / not a content|file URI.
         */
        fun unwrapModel(raw: String): String? {
            if (!raw.startsWith(SCHEME_PREFIX)) return null
            val inner = raw.removePrefix(SCHEME_PREFIX).substringBefore('#')
            if (!inner.startsWith("content:") && !inner.startsWith("file:")) return null
            return inner
        }

        /**
         * Parse `localaudioart:<uri>[#fragment]` into the underlying media URI.
         * Accepts legacy models without `#apic1` so DB rows from older scans still resolve.
         */
        fun parseModelUri(raw: String): android.net.Uri? {
            val inner = unwrapModel(raw) ?: return null
            return runCatching { android.net.Uri.parse(inner) }.getOrNull()
        }
    }
}
