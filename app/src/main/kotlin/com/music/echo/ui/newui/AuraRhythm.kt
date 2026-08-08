package iad1tya.echo.music.ui.newui

import android.media.audiofx.Visualizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.C
import kotlinx.coroutines.isActive
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Low-rate waveform energy for the full-player ground. Capture is capped well below the device
 * max rate and the Compose side only samples once per frame via [withFrameNanos] — never a busy
 * loop and never while Performance Mode is on (callers pass [enabled] = false).
 *
 * Does not touch the audio path / Superpowered / EQ. Visualizer is observation-only.
 */
@Composable
fun rememberAuraRhythmLevel(
    audioSessionId: Int,
    enabled: Boolean,
    playing: Boolean,
): State<Float> {
    val level = remember { mutableFloatStateOf(0f) }
    var raw by remember { mutableFloatStateOf(0f) }

    DisposableEffect(audioSessionId, enabled) {
        if (!enabled || audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            raw = 0f
            onDispose { }
        } else {
            val visualizer = runCatching {
                Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[0]
                    // Floor the rate: ~10–15 Hz is enough for bloom pulse; never maxCaptureRate.
                    val target = min(15_000, Visualizer.getMaxCaptureRate() / 4)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int,
                            ) {
                                if (waveform == null || waveform.isEmpty()) return
                                var sum = 0.0
                                for (b in waveform) {
                                    val centered = (b.toInt() and 0xFF) - 128
                                    sum += centered * centered
                                }
                                val rms = sqrt(sum / waveform.size).toFloat() / 128f
                                raw = rms.coerceIn(0f, 1f)
                            }

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int,
                            ) = Unit
                        },
                        target,
                        true,
                        false,
                    )
                    setEnabled(true)
                }
            }.getOrNull()
            onDispose {
                runCatching {
                    visualizer?.setEnabled(false)
                    visualizer?.release()
                }
                raw = 0f
            }
        }
    }

    LaunchedEffect(enabled, playing) {
        if (!enabled) {
            level.floatValue = 0f
            return@LaunchedEffect
        }
        var smoothed = 0f
        while (isActive) {
            withFrameNanos {
                val target = if (playing) raw else 0f
                // Fast attack, slower release — reads as rhythm without jitter.
                smoothed += (target - smoothed) * if (target > smoothed) 0.35f else 0.12f
                level.floatValue = smoothed.coerceIn(0f, 1f)
            }
        }
    }

    return level
}
