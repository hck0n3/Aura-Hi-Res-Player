package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import iad1tya.echo.music.ui.component.AiPlaylistDialog
import iad1tya.echo.music.ui.component.CreatePlaylistDialog
import iad1tya.echo.music.ui.component.TextFieldDialog
import iad1tya.echo.music.ui.screens.library.LocalSongScreen
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference

/**
 * # Biblioteca — "Interfaz nueva"
 *
 * The render's Biblioteca: title, a search glyph, the pill filter chips, and rows whose right edge
 * carries the quality badge or the download tick.
 *
 * ## Same shell, same state, same actions as the classic Library
 * The five tabs are not five routes — they are one screen switched by [ChipSortTypeKey], and this
 * screen reads and writes THAT key, so which tab you were on survives toggling "Interfaz nueva" in
 * either direction. The floating actions open the SAME `CreatePlaylistDialog` / `AiPlaylistDialog` /
 * `TextFieldDialog` with the same parse rules and the same navigation targets.
 *
 * ## Surfaces reused as-is
 *  · The **Local** tab is `LocalSongScreen` verbatim. It is its own inventory section (17.1.7) with a
 *    whole scan sheet — permissions, folder exclusions, a duration slider — and it is not one of the
 *    six screens in this beta.
 *  · The **dialogs** (create / AI / import by URL) are classic. They are modal surfaces, not screens.
 *
 * ## Not drawn here
 * The mini player and the bottom bar in the render's Biblioteca belong to the app skeleton.
 */
@Composable
fun AuraLibraryScreen(navController: NavController) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)

    var showImportMenu by remember { mutableStateOf(false) }
    var showYoutubeImportDialog by remember { mutableStateOf(false) }
    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showAiPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val (aiPlaylistEnabled) = rememberPreference(AiPlaylistEnabledKey, true)
    val context = LocalContext.current

    BackHandler(enabled = filterType != LibraryFilter.LIBRARY) {
        filterType = LibraryFilter.LIBRARY
    }

    // Biblioteca has no now-playing context of its own; the render dims its bloom to .40.
    val bloom = rememberAuraBloom(null)

    val isPlaylists = filterType == LibraryFilter.LIBRARY || filterType == LibraryFilter.PLAYLISTS

    // Same trick as the classic screen: on the tabs that show the floating buttons, extend the bottom
    // inset so the list can scroll clear of them.
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
    // The header + chip row are laid out ABOVE the tab list, so they must consume the top inset
    // themselves and the list must not add it a second time. (Before, nothing consumed it: the chips
    // were laid out at y = 0, i.e. behind the status bar and behind the top bar that used to be drawn
    // here, while each tab list still reserved the full top inset below them.)
    val topInsetOnly = remember(currentInsets) { currentInsets.only(WindowInsetsSides.Top) }
    val tabInsets = remember(paddedInsets, currentInsets, isPlaylists) {
        val source = if (isPlaylists) paddedInsets else currentInsets
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
                source.getLeft(density, layoutDirection)

            override fun getTop(density: Density) = 0

            override fun getRight(density: Density, layoutDirection: LayoutDirection) =
                source.getRight(density, layoutDirection)

            override fun getBottom(density: Density) = source.getBottom(density)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.40f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(topInsetOnly),
        ) {
            // The render's own "Biblioteca" title. It used to be omitted because MainActivity's opaque
            // TopAppBar drew it, and two titles would have been worse than a Material one — but that bar
            // is exactly the "barra negra fea" of the beta verdict and is no longer drawn on this route
            // (MainActivity gates it on `auraOwnsHeader`). So the title comes home to the content, where
            // the render puts it, and the global actions the bar carried ride in its trailing slot.
            AuraScreenHeader(
                title = stringResource(R.string.filter_library),
                trailing = { AuraTopActions() },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(auraLibraryChips(), key = { it.first.name }) { (chip, labelRes) ->
                        AuraChip(
                            text = stringResource(labelRes),
                            selected = filterType == chip,
                            // Tapping the chip that is already active returns to the hub — as today.
                            onClick = {
                                filterType = if (filterType == chip) LibraryFilter.LIBRARY else chip
                            },
                        )
                    }
                }
                // The render's search glyph. Only the tabs that own a search field (Canciones,
                // Artistas, Listas) offer it; the hub, Álbumes and Local have none, so it is absent
                // there rather than drawn dead.
                if (auraTabHasSearch(filterType)) {
                    AuraIconButton(
                        icon = AuraIcons.Search,
                        contentDescription = stringResource(R.string.search_library),
                        onClick = { searchOpen = !searchOpen },
                        size = 20.dp,
                        tint = if (searchOpen) AuraPalette.Teal else AuraPalette.OnGroundFaint,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }

            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides tabInsets,
            ) {
                Box(Modifier.fillMaxSize()) {
                    when (filterType) {
                        LibraryFilter.LIBRARY -> AuraLibraryHub(navController)
                        LibraryFilter.PLAYLISTS -> AuraLibraryPlaylistsTab(navController, searchOpen)
                        LibraryFilter.SONGS -> AuraLibrarySongsTab(navController, searchOpen)
                        LibraryFilter.ALBUMS -> AuraLibraryAlbumsTab(navController)
                        LibraryFilter.ARTISTS -> AuraLibraryArtistsTab(navController, searchOpen)
                        // ── Local: the classic screen, but as a BODY and not as a second screen ──────
                        // Its scan sheet (permissions, excluded folders, a minimum-duration slider) is
                        // a whole surface of its own and is not one of the six screens in this beta, so
                        // the screen itself is still reused verbatim. What is NOT reused is its chrome:
                        // it used to draw its own LargeTopAppBar with a "Local" title, a back arrow and
                        // a gear directly under this screen's header — two headers, one on top of the
                        // other, which is what the beta verdict shows. `hideChrome` drops exactly that
                        // (search and the scan gear survive as a bare action row, see LocalSongScreen).
                        //
                        // The colour scheme is the second half of the same problem. The redesign paints
                        // its own dark ground and does NOT install a Material theme, so a user on the
                        // light theme got a classic screen rendering its text and surfaces in LIGHT
                        // colours on top of that dark ground — the "pantalla blanca" of the report. The
                        // embedded screen is therefore given a dark scheme anchored on the Aura palette
                        // for as long as it is embedded; nothing outside this branch sees it.
                        LibraryFilter.LOCAL -> MaterialTheme(
                            colorScheme = AuraEmbeddedScheme,
                            typography = MaterialTheme.typography,
                            shapes = MaterialTheme.shapes,
                        ) {
                            LocalSongScreen(
                                navController,
                                { filterType = LibraryFilter.LIBRARY },
                                isEmbedded = true,
                                hideChrome = true,
                            )
                        }
                    }
                }
            }
        }

        // Floating actions: AI-generate, create, and import a playlist by URL.
        if (isPlaylists) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (aiPlaylistEnabled) {
                        AuraFab(
                            icon = AuraIcons.Radio,
                            label = null,
                            contentDescription = stringResource(R.string.ai_playlist_title),
                            onClick = { showAiPlaylistDialog = true },
                        )
                    }
                    AuraFab(
                        icon = AuraIcons.Plus,
                        label = stringResource(R.string.create_playlist),
                        contentDescription = stringResource(R.string.create_playlist),
                        onClick = { showCreatePlaylistDialog = true },
                    )
                    Box {
                        AuraFab(
                            icon = AuraIcons.Download,
                            label = stringResource(R.string.import_playlist),
                            contentDescription = stringResource(R.string.import_playlist),
                            onClick = { showImportMenu = true },
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

    // ── Dialogs: classic, verbatim, same parse rules and same destinations ────────────────────────

    if (showYoutubeImportDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
            title = {
                Column {
                    Text(text = stringResource(R.string.import_youtube_playlist_title))
                    Text(
                        text = stringResource(R.string.import_youtube_playlist_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
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
                    navController.navigate("online_playlist/$listId")
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
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            initialTextFieldValue = TextFieldValue(""),
            autoFocus = true,
            onDismiss = { showSpotifyImportDialog = false },
            onDone = { finalUrl ->
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

/**
 * The Material scheme a CLASSIC screen is rendered with while it is embedded in a redesigned one.
 *
 * The redesign draws with [AuraPalette] directly and never installs a Material theme, so a reused
 * classic surface keeps whatever scheme the app is on — light, for most users. Inside the Aura ground
 * that means dark text and pale containers over near-black: unreadable, and the reason the Local tab
 * looked like a separate white screen. Mapping the Material roles the reused screens actually read
 * onto the Aura palette makes them blend instead. Backgrounds are [AuraPalette.Ground] rather than
 * transparent on purpose: the sheets and menus these screens open must stay opaque.
 */
private val AuraEmbeddedScheme = darkColorScheme(
    primary = AuraPalette.Teal,
    onPrimary = AuraPalette.OnAccent,
    secondary = AuraPalette.Blue,
    onSecondary = AuraPalette.OnAccent,
    tertiary = AuraPalette.Violet,
    onTertiary = AuraPalette.OnGround,
    background = AuraPalette.Ground,
    onBackground = AuraPalette.OnGround,
    surface = AuraPalette.Ground,
    onSurface = AuraPalette.OnGround,
    surfaceVariant = AuraPalette.GroundRaised,
    onSurfaceVariant = AuraPalette.OnGroundMuted,
    surfaceContainerLowest = AuraPalette.Ground,
    surfaceContainerLow = AuraPalette.GroundRaised,
    surfaceContainer = AuraPalette.GroundRaised,
    surfaceContainerHigh = AuraPalette.GroundRaised,
    surfaceContainerHighest = AuraPalette.GroundRaised,
    outline = AuraPalette.OnGroundDisabled,
    outlineVariant = AuraPalette.SurfaceLine,
)

/** The five filter chips, in the classic order. */
private fun auraLibraryChips(): List<Pair<LibraryFilter, Int>> = listOf(
    LibraryFilter.PLAYLISTS to R.string.filter_playlists,
    LibraryFilter.SONGS to R.string.filter_songs,
    LibraryFilter.ALBUMS to R.string.filter_albums,
    LibraryFilter.ARTISTS to R.string.filter_artists,
    LibraryFilter.LOCAL to R.string.filter_local,
)

/** Which tabs actually own a search field today. Álbumes and the hub do not — see the inventory. */
private fun auraTabHasSearch(filter: LibraryFilter): Boolean = when (filter) {
    LibraryFilter.SONGS, LibraryFilter.ARTISTS, LibraryFilter.PLAYLISTS -> true
    else -> false
}

/**
 * A floating action in the new language: a `SurfaceFill` pill with a hairline and a teal glyph.
 * [label] `null` draws the small round variant (the AI button); otherwise it is an extended pill.
 */
@Composable
private fun AuraFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = modifier
        .height(52.dp)
        .clip(AuraShapes.Pill)
        .background(AuraPalette.SurfaceFill)
        .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
        .auraClickableInternal(onClick = onClick, contentDescription = contentDescription)

    if (label == null) {
        Box(base.size(52.dp), contentAlignment = Alignment.Center) {
            AuraIconGlyph(icon, null, size = 22.dp, tint = AuraPalette.Teal)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = base.padding(horizontal = 20.dp),
        ) {
            AuraIconGlyph(icon, null, size = 20.dp, tint = AuraPalette.Teal)
            Text(
                text = label,
                style = AuraType.Chip,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
        }
    }
}
