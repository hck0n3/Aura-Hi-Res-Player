package iad1tya.echo.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aura Hi-Res brand identity — shared so the gradient/seed live in one place instead of being
 * re-declared per screen. Used by [echomusicTheme] (when the Aura theme is enabled) and by the
 * brand wordmarks (Welcome / License / Now Playing accents).
 */

val AuraPink = Color(0xFFDE60B3)
val AuraPurple = Color(0xFF9B6CFF)
val AuraBlue = Color(0xFF3DA9ED)

/** Seed color [echomusicTheme] uses to derive the Material 3 scheme when the Aura theme is on. */
val AuraSeedColor = AuraPurple

/** Brand gradient for wordmarks/titles (pink → purple → blue). */
val AuraBrandGradient = Brush.linearGradient(
    colors = listOf(AuraPink, AuraPurple, AuraBlue),
)

/** Soft top-down screen wash for Aura hero surfaces. */
val AuraScreenGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF201433), Color(0xFF0B0B10)),
)
