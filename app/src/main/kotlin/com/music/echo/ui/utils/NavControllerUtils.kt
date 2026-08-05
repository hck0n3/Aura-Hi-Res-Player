

package iad1tya.echo.music.ui.utils

import androidx.navigation.NavController

/**
 * Re-entry from OUTSIDE the app (launcher shortcut, playlist widget) without wiping the user's
 * place.
 *
 * The shortcut branch used to `navigate(route) { popUpTo(graph.startDestinationId) }`. The app is
 * singleTask, so on a warm start that ran through onNewIntent while a real chain was alive and
 * discarded all of it — tapping "Biblioteca" while three screens deep reset you through Home. The
 * widget branch had the opposite flaw: a bare `launchSingleTop` pushed a duplicate entry every tap,
 * so the same playlist stacked up N times and back had to be pressed N times to get out.
 *
 * Both are fixed by the same rule: if [route] is ALREADY on the back stack, go back to that
 * existing entry instead of pushing or rebuilding; otherwise push it on top of whatever the user
 * was doing, so their chain survives underneath and back returns them to it.
 *
 * On a cold start this is a no-op — the start destination is already picked from the same intent,
 * so the entry exists and nothing sits above it.
 */
fun NavController.navigateToReentryTarget(route: String) {
    val alreadyOnStack = runCatching { getBackStackEntry(route) }.isSuccess
    if (alreadyOnStack) {
        popBackStack(route, inclusive = false)
    } else {
        navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    }
}
