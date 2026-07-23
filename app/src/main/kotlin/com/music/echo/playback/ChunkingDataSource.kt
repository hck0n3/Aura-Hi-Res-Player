package iad1tya.echo.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * DOWNLOAD-ONLY throttling bypass (port of Echo-Music 5.2.81's ChunkingDataSource, hardened).
 *
 * googlevideo shapes long single-connection transfers down to ~streaming speed; re-opening the
 * upstream source every [chunkSize] bytes via DataSpec position/length (→ HTTP Range headers) keeps
 * each connection short and fast. Wired ONLY into DownloadUtil's factory chain — the streaming path
 * (MusicService) is untouched, exactly like upstream.
 *
 * Two deliberate deviations from upstream:
 *  1. read() does NOT swallow exceptions. Upstream caught every Exception and mapped it to
 *     RESULT_END_OF_INPUT, so a transient network error silently TRUNCATED the download and
 *     DownloadManager marked it STATE_COMPLETED. Here only the two genuine end-of-stream signals —
 *     416 (Range Not Satisfiable) and DataSourceException(POSITION_OUT_OF_RANGE) from the
 *     next-chunk open — become end-of-input; every other error propagates so DownloadManager's
 *     retry machinery handles it.
 *  2. Host gate in open(): only googlevideo hosts throttle this way. Qobuz FLAC / Saavn CDN
 *     downloads go through a pure 1:1 passthrough (no chunking, no Range games) so their behavior
 *     stays byte-identical.
 */
class ChunkingDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var bytesToRead: Long = C.LENGTH_UNSET.toLong()
    private var bytesReadTotal: Long = 0
    private var isOpened = false
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.bytesReadTotal = 0
        this.bytesToRead = dataSpec.length
        this.isOpened = true

        // HOST GATE (hardening #2): chunk ONLY googlevideo — the sole CDN that throttles long
        // single connections. Everything else (Qobuz FLAC, Saavn CDN) is delegated 1:1.
        passthrough = dataSpec.uri.host?.contains("googlevideo", ignoreCase = true) != true
        if (passthrough) {
            return upstream.open(dataSpec)
        }

        openNextChunk()

        return bytesToRead
    }

    private fun openNextChunk() {
        val currentDataSpec = this.dataSpec ?: throw IOException("DataSpec is null")
        val position = currentDataSpec.position + bytesReadTotal

        val length = if (bytesToRead == C.LENGTH_UNSET.toLong()) {
            chunkSize
        } else {
            val remaining = bytesToRead - bytesReadTotal
            if (remaining == 0L) return
            minOf(chunkSize, remaining)
        }

        val chunkDataSpec = currentDataSpec.buildUpon()
            .setPosition(position)
            .setLength(length)
            .build()

        upstream.open(chunkDataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!isOpened) return C.RESULT_END_OF_INPUT
        if (passthrough) return upstream.read(buffer, offset, readLength)
        if (bytesToRead != C.LENGTH_UNSET.toLong() && bytesReadTotal >= bytesToRead) {
            return C.RESULT_END_OF_INPUT
        }

        // HARDENING #1: no blanket try/catch around the reads — a transient failure must PROPAGATE
        // (DownloadManager retries it), never silently truncate a download into STATE_COMPLETED.
        val bytes = upstream.read(buffer, offset, readLength)

        if (bytes == C.RESULT_END_OF_INPUT) {
            // Current chunk exhausted → close it and open the next Range slice at our position.
            upstream.close()
            try {
                openNextChunk()
            } catch (e: InvalidResponseCodeException) {
                // 416 Range Not Satisfiable: we asked past the end of the file → genuine end of stream.
                if (e.responseCode == 416) {
                    return C.RESULT_END_OF_INPUT
                }
                throw e
            } catch (e: DataSourceException) {
                if (e.reason == DataSourceException.POSITION_OUT_OF_RANGE) {
                    return C.RESULT_END_OF_INPUT
                }
                throw e
            }
            val newBytes = upstream.read(buffer, offset, readLength)
            if (newBytes == C.RESULT_END_OF_INPUT) {
                return C.RESULT_END_OF_INPUT
            }
            bytesReadTotal += newBytes
            return newBytes
        }
        bytesReadTotal += bytes
        return bytes
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        isOpened = false
        upstream.close()
    }
}

class ChunkingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val chunkSize: Long = 5L * 1024 * 1024 // 5MB chunks
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return ChunkingDataSource(upstreamFactory.createDataSource(), chunkSize)
    }
}
