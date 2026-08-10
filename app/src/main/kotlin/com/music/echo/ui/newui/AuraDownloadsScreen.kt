package iad1tya.echo.music.ui.newui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.R
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.SongSortDescendingKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.constants.SongSortTypeKey
import iad1tya.echo.music.constants.YtmSyncKey
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.extensions.tryOrNull
import iad1tya.echo.music.playback.ExoDownloadService
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.AutoPlaylistMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.screens.playlist.PlaylistType
import iad1tya.echo.music.ui.utils.formatFileSize
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.AutoPlaylistViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

/**
 * # Descargado / auto-listas — "Interfaz nueva"
 *
 * The redesigned `AutoPlaylistScreen` (route `auto_playlist/{playlist}`). That ONE classic screen is
 * four screens to the user — **Descargado**, Canciones que me gustan, Subidas, Exportado — and the
 * downloads screen the order asks for is its `downloaded` mode. Rebuilding only that mode would have
 * meant a host that branches on a route argument; rebuilding the screen keeps one host, one call site
 * and the other three modes are covered for free.
 *
 * The shape itself lives in [AuraSongCollectionScaffold], shared with "En caché" and "Mi Top N" — the
 * three auto-collections used to be one redesigned screen and two classic ones, i.e. two dialects
 * inside one feature.
 *
 * ## Presentation only
 * The list, the sort keys, the search filter, the queue, the "Aleatorio mejorado" memory and every
 * menu are the classic ones:
 *  · [AutoPlaylistViewModel] — the same `hiltViewModel()`, so the same DataStore-driven flow decides
 *    which songs are in the list, in which order, with the same explicit/video filters.
 *  · `contextId = "AP:<playlist>"` — the SAME no-repeat bucket, so toggling "Interfaz nueva" does not
 *    hand the user a second shuffle memory and make songs repeat.
 *  · [SongMenu] / [AutoPlaylistMenu] — the classic sheets, which is where per-song *Descargar /
 *    Descargando (cancela) / Eliminar descarga* and every bulk action live.
 *
 * ## What the classic screen could NOT show, and this one does
 * `database.downloadedSongs(...)` only lists songs whose download has **completed**, so on the classic
 * "Descargado" screen a download in flight is invisible: no progress, nothing to cancel, no sign a
 * failure happened. The two additions below both read REAL state and are gated to
 * [PlaylistType.DOWNLOAD]:
 *  · **Descargas en curso** — the live `DownloadUtil.downloads` map, one row per queued / downloading /
 *    failed / paused item, with the real percentage (see [rememberAuraDownloadProgress]), a cancel
 *    that is `DownloadService.sendRemoveDownload` and a retry that re-enqueues the SAME
 *    [DownloadRequest] the rest of the app builds.
 *  · **Almacenamiento** — `downloadCache.cacheSpace`, the exact number Ajustes ▸ Almacenamiento
 *    prints, formatted with the same [formatFileSize]. Tapping it opens that screen, where the
 *    "vaciar descargas" action already lives.
 *
 * @param scrollBehavior accepted for signature parity with the classic screen; this shape draws its own
 *   header instead of a `TopAppBar`, so there is no collapsing bar to drive with it.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraAutoPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val downloadUtil = LocalDownloadUtil.current

    // Same title mapping as the classic screen (AutoPlaylistScreen.kt:139-145).
    val playlist = when (viewModel.playlist) {
        "liked" -> stringResource(R.string.liked)
        "exported" -> stringResource(R.string.action_exported)
        "exported_videos" -> stringResource(R.string.exported_videos_playlist)
        else -> stringResource(R.string.offline)
    }
    val playlistId = viewModel.playlist
    val playlistType = when (playlistId) {
        "liked" -> PlaylistType.LIKE
        "downloaded" -> PlaylistType.DOWNLOAD
        "exported" -> PlaylistType.EXPORTED
        "exported_videos" -> PlaylistType.EXPORTED_VIDEO
        else -> PlaylistType.OTHER
    }
    val contextId = "AP:$playlistId"

    val songs by viewModel.likedSongs.collectAsState(null)

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    // Aggregate download state of the whole collection — the value AutoPlaylistMenu turns into
    // "Descargar" / "Descargando (cancela)" / "Eliminar descarga". Same derivation as the classic
    // screen, driven by the same flow.
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
    LaunchedEffect(songs) {
        if (songs?.isEmpty() != false) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState = when {
                songs?.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED } == true ->
                    Download.STATE_COMPLETED

                songs?.all {
                    downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                        downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                        downloads[it.song.id]?.state == Download.STATE_COMPLETED
                } == true -> Download.STATE_DOWNLOADING

                else -> Download.STATE_STOPPED
            }
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs?.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
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

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val canRefresh = playlistType == PlaylistType.LIKE

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                if (playlistType == PlaylistType.LIKE) viewModel.syncLikedSongs()
            }
        }
    }

    val isDownloadsScreen = playlistType == PlaylistType.DOWNLOAD
    val activeDownloads = rememberAuraActiveDownloads(enabled = isDownloadsScreen)
    val downloadProgress by rememberAuraDownloadProgress(enabled = isDownloadsScreen)

    val currentSongs = songs
    val emptyText = if (playlistType == PlaylistType.EXPORTED_VIDEO) {
        stringResource(R.string.exported_videos_empty)
    } else {
        null
    }
    AuraSongCollectionScaffold(
        title = playlist,
        songs = currentSongs,
        contextId = contextId,
        aboutTitleRes = R.string.about_album,
        aboutText = stringResource(R.string.aura_auto_playlist_about, playlist),
        onBack = { navController.navigateUp() },
        emptyText = emptyText,
        onHeaderMenu = {
            menuState.show {
                AutoPlaylistMenu(
                    downloadState = downloadState,
                    // Only Liked can sync, and only when signed in — the classic gate.
                    onSync = if (isLoggedIn && playlistType == PlaylistType.LIKE) {
                        {
                            viewModel.syncLikedSongs()
                            Toast.makeText(
                                context,
                                "Sincronizando con YouTube Music…",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else null,
                    onQueue = {
                        playerConnection.addToQueue(currentSongs.orEmpty().map { it.toMediaItem() })
                    },
                    onDownload = {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> showRemoveDownloadDialog = true
                            Download.STATE_DOWNLOADING -> currentSongs.orEmpty().forEach { song ->
                                DownloadService.sendRemoveDownload(
                                    context,
                                    ExoDownloadService::class.java,
                                    song.song.id,
                                    false,
                                )
                            }

                            else -> currentSongs.orEmpty().forEach { song ->
                                val request = DownloadRequest
                                    .Builder(song.song.id, song.song.id.toUri())
                                    .setCustomCacheKey(song.song.id)
                                    .setData(song.song.title.toByteArray())
                                    .build()
                                DownloadService.sendAddDownload(
                                    context,
                                    ExoDownloadService::class.java,
                                    request,
                                    false,
                                )
                            }
                        }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        },
        onSongMenu = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        // On "Descargado" every row IS a download, so the tick would be noise — the same rule the new
        // Biblioteca applies to its Descargado sub-filter.
        showDownloadTick = !isDownloadsScreen,
        canRefresh = canRefresh,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        sortItem = { filtered ->
            AuraInlineSortControl(
                sortType = sortType,
                sortDescending = sortDescending,
                options = listOf(
                    SongSortType.CREATE_DATE to R.string.sort_by_create_date,
                    SongSortType.NAME to R.string.sort_by_name,
                    SongSortType.ARTIST to R.string.sort_by_artist,
                    SongSortType.PLAY_TIME to R.string.sort_by_play_time,
                ),
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                trailing = { AuraSongCountLabel(filtered.size) },
            )
        },
        extraItemsPresent = activeDownloads.isNotEmpty(),
        extraItems = { isSearching ->
            // ── Almacenamiento (solo "Descargado") ────────────────────────────────────────────────
            if (isDownloadsScreen && !isSearching) {
                item(key = "aura_ap_storage") {
                    AuraDownloadStorageCard(
                        onOpenStorageSettings = { navController.navigate("settings/storage") },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // ── Descargas en curso (solo "Descargado") ────────────────────────────────────────────
            if (isDownloadsScreen && activeDownloads.isNotEmpty()) {
                item(key = "aura_ap_active_label") {
                    AuraSectionLabel(
                        text = stringResource(R.string.aura_downloads_in_progress).uppercase(Locale.ROOT),
                        modifier = Modifier
                            .animateItem()
                            .padding(
                                start = AuraSpacing.Gutter,
                                end = AuraSpacing.Gutter,
                                top = AuraSpacing.SectionTop,
                                bottom = AuraSpacing.SectionGap,
                            ),
                    )
                }
                items(
                    items = activeDownloads,
                    key = { "aura_dl_" + it.request.id },
                ) { download ->
                    AuraActiveDownloadRow(
                        download = download,
                        percent = downloadProgress[download.request.id],
                        onCancel = {
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                download.request.id,
                                false,
                            )
                        },
                        onRetry = {
                            // Re-enqueue the SAME request the rest of the app builds, after dropping the
                            // memoised stream URL so a failed download re-resolves instead of pulling the
                            // same stale stream again (DownloadUtil.invalidateSongUrl).
                            downloadUtil.invalidateSongUrl(download.request.id)
                            DownloadService.sendAddDownload(
                                context,
                                ExoDownloadService::class.java,
                                download.request,
                                false,
                            )
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = AuraSpacing.Gutter),
                    )
                }
            }
        },
    )
}

// ── Descargas ─────────────────────────────────────────────────────────────────────────────────────

/**
 * "Canciones descargadas · <tamaño real>". The number is `downloadCache.cacheSpace`, i.e. the exact
 * value Ajustes ▸ Almacenamiento prints, through the same [formatFileSize]; tapping opens that screen,
 * which already owns "vaciar descargas". Read off the main thread and only while a transfer is in
 * flight — the cache walk is disk work and this card is decoration, not a meter.
 */
@Composable
private fun AuraDownloadStorageCard(
    onOpenStorageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadUtil = LocalDownloadUtil.current
    val downloads by downloadUtil.downloads.collectAsState()
    var sizeBytes by remember { mutableLongStateOf(-1L) }
    // Only a transfer in flight makes the number move without the map changing; the rest of the time
    // one measurement per map change is exact. `cacheSpace` walks the cache index, so this is a slow
    // 5 s tick and never an idle-screen loop.
    val measuringWhileActive = downloads.values.any { it.state == Download.STATE_DOWNLOADING }

    LaunchedEffect(downloads.size, measuringWhileActive) {
        while (isActive) {
            sizeBytes = withContext(Dispatchers.IO) {
                tryOrNull { downloadUtil.downloadCache.cacheSpace } ?: 0L
            }
            if (!measuringWhileActive) return@LaunchedEffect
            delay(5000)
        }
    }

    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 6.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(
                onClick = onOpenStorageSettings,
                contentDescription = stringResource(R.string.storage),
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        AuraIconGlyph(
            icon = AuraIcons.Download,
            contentDescription = null,
            size = 19.dp,
            tint = AuraPalette.Teal,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.downloaded_songs),
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            // Until the first measurement lands nothing is printed — a "0 B" that later jumps would be
            // a number the screen never actually knew.
            if (sizeBytes >= 0L) {
                AuraTechnicalText(text = formatFileSize(sizeBytes))
            }
        }
        AuraIconGlyph(
            icon = AuraIcons.ChevronRight,
            contentDescription = null,
            size = 16.dp,
            tint = AuraPalette.OnGroundDisabled,
        )
    }
}

/**
 * One in-flight download: title, state, the real percentage, and the two actions the classic screen
 * had nowhere to put — cancel (`sendRemoveDownload`, the same call the ⋯ menus make) and, for a failed
 * one, retry.
 *
 * The title comes from the DB row when the song is known and otherwise from `request.data`, which every
 * enqueue site in the app fills with the song title (`setData(title.toByteArray())`) — so a download
 * queued before the song was ever stored still names itself instead of showing a video id.
 */
@Composable
private fun AuraActiveDownloadRow(
    download: Download,
    percent: Float?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val song by remember(download.request.id) { database.song(download.request.id) }
        .collectAsState(initial = null)

    val fallbackTitle = remember(download.request) {
        runCatching { download.request.data.toString(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: download.request.id
    }
    val title = song?.song?.title ?: fallbackTitle
    val failed = download.state == Download.STATE_FAILED
    val subtitle = when (download.state) {
        Download.STATE_FAILED -> stringResource(R.string.aura_download_failed)
        Download.STATE_QUEUED -> stringResource(R.string.aura_download_queued)
        Download.STATE_STOPPED -> stringResource(R.string.aura_download_paused)
        else -> stringResource(R.string.downloading)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AuraRow(
            title = title,
            subtitle = subtitle,
            contentDescription = title,
            artwork = {
                AuraCover(
                    thumbnailUrl = song?.song?.thumbnailUrl,
                    size = 50.dp,
                    seed = download.request.id,
                )
            },
            trailing = {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // `percentDownloaded` is C.PERCENTAGE_UNSET (-1) until the transfer knows the
                    // content length, so an unknown percentage prints nothing instead of "0 %".
                    if (percent != null && percent >= 0f) {
                        AuraTechnicalText(
                            text = "${percent.toInt()} %",
                            color = if (failed) AuraPalette.OnGroundGhost else AuraPalette.Teal,
                        )
                    }
                    if (failed) {
                        AuraIconButton(
                            icon = AuraIcons.Download,
                            contentDescription = stringResource(R.string.retry),
                            onClick = onRetry,
                            size = 18.dp,
                            tint = AuraPalette.Teal,
                        )
                    }
                    AuraIconButton(
                        // A "+" turned 45° is the render's close glyph.
                        icon = AuraIcons.Plus,
                        contentDescription = stringResource(R.string.remove_download),
                        onClick = onCancel,
                        size = 18.dp,
                        tint = AuraPalette.OnGroundDisabled,
                        modifier = Modifier.graphicsLayer { rotationZ = 45f },
                    )
                }
            },
        )
        // Determinate while the percentage is known, indeterminate while it is not — never a bar that
        // pretends to know a progress nobody reported.
        if (percent != null && percent >= 0f) {
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                color = if (failed) AuraPalette.OnGroundDisabled else AuraPalette.Teal,
                trackColor = AuraPalette.TrackEmpty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(AuraShapes.Pill),
            )
        } else if (!failed) {
            LinearProgressIndicator(
                color = AuraPalette.Teal,
                trackColor = AuraPalette.TrackEmpty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(AuraShapes.Pill),
            )
        }
    }
}

/**
 * The downloads that are NOT finished: queued, downloading, failed or paused. Read from the same
 * `DownloadUtil.downloads` flow the badges and the menus read, so the section can never disagree with
 * the rest of the app.
 */
@Composable
private fun rememberAuraActiveDownloads(enabled: Boolean): List<Download> {
    // Collected UNCONDITIONALLY — `enabled` only decides what comes out, never whether a composable
    // runs, so the call order can never depend on which auto-playlist is open.
    val downloads by LocalDownloadUtil.current.downloads.collectAsState()
    return remember(downloads, enabled) {
        if (!enabled) return@remember emptyList()
        downloads.values
            .filter {
                it.state == Download.STATE_DOWNLOADING ||
                    it.state == Download.STATE_QUEUED ||
                    it.state == Download.STATE_FAILED ||
                    it.state == Download.STATE_STOPPED
            }
            // Active first, then queued, then the ones that need attention — the order the user acts in.
            .sortedBy { download ->
                when (download.state) {
                    Download.STATE_DOWNLOADING -> 0
                    Download.STATE_QUEUED -> 1
                    Download.STATE_STOPPED -> 2
                    else -> 3
                }
            }
    }
}

/**
 * Live per-download percentage, `id -> percentDownloaded`.
 *
 * `DownloadManager.Listener.onDownloadChanged` fires on STATE changes, not on progress, so the map in
 * `DownloadUtil.downloads` carries no moving percentage — a bar driven off it would sit still and lie.
 * media3's own live source is `DownloadManager.currentDownloads`, which is polled here.
 *
 * Heat/battery: the loop exists ONLY while something is actually downloading (`hasActive`), ticks once
 * a second, and is cancelled with the composition. Nothing polls on an idle screen.
 */
@Composable
private fun rememberAuraDownloadProgress(enabled: Boolean): State<Map<String, Float>> {
    val downloadUtil = LocalDownloadUtil.current
    val downloads by downloadUtil.downloads.collectAsState()
    val hasActive = enabled && downloads.values.any {
        it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
    }
    val progress = remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    LaunchedEffect(hasActive) {
        if (!hasActive) {
            progress.value = emptyMap()
            return@LaunchedEffect
        }
        while (isActive) {
            progress.value = downloadUtil.downloadManager.currentDownloads
                .associate { it.request.id to it.percentDownloaded }
            delay(1000)
        }
    }
    return progress
}
