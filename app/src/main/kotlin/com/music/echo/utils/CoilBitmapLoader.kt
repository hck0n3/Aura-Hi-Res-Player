

package iad1tya.echo.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.BitmapLoader
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun createFallbackBitmap(): Bitmap =
        createBitmap(64, 64)

    // Cap the artwork used for the media notification / lockscreen / Android Auto. The full-res cover is
    // 1200x1200 (~5.7 MB as ARGB_8888); handing a bitmap that big to NotificationManager.notify() blows the
    // Binder transaction limit -> TransactionTooLargeException crash (seen on Xiaomi/Android 16). 512px is
    // plenty for a notification/lockscreen icon and keeps the parcel small.
    private fun Bitmap.downscaledIfLarge(): Bitmap {
        val maxSide = maxOf(width, height)
        if (maxSide <= MAX_ARTWORK_PX || maxSide <= 0) return this
        val scale = MAX_ARTWORK_PX.toFloat() / maxSide
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(this, w, h, true)
        } catch (e: Exception) {
            this
        }
    }

    private fun Bitmap.copyIfNeeded(): Bitmap {
        return if (isRecycled) {
            createFallbackBitmap()
        } else {
            try {
                downscaledIfLarge().copy(Bitmap.Config.ARGB_8888, false) ?: createFallbackBitmap()
            } catch (e: Exception) {
                createFallbackBitmap()
            }
        }
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                bitmap?.copyIfNeeded() ?: createFallbackBitmap()
            } catch (e: Exception) {
                Timber.tag("CoilBitmapLoader").w(e, "Failed to decode bitmap data")
                createFallbackBitmap()
            }
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            // #54 — BOUND THE WHOLE LOAD. media3's MediaSessionLegacyStub publishes the metadata with a NULL
            // bitmap as soon as the track changes and only republishes WITH artwork when this future completes.
            // Nothing here was bounded (Coil has no timeout; the HTTP fallback alone allows 10s+10s), so on a
            // slow mobile link the car head unit showed the old thumbnail until the future finally resolved —
            // the reported "the thumbnail doesn't update until the song is halfway through". A missed cover is
            // a far smaller problem than a stale one, so cap it and let the next track try again.
            // On timeout FAIL the future — never hand back a placeholder. createFallbackBitmap() is a fully
            // transparent 64x64, and media3 wraps this loader in a CacheBitmapLoader that memoises the last
            // (uri -> future) pair: returning the blank would CACHE it for that artwork, so the car head unit
            // and lockscreen would show an empty box for the whole track. A failed future instead leaves the
            // previous metadata in place, which is strictly better than painting a blank.
            withTimeoutOrNull(ARTWORK_LOAD_TIMEOUT_MS) {
                loadBitmapInner(uri)
            } ?: throw java.util.concurrent.TimeoutException("Artwork load exceeded ${ARTWORK_LOAD_TIMEOUT_MS}ms")
        }

    private suspend fun loadBitmapInner(uri: Uri): Bitmap {
            // 1) Try Coil (uses the app's image cache).
            val viaCoil = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    // Ask for the size we actually USE. Without this Coil decodes at native resolution: the
                    // session artworkUri is resize(1200,1200) (models/MediaMetadata.kt), i.e. ~5.7 MB as
                    // ARGB_8888, downloaded and decoded in full only to be scaled to 384 right after. That is
                    // ~10x the bytes over the air plus three large allocations per track, on a backgrounded and
                    // throttled process. In-app artwork is unaffected — Thumbnail.kt re-applies its own
                    // resize(1200,1200) independently.
                    .size(MAX_ARTWORK_PX)
                    .allowHardware(false)
                    .build()
                when (val result = context.imageLoader.execute(request)) {
                    is SuccessResult -> result.image.toBitmap().copyIfNeeded()
                    is ErrorResult -> null
                }
            }.getOrNull()
            if (viaCoil != null) return viaCoil

            // 2) Fallback: download the bytes directly. Coil can fail to run in the MediaSession
            //    service context (singleton imageLoader / network component not ready), which left
            //    the media notification with no large icon — only the app icon showed. A plain
            //    HTTP fetch makes the cover load reliably for the notification.
            val direct = runCatching {
                when (uri.scheme?.lowercase()) {
                    "http", "https" -> {
                        val conn = (java.net.URL(uri.toString()).openConnection()
                                as java.net.HttpURLConnection).apply {
                            connectTimeout = 10_000
                            readTimeout = 10_000
                            doInput = true
                        }
                        try {
                            conn.inputStream.use { BitmapFactory.decodeStream(it) }?.copyIfNeeded()
                        } finally {
                            conn.disconnect()
                        }
                    }
                    else -> context.contentResolver.openInputStream(uri)
                        ?.use { BitmapFactory.decodeStream(it) }?.copyIfNeeded()
                }
            }.getOrElse {
                Timber.tag("CoilBitmapLoader").w(it, "Direct artwork fetch failed")
                null
            }

            return direct ?: createFallbackBitmap()
    }

    private companion object {
        // Max side (px) for the media-notification/lockscreen/Android-Auto artwork so the metadata parcel stays
        // well under the ~1 MB Binder transaction limit. 512×512×ARGB_8888 = 1,048,576 B = the ENTIRE Binder
        // buffer, which on high-density Xiaomi/MIUI panels (where the platform's 320dp downscale doesn't kick
        // in) can breach it and kill the media service → the app disappears from Android Auto. 384×384×4 ≈
        // 576 KB leaves safe headroom and is still crisp for a lockscreen/car icon. In-app full-res is unaffected.
        const val MAX_ARTWORK_PX = 384

        /**
         * Hard cap on a single artwork load (#54). media3 publishes metadata with a null bitmap immediately
         * and republishes when this resolves, so an unbounded load means the previous track's cover stays on
         * screen — worse than briefly showing none.
         *
         * Must stay ABOVE the direct-HTTP fallback's own budget (10s connect + 10s read below). At 6s that
         * fallback was unreachable by construction — the very path that exists for when Coil can't run in the
         * MediaSession service context could never finish, so it always degraded to a failure.
         */
        const val ARTWORK_LOAD_TIMEOUT_MS = 22_000L
    }
}
