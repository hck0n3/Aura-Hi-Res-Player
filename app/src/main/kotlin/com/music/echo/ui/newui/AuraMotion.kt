package iad1tya.echo.music.ui.newui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color

/**
 * The motion grammar of the redesigned interface.
 *
 * THE POINT OF THIS FILE: the app already speaks two coherent motion dialects — the floating
 * toolbar's springs ([iad1tya.echo.music.ui.component.FloatingNavigationToolbar]) and the classic
 * player's transport weight — and the redesign was using neither, so every new surface either
 * snapped with no transition at all or picked a linear `tween` of its own. Three constants, lifted
 * VERBATIM from what already ships, so the new UI joins an existing dialect instead of inventing a
 * third one. Do not add a curve here without a call site that needs something these cannot express.
 *
 * No `tween` anywhere: a spring settles on its own and costs nothing once it has, which is what
 * keeps this side of the standing heat/battery gate.
 */
object AuraMotion {
    /** Position, size, padding, offset — anything that moves or resizes. */
    fun <T> standard() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Tints, fills, borders. Faster than [standard] so colour never lags the shape it belongs to. */
    fun <T> color() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Press feedback and icon scale. The only place a bounce is wanted. */
    fun <T> press() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Scale a pressed target settles to, matching the classic toolbar. */
    const val PRESS_SCALE = 0.91f

    // Typed aliases for the three types that make up almost every call site, so a caller does not
    // have to spell the generic out.
    val dp get() = standard<Dp>()
    val float get() = standard<Float>()
    val intOffset get() = standard<IntOffset>()
    val intSize get() = standard<IntSize>()
    val tint get() = color<Color>()
    val pressFloat get() = press<Float>()
}
