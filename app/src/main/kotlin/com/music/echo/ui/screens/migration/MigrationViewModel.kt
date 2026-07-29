package iad1tya.echo.music.ui.screens.migration

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.migration.MigrationEngine
import com.aura.migration.model.ImportReport
import com.aura.migration.model.MatchResult
import com.aura.migration.model.SourcePlaylist
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import com.aura.migration.source.PlaylistSource
import com.aura.migration.source.SourceError
import com.aura.migration.source.deezer.DeezerSource
import com.aura.migration.source.file.FileSource
import com.aura.migration.source.tidal.TidalSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.DataSaverEnabledKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.migration.InMemoryPlaylistSource
import iad1tya.echo.music.migration.TidalTokenStore
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

/**
 * Tidal OAuth + collection state, kept SEPARATE from [MigrationUiState] on purpose: the login lives on
 * its own screen ([MigrationTidalScreen]) and must not be wiped by [reset]/[cancelPending], which only
 * touch the import state machine. Once a Tidal playlist is prepared it flows into the SAME CONFIRM ->
 * RUNNING -> DONE machinery as the file/Deezer paths via [uiState].
 */
data class TidalAuthUiState(
    val authenticated: Boolean = false,
    val loggingIn: Boolean = false,
    val collection: List<SourcePlaylist> = emptyList(),
    val collectionLoading: Boolean = false,
    val error: String? = null,
    /** Set when [beginTidalLogin] has built the authorize URL off-Main; the screen opens it then clears it. */
    val pendingAuthUrl: String? = null,
)

@HiltViewModel
class MigrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: MigrationEngine,
    private val deezerSource: DeezerSource,
    private val tidalSource: TidalSource,
    private val tidalTokenStore: TidalTokenStore,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MigrationUiState())
    val uiState: StateFlow<MigrationUiState> = _uiState.asStateFlow()

    private val _tidalState = MutableStateFlow(TidalAuthUiState())
    val tidalState: StateFlow<TidalAuthUiState> = _tidalState.asStateFlow()

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

    // ── TIDAL: AUTH ──────────────────────────────────────────────────────

    /** Re-derives Tidal login from the encrypted token vault, and lazily loads the collection once. */
    fun refreshTidalAuth() {
        // Off Main: isAuthenticated lazily builds EncryptedSharedPreferences (Keystore + disk) on first
        // touch — jank if done on the UI thread at screen entry (thermal/battery audit discipline).
        viewModelScope.launch {
            val authed = withContext(Dispatchers.IO) { tidalTokenStore.isAuthenticated }
            _tidalState.value = _tidalState.value.copy(authenticated = authed)
            if (authed && _tidalState.value.collection.isEmpty() && !_tidalState.value.collectionLoading) {
                loadTidalCollection()
            }
        }
    }

    /**
     * Step 1 of PKCE: build the authorize URL (verifier persisted by the store) and post it to
     * [TidalAuthUiState.pendingAuthUrl] for the UI to open in a Custom Tab. Runs OFF Main — beginAuth()
     * reads the client id from DataStore (a runBlocking read) and lazily inits EncryptedSharedPreferences,
     * neither of which may block the UI thread on the login tap.
     * Deliberately does NOT flip loggingIn: the user is about to leave for the browser, and if they cancel
     * there WITHOUT a redirect no callback fires — a flag set now would strand the button disabled forever.
     * loggingIn is scoped to the code exchange in [completeTidalLogin].
     */
    fun beginTidalLogin() {
        viewModelScope.launch {
            _tidalState.value = _tidalState.value.copy(error = null, pendingAuthUrl = null)
            val url = withContext(Dispatchers.IO) {
                runCatching { tidalTokenStore.beginAuth() }.getOrNull()
            }
            _tidalState.value = if (url != null) {
                _tidalState.value.copy(pendingAuthUrl = url)
            } else {
                _tidalState.value.copy(
                    error = "No pude iniciar el acceso a Tidal. Revisa que el client id sea correcto.",
                )
            }
        }
    }

    /** The UI opened the pending auth URL — clear it so it isn't re-opened on recomposition. */
    fun consumeTidalAuthUrl() {
        _tidalState.value = _tidalState.value.copy(pendingAuthUrl = null)
    }

    /** The redirect came back with ?error= (typically the user cancelled the consent screen). */
    fun tidalLoginFailed(error: String) {
        _tidalState.value = _tidalState.value.copy(
            loggingIn = false,
            error = if (error == "access_denied") "Cancelaste el acceso a Tidal."
            else "Tidal rechazó el acceso ($error).",
        )
    }

    /**
     * Step 3 of PKCE: redeem the authorization code delivered via echomusic://tidal-callback.
     *
     * `state` is intentionally NOT validated: [com.aura.migration.source.tidal.TidalAuth.buildAuthRequest]
     * emits no `state` param, so there is nothing to compare against — the PKCE code_verifier (persisted in
     * [TidalTokenStore]) already binds this exchange to the request we started. Acceptable for a private
     * beta; if a `state` is ever added to the authorize URL, validate it here.
     */
    fun completeTidalLogin(code: String, state: String?) {
        _tidalState.value = _tidalState.value.copy(loggingIn = true, error = null)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { tidalTokenStore.exchangeCode(code) }.getOrDefault(false)
            }
            if (ok) {
                _tidalState.value = _tidalState.value.copy(authenticated = true, loggingIn = false, error = null)
                loadTidalCollection()
            } else {
                _tidalState.value = _tidalState.value.copy(
                    authenticated = false,
                    loggingIn = false,
                    error = "No pude completar el acceso a Tidal. Inténtalo de nuevo.",
                )
            }
        }
    }

    /** Loads the signed-in user's Tidal playlist collection (nice-to-have; the URL field is the fallback). */
    fun loadTidalCollection() {
        _tidalState.value = _tidalState.value.copy(collectionLoading = true, error = null)
        viewModelScope.launch {
            try {
                val lists = withContext(Dispatchers.IO) { tidalSource.listPlaylists(null) }
                _tidalState.value = _tidalState.value.copy(collection = lists, collectionLoading = false)
            } catch (e: SourceError.NotAuthenticated) {
                _tidalState.value = _tidalState.value.copy(
                    authenticated = false,
                    collectionLoading = false,
                    error = "Tu sesión de Tidal caducó. Vuelve a iniciar sesión.",
                )
            } catch (e: Exception) {
                _tidalState.value = _tidalState.value.copy(
                    collectionLoading = false,
                    error = "No pude cargar tus listas de Tidal. Puedes pegar el enlace de una lista abajo.",
                )
            }
        }
    }

    fun logoutTidal() {
        tidalTokenStore.logout()
        _tidalState.value = TidalAuthUiState()
    }

    fun dismissTidalError() {
        _tidalState.value = _tidalState.value.copy(error = null)
    }

    // ── TIDAL: IMPORT ────────────────────────────────────────────────────

    /** Prepare an import from a pasted Tidal playlist URL. On success -> CONFIRM (shared machinery). */
    fun prepareTidalImport(input: String) {
        viewModelScope.launch {
            try {
                _tidalState.value = _tidalState.value.copy(error = null)
                if (!tidalSource.accepts(input)) {
                    tidalError("Pega el enlace de una lista de Tidal (tidal.com/playlist/…).")
                    return@launch
                }
                val meta = withContext(Dispatchers.IO) { tidalSource.listPlaylists(input) }.firstOrNull()
                    ?: run { tidalError("No pude leer esa lista de Tidal."); return@launch }
                beginTidalConfirm(meta)
            } catch (e: SourceError.NotAuthenticated) {
                _tidalState.value = _tidalState.value.copy(
                    authenticated = false,
                    error = "Tu sesión de Tidal caducó. Vuelve a iniciar sesión.",
                )
            } catch (e: SourceError) {
                tidalError(e.message ?: "No pude leer la lista de Tidal.")
            } catch (e: Exception) {
                tidalError(e.message ?: "Enlace de Tidal no válido.")
            }
        }
    }

    /** Prepare an import from a collection playlist the user tapped. On success -> CONFIRM. */
    fun prepareTidalPlaylist(playlist: SourcePlaylist) = beginTidalConfirm(playlist)

    private fun beginTidalConfirm(meta: SourcePlaylist) {
        val name = meta.name.ifBlank { "Lista de Tidal" }
        prepared = Prepared(source = tidalSource, playlistId = meta.id, name = name, count = meta.trackCount)
        _tidalState.value = _tidalState.value.copy(error = null)
        toConfirm(name, meta.trackCount)
    }

    private fun tidalError(message: String) {
        _tidalState.value = _tidalState.value.copy(error = message)
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
