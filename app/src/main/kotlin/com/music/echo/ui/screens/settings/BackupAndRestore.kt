

package iad1tya.echo.music.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.ui.menu.AddToPlaylistDialogOnline
import iad1tya.echo.music.ui.menu.CsvColumnMappingDialog
import iad1tya.echo.music.ui.menu.CsvImportProgressDialog
import iad1tya.echo.music.ui.menu.LoadingScreen
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.viewmodels.BackupRestoreViewModel
import iad1tya.echo.music.viewmodels.ConvertedSongLog
import iad1tya.echo.music.viewmodels.CsvImportState
import iad1tya.echo.music.utils.rememberPreference
import android.app.backup.BackupManager
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding

enum class BackupSubScreen { MAIN, IMPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestore(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    var importedTitle by remember { mutableStateOf("") }
    val importedSongs = remember { mutableStateListOf<Song>() }
    var showChoosePlaylistDialogOnline by rememberSaveable {
        mutableStateOf(false)
    }

    var isProgressStarted by rememberSaveable {
        mutableStateOf(false)
    }

    var progressPercentage by rememberSaveable {
        mutableIntStateOf(0)
    }

    
    var csvImportState by remember { mutableStateOf<CsvImportState?>(null) }
    var showCsvColumnMapping by rememberSaveable { mutableStateOf(false) }
    var showCsvImportProgress by rememberSaveable { mutableStateOf(false) }
    var csvImportProgress by rememberSaveable { mutableIntStateOf(0) }
    val csvRecentLogs = remember { mutableStateListOf<ConvertedSongLog>() }
    var pendingCsvUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = iad1tya.echo.music.LocalDatabase.current

    val importJrLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val result = runCatching {
                    val content = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().decodeToString()
                    } ?: return@runCatching null
                    val file = iad1tya.echo.music.playlistimport.JrPlaylistImporter.parse(content)
                    iad1tya.echo.music.playlistimport.JrPlaylistImporter.import(database, file)
                }.getOrNull()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = if (result == null) {
                        "Couldn't import playlist"
                    } else {
                        "Imported ${result.resolved}/${result.total} tracks into \"${result.playlistName}\""
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(context, uri)
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.restore(context, uri)
            }
        }
    val importPlaylistFromCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            pendingCsvUri = uri
            val previewState = viewModel.previewCsvFile(context, uri)
            csvImportState = previewState
            showCsvColumnMapping = true
        }
    val importM3uLauncherOnline = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = viewModel.loadM3UOnline(context, uri)
        importedSongs.clear()
        importedSongs.addAll(result)

        if (importedSongs.isNotEmpty()) {
            showChoosePlaylistDialogOnline = true
        }
    }

    // Selective migration (playlists / all artists / all EQ presets) — additive, never destructive.
    var showSelectiveExportDialog by rememberSaveable { mutableStateOf(false) }
    val selectedPlaylistIds = remember { mutableStateListOf<String>() }
    var includeArtists by rememberSaveable { mutableStateOf(true) }
    var includePresets by rememberSaveable { mutableStateOf(true) }
    val selectivePlaylists by viewModel.playlists.collectAsState()

    val selectiveExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.exportSelective(
                context, uri, selectedPlaylistIds.toList(), includeArtists, includePresets,
            )
        }
    val selectiveImportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importSelective(context, uri)
        }

    var currentScreen by rememberSaveable { mutableStateOf(BackupSubScreen.MAIN) }

    BackHandler(enabled = currentScreen != BackupSubScreen.MAIN) {
        currentScreen = BackupSubScreen.MAIN
    }

    Crossfade(targetState = currentScreen, label = "BackupSubScreen") { screen ->
        Column(
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Top
                    )
                )
            )

            when (screen) {
                BackupSubScreen.MAIN -> {
                    Material3SettingsGroup(
                        items = listOf(
                            Material3SettingsItem(
                                title = { Text("Copia de seguridad local") },
                                description = { Text("Crea una copia de seguridad ZIP manual de tus datos") },
                                icon = painterResource(R.drawable.backup),
                                onClick = {
                                    val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                                    backupLauncher.launch(
                                        "${context.getString(R.string.app_name)}_${
                                            LocalDateTime.now().format(formatter)
                                        }.backup"
                                    )
                                }
                            ),
                            Material3SettingsItem(
                                title = { Text("Migración selectiva (Aura)") },
                                description = { Text("Elige qué playlists migrar, y/o todos los artistas y todos los presets de EQ") },
                                icon = painterResource(R.drawable.backup),
                                onClick = { showSelectiveExportDialog = true }
                            )
                        )
                    )
                    Spacer(modifier = Modifier.padding(8.dp))

                    Material3SettingsGroup(
                        items = listOf(
                            Material3SettingsItem(
                                title = { Text("Importar") },
                                description = { Text("Restaura datos desde copias de seguridad u otras fuentes") },
                                icon = painterResource(R.drawable.restore),
                                onClick = { currentScreen = BackupSubScreen.IMPORT }
                            )
                        )
                    )
                }
                BackupSubScreen.IMPORT -> {
                    Material3SettingsGroup(
                        title = "Import Data",
                        items = listOf(
                            Material3SettingsItem(
                                title = { Text("Importar desde Spotify") },
                                icon = painterResource(R.drawable.ic_spotify),
                                onClick = { navController.navigate("settings/spotify_import") }
                            ),
                            Material3SettingsItem(
                                title = { Text("Sincronizar desde YouTube Music") },
                                description = { Text("Trae tu me gusta, álbumes, artistas, suscripciones y playlists de tu cuenta") },
                                icon = painterResource(R.drawable.sync),
                                onClick = { navController.navigate("settings/ytm_sync") }
                            ),
                            Material3SettingsItem(
                                title = { Text("Importar desde archivo local") },
                                icon = painterResource(R.drawable.restore),
                                onClick = {
                                    // Use */* so the custom ".backup" file is always selectable:
                                    // SAF derives MIME from the unknown ".backup" extension and an
                                    // octet-stream-only filter greys it out on many devices.
                                    restoreLauncher.launch(arrayOf("*/*"))
                                }
                            ),
                            Material3SettingsItem(
                                title = { Text("Importar lista 'm3u'") },
                                icon = painterResource(R.drawable.playlist_add),
                                onClick = {
                                    importM3uLauncherOnline.launch(arrayOf("audio/*"))
                                }
                            ),
                            Material3SettingsItem(
                                title = { Text("Importar lista 'csv'") },
                                icon = painterResource(R.drawable.playlist_add),
                                onClick = {
                                    importPlaylistFromCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain"))
                                }
                            ),
                            Material3SettingsItem(
                                title = { Text("Importar lista de Aura Hi-Res Player") },
                                description = { Text(".jrpl.json exported from the desktop app") },
                                icon = painterResource(R.drawable.playlist_add),
                                onClick = {
                                    importJrLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                }
                            ),
                            Material3SettingsItem(
                                title = { Text("Importar migración selectiva (Aura)") },
                                description = { Text("Playlists, artistas y presets exportados de Aura — aditivo, no borra nada") },
                                icon = painterResource(R.drawable.restore),
                                onClick = {
                                    selectiveImportLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                            )
                        )
                    )
                }
            }
        }
    }
    val titleRes = when (currentScreen) {
        BackupSubScreen.MAIN -> stringResource(R.string.backup_restore)
        BackupSubScreen.IMPORT -> "Import"
    }

    TopAppBar(
        title = { Text(titleRes) },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (currentScreen != BackupSubScreen.MAIN) {
                        currentScreen = BackupSubScreen.MAIN
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        initialTextFieldValue = importedTitle,
        songs = importedSongs,
        onDismiss = { showChoosePlaylistDialogOnline = false },
        onProgressStart = { newVal -> isProgressStarted = newVal },
        onPercentageChange = { newPercentage -> progressPercentage = newPercentage }
    )

    LaunchedEffect(progressPercentage, isProgressStarted) {
        if (isProgressStarted && progressPercentage == 99) {
            delay(10000)
            if (progressPercentage == 99) {
                isProgressStarted = false
                progressPercentage = 0
            }
        }
    }

    LoadingScreen(
        isVisible = isProgressStarted,
        value = progressPercentage,
    )

    
    csvImportState?.let { state ->
        CsvColumnMappingDialog(
            isVisible = showCsvColumnMapping,
            csvState = state,
            onDismiss = {
                showCsvColumnMapping = false
                csvImportState = null
            },
            onConfirm = { mappingState ->
                showCsvColumnMapping = false
                csvImportState = mappingState
                pendingCsvUri?.let { uri ->
                    showCsvImportProgress = true
                    coroutineScope.launch(Dispatchers.Default) {
                        val result = viewModel.importPlaylistFromCsv(
                            context,
                            uri,
                            mappingState,
                            onProgress = { progress ->
                                csvImportProgress = progress
                            },
                            onLogUpdate = { logs ->
                                csvRecentLogs.clear()
                                csvRecentLogs.addAll(logs)
                            },
                        )
                        importedSongs.clear()
                        importedSongs.addAll(result)
                        if (result.isNotEmpty()) {
                            showCsvImportProgress = false
                            csvImportProgress = 0
                            csvRecentLogs.clear()
                            showChoosePlaylistDialogOnline = true
                        }
                    }
                }
            },
        )
    }

    
    CsvImportProgressDialog(
        isVisible = showCsvImportProgress,
        progress = csvImportProgress,
        recentLogs = csvRecentLogs.toList(),
        onDismiss = {

        },
    )

    if (showSelectiveExportDialog) {
        SelectiveExportDialog(
            playlists = selectivePlaylists,
            selectedIds = selectedPlaylistIds,
            includeArtists = includeArtists,
            includePresets = includePresets,
            onToggleArtists = { includeArtists = it },
            onTogglePresets = { includePresets = it },
            onDismiss = { showSelectiveExportDialog = false },
            onExport = {
                showSelectiveExportDialog = false
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                selectiveExportLauncher.launch("aura-migracion-${LocalDateTime.now().format(formatter)}.json")
            },
        )
    }
}

@Composable
private fun SelectiveExportDialog(
    playlists: List<iad1tya.echo.music.db.entities.Playlist>,
    selectedIds: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    includeArtists: Boolean,
    includePresets: Boolean,
    onToggleArtists: (Boolean) -> Unit,
    onTogglePresets: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Migración selectiva") },
        text = {
            Column {
                Text(
                    "Elige qué exportar a un archivo. Al importarlo se añade a tu biblioteca sin borrar nada.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(4.dp))
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = includeArtists, onCheckedChange = onToggleArtists)
                    Spacer(Modifier.padding(4.dp))
                    Text("Todos los artistas seguidos")
                }
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = includePresets, onCheckedChange = onTogglePresets)
                    Spacer(Modifier.padding(4.dp))
                    Text("Todos los presets de EQ")
                }
                Spacer(Modifier.padding(4.dp))
                Text("Playlists (elige cuáles):", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                if (playlists.isEmpty()) {
                    Text(
                        "No tienes playlists.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                    ) {
                        items(playlists) { pl ->
                            val id = pl.playlist.id
                            val checked = selectedIds.contains(id)
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (checked) selectedIds.remove(id) else selectedIds.add(id) }
                                    .padding(vertical = 4.dp),
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = checked,
                                    onCheckedChange = { if (it) selectedIds.add(id) else selectedIds.remove(id) },
                                )
                                Spacer(Modifier.padding(4.dp))
                                Text(pl.playlist.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = onExport,
                enabled = selectedIds.isNotEmpty() || includeArtists || includePresets,
            ) { Text("Exportar") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

