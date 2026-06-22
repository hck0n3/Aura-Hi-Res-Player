package iad1tya.echo.music.playback

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import iad1tya.echo.music.eq.audio.LoudnessAnalyzer
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer

/**
 * Best-effort: measures the true integrated loudness (ITU-R BS.1770) of a freshly DOWNLOADED track
 * and returns its `loudnessDb` (the app's per-track convention vs the ~-14 LUFS reference), so the
 * volume normalizer is accurate even when YouTube's metadata is missing or off. Returns null on ANY
 * problem (decoder/codec/cache) — the caller then just keeps the existing value, so this can never
 * break playback. Heavy lifting (decode + analyze) is for the caller's background dispatcher.
 */
object DownloadLoudnessMeasurer {

    private const val TAG = "DownloadLoudness"

    fun measureLoudnessDb(
        downloadCache: SimpleCache,
        mediaId: String,
        uri: Uri,
        contentLength: Long,
    ): Double? {
        var tmp: File? = null
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            // 1) Pull the fully-cached bytes out to a temp file (most reliable input for MediaExtractor).
            tmp = File.createTempFile("loud_", ".bin")
            val copied = copyFromCache(downloadCache, mediaId, uri, tmp)
            if (copied <= 0L) {
                Timber.tag(TAG).w("No cached bytes for %s (copied=%d) — skip", mediaId, copied)
                return null
            }
            Timber.tag(TAG).d("Copied %d bytes for %s", copied, mediaId)

            // 2) Find the audio track.
            extractor = MediaExtractor().apply { setDataSource(tmp.absolutePath) }
            var trackIndex = -1
            var inFormat: MediaFormat? = null
            for (t in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(t)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = t; inFormat = fmt; break
                }
            }
            if (trackIndex < 0 || inFormat == null) {
                Timber.tag(TAG).w("No audio track for %s", mediaId); return null
            }
            extractor.selectTrack(trackIndex)
            val mime = inFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // 3) Decode to PCM and feed the loudness meter.
            val analyzer = LoudnessAnalyzer(sampleRate, channels)
            codec = MediaCodec.createDecoderByType(mime).apply { configure(inFormat, null, null, 0); start() }
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var floatBuf = FloatArray(0)

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shorts = outBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val n = shorts.remaining()
                        if (floatBuf.size < n) floatBuf = FloatArray(n)
                        var k = 0
                        while (k < n) { floatBuf[k] = shorts.get(k) / 32768f; k++ }
                        val frames = if (channels > 0) n / channels else n
                        analyzer.process(floatBuf, frames)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                }
            }

            val lufs = analyzer.integratedLufs
            if (lufs == null) {
                Timber.tag(TAG).w("Analyzer produced no loudness for %s", mediaId); return null
            }
            val loudnessDb = LoudnessAnalyzer.lufsToLoudnessDb(lufs)
            // Sanity clamp: ignore implausible measurements (corrupt/odd files) so we never push a wild gain.
            if (loudnessDb < -30.0 || loudnessDb > 30.0) {
                Timber.tag(TAG).w("Implausible loudnessDb=%.2f (lufs=%.2f) for %s — skip", loudnessDb, lufs, mediaId)
                return null
            }
            Timber.tag(TAG).i("Measured %s: %.2f LUFS -> loudnessDb=%.2f", mediaId, lufs, loudnessDb)
            return loudnessDb
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Loudness measure failed for %s — keeping existing value", mediaId)
            return null
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try { tmp?.delete() } catch (_: Exception) {}
        }
    }

    /** Read the whole cached content for [mediaId] into [dest]. Returns bytes copied (0 on miss). */
    private fun copyFromCache(cache: SimpleCache, mediaId: String, uri: Uri, dest: File): Long {
        val ds = CacheDataSource.Factory()
            .setCache(cache)
            // Cache-only read: a FileDataSource upstream is harmless (never used for a complete download)
            // and avoids any network. Keyed by mediaId (the same key the player/cache uses).
            .setUpstreamDataSourceFactory(FileDataSource.Factory())
            .createDataSource()
        var total = 0L
        try {
            ds.open(DataSpec.Builder().setUri(uri).setKey(mediaId).build())
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = ds.read(buf, 0, buf.size)
                    if (r == C.RESULT_END_OF_INPUT) break
                    out.write(buf, 0, r)
                    total += r
                }
            }
        } finally {
            try { ds.close() } catch (_: Exception) {}
        }
        return total
    }
}
