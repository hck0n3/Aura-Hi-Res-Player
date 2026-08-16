package iad1tya.echo.music.ui.newui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.LocalRingtoneViewModel
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.constants.ExportedSongIdsKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.ExportingSongIdsKey
import iad1tya.echo.music.constants.ListItemHeight
import iad1tya.echo.music.extensions.toggleRepeatMode
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.rememberResolvedAlbum
import iad1tya.echo.music.navigateAsTab
import iad1tya.echo.music.playback.AudioExportService
import iad1tya.echo.music.playback.enqueueSongDownloads
import iad1tya.echo.music.playback.removeSongDownloads
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.component.VolumeSlider
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.ui.menu.ListenTogetherDialog
import iad1tya.echo.music.ui.menu.TempoPitchDialog
import iad1tya.echo.music.echomusic.AudioDeviceBottomSheet
import iad1tya.echo.music.ui.player.rememberHidePlayerVolume
import iad1tya.echo.music.ui.player.showPlayerVolumeControl
import iad1tya.echo.music.ui.utils.ExportFormat
import iad1tya.echo.music.ui.utils.ExportFormatChooserDialog
import iad1tya.echo.music.utils.ShareLinks
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.lookupExportedFileUri
import iad1tya.echo.music.utils.needsOnlineBrowseResolution
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.resolveOnlineAlbumBrowseId
import iad1tya.echo.music.utils.resolveOnlineArtistBrowseId
import iad1tya.echo.music.utils.shareContentUri
import iad1tya.echo.music.utils.shareLocalAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

private const val SECTION_PLAYBACK = "playback"
private const val SECTION_LIBRARY = "library"
private const val SECTION_MORE = "more"

/**
 * # "Interfaz nueva" — Menú del reproductor (acordeón)
 *
 * Header always visible; the rest folds into one-open accordion sections so the sheet stays short
 * and does not duplicate quick-access chrome (like / dislike / download icon).
 *
 * **Presentation only.** Every `onClick` still calls the same service / dialog / route the classic
 * menus use. No sleep timer.
 */
@Composable
fun AuraPlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val ringtoneViewModel = LocalRingtoneViewModel.current

    val playerVolume by playerConnection.service.playerVolume.collectAsState()
    val hidePlayerVolume = rememberHidePlayerVolume()
    val castHandler = remember(playerConnection) {
        runCatching { playerConnection.service.castConnectionHandler }.getOrNull()
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castVolume by castHandler?.castVolume?.collectAsState() ?: remember { mutableFloatStateOf(1f) }
    val castDeviceName by castHandler?.castDeviceName?.collectAsState()
        ?: remember { mutableStateOf<String?>(null) }

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)

    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST
    val pendingSuggestions by listenTogetherManager?.pendingSuggestions
        ?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val librarySong by database
        .songWithEquivalent(
            mediaMetadata.id,
            mediaMetadata.title,
            mediaMetadata.artists.firstOrNull()?.name,
        )
        .collectAsState(initial = null)
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val videoMode by playerConnection.videoMode.collectAsState()

    val artists = remember(mediaMetadata.artists) { mediaMetadata.artists }
    val resolvedAlbum = rememberResolvedAlbum(
        songId = mediaMetadata.id,
        initial = mediaMetadata.album,
        dbAlbumId = librarySong?.song?.albumId,
        dbAlbumName = librarySong?.song?.albumName,
    )

    val (exportDirectoryUri, onExportDirectoryUriChange) = rememberPreference(ExportDirectoryUriKey, "")
    val (exportingSongIds) = rememberPreference(ExportingSongIdsKey, "")
    val (exportedSongIds) = rememberPreference(ExportedSongIdsKey, "")
    val (exportedVideoIds) = rememberPreference(ExportedVideoIdsKey, "")
    val ensureMp3Folder = iad1tya.echo.music.ui.utils.rememberMp3ExportFolderAccess(
        exportDirectoryUri = exportDirectoryUri,
        onExportDirectoryUriChange = onExportDirectoryUriChange,
    )
    val isExporting = remember(exportingSongIds, mediaMetadata.id) {
        exportingSongIds.split(",").contains(mediaMetadata.id)
    }
    val isExported = remember(exportedSongIds, exportedVideoIds, mediaMetadata.id) {
        val id = mediaMetadata.id
        exportedSongIds.split(",").contains(id) || exportedVideoIds.split(",").contains(id)
    }
    val isExportedVideo = remember(exportedVideoIds, mediaMetadata.id) {
        exportedVideoIds.split(",").contains(mediaMetadata.id)
    }
    val isLocalTrack = mediaMetadata.id.isLocalMediaId() || currentSong?.song?.isLocal == true

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showListenTogetherDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }
    var showPitchTempoDialog by rememberSaveable { mutableStateOf(false) }
    var showAudioDeviceSheet by rememberSaveable { mutableStateOf(false) }
    var showExportFormatDialog by rememberSaveable { mutableStateOf(false) }
    // Start collapsed: pinned rows (artist/album/EQ/details/settings) are the only visible actions.
    var openSection by rememberSaveable { mutableStateOf("") }

    fun toggleSection(key: String) {
        openSection = if (openSection == key) "" else key
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            database.withTransaction { insert(mediaMetadata) }
            onDismiss()
            listOf(mediaMetadata.id)
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    ListenTogetherDialog(
        visible = showListenTogetherDialog,
        mediaMetadata = mediaMetadata,
        onDismiss = { showListenTogetherDialog = false },
    )

    if (showPitchTempoDialog) {
        TempoPitchDialog(onDismiss = { showPitchTempoDialog = false })
    }

    if (showAudioDeviceSheet) {
        AudioDeviceBottomSheet(onDismiss = { showAudioDeviceSheet = false })
    }

    if (showExportFormatDialog) {
        ExportFormatChooserDialog(
            songId = mediaMetadata.id,
            includeOfflineDownload = true,
            hasMusicVideo = mediaMetadata.isVideoSong ||
                !mediaMetadata.podcastVideoUrl.isNullOrEmpty(),
            onDismiss = { showExportFormatDialog = false },
            onChoose = { format ->
                when (format) {
                    ExportFormat.Offline -> {
                        database.transaction { insert(mediaMetadata) }
                        val watching =
                            videoMode && playerConnection.mediaMetadata.value?.id == mediaMetadata.id
                        enqueueSongDownloads(
                            context,
                            mediaMetadata.id,
                            mediaMetadata.title,
                            isVideoSong = mediaMetadata.isVideoSong,
                            deferWhileLiveVideo = watching,
                        )
                        onDismiss()
                    }
                    ExportFormat.Mp3, ExportFormat.Video -> {
                        ensureMp3Folder { directoryUri ->
                            onDismiss()
                            AudioExportService.start(
                                context = context,
                                songId = mediaMetadata.id,
                                songTitle = mediaMetadata.title,
                                songArtist = artists.joinToString(", ") { it.name },
                                songAlbum = mediaMetadata.album?.title ?: "",
                                artworkUrl = mediaMetadata.thumbnailUrl ?: "",
                                targetDirectoryUri = directoryUri,
                                exportAsVideo = format == ExportFormat.Video,
                            )
                        }
                    }
                }
            },
        )
    }

    if (showSelectArtistDialog) {
        ListDialog(onDismiss = { showSelectArtistDialog = false }) {
            items(artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            coroutineScope.launch {
                                val browseId = if (needsOnlineBrowseResolution(artist.id)) {
                                    withContext(Dispatchers.IO) {
                                        resolveOnlineArtistBrowseId(artist.name)
                                    }
                                } else {
                                    artist.id
                                }
                                if (!browseId.isNullOrBlank()) {
                                    navController.navigate("artist/$browseId")
                                } else {
                                    navController.navigate(
                                        "search/${URLEncoder.encode(artist.name, "UTF-8")}"
                                    )
                                }
                                showSelectArtistDialog = false
                                playerBottomSheetState.collapseSoft()
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                }
            }
        }
    }

    val isInLibrary = librarySong?.song?.inLibrary != null
    val hasVideo = mediaMetadata.isVideoSong ||
        isExportedVideo ||
        !mediaMetadata.podcastVideoUrl.isNullOrEmpty()
    // Sheet already paints FrostFill ([BottomSheetMenu] + LocalAuraFloatingChrome).
    val floatingChrome = LocalAuraFloatingChrome.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .auraBleedHorizontal(20.dp)
            .background(if (floatingChrome) Color.Transparent else AuraPalette.GroundRaised),
        contentPadding = PaddingValues(
            bottom = 16.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        // ── Cabecera (siempre) ────────────────────────────────────────────────────────────────────
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Gutter, vertical = 12.dp),
            ) {
                AuraCover(
                    thumbnailUrl = mediaMetadata.thumbnailUrl,
                    size = 48.dp,
                    seed = mediaMetadata.id,
                    fillBleed = true,
                    ratio = if (mediaMetadata.isVideoSong) 16f / 9f else 1f,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = mediaMetadata.title,
                        style = AuraType.RowTitle,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                    Text(
                        text = mediaMetadata.artists.joinToString(", ") { it.name },
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                }
            }
        }

        if (isCasting && castDeviceName != null) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AuraSpacing.Gutter, end = AuraSpacing.Gutter, bottom = 8.dp),
                ) {
                    AuraIconGlyph(AuraIcons.Cast, null, size = 18.dp, tint = AuraPalette.Teal)
                    AuraTechnicalText(
                        text = stringResource(R.string.casting_to, castDeviceName ?: ""),
                        color = AuraPalette.Teal,
                    )
                }
            }
        }

        if (showPlayerVolumeControl(hidePlayerVolume)) {
            item {
                VolumeSlider(
                    value = if (isCasting) castVolume else playerVolume,
                    onValueChange = { volume ->
                        if (isCasting) castHandler?.setVolume(volume)
                        else playerConnection.service.playerVolume.value = volume
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Gutter),
                    accentColor = AuraPalette.Teal,
                )
            }
        }

        // Streaming quality cycling (Opus / Saavn / Qobuz) removed — Opus is the only stream path.
        item {
            Spacer(Modifier.height(6.dp))
            AuraDivider()
        }

        // ── Fijas (siempre visibles) ───────────────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (artists.isNotEmpty()) {
                    AuraMenuRow(
                        icon = AuraIcons.Artist,
                        label = stringResource(R.string.view_artist),
                        onClick = {
                            if (artists.size == 1) {
                                val only = artists[0]
                                coroutineScope.launch {
                                    val browseId = if (needsOnlineBrowseResolution(only.id)) {
                                        withContext(Dispatchers.IO) {
                                            resolveOnlineArtistBrowseId(only.name)
                                        }
                                    } else {
                                        only.id
                                    }
                                    if (!browseId.isNullOrBlank()) {
                                        navController.navigate("artist/$browseId")
                                    } else {
                                        navController.navigate(
                                            "search/${URLEncoder.encode(only.name, "UTF-8")}"
                                        )
                                    }
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                }
                            } else {
                                showSelectArtistDialog = true
                            }
                        },
                    )
                }
                resolvedAlbum?.let { album ->
                    AuraMenuRow(
                        icon = AuraIcons.Album,
                        label = stringResource(R.string.view_album),
                        onClick = {
                            coroutineScope.launch {
                                val browseId = if (needsOnlineBrowseResolution(album.id)) {
                                    val query = listOfNotNull(
                                        album.title.takeIf { it.isNotBlank() },
                                        artists.joinToString(" ") { it.name }.takeIf { it.isNotBlank() },
                                    ).joinToString(" ")
                                    withContext(Dispatchers.IO) {
                                        resolveOnlineAlbumBrowseId(query)
                                    }
                                } else {
                                    album.id
                                }
                                if (!browseId.isNullOrBlank()) {
                                    navController.navigate("album/$browseId")
                                } else if (album.title.isNotBlank()) {
                                    navController.navigate(
                                        "search/${URLEncoder.encode(album.title, "UTF-8")}"
                                    )
                                }
                                playerBottomSheetState.collapseSoft()
                                onDismiss()
                            }
                        },
                    )
                }
                AuraMenuRow(
                    icon = AuraIcons.Equalizer,
                    label = stringResource(R.string.equalizer),
                    onClick = {
                        playerBottomSheetState.collapseSoft()
                        navController.navigate("settings/equalizer") { launchSingleTop = true }
                        onDismiss()
                    },
                )
                AuraMenuRow(
                    icon = AuraIcons.Volume,
                    label = "Sonido",
                    onClick = {
                        playerBottomSheetState.collapseSoft()
                        navController.navigate("settings/sound")
                        onDismiss()
                    },
                )
                AuraMenuRow(
                    icon = AuraIcons.Search,
                    label = stringResource(R.string.details),
                    onClick = {
                        onShowDetailsDialog()
                        onDismiss()
                    },
                )
                AuraMenuRow(
                    icon = AuraIcons.Settings,
                    label = stringResource(R.string.settings),
                    onClick = {
                        playerBottomSheetState.collapseSoft()
                        navController.navigateAsTab("settings")
                        onDismiss()
                    },
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            AuraDivider()
        }

        // ── REPRODUCCIÓN (contraída) ───────────────────────────────────────────────────────────────
        item {
            AuraMenuExpandableSection(
                title = "Reproducción",
                expanded = openSection == SECTION_PLAYBACK,
                onToggle = { toggleSection(SECTION_PLAYBACK) },
            ) {
                if (!isListenTogetherGuest) {
                    AuraMenuRow(
                        icon = AuraIcons.Shuffle,
                        label = stringResource(R.string.shuffle),
                        iconTint = if (shuffleModeEnabled) AuraPalette.Teal
                        else AuraPalette.OnGround.copy(alpha = 0.75f),
                        onClick = {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                            onDismiss()
                        },
                        trailing = {
                            AuraSwitch(
                                checked = shuffleModeEnabled,
                                onCheckedChange = {
                                    playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                                    onDismiss()
                                },
                                contentDescription = stringResource(R.string.shuffle),
                            )
                        },
                    )
                    AuraMenuRow(
                        icon = AuraIcons.Repeat,
                        label = stringResource(R.string.repeat),
                        iconTint = if (repeatMode != Player.REPEAT_MODE_OFF) AuraPalette.Teal
                        else AuraPalette.OnGround.copy(alpha = 0.75f),
                        onClick = { playerConnection.player.toggleRepeatMode() },
                        trailing = {
                            AuraTechnicalText(
                                text = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> "UNA"
                                    Player.REPEAT_MODE_ALL -> "TODO"
                                    else -> "NO"
                                },
                                color = if (repeatMode != Player.REPEAT_MODE_OFF) AuraPalette.Teal
                                else AuraPalette.OnGroundDisabled,
                            )
                        },
                    )
                    val startingRadioText = stringResource(R.string.starting_radio)
                    AuraMenuRow(
                        icon = AuraIcons.Radio,
                        label = stringResource(R.string.start_radio),
                        onClick = {
                            Toast.makeText(context, startingRadioText, Toast.LENGTH_SHORT).show()
                            playerConnection.startRadioSeamlessly()
                            onDismiss()
                        },
                    )
                }
                AuraMenuRow(
                    icon = AuraIcons.Speed,
                    label = stringResource(R.string.advanced),
                    onClick = { showPitchTempoDialog = true },
                )
                AuraMenuRow(
                    icon = AuraIcons.Radio,
                    label = stringResource(R.string.refetch),
                    onClick = { playerConnection.refetchCurrentInOpus() },
                )
            }
        }

        // ── BIBLIOTECA ────────────────────────────────────────────────────────────────────────────
        item {
            AuraMenuExpandableSection(
                title = "Biblioteca",
                expanded = openSection == SECTION_LIBRARY,
                onToggle = { toggleSection(SECTION_LIBRARY) },
            ) {
                AuraMenuRow(
                    icon = if (isInLibrary) AuraIcons.Check else AuraIcons.Plus,
                    label = stringResource(
                        if (isInLibrary) R.string.remove_from_library else R.string.add_to_library
                    ),
                    onClick = {
                        playerConnection.toggleLibrary()
                        onDismiss()
                    },
                )
                AuraMenuRow(
                    icon = AuraIcons.PlaylistAdd,
                    label = stringResource(R.string.add_to_playlist),
                    onClick = { showChoosePlaylistDialog = true },
                )
                AuraMenuRow(
                    icon = AuraIcons.Timer,
                    label = stringResource(R.string.set_as_ringtone),
                    onClick = {
                        ringtoneViewModel.showTrimmer(
                            mediaMetadata.id,
                            mediaMetadata.title,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                        )
                        onDismiss()
                    },
                )
            }
        }

        // ── MÁS OPCIONES (resto contraído) ─────────────────────────────────────────────────────────
        item {
            AuraMenuExpandableSection(
                title = "Más opciones",
                expanded = openSection == SECTION_MORE,
                onToggle = { toggleSection(SECTION_MORE) },
            ) {
                if (mediaMetadata.id.startsWith("http")) {
                    AuraMenuRow(
                        icon = AuraIcons.Queue,
                        label = stringResource(R.string.go_to_podcast),
                        onClick = {
                            val epId = mediaMetadata.id
                            coroutineScope.launch {
                                val feed = iad1tya.echo.music.podcast.PodcastStoreEntryPoint
                                    .get(context).get(epId)?.feedUrl
                                playerBottomSheetState.collapseSoft()
                                onDismiss()
                                navController.navigate(
                                    if (!feed.isNullOrBlank())
                                        "podcasts?feedUrl=" + URLEncoder.encode(feed, "UTF-8")
                                    else "podcasts"
                                )
                            }
                        },
                    )
                }
                if (hasVideo) {
                    AuraMenuRow(
                        icon = AuraIcons.Video,
                        label = "Vídeo",
                        iconTint = if (videoMode) AuraPalette.Teal
                        else AuraPalette.OnGround.copy(alpha = 0.75f),
                        onClick = {
                            playerConnection.toggleVideoMode()
                            onDismiss()
                        },
                    )
                }
                AuraMenuRow(
                    icon = AuraIcons.Equalizer,
                    label = "Sonido",
                    onClick = {
                        playerBottomSheetState.collapseSoft()
                        navController.navigate("settings/sound")
                        onDismiss()
                    },
                )
                AuraMenuRow(
                    icon = AuraIcons.Volume,
                    label = stringResource(R.string.audio_devices),
                    onClick = { showAudioDeviceSheet = true },
                )
                AuraMenuRow(
                    icon = AuraIcons.People,
                    label = stringResource(R.string.listen_together),
                    onClick = { showListenTogetherDialog = true },
                    trailing = if (pendingSuggestions.isEmpty()) null else {
                        {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(AuraShapes.Pill)
                                    .background(AuraPalette.Teal)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                AuraTechnicalText(
                                    text = pendingSuggestions.size.toString(),
                                    color = AuraPalette.OnAccent,
                                )
                            }
                        }
                    },
                )
                if (isListenTogetherGuest) {
                    AuraMenuRow(
                        icon = AuraIcons.Radio,
                        label = stringResource(R.string.resync),
                        onClick = {
                            listenTogetherManager?.requestSync()
                            onDismiss()
                        },
                    )
                }
                AuraMenuRow(
                    icon = AuraIcons.Album,
                    label = stringResource(R.string.ambient_mode),
                    onClick = {
                        playerBottomSheetState.collapseSoft()
                        navController.navigate("ambient_mode") { launchSingleTop = true }
                        onDismiss()
                    },
                )
                AuraMenuRow(
                    icon = AuraIcons.Share,
                    label = stringResource(R.string.share),
                    onClick = {
                        coroutineScope.launch {
                            if (isLocalTrack) {
                                shareLocalAudio(context, mediaMetadata.id)
                                onDismiss()
                                return@launch
                            }
                            if (isExported) {
                                val uri = lookupExportedFileUri(context, mediaMetadata.id)
                                if (uri != null &&
                                    shareContentUri(
                                        context,
                                        uri,
                                        if (isExportedVideo) "video/mp4" else "audio/mpeg",
                                    )
                                ) {
                                    onDismiss()
                                    return@launch
                                }
                            }
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, ShareLinks.song(mediaMetadata.id))
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                            onDismiss()
                        }
                    },
                )
            }
        }
    }

}

/**
 * Accordion group header (Settings-style chevron) for the player Más menu. Only one section should
 * be open at a time — the host owns that via [expanded] / [onToggle].
 */
@Composable
private fun AuraMenuExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    sectionBody: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = AuraMotion.float,
        label = "aura-player-menu-chevron",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = title, role = Role.Button, onClick = onToggle)
                .padding(
                    start = AuraSpacing.Gutter,
                    end = AuraSpacing.Gutter,
                    top = 15.dp,
                    bottom = 7.dp,
                ),
        ) {
            Text(
                text = title,
                style = AuraType.MenuGroupLabel.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = AuraPalette.OnGroundMuted,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = AuraIcons.ChevronRight,
                contentDescription = null,
                tint = AuraPalette.OnGroundDisabled,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = AuraMotion.intSize) +
                fadeIn(animationSpec = AuraMotion.float),
            exit = shrinkVertically(animationSpec = AuraMotion.intSize) +
                fadeOut(animationSpec = AuraMotion.float),
        ) {
            Column(Modifier.fillMaxWidth()) {
                sectionBody()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Grows the content horizontally by [amount] on each side and shifts it back, so a child can paint
 * edge-to-edge inside a parent that already applied a horizontal padding. No-op when the incoming
 * constraints are unbounded.
 */
private fun Modifier.auraBleedHorizontal(amount: Dp): Modifier = this.layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extra = amount.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = width, maxWidth = width),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
