package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import iad1tya.echo.music.constants.NewUiEnabledKey
import iad1tya.echo.music.utils.rememberPreference

/**
 * Reads the "Interfaz nueva" master switch ([NewUiEnabledKey], default `true` for launch), ANDed with
 * [NEW_UI_SWITCH_VISIBLE].
 *
 * The availability term is what keeps a beta tester from being trapped: beta and stable share the
 * applicationId, so taking a stable update over a beta is an in-place install and the stored `true`
 * survives it. Without the AND the tester would boot into the redesigned shell in a build where both
 * copies of the switch are compiled out, with no way back short of clearing app data. The stored value
 * itself is never touched, so downgrading to a beta again restores exactly what was chosen.
 *
 * The preference is still read UNCONDITIONALLY so the composable call order never depends on the build.
 *
 * Use [NewUiGate] instead of this wherever you are choosing between two screens — reading the flag
 * yourself and branching by hand is how per-screen fallback gets forgotten.
 */
@Composable
fun rememberNewUiEnabled(): Boolean {
    val enabled by rememberPreference(NewUiEnabledKey, defaultValue = true)
    return isNewUiActive(NEW_UI_SWITCH_VISIBLE, enabled)
}

/**
 * The per-screen chooser. **This is the safety net of the whole beta.**
 *
 * ```
 * NewUiGate(
 *     classic = { HomeScreen(navController, snackbarHostState) },
 *     new = { AuraHomeScreen(navController, snackbarHostState) },   // or null while unbuilt
 * )
 * ```
 *
 * Semantics:
 *  · flag ON  **and** [new] non-null → the new screen.
 *  · anything else → the classic screen, untouched, exactly as it behaves today.
 *
 * That "anything else" is what makes the beta shippable with only six screens rebuilt: the other ~89
 * screens keep working because their host simply has no `new` to offer. It is also what makes the
 * switch reversible — turning the flag off puts every screen back on the classic path with no
 * migration, no state transfer and no cleanup.
 *
 * The flag is read UNCONDITIONALLY, before the branch, so the composable call order is stable no
 * matter how `new` changes; do not "optimise" that into a short-circuit.
 *
 * ## Why the palette is resolved here
 * [AuraPaletteSync] turns the user's Apariencia settings (accent swatch / typed hex / intensity /
 * dynamic accent, AMOLED, cover corner radius) into the values `AuraPalette` hands to ~143 call sites.
 * It runs BEFORE `new()` so a new screen composes with the resolved values in the same pass instead of
 * one frame after; it is idempotent, so composing several gates at once costs three comparisons. It is
 * deliberately inside the `new` branch: with the flag off nothing is resolved, nothing is written, and
 * every value in `AuraPalette` is the literal that shipped.
 */
@Composable
fun NewUiGate(
    classic: @Composable () -> Unit,
    new: (@Composable () -> Unit)? = null,
) {
    val enabled = rememberNewUiEnabled()
    if (enabled && new != null) {
        AuraPaletteSync()
        new()
    } else {
        classic()
    }
}
