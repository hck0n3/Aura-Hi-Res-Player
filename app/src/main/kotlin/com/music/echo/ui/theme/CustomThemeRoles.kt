package iad1tya.echo.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import iad1tya.echo.music.constants.CustomBackgroundColorKey
import iad1tya.echo.music.constants.CustomOnBackgroundColorKey
import iad1tya.echo.music.constants.CustomOnPrimaryColorKey
import iad1tya.echo.music.constants.CustomOnSurfaceVariantColorKey
import iad1tya.echo.music.constants.CustomOutlineColorKey
import iad1tya.echo.music.constants.CustomSurfaceColorKey
import iad1tya.echo.music.utils.rememberPreference

/**
 * Optional per-role colour overrides shared by classic [ColorScheme] and [iad1tya.echo.music.ui.newui.AuraPalette].
 *
 * Each field is null when the matching preference is `0` (automatic). That keeps a fresh install and
 * every existing user on today's look until they open Tema y colores ▸ Personalizar roles.
 */
data class CustomThemeRoles(
    val background: Color? = null,
    val surface: Color? = null,
    val onBackground: Color? = null,
    val onSurfaceVariant: Color? = null,
    val outline: Color? = null,
    val onPrimary: Color? = null,
) {
    val isEmpty: Boolean
        get() = background == null &&
            surface == null &&
            onBackground == null &&
            onSurfaceVariant == null &&
            outline == null &&
            onPrimary == null

    companion object {
        val None = CustomThemeRoles()

        /** Stored preference sentinel: never set. Distinct from opaque black (`0xFF000000`). */
        const val AUTO_ARGB = 0

        fun fromArgb(
            background: Int = AUTO_ARGB,
            surface: Int = AUTO_ARGB,
            onBackground: Int = AUTO_ARGB,
            onSurfaceVariant: Int = AUTO_ARGB,
            outline: Int = AUTO_ARGB,
            onPrimary: Int = AUTO_ARGB,
        ): CustomThemeRoles = CustomThemeRoles(
            background = background.toColorOrNull(),
            surface = surface.toColorOrNull(),
            onBackground = onBackground.toColorOrNull(),
            onSurfaceVariant = onSurfaceVariant.toColorOrNull(),
            outline = outline.toColorOrNull(),
            onPrimary = onPrimary.toColorOrNull(),
        )
    }
}

fun Int.toColorOrNull(): Color? =
    if (this == CustomThemeRoles.AUTO_ARGB) null else Color(this)

fun Color?.toArgbOrAuto(): Int = this?.toArgb() ?: CustomThemeRoles.AUTO_ARGB

@Composable
fun rememberCustomThemeRoles(): CustomThemeRoles {
    val background by rememberPreference(CustomBackgroundColorKey, CustomThemeRoles.AUTO_ARGB)
    val surface by rememberPreference(CustomSurfaceColorKey, CustomThemeRoles.AUTO_ARGB)
    val onBackground by rememberPreference(CustomOnBackgroundColorKey, CustomThemeRoles.AUTO_ARGB)
    val onSurfaceVariant by rememberPreference(CustomOnSurfaceVariantColorKey, CustomThemeRoles.AUTO_ARGB)
    val outline by rememberPreference(CustomOutlineColorKey, CustomThemeRoles.AUTO_ARGB)
    val onPrimary by rememberPreference(CustomOnPrimaryColorKey, CustomThemeRoles.AUTO_ARGB)
    return remember(background, surface, onBackground, onSurfaceVariant, outline, onPrimary) {
        CustomThemeRoles.fromArgb(
            background = background,
            surface = surface,
            onBackground = onBackground,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            onPrimary = onPrimary,
        )
    }
}

private const val ROLE_TEXT_CONTRAST = 4.5f

/**
 * Applies [roles] on top of an already-built scheme.
 *
 * **AMOLED:** when [pureBlack] is true, [CustomThemeRoles.background] is ignored so Pure Black stays
 * truly black. Surface / text / outline / onPrimary overrides still apply.
 */
fun ColorScheme.applyCustomRoles(
    roles: CustomThemeRoles,
    pureBlack: Boolean,
): ColorScheme {
    if (roles.isEmpty) return this

    var result = this

    val bgOverride = if (pureBlack) null else roles.background
    val surfaceOverride = roles.surface

    if (bgOverride != null || surfaceOverride != null) {
        val newBackground = bgOverride ?: result.background
        // Plan: background alone also drives the surface base when surface is still automatic.
        val newSurface = surfaceOverride ?: bgOverride ?: result.surface
        val raised = surfaceOverride
            ?: if (bgOverride != null) {
                Color.White.copy(alpha = 0.04f).compositeOver(bgOverride)
            } else {
                result.surfaceContainerLow
            }
        val card = Color.White.copy(alpha = 0.07f).compositeOver(newSurface)
        val cardHigh = Color.White.copy(alpha = 0.10f).compositeOver(newSurface)
        val cardHighest = Color.White.copy(alpha = 0.13f).compositeOver(newSurface)
        result = result.copy(
            background = newBackground,
            surface = newSurface,
            surfaceDim = bgOverride ?: result.surfaceDim,
            surfaceBright = cardHighest,
            surfaceContainerLowest = bgOverride ?: result.surfaceContainerLowest,
            surfaceContainerLow = raised,
            surfaceContainer = if (surfaceOverride != null || bgOverride != null) card else result.surfaceContainer,
            surfaceContainerHigh = if (surfaceOverride != null || bgOverride != null) cardHigh else result.surfaceContainerHigh,
            surfaceContainerHighest = if (surfaceOverride != null || bgOverride != null) cardHighest else result.surfaceContainerHighest,
            surfaceVariant = if (surfaceOverride != null || bgOverride != null) cardHigh else result.surfaceVariant,
        )
    }

    roles.onBackground?.let { raw ->
        val ink = ensureLegibleOn(raw, result.surface, ROLE_TEXT_CONTRAST)
        result = result.copy(onBackground = ink, onSurface = ink)
    }

    roles.onSurfaceVariant?.let { raw ->
        val muted = ensureLegibleOn(raw, result.surface, ROLE_TEXT_CONTRAST)
        result = result.copy(onSurfaceVariant = muted)
    }

    roles.outline?.let { line ->
        result = result.copy(
            outline = line,
            outlineVariant = line.copy(alpha = (line.alpha * 0.55f).coerceIn(0.08f, 1f)),
        )
    }

    roles.onPrimary?.let { raw ->
        val onP = ensureLegibleOn(raw, result.primary, ROLE_TEXT_CONTRAST)
        val onS = ensureLegibleOn(raw, result.secondary, ROLE_TEXT_CONTRAST)
        val onT = ensureLegibleOn(raw, result.tertiary, ROLE_TEXT_CONTRAST)
        result = result.copy(onPrimary = onP, onSecondary = onS, onTertiary = onT)
    }

    return result
}

/**
 * Effective redesign ground for accent contrast: AMOLED black wins; else custom background; else fallback.
 */
fun CustomThemeRoles.effectiveAuraGround(pureBlack: Boolean, fallback: Color): Color = when {
    pureBlack -> Color.Black
    background != null -> background
    else -> fallback
}
