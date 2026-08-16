

package iad1tya.echo.music.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AiPlaylistEnabledKey
import iad1tya.echo.music.constants.ChipSortTypeKey
import iad1tya.echo.music.constants.FloatingToolbarBottomPadding
import iad1tya.echo.music.constants.LibraryFilter
import iad1tya.echo.music.constants.MiniPlayerBottomSpacing
import iad1tya.echo.music.constants.MiniPlayerHeight
import iad1tya.echo.music.constants.NavigationBarHeight
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.ui.component.AiPlaylistDialog
import iad1tya.echo.music.ui.component.ChipsRow
import iad1tya.echo.music.ui.component.CreatePlaylistDialog
import iad1tya.echo.music.ui.component.TextFieldDialog
import iad1tya.echo.music.ui.screens.DownloadedOnlyView
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference

@Composable
fun LibraryScreen(navController: NavController) {
    val offlineMode by rememberPreference(OfflineModeKey, false)
    if (offlineMode) {
        // Owner: offline library is downloads-only; nothing stream-only.
        DownloadedOnlyView(navController = navController)
        return
    }

    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)

    var showImportMenu by remember { mutableStateOf(false) }
    var showYoutubeImportDialog by remember { mutableStateOf(false) }
    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showAiPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val (aiPlaylistEnabled) = rememberPreference(AiPlaylistEnabledKey, true)
    val context = LocalContext.current

    BackHandler(enabled = filterType != LibraryFilter.LIBRARY) {
        filterType = LibraryFilter.LIBRARY
    }

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips =
                listOf(
                    LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                    LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                    LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                    LibraryFilter.LOCAL to stringResource(R.string.filter_local),
                ),
                currentValue = filterType,
                onValueUpdate = {
                    filterType =
                        if (filterType == it) {
                            LibraryFilter.LIBRARY
                        } else {
                            it
                        }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val isPlaylists = filterType == LibraryFilter.PLAYLISTS
    // Show the create/import floating buttons on the main Library hub AND the Playlists tab so they're
    // discoverable right away (not buried behind the Playlists chip).
    val showLibraryFab = filterType == LibraryFilter.LIBRARY || filterType == LibraryFilter.PLAYLISTS

    // On the Playlists tab, reserve extra bottom space so the list can scroll clear of the floating
    // action buttons. Other tabs keep the normal player-aware insets untouched.
    val currentInsets = LocalPlayerAwareWindowInsets.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fabColumnClearance = 220.dp
    val paddedInsets = remember(currentInsets, density, layoutDirection) {
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getLeft(density, layoutDirection)

            override fun getTop(density: Density) = currentInsets.getTop(density)

            override fun getRight(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getRight(density, layoutDirection)

            override fun getBottom(density: Density) =
                currentInsets.getBottom(density) + with(density) { fabColumnClearance.roundToPx() }
        }
    }

    // Wrap the screen content AND the floating buttons in one full-size Box so the FABs are guaranteed to
    // overlay the list (as top-level siblings they weren't reliably stacked by the nav host — the reason the
    // import/create buttons weren't showing).
    Box(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(
        LocalPlayerAwareWindowInsets provides if (showLibraryFab) paddedInsets else currentInsets,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            when (filterType) {
                LibraryFilter.LIBRARY -> LibraryMixScreen(navController, filterContent)
                LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
                LibraryFilter.SONGS -> LibrarySongsScreen(
                    navController,
                    { filterType = LibraryFilter.LIBRARY })

                LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                    navController,
                    { filterType = LibraryFilter.LIBRARY })

                LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                    navController,
                    { filterType = LibraryFilter.LIBRARY })

                LibraryFilter.LOCAL -> LocalSongScreen(
                    navController,
                    { filterType = LibraryFilter.LIBRARY },
                    isEmbedded = true
                )
            }
        }
    }

    // Floating actions: create, AI-generate, and import a playlist from Spotify or YouTube Music by URL.
    if (showLibraryFab) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(
                    end = 16.dp,
                    bottom = FloatingToolbarBottomPadding + NavigationBarHeight +
                        MiniPlayerBottomSpacing + MiniPlayerHeight + 16.dp,
                ),
        ) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (aiPlaylistEnabled) {
                    SmallFloatingActionButton(
                        onClick = { showAiPlaylistDialog = true },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.auto_awesome),
                            contentDescription = stringResource(R.string.ai_playlist_title),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.create_playlist)) },
                    icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
                    onClick = { showCreatePlaylistDialog = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )

                Box {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.import_playlist)) },
                        icon = { Icon(painter = painterResource(R.drawable.download), contentDescription = null) },
                        onClick = { showImportMenu = true },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )

                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_from_spotify)) },
                            onClick = {
                                showImportMenu = false
                                showSpotifyImportDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_from_youtube)) },
                            onClick = {
                                showImportMenu = false
                                showYoutubeImportDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.migrate_playlist)) },
                            onClick = {
                                showImportMenu = false
                                navController.navigate("migration")
                            },
                        )
                    }
                }
            }
        }
    }
    }

    if (showYoutubeImportDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
            title = {
                Column {
                    Text(text = stringResource(R.string.import_youtube_playlist_title))
                    Text(
                        text = stringResource(R.string.import_youtube_playlist_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            initialTextFieldValue = TextFieldValue(""),
            autoFocus = true,
            onDismiss = { showYoutubeImportDialog = false },
            onDone = { finalUrl ->
                val listId = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(finalUrl)?.groupValues?.get(1)
                if (listId != null) {
                    navController.navigate("online_playlist/$listId?autoSave=true")
                } else {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.invalid_playlist_url),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                showYoutubeImportDialog = false
            },
        )
    }

    if (showSpotifyImportDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
            title = {
                Column {
                    Text(text = stringResource(R.string.spotify_add_by_link))
                    Text(
                        text = stringResource(R.string.spotify_add_by_link_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            initialTextFieldValue = TextFieldValue(""),
            autoFocus = true,
            onDismiss = { showSpotifyImportDialog = false },
            onDone = { finalUrl ->
                // Accept a share link, a spotify:playlist:… URI, or a bare 22-char id — the same
                // references the import path itself parses (authoritative parse/fetch happens there).
                val isSpotifyPlaylist =
                    Regex("playlist[:/]([A-Za-z0-9]{22})").containsMatchIn(finalUrl) ||
                        finalUrl.trim().matches(Regex("[A-Za-z0-9]{22}"))
                if (isSpotifyPlaylist) {
                    navController.navigate(
                        "settings/spotify_import?link=" + android.net.Uri.encode(finalUrl.trim()),
                    )
                } else {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.invalid_playlist_url),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                showSpotifyImportDialog = false
            },
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = null,
            allowSyncing = true,
            onPlaylistCreated = { playlistId ->
                showCreatePlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            },
        )
    }

    if (showAiPlaylistDialog) {
        AiPlaylistDialog(
            onDismiss = { showAiPlaylistDialog = false },
            onPlaylistCreated = { playlistId ->
                showAiPlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            },
            onOpenAiSettings = {
                showAiPlaylistDialog = false
                navController.navigate("settings/ai")
            },
        )
    }
}
