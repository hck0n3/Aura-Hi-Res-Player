package iad1tya.echo.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.eq.audio.SpectrumBus

/**
 * Real-time FFT spectrum bars (Aura Hi-Res Player visualizer port).
 * Renders the 64 log-spaced bands published by [SpectrumBus] as rounded bars in the accent color.
 *
 * The raw bus values arrive irregularly, which looked choppy when drawn directly. We instead run a
 * per-frame (~60 fps) loop that eases the displayed levels toward the latest values — fast attack,
 * slow decay — so the bars rise instantly with the beat but fall smoothly. This decouples the render
 * smoothness from the bus emit rate.
 */
@Composable
fun SpectrumVisualizer(
    color: Color,
    modifier: Modifier = Modifier,
) {
    var displayed by remember { mutableStateOf(FloatArray(0)) }

    LaunchedEffect(Unit) {
        // Reused work buffer — no per-frame allocation. We only push a new state value (triggering a
        // redraw) when something actually changed beyond a small epsilon, so when audio is paused /
        // silent the visualizer goes fully idle instead of recomposing at 60 fps forever.
        var buf = FloatArray(0)
        while (true) {
            withFrameNanos {
                val target = SpectrumBus.spectrum.value
                if (target.isEmpty()) return@withFrameNanos
                if (buf.size != target.size) buf = FloatArray(target.size)
                var changed = false
                for (i in target.indices) {
                    val cur = buf[i]
                    val tgt = target[i].coerceIn(0f, 1f)
                    // Fast attack, slow decay for a fluid, musical motion.
                    val coef = if (tgt > cur) 0.55f else 0.12f
                    var nv = cur + (tgt - cur) * coef
                    if (nv < 0.0015f && tgt == 0f) nv = 0f   // settle to exact zero when idle
                    if (kotlin.math.abs(nv - cur) > 0.0015f || (nv == 0f && cur != 0f)) changed = true
                    buf[i] = nv
                }
                if (changed) displayed = buf.copyOf()
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val bands = displayed.size
        if (bands == 0) return@Canvas

        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (bands - 1)) / bands
        val minBar = 2.dp.toPx()
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until bands) {
            val level = displayed[i].coerceIn(0f, 1f)
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
