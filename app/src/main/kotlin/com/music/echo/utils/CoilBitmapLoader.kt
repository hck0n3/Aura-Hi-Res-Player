

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
import iad1tya.echo.music.ui.utils.resize
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import timber.log.Timber

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun createFallbackBitmap(): Bitmap =
        createBitmap(64, 64)

    /**
     * Rewrite the artwork URL so the CDN returns a SMALL image, instead of downloading the 1200x1200 one the
     * session metadata carries and shrinking it locally.
     *
     * This is the part that actually helps the "the cover doesn't update until the song is halfway through"
     * report: on a slow mobile link the cost is the DOWNLOAD, and Coil's `.size()` only bounds the DECODE —
     * the fetcher requests whatever the URL says. Reuses the app's own [resize] so the per-host rules stay in
     * one place (googleusercontent gets `=w384-h384`, i.ytimg falls to sddefault, anything else is untouched).
     */
    private fun smallArtworkUri(uri: Uri): Any =
        runCatching {
            val s = uri.toString()
            if (s.startsWith("http", ignoreCase = true)) s.resize(MAX_ARTWORK_PX, MAX_ARTWORK_PX) else uri
        }.getOrDefault(uri)

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
            // #54 — media3's MediaSessionLegacyStub publishes the metadata with a NULL bitmap as soon as the
            // track changes and only republishes WITH artwork when this future completes.
            // NO overall timeout. An earlier attempt wrapped this in withTimeoutOrNull and threw on expiry;
            // an audit proved that made things WORSE, three ways:
            //  1. `runCatching` around the Coil phase below catches Throwable — including the
            //     TimeoutCancellationException — so the deadline firing did not abort anything: it fell
            //     through into the blocking fallback, ran it to completion, and THEN discarded a cover it had
            //     successfully fetched.
            //  2. That fallback is plain blocking I/O (HttpURLConnection + BitmapFactory) with no suspension
            //     point, so a coroutine deadline cannot interrupt it regardless.
            //  3. Failing the future does NOT restore the previous cover: verified in the media3 1.10.1
            //     bytecode, MediaSessionLegacyStub publishes the new metadata with a NULL bitmap immediately
            //     and its onFailure only logs. So expiring guaranteed the correct cover NEVER arrived — and a
            //     head unit that keeps painting its last bitmap then shows exactly the reported stale cover.
            // Slow is better than never here; the real lever is fetching a smaller image (below), not a cap.
            loadBitmapInner(uri)
        }

    private suspend fun loadBitmapInner(uri: Uri): Bitmap {
            // 1) Try Coil (uses the app's image cache).
            val viaCoil = runCatching {
                val request = ImageRequest.Builder(context)
                    // Ask the CDN for a small image instead of downloading the 1200x1200 one and shrinking it
                    // here. This is the half that actually helps the reported bug: on a slow car link the
                    // latency is the DOWNLOAD, and Coil's `.size()` only bounds DECODING — the fetcher GETs
                    // whatever the URL says. Rewriting the URL is what changes the bytes on the wire.
                    .data(smallArtworkUri(uri))
                    // Still cap the decode, for the sources whose URL cannot be rewritten.
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
                        // Small URL here too — same reason as the Coil phase above.
                        val conn = (java.net.URL(smallArtworkUri(uri).toString()).openConnection()
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

    }
}
