

package iad1tya.echo.music.ui.screens

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.music.innertube.YouTube
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalSyncUtils
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AccountChannelHandleKey
import iad1tya.echo.music.constants.AccountEmailKey
import iad1tya.echo.music.constants.AccountNameKey
import iad1tya.echo.music.constants.DataSyncIdKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.VisitorDataKey
import iad1tya.echo.music.db.MusicDatabaseEntryPoint
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Context
import android.content.Intent
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Cold-restarts the app so every screen reloads in the new (authenticated) state. Uses
 * ProcessPhoenix (a dedicated relaunch process) which is reliable across devices — the previous
 * AlarmManager / startActivity-then-exit approaches often just closed the app without reopening on
 * Xiaomi/Doze devices.
 */
private fun restartApp(context: Context) {
    runCatching { com.jakewharton.processphoenix.ProcessPhoenix.triggerRebirth(context) }
        .onFailure {
            // Fallback: best-effort relaunch + exit if Phoenix isn't available for some reason.
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                ?.let { context.startActivity(it) }
            if (context is Activity) context.finishAffinity()
            Runtime.getRuntime().exit(0)
        }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var hasCompletedLogin by remember { mutableStateOf(false) }

    // Held in state so the account-picker callback can reload it. The WebView still loads the normal
    // ServiceLogin URL by default; the picker only PRE-FILLS the email so the user skips typing it.
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // System account picker (AndroidX framework, NOT Google Play Services) — enumerates the phone's
    // synced Google accounts with NO permission and NO GMS, so it works on the FOSS flavor too. It only
    // returns the chosen email; Google's own web sign-in still handles the password/2FA. login_hint is
    // the standard OIDC param the sign-in page honours to pre-select/pre-fill that account.
    val accountPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!email.isNullOrBlank()) {
            val hinted = "https://accounts.google.com/ServiceLogin" +
                "?continue=https%3A%2F%2Fmusic.youtube.com" +
                "&Email=${Uri.encode(email)}&login_hint=${Uri.encode(email)}"
            webViewRef?.loadUrl(hinted)
        }
    }

    fun launchAccountPicker() {
        runCatching {
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null,
            )
            accountPicker.launch(intent)
        }.onFailure { Timber.w(it, "Account picker unavailable") }
    }

    var webView: WebView? = null

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                        loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                        // getCookie(url) can return null (cookies not ready yet) — never force-assign it
                        // (that crashed with "getCookie(...) must not be null"). Also require the
                        // authenticated cookie (SAPISID) so we only complete once the user is actually
                        // signed in; otherwise wait for the next onPageFinished.
                        val pageCookie = if (url?.startsWith("https://music.youtube.com") == true)
                            CookieManager.getInstance().getCookie(url) else null
                        if (!hasCompletedLogin && !pageCookie.isNullOrBlank() && pageCookie.contains("SAPISID")) {
                            innerTubeCookie = pageCookie
                            hasCompletedLogin = true

                            coroutineScope.launch {
                                
                                delay(500)

                                
                                YouTube.cookie = innerTubeCookie
                                YouTube.dataSyncId = dataSyncId
                                YouTube.visitorData = visitorData

                                Timber.d("Login: YouTube object initialized, validating...")

                                YouTube.accountInfo().onSuccess {
                                    accountName = it.name
                                    accountEmail = it.email.orEmpty()
                                    accountChannelHandle = it.channelHandle.orEmpty()

                                    Timber.d("Login: Successfully logged in as ${it.name}, persisting session and restarting...")

                                    webView?.apply {
                                        stopLoading()
                                        clearHistory()
                                        clearCache(true)
                                        clearFormData()
                                    }

                                    // ATTACHING an account is as much of a boundary as detaching one.
                                    //
                                    // `App.forgetAccount` clears the account-scoped artist markers on
                                    // the way OUT, which covers every logout and account switch made
                                    // on this device. It does not cover a database that arrived here
                                    // WITHOUT passing through a logout — and Android hands us exactly
                                    // that on a "copy apps & data" transfer, a cloud restore or an
                                    // `adb restore`: song.db is restored (it is not excluded from the
                                    // backup rules, deliberately — see App.classifyInstallOrigin) while
                                    // datastore/settings.preferences_pb, which holds the cookie, IS
                                    // excluded. The app comes up signed out, carrying account A's
                                    // `unfollowedByUserAt` + `ytmSyncedAt` markers, and the moment the
                                    // user signs into account B those markers become 50 real
                                    // `subscribeChannel(id, false)` calls per pass against B.
                                    //
                                    // So: any database reaching a login without having passed through
                                    // a logout is by definition carrying markers written under an
                                    // account we cannot identify. Drop them. Re-logging into the SAME
                                    // account is a no-op — the subscription read-back re-stamps
                                    // `ytmSyncedAt` from the account's real list on the next sync —
                                    // and `followedByUserAt` / `bookmarkedAt` are untouched, so the
                                    // library and the user's own follows survive intact.
                                    //
                                    // Best-effort: a database problem must never block a sign-in.
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            MusicDatabaseEntryPoint.get(context)
                                                .clearArtistAccountSyncMarkers()
                                        }
                                    }.onFailure { e ->
                                        Timber.w(e, "Login: could not clear the artist account-sync markers")
                                    }

                                    // Persist the session synchronously, then cold-restart so every
                                    // screen (home, library, account) loads already authenticated.
                                    context.dataStore.edit { prefs ->
                                        prefs[InnerTubeCookieKey] = innerTubeCookie
                                        if (visitorData.isNotEmpty()) prefs[VisitorDataKey] = visitorData
                                        if (dataSyncId.isNotEmpty()) prefs[DataSyncIdKey] = dataSyncId
                                        prefs[AccountNameKey] = it.name
                                        prefs[AccountEmailKey] = it.email.orEmpty()
                                        prefs[AccountChannelHandleKey] = it.channelHandle.orEmpty()
                                    }
                                    restartApp(context)
                                }.onFailure {
                                    Timber.e(it, "Login: Authentication validation failed")
                                    hasCompletedLogin = false 
                                    reportException(it)
                                }
                            }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    // Hardening: this WebView only loads the remote Google sign-in page, so deny it any
                    // access to local files/content — the JS bridge can't be used to read local data.
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRetrieveVisitorData(newVisitorData: String?) {
                        if (newVisitorData != null) {
                            visitorData = newVisitorData
                        }
                    }
                    @JavascriptInterface
                    fun onRetrieveDataSyncId(newDataSyncId: String?) {
                        if (newDataSyncId != null) {
                            dataSyncId = newDataSyncId.substringBefore("||")
                        }
                    }
                }, "Android")
                webView = this
                webViewRef = this
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
            }
        }
    )

    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = null
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        actions = {
            // "Use a phone account": pre-selects one of the device's Google accounts so the user skips
            // typing their email. The web sign-in (password/2FA) still runs — this is a convenience, not
            // a full auto-login (YTM auth is a web cookie, not an OAuth token).
            androidx.compose.material3.TextButton(onClick = { launchAccountPicker() }) {
                Text(stringResource(R.string.login_use_phone_account))
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
