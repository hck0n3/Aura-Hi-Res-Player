package iad1tya.echo.music.ui.screens.migration

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.migration.MigrationEngine
import com.aura.migration.model.ImportReport
import com.aura.migration.model.MatchResult
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import com.aura.migration.source.PlaylistSource
import com.aura.migration.source.SourceError
import com.aura.migration.source.deezer.DeezerSource
import com.aura.migration.source.file.FileSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.DataSaverEnabledKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.migration.InMemoryPlaylistSource
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

enum class MigrationPhase { PICK, CONFIRM, RUNNING, DONE, ERROR }

/** One ambiguous track awaiting the user's decision. */
data class AmbiguousItem(
    val track: SourceTrack,
    val candidates: List<YtmCandidate>,
    val chosenVideoId: String? = null,
    val resolved: Boolean = false,
)

data class MigrationUiState(
    val phase: MigrationPhase = MigrationPhase.PICK,
    val signedInYouTube: Boolean = false,
    val dataSaver: Boolean = false,
    // CONFIRM
    val pendingName: String = "",
    val pendingCount: Int = 0,
    // RUNNING
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val progressCurrent: String = "",
    // DONE
    val playlistName: String = "",
    val matchedCount: Int = 0,
    val notFoundCount: Int = 0,
    val ytmPlaylistId: String? = null,
    val localPlaylistId: String? = null,
    val ambiguous: List<AmbiguousItem> = emptyList(),
    val appendingResolved: Boolean = false,
    // ERROR
    val errorMessage: String? = null,
) {
    val ambiguousPending: Int get() = ambiguous.count { !it.resolved }
}

@HiltViewModel
class MigrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: MigrationEngine,
    private val deezerSource: DeezerSource,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MigrationUiState())
    val uiState: StateFlow<MigrationUiState> = _uiState.asStateFlow()

    /** The running import, so a re-entry cancels the previous one instead of running two at once. */
    private var migrationJob: Job? = null

    /** The prepared, ready-to-run import (set in CONFIRM, consumed by [confirmAndStart]). */
    private data class Prepared(
        val source: PlaylistSource,
        val playlistId: String,
        val name: String,
        val count: Int,
    )

    private var prepared: Prepared? = null

    init {
        refreshEnvironment()
    }

    /** Re-reads YouTube login + Data Saver so the picker can gate/warn honestly. */
    fun refreshEnvironment() {
        viewModelScope.launch {
            val prefs = withContext(Dispatchers.IO) { context.dataStore.data.first() }
            _uiState.value = _uiState.value.copy(
                signedInYouTube = prefs[InnerTubeCookieKey].orEmpty().isNotEmpty(),
                dataSaver = prefs[DataSaverEnabledKey] ?: false,
            )
        }
    }

    // ── ARCHIVO ──────────────────────────────────────────────────────────

    fun prepareFileImport(uri: Uri) {
        viewModelScope.launch {
            try {
                val displayName = withContext(Dispatchers.IO) { queryDisplayName(uri) }
                    ?: uri.lastPathSegment.orEmpty()
                val extension = displayName.substringAfterLast('.', "").lowercase()
                if (extension.isBlank()) {
                    fail("No pude reconocer la extensión del archivo. Usa CSV, M3U, JSPF o XSPF.")
                    return@launch
                }
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // UTF-8, tolerating a leading BOM that some exporters (Excel/Windows) prepend.
                        input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
                    }
                } ?: run {
                    fail("No pude leer el archivo.")
                    return@launch
                }

                val tracks = withContext(Dispatchers.IO) { FileSource().parse(text, extension) }
                if (tracks.isEmpty()) {
                    fail("No encontré canciones en el archivo. Revisa que tenga columnas de título y artista.")
                    return@launch
                }

                val name = displayName.substringBeforeLast('.').ifBlank { "Playlist importada" }
                prepared = Prepared(
                    source = InMemoryPlaylistSource(tracks, displayName = name),
                    playlistId = "",
                    name = name,
                    count = tracks.size,
                )
                toConfirm(name, tracks.size)
            } catch (e: SourceError) {
                fail(e.message ?: "Formato de archivo no soportado.")
            } catch (e: Exception) {
                fail(e.message ?: "No pude procesar el archivo.")
            }
        }
    }

    // ── DEEZER ───────────────────────────────────────────────────────────

    fun prepareDeezerImport(input: String) {
        viewModelScope.launch {
            try {
                val id = deezerSource.parseId(input)
                val meta = withContext(Dispatchers.IO) { deezerSource.listPlaylists(input) }.firstOrNull()
                val name = meta?.name?.takeIf { it.isNotBlank() } ?: "Playlist de Deezer"
                val count = meta?.trackCount ?: 0
                prepared = Prepared(source = deezerSource, playlistId = id, name = name, count = count)
                toConfirm(name, count)
            } catch (e: SourceError) {
                fail(e.message ?: "No pude leer la playlist de Deezer.")
            } catch (e: Exception) {
                fail(e.message ?: "URL de Deezer no válida.")
            }
        }
    }

    // ── IMPORT ───────────────────────────────────────────────────────────

    fun confirmAndStart() {
        val p = prepared ?: return
        if (!_uiState.value.signedInYouTube) {
            fail("Inicia sesión en YouTube Music (Ajustes → Cuentas) para migrar: la playlist se crea en tu cuenta de YouTube Music.")
            return
        }
        _uiState.value = _uiState.value.copy(
            phase = MigrationPhase.RUNNING,
            progressDone = 0,
            progressTotal = p.count,
            progressCurrent = "",
            errorMessage = null,
        )
        // Dispatchers.IO is LOAD-BEARING, not decoration: MigrationEngine.import is a plain flow{} with
        // no flowOn, so its body runs on the collector's dispatcher. On viewModelScope (Main) the very
        // first act — source.fetchTracks() — is a SYNCHRONOUS OkHttp call for Deezer =>
        // NetworkOnMainThreadException (swallowed into Progress.Failed => "la migración falló" on EVERY
        // Deezer import), and createPlaylist's runBlocking would ANR on slow networks for File imports
        // too. Collecting on IO puts the whole resolve/create loop off the main thread; the UI-state
        // writes below are cheap value copies to a StateFlow (thread-safe), read back on Main by Compose.
        migrationJob?.cancel()
        migrationJob = viewModelScope.launch(Dispatchers.IO) {
            engine.import(p.source, p.playlistId, playlistName = p.name, createInYtm = true)
                .collect { progress ->
                    when (progress) {
                        is MigrationEngine.Progress.Started ->
                            _uiState.value = _uiState.value.copy(
                                progressTotal = progress.total,
                                progressDone = 0,
                                playlistName = progress.playlistName,
                            )

                        is MigrationEngine.Progress.Track -> {
                            _uiState.value = _uiState.value.copy(
                                progressDone = progress.done,
                                progressTotal = progress.total,
                                progressCurrent = progress.current,
                            )
                            // Real thermal backpressure: the engine's flow is UNBUFFERED, so suspending
                            // the collector here suspends the producer — pacing the up-to-4 searches/track
                            // so a 1000-track import can't hammer the endpoint back-to-back (the promise
                            // the unused MigrationRunner made; enforced on the LIVE path instead).
                            delay(TRACK_THROTTLE_MS)
                        }

                        is MigrationEngine.Progress.Finished -> onFinished(progress.report, progress.ytmPlaylistId)

                        is MigrationEngine.Progress.Failed ->
                            fail(progress.error.message ?: "La migración falló.")
                    }
                }
        }
    }

    private suspend fun onFinished(report: ImportReport, ytmPlaylistId: String?) {
        var localId: String? = null
        if (ytmPlaylistId != null) {
            // Persist the local mirror row. bookmarkedAt is MANDATORY — without it the playlist is invisible
            // in Library (documented landmine). browseId = the YTM id so Aura's sync keeps it up to date.
            localId = PlaylistEntity.generatePlaylistId()
            val entity = PlaylistEntity(
                id = localId,
                name = report.playlistName,
                browseId = ytmPlaylistId,
                bookmarkedAt = LocalDateTime.now(),
                isEditable = true,
            )
            // Await the insert (not fire-and-forget) so the row exists BEFORE syncPlaylist writes
            // PlaylistSongMap rows against it — same insert-parent-first order as the Spotify import.
            database.withTransaction { insert(entity) }
            // Pull the freshly-created YTM playlist's songs into the local row so it shows populated.
            syncUtils.syncPlaylist(ytmPlaylistId, localId)
        }

        _uiState.value = _uiState.value.copy(
            phase = MigrationPhase.DONE,
            playlistName = report.playlistName,
            matchedCount = report.matched.size,
            notFoundCount = report.notFound.size,
            ytmPlaylistId = ytmPlaylistId,
            localPlaylistId = localId,
            ambiguous = report.ambiguous.map { AmbiguousItem(it.source, it.candidates) },
        )
    }

    // ── AMBIGUOUS REVIEW ─────────────────────────────────────────────────

    fun chooseCandidate(index: Int, videoId: String) {
        val list = _uiState.value.ambiguous.toMutableList()
        val item = list.getOrNull(index) ?: return
        list[index] = item.copy(chosenVideoId = videoId, resolved = true)
        _uiState.value = _uiState.value.copy(ambiguous = list)
    }

    fun skipAmbiguous(index: Int) {
        val list = _uiState.value.ambiguous.toMutableList()
        val item = list.getOrNull(index) ?: return
        list[index] = item.copy(chosenVideoId = null, resolved = true)
        _uiState.value = _uiState.value.copy(ambiguous = list)
    }

    /** Persists the user's picks and appends them to the created YTM playlist, then re-syncs. */
    fun applyResolved() {
        val state = _uiState.value
        val ytmId = state.ytmPlaylistId
        val localId = state.localPlaylistId
        if (ytmId == null) {
            fail("No se creó ninguna playlist (ninguna canción tuvo coincidencia automática), así que no hay dónde añadir las revisadas.")
            return
        }
        val chosen = state.ambiguous.filter { it.chosenVideoId != null }
        if (chosen.isEmpty()) {
            // Nothing to add: just drop the resolved items and stay on the result screen.
            _uiState.value = state.copy(ambiguous = state.ambiguous.filterNot { it.resolved })
            return
        }
        _uiState.value = state.copy(appendingResolved = true)
        viewModelScope.launch {
            try {
                chosen.forEach { engine.confirmMatch(it.track, it.chosenVideoId!!) }
                engine.appendResolved(ytmId, chosen.map { it.chosenVideoId!! })
                if (localId != null) syncUtils.syncPlaylist(ytmId, localId)
                _uiState.value = _uiState.value.copy(
                    appendingResolved = false,
                    matchedCount = _uiState.value.matchedCount + chosen.size,
                    // Keep only the still-unresolved ones (skipped or untouched) on the list.
                    ambiguous = _uiState.value.ambiguous.filterNot { it.resolved },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(appendingResolved = false)
                fail(e.message ?: "No pude añadir las canciones revisadas.")
            }
        }
    }

    // ── NAV / STATE HELPERS ──────────────────────────────────────────────

    fun reset() {
        prepared = null
        _uiState.value = MigrationUiState(
            signedInYouTube = _uiState.value.signedInYouTube,
            dataSaver = _uiState.value.dataSaver,
        )
    }

    fun cancelPending() {
        prepared = null
        _uiState.value = _uiState.value.copy(phase = MigrationPhase.PICK, errorMessage = null)
    }

    fun dismissError() {
        // From an error, return to the picker so the user can retry.
        _uiState.value = _uiState.value.copy(phase = MigrationPhase.PICK, errorMessage = null)
    }

    private fun toConfirm(name: String, count: Int) {
        _uiState.value = _uiState.value.copy(
            phase = MigrationPhase.CONFIRM,
            pendingName = name,
            pendingCount = count,
            errorMessage = null,
        )
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(phase = MigrationPhase.ERROR, errorMessage = message)
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && c.columnCount > 0) c.getString(0) else null
        }

    private companion object {
        // Per-track pause between resolves — matches the (now-removed) MigrationRunner's value.
        const val TRACK_THROTTLE_MS = 120L
    }
}
