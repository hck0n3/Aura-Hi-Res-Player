package iad1tya.echo.music.ui.theme

import iad1tya.echo.music.ui.screens.settings.DarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two promises of [effectiveDarkTheme], pinned.
 *
 *  1. With "Interfaz nueva" ON the answer is always dark — that is what stops the ~89 classic screens
 *     and every dialog rendering white against the redesign's ground.
 *  2. With it OFF the answer is EXACTLY the expression each call site used to carry inline. Five
 *     composables (the classic player, the mini player, the Search route, the lyrics view and the
 *     playlist-cover cropper) each carried their own copy; if this drifts by one term they now all
 *     drift together, and silently, because nothing on screen names this function.
 */
class EffectiveDarkThemeTest {

    /** The expression that was inlined at every call site before this function existed. */
    private fun asItShipped(darkMode: DarkMode, systemDark: Boolean): Boolean =
        if (darkMode == DarkMode.AUTO) systemDark else darkMode == DarkMode.ON

    @Test
    fun `flag off reduces term for term to the expression every call site had inline`() {
        for (darkMode in DarkMode.entries) {
            for (systemDark in listOf(false, true)) {
                assertEquals(
                    "darkMode=$darkMode systemDark=$systemDark",
                    asItShipped(darkMode, systemDark),
                    effectiveDarkTheme(
                        newUiForcesDark = false,
                        darkMode = darkMode,
                        systemDark = systemDark,
                    ),
                )
            }
        }
    }

    @Test
    fun `flag on is dark whatever the preference and whatever the phone says`() {
        for (darkMode in DarkMode.entries) {
            for (systemDark in listOf(false, true)) {
                assertTrue(
                    "darkMode=$darkMode systemDark=$systemDark",
                    effectiveDarkTheme(
                        newUiForcesDark = true,
                        darkMode = darkMode,
                        systemDark = systemDark,
                    ),
                )
            }
        }
    }

    // The owner's actual state when he reported it: "Interfaz nueva" on, theme preference light.
    @Test
    fun `Claro plus the new UI is dark, Claro alone is light`() {
        assertTrue(
            effectiveDarkTheme(newUiForcesDark = true, darkMode = DarkMode.OFF, systemDark = false),
        )
        assertEquals(
            false,
            effectiveDarkTheme(newUiForcesDark = false, darkMode = DarkMode.OFF, systemDark = false),
        )
    }

    @Test
    fun `Auto on a light-mode phone plus the new UI is dark`() {
        assertTrue(
            effectiveDarkTheme(newUiForcesDark = true, darkMode = DarkMode.AUTO, systemDark = false),
        )
        assertEquals(
            false,
            effectiveDarkTheme(newUiForcesDark = false, darkMode = DarkMode.AUTO, systemDark = false),
        )
    }
}
