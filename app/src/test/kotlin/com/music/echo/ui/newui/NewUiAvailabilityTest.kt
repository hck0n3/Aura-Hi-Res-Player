package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The escape hatch of the "Interfaz nueva" beta, pinned.
 *
 * Beta and stable share the applicationId (`app/build.gradle.kts`: no suffix outside the `noSub` and
 * `debug` variants), so a stable release installs OVER 0.6.144-beta1 IN PLACE and
 * `datastore/settings.preferences_pb` survives with `new_ui_enabled = true` still written in it.
 *
 * If availability only gated the two places that DRAW the switch, that tester would boot into the
 * redesigned shell — six of ~95 screens rebuilt — inside a build where both copies of the switch are
 * compiled out, with no way back short of clearing app data and losing the library. So the flag itself
 * is ANDed with availability, and the stored preference is never rewritten, which is what makes the
 * round trip work in both directions.
 */
class NewUiAvailabilityTest {

    // ------------------------------------------------------------ which builds offer the switch

    @Test
    fun `stable version names do not offer the switch`() {
        assertFalse(isNewUiSwitchVisible("0.6.145", debugBuild = false))
        assertFalse(isNewUiSwitchVisible("0.6.144", debugBuild = false))
        assertFalse(isNewUiSwitchVisible("0.6.146", debugBuild = false))
        // The `noSub` variant only appends "-nosub"; it is still a stable build.
        assertFalse(isNewUiSwitchVisible("0.6.145-nosub", debugBuild = false))
    }

    @Test
    fun `pre-release version names offer the switch`() {
        assertTrue(isNewUiSwitchVisible("0.6.146-beta1", debugBuild = false))
        assertTrue(isNewUiSwitchVisible("0.6.144-beta1", debugBuild = false))
        assertTrue(isNewUiSwitchVisible("0.6.150-alpha3", debugBuild = false))
        // The suffix is matched case-insensitively, as tags have been written both ways.
        assertTrue(isNewUiSwitchVisible("0.6.146-Beta1", debugBuild = false))
        assertTrue(isNewUiSwitchVisible("0.6.150-ALPHA", debugBuild = false))
    }

    @Test
    fun `a debug build always offers the switch`() {
        assertTrue(isNewUiSwitchVisible("0.6.145", debugBuild = true))
    }

    @Test
    fun `the shipped constant is exactly the predicate over this build`() {
        assertEquals(
            isNewUiSwitchVisible(BuildConfig.VERSION_NAME, BuildConfig.DEBUG),
            NEW_UI_SWITCH_VISIBLE,
        )
    }

    // ------------------------------------------------------------ the trap, and the round trip

    @Test
    fun `a stable build cannot render the new UI even with the preference stored true`() {
        val stableVisible = isNewUiSwitchVisible("0.6.145", debugBuild = false)
        assertFalse(isNewUiActive(stableVisible, storedPreference = true))
    }

    @Test
    fun `beta on then stable then beta again restores the tester's choice`() {
        val stored = true // written on 0.6.144-beta1 and never rewritten afterwards

        assertTrue(isNewUiActive(isNewUiSwitchVisible("0.6.144-beta1", false), stored))
        assertFalse(isNewUiActive(isNewUiSwitchVisible("0.6.145", false), stored))
        assertTrue(isNewUiActive(isNewUiSwitchVisible("0.6.146-beta1", false), stored))
    }

    @Test
    fun `the switch is never visible while the flag is forced off, and never hidden while it is on`() {
        for (visible in listOf(false, true)) {
            for (stored in listOf(false, true)) {
                val active = isNewUiActive(visible, stored)
                // Active implies a reachable switch: no state shows the new UI with no way out.
                assertFalse("new UI active with the switch hidden", active && !visible)
                // Visible implies the switch tells the truth: it never reads ON while forced off.
                assertEquals("visible switch disagrees with what renders", visible && stored, active)
            }
        }
    }
}
