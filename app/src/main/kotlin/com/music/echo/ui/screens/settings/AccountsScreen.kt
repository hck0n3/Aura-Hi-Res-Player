package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AccountChannelHandleKey
import iad1tya.echo.music.constants.AccountEmailKey
import iad1tya.echo.music.constants.AccountNameKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.LastFMSessionKey
import iad1tya.echo.music.constants.LastFMUsernameKey
import iad1tya.echo.music.constants.ListenBrainzEnabledKey
import iad1tya.echo.music.constants.ListenBrainzTokenKey
import iad1tya.echo.music.spotifyimport.SpotifyImportViewModel
import iad1tya.echo.music.ui.component.*
import iad1tya.echo.music.utils.lastfm.LastFM
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AccountSettingsViewModel
import iad1tya.echo.music.viewmodels.HomeViewModel

/**
 * "Cuentas": one card per connectable music service (YouTube Music + Spotify + Last.fm + ListenBrainz).
 * Pure aggregation of the EXISTING auth state / login routes / logout actions — this screen adds NO new
 * authentication logic.
 *
 * - YouTube Music: logged in = the InnerTube cookie carries SAPISID. Identity from AccountName/Email/
 *   ChannelHandle prefs + HomeViewModel avatar. Login = route "login"; logout = the existing 3-option
 *   dialog via AccountSettingsViewModel (keep data / clear synced data).
 * - Spotify: state + logout come straight from the existing SpotifyImportViewModel; connect opens the
 *   existing "settings/spotify_import" screen.
 * - Last.fm: state from the SAME prefs the scrobbling screen reads (session key + username); connect
 *   opens "settings/lastfm"; logout is the scrobbling screen's exact 3-line clear (incl. LastFM.sessionKey)
 *   behind a confirm dialog.
 * - ListenBrainz: token + enable switch read from prefs (honest 3-state description); connect/manage
 *   opens "settings/lastfm" (token editing lives there).
 *
 * Discord is intentionally omitted (no login UI in the fork).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    // ── YouTube Music session ──
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val ytLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    val (ytNamePref) = rememberPreference(AccountNameKey, "")
    val (ytEmail) = rememberPreference(AccountEmailKey, "")
    val (ytHandle) = rememberPreference(AccountChannelHandleKey, "")

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val ytImageUrl by homeViewModel.accountImageUrl.collectAsState()
    val homeAccountName by homeViewModel.accountName.collectAsState()

    val ytDisplayName = ytNamePref.ifBlank { homeAccountName }
    val ytSecondary = ytHandle.ifBlank { ytEmail }

    // ── Spotify session (reuses the import screen's ViewModel — no new auth) ──
    val spotifyViewModel: SpotifyImportViewModel = hiltViewModel()
    val spotifyState by spotifyViewModel.uiState.collectAsState()
    val spotifyLoggedIn = spotifyState.isAuthenticated
    val spotifyName = spotifyState.accountName.ifBlank { "Spotify" }
    val spotifyAvatar = spotifyState.accountAvatarUrl

    // ── Last.fm session (same prefs the scrobbling screen reads — no new auth) ──
    var lastfmSession by rememberPreference(LastFMSessionKey, "")
    var lastfmUsername by rememberPreference(LastFMUsernameKey, "")
    val lastFmLoggedIn = remember(lastfmSession) { lastfmSession.isNotBlank() }

    // ── Qobuz (owner's OWN subscription; token vault + toggle live in the Qobuz screen) ──
    // The enabled preference flips true on link / false on logout, so it is a reactive proxy for the
    // connected badge here — the Qobuz screen shows the authoritative tier + controls.
    val (qobuzEnabled) = rememberPreference(iad1tya.echo.music.constants.UseOwnQobuzHiResKey, false)

    // ── ListenBrainz (token + enable switch live in the scrobbling screen; read-only here) ──
    val (listenBrainzToken) = rememberPreference(ListenBrainzTokenKey, "")
    val (listenBrainzEnabled) = rememberPreference(ListenBrainzEnabledKey, false)
    val listenBrainzConnected = listenBrainzToken.isNotBlank()
    // Data Saver force-disables ListenBrainz submissions in MusicService (the stored preference is kept
    // and resumes when Data Saver goes off). Read it here so the row cannot claim "scrobbling activo"
    // while nothing is actually being submitted.
    val (dataSaverEnabled) = rememberPreference(
        iad1tya.echo.music.constants.DataSaverEnabledKey,
        false,
    )

    var showYtLogoutDialog by remember { mutableStateOf(false) }
    var showSpotifyLogoutDialog by remember { mutableStateOf(false) }
    var showLastFmLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuentas") },
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ── YouTube Music ──
            Material3SettingsGroup(
                title = "YouTube Music",
                items = listOf(
                    Material3SettingsItem(
                        icon = if (ytLoggedIn && !ytImageUrl.isNullOrBlank()) null else painterResource(R.drawable.account),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (ytLoggedIn && !ytImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ytImageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(
                                    text = if (ytLoggedIn) ytDisplayName else stringResource(R.string.login),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        description = {
                            Text(
                                text = when {
                                    !ytLoggedIn -> stringResource(R.string.not_logged_in)
                                    ytSecondary.isNotBlank() -> ytSecondary
                                    else -> "Sesión iniciada"
                                }
                            )
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = {
                                    if (ytLoggedIn) showYtLogoutDialog = true
                                    else navController.navigate("login")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(if (ytLoggedIn) R.string.action_logout else R.string.login))
                            }
                        },
                        onClick = {
                            if (ytLoggedIn) navController.navigate("settings/ytm_sync")
                            else navController.navigate("login")
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Spotify ──
            Material3SettingsGroup(
                title = "Spotify",
                items = listOf(
                    Material3SettingsItem(
                        icon = if (spotifyLoggedIn && !spotifyAvatar.isNullOrBlank()) null else painterResource(R.drawable.ic_spotify),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (spotifyLoggedIn && !spotifyAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = spotifyAvatar,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(
                                    text = if (spotifyLoggedIn) spotifyName else "Spotify",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        description = {
                            Text(
                                text = if (spotifyLoggedIn) "Sesión iniciada" else stringResource(R.string.not_logged_in)
                            )
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = {
                                    if (spotifyLoggedIn) showSpotifyLogoutDialog = true
                                    else navController.navigate("settings/spotify_import")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(if (spotifyLoggedIn) R.string.action_logout else R.string.connect))
                            }
                        },
                        onClick = {
                            navController.navigate("settings/spotify_import")
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Last.fm ──
            Material3SettingsGroup(
                title = "Last.fm",
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.ic_lastfm),
                        title = {
                            Text(
                                text = if (lastFmLoggedIn) lastfmUsername.ifBlank { "Last.fm" } else "Last.fm",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        description = {
                            Text(
                                text = if (lastFmLoggedIn) "Sesión iniciada" else stringResource(R.string.not_logged_in)
                            )
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = {
                                    if (lastFmLoggedIn) showLastFmLogoutDialog = true
                                    else navController.navigate("settings/lastfm")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(if (lastFmLoggedIn) R.string.action_logout else R.string.connect))
                            }
                        },
                        onClick = {
                            navController.navigate("settings/lastfm")
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Qobuz (Hi-Res con tu propia suscripción) ──
            Material3SettingsGroup(
                title = "Qobuz",
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.echoequlizer),
                        title = {
                            Text(
                                text = "Qobuz",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        description = {
                            Text(
                                text = if (qobuzEnabled) stringResource(R.string.qobuz_account_active)
                                else stringResource(R.string.not_logged_in)
                            )
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { navController.navigate("settings/qobuz") },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    if (qobuzEnabled) stringResource(R.string.qobuz_manage)
                                    else stringResource(R.string.connect)
                                )
                            }
                        },
                        onClick = { navController.navigate("settings/qobuz") }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── ListenBrainz ──
            Material3SettingsGroup(
                title = "ListenBrainz",
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.ic_listenbrainz),
                        title = {
                            Text(
                                text = "ListenBrainz",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        description = {
                            Text(
                                text = when {
                                    !listenBrainzConnected -> stringResource(R.string.not_logged_in)
                                    listenBrainzEnabled && dataSaverEnabled ->
                                        "Conectado — pausado por Ahorro de datos"
                                    listenBrainzEnabled -> "Conectado — scrobbling activo"
                                    else -> "Token guardado — scrobbling desactivado"
                                }
                            )
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { navController.navigate("settings/lastfm") },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(if (listenBrainzConnected) "Administrar" else stringResource(R.string.connect))
                            }
                        },
                        onClick = {
                            navController.navigate("settings/lastfm")
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── YouTube Music logout: reuse the existing 3-option dialog (cancel / clear data / keep data) ──
        if (showYtLogoutDialog) {
            DefaultDialog(
                onDismiss = { showYtLogoutDialog = false },
                title = { Text(stringResource(R.string.logout_dialog_title)) },
                buttons = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        ToggleButton(
                            checked = false,
                            onCheckedChange = { showYtLogoutDialog = false },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }

                        ToggleButton(
                            checked = false,
                            onCheckedChange = {
                                accountSettingsViewModel.logoutAndClearSyncedContent(context, onInnerTubeCookieChange)
                                showYtLogoutDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.logout_clear_data))
                        }

                        ToggleButton(
                            checked = true,
                            onCheckedChange = {
                                accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                                showYtLogoutDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                        ) {
                            Text(stringResource(R.string.logout_keep_data))
                        }
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.logout_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // ── Spotify logout: confirm, then reuse the import ViewModel's existing logout() ──
        if (showSpotifyLogoutDialog) {
            DefaultDialog(
                onDismiss = { showSpotifyLogoutDialog = false },
                title = { Text(stringResource(R.string.logout_dialog_title)) },
                buttons = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        ToggleButton(
                            checked = false,
                            onCheckedChange = { showSpotifyLogoutDialog = false },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }

                        ToggleButton(
                            checked = true,
                            onCheckedChange = {
                                spotifyViewModel.logout()
                                showSpotifyLogoutDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.action_logout))
                        }
                    }
                }
            ) {
                Text(
                    text = "¿Cerrar sesión de Spotify?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // ── Last.fm logout: confirm, then the scrobbling screen's exact 3-line clear ──
        if (showLastFmLogoutDialog) {
            DefaultDialog(
                onDismiss = { showLastFmLogoutDialog = false },
                title = { Text(stringResource(R.string.logout_dialog_title)) },
                buttons = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        ToggleButton(
                            checked = false,
                            onCheckedChange = { showLastFmLogoutDialog = false },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }

                        ToggleButton(
                            checked = true,
                            onCheckedChange = {
                                lastfmSession = ""
                                lastfmUsername = ""
                                LastFM.sessionKey = null
                                showLastFmLogoutDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.action_logout))
                        }
                    }
                }
            ) {
                Text(
                    text = "¿Cerrar sesión de Last.fm?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}
