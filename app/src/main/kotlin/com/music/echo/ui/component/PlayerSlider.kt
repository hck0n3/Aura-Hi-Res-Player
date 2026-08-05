

package iad1tya.echo.music.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.constants.SliderStyle
import iad1tya.echo.music.constants.SliderStyleKey
import iad1tya.echo.music.constants.SquigglySliderKey
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPerfGatedBoolean

/**
 * THE player timeline. One implementation, shared by every player shape.
 *
 * `SliderStyleKey` + `SquigglySliderKey` select between the four styles the Ajustes screen offers —
 * DEFAULT, WAVY (Material 3 expressive), WAVY+squiggly and SLIM. **This composable is the single read
 * site of both preferences**, so a player shape honours the setting by calling it and cannot honour it
 * halfway. Duplicating this `when` elsewhere is how the two copies drift and how "Estilo de la barra de
 * progreso" turns into a control that does nothing.
 *
 * Presentation only: the caller keeps owning the seek (`player.seekTo` / `castHandler.seekTo`) through
 * [onValueChange] / [onValueChangeFinished], exactly as before.
 *
 * @param colors the caller's palette — each player shape tints its own timeline.
 * @param isPlaying drives the wave animation of the two WAVY styles (a paused wave flattens).
 * @param enabled false disables ALL four styles. Previously only DEFAULT and SLIM were disabled, so a
 *   Listen Together guest could still scrub a wavy timeline; the guard is now uniform.
 * @param slimTrackGrowsOnDrag SLIM only: grow the track 10→16 dp while touched. The classic player's
 *   "nuevo diseño" turns this off, so it stays a parameter instead of a second copy of the style.
 */
@Composable
fun PlayerProgressSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    colors: SliderColors,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    slimTrackGrowsOnDrag: Boolean = true,
) {
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val squigglySlider by rememberPerfGatedBoolean(SquigglySliderKey, false)

    when (sliderStyle) {
        SliderStyle.DEFAULT -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = { if (enabled) onValueChange(it) },
                onValueChangeFinished = { if (enabled) onValueChangeFinished() },
                enabled = enabled,
                colors = colors,
                modifier = modifier,
            )
        }

        SliderStyle.WAVY -> {
            if (squigglySlider) {
                SquigglySlider(
                    value = value,
                    valueRange = valueRange,
                    onValueChange = { if (enabled) onValueChange(it) },
                    onValueChangeFinished = { if (enabled) onValueChangeFinished() },
                    enabled = enabled,
                    colors = colors,
                    isPlaying = isPlaying,
                    modifier = modifier,
                )
            } else {
                WavySlider(
                    value = value,
                    valueRange = valueRange,
                    onValueChange = { if (enabled) onValueChange(it) },
                    onValueChangeFinished = { if (enabled) onValueChangeFinished() },
                    enabled = enabled,
                    colors = colors,
                    isPlaying = isPlaying,
                    modifier = modifier,
                )
            }
        }

        SliderStyle.SLIM -> {
            val trackInteractionSource = remember { MutableInteractionSource() }
            val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
            val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
            val isTrackActive = (isTrackDragged || isTrackPressed) && slimTrackGrowsOnDrag

            val trackHeight by animateDpAsState(
                targetValue = if (isTrackActive) 16.dp else 10.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "trackHeight",
            )

            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = { if (enabled) onValueChange(it) },
                onValueChangeFinished = { if (enabled) onValueChangeFinished() },
                enabled = enabled,
                interactionSource = trackInteractionSource,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        trackHeight = trackHeight,
                        colors = colors,
                    )
                },
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSliderTrack(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    trackHeight: Dp = 10.dp
) {
    val inactiveTrackColor = colors.inactiveTrackColor
    val activeTrackColor = colors.activeTrackColor
    val inactiveTickColor = colors.inactiveTickColor
    val activeTickColor = colors.activeTickColor
    val valueRange = sliderState.valueRange
    Canvas(
        modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        drawTrack(
            stepsToTickFractions(sliderState.steps),
            0f,
            calcFraction(
                valueRange.start,
                valueRange.endInclusive,
                sliderState.value.coerceIn(valueRange.start, valueRange.endInclusive)
            ),
            inactiveTrackColor,
            activeTrackColor,
            inactiveTickColor,
            activeTickColor,
            trackHeight
        )
    }
}

private fun DrawScope.drawTrack(
    tickFractions: FloatArray,
    activeRangeStart: Float,
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    inactiveTickColor: Color,
    activeTickColor: Color,
    trackHeight: Dp = 2.dp
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val sliderLeft = Offset(0f, center.y)
    val sliderRight = Offset(size.width, center.y)
    val sliderStart = if (isRtl) sliderRight else sliderLeft
    val sliderEnd = if (isRtl) sliderLeft else sliderRight
    val tickSize = 2.0.dp.toPx()
    val trackStrokeWidth = trackHeight.toPx()
    drawLine(
        inactiveTrackColor,
        sliderStart,
        sliderEnd,
        trackStrokeWidth,
        StrokeCap.Round
    )
    val sliderValueEnd = Offset(
        sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeEnd,
        center.y
    )
    val sliderValueStart = Offset(
        sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeStart,
        center.y
    )
    drawLine(
        activeTrackColor,
        sliderValueStart,
        sliderValueEnd,
        trackStrokeWidth,
        StrokeCap.Round
    )
    for (tick in tickFractions) {
        val outsideFraction = tick > activeRangeEnd || tick < activeRangeStart
        drawCircle(
            color = if (outsideFraction) inactiveTickColor else activeTickColor,
            center = Offset(lerp(sliderStart, sliderEnd, tick).x, center.y),
            radius = tickSize / 2f
        )
    }
}

private fun stepsToTickFractions(steps: Int): FloatArray {
    return if (steps == 0) floatArrayOf() else FloatArray(steps + 2) { it.toFloat() / (steps + 1) }
}

private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)
