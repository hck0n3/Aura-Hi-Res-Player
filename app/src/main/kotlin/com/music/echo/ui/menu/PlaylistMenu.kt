

package iad1tya.echo.music.ui.menu

import iad1tya.echo.music.utils.ShareLinks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.music.innertube.YouTube
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.LocalSyncUtils
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableExportAsMp3Key
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.constants.ExportingSongIdsKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.SpeedDialItem
import iad1tya.echo.music.db.entities.PlaylistSong
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.AudioExportService
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.playback.ShuffleContexts
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.component.Material3MenuGroup
import iad1tya.echo.music.ui.component.Material3MenuItemData
import iad1tya.echo.music.ui.component.NewAction
import iad1tya.echo.music.ui.component.NewActionGrid
import iad1tya.echo.music.ui.component.PlaylistListItem
import iad1tya.echo.music.ui.component.TextFieldDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val syncUtils = LocalSyncUtils.current
    // Same cookie gate the rest of the app uses to know if the user is signed into YouTube Music.
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    val (enableExportAsMp3) = rememberPreference(key = EnableExportAsMp3Key, defaultValue = true)
    val (_, onExportDirectoryUriChange) = rememberPreference(key = ExportDirectoryUriKey, defaultValue = "")
    // Enhanced-shuffle context for this menu's Shuffle action (must match the screens' PL:/AP: scheme).
    val menuShuffleContextId = if (autoPlaylist == true || downloadPlaylist == true) {
        "AP:" + playlist.playlist.id
    } else {
        ShuffleContexts.forPlaylist(
            playlist.playlist.isEditable,
            playlist.playlist.id,
            playlist.playlist.browseId,
        )
    }
    val menuPlayedSet = rememberPlayedShuffleSet(menuShuffleContextId)
    val dbPlaylist by database.playlist(playlist.id).collectAsState(initial = playlist)
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    LaunchedEffect(Unit) {
        if (autoPlaylist == false) {
            database.playlistSongs(playlist.id).collect {
                songs = it.map(PlaylistSong::song)
            }
        } else {
            if (songList != null) {
                songs = songList
            }
        }
    }

    // Hoisted out of the action list on purpose: the actions are built conditionally, and a
    // remember/rememberSaveable inside a conditional branch would lose its slot when the condition
    // flips. The dialog lives in the sheet's own composition (like the rename/delete dialogs above) and
    // the sheet is only dismissed from inside the callback, so closing it can't kill the dialog.
    val onMenuShuffleClick = rememberShuffleMemoryPrompt(
        contextId = menuShuffleContextId,
        playedCount = songs.count { it.id in menuPlayedSet || it.song.totalPlayTime > 0L },
        totalCount = songs.size,
    ) { resetMemory ->
        onDismiss()
        if (songs.isNotEmpty()) {
            val seed = ShuffleContexts.seedPlayedIds(
                resetMemory = resetMemory,
                songIds = songs.map { it.id },
                shufflePlayed = menuPlayedSet,
                playTimeMs = { id -> songs.firstOrNull { it.id == id }?.song?.totalPlayTime ?: 0L },
            )
            val ordered = if (resetMemory) {
                songs.shuffled()
            } else {
                val (unheard, heard) = songs.partition { it.id !in seed }
                unheard.shuffled() + heard.shuffled()
            }
            playerConnection.playQueue(
                ListQueue(
                    title = playlist.playlist.name,
                    items = ordered.map(Song::toMediaItem),
                    contextId = menuShuffleContextId,
                    startShuffled = true,
                    seedPlayedIds = seed,
                )
            )
        }
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist.playlist.isEditable == true

    val isPinned by database.speedDialDao.isPinned(playlist.id).collectAsState(initial = false)

    // Export the playlist (its songs) to a JSON file the user picks.
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    val arr = org.json.JSONArray()
                    songs.forEach { s ->
                        arr.put(
                            org.json.JSONObject()
                                .put("id", s.id)
                                .put("title", s.song.title)
                                .put("artists", s.artists.joinToString(", ") { it.name })
                        )
                    }
                    val out = org.json.JSONObject()
                        .put("name", playlist.playlist.name)
                        .put("songs", arr)
                        .toString()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(out.toByteArray()) }
                }
            }
        }
        onDismiss()
    }

    // Export the playlist as a CSV (Title,Artists) — spreadsheet-friendly.
    val exportCsvLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    val esc = { v: String -> "\"" + v.replace("\"", "\"\"") + "\"" }
                    val sb = StringBuilder("Title,Artists\n")
                    songs.forEach { s ->
                        sb.append(esc(s.song.title)).append(',')
                            .append(esc(s.artists.joinToString("; ") { it.name })).append('\n')
                    }
                    context.contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
                }
            }
        }
        onDismiss()
    }

    // MP3 export: choice → optional selection → folder picker → serial AudioExportService.start
    // (service stopSelf() after each song; concurrent starts would cancel in-flight work).
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
                        selectedMp3Ids = songs.map { it.id }.toSet()
                        showMp3SelectDialog = true
                    },
                ) {
                    Text(text = "Seleccionar")
                }
                TextButton(
                    onClick = {
                        showMp3ChoiceDialog = false
                        openMp3FolderPicker(songs)
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
                            checked = songs.isNotEmpty() && selectedMp3Ids.size == songs.size,
                            onCheckedChange = { checked ->
                                selectedMp3Ids =
                                    if (checked) songs.map { it.id }.toSet() else emptySet()
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedMp3Ids =
                            if (selectedMp3Ids.size == songs.size) emptySet()
                            else songs.map { it.id }.toSet()
                    },
                )
            }
            items(songs, key = { it.id }) { song ->
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
                            openMp3FolderPicker(songs.filter { it.id in selectedMp3Ids })
                        },
                    ) {
                        Text(text = "Continuar")
                    }
                }
            }
        }
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = stringResource(R.string.edit_playlist)) },
            onDismiss = { showEditDialog = false },
            initialTextFieldValue =
            TextFieldValue(
                playlist.playlist.name,
                TextRange(playlist.playlist.name.length),
            ),
            onDone = { name ->
                onDismiss()
                database.query {
                    update(
                        playlist.playlist.copy(
                            name = name,
                            lastUpdateTime = LocalDateTime.now()
                        )
                    )
                }
                coroutineScope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                }
            },
        )
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
                        playlist.playlist.name
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
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
                                false,
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }

    if (showDeletePlaylistDialog) {
        val ytBrowseId = playlist.playlist.browseId

        // Local delete only: removes the playlist from the app, never from YouTube.
        // For a SYNCED playlist (browseId != null) the row is KEPT as a tombstone with bookmarkedAt =
        // null instead of being deleted: the account still has it, so the next sync would otherwise see
        // "not in the library" and re-create it — the deletion undid itself on every sync (owner report).
        // The sync skips browseIds whose only local rows are un-bookmarked; saving the playlist again
        // from its online screen re-bookmarks this same row. A purely local playlist is deleted for real.
        // [alsoDeletedRemotely] = the user chose "delete from YouTube too". Then the account copy is going
        // away, so the row is really deleted: keeping a tombstone would block the sync from ever restoring
        // the playlist if the remote delete FAILS. Removing it only from the app keeps the tombstone.
        val deletePlaylistLocally: (alsoDeletedRemotely: Boolean) -> Unit = { alsoDeletedRemotely ->
            showDeletePlaylistDialog = false
            onDismiss()
            database.transaction {
                if (ytBrowseId != null && !alsoDeletedRemotely) {
                    // Tombstone only — do NOT clear the songs: the row is already invisible to every
                    // Library query, and the re-save paths only re-bookmark the row (they never re-insert
                    // songs), so wiping the map would bring the playlist back EMPTY.
                    update(playlist.playlist.copy(bookmarkedAt = null))
                } else {
                    if (playlist.playlist.bookmarkedAt != null) {
                        update(playlist.playlist.toggleLike())
                    }
                    delete(playlist.playlist)
                }
            }
        }

        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                // Local-only playlist: SAY SO. Without this the missing "delete from YouTube too" option
                // reads as a broken/regressed dialog (owner reported exactly that) instead of what it is:
                // there is no remote copy to delete because the playlist was never synced.
                if (ytBrowseId == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.delete_playlist_only_local_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }

                // Synced playlist: let the user choose whether to also delete it on YouTube.
                if (ytBrowseId != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            deletePlaylistLocally(true)
                            coroutineScope.launch(Dispatchers.IO) {
                                // Surface a remote failure: this used to be fire-and-forget, so YouTube
                                // rejecting the delete looked like success (the row vanished locally).
                                YouTube.deletePlaylist(ytBrowseId).onFailure { e ->
                                    reportException(e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.delete_playlist_youtube_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.delete_playlist_from_youtube_too))
                    }
                    TextButton(
                        onClick = { deletePlaylistLocally(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.delete_playlist_local_only))
                    }
                }
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                // Pure local playlist: keep the simple confirm (no YouTube option).
                if (ytBrowseId == null) {
                    TextButton(
                        onClick = { deletePlaylistLocally(false) }
                    ) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                }
            }
        )
    }

    PlaylistListItem(
        playlist = playlist,
        trailingContent = {
            if (playlist.playlist.isEditable != true) {
                IconButton(
                    onClick = {
                        database.query {
                            dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
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
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

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
                // buildList, not listOfNotNull(if (!isGuest) { play; shuffle } else null, ...):
                // a Kotlin block evaluates to its LAST expression, so the Play action was built
                // and silently discarded, leaving the grid with [Shuffle][Share] + an empty slot.
                actions = buildList {
                    if (!isGuest) {
                        add(NewAction(
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
                                onDismiss()
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = playlist.playlist.name,
                                            items = songs.map(Song::toMediaItem)
                                        )
                                    )
                                }
                            }
                        ))
                        add(NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.shuffle),
                            onClick = { onMenuShuffleClick() }
                        ))
                    }
                    add(NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.share),
                        onClick = {
                            onDismiss()
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, ShareLinks.playlist(dbPlaylist?.playlist?.browseId))
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    ))
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                columns = if (isGuest) 1 else 3
            )
        }

        item {
            Material3MenuGroup(
                items = buildList {
                    add(
                        Material3MenuItemData(
                            title = { Text(text = "Exportar playlist") },
                            description = { Text(text = "Guardar las canciones en un archivo") },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                exportLauncher.launch("${playlist.playlist.name}.json")
                            }
                        )
                    )
                    add(
                        Material3MenuItemData(
                            title = { Text(text = "Exportar CSV") },
                            description = { Text(text = "Título y artistas (para hojas de cálculo)") },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                exportCsvLauncher.launch("${playlist.playlist.name}.csv")
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
                                    if (songs.isEmpty()) {
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
                    if (!isGuest) {
                        playlist.playlist.browseId?.let { browseId ->
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.start_radio)) },
                                    description = { Text(text = stringResource(R.string.start_radio_desc)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            YouTube.playlist(browseId).getOrNull()?.playlist?.let { playlistItem ->
                                                playlistItem.radioEndpoint?.let { radioEndpoint ->
                                                    withContext(Dispatchers.Main) {
                                                        playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                                    }
                                                }
                                            }
                                        }
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }
                    // Manual on-demand sync — only for a YouTube-linked (synced) playlist: browseId != null.
                    if (!isGuest) {
                        playlist.playlist.browseId?.let { browseId ->
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.playlist_sync_now)) },
                                    description = { Text(text = stringResource(R.string.sync_playlist_desc)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.sync),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        onDismiss()
                                        if (!isLoggedIn) {
                                            // Never hit YouTube signed out — just tell the user to sign in.
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.sync_login_required),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.playlist_syncing),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val ok = syncUtils.syncPlaylistNow(browseId, playlist.playlist.id)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(
                                                            if (ok) R.string.playlist_synced else R.string.playlist_sync_failed
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            )
                        }
                    }
                    if (!isGuest) {
                        add(
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
                                        playerConnection.playNext(songs.map { it.toMediaItem() })
                                    }
                                    onDismiss()
                                }
                            )
                        )
                    }
                    if (!isGuest) {
                        add(
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
                                    onDismiss()
                                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
                                }
                            )
                        )
                    }
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    if (editable && autoPlaylist != true && !isGuest) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.edit)) },
                                description = { Text(text = stringResource(R.string.edit_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.edit),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showEditDialog = true
                                }
                            )
                        )
                    }
                    add(
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
                                        database.speedDialDao.insert(
                                            SpeedDialItem(
                                                id = playlist.id,
                                                title = playlist.playlist.name,
                                                subtitle = null,
                                                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                                                type = "LOCAL_PLAYLIST"
                                            )
                                        )
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )
                    if (downloadPlaylist != true) {
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
                                            coroutineScope.launch {
                                                var list = songs
                                                if (list.isEmpty() && autoPlaylist != true) {
                                                    // Menu can open before Room's first emit — same race the
                                                    // online playlist header already guards against.
                                                    list = withContext(Dispatchers.IO) {
                                                        database.playlistSongs(playlist.id)
                                                            .first()
                                                            .map(PlaylistSong::song)
                                                    }
                                                }
                                                if (list.isEmpty()) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.playlist_download_empty),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                    return@launch
                                                }
                                                list.forEach { song ->
                                                    val downloadRequest =
                                                        DownloadRequest
                                                            .Builder(song.id, song.id.toUri())
                                                            .setCustomCacheKey(song.id)
                                                            .setData(song.song.title.toByteArray())
                                                            .build()
                                                    DownloadService.sendAddDownload(
                                                        context,
                                                        ExoDownloadService::class.java,
                                                        downloadRequest,
                                                        false,
                                                    )
                                                }
                                            }
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        )
                    }
                    if (autoPlaylist != true && !isGuest) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.delete)) },
                                description = { Text(text = stringResource(R.string.delete_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showDeletePlaylistDialog = true
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}
