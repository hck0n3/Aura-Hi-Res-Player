

package iad1tya.echo.music.ui.screens.settings

import iad1tya.echo.music.R
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.navigation.NavController
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.echomusic.updater.getUpdateAvailableState
import iad1tya.echo.music.license.SubscriptionEntryScreen
import iad1tya.echo.music.license.LicenseManager
import iad1tya.echo.music.license.LicenseLogic
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // "Suscríbete ahora / activa tu clave" shortcut: opens the existing (already-audited) subscription
    // entry screen, which shows BOTH the Gumroad subscribe button AND the license-key field (activating a
    // key does NOT require tapping subscribe). Surfaced only when a subscription is required and not already
    // active. This only presents the existing flow — it never touches the license/grace/subscription logic.
    var showSubscribe by rememberSaveable { mutableStateOf(false) }
    val subState = remember { LicenseManager.lastResolvedState(context) }
    val showSubscribeEntry = BuildConfig.REQUIRE_SUBSCRIPTION &&
        subState != LicenseLogic.AppState.SUBSCRIPTION_ACTIVE
    // Back while on the subscribe sub-screen returns to the settings list (not out of Settings entirely).
    BackHandler(showSubscribe) { showSubscribe = false }
    if (showSubscribe) {
        SubscriptionEntryScreen(
            onActivated = { showSubscribe = false },
            onBack = { showSubscribe = false },
        )
        return
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchLower = searchQuery.lowercase()

    val accountText = stringResource(R.string.account)
    val accountsText = "Cuentas"
    val scrobblingText = stringResource(R.string.scrobbling)
    val appearanceText = stringResource(R.string.appearance)
    val playerText = "Ajustes del reproductor"
    val soundText = "Sonido y ecualización"
    val performanceText = "Rendimiento"
    val listenTogetherText = stringResource(R.string.listen_together)
    val contentText = stringResource(R.string.content)
    val aiLyricsText = stringResource(R.string.ai_lyrics_translation)
    val privacyText = stringResource(R.string.privacy)
    val storageText = stringResource(R.string.storage)
    val backupText = stringResource(R.string.backup_restore)
    val statsText = stringResource(R.string.stats)
    val logsText = stringResource(R.string.logs)
    val aboutText = stringResource(R.string.about)
    val updateText = "Aura Hi-Res Update"
    val subscribeText = "Suscripción Pro"
    val noResultsText = stringResource(R.string.settings_search_no_results, searchQuery)

    val scrollState = rememberScrollState()
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 16.dp)
        )

        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search)) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = null
                        )
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
        )

        val itemsList = buildList {
            if (showSubscribeEntry && subscribeText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.subscribe),
                        title = { Text(subscribeText) },
                        description = { Text("Suscríbete o ingresa tu clave de licencia") },
                        onClick = { showSubscribe = true }
                    )
                )
            }
            if (accountsText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.account),
                        title = { Text(accountsText) },
                        onClick = { navController.navigate("settings/accounts") }
                    )
                )
            }
            if (scrobblingText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.ic_lastfm),
                        title = { Text(scrobblingText) },
                        description = { Text("Last.fm & ListenBrainz") },
                        onClick = { navController.navigate("settings/lastfm") }
                    )
                )
            }
            if (appearanceText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text(appearanceText) },
                        onClick = { navController.navigate("settings/appearance") }
                    )
                )
            }
            if (updateText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.download),
                        title = { Text(updateText) },
                        description = { Text("Cambios de esta versión y nuevas actualizaciones") },
                        onClick = { navController.navigate("settings/update") }
                    )
                )
            }
            if (playerText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.play),
                        title = { Text(playerText) },
                        onClick = { navController.navigate("settings/player") }
                    )
                )
            }
            if (soundText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.echoequlizer),
                        title = { Text(soundText) },
                        onClick = { navController.navigate("settings/sound") }
                    )
                )
            }
            if (performanceText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text(performanceText) },
                        onClick = { navController.navigate("settings/performance") }
                    )
                )
            }
            if (listenTogetherText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.group),
                        title = { Text(listenTogetherText) },
                        onClick = { navController.navigate(Screens.ListenTogether.route) }
                    )
                )
            }
            if (contentText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(contentText) },
                        onClick = { navController.navigate("settings/content") }
                    )
                )
            }
            if (aiLyricsText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.translate),
                        title = { Text(aiLyricsText) },
                        onClick = { navController.navigate("settings/ai") }
                    )
                )
            }
            if (privacyText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.security),
                        title = { Text(privacyText) },
                        onClick = { navController.navigate("settings/privacy") }
                    )
                )
            }
            if (storageText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.storage),
                        title = { Text(storageText) },
                        onClick = { navController.navigate("settings/storage") }
                    )
                )
            }
            if (backupText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.restore),
                        title = { Text(backupText) },
                        onClick = { navController.navigate("settings/backup_restore") }
                    )
                )
            }
            if (statsText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.stats),
                        title = { Text(statsText) },
                        onClick = { navController.navigate("stats") }
                    )
                )
            }
            if (logsText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.bug_report),
                        title = { Text(logsText) },
                        description = { Text(stringResource(R.string.logs_desc)) },
                        onClick = { navController.navigate("settings/logs") }
                    )
                )
            }
            if (aboutText.lowercase().contains(searchLower)) {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text(aboutText) },
                        onClick = { navController.navigate("settings/about") }
                    )
                )
            }
        }

        // Indexed sub-settings: while searching, surface every individual setting (adapted from upstream
        // e244ac9). Each result navigates to its PARENT screen — the scroll-to-highlight refinement is not
        // ported (it would require editing every settings screen). See SearchableSettings.kt.
        val finalItemsList = if (searchQuery.isNotEmpty()) {
            val matchedSubSettings = getAllSearchableSettings()
                .filter { it.first.lowercase().contains(searchLower) }
                .map { (title, parentTitle, route) ->
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.search),
                        title = { Text(title) },
                        description = { Text(parentTitle) },
                        onClick = { navController.navigate(route) }
                    )
                }
            itemsList + matchedSubSettings
        } else {
            itemsList
        }

        if (finalItemsList.isEmpty() && searchQuery.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = noResultsText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        } else {
            Material3SettingsGroup(items = finalItemsList)
        }
        
        Spacer(modifier = Modifier.height(50.dp))
    }

    TopAppBar(
        title = {
            androidx.compose.animation.AnimatedVisibility(
                visible = scrollState.value > 100,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
