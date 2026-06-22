

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
import timber.log.Timber

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun createFallbackBitmap(): Bitmap =
        createBitmap(64, 64)

    private fun Bitmap.copyIfNeeded(): Bitmap {
        return if (isRecycled) {
            createFallbackBitmap()
        } else {
            try {
                copy(Bitmap.Config.ARGB_8888, false) ?: createFallbackBitmap()
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
            // 1) Try Coil (uses the app's image cache).
            val viaCoil = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                when (val result = context.imageLoader.execute(request)) {
                    is SuccessResult -> result.image.toBitmap().copyIfNeeded()
                    is ErrorResult -> null
                }
            }.getOrNull()
            if (viaCoil != null) return@future viaCoil

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

            direct ?: createFallbackBitmap()
        }
}
