package iad1tya.echo.music.license

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import iad1tya.echo.music.license.LicenseLogic.AppState
import kotlinx.coroutines.launch

/**
 * Wraps the whole app: evaluates the license on entry and shows the proper gate screen, or the real
 * app content when entitled (DEMO or active subscription).
 */
@Composable
fun LicenseGate(appContent: @Composable () -> Unit) {
    // Private test build (-Pnosub=true): no subscription gate, go straight into the app.
    if (!iad1tya.echo.music.BuildConfig.REQUIRE_SUBSCRIPTION) {
        appContent()
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appState by remember { mutableStateOf<AppState?>(null) }
    var showEntry by remember { mutableStateOf(false) }

    fun refresh() {
        showEntry = false
        appState = null
        scope.launch { appState = LicenseManager.evaluate(context) }
    }

    LaunchedEffect(Unit) { refresh() }

    if (showEntry) {
        SubscriptionEntryScreen(onActivated = { refresh() }, onBack = { showEntry = false })
        return
    }

    when (val s = appState) {
        null -> LoadingLicenseScreen()
        AppState.DEMO, AppState.SUBSCRIPTION_ACTIVE -> appContent()
        AppState.FIRST_RUN -> ActivationPromptScreen(
            demoExpired = false,
            onTryDemo = {
                appState = null
                scope.launch {
                    // Demo requires a connection (registers the device on the server); if offline we
                    // can't start it — ask the user to connect instead of granting a free local demo.
                    appState = if (LicenseManager.startDemo(context)) LicenseManager.evaluate(context)
                    else AppState.NEEDS_CONNECTION
                }
            },
            onHaveSubscription = { showEntry = true },
        )
        AppState.DEMO_EXPIRED -> ActivationPromptScreen(
            demoExpired = true,
            onTryDemo = null,
            onHaveSubscription = { showEntry = true },
        )
        AppState.SUBSCRIPTION_EXPIRED -> RenewScreen(onActivated = { refresh() })
        AppState.NEEDS_CONNECTION -> NeedsConnectionScreen(onRetry = { refresh() })
        AppState.DEVICE_BLOCKED -> DeviceBlockedScreen(onRetry = { refresh() })
    }
}
