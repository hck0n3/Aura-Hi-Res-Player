

package iad1tya.echo.music.ui.menu

import iad1tya.echo.music.utils.ShareLinks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableExportAsMp3Key
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.constants.ExportingSongIdsKey
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.PlaylistSong
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.playback.AudioExportService
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.component.Material3MenuGroup
import iad1tya.echo.music.ui.component.Material3MenuItemData
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistMenu(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    context: Context,
    downloadState: Int,
    onEdit: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onQueue: () -> Unit,
    onDismiss: () -> Unit,
    /** Null hides "Editar con IA" (feature toggled off, or a playlist it doesn't apply to). */
    onAiModify: (() -> Unit)? = null,
) {
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val coroutineScope = rememberCoroutineScope()
    val (enableExportAsMp3) = rememberPreference(key = EnableExportAsMp3Key, defaultValue = true)
    val (_, onExportDirectoryUriChange) = rememberPreference(key = ExportDirectoryUriKey, defaultValue = "")
    val exportSongs = remember(songs) { songs.map { it.song } }

    // MP3 export: choice → optional selection → folder picker → serial AudioExportService.start
    var showMp3ChoiceDialog by remember { mutableStateOf(false) }
    var showMp3SelectDialog by remember { mutableStateOf(false) }
    var selectedMp3Ids by remember { mutableStateOf(emptySet<String>()) }
    var pendingMp3Songs by remember { mutableStateOf<List<Song>>(emptyList()) }

    val startSerialMp3Export: (List<Song>, String) -> Unit = { toExport, directoryUri ->
        coroutineScope.launch(Dispatchers.IO) {
            for (song in toExport) {
                withContext(Dispatchers.Main) {
                    AudioExportService.start(
                        context = context,
                        songId = song.id,
                        songTitle = song.song.title,
                        songArtist = song.artists.joinToString(", ") { it.name },
                        songAlbum = song.song.albumName ?: "",
                        artworkUrl = song.song.thumbnailUrl ?: "",
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

    val openMp3FolderPicker: (List<Song>) -> Unit = { list ->
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
                        selectedMp3Ids = exportSongs.map { it.id }.toSet()
                        showMp3SelectDialog = true
                    },
                ) {
                    Text(text = "Seleccionar")
                }
                TextButton(
                    onClick = {
                        showMp3ChoiceDialog = false
                        openMp3FolderPicker(exportSongs)
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
                            checked = exportSongs.isNotEmpty() && selectedMp3Ids.size == exportSongs.size,
                            onCheckedChange = { checked ->
                                selectedMp3Ids =
                                    if (checked) exportSongs.map { it.id }.toSet() else emptySet()
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedMp3Ids =
                            if (selectedMp3Ids.size == exportSongs.size) emptySet()
                            else exportSongs.map { it.id }.toSet()
                    },
                )
            }
            items(exportSongs, key = { it.id }) { song ->
                val checked = song.id in selectedMp3Ids
                ListItem(
                    headlineContent = { Text(text = song.song.title) },
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
                            openMp3FolderPicker(exportSongs.filter { it.id in selectedMp3Ids })
                        },
                    ) {
                        Text(text = "Continuar")
                    }
                }
            }
        }
    }

    val downloadMenuItem = when (downloadState) {
        Download.STATE_COMPLETED -> Material3MenuItemData(
            title = { Text(stringResource(R.string.remove_download)) },
            description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Material3MenuItemData(
            title = { Text(stringResource(R.string.downloading)) },
            description = { Text(stringResource(R.string.download_in_progress_desc)) },
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        else -> Material3MenuItemData(
            title = { Text(stringResource(R.string.action_download)) },
            description = { Text(stringResource(R.string.download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
    }

    val isYouTubePlaylist = playlist.playlist.browseId != null

    val menuItems = buildList {
        add(
            Material3MenuItemData(
                title = { Text(stringResource(R.string.edit)) },
                description = { Text(stringResource(R.string.edit_playlist)) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null
                    )
                },
                onClick = {
                    onEdit()
                    onDismiss()
                }
            )
        )

        onAiModify?.let { aiModify ->
            add(
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.ai_modify_playlist_title)) },
                    description = { Text(stringResource(R.string.ai_modify_playlist_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.auto_awesome),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        aiModify()
                        onDismiss()
                    }
                )
            )
        }


        if (isYouTubePlaylist) {
            add(
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.action_sync)) },
                    description = { Text(stringResource(R.string.sync_playlist_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.sync),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onSync()
                        onDismiss()
                    }
                )
            )
        }

        if (!isGuest) {
            add(
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.add_to_queue)) },
                    description = { Text(stringResource(R.string.add_to_queue_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onQueue()
                        onDismiss()
                    }
                )
            )
        }

        add(downloadMenuItem)

        add(
            Material3MenuItemData(
                title = { Text(stringResource(R.string.share)) },
                description = { Text(stringResource(R.string.share_playlist_desc)) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null
                    )
                },
                onClick = {
                    val shareText = if (isYouTubePlaylist) {
                        ShareLinks.playlist(playlist.playlist.browseId)
                    } else {
                        songs.joinToString("\n") { it.song.song.title }
                    }
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    onDismiss()
                }
            )
        )

        if (enableExportAsMp3) {
            add(
                Material3MenuItemData(
                    title = { Text(text = "Exportar a MP3") },
                    description = { Text(text = "Todas o seleccionar canciones") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.file_export),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    onClick = {
                        if (exportSongs.isEmpty()) {
                            Toast.makeText(
                                context,
                                "No hay canciones para exportar",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            showMp3ChoiceDialog = true
                        }
                    }
                )
            )
        }

        add(
            Material3MenuItemData(
                title = { Text(stringResource(R.string.delete)) },
                description = { Text(stringResource(R.string.delete_playlist_desc)) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = null
                    )
                },
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )
        )
    }

    Material3MenuGroup(items = menuItems)
}


@Composable
fun AutoPlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onSync: (() -> Unit)? = null,
) {
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    val downloadMenuItem = when (downloadState) {
        Download.STATE_COMPLETED -> Material3MenuItemData(
            title = { Text(stringResource(R.string.remove_download)) },
            description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Material3MenuItemData(
            title = { Text(stringResource(R.string.downloading)) },
            description = { Text(stringResource(R.string.download_in_progress_desc)) },
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        else -> Material3MenuItemData(
            title = { Text(stringResource(R.string.action_download)) },
            description = { Text(stringResource(R.string.download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
    }

    Material3MenuGroup(
        items = listOfNotNull(
            onSync?.let {
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.action_sync)) },
                    description = { Text("Traer los últimos cambios de YouTube Music") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.sync),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        it()
                        onDismiss()
                    }
                )
            },
            if (!isGuest) {
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.add_to_queue)) },
                    description = { Text(stringResource(R.string.add_to_queue_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onQueue()
                        onDismiss()
                    }
                )
            } else null,
            downloadMenuItem
        )
    )
}


@Composable
fun TopPlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    val downloadMenuItem = when (downloadState) {
        Download.STATE_COMPLETED -> Material3MenuItemData(
            title = { Text(stringResource(R.string.remove_download)) },
            description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Material3MenuItemData(
            title = { Text(stringResource(R.string.downloading)) },
            description = { Text(stringResource(R.string.download_in_progress_desc)) },
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        else -> Material3MenuItemData(
            title = { Text(stringResource(R.string.action_download)) },
            description = { Text(stringResource(R.string.download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
    }

    Material3MenuGroup(
        items = listOfNotNull(
            if (!isGuest) {
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.add_to_queue)) },
                    description = { Text(stringResource(R.string.add_to_queue_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onQueue()
                        onDismiss()
                    }
                )
            } else null,
            downloadMenuItem
        )
    )
}


@Composable
fun CachePlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    val downloadMenuItem = when (downloadState) {
        Download.STATE_COMPLETED -> Material3MenuItemData(
            title = { Text(stringResource(R.string.remove_download)) },
            description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Material3MenuItemData(
            title = { Text(stringResource(R.string.downloading)) },
            description = { Text(stringResource(R.string.download_in_progress_desc)) },
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        else -> Material3MenuItemData(
            title = { Text(stringResource(R.string.action_download)) },
            description = { Text(stringResource(R.string.download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
    }

    Material3MenuGroup(
        items = listOfNotNull(
            if (!isGuest) {
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.add_to_queue)) },
                    description = { Text(stringResource(R.string.add_to_queue_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onQueue()
                        onDismiss()
                    }
                )
            } else null,
            downloadMenuItem
        )
    )
}
