

package iad1tya.echo.music.ui.menu

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.utils.completed
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableExportAsMp3Key
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.constants.ExportingSongIdsKey
import iad1tya.echo.music.constants.ListThumbnailSize
import iad1tya.echo.music.constants.ThumbnailCornerRadius
import iad1tya.echo.music.db.entities.SpeedDialItem
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.AudioExportService
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.component.Material3MenuGroup
import iad1tya.echo.music.ui.component.Material3MenuItemData
import iad1tya.echo.music.ui.component.NewAction
import iad1tya.echo.music.ui.component.NewActionGrid
import iad1tya.echo.music.ui.component.YouTubeListItem
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.joinByBullet
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubePlaylistMenu(
    playlist: PlaylistItem,
    songs: List<SongItem> = emptyList(),
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    selectAction: () -> Unit = {},
    canSelect: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val dbPlaylist by database.playlistByBrowseId(playlist.id).collectAsState(initial = null)
    val isPinned by database.speedDialDao.isPinned(playlist.id).collectAsState(initial = false)
    val (enableExportAsMp3) = rememberPreference(key = EnableExportAsMp3Key, defaultValue = true)
    val (_, onExportDirectoryUriChange) = rememberPreference(key = ExportDirectoryUriKey, defaultValue = "")

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showImportPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }

    val notAddedList by remember {
        mutableStateOf(mutableListOf<MediaMetadata>())
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { targetPlaylist ->
            val allSongs = songs
                .ifEmpty {
                    // The SOURCE is this menu's playlist, not the destination: targetPlaylist.id is
                    // the LOCAL "LP########" id of the playlist we are adding to, which YouTube can
                    // never resolve, so this fetch always failed and nothing was added.
                    YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }.map {
                    it.toMediaMetadata()
                }
            // withTransaction (suspending), NOT transaction {}: the latter posts the work to Room's
            // transaction executor and returns immediately, so onGetSong could hand these ids back
            // before the song rows are committed. The caller then inserts PlaylistSongMap rows whose
            // songId FK is ON DELETE CASCADE — if that insert wins the race, the whole @Transaction
            // addSongToPlaylist aborts and nothing is added, silently.
            database.withTransaction {
                allSongs.forEach(::insert)
            }
            // No remote add here: AddToPlaylistDialog is the single writer to the remote playlist
            // (it calls YouTube.addToPlaylist for every returned id). Adding here too made every
            // song land TWICE in a synced YouTube playlist.
            allSongs.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    YouTubeListItem(
        item = playlist,
        trailingContent = {
            if (playlist.id != "LM" && !playlist.isEditable) {
                IconButton(
                    onClick = {
                        if (dbPlaylist?.playlist == null) {
                            database.transaction {
                                val playlistEntity = PlaylistEntity(
                                    name = playlist.title,
                                    browseId = playlist.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    isEditable = playlist.isEditable,
                                    remoteSongCount = playlist.songCountText?.let {
                                        Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                    },
                                    playEndpointParams = playlist.playEndpoint?.params,
                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                    radioEndpointParams = playlist.radioEndpoint?.params
                                ).toggleLike()
                                insert(playlistEntity)
                                coroutineScope.launch(Dispatchers.IO) {
                                    songs.ifEmpty {
                                        YouTube.playlist(playlist.id).completed()
                                            .getOrNull()?.songs.orEmpty()
                                    }.map { it.toMediaMetadata() }
                                        .onEach(::insert)
                                        .mapIndexed { index, song ->
                                            PlaylistSongMap(
                                                songId = song.id,
                                                playlistId = playlistEntity.id,
                                                position = index,
                                                setVideoId = song.setVideoId
                                            )
                                        }
                                        .forEach(::insert)
                                }
                            }
                        } else {
                            database.transaction {
                                val currentPlaylist = dbPlaylist!!.playlist
                                update(currentPlaylist, playlist)
                                update(currentPlaylist.toggleLike())
                            }
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(if (dbPlaylist?.playlist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (dbPlaylist?.playlist?.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null
                    )
                }
            }
        }
    )
    HorizontalDivider()

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }
    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED })
                    Download.STATE_COMPLETED
                else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED
                                || downloads[it.id]?.state == Download.STATE_DOWNLOADING
                                || downloads[it.id]?.state == Download.STATE_COMPLETED
                    })
                    Download.STATE_DOWNLOADING
                else
                    Download.STATE_STOPPED
        }
    }
    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist.title
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    // MP3 export: resolve songs → choice → optional selection → folder → serial starts
    var showMp3ChoiceDialog by remember { mutableStateOf(false) }
    var showMp3SelectDialog by remember { mutableStateOf(false) }
    var selectedMp3Ids by remember { mutableStateOf(emptySet<String>()) }
    var mp3SongPool by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var pendingMp3Songs by remember { mutableStateOf<List<SongItem>>(emptyList()) }

    val startSerialMp3Export: (List<SongItem>, String) -> Unit = { toExport, directoryUri ->
        coroutineScope.launch(Dispatchers.IO) {
            for (song in toExport) {
                withContext(Dispatchers.Main) {
                    AudioExportService.start(
                        context = context,
                        songId = song.id,
                        songTitle = song.title,
                        songArtist = song.artists.joinToString(", ") { it.name },
                        songAlbum = song.album?.name ?: "",
                        artworkUrl = song.thumbnail,
                        targetDirectoryUri = directoryUri,
                    )
                }
                val exporting = context.dataStore.data.map { prefs ->
                    prefs[ExportingSongIdsKey].orEmpty().split(",").any { it == song.id }
                }
                withTimeoutOrNull(15_000) { exporting.first { it } }
                exporting.first { !it }
            }
        }
    }

    val mp3FolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null && pendingMp3Songs.isNotEmpty()) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val toExport = pendingMp3Songs
            pendingMp3Songs = emptyList()
            onExportDirectoryUriChange(uri.toString())
            onDismiss()
            startSerialMp3Export(toExport, uri.toString())
        } else {
            pendingMp3Songs = emptyList()
        }
    }

    val openMp3FolderPicker: (List<SongItem>) -> Unit = { list ->
        if (list.isEmpty()) {
            Toast.makeText(context, "No hay canciones para exportar", Toast.LENGTH_SHORT).show()
        } else {
            pendingMp3Songs = list
            try {
                mp3FolderLauncher.launch(null)
            } catch (_: ActivityNotFoundException) {
                pendingMp3Songs = emptyList()
                Toast.makeText(
                    context,
                    context.getString(R.string.export_directory_picker_unavailable),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val beginMp3Export: () -> Unit = {
        coroutineScope.launch {
            val resolved = songs.ifEmpty {
                withContext(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }
            }
            if (resolved.isEmpty()) {
                Toast.makeText(context, "No hay canciones para exportar", Toast.LENGTH_SHORT).show()
                return@launch
            }
            mp3SongPool = resolved
            showMp3ChoiceDialog = true
        }
    }

    if (showMp3ChoiceDialog) {
        DefaultDialog(
            onDismiss = { showMp3ChoiceDialog = false },
            title = { Text(text = "Exportar a MP3") },
            content = {
                Text(
                    text = "¿Exportar todas las canciones de la playlist o seleccionar cuáles?",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showMp3ChoiceDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showMp3ChoiceDialog = false
                        selectedMp3Ids = mp3SongPool.map { it.id }.toSet()
                        showMp3SelectDialog = true
                    },
                ) {
                    Text(text = "Seleccionar")
                }
                TextButton(
                    onClick = {
                        showMp3ChoiceDialog = false
                        openMp3FolderPicker(mp3SongPool)
                    },
                ) {
                    Text(text = "Todas")
                }
            },
        )
    }

    if (showMp3SelectDialog) {
        ListDialog(
            onDismiss = { showMp3SelectDialog = false },
        ) {
            item {
                ListItem(
                    headlineContent = { Text(text = "Seleccionar todas") },
                    trailingContent = {
                        Checkbox(
                            checked = mp3SongPool.isNotEmpty() && selectedMp3Ids.size == mp3SongPool.size,
                            onCheckedChange = { checked ->
                                selectedMp3Ids =
                                    if (checked) mp3SongPool.map { it.id }.toSet() else emptySet()
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedMp3Ids =
                            if (selectedMp3Ids.size == mp3SongPool.size) emptySet()
                            else mp3SongPool.map { it.id }.toSet()
                    },
                )
            }
            items(mp3SongPool, key = { it.id }) { song ->
                val checked = song.id in selectedMp3Ids
                ListItem(
                    headlineContent = { Text(text = song.title) },
                    supportingContent = {
                        Text(text = song.artists.joinToString { it.name })
                    },
                    trailingContent = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selectedMp3Ids =
                                    if (on) selectedMp3Ids + song.id else selectedMp3Ids - song.id
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedMp3Ids =
                            if (checked) selectedMp3Ids - song.id else selectedMp3Ids + song.id
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    TextButton(onClick = { showMp3SelectDialog = false }) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = selectedMp3Ids.isNotEmpty(),
                        onClick = {
                            showMp3SelectDialog = false
                            openMp3FolderPicker(mp3SongPool.filter { it.id in selectedMp3Ids })
                        },
                    ) {
                        Text(text = "Continuar")
                    }
                }
            }
        }
    }

    ImportPlaylistDialog(
        isVisible = showImportPlaylistDialog,
        onGetSong = {
            val allSongs = songs
                .ifEmpty {
                    YouTube.playlist(playlist.id).completed().getOrNull()?.songs.orEmpty()
                }.map {
                    it.toMediaMetadata()
                }
            // Same race as the AddToPlaylistDialog path above: ImportPlaylistDialog calls
            // addSongToPlaylist right after onGetSong returns, so the song rows must be committed
            // before we hand the ids back or the CASCADE FK aborts the whole insert.
            database.withTransaction {
                allSongs.forEach(::insert)
            }
            allSongs.map { it.id }
        },
        playlistTitle = playlist.title,
        onDismiss = { showImportPlaylistDialog = false }
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(notAddedList) { song ->
                ListItem(
                    headlineContent = { Text(text = song.title) },
                    leadingContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(ListThumbnailSize),
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                            )
                        }
                    },
                    supportingContent = {
                        Text(
                            text = joinByBullet(
                                song.artists.joinToString { it.name },
                                makeTimeString(song.duration * 1000L),
                            )
                        )
                    },
                )
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    if (!isGuest) {
                        playlist.playEndpoint?.let { playEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.play),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.play),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(playEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.shuffle),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.shuffle),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        playlist.radioEndpoint?.let { radioEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.start_radio),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            Material3MenuGroup(
                items = listOfNotNull(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.do_not_recommend)) },
                        description = { Text(text = stringResource(R.string.do_not_recommend_playlist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.thumb_down),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                iad1tya.echo.music.dislike.DislikeStoreEntryPoint.get(context).dislikePlaylist(playlist.id)
                            }
                            onDismiss()
                        }
                    ),
                    if (!isGuest) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.play_next)) },
                            description = { Text(text = stringResource(R.string.play_next_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.playlist_play),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                coroutineScope.launch {
                                    songs
                                        .ifEmpty {
                                            withContext(Dispatchers.IO) {
                                                YouTube
                                                    .playlist(playlist.id)
                                                    .completed()
                                                    .getOrNull()
                                                    ?.songs
                                                    .orEmpty()
                                            }
                                        }.let { songs ->
                                            playerConnection.playNext(songs.map {
                                                it.copy(thumbnail = it.thumbnail.resize(1200, 1200))
                                                    .toMediaItem()
                                            })
                                        }
                                }
                                onDismiss()
                            }
                        )
                    } else null,
                    if (!isGuest) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_queue)) },
                            description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                coroutineScope.launch {
                                    songs
                                        .ifEmpty {
                                            withContext(Dispatchers.IO) {
                                                YouTube
                                                    .playlist(playlist.id)
                                                    .completed()
                                                    .getOrNull()
                                                    ?.songs
                                                    .orEmpty()
                                            }
                                        }.let { songs ->
                                            playerConnection.addToQueue(songs.map { it.toMediaItem() })
                                        }
                                }
                                onDismiss()
                            }
                        )
                    } else null,
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_playlist)) },
                        description = { Text(text = stringResource(R.string.add_to_playlist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    ),
                    Material3MenuItemData(
                        title = { 
                            Text(
                                text = if (isPinned) stringResource(R.string.unpin_from_speed_dial) else stringResource(R.string.pin_to_speed_dial) 
                            ) 
                        },
                        icon = {
                            Icon(
                                painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                if (isPinned) {
                                    database.speedDialDao.delete(playlist.id)
                                } else {
                                    database.speedDialDao.insert(SpeedDialItem.fromYTItem(playlist))
                                }
                            }
                            onDismiss()
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    if (enableExportAsMp3) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = "Exportar a MP3") },
                                description = { Text(text = "Todas o seleccionar canciones") },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.file_export),
                                        contentDescription = null,
                                    )
                                },
                                onClick = { beginMp3Export() }
                            )
                        )
                    }
                    if (songs.isNotEmpty()) {
                        add(
                            when (downloadState) {
                                Download.STATE_COMPLETED -> {
                                    Material3MenuItemData(
                                        title = {
                                            Text(
                                                text = stringResource(R.string.remove_download)
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.offline),
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showRemoveDownloadDialog = true
                                        }
                                    )
                                }
                                Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                    Material3MenuItemData(
                                        title = { Text(text = stringResource(R.string.downloading)) },
                                        icon = {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        },
                                        onClick = {
                                            showRemoveDownloadDialog = true
                                        }
                                    )
                                }
                                else -> {
                                    Material3MenuItemData(
                                        title = { Text(text = stringResource(R.string.action_download)) },
                                        description = { Text(text = stringResource(R.string.download_desc)) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            songs.forEach { song ->
                                                val downloadRequest =
                                                    DownloadRequest.Builder(song.id, song.id.toUri())
                                                        .setCustomCacheKey(song.id)
                                                        .setData(song.title.toByteArray())
                                                        .build()
                                                DownloadService.sendAddDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    downloadRequest,
                                                    false
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.share)) },
                            description = { Text(text = stringResource(R.string.share_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.share),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                val intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, playlist.shareLink)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                                onDismiss()
                            }
                        )
                    )
                    if (canSelect) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.select)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.select_all),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    selectAction()
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}
