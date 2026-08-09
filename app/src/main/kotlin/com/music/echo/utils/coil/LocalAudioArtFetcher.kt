package iad1tya.echo.music.utils.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.request.Options
import coil3.Uri as CoilUri

/**
 * Coil model for local embedded/folder art. Using a dedicated type (not a nested
 * `localaudioart:content://…` string) avoids Coil3 Uri parsing mangling the inner MediaStore URI
 * and avoids ContentResolver treating the private scheme as a content provider.
 */
data class LocalAudioArtModel(
    val mediaUri: Uri,
    /** Stable Coil memory/disk cache key (includes optional cache-bust fragment). */
    val cacheKey: String,
)

/**
 * Coil fetcher that renders the EMBEDDED cover art of a local audio file.
 *
 * Local songs set their `thumbnailUrl` to [uriFor]`(mediaContentUri)`. Prefer the encoded form
 * `localaudioart://a/<urlencoded>#apic2` so Coil never sees a nested `content://` inside the scheme-specific
 * part. Legacy `localaudioart:content://…` models from older scans remain accepted by [unwrapModel].
 *
 * **APIC first.** On API 29+, `ContentResolver.loadThumbnail` often returns a generic / empty MediaStore
 * glyph for newly scanned MP3s even when the file has a real ID3 cover. We decode
 * `MediaMetadataRetriever.embeddedPicture` first and only fall back to `loadThumbnail` / folder art.
 */
class LocalAudioArtFetcher(
    private val context: Context,
    private val uri: Uri,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bitmap = loadEmbeddedArt() ?: return null
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    /** Blocking decode for media-session / notification loaders that are not on Coil's pipeline. */
    fun decodeBitmap(): Bitmap? = loadEmbeddedArt()

    private fun loadEmbeddedArt(): Bitmap? {
        decodeEmbeddedPicture(uri)?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            }.getOrNull()?.takeIf { it.width > 1 && it.height > 1 }?.let { return it }
        }

        findFolderArt(uri)?.let { return it }

        return null
    }

    private fun decodeEmbeddedPicture(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        var pfd: android.os.ParcelFileDescriptor? = null
        return try {
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path ?: return null
                    retriever.setDataSource(path)
                }
                "content" -> {
                    var success = false
                    try {
                        retriever.setDataSource(context, uri)
                        success = true
                    } catch (_: Exception) {
                        // Some OEM / SAF document URIs reject setDataSource(Context, Uri)
                    }
                    if (!success) {
                        pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            retriever.setDataSource(pfd.fileDescriptor)
                        } else {
                            return null
                        }
                    }
                }
                else -> return null
            }
            val bytes = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.takeIf { it.width > 1 && it.height > 1 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { pfd?.close() }
            runCatching { retriever.release() }
        }
    }

    private fun findFolderArt(uri: Uri): Bitmap? {
        val filePath = when (uri.scheme) {
            "file" -> uri.path
            "content" -> {
                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)
                        } else null
                    }
                }.getOrNull()
            }
            else -> null
        } ?: return null

        val parentDir = java.io.File(filePath).parentFile ?: return null
        if (!parentDir.exists() || !parentDir.isDirectory) return null

        val artNames = listOf(
            "cover.jpg", "cover.png", "folder.jpg", "folder.png",
            "album.jpg", "album.png", "front.jpg", "front.png",
        )
        for (name in artNames) {
            val artFile = java.io.File(parentDir, name)
            if (artFile.exists() && artFile.canRead()) {
                val bmp = runCatching { BitmapFactory.decodeFile(artFile.absolutePath) }.getOrNull()
                if (bmp != null && bmp.width > 1 && bmp.height > 1) {
                    return bmp
                }
            }
        }
        return null
    }

    class ModelFetcherFactory : Fetcher.Factory<LocalAudioArtModel> {
        override fun create(data: LocalAudioArtModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return LocalAudioArtFetcher(options.context.applicationContext, data.mediaUri)
        }
    }

    /** Legacy CoilUri factory — maps mangled/legacy private-scheme URIs into this fetcher. */
    class Factory : Fetcher.Factory<CoilUri> {
        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val realUri = parseModelUri(data.toString()) ?: return null
            return LocalAudioArtFetcher(options.context.applicationContext, realUri)
        }
    }

    class StringMapper : Mapper<String, LocalAudioArtModel> {
        override fun map(data: String, options: Options): LocalAudioArtModel? {
            val media = parseModelUri(data) ?: return null
            return LocalAudioArtModel(media, data)
        }
    }

    class CoilUriMapper : Mapper<CoilUri, LocalAudioArtModel> {
        override fun map(data: CoilUri, options: Options): LocalAudioArtModel? {
            val raw = data.toString()
            val media = parseModelUri(raw) ?: return null
            return LocalAudioArtModel(media, raw)
        }
    }

    class ModelKeyer : Keyer<LocalAudioArtModel> {
        override fun key(data: LocalAudioArtModel, options: Options): String = data.cacheKey
    }

    companion object {
        /** Private scheme so ONLY this fetcher claims these models (never Coil's ContentUriFetcher). */
        const val SCHEME_PREFIX = "localaudioart:"

        /**
         * Wrap a local audio content/file URI as a thumbnail model this fetcher will handle.
         *
         * Encoded form (`localaudioart://a/<urlencoded>#apic2`) avoids Coil3 / Android Uri parsers
         * treating the nested `content://` as a second scheme. `#apic2` busts Coil disk entries that
         * cached blanks or failed loads for older model strings.
         */
        fun uriFor(mediaContentUri: String): String =
            SCHEME_PREFIX + "//a/" + Uri.encode(mediaContentUri) + "#apic2"

        /**
         * Strip the private scheme (+ optional fragment) and return the inner media URI string,
         * or null if [raw] is not a local-audio-art model.
         */
        fun unwrapModel(raw: String): String? {
            if (!raw.startsWith(SCHEME_PREFIX)) return null
            val body = raw.removePrefix(SCHEME_PREFIX).substringBefore('#')
            when {
                body.startsWith("//a/") -> {
                    val decoded = Uri.decode(body.removePrefix("//a/"))
                    if (decoded.startsWith("content:") || decoded.startsWith("file:")) return decoded
                    return null
                }
                // Legacy pre-0.6.164: `localaudioart:content://…` / `localaudioart:file://…`
                body.startsWith("content:") || body.startsWith("file:") -> return body
                else -> return null
            }
        }

        fun parseModelUri(raw: String): Uri? {
            val inner = unwrapModel(raw) ?: return null
            return runCatching { Uri.parse(inner) }.getOrNull()
        }
    }
}
