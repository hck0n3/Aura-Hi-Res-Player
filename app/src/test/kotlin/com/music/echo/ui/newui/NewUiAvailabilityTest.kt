package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The escape hatch of "Interfaz nueva", pinned.
 *
 * From 0.6.150 the switch is offered in every build. The stored preference alone decides whether
 * the redesigned shell renders — never leave a user in the new UI with no way back.
 */
class NewUiAvailabilityTest {

    @Test
    fun `every version name offers the switch`() {
        assertTrue(isNewUiSwitchVisible("0.6.150", debugBuild = false))
        assertTrue(isNewUiSwitchVisible("0.6.145", debugBuild = false))
        assertTrue(isNewUiSwitchVisible("0.6.145-nosub", debugBuild = false))
        assertTrue(isNewUiSwitchVisible("0.6.146-beta1", debugBuild = false))
        assertTrue(isNewUiSwitchVisible("0.6.150-alpha3", debugBuild = false))
        assertTrue(isNewUiSwitchVisible("0.6.145", debugBuild = true))
    }

    @Test
    fun `the shipped constant is exactly the predicate over this build`() {
        assertEquals(
            isNewUiSwitchVisible(BuildConfig.VERSION_NAME, BuildConfig.DEBUG),
            NEW_UI_SWITCH_VISIBLE,
        )
    }

    @Test
    fun `stable build renders new UI when the preference is true`() {
        val stableVisible = isNewUiSwitchVisible("0.6.150", debugBuild = false)
        assertTrue(isNewUiActive(stableVisible, storedPreference = true))
        assertFalse(isNewUiActive(stableVisible, storedPreference = false))
    }

    @Test
    fun `turning the preference off always returns to classic`() {
        for (version in listOf("0.6.150", "0.6.149-beta1", "0.6.145-nosub")) {
            assertFalse(
                isNewUiActive(isNewUiSwitchVisible(version, false), storedPreference = false),
            )
        }
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
