@file:OptIn(ExperimentalMaterial3Api::class)

package iad1tya.echo.music.ui.screens.migration

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.migration.model.SourcePlaylist
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.TidalClientIdKey
import iad1tya.echo.music.migration.TidalAuthCallbackBus
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.rememberPreference

/**
 * Tidal migration — client-id setup + real OAuth (PKCE) sign-in + import.
 *
 * Tidal's Open API needs a client id (PKCE, no secret). This project is open source, so no id can be
 * committed; the owner pastes their own from developer.tidal.com and it is stored via [TidalClientIdKey]
 * (kept off any committed source). Once a client id is present, the user can sign in: [beginTidalLogin]
 * opens the authorize URL in a Chrome Custom Tab, the redirect returns through
 * echomusic://tidal-callback -> [TidalAuthCallbackBus] -> [MigrationViewModel.completeTidalLogin]. After
 * auth the user picks a playlist from their collection OR pastes a playlist URL; either flows into the
 * SAME CONFIRM -> RUNNING -> DONE machinery as the file/Deezer paths.
 *
 * The ViewModel is scoped to the "migration" back-stack entry (shared with [MigrationScreen]) so that
 * once a Tidal playlist is prepared, popping back to the picker shows the existing confirm/progress/
 * result/ambiguous-review UI without duplicating any of it.
 */
@Composable
fun MigrationTidalScreen(navController: NavController) {
    val context = LocalContext.current
    val (savedClientId, setSavedClientId) = rememberPreference(TidalClientIdKey, "")
    var clientIdField by remember(savedClientId) { mutableStateOf(savedClientId) }

    // Share the picker's ViewModel so a prepared Tidal import reuses the same state machine + engine.
    val migrationEntry = remember(navController) { navController.getBackStackEntry("migration") }
    val viewModel: MigrationViewModel = hiltViewModel(migrationEntry)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tidalState by viewModel.tidalState.collectAsStateWithLifecycle()

    var urlField by remember { mutableStateOf("") }

    // Re-derive login from the encrypted token vault whenever the screen is shown.
    LaunchedEffect(Unit) { viewModel.refreshTidalAuth() }

    // Open the authorize URL once beginTidalLogin has built it off-Main, then clear it so a recomposition
    // can't re-open the browser.
    LaunchedEffect(tidalState.pendingAuthUrl) {
        tidalState.pendingAuthUrl?.let { url ->
            openAuthUrl(context, url)
            viewModel.consumeTidalAuthUrl()
        }
    }

    // Receive the OAuth redirect (delivered by MainActivity into the callback bus). replay=1 means we
    // catch it even if this screen resumed a beat after the Custom Tab closed; clear() drops the used code.
    LaunchedEffect(Unit) {
        TidalAuthCallbackBus.events.collect { cb ->
            when {
                !cb.error.isNullOrBlank() -> {
                    viewModel.tidalLoginFailed(cb.error!!)
                    TidalAuthCallbackBus.clear()
                }
                !cb.code.isNullOrBlank() -> {
                    viewModel.completeTidalLogin(cb.code!!, cb.state)
                    TidalAuthCallbackBus.clear()
                }
            }
        }
    }

    // A prepared Tidal import moves the shared state to CONFIRM; hand off to the picker, which renders it.
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == MigrationPhase.CONFIRM) navController.navigateUp()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.migrate_source_tidal)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.migrate_tidal_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Client id entry
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.migrate_tidal_clientid_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.migrate_tidal_clientid_how),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = clientIdField,
                        onValueChange = { clientIdField = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.migrate_tidal_clientid_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { openExternal(context, "https://developer.tidal.com/dashboard") },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.migrate_tidal_open_dashboard)) }
                        Button(
                            onClick = { setSavedClientId(clientIdField.trim()) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.migrate_tidal_save)) }
                    }
                    if (savedClientId.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.migrate_tidal_saved),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Sign-in / import — only once a client id exists (nothing to authenticate against otherwise).
            if (savedClientId.isNotBlank()) {
                if (!tidalState.authenticated) {
                    LoginCard(
                        loggingIn = tidalState.loggingIn,
                        // beginTidalLogin builds the URL OFF-Main and posts it to pendingAuthUrl; the
                        // LaunchedEffect below opens it. Keeps the button tap free of blocking I/O.
                        onLogin = { viewModel.beginTidalLogin() },
                    )
                } else {
                    ConnectedCard(
                        state = tidalState,
                        urlField = urlField,
                        onUrlChange = { urlField = it },
                        onImportUrl = { viewModel.prepareTidalImport(urlField.trim()) },
                        onPickPlaylist = { viewModel.prepareTidalPlaylist(it) },
                        onReload = { viewModel.loadTidalCollection() },
                        onLogout = { viewModel.logoutTidal() },
                    )
                }

                tidalState.error?.let { message ->
                    ErrorCard(text = message, onDismiss = { viewModel.dismissTidalError() })
                }
            }

            // The file route always works, with or without a client id / login.
            Text(
                text = stringResource(R.string.migrate_tidal_use_file),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoginCard(loggingIn: Boolean, onLogin: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.migrate_tidal_login_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.migrate_tidal_login_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onLogin,
                enabled = !loggingIn,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (loggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.migrate_tidal_login))
                }
            }
        }
    }
}

@Composable
private fun ConnectedCard(
    state: TidalAuthUiState,
    urlField: String,
    onUrlChange: (String) -> Unit,
    onImportUrl: () -> Unit,
    onPickPlaylist: (SourcePlaylist) -> Unit,
    onReload: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.migrate_tidal_connected),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onLogout) {
                    Text(stringResource(R.string.migrate_tidal_logout))
                }
            }

            // Collection: nicer path — tap a playlist to import it.
            Text(
                text = stringResource(R.string.migrate_tidal_pick_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            when {
                state.collectionLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.migrate_tidal_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.collection.isNotEmpty() -> {
                    state.collection.forEach { playlist ->
                        PlaylistRow(playlist = playlist, onClick = { onPickPlaylist(playlist) })
                    }
                    TextButton(onClick = onReload) {
                        Text(stringResource(R.string.migrate_tidal_reload))
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.migrate_tidal_no_playlists),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onReload) {
                        Text(stringResource(R.string.migrate_tidal_reload))
                    }
                }
            }

            HorizontalDivider()

            // URL field: minimal, always-works path — import a specific playlist by link.
            Text(
                text = stringResource(R.string.migrate_tidal_url_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = urlField,
                onValueChange = onUrlChange,
                singleLine = true,
                label = { Text(stringResource(R.string.migrate_tidal_url_label)) },
                placeholder = { Text("https://tidal.com/playlist/…") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onImportUrl,
                enabled = urlField.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.migrate_tidal_import))
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: SourcePlaylist, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.migrate_tidal_track_count, playlist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorCard(text: String, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

// Opens the Tidal authorize URL in a Chrome Custom Tab (mirrors the license screen's Gumroad checkout —
// the user stays in-app and the redirect returns cleanly). Falls back to any external browser.
private fun openAuthUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching {
        androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

private fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
