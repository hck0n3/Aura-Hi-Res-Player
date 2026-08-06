package iad1tya.echo.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import iad1tya.echo.music.constants.DarkModeKey
import iad1tya.echo.music.ui.newui.rememberNewUiEnabled
import iad1tya.echo.music.ui.screens.settings.DarkMode
import iad1tya.echo.music.utils.rememberEnumPreference

/**
 * # "Is the app dark right now?" — asked ONCE, in one place
 *
 * `MainActivity` resolves the app's `ColorScheme` from `DarkModeKey` **or** the "Interfaz nueva" flag
 * (`newUiShell || …`, MainActivity.kt:672-674) — the redesign is dark-only, so turning it on forces the
 * whole app dark. But several other composables re-derived light/dark for themselves from `DarkModeKey`
 * and never saw that term. With the flag ON and the user on Claro — or on Auto with a light-mode phone
 * — each of those disagreed with the scheme every screen around it was painted with: a black play
 * button on the redesign's near-black ground, a status bar drawing dark icons over a dark bar, a cover
 * cropper doing the same. That is the tail of "la app son dos temas a la vez".
 *
 * The expression below is `MainActivity`'s, term for term. It is duplicated rather than read back
 * because the resolved value is a local in `MainActivity#setContent` with no CompositionLocal to reach
 * it through; see the report — publishing it as a `LocalAppDarkTheme` from there would let this file
 * delete its own copy of the `when`.
 *
 * ## Reversibility
 * With "Interfaz nueva" OFF `newUiForcesDark` is `false`, `false || x == x`, and this returns exactly
 * `if (darkMode == AUTO) systemDark else darkMode == ON` — the literal expression every call site had
 * inline before. No call site changes behaviour with the flag off.
 *
 * ## Cost
 * One `DarkModeKey` flow + one `NewUiEnabledKey` flow per call site, both `distinctUntilChanged`, both
 * seeded by a single cached read (utils/DataStore.kt). Nothing per frame; the `remember` keeps the
 * boolean from re-deriving on unrelated recompositions, as the inline copies did.
 */
@Composable
fun rememberEffectiveDarkTheme(): Boolean {
    val newUiForcesDark = rememberNewUiEnabled()
    val darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val systemDark = isSystemInDarkTheme()
    return remember(darkMode, systemDark, newUiForcesDark) {
        effectiveDarkTheme(
            newUiForcesDark = newUiForcesDark,
            darkMode = darkMode,
            systemDark = systemDark,
        )
    }
}

/**
 * [rememberEffectiveDarkTheme]'s decision as a PURE function, so the reduction can actually be
 * asserted rather than asserted about — the same reason [surfacesFor] exists (see
 * `AuraGroundThemeTest`). Pinned by `EffectiveDarkThemeTest`.
 */
fun effectiveDarkTheme(
    newUiForcesDark: Boolean,
    darkMode: DarkMode,
    systemDark: Boolean,
): Boolean =
    newUiForcesDark || if (darkMode == DarkMode.AUTO) systemDark else darkMode == DarkMode.ON

/**
 * The "Interfaz nueva" term of [rememberEffectiveDarkTheme] on its own.
 *
 * For the handful of call sites that derive their look from the SYSTEM setting only and never read
 * `DarkModeKey` at all. OR-ing this onto what they already compute makes them agree with the forced
 * dark scheme while the redesign is on, and leaves them exactly as they were while it is off — which
 * [rememberEffectiveDarkTheme] would not: swapping them wholesale would also change how they behave on
 * the classic UI for a user whose `DarkModeKey` disagrees with the phone, and that is not this change's
 * to fix.
 */
@Composable
fun rememberNewUiForcesDarkTheme(): Boolean = rememberNewUiEnabled()
