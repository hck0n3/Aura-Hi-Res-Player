package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import iad1tya.echo.music.constants.NewUiEnabledKey
import iad1tya.echo.music.utils.rememberPreference

/**
 * Reads the "Interfaz nueva" master switch ([NewUiEnabledKey], default `false`).
 *
 * Use [NewUiGate] instead of this wherever you are choosing between two screens — reading the flag
 * yourself and branching by hand is how per-screen fallback gets forgotten.
 */
@Composable
fun rememberNewUiEnabled(): Boolean {
    val enabled by rememberPreference(NewUiEnabledKey, defaultValue = false)
    return enabled
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
 */
@Composable
fun NewUiGate(
    classic: @Composable () -> Unit,
    new: (@Composable () -> Unit)? = null,
) {
    val enabled = rememberNewUiEnabled()
    if (enabled && new != null) new() else classic()
}
