

package iad1tya.echo.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "format")
data class FormatEntity(
    @PrimaryKey val id: String,
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double? = null,
    // Loudness (dB vs the same -14 LUFS reference as [loudnessDb]) MEASURED at playback time for tracks
    // that ship NO loudness metadata (local files, Saavn, some YouTube). Measured once, cached here, so
    // the whole library levels to one consistent loudness. Nullable: null = not yet measured.
    val measuredLoudnessDb: Double? = null,
    @Deprecated("playbackTrackingUrl should be retrieved from a fresh player request")
    val playbackUrl: String?
)
