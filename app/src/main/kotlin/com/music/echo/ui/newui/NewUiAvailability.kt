package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.BuildConfig

/**
 * Whether the "Interfaz nueva" master switch is offered to the user.
 *
 * From 0.6.150 the redesign is the default public appearance. The switch must stay visible in
 * every build (stable, beta, debug) so a user who dislikes it can return to the classic UI without
 * clearing data — and so a tester who already had it ON never boots into a shell with no way out
 * after installing a stable over a beta (shared applicationId, DataStore survives).
 */
val NEW_UI_SWITCH_VISIBLE: Boolean =
    isNewUiSwitchVisible(BuildConfig.VERSION_NAME, BuildConfig.DEBUG)

/**
 * The pure form of [NEW_UI_SWITCH_VISIBLE], so the rule can be pinned by a unit test instead of being
 * re-read off whichever variant happens to have generated `BuildConfig`.
 *
 * Always `true` since 0.6.150 (escape hatch is permanent).
 */
fun isNewUiSwitchVisible(versionName: String, debugBuild: Boolean): Boolean = true

/**
 * Whether the redesigned layer may actually render.
 *
 * Availability ANDs with the stored preference so the switch and the shell never disagree: if the
 * switch is somehow hidden, the new UI cannot stay on with no way back.
 */
fun isNewUiActive(switchVisible: Boolean, storedPreference: Boolean): Boolean =
    switchVisible && storedPreference
