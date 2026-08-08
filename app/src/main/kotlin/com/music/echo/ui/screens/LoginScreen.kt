

package iad1tya.echo.music.ui.screens

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
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
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Persists the YouTube Music session and returns to the previous screen WITHOUT cold-restarting.
 * ProcessPhoenix felt like "leaving the app" (owner); DataStore + in-memory YouTube state already
 * update every rememberPreference / sync consumer, so a rebirth is unnecessary for sign-in.
 */
private fun finishLoginInPlace(navController: NavController, syncUtils: SyncUtils) {
    runCatching {
        syncUtils.syncLikedSongs()
        syncUtils.syncLibrarySongs()
        syncUtils.syncArtistsSubscriptions()
        syncUtils.syncSavedPlaylists()
    }
    navController.navigateUp()
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
    val skin = rememberAuraPanelSkin()

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

    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (skin.enabled && skin.darkGround) Modifier.background(AuraPalette.Ground)
                else Modifier,
            ),
    ) {
        if (skin.enabled && skin.darkGround) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            ) {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = null,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = AuraPalette.OnGround,
                    )
                }
                Text(
                    text = stringResource(R.string.login),
                    style = AuraType.ScreenTitle,
                    color = AuraPalette.OnGround,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = { launchAccountPicker() }) {
                    Text(
                        stringResource(R.string.login_use_phone_account),
                        color = AuraPalette.Teal,
                    )
                }
            }
        } else {
            TopAppBar(
                title = { Text(stringResource(R.string.login)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = null,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = { launchAccountPicker() }) {
                        Text(stringResource(R.string.login_use_phone_account))
                    }
                },
            )
        }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
            factory = { webViewContext ->
                WebView(webViewContext).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

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

                                        Timber.d("Login: Successfully logged in as ${it.name}")

                                        webView?.apply {
                                            stopLoading()
                                            clearHistory()
                                            clearCache(true)
                                            clearFormData()
                                        }

                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                MusicDatabaseEntryPoint.get(context)
                                                    .clearArtistAccountSyncMarkers()
                                            }
                                        }.onFailure { e ->
                                            Timber.w(e, "Login: could not clear the artist account-sync markers")
                                        }

                                        context.dataStore.edit { prefs ->
                                            prefs[InnerTubeCookieKey] = innerTubeCookie
                                            if (visitorData.isNotEmpty()) prefs[VisitorDataKey] = visitorData
                                            if (dataSyncId.isNotEmpty()) prefs[DataSyncIdKey] = dataSyncId
                                            prefs[AccountNameKey] = it.name
                                            prefs[AccountEmailKey] = it.email.orEmpty()
                                            prefs[AccountChannelHandleKey] = it.channelHandle.orEmpty()
                                        }
                                        finishLoginInPlace(navController, syncUtils)
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
            },
        )
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
