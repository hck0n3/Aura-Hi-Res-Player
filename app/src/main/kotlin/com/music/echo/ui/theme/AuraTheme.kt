package iad1tya.echo.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * "Aura Glass" identity — a dark glassmorphism look (translucent surfaces, big rounded corners, a
 * cyan/teal accent over a deep blue-black). Enabling the Aura theme changes the whole UI feel: accent
 * colors (from [AuraSeedColor]), component shapes ([AuraGlassShapes]) and the glass background
 * ([AuraGlassBackground]) — not just a seed color. Wired in [echomusicTheme].
 */

// Accent ramp (cyan → sky → indigo).
val AuraCyan = Color(0xFF3DD6E0)
val AuraSky = Color(0xFF4FB6FF)
val AuraIndigo = Color(0xFF7B8CFF)

/** Seed [echomusicTheme] derives the Material 3 scheme from when Aura Glass is on. */
val AuraSeedColor = AuraCyan

/** Brand gradient for wordmarks/titles (cyan → sky → indigo). */
val AuraBrandGradient = Brush.linearGradient(
    colors = listOf(AuraCyan, AuraSky, AuraIndigo),
)

/** Deep glass background wash (blue-black with a faint top glow). */
val AuraScreenGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF0C1622), Color(0xFF060A11)),
)

/** Full-screen radial glass backdrop used behind Aura Glass screens. */
val AuraGlassBackground = Brush.radialGradient(
    colors = listOf(Color(0xFF12203A), Color(0xFF070B12)),
    radius = 1600f,
)

/** Translucent "glass" surface fill (used for cards/sheets when Aura Glass is on). */
val AuraGlassSurface = Color(0x14FFFFFF)         // ~8% white frost
val AuraGlassStroke = Color(0x22FFFFFF)          // hairline highlight

/**
 * Translucent + accent-tinted "glass" surfaces so the frosted panels share the SAME dynamic color as
 * the theme (the accent comes from the current artwork). Each surface is blended toward [primary]
 * (the dynamic accent) before going translucent, so glass + theme + blurred backdrop all read as one
 * cohesive palette instead of "neutral grey glass over a colored theme". `background` is transparent
 * so the blurred artwork shows through.
 */
fun ColorScheme.auraGlass(): ColorScheme {
    val a = 0.52f
    val tint = primary // dynamic accent (derived from the playing artwork)
    fun glass(base: Color, amount: Float = 0.22f): Color = lerp(base, tint, amount).copy(alpha = a)
    return copy(
        background = Color.Transparent,
        surface = glass(surface),
        surfaceVariant = glass(surfaceVariant),
        surfaceContainerLowest = glass(surfaceContainerLowest, 0.16f),
        surfaceContainerLow = glass(surfaceContainerLow, 0.18f),
        surfaceContainer = glass(surfaceContainer, 0.22f),
        surfaceContainerHigh = glass(surfaceContainerHigh, 0.26f),
        surfaceContainerHighest = glass(surfaceContainerHighest, 0.30f),
    )
}

/** Big, soft rounded corners — a key part of the glass feel, applied app-wide via MaterialTheme. */
val AuraGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)
