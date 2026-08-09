package iad1tya.echo.music.ui.newui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.navigateAsTab
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.LocalRingtoneViewModel
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableExportAsMp3Key
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.constants.ExportedSongIdsKey
import iad1tya.echo.music.constants.ExportingSongIdsKey
import iad1tya.echo.music.constants.ListItemHeight
import iad1tya.echo.music.extensions.toggleRepeatMode
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.rememberResolvedAlbum
import iad1tya.echo.music.playback.enqueueSongDownloads
import iad1tya.echo.music.playback.removeSongDownloads
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.component.VolumeSlider
import iad1tya.echo.music.ui.player.rememberHidePlayerVolume
import iad1tya.echo.music.ui.player.showPlayerVolumeControl
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.ui.menu.ListenTogetherDialog
import iad1tya.echo.music.ui.menu.TempoPitchDialog
import iad1tya.echo.music.echomusic.AudioDeviceBottomSheet
import iad1tya.echo.music.utils.ShareLinks
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.launch

/**
 * # "Interfaz nueva" — Menú del reproductor (FUSIONADO)
 *
 * The render draws ONE player menu. The app has **two live ones with different contents**:
 *
 *  · `OldPlayerMenu` — what the player's "Más" chip opens. Owns *Aleatorio*, *Repetir* and *Sonido*.
 *  · `PlayerMenu` — reachable ONLY from the Queue (`Queue.kt:475` / `:891`). It is the ONLY home of
 *    **Modo ambiente**, **Ecualizador** and **Velocidad y tono** (its "Avanzado" entry).
 *
 * Merging them without carrying both lists would have silently deleted those three. This menu carries
 * every entry of BOTH, plus the audio-output picker (`AudioDeviceBottomSheet`), which in the classic
 * player design is likewise reachable only from the Queue.
 *
 * Grouped as the render groups them: REPRODUCCIÓN / BIBLIOTECA / IR A / MÁS.
 *
 * **Presentation only.** Every `onClick` calls the same function the classic menu calls — the same
 * `playerConnection` method, the same `DownloadService` intent, the same `navController` route, the
 * same dialogs (`AddToPlaylistDialog`, `ListenTogetherDialog`, `TempoPitchDialog`, `ListDialog`).
 * Labels are the same `stringResource` ids, so the Spanish cannot drift.
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
    // "Ocultar control de volumen" (HidePlayerSliderKey) — read through the shared helper, never re-keyed.
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
    // Same flow the classic PlayerMenu badges with (PlayerMenu.kt:180) — read, never written.
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
    val disliked by playerConnection.currentSongDisliked.collectAsState()
    val videoMode by playerConnection.videoMode.collectAsState()

    val artists = remember(mediaMetadata.artists) { mediaMetadata.artists }
    val resolvedAlbum = rememberResolvedAlbum(
        songId = mediaMetadata.id,
        initial = mediaMetadata.album,
        dbAlbumId = librarySong?.song?.albumId,
        dbAlbumName = librarySong?.song?.albumName,
    )

    val (enableExportAsMp3) = rememberPreference(EnableExportAsMp3Key, true)
    val (exportDirectoryUri, onExportDirectoryUriChange) = rememberPreference(ExportDirectoryUriKey, "")
    val (exportingSongIds) = rememberPreference(ExportingSongIdsKey, "")
    val (exportedSongIds) = rememberPreference(ExportedSongIdsKey, "")
    val ensureMp3Folder = iad1tya.echo.music.ui.utils.rememberMp3ExportFolderAccess(
        exportDirectoryUri = exportDirectoryUri,
        onExportDirectoryUriChange = onExportDirectoryUriChange,
    )
    val isExporting = remember(exportingSongIds, mediaMetadata.id) {
        exportingSongIds.split(",").contains(mediaMetadata.id)
    }
    val isExported = remember(exportedSongIds, mediaMetadata.id) {
        exportedSongIds.split(",").contains(mediaMetadata.id)
    }

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showListenTogetherDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }
    var showPitchTempoDialog by rememberSaveable { mutableStateOf(false) }
    var showAudioDeviceSheet by rememberSaveable { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            // Identical to the classic menus: withTransaction (suspending) so the song row is committed
            // before AddToPlaylistDialog inserts the ON DELETE CASCADE map row.
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

    if (showSelectArtistDialog) {
        ListDialog(onDismiss = { showSelectArtistDialog = false }) {
            items(artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            if (artist.id != null) {
                                navController.navigate("artist/${artist.id}")
                            } else {
                                navController.navigate(
                                    "search/${java.net.URLEncoder.encode(artist.name, "UTF-8")}"
                                )
                            }
                            showSelectArtistDialog = false
                            playerBottomSheetState.collapseSoft()
                            onDismiss()
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

    val liked = currentSong?.song?.liked == true
    val isInLibrary = librarySong?.song?.inLibrary != null
    val hasVideo = mediaMetadata.isVideoSong || !mediaMetadata.podcastVideoUrl.isNullOrEmpty()
    // Sheet already paints FrostFill ([BottomSheetMenu] + LocalAuraFloatingChrome). An opaque
    // GroundRaised here was covering it — the "Más" menu looked like a solid Material card.
    val floatingChrome = LocalAuraFloatingChrome.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            // The hosting sheet pads its content by 20 dp; the render's menu is edge-to-edge over
            // the sheet plate, so bleed back out over that padding.
            .auraBleedHorizontal(20.dp)
            .background(if (floatingChrome) Color.Transparent else AuraPalette.GroundRaised),
        contentPadding = PaddingValues(
            bottom = 16.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        // ── Cabecera: portada + título + artista ──────────────────────────────────────────────────
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Gutter, vertical = 12.dp),
            ) {
                // Must pass the URL — AuraArtwork alone is only the placeholder plate (empty square).
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

        // "Transmitiendo a %s" — both classic menus show it in the header while casting.
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

        // Volume slider — the header control of BOTH classic menus (controls the Cast volume while casting).
        //
        // "Ocultar control de volumen" (HidePlayerSliderKey) gates it. That row is reachable from Ajustes
        // with the new UI on, and the classic Apple-Music transport is the only thing that used to read
        // it, so with this shape on screen the switch changed nothing. This IS the volume control the new
        // player draws, so this is where it gets hidden. The audio-output picker is a separate entry
        // further down (and a quick-access button in the player), so hiding the volume never costs the
        // user the device chooser.
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

        // Streaming quality — same keys as Ajustes ▸ Reproductor, surfaced here so the volume/quality
        // cluster the owner asked for lives in one premium menu (not only the classic settings sheet).
        item {
            val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
                iad1tya.echo.music.constants.AudioQualityKey,
                defaultValue = iad1tya.echo.music.constants.AudioQuality.OPUS,
            )
            AuraMenuRow(
                icon = AuraIcons.Equalizer,
                label = stringResource(R.string.audio_quality),
                onClick = {
                    val values = iad1tya.echo.music.constants.AudioQuality.entries
                    val next = values[(values.indexOf(audioQuality) + 1) % values.size]
                    onAudioQualityChange(next)
                },
                trailing = {
                    AuraTechnicalText(
                        text = when (audioQuality) {
                            iad1tya.echo.music.constants.AudioQuality.OPUS -> "OPUS"
                            iad1tya.echo.music.constants.AudioQuality.SAAVN -> "SAAVN"
                            iad1tya.echo.music.constants.AudioQuality.LOSSLESS -> "QOBUZ"
                        },
                        color = AuraPalette.Teal,
                    )
                },
            )
        }
        item { Spacer(Modifier.height(6.dp)); AuraDivider() }

        // ── REPRODUCCIÓN ──────────────────────────────────────────────────────────────────────────
        item { AuraMenuGroupLabel("REPRODUCCIÓN") }

        if (!isListenTogetherGuest) {
            item {
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
            }
            item {
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
            }
            item {
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
        }

        // "Menos de esto" — the dislike half of the classic player's like/dislike pill.
        item {
            AuraMenuRow(
                icon = AuraIcons.ThumbDown,
                label = stringResource(R.string.action_dislike),
                iconTint = if (disliked) AuraPalette.Teal else AuraPalette.OnGround.copy(alpha = 0.75f),
                onClick = {
                    playerConnection.toggleDislikeCurrentSong()
                    onDismiss()
                },
            )
        }

        // "Velocidad y tono" — PlayerMenu's "Avanzado". Only reachable from the Queue today.
        item {
            AuraMenuRow(
                icon = AuraIcons.Speed,
                label = stringResource(R.string.advanced),
                onClick = { showPitchTempoDialog = true },
            )
        }

        // ── BIBLIOTECA ────────────────────────────────────────────────────────────────────────────
        item { AuraMenuGroupLabel("BIBLIOTECA") }

        item {
            AuraMenuRow(
                icon = if (liked) AuraIcons.HeartFilled else AuraIcons.Heart,
                label = stringResource(R.string.action_like),
                iconTint = if (liked) AuraPalette.Teal else AuraPalette.OnGround.copy(alpha = 0.75f),
                onClick = { playerConnection.toggleLike() },
                trailing = {
                    AuraSwitch(
                        checked = liked,
                        onCheckedChange = { playerConnection.toggleLike() },
                        contentDescription = stringResource(R.string.action_like),
                    )
                },
            )
        }

        item {
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
        }

        item {
            AuraMenuRow(
                icon = AuraIcons.PlaylistAdd,
                label = stringResource(R.string.add_to_playlist),
                onClick = { showChoosePlaylistDialog = true },
            )
        }

        item {
            when (download?.state) {
                Download.STATE_COMPLETED -> AuraMenuRow(
                    icon = AuraIcons.Check,
                    label = stringResource(R.string.remove_download),
                    iconTint = AuraPalette.Teal,
                    onClick = {
                        removeSongDownloads(
                            context,
                            mediaMetadata.id,
                            mediaMetadata.isVideoSong,
                        )
                        onDismiss()
                    },
                )
                Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> AuraMenuRow(
                    icon = AuraIcons.Download,
                    label = stringResource(R.string.downloading),
                    onClick = {
                        removeSongDownloads(
                            context,
                            mediaMetadata.id,
                            mediaMetadata.isVideoSong,
                        )
                        onDismiss()
                    },
                    trailing = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AuraPalette.Teal,
                        )
                    },
                )
                else -> AuraMenuRow(
                    icon = AuraIcons.Download,
                    label = stringResource(R.string.action_download),
                    onClick = {
                        database.transaction { insert(mediaMetadata) }
                        enqueueSongDownloads(
                            context,
                            mediaMetadata.id,
                            mediaMetadata.title,
                            mediaMetadata.isVideoSong,
                        )
                        onDismiss()
                    },
                )
            }
        }

        if (enableExportAsMp3) {
            item {
                when {
                    isExporting -> AuraMenuRow(
                        icon = AuraIcons.Export,
                        label = stringResource(R.string.exporting),
                        onClick = {},
                        trailing = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AuraPalette.Teal,
                            )
                        },
                    )
                    isExported -> AuraMenuRow(
                        icon = AuraIcons.Export,
                        label = stringResource(R.string.action_exported),
                        iconTint = AuraPalette.Teal,
                        onClick = {},
                    )
                    else -> AuraMenuRow(
                        icon = AuraIcons.Export,
                        label = stringResource(R.string.action_export),
                        onClick = {
                            ensureMp3Folder { directoryUri ->
                                onDismiss()
                                iad1tya.echo.music.playback.AudioExportService.start(
                                    context = context,
                                    songId = mediaMetadata.id,
                                    songTitle = mediaMetadata.title,
                                    songArtist = artists.joinToString(", ") { it.name },
                                    songAlbum = mediaMetadata.album?.title ?: "",
                                    artworkUrl = mediaMetadata.thumbnailUrl ?: "",
                                    targetDirectoryUri = directoryUri,
                                )
                            }
                        },
                    )
                }
            }
        }

        item {
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

        item {
            AuraMenuRow(
                icon = AuraIcons.Radio,
                label = stringResource(R.string.refetch),
                onClick = { playerConnection.refetchCurrentInOpus() },
            )
        }

        // ── IR A ──────────────────────────────────────────────────────────────────────────────────
        item { AuraMenuGroupLabel("IR A") }

        if (artists.isNotEmpty()) {
            item {
                AuraMenuRow(
                    icon = AuraIcons.Artist,
                    label = stringResource(R.string.view_artist),
                    onClick = {
                        if (artists.size == 1) {
                            val only = artists[0]
                            if (only.id != null) {
                                navController.navigate("artist/${only.id}")
                            } else {
                                navController.navigate(
                                    "search/${java.net.URLEncoder.encode(only.name, "UTF-8")}"
                                )
                            }
                            playerBottomSheetState.collapseSoft()
                            onDismiss()
                        } else {
                            showSelectArtistDialog = true
                        }
                    },
                )
            }
        }

        resolvedAlbum?.let { album ->
            item {
                AuraMenuRow(
                    icon = AuraIcons.Album,
                    label = stringResource(R.string.view_album),
                    onClick = {
                        navController.navigate("album/${album.id}")
                        playerBottomSheetState.collapseSoft()
                        onDismiss()
                    },
                )
            }
        }

        if (mediaMetadata.id.startsWith("http")) {
            item {
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
                                    "podcasts?feedUrl=" + java.net.URLEncoder.encode(feed, "UTF-8")
                                else "podcasts"
                            )
                        }
                    },
                )
            }
        }

        // ── MÁS ───────────────────────────────────────────────────────────────────────────────────
        item { AuraMenuGroupLabel("MÁS") }

        if (hasVideo) {
            item {
                AuraMenuRow(
                    icon = AuraIcons.Video,
                    label = "Vídeo",
                    iconTint = if (videoMode) AuraPalette.Teal else AuraPalette.OnGround.copy(alpha = 0.75f),
                    onClick = {
                        playerConnection.toggleVideoMode()
                        onDismiss()
                    },
                )
            }
        }

        // The audio-output picker. It was labelled "Transmitir" behind a CAST glyph, which is a different
        // feature: [AudioDeviceBottomSheet] enumerates the local AudioManager outputs (altavoz, jack,
        // Bluetooth) and never touches a Cast route. Real casting is the pinned [CastButton] in the player
        // — and in a FOSS build there is no casting at all, so that label promised a feature the build does
        // not ship. Same string the rest of the app uses for this sheet, and a speaker glyph to match the
        // queue bar's own button.
        //
        // Kept rather than removed even though the queue bar draws the same picker: the bar is the queue
        // sheet's COLLAPSED content, so while that sheet is expanded this menu entry is the only door.
        item {
            AuraMenuRow(
                icon = AuraIcons.Volume,
                label = stringResource(R.string.audio_devices),
                onClick = { showAudioDeviceSheet = true },
            )
        }

        item {
            AuraMenuRow(
                icon = AuraIcons.People,
                label = stringResource(R.string.listen_together),
                onClick = { showListenTogetherDialog = true },
                // §5 — the numeric badge of pending suggestions. The classic menu draws it over the
                // icon; here it rides the trailing slot so it stays legible at the render's icon size.
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
        }

        if (isListenTogetherGuest) {
            item {
                AuraMenuRow(
                    icon = AuraIcons.Radio,
                    label = stringResource(R.string.resync),
                    onClick = {
                        listenTogetherManager?.requestSync()
                        onDismiss()
                    },
                )
            }
        }

        // Modo Ambiente — today reachable ONLY from the Queue's ⋮ menu.
        item {
            AuraMenuRow(
                icon = AuraIcons.Album,
                label = stringResource(R.string.ambient_mode),
                onClick = {
                    playerBottomSheetState.collapseSoft()
                    navController.navigate("ambient_mode") { launchSingleTop = true }
                    onDismiss()
                },
            )
        }

        // Ecualizador — today reachable ONLY from the Queue's ⋮ menu (and the player's "Audio" chip).
        item {
            AuraMenuRow(
                icon = AuraIcons.Equalizer,
                label = stringResource(R.string.equalizer),
                onClick = {
                    playerBottomSheetState.collapseSoft()
                    navController.navigate("settings/equalizer") { launchSingleTop = true }
                    onDismiss()
                },
            )
        }

        item {
            AuraMenuRow(
                icon = AuraIcons.Equalizer,
                label = "Sonido",
                onClick = {
                    playerBottomSheetState.collapseSoft()
                    navController.navigate("settings/sound")
                    onDismiss()
                },
            )
        }

        item {
            AuraMenuRow(
                icon = AuraIcons.Share,
                label = stringResource(R.string.share),
                onClick = {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, ShareLinks.song(mediaMetadata.id))
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                },
            )
        }

        item {
            AuraMenuRow(
                icon = AuraIcons.Search,
                label = stringResource(R.string.details),
                onClick = {
                    onShowDetailsDialog()
                    onDismiss()
                },
            )
        }

        item {
            AuraMenuRow(
                icon = AuraIcons.Settings,
                label = stringResource(R.string.settings),
                onClick = {
                    playerBottomSheetState.collapseSoft()
                    // navigateAsTab, NOT navigate: under the new shell "settings" KEEPS the bottom bar
                    // (MainActivity: `newUiShell && currentRoute == "settings"`), so it is a tab in every
                    // respect. Pushing it plainly leaves it on top of whichever tab was showing, and the
                    // next tap on that tab saves [tab, settings] as ONE deque keyed by the tab and
                    // restores it whole — the tab then reopens Ajustes forever. That is exactly the
                    // "Biblioteca no muestra nada y me manda a Ajustes" report, reachable from here too.
                    navController.navigateAsTab("settings")
                    onDismiss()
                },
            )
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
