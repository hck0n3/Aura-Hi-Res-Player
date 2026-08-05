package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.BuildConfig

/**
 * Whether the "Interfaz nueva" master switch is offered to the user.
 *
 * The redesigned presentation layer currently covers six of roughly ninety-five screens: the player and
 * its menu, the queue, home, library and the settings index. Everything else falls back to the classic
 * design through [NewUiGate], so nothing is ever lost — but the seams are visible. Tapping "Buscar" from
 * the new Inicio lands on the classic search immediately.
 *
 * That is fine for the owner testing a beta and wrong for a paying customer who merely accepted an update.
 * So the switch is offered only in builds whose version name carries a pre-release suffix. The redesigned
 * code still ships in stable releases, inert and unreachable: it costs a little APK size and buys ONE
 * codebase and ONE test suite behind both tags, instead of hand-separating changes that are intertwined
 * across the same files — which is precisely where a change goes missing.
 *
 * Flip this to `true` unconditionally once the redesign covers enough of the app to stand on its own.
 */
val NEW_UI_SWITCH_VISIBLE: Boolean =
    isNewUiSwitchVisible(BuildConfig.VERSION_NAME, BuildConfig.DEBUG)

/**
 * The pure form of [NEW_UI_SWITCH_VISIBLE], so the rule can be pinned by a unit test instead of being
 * re-read off whichever variant happens to have generated `BuildConfig`.
 *
 * `"0.6.145"` → `false`; `"0.6.146-beta1"` → `true`.
 */
fun isNewUiSwitchVisible(versionName: String, debugBuild: Boolean): Boolean =
    versionName.contains("beta", ignoreCase = true) ||
        versionName.contains("alpha", ignoreCase = true) ||
        debugBuild

/**
 * Whether the redesigned layer may actually render.
 *
 * Beta and stable share the applicationId, so a stable update installs OVER a beta IN PLACE and the
 * DataStore file survives with `new_ui_enabled = true` still in it. Availability therefore has to gate
 * the flag itself, not merely the two places that DRAW the switch: otherwise the tester boots into the
 * redesigned shell in a build where nothing can turn it off.
 *
 * The stored preference is deliberately left alone — clearing it would silently discard a tester's
 * choice, and putting it back would need a fresh one-time migration key. Downgrade to a beta build and
 * the answer flips back to whatever was stored.
 */
fun isNewUiActive(switchVisible: Boolean, storedPreference: Boolean): Boolean =
    switchVisible && storedPreference
