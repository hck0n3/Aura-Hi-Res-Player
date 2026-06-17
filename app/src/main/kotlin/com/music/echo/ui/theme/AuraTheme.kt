package iad1tya.echo.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand wordmark gradient (used for the "Aura" title on the welcome / license screens). The old
 * "Aura Glass" theme was removed; this is the only remaining brand styling and is a candidate for the
 * upcoming wordmark/text-style redesign.
 */
private val BrandSlate = Color(0xFF6B7480)
private val BrandSlateLight = Color(0xFF8A93A0)
private val BrandSlateDeep = Color(0xFF3A4250)

/** Subtle grey brand gradient for wordmarks/titles. */
val AuraBrandGradient = Brush.linearGradient(
    colors = listOf(BrandSlateLight, BrandSlate, BrandSlateDeep),
)
