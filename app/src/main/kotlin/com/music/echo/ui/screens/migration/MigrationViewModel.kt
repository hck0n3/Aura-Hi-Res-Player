package iad1tya.echo.music.ui.screens.migration

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.migration.MigrationEngine
import com.aura.migration.model.CollectionKind
import com.aura.migration.model.ImportReport
import com.aura.migration.model.SourceArtist
import com.aura.migration.model.SourcePlaylist
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import com.aura.migration.source.PlaylistSource
import com.aura.migration.source.SourceError
import com.aura.migration.source.deezer.DeezerSource
import com.aura.migration.source.file.FileSource
import com.aura.migration.source.tidal.TidalSource
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.DataSaverEnabledKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.migration.InMemoryPlaylistSource
import iad1tya.echo.music.migration.TidalTokenStore
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.spotifyimport.ArtistNameMatching
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

enum class MigrationPhase { PICK, COLLECTION, CONFIRM, RUNNING, DONE, ERROR }

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
    // COLLECTION — a source that exposes MANY collections (Deezer profile) instead of a single list.
    val collection: List<SourcePlaylist> = emptyList(),
    val collectionLoading: Boolean = false,
    // CONFIRM
    val pendingName: String = "",
    val pendingCount: Int = 0,
    /** What the pending import IS, which decides where it lands (playlist / likes / library). */
    val pendingKind: CollectionKind = CollectionKind.PLAYLIST,
    // RUNNING
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val progressCurrent: String = "",
    // DONE
    val playlistName: String = "",
    val matchedCount: Int = 0,
    val notFoundCount: Int = 0,
    val artistsAdded: Int = 0,
    /** The kind of the FINISHED import, so the result screen reports the right destination. */
    val resultKind: CollectionKind = CollectionKind.PLAYLIST,
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

    /** The running "append resolved ambiguous" job. Cleared by [reset] so it can't write stale ids into a
     *  fresh import; the work itself is serialised by [appendMutex] rather than cancelled (cancelling
     *  mid-append would leave a half-filled remote playlist). */
    private var appendJob: Job? = null

    /** Serialises applyResolved: a double tap waits instead of creating a second playlist / appending twice. */
    private val appendMutex = kotlinx.coroutines.sync.Mutex()

    /** The prepared, ready-to-run import (set in CONFIRM, consumed by [confirmAndStart]). */
    private data class Prepared(
        val source: PlaylistSource,
        val playlistId: String,
        val name: String,
        val count: Int,
        /**
         * What this collection IS on the source platform — the ONLY thing that decides the destination
         * in Aura. PLAYLIST keeps the original "create a playlist in YouTube Music" behaviour; the
         * library kinds bypass playlist creation entirely (see [confirmAndStart] / [onFinished]).
         */
        val kind: CollectionKind = CollectionKind.PLAYLIST,
    )

    private var prepared: Prepared? = null

    /** The source that produced [MigrationUiState.collection], so a tapped row knows who to fetch from. */
    private var collectionSource: PlaylistSource? = null

    /**
     * Display name of the source of the running/finished import (e.g. "Tidal"). Kept so the ambiguous-
     * resolve path can build the same "Importada desde X" description if it has to create the YTM
     * playlist lazily (0 auto-matches case) — [prepared] may be a stale/other import by then.
     */
    private var sourceDisplayName: String = ""

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
                toConfirm(name, tracks.size, CollectionKind.PLAYLIST)
            } catch (e: SourceError) {
                fail(e.message ?: "Formato de archivo no soportado.")
            } catch (e: Exception) {
                fail(e.message ?: "No pude procesar el archivo.")
            }
        }
    }

    // ── DEEZER ───────────────────────────────────────────────────────────

    /**
     * Two shapes of input, decided by [DeezerSource.isProfile]:
     *  - a PLAYLIST url / bare id -> the original single-playlist flow, byte-for-byte unchanged.
     *  - a PROFILE url -> the user's whole public library: favourites, saved albums, followed artists
     *    and their playlists, listed for selection (phase COLLECTION), mirroring the Tidal list.
     *
     * The branch is mandatory, not cosmetic: [DeezerSource.parseId] THROWS on a profile url, so without
     * it a pasted profile link only ever produced "URL de Deezer no válida".
     */
    fun prepareDeezerImport(input: String) {
        viewModelScope.launch {
            try {
                if (deezerSource.isProfile(input)) {
                    loadDeezerProfile(input)
                    return@launch
                }
                val id = deezerSource.parseId(input)
                val meta = withContext(Dispatchers.IO) { deezerSource.listPlaylists(input) }.firstOrNull()
                val name = meta?.name?.takeIf { it.isNotBlank() } ?: "Playlist de Deezer"
                val count = meta?.trackCount ?: 0
                prepared = Prepared(source = deezerSource, playlistId = id, name = name, count = count)
                toConfirm(name, count, CollectionKind.PLAYLIST)
            } catch (e: SourceError) {
                fail(e.message ?: "No pude leer la playlist de Deezer.")
            } catch (e: Exception) {
                fail(e.message ?: "URL de Deezer no válida.")
            }
        }
    }

    /**
     * Lists everything a PUBLIC Deezer profile exposes. Dispatchers.IO is load-bearing here for the same
     * reason as [confirmAndStart]: DeezerSource issues SYNCHRONOUS OkHttp calls, and running them on Main
     * throws NetworkOnMainThreadException, which used to be swallowed into a generic failure.
     */
    private suspend fun loadDeezerProfile(input: String) {
        _uiState.value = _uiState.value.copy(
            phase = MigrationPhase.COLLECTION,
            collection = emptyList(),
            collectionLoading = true,
            errorMessage = null,
        )
        try {
            val lists = withContext(Dispatchers.IO) { deezerSource.listPlaylists(input) }
            Timber.tag("Migration").i("Deezer profile loaded: %d collections", lists.size)
            collectionSource = deezerSource
            _uiState.value = _uiState.value.copy(collection = lists, collectionLoading = false)
        } catch (e: SourceError.PrivatePlaylist) {
            _uiState.value = _uiState.value.copy(collectionLoading = false)
            fail(context.getString(R.string.migrate_error_deezer_profile))
        } catch (e: Exception) {
            Timber.tag("Migration").w(e, "Deezer profile load failed")
            _uiState.value = _uiState.value.copy(collectionLoading = false)
            fail(e.message ?: context.getString(R.string.migrate_error_deezer_profile))
        }
    }

    /** The user tapped one of the collections listed in phase COLLECTION. -> CONFIRM. */
    fun prepareCollectionItem(item: SourcePlaylist) {
        val source = collectionSource ?: return
        val name = item.name.ifBlank { "Importación" }
        prepared = Prepared(
            source = source,
            playlistId = item.id,
            name = name,
            count = item.trackCount,
            kind = item.kind,
        )
        toConfirm(name, item.trackCount, item.kind)
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
                runCatching { tidalTokenStore.exchangeCode(code) }
                    .onFailure { Timber.tag("Migration").w(it, "Tidal exchangeCode threw") }
                    .getOrDefault(false)
            }
            Timber.tag("Migration").i("Tidal token exchange ok=%s", ok)
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
                // Log the count so a 200-but-empty (wrong `me` path / JSON shape) is distinguishable from
                // a real 403 in the shareable logs — the collection path used to be a total blind spot.
                Timber.tag("Migration").i("Tidal collection loaded: %d playlists", lists.size)
                _tidalState.value = _tidalState.value.copy(collection = lists, collectionLoading = false)
            } catch (e: SourceError.NotAuthenticated) {
                Timber.tag("Migration").w("Tidal collection: not authenticated")
                _tidalState.value = _tidalState.value.copy(
                    authenticated = false,
                    collectionLoading = false,
                    error = "Tu sesión de Tidal caducó. Vuelve a iniciar sesión.",
                )
            } catch (e: Exception) {
                // The REAL cause (403 = scopes not granted, 404 = wrong endpoint, parse error) — without
                // this line the user only ever saw a blank list and we were guessing.
                Timber.tag("Migration").w(e, "Tidal collection load failed")
                _tidalState.value = _tidalState.value.copy(
                    collectionLoading = false,
                    error = "No pude cargar tus listas de Tidal (${e.message ?: "error"}). Puedes pegar el enlace de una lista abajo.",
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
        // meta.kind carries whether this is a real playlist or one of the synthetic LIBRARY collections
        // (favourites / saved albums / followed artists) that TidalSource now surfaces.
        prepared = Prepared(
            source = tidalSource,
            playlistId = meta.id,
            name = name,
            count = meta.trackCount,
            kind = meta.kind,
        )
        _tidalState.value = _tidalState.value.copy(error = null)
        toConfirm(name, meta.trackCount, meta.kind)
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
        sourceDisplayName = p.source.displayName
        _uiState.value = _uiState.value.copy(
            phase = MigrationPhase.RUNNING,
            progressDone = 0,
            progressTotal = p.count,
            progressCurrent = "",
            errorMessage = null,
        )
        migrationJob?.cancel()
        // Followed artists carry NO tracks, so there is nothing for the resolver to do: they never go
        // through the engine at all (see [runArtistImport]).
        migrationJob = if (p.kind == CollectionKind.FOLLOWED_ARTISTS) {
            viewModelScope.launch(Dispatchers.IO) { runArtistImport(p) }
        } else {
            viewModelScope.launch(Dispatchers.IO) { runTrackImport(p) }
        }
    }

    /**
     * The resolve-every-track path, shared by playlists AND by the two track-carrying library kinds.
     *
     * The ONLY difference is `createInYtm`: a real playlist is mirrored into a NEW YouTube Music playlist
     * exactly as before, while favourites/saved albums must NOT create one — their songs belong in "Me
     * gusta" / the Library, so the engine just resolves and hands back the videoIds ([onFinished] writes).
     *
     * Dispatchers.IO (set by the caller) is LOAD-BEARING, not decoration: MigrationEngine.import is a plain
     * flow{} with no flowOn, so its body runs on the collector's dispatcher. On viewModelScope (Main) the
     * very first act — source.fetchTracks() — is a SYNCHRONOUS OkHttp call for Deezer =>
     * NetworkOnMainThreadException (swallowed into Progress.Failed => "la migración falló" on EVERY Deezer
     * import), and createPlaylist's runBlocking would ANR on slow networks for File imports too. The
     * UI-state writes below are cheap value copies to a StateFlow (thread-safe), read back on Main.
     */
    private suspend fun runTrackImport(p: Prepared) {
        engine.import(
            p.source,
            p.playlistId,
            playlistName = p.name,
            createInYtm = p.kind == CollectionKind.PLAYLIST,
        ).collect { progress ->
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

                is MigrationEngine.Progress.Finished ->
                    onFinished(progress.report, progress.ytmPlaylistId, p.kind)

                is MigrationEngine.Progress.Failed ->
                    fail(progress.error.message ?: "La migración falló.")
            }
        }
    }

    /**
     * Persists the local mirror row for a freshly-created YTM playlist and returns its local id.
     * bookmarkedAt is MANDATORY — without it the playlist is invisible in Library (documented landmine).
     * browseId = the YTM id so Aura's sync keeps it up to date. The insert is awaited (not fire-and-forget)
     * so the row exists BEFORE any syncPlaylist writes PlaylistSongMap rows against it — same
     * insert-parent-first order as the Spotify import. Shared by [onFinished] and [applyResolved].
     */
    private suspend fun createLocalMirror(ytmPlaylistId: String, name: String): String {
        val localId = PlaylistEntity.generatePlaylistId()
        val entity = PlaylistEntity(
            id = localId,
            name = name,
            browseId = ytmPlaylistId,
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
        )
        database.withTransaction { insert(entity) }
        return localId
    }

    private suspend fun onFinished(report: ImportReport, ytmPlaylistId: String?, kind: CollectionKind) {
        var localId: String? = null
        when (kind) {
            // Unchanged: the playlist path the owner already uses.
            CollectionKind.PLAYLIST -> {
                if (ytmPlaylistId != null) {
                    localId = createLocalMirror(ytmPlaylistId, report.playlistName)
                    // Pull the freshly-created YTM playlist's songs into the local row so it shows populated.
                    syncUtils.syncPlaylist(ytmPlaylistId, localId)
                }
            }
            // Favourites become LIKED songs, saved albums land in the LIBRARY. Neither creates a playlist.
            CollectionKind.FAVORITE_TRACKS ->
                persistResolvedSongs(report.matched.map { it.source to it.videoId }, markLiked = true)

            CollectionKind.SAVED_ALBUMS ->
                persistResolvedSongs(report.matched.map { it.source to it.videoId }, markLiked = false)

            // Never reaches the engine; kept exhaustive so a new kind can't silently fall through.
            CollectionKind.FOLLOWED_ARTISTS -> Unit
        }

        _uiState.value = _uiState.value.copy(
            phase = MigrationPhase.DONE,
            playlistName = report.playlistName,
            matchedCount = report.matched.size,
            notFoundCount = report.notFound.size,
            resultKind = kind,
            ytmPlaylistId = ytmPlaylistId,
            localPlaylistId = localId,
            ambiguous = report.ambiguous.map { AmbiguousItem(it.source, it.candidates) },
        )
    }

    // ── DESTINO: CANCIONES (ME GUSTA / BIBLIOTECA) ───────────────────────

    /**
     * Writes freshly-resolved videoIds into the local database as LIKED songs (`markLiked = true`) or as
     * LIBRARY songs (`markLiked = false`). This is the whole point of the non-playlist kinds.
     *
     * Three landmines this deliberately avoids, all of them documented project rules:
     *  1. A resolved videoId may have NO row at all. `update()` on a missing row is a SILENT no-op, so a
     *     brand-new song is INSERTED first (via the DAO's `insert(MediaMetadata)`, which also creates the
     *     artist rows + song↔artist maps) with the flags already applied — the same shape the Spotify
     *     import uses in mirrorPlaylist. Existing rows are written with **upsert**, never `update`.
     *  2. `inLibrary` is what every Library song query filters on (`WHERE inLibrary IS NOT NULL`); a row
     *     written without it is real but INVISIBLE. It is always stamped, for likes too (liking a song in
     *     this app also puts it in the library — see SongEntity.toggleLike).
     *  3. Existing rows keep their original likedDate/inLibrary: re-importing must never re-stamp and
     *     reshuffle the user's ordering.
     *
     * Metadata comes from ONE batched [YouTube.queue] per 50 ids (same machinery as JrPlaylistImporter)
     * so imported songs carry real titles/artists/artwork; if that lookup fails the SourceTrack itself is
     * the fallback, because a like that lands with a plain title beats a like that never lands.
     *
     * Caller must already be on Dispatchers.IO: this is network + disk from top to bottom.
     */
    private suspend fun persistResolvedSongs(
        resolved: List<Pair<SourceTrack, String>>,
        markLiked: Boolean,
    ): Int {
        if (resolved.isEmpty()) return 0
        val ordered = resolved
            .sortedBy { it.first.sourcePosition }
            .distinctBy { it.second }

        val byVideoId = HashMap<String, SongItem>()
        ordered.map { it.second }.chunked(YTM_QUEUE_CHUNK).forEach { chunk ->
            runCatching { YouTube.queue(videoIds = chunk).getOrNull() }
                .getOrNull()
                ?.forEach { song -> byVideoId[song.id] = song }
            delay(TRACK_THROTTLE_MS)
        }

        val metadata = ordered.map { (track, videoId) ->
            byVideoId[videoId]?.toMediaMetadata() ?: fallbackMetadata(track, videoId)
        }

        // One transaction per block so Room's invalidation tracker emits once per block instead of once
        // per row — the same anti-flicker batching SyncUtils uses for big libraries.
        metadata.chunked(DB_BATCH_SIZE).forEach { block ->
            database.withTransaction {
                val now = LocalDateTime.now()
                block.forEach { meta ->
                    val existing = getSongByIdBlocking(meta.id)?.song
                    if (existing == null) {
                        insert(meta) { song ->
                            song.copy(
                                inLibrary = song.inLibrary ?: now,
                                liked = song.liked || markLiked,
                                likedDate = if (markLiked) song.likedDate ?: now else song.likedDate,
                            )
                        }
                    } else if (existing.inLibrary == null || (markLiked && !existing.liked)) {
                        // UPSERT, never update: the hard project rule for like writes (an update on a row
                        // that vanished between the read and the write is a silent no-op).
                        upsert(
                            existing.copy(
                                inLibrary = existing.inLibrary ?: now,
                                liked = existing.liked || markLiked,
                                likedDate = if (markLiked) existing.likedDate ?: now else existing.likedDate,
                            ),
                        )
                    }
                }
            }
        }

        // Imported song artists also become followed, so "tus artistas" reflects the import (same tail as
        // the Spotify import). Additive and best-effort — it must never fail the import itself.
        runCatching { database.followArtistsWithContent(LocalDateTime.now()) }
        // Mirror upwards on the existing, retried, queued sync path instead of firing one raw
        // YouTube.likeVideo per song from here (that would be an unbounded burst on a 2000-song library).
        runCatching {
            if (markLiked) syncUtils.syncLikedSongs() else syncUtils.syncLibrarySongs()
        }
        Timber.tag("Migration").i(
            "Persisted %d resolved songs (liked=%s)", metadata.size, markLiked,
        )
        return metadata.size
    }

    /**
     * Minimal metadata for a videoId whose YouTube lookup failed — title/artists come from the SOURCE
     * platform, which we always have. Mirrors JrPlaylistImporter.importDirect: a thin row is filled in
     * with real artwork/album the first time the song is played.
     */
    private fun fallbackMetadata(track: SourceTrack, videoId: String) = MediaMetadata(
        id = videoId,
        title = track.title,
        artists = track.artists.filter { it.isNotBlank() }.map { MediaMetadata.Artist(id = null, name = it) },
        duration = track.durationMs?.let { (it / 1000).toInt() } ?: -1,
    )

    // ── DESTINO: ARTISTAS SEGUIDOS ───────────────────────────────────────

    /**
     * Followed artists have no tracks to resolve, so the engine is bypassed entirely: each source artist
     * is matched to a YouTube Music artist channel and BOOKMARKED locally — `bookmarkedAt` is exactly
     * what "Biblioteca -> Artistas" filters on (`WHERE bookmarkedAt IS NOT NULL`); without it the row
     * exists but is invisible. Same shape as the Spotify import's matchAndFollowArtist.
     *
     * Sequential + throttled on purpose (one search per artist): a parallel burst is the thermal/network
     * behaviour this project keeps auditing out.
     */
    private suspend fun runArtistImport(p: Prepared) {
        try {
            val artists = p.source.fetchArtists(p.playlistId)
            if (artists.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    phase = MigrationPhase.DONE,
                    playlistName = p.name,
                    matchedCount = 0,
                    notFoundCount = 0,
                    artistsAdded = 0,
                    resultKind = CollectionKind.FOLLOWED_ARTISTS,
                    ytmPlaylistId = null,
                    localPlaylistId = null,
                    ambiguous = emptyList(),
                )
                return
            }

            _uiState.value = _uiState.value.copy(
                progressTotal = artists.size,
                progressDone = 0,
                playlistName = p.name,
            )

            var added = 0
            artists.forEachIndexed { index, artist ->
                _uiState.value = _uiState.value.copy(
                    progressDone = index + 1,
                    progressTotal = artists.size,
                    progressCurrent = artist.name,
                )
                // One artist that fails to match NEVER sinks the rest — the engine's non-negotiable
                // principle, applied to this path too.
                if (runCatching { bookmarkArtist(artist) }.getOrDefault(false)) added++
                delay(TRACK_THROTTLE_MS)
            }

            // Push the new follows up to the account. syncArtistsSubscriptions() only pulls DOWN, so
            // the upload pass is what actually subscribes them — and it only touches rows carrying
            // followedByUserAt, which bookmarkArtist() above set because these came from the source
            // account's real "followed artists" list. Bounded, batched and resumable.
            runCatching { syncUtils.syncArtistsSubscriptions() }
            runCatching {
                iad1tya.echo.music.utils.YtmSyncWorker.enqueue(
                    context,
                    iad1tya.echo.music.utils.YtmSyncWorker.TYPE_UPLOAD_LIBRARY,
                )
            }

            _uiState.value = _uiState.value.copy(
                phase = MigrationPhase.DONE,
                playlistName = p.name,
                matchedCount = 0,
                notFoundCount = artists.size - added,
                artistsAdded = added,
                resultKind = CollectionKind.FOLLOWED_ARTISTS,
                ytmPlaylistId = null,
                localPlaylistId = null,
                ambiguous = emptyList(),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("Migration").w(e, "Artist import failed")
            fail(e.message ?: context.getString(R.string.migrate_error_artists))
        }
    }

    /** Matches one source artist on YouTube Music and bookmarks it. Returns true when it landed. */
    private suspend fun bookmarkArtist(artist: SourceArtist): Boolean {
        if (artist.name.isBlank()) return false
        val results = YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
            ?: return false
        val match = results.items
            .filterIsInstance<ArtistItem>()
            .firstOrNull { ArtistNameMatching.isLikelyMatch(artist.name, it.title) }
            ?: return false

        // Resolve the real channel id the same way SyncUtils / the Spotify import do: search-result
        // ArtistItems carry channelId == null and a browse id that is often not a channel id.
        val channelId = match.channelId
            ?: if (match.id.startsWith("UC")) match.id
            else runCatching { YouTube.getChannelId(match.id) }.getOrNull()?.takeIf { it.isNotEmpty() }

        val existing = database.getArtistById(match.id)
        database.withTransaction {
            val now = LocalDateTime.now()
            if (existing == null) {
                insert(
                    ArtistEntity(
                        id = match.id,
                        name = match.title,
                        thumbnailUrl = match.thumbnail ?: artist.artworkUrl,
                        channelId = channelId,
                        // MANDATORY: "Biblioteca -> Artistas" filters WHERE bookmarkedAt IS NOT NULL.
                        bookmarkedAt = now,
                        // DELIBERATE follow: this row comes from the source account's real
                        // "followed artists" list, which the user explicitly chose to import — not
                        // from followArtistsWithContent. Only these are pushed up as subscriptions.
                        followedByUserAt = now,
                    ),
                )
            } else {
                update(
                    existing.copy(
                        name = match.title,
                        thumbnailUrl = match.thumbnail ?: existing.thumbnailUrl,
                        channelId = channelId ?: existing.channelId,
                        // Never re-stamp: keep the date the user originally followed them.
                        bookmarkedAt = existing.bookmarkedAt ?: now,
                        followedByUserAt = existing.followedByUserAt ?: now,
                        // A fresh follow supersedes any queued unsubscribe for this artist.
                        unfollowedByUserAt = null,
                        lastUpdateTime = now,
                    ),
                )
            }
        }
        return true
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

    /** Persists the user's picks and appends them to the YTM playlist, then re-syncs. */
    fun applyResolved() {
        val state = _uiState.value
        val chosen = state.ambiguous.filter { it.chosenVideoId != null }
        if (chosen.isEmpty()) {
            // Nothing to add (only skips): just drop the resolved items and stay on the result screen.
            // Never create an empty playlist for a user who resolved nothing.
            _uiState.value = state.copy(ambiguous = state.ambiguous.filterNot { it.resolved })
            return
        }
        _uiState.value = state.copy(appendingResolved = true)
        // Dispatchers.IO is LOAD-BEARING here, same as confirmAndStart: engine.createPlaylist reaches
        // YouTube.createPlaylist, which is NOT suspend — it wraps runBlocking — so on the Main dispatcher
        // this freezes the UI for a full InnerTube round trip and ANRs on a slow connection.
        // appendResolved/syncPlaylist/DB writes below are network+disk too.
        // A MUTEX, not cancel-on-reentry: engine.createPlaylist wraps runBlocking (not interruptible) and
        // appendResolved adds ONE video per request with delays between them, so cancelling mid-flight
        // would leave a HALF-appended playlist, skip the local sync, and surface "Job was cancelled" as a
        // migration error. Serialising instead makes a double tap a harmless no-op wait.
        appendJob = viewModelScope.launch(Dispatchers.IO) {
            appendMutex.withLock {
                // NonCancellable: once we start creating/appending remote state, finishing is safer than
                // aborting halfway (a cancel here is unobservable to the user but leaves YTM inconsistent).
                withContext(NonCancellable) {
                    try {
                        chosen.forEach { engine.confirmMatch(it.track, it.chosenVideoId!!) }

                        // A LIBRARY import has no playlist to append to: the reviewed songs go exactly
                        // where the auto-matched ones went (liked / library). Creating a playlist here
                        // would be the very bug this change fixes, just deferred to the review screen.
                        if (state.resultKind != CollectionKind.PLAYLIST) {
                            persistResolvedSongs(
                                chosen.map { it.track to it.chosenVideoId!! },
                                markLiked = state.resultKind == CollectionKind.FAVORITE_TRACKS,
                            )
                            _uiState.value = _uiState.value.copy(
                                appendingResolved = false,
                                matchedCount = _uiState.value.matchedCount + chosen.size,
                                ambiguous = _uiState.value.ambiguous.filterNot { it.resolved },
                            )
                            return@withContext
                        }

                        // 0 auto-matches means engine.import (matched.isEmpty()) created NO playlist — by
                        // design, to never leave an empty list behind. Now that the user has resolved at
                        // least one ambiguous track we have something to add, so create the YTM playlist +
                        // local mirror row ON DEMAND. This turns the old dead-end ("no hay dónde añadir las
                        // revisadas") into a working migration when every track needed review.
                        var ytmId = _uiState.value.ytmPlaylistId
                        var localId = _uiState.value.localPlaylistId
                        // TWO INDEPENDENT guards. With a single `if (ytmId == null)` around both, a
                        // createLocalMirror failure (Room/disk) left ytmId committed but localId null, and
                        // the retry skipped the whole block — the playlist then existed on YouTube but NO
                        // bookmarked row was ever written, so it stayed invisible in Library forever.
                        if (ytmId == null) {
                            val name = state.playlistName.ifBlank { "Playlist importada" }
                            val description = "Importada desde ${sourceDisplayName.ifBlank { "otra plataforma" }}"
                            ytmId = engine.createPlaylist(name, description)
                            // Commit the REMOTE id immediately: if the mirror below throws, a retry must
                            // reuse this playlist instead of creating a SECOND one and orphaning it.
                            _uiState.value = _uiState.value.copy(ytmPlaylistId = ytmId)
                        }
                        if (localId == null) {
                            val name = state.playlistName.ifBlank { "Playlist importada" }
                            localId = createLocalMirror(ytmId, name)
                            _uiState.value = _uiState.value.copy(localPlaylistId = localId)
                        }
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
        }
    }

    // ── NAV / STATE HELPERS ──────────────────────────────────────────────

    fun reset() {
        prepared = null
        collectionSource = null
        // Cancel BOTH jobs: an in-flight import or append that outlives reset() writes the OLD import's
        // ids/counts into the FRESH state — a stale ytmPlaylistId then makes the next applyResolved append
        // the new import's tracks into the PREVIOUS playlist (cross-import corruption), and a stale
        // matchedCount/ambiguous filter silently drops the new import's resolved rows.
        // The append body runs under NonCancellable, so an already-started remote append still finishes
        // consistently; what this cancels is the job wrapper (and any append still waiting on the mutex).
        migrationJob?.cancel()
        migrationJob = null
        appendJob?.cancel()
        appendJob = null
        _uiState.value = MigrationUiState(
            signedInYouTube = _uiState.value.signedInYouTube,
            dataSaver = _uiState.value.dataSaver,
        )
    }

    fun cancelPending() {
        prepared = null
        // Back to the collection list when there is one (a Deezer profile) instead of throwing the user
        // all the way out to the picker and making them paste the URL again.
        val hasCollection = _uiState.value.collection.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            phase = if (hasCollection) MigrationPhase.COLLECTION else MigrationPhase.PICK,
            errorMessage = null,
        )
    }

    /** Leaves the collection list (Deezer profile) and returns to the source picker. */
    fun cancelCollection() {
        prepared = null
        collectionSource = null
        _uiState.value = _uiState.value.copy(
            phase = MigrationPhase.PICK,
            collection = emptyList(),
            collectionLoading = false,
            errorMessage = null,
        )
    }

    fun dismissError() {
        // From an error, return to the picker so the user can retry.
        _uiState.value = _uiState.value.copy(phase = MigrationPhase.PICK, errorMessage = null)
    }

    private fun toConfirm(name: String, count: Int, kind: CollectionKind) {
        _uiState.value = _uiState.value.copy(
            phase = MigrationPhase.CONFIRM,
            pendingName = name,
            pendingCount = count,
            pendingKind = kind,
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

        /** YouTube.queue asserts a hard per-request cap; 50 is what the other importers use. */
        const val YTM_QUEUE_CHUNK = 50

        /** Rows written per transaction, so Room emits one invalidation per block, not per row. */
        const val DB_BATCH_SIZE = 300
    }
}
