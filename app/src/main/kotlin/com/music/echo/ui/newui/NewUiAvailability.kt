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
    BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true) ||
        BuildConfig.VERSION_NAME.contains("alpha", ignoreCase = true) ||
        BuildConfig.DEBUG
