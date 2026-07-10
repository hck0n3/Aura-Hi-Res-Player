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
import iad1tya.echo.music.spotifyimport.SpotifyImportViewModel
import iad1tya.echo.music.ui.component.*
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AccountSettingsViewModel
import iad1tya.echo.music.viewmodels.HomeViewModel

/**
 * "Cuentas": one card per connected music service (YouTube Music + Spotify). Pure aggregation of the
 * EXISTING auth state / login routes / logout actions — this screen adds NO new authentication logic.
 *
 * - YouTube Music: logged in = the InnerTube cookie carries SAPISID. Identity from AccountName/Email/
 *   ChannelHandle prefs + HomeViewModel avatar. Login = route "login"; logout = the existing 3-option
 *   dialog via AccountSettingsViewModel (keep data / clear synced data).
 * - Spotify: state + logout come straight from the existing SpotifyImportViewModel; connect opens the
 *   existing "settings/spotify_import" screen.
 *
 * Discord / Last.fm are intentionally omitted (no login UI in the fork).
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

    var showYtLogoutDialog by remember { mutableStateOf(false) }
    var showSpotifyLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuentas") },
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
    }
}
