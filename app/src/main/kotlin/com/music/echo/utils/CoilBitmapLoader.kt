package iad1tya.echo.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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

    /**
     * Last-resort plate when a bitmap is required and the real cover is gone (recycled, undecodable,
     * or Coil + direct fetch both failed). MUST NOT draw the Aura launcher: MediaStyle uses this as
     * the notification LARGE artwork, so a brand icon here looks like the album cover.
     *
     * Do not throw on a failed URI fetch: media3 1.10.1 leaves artwork null and MediaStyle then
     * substitutes the smallIcon (launcher) into that slot (registry #63 / #137). A dark opaque
     * square is honest empty art. Keep [MAX_ARTWORK_PX] at 384 (#26 / #54).
     */
    private fun createFallbackBitmap(): Bitmap {
        val bmp = createBitmap(MAX_ARTWORK_PX, MAX_ARTWORK_PX)
        Canvas(bmp).drawColor(0xFF121212.toInt())
        return bmp
    }

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
            //  3. Throwing does NOT restore the previous cover in media3 1.10.1 — it leaves a null
            //     artwork until a later success. MediaStyle then substitutes the smallIcon (Aura launcher)
            //     as the LARGE notification thumbnail. Prefer an opaque dark plate over that brand icon.
            val raw = uri.toString()
            val localMediaUri = iad1tya.echo.music.utils.coil.LocalAudioArtFetcher.parseModelUri(raw)
            // Pass the STRING model for localaudioart so Coil's LocalAudioArtMapper runs — never hand
            // android.net.Uri("localaudioart:…") to ContentResolver (log: FileNotFoundException
            // "No content provider: localaudioart:content://…").
            val coilData: Any =
                if (localMediaUri != null) raw else smallArtworkUri(uri)
            val coilResult = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(coilData)
                    .size(MAX_ARTWORK_PX, MAX_ARTWORK_PX)
                    .allowHardware(false)
                    .build()
                when (val result = context.imageLoader.execute(request)) {
                    is SuccessResult -> result.image.toBitmap().copyIfNeeded()
                    is ErrorResult -> {
                        Timber.tag("CoilBitmapLoader").d("Coil error: ${result.throwable?.message?.take(100)}")
                        null
                    }
                }
            }.getOrElse {
                Timber.tag("CoilBitmapLoader").d("Coil execution failed: ${it.message?.take(100)}")
                null
            }
            if (coilResult != null) return@future coilResult

            // Direct fallback when Coil cannot run in the media-session / service context.
            val direct = runCatching {
                if (localMediaUri != null) {
                    iad1tya.echo.music.utils.coil.LocalAudioArtFetcher(
                        context.applicationContext,
                        localMediaUri,
                    ).decodeBitmap()?.copyIfNeeded()
                } else {
                    when (uri.scheme?.lowercase()) {
                        "http", "https" -> {
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
                        "content", "file", "android.resource" ->
                            runCatching {
                                context.contentResolver.openInputStream(uri)
                                    ?.use { BitmapFactory.decodeStream(it) }?.copyIfNeeded()
                            }.getOrElse { e ->
                                // Silent failure for orphan/invalid URIs (e.g., localaudioart:content://...)
                                Timber.tag("CoilBitmapLoader").d("ContentResolver failed for $uri: ${e.message?.take(100)}")
                                null
                            }
                        else -> null
                    }
                }
            }.getOrElse {
                Timber.tag("CoilBitmapLoader").w(it, "Direct artwork fetch failed")
                null
            }

            // Last resort: opaque dark plate, NEVER the Aura launcher. CacheBitmapLoader memoizes this
            // future per URI; a brand icon here became the song's "cover" in the notification (owner
            // report). A dark square is honest empty art and stops MediaStyle from substituting smallIcon.
            return@future direct ?: createFallbackBitmap()
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
