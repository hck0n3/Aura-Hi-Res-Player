package iad1tya.echo.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.eq.audio.SpectrumBus

/**
 * Real-time FFT spectrum bars (Aura Hi-Res Player visualizer port).
 * Renders the 64 log-spaced bands published by [SpectrumBus] as rounded
 * bars in the accent color with a soft glow underlay.
 */
@Composable
fun SpectrumVisualizer(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val spectrum by SpectrumBus.spectrum.collectAsState()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val bands = spectrum.size
        if (bands == 0) return@Canvas

        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (bands - 1)) / bands
        val minBar = 2.dp.toPx()
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until bands) {
            val level = spectrum[i].coerceIn(0f, 1f)
            val barHeight = (minBar + (size.height - minBar) * level)
            val x = i * (barWidth + gap)
            val y = size.height - barHeight

            // Soft glow underlay
            drawRoundRect(
                color = color.copy(alpha = 0.25f),
                topLeft = Offset(x - 1f, y - 2f),
                size = Size(barWidth + 2f, barHeight + 2f),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = color.copy(alpha = 0.45f + 0.55f * level),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}
