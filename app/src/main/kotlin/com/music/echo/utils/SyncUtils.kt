

package iad1tya.echo.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.LastFMUseSendLikes
import iad1tya.echo.music.constants.LastFullSyncKey
import iad1tya.echo.music.constants.YtmLastSyncKey
import iad1tya.echo.music.constants.SYNC_COOLDOWN
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.extensions.collectLatest
import iad1tya.echo.music.extensions.isInternetConnected
import iad1tya.echo.music.extensions.isSyncEnabled
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncOperation {
    data object FullSync : SyncOperation()
    data object LikedSongs : SyncOperation()
    data object LibrarySongs : SyncOperation()
    data object UploadedSongs : SyncOperation()
    data object LikedAlbums : SyncOperation()
    data object UploadedAlbums : SyncOperation()
    data object ArtistsSubscriptions : SyncOperation()
    data object SavedPlaylists : SyncOperation()
    data object AutoSyncPlaylists : SyncOperation()
    data class SinglePlaylist(val browseId: String, val playlistId: String) : SyncOperation()
    data class LikeSong(val song: SongEntity) : SyncOperation()
    data object CleanupDuplicates : SyncOperation()
    data object ClearAllSynced : SyncOperation()
    data object MirrorLikedSongs : SyncOperation()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Completed : SyncStatus()
}

data class SyncState(
    val overallStatus: SyncStatus = SyncStatus.Idle,
    val likedSongs: SyncStatus = SyncStatus.Idle,
    val librarySongs: SyncStatus = SyncStatus.Idle,
    val uploadedSongs: SyncStatus = SyncStatus.Idle,
    val likedAlbums: SyncStatus = SyncStatus.Idle,
    val uploadedAlbums: SyncStatus = SyncStatus.Idle,
    val artists: SyncStatus = SyncStatus.Idle,
    val playlists: SyncStatus = SyncStatus.Idle,
    val currentOperation: String = ""
)

@Singleton
class SyncUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Timber.e(throwable, "Sync coroutine exception")
        }
    }

    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob + exceptionHandler)

    private val syncChannel = Channel<SyncOperation>(Channel.BUFFERED)
    private var processingJob: Job? = null

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var lastfmSendLikes = false

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        // How many rows we write per DB transaction during a sync. Whole blocks are written inside a
        // single withTransaction {} so Room's invalidation tracker emits ONCE per block (not once per
        // row) — this is what kills the Library "flicker" during a sync while still updating in near
        // real time (the list grows a block at a time). It also lets us drop the old per-row throttle
        // (the former DB_OPERATION_DELAY_MS) that made syncing 4000+ likes crawl.
        private const val SYNC_BATCH_SIZE = 300
        // SQLite caps the number of bound host parameters (historically 999). Chunk `id IN (...)`
        // lookups so a 4000+ liked-songs library never blows past that limit.
        private const val SQL_IN_CHUNK = 900
        // Max artist cover photos to fetch per sync run (bounded so it never hammers the network/API).
        private const val MAX_ARTIST_IMAGE_FETCH = 250
    }

    init {
        context.dataStore.data
            .map { it[LastFMUseSendLikes] ?: false }
            .distinctUntilChanged()
            .collectLatest(syncScope) {
                lastfmSendLikes = it
            }

        startProcessingQueue()
    }

    private fun startProcessingQueue() {
        processingJob = syncScope.launch {
            for (operation in syncChannel) {
                try {
                    processOperation(operation)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing sync operation: $operation")
                }
            }
        }
    }

    private suspend fun processOperation(operation: SyncOperation) {
        when (operation) {
            is SyncOperation.FullSync -> executeFullSync()
            is SyncOperation.LikedSongs -> executeSyncLikedSongs()
            is SyncOperation.LibrarySongs -> executeSyncLibrarySongs()
            is SyncOperation.UploadedSongs -> executeSyncUploadedSongs()
            is SyncOperation.LikedAlbums -> executeSyncLikedAlbums()
            is SyncOperation.UploadedAlbums -> executeSyncUploadedAlbums()
            is SyncOperation.ArtistsSubscriptions -> executeSyncArtistsSubscriptions()
            is SyncOperation.SavedPlaylists -> executeSyncSavedPlaylists()
            is SyncOperation.AutoSyncPlaylists -> executeSyncAutoSyncPlaylists()
            is SyncOperation.SinglePlaylist -> executeSyncPlaylist(operation.browseId, operation.playlistId)
            is SyncOperation.LikeSong -> executeLikeSong(operation.song)
            is SyncOperation.CleanupDuplicates -> executeCleanupDuplicatePlaylists()
            is SyncOperation.ClearAllSynced -> executeClearAllSyncedContent()
            is SyncOperation.MirrorLikedSongs -> executeMirrorLikedSongs()
        }
    }

    private suspend fun isLoggedIn(): Boolean {
        return try {
            val cookie = context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .first()
            cookie?.let { "SAPISID" in parseCookieString(it) } ?: false
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "Error checking login status")
            false
        }
    }

    private suspend fun <T> withRetry(
        maxRetries: Int = MAX_RETRIES,
        initialDelay: Long = INITIAL_RETRY_DELAY_MS,
        block: suspend () -> T
    ): Result<T> {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                return Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                Timber.w(e, "Attempt ${attempt + 1}/$maxRetries failed")
                if (attempt == maxRetries - 1) {
                    return Result.failure(e)
                }
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return Result.failure(Exception("Max retries exceeded"))
    }

    private fun updateState(update: SyncState.() -> SyncState) {
        _syncState.value = _syncState.value.update()
    }

    // ---- Batched DB helpers (speed + real-time-without-flicker) ------------------------------------
    // The sync loops used to read each row back with `database.song(id).firstOrNull()` (N+1), then write
    // each row in its OWN `database.transaction {}` with a per-row `delay(50ms)`. That made Room re-emit
    // the FULL list per row (flicker) and dragged a 4000+ liked library out for minutes. Instead we now
    // load the existing rows once, decide in memory, and write in blocks — one transaction per block.

    /**
     * Load the existing [SongEntity] rows for [ids] in ONE (chunked) query instead of N+1
     * `database.song(id).firstOrNull()` reads. Chunked to stay under SQLite's bound-parameter cap.
     */
    private suspend fun loadExistingSongs(ids: List<String>): HashMap<String, SongEntity> {
        val map = HashMap<String, SongEntity>(ids.size.coerceAtLeast(0))
        if (ids.isEmpty()) return map
        ids.distinct().chunked(SQL_IN_CHUNK).forEach { chunk ->
            database.getSongsByIds(chunk).forEach { map[it.id] = it.song }
        }
        return map
    }

    /** Same as [loadExistingSongs] but for followed/synced artists. */
    private suspend fun loadExistingArtists(ids: List<String>): HashMap<String, ArtistEntity> {
        val map = HashMap<String, ArtistEntity>(ids.size.coerceAtLeast(0))
        if (ids.isEmpty()) return map
        ids.distinct().chunked(SQL_IN_CHUNK).forEach { chunk ->
            database.getArtistEntitiesByIds(chunk).forEach { map[it.id] = it }
        }
        return map
    }

    /**
     * Insert brand-new synced songs (+ their artists/maps) in blocks of [SYNC_BATCH_SIZE], each block in
     * its own transaction so Room emits once per block. [entities] and [mediaList] are index-aligned.
     */
    private suspend fun batchInsertSongs(entities: List<SongEntity>, mediaList: List<MediaMetadata>) {
        if (entities.isEmpty()) return
        entities.indices.chunked(SYNC_BATCH_SIZE).forEach { block ->
            val songBlock = block.map { entities[it] }
            val metaBlock = block.map { mediaList[it] }
            database.withTransaction {
                insertSongsWithArtists(songBlock, metaBlock)
            }
        }
    }

    /** Update existing songs in blocks of [SYNC_BATCH_SIZE], one transaction per block. */
    private suspend fun batchUpdateSongs(entities: List<SongEntity>) {
        if (entities.isEmpty()) return
        entities.chunked(SYNC_BATCH_SIZE).forEach { block ->
            database.withTransaction {
                updateSongs(block)
            }
        }
    }

    fun performFullSync() {
        syncScope.launch {
            syncChannel.send(SyncOperation.FullSync)
        }
    }

    suspend fun performFullSyncSuspend() {
        if (!isLoggedIn()) {
            Timber.w("Skipping full sync - user not logged in")
            return
        }
        executeFullSync()
    }

    fun tryAutoSync() {
        syncScope.launch {
            if (!isLoggedIn()) {
                Timber.d("Skipping auto sync - user not logged in")
                return@launch
            }

            if (!context.isSyncEnabled() || !context.isInternetConnected()) {
                return@launch
            }

            val lastSync = context.dataStore.get(LastFullSyncKey, 0L)
            val currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            // A NEGATIVE elapsed means the wall clock was rolled back past the stored lastSync;
            // without the lower bound that would keep auto-sync blocked indefinitely (elapsed stays
            // < cooldown until the clock catches up). Treat clock drift as an expired cooldown.
            val elapsed = currentTime - lastSync
            if (lastSync > 0 && elapsed in 0 until SYNC_COOLDOWN) {
                return@launch
            }

            syncChannel.send(SyncOperation.FullSync)

            context.dataStore.edit { settings ->
                settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
        }
    }

    fun runAllSyncs() {
        performFullSync()
    }

    fun likeSong(s: SongEntity) {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikeSong(s))
        }
    }

    fun syncLikedSongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedSongs)
        }
    }

    /**
     * Manual, user-initiated "mirror favorites from my account": makes the local liked songs match
     * the YouTube account EXACTLY — adds the ones on the account and removes the local likes that are
     * no longer on it. Unlike the automatic sync (which is purely additive on purpose), this can remove
     * local likes, so it is ONLY ever run when the user explicitly taps the button and confirms.
     */
    fun mirrorLikedSongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.MirrorLikedSongs)
        }
    }

    suspend fun mirrorLikedSongsSuspend() = executeMirrorLikedSongs()

    fun syncLibrarySongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LibrarySongs)
        }
    }

    fun syncUploadedSongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.UploadedSongs)
        }
    }

    fun syncLikedAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedAlbums)
        }
    }

    fun syncUploadedAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.UploadedAlbums)
        }
    }

    fun syncArtistsSubscriptions() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ArtistsSubscriptions)
        }
    }

    fun syncSavedPlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.SavedPlaylists)
        }
    }

    fun syncAutoSyncPlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.AutoSyncPlaylists)
        }
    }

    fun syncPlaylist(browseId: String, playlistId: String) {
        syncScope.launch {
            executeSyncPlaylist(browseId, playlistId)
        }
    }

    fun syncAllAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedAlbums)
            syncChannel.send(SyncOperation.UploadedAlbums)
        }
    }

    fun syncAllArtists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ArtistsSubscriptions)
        }
    }

    fun cleanupDuplicatePlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.CleanupDuplicates)
        }
    }

    fun clearAllSyncedContent() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ClearAllSynced)
        }
    }

    

    suspend fun syncLikedSongsSuspend() = executeSyncLikedSongs()
    suspend fun syncLibrarySongsSuspend() = executeSyncLibrarySongs()
    suspend fun syncUploadedSongsSuspend() = executeSyncUploadedSongs()
    suspend fun syncLikedAlbumsSuspend() = executeSyncLikedAlbums()
    suspend fun syncUploadedAlbumsSuspend() = executeSyncUploadedAlbums()
    suspend fun syncArtistsSubscriptionsSuspend() = executeSyncArtistsSubscriptions()
    suspend fun syncSavedPlaylistsSuspend() = executeSyncSavedPlaylists()
    suspend fun syncAutoSyncPlaylistsSuspend() = executeSyncAutoSyncPlaylists()
    suspend fun cleanupDuplicatePlaylistsSuspend() = executeCleanupDuplicatePlaylists()
    suspend fun clearAllSyncedContentSuspend() = executeClearAllSyncedContent()

    suspend fun syncAllAlbumsSuspend() {
        executeSyncLikedAlbums()
        executeSyncUploadedAlbums()
    }

    suspend fun syncAllArtistsSuspend() {
        executeSyncArtistsSubscriptions()
    }

    

    private suspend fun executeFullSync() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping full sync - user not logged in")
            return@withContext
        }

        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Starting full sync") }

        try {
            
            executeSyncLikedSongs()

            executeSyncLibrarySongs()

            // PLAYLISTS moved up — BEFORE the expensive album re-fetch + artist-image steps. They used to
            // run LAST, and on real-sized accounts the album/artist loops burned the whole ~10-min worker
            // budget → the worker was stopped and retried from the top → saved playlists were NEVER reached
            // ("no me pone nunca las playlists"). Running them early guarantees they complete.
            executeSyncSavedPlaylists()

            executeSyncAutoSyncPlaylists()

            executeSyncUploadedSongs()

            executeSyncLikedAlbums()

            executeSyncUploadedAlbums()

            executeSyncArtistsSubscriptions()

            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
            // C3: stamp the REAL completion time (single source of truth for "last synced X ago"). Reached only
            // after a genuine full sync ran — we returned early above when not logged in — so the sync screen
            // shows a truthful timestamp instead of a placebo. Both the manual "sync all" and the scheduled
            // worker funnel through here.
            runCatching { context.dataStore.edit { it[YtmLastSyncKey] = System.currentTimeMillis() } }
            Timber.d("Full sync completed successfully")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "Error during full sync")
            updateState { copy(overallStatus = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    private suspend fun executeLikeSong(s: SongEntity) = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping likeSong - user not logged in")
            return@withContext
        }

        withRetry {
            YouTube.likeVideo(s.id, s.liked)
        }.onFailure { e ->
            Timber.e(e, "Failed to like song on YouTube: ${s.id}")
        }


    }

    private suspend fun executeSyncLikedSongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLikedSongs - user not logged in")
            return@withContext
        }

        updateState { copy(likedSongs = SyncStatus.Syncing, currentOperation = "Syncing liked songs") }

        withRetry {
            YouTube.playlist("LM").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.songs
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.likedSongsByNameAsc().first()

                    localSongs.filterNot { it.id in remoteIds || it.song.isLocal }.forEach { song ->
                        try {
                            // Push local likes up to the account; NEVER un-like locally here. Removing a
                            // local like that hadn't synced yet is what made the heart "sometimes not work"
                            // (the like would silently revert on the next sync).
                            withRetry {
                                YouTube.likeVideo(song.id, true)
                            }.onFailure { e ->
                                Timber.e(e, "Failed to like song on YouTube: ${song.id}")
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to update song: ${song.id}")
                        }
                    }

                    // C4 — DIFFERENTIAL + BATCHED: decide new/changed songs in memory against a single
                    // pre-loaded snapshot (no N+1 `song(id)` reads), then write in blocks of one
                    // transaction each (Room emits per block, not per row — no Library flicker, no per-row
                    // throttle). New likes are stamped to preserve the remote order (newest first); KNOWN
                    // songs keep their ORIGINAL likedDate (never rewritten on every sync → preserves local
                    // order and avoids accidental re-uploads).
                    val now = LocalDateTime.now()
                    val existing = loadExistingSongs(remoteSongs.map { it.id })
                    val insertEntities = ArrayList<SongEntity>()
                    val insertMeta = ArrayList<MediaMetadata>()
                    val updateEntities = ArrayList<SongEntity>()
                    remoteSongs.forEachIndexed { index, song ->
                        try {
                            val dbSong = existing[song.id]
                            val isVideoSong = song.isVideoSong
                            if (dbSong == null) {
                                val meta = song.toMediaMetadata()
                                insertMeta.add(meta)
                                insertEntities.add(
                                    meta.toSongEntity().copy(
                                        liked = true,
                                        likedDate = now.minusSeconds(index.toLong()),
                                        isVideo = isVideoSong,
                                    )
                                )
                            } else if (!dbSong.liked || dbSong.isVideo != isVideoSong) {
                                // Keep the original likedDate — never overwrite it with the sync timestamp.
                                updateEntities.add(dbSong.copy(liked = true, isVideo = isVideoSong))
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process song: ${song.id}")
                        }
                    }
                    val newCount = insertEntities.size
                    val updatedCount = updateEntities.size
                    batchInsertSongs(insertEntities, insertMeta)
                    batchUpdateSongs(updateEntities)
                    Timber.d(
                        "Liked songs sync: ${remoteSongs.size} remote — $newCount new, $updatedCount updated, " +
                            "${(remoteSongs.size - newCount - updatedCount).coerceAtLeast(0)} unchanged (skipped)",
                    )

                    // Imported song artists also become followed, so "your artists" fills up like Spotify.
                    runCatching { database.followArtistsWithContent(LocalDateTime.now()) }
                    updateState { copy(likedSongs = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteSongs.size} liked songs")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing liked songs")
                    updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch liked songs from YouTube")
                updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync liked songs after retries")
            updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeMirrorLikedSongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping mirrorLikedSongs - user not logged in")
            return@withContext
        }

        updateState { copy(likedSongs = SyncStatus.Syncing, currentOperation = "Mirroring liked songs from account") }

        withRetry {
            YouTube.playlist("LM").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.songs
                    val remoteIds = remoteSongs.map { it.id }.toSet()

                    // SAFETY GUARD: never wipe the local favorites if the account came back empty (a
                    // failed/anomalous fetch). The canonical "LM" liked playlist via .completed() is the
                    // reliable source for likes (unlike the capped library views), but an empty result is
                    // always treated as an error, not "you unliked everything".
                    if (remoteIds.isEmpty()) {
                        Timber.w("mirrorLikedSongs: remote returned empty; aborting to avoid wiping favorites")
                        updateState { copy(likedSongs = SyncStatus.Error("Empty remote — aborted to protect favorites")) }
                        return@onSuccess
                    }

                    // 1) Additive: make sure everything on the account is liked locally. Decided in memory
                    // against one pre-loaded snapshot (no N+1) and written in batched transactions.
                    val now = LocalDateTime.now()
                    val existing = loadExistingSongs(remoteSongs.map { it.id })
                    val insertEntities = ArrayList<SongEntity>()
                    val insertMeta = ArrayList<MediaMetadata>()
                    val updateEntities = ArrayList<SongEntity>()
                    remoteSongs.forEachIndexed { index, song ->
                        try {
                            val dbSong = existing[song.id]
                            val timestamp = now.minusSeconds(index.toLong())
                            val isVideoSong = song.isVideoSong
                            if (dbSong == null) {
                                val meta = song.toMediaMetadata()
                                insertMeta.add(meta)
                                insertEntities.add(
                                    meta.toSongEntity().copy(liked = true, likedDate = timestamp, isVideo = isVideoSong)
                                )
                            } else if (!dbSong.liked) {
                                updateEntities.add(dbSong.copy(liked = true, likedDate = timestamp, isVideo = isVideoSong))
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "mirrorLikedSongs: failed to add song ${song.id}")
                        }
                    }
                    batchInsertSongs(insertEntities, insertMeta)
                    batchUpdateSongs(updateEntities)

                    // 2) Mirror: remove local likes that are no longer on the account. Local files are left
                    // alone. Done locally only — the account is the source of truth, so there's nothing to
                    // push up (the song is already not liked on the account).
                    val localLiked = database.likedSongsByNameAsc().first()
                    val toUnlike = localLiked
                        .filterNot { it.id in remoteIds || it.song.isLocal }
                        .map { it.song.copy(liked = false, likedDate = null) }
                    batchUpdateSongs(toUnlike)

                    updateState { copy(likedSongs = SyncStatus.Completed) }
                    Timber.d("mirrorLikedSongs: mirrored ${remoteSongs.size} liked songs from account")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error mirroring liked songs")
                    updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch liked songs from YouTube (mirror)")
                updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to mirror liked songs after retries")
            updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncLibrarySongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLibrarySongs - user not logged in")
            return@withContext
        }

        updateState { copy(librarySongs = SyncStatus.Syncing, currentOperation = "Syncing library songs") }

        withRetry {
            YouTube.library("FEmusic_liked_videos").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.songsByNameAsc().first()

                    // Additive only: NEVER drop a song from the local library just because it isn't in
                    // this remote "liked videos" page. That page is paginated/capped and routinely returns
                    // fewer items than the user actually has, and toggleLibrary() ALSO clears `liked` /
                    // `likedDate` — so reconciling against it silently un-liked thousands of favorites
                    // (4000 -> 1700). Push local-only library adds UP to the account instead, exactly like
                    // the liked-songs and liked-albums syncs already do.
                    localSongs.filterNot { it.id in remoteIds || it.song.isLocal }.forEach { song ->
                        try {
                            if (song.song.inLibrary != null) {
                                withRetry {
                                    YouTube.toggleSongLibrary(song.id, true)
                                }.onFailure { e ->
                                    Timber.e(e, "Failed to add song to YouTube library: ${song.id}")
                                }
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to push library song: ${song.id}")
                        }
                    }

                    // Decide new/changed in memory (no N+1) and write in batched transactions. toggleLibrary()
                    // keeps its existing behaviour: new songs get inLibrary set (and are pushed to the account),
                    // known songs are only touched when they aren't in the library yet.
                    val existing = loadExistingSongs(remoteSongs.map { it.id })
                    val insertEntities = ArrayList<SongEntity>()
                    val insertMeta = ArrayList<MediaMetadata>()
                    val updateEntities = ArrayList<SongEntity>()
                    remoteSongs.forEach { song ->
                        try {
                            val dbSong = existing[song.id]
                            if (dbSong == null) {
                                val meta = song.toMediaMetadata()
                                insertMeta.add(meta)
                                insertEntities.add(meta.toSongEntity().toggleLibrary())
                            } else if (dbSong.inLibrary == null) {
                                updateEntities.add(dbSong.toggleLibrary())
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process song: ${song.id}")
                        }
                    }
                    batchInsertSongs(insertEntities, insertMeta)
                    batchUpdateSongs(updateEntities)

                    updateState { copy(librarySongs = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteSongs.size} library songs")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing library songs")
                    updateState { copy(librarySongs = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch library songs from YouTube")
                updateState { copy(librarySongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync library songs after retries")
            updateState { copy(librarySongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncUploadedSongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncUploadedSongs - user not logged in")
            return@withContext
        }

        updateState { copy(uploadedSongs = SyncStatus.Syncing, currentOperation = "Syncing uploaded songs") }

        withRetry {
            YouTube.library("FEmusic_library_privately_owned_tracks", tabIndex = 1).completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.uploadedSongsByNameAsc().first()

                    val songsToRemove = localSongs.filterNot { it.id in remoteIds }
                    batchUpdateSongs(songsToRemove.map { it.song.toggleUploaded() })

                    // Decide new/changed in memory (no N+1) and write in batched transactions.
                    val existing = loadExistingSongs(remoteSongs.map { it.id })
                    val insertEntities = ArrayList<SongEntity>()
                    val insertMeta = ArrayList<MediaMetadata>()
                    val updateEntities = ArrayList<SongEntity>()
                    remoteSongs.forEach { song ->
                        try {
                            val dbSong = existing[song.id]
                            if (dbSong == null) {
                                val meta = song.toMediaMetadata()
                                insertMeta.add(meta)
                                insertEntities.add(meta.toSongEntity().toggleUploaded())
                            } else if (!dbSong.isUploaded) {
                                updateEntities.add(dbSong.toggleUploaded())
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process song: ${song.id}")
                        }
                    }
                    batchInsertSongs(insertEntities, insertMeta)
                    batchUpdateSongs(updateEntities)

                    updateState { copy(uploadedSongs = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteSongs.size} uploaded songs (${insertEntities.size} new, ${updateEntities.size} updated)")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing uploaded songs")
                    updateState { copy(uploadedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                // Not an error path worth a stack trace: an account with nothing uploaded now comes
                // back as an empty page, so this only fires when the response shape was unusable.
                Timber.w("Could not read uploaded songs from YouTube: ${e.message}")
                updateState { copy(uploadedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.w("Failed to sync uploaded songs after retries: ${e.message}")
            updateState { copy(uploadedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncLikedAlbums() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLikedAlbums - user not logged in")
            return@withContext
        }

        updateState { copy(likedAlbums = SyncStatus.Syncing, currentOperation = "Syncing liked albums") }

        withRetry {
            YouTube.library("FEmusic_liked_albums").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                    val remoteIds = remoteAlbums.map { it.id }.toSet()
                    val localAlbums = database.albumsLikedByNameAsc().first()

                    // Additive only: do NOT un-favorite local albums that aren't in the remote account.
                    // We don't push app-favorites to the YouTube account, so removing them here made the
                    // user's favorite albums "disappear" on their own a few minutes after adding them.

                    remoteAlbums.forEach { album ->
                        try {
                            val dbAlbum = database.album(album.id).firstOrNull()
                            if (dbAlbum == null) {
                                // NEW album only: fetch the full page + insert. The old code called
                                // YouTube.album() for EVERY album every run (the dbAlbum==null check gated
                                // only the write, not the network) → hundreds of round-trips that burned the
                                // worker budget and starved later steps (notably playlists). Now only the
                                // missing albums hit the network.
                                YouTube.album(album.browseId).onSuccess { albumPage ->
                                    database.insert(albumPage)
                                    database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                        database.update(newDbAlbum.album.localToggleLike())
                                    }
                                }
                            } else if (dbAlbum.album.bookmarkedAt == null) {
                                // Already in the DB — just mark it favorited locally, no network call.
                                database.update(dbAlbum.album.localToggleLike())
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process album: ${album.id}")
                        }
                    }

                    // Album artists become followed too, so "your artists" reflects all imports.
                    runCatching { database.followArtistsWithContent(LocalDateTime.now()) }
                    updateState { copy(likedAlbums = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteAlbums.size} liked albums")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing liked albums")
                    updateState { copy(likedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch liked albums from YouTube")
                updateState { copy(likedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync liked albums after retries")
            updateState { copy(likedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncUploadedAlbums() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncUploadedAlbums - user not logged in")
            return@withContext
        }

        updateState { copy(uploadedAlbums = SyncStatus.Syncing, currentOperation = "Syncing uploaded albums") }

        withRetry {
            YouTube.library("FEmusic_library_privately_owned_releases").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                    val remoteIds = remoteAlbums.map { it.id }.toSet()
                    val localAlbums = database.albumsUploadedByNameAsc().first()

                    localAlbums.filterNot { it.id in remoteIds }.forEach { album ->
                        try {
                            database.update(album.album.toggleUploaded())
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to update album: ${album.id}")
                        }
                    }

                    remoteAlbums.forEach { album ->
                        try {
                            val dbAlbum = database.album(album.id).firstOrNull()
                            if (dbAlbum == null) {
                                // Only NEW albums hit the network (was: every album, every run → starvation).
                                YouTube.album(album.browseId).onSuccess { albumPage ->
                                    database.insert(albumPage)
                                    database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                        database.update(newDbAlbum.album.toggleUploaded())
                                    }
                                }.onFailure { reportException(it) }
                            } else if (!dbAlbum.album.isUploaded) {
                                database.update(dbAlbum.album.toggleUploaded())
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process album: ${album.id}")
                        }
                    }

                    updateState { copy(uploadedAlbums = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteAlbums.size} uploaded albums")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing uploaded albums")
                    updateState { copy(uploadedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.w("Could not read uploaded albums from YouTube: ${e.message}")
                updateState { copy(uploadedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.w("Failed to sync uploaded albums after retries: ${e.message}")
            updateState { copy(uploadedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncArtistsSubscriptions() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncArtistsSubscriptions - user not logged in")
            return@withContext
        }

        updateState { copy(artists = SyncStatus.Syncing, currentOperation = "Syncing artist subscriptions") }

        withRetry {
            YouTube.library("FEmusic_library_corpus_artists").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteArtists = page.items.filterIsInstance<ArtistItem>()
                    val remoteIds = remoteArtists.map { it.id }.toSet()
                    val localArtists = database.artistsBookmarkedByNameAsc().first()

                    // Local follows not yet on YouTube (e.g. the artists picked during onboarding):
                    // push them UP — subscribe them on the user's YouTube account instead of dropping
                    // them locally. This keeps the user's chosen artists AND makes YouTube's algorithm
                    // (home feed) reflect their taste from the start.
                    localArtists.filterNot { it.id in remoteIds }.forEach { artist ->
                        try {
                            val channelId = artist.artist.channelId
                                ?: if (artist.id.startsWith("UC")) {
                                    runCatching { YouTube.getChannelId(artist.id) }.getOrNull()?.takeIf { it.isNotEmpty() }
                                } else null
                            if (!channelId.isNullOrEmpty()) {
                                runCatching { YouTube.subscribeChannel(channelId, true) }
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to subscribe artist on YouTube: ${artist.id}")
                        }
                    }

                    // Decide new/changed in memory against one pre-loaded snapshot (no N+1 `artist(id)`
                    // reads), then write in batched transactions so "your artists" fills a block at a time
                    // instead of flickering per row. The per-artist getChannelId() network call is kept
                    // (only fires for UC-prefixed artists that lack a channelId).
                    val now = LocalDateTime.now()
                    val existingArtists = loadExistingArtists(remoteArtists.map { it.id })
                    val artistsToInsert = ArrayList<ArtistEntity>()
                    val artistsToUpdate = ArrayList<ArtistEntity>()
                    remoteArtists.forEach { artist ->
                        try {
                            val dbArtist = existingArtists[artist.id]
                            val channelId = artist.channelId ?: if (artist.id.startsWith("UC")) {
                                try {
                                    YouTube.getChannelId(artist.id).takeIf { it.isNotEmpty() }
                                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                                    null
                                }
                            } else null

                            if (dbArtist == null) {
                                artistsToInsert.add(
                                    ArtistEntity(
                                        id = artist.id,
                                        name = artist.title,
                                        thumbnailUrl = artist.thumbnail,
                                        channelId = channelId,
                                        bookmarkedAt = now
                                    )
                                )
                            } else {
                                val needsChannelIdUpdate = dbArtist.channelId == null && channelId != null
                                if (dbArtist.bookmarkedAt == null || needsChannelIdUpdate ||
                                    dbArtist.name != artist.title || dbArtist.thumbnailUrl != artist.thumbnail) {
                                    artistsToUpdate.add(
                                        dbArtist.copy(
                                            name = artist.title,
                                            thumbnailUrl = artist.thumbnail,
                                            channelId = channelId ?: dbArtist.channelId,
                                            bookmarkedAt = dbArtist.bookmarkedAt ?: now,
                                            lastUpdateTime = now
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process artist: ${artist.id}")
                        }
                    }
                    artistsToInsert.chunked(SYNC_BATCH_SIZE).forEach { block ->
                        database.withTransaction { insertArtists(block) }
                    }
                    artistsToUpdate.chunked(SYNC_BATCH_SIZE).forEach { block ->
                        database.withTransaction { updateArtists(block) }
                    }

                    // Follow EVERY imported artist (also those from liked/library content, not just
                    // channel subscriptions) so they all appear under "your artists", like Spotify.
                    runCatching { database.followArtistsWithContent(LocalDateTime.now()) }
                    updateState { copy(artists = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteArtists.size} artist subscriptions")
                    // Fill in missing artist cover photos (e.g. artists that came in only via songs) —
                    // bounded + throttled so it doesn't hammer the network/API.
                    runCatching { fillMissingArtistImages() }
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing artist subscriptions")
                    updateState { copy(artists = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch artist subscriptions from YouTube")
                updateState { copy(artists = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync artist subscriptions after retries")
            updateState { copy(artists = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    /**
     * Fill missing artist cover photos. Many followed artists (especially those that came in only via
     * synced songs) have no image, so "your artists" shows blanks. Fetch each one's picture from its
     * artist page and store it. BOUNDED (max [MAX_ARTIST_IMAGE_FETCH] per run) and throttled so it never
     * hammers the network/API; re-running the sync fills more. Runs inside the background sync worker.
     */
    private suspend fun fillMissingArtistImages() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) return@withContext
        val missing = runCatching { database.bookmarkedArtistsMissingImage(MAX_ARTIST_IMAGE_FETCH) }
            .getOrNull().orEmpty()
        if (missing.isEmpty()) return@withContext
        var filled = 0
        for (a in missing) {
            if (a.isLocal || a.id.isBlank()) continue
            try {
                val thumb = YouTube.artist(a.id).getOrNull()?.artist?.thumbnail
                if (!thumb.isNullOrBlank() && thumb != a.thumbnailUrl) {
                    database.update(a.copy(thumbnailUrl = thumb))
                    filled++
                }
                delay(150) // gentle throttle to avoid rate limits
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to fetch artist image: ${a.id}")
            }
        }
        Timber.d("Filled $filled missing artist images (of ${missing.size} checked)")
    }

    private suspend fun executeSyncSavedPlaylists() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncSavedPlaylists - user not logged in")
            return@withContext
        }

        updateState { copy(playlists = SyncStatus.Syncing, currentOperation = "Syncing saved playlists") }

        withRetry {
            YouTube.library("FEmusic_liked_playlists").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                        .filterNot { it.id == "LM" || it.id == "SE" }
                        .reversed()
                    val localPlaylists = database.playlistsByNameAsc().first()
                    // Additive only: don't un-bookmark local saved playlists that are missing from this
                    // remote page (it can be incomplete). Same reasoning as the liked songs/albums syncs.

                    for (playlist in remotePlaylists) {
                        try {
                            var playlistEntity = localPlaylists.find { it.playlist.browseId == playlist.id }?.playlist

                            if (playlistEntity == null) {
                                playlistEntity = PlaylistEntity(
                                    name = playlist.title,
                                    browseId = playlist.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    isEditable = playlist.isEditable,
                                    bookmarkedAt = LocalDateTime.now(),
                                    remoteSongCount = playlist.songCountText?.let {
                                        Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                    },
                                    playEndpointParams = playlist.playEndpoint?.params,
                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                    radioEndpointParams = playlist.radioEndpoint?.params
                                )
                                database.insert(playlistEntity)
                                Timber.d("syncSavedPlaylists: Created new playlist ${playlist.title} (${playlist.id})")
                            } else {
                                database.update(playlistEntity, playlist)
                                Timber.d("syncSavedPlaylists: Updated existing playlist ${playlist.title} (${playlist.id})")
                            }

                            executeSyncPlaylist(playlist.id, playlistEntity.id)
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to sync playlist ${playlist.title}")
                        }
                    }

                    updateState { copy(playlists = SyncStatus.Completed) }
                    Timber.d("Synced ${remotePlaylists.size} saved playlists")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing saved playlists")
                    updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "syncSavedPlaylists: Failed to fetch playlists from YouTube")
                updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync saved playlists after retries")
            updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncAutoSyncPlaylists() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncAutoSyncPlaylists - user not logged in")
            return@withContext
        }

        try {
            val autoSyncPlaylists = database.playlistsByNameAsc().first()
                .filter { it.playlist.isAutoSync && it.playlist.browseId != null }

            Timber.d("syncAutoSyncPlaylists: Found ${autoSyncPlaylists.size} playlists to sync")

            autoSyncPlaylists.forEach { playlist ->
                try {
                    executeSyncPlaylist(playlist.playlist.browseId!!, playlist.playlist.id)
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
                }
            }
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "Error syncing auto-sync playlists")
        }
    }

    private suspend fun executeSyncPlaylist(browseId: String, playlistId: String) = withContext(Dispatchers.IO) {
        Timber.d("syncPlaylist: Starting sync for browseId=$browseId, playlistId=$playlistId")

        withRetry {
            YouTube.playlist(browseId).completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val songs = page.songs.map(SongItem::toMediaMetadata)
                    Timber.d("syncPlaylist: Fetched ${songs.size} songs from remote")

                    if (songs.isEmpty()) {
                        // Do NOT clear the local playlist here. A successful-but-empty response is almost
                        // always a transient/anomalous fetch, and wiping the local copy permanently loses
                        // the user's songs. Leave the local playlist untouched.
                        Timber.w("syncPlaylist: Remote playlist returned empty; leaving local playlist intact")
                        return@onSuccess
                    }

                    val remoteIds = songs.map { it.id }
                    val localIds = database.playlistSongs(playlistId).first()
                        .sortedBy { it.map.position }
                        .map { it.song.id }

                    if (remoteIds == localIds) {
                        Timber.d("syncPlaylist: Local and remote are in sync, no changes needed")
                        return@onSuccess
                    }

                    // Guard against a truncated/partial remote page wiping the playlist: if the remote came back
                    // MATERIALLY smaller than the local copy, treat it as a transient bad fetch and skip the
                    // destructive clear-and-replace (same spirit as the empty-response guard above). Real
                    // shrinks of <50% still sync on a later run when the full page returns.
                    if (localIds.isNotEmpty() && remoteIds.size < localIds.size / 2) {
                        Timber.w("syncPlaylist: remote (${remoteIds.size}) << local (${localIds.size}); skipping to avoid truncating the playlist")
                        return@onSuccess
                    }

                    Timber.d("syncPlaylist: Updating local playlist (remote: ${remoteIds.size}, local: ${localIds.size})")

                    val libraryNow = LocalDateTime.now()
                    // Load the songs that already exist ONCE (no per-row `song(id)` + `getSongByIdBlocking`
                    // reads inside the transaction). This whole playlist rebuild is already one transaction,
                    // so Room still emits a single time — the win here is dropping the N+1 reads.
                    val existing = loadExistingSongs(remoteIds)
                    database.withTransaction {
                        database.clearPlaylist(playlistId)
                        songs.forEachIndexed { idx, song ->
                            val dbSong = existing[song.id]
                            if (dbSong == null) {
                                // New song → insert and drop it into the library so it surfaces in
                                // Library → Songs (WHERE inLibrary IS NOT NULL).
                                database.insert(song) { it.copy(inLibrary = libraryNow) }
                            } else if (dbSong.inLibrary == null) {
                                // Set inLibrary on existing songs that don't have it yet.
                                database.update(dbSong.copy(inLibrary = libraryNow))
                            }
                            database.insert(
                                PlaylistSongMap(
                                    songId = song.id,
                                    playlistId = playlistId,
                                    position = idx,
                                    setVideoId = song.setVideoId
                                )
                            )
                        }
                    }
                    Timber.d("syncPlaylist: Successfully synced playlist")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing playlist sync")
                }
            }.onFailure { e ->
                Timber.e(e, "syncPlaylist: Failed to fetch playlist from YouTube")
            }
        }.onFailure { e ->
            Timber.e(e, "syncPlaylist: Failed after retries")
        }
    }

    private suspend fun executeCleanupDuplicatePlaylists() = withContext(Dispatchers.IO) {
        try {
            val allPlaylists = database.playlistsByNameAsc().first()
            val browseIdGroups = allPlaylists
                .filter { it.playlist.browseId != null }
                .groupBy { it.playlist.browseId }

            for ((browseId, playlists) in browseIdGroups) {
                if (playlists.size > 1) {
                    Timber.w("Found ${playlists.size} duplicate playlists for browseId: $browseId")
                    val toKeep = playlists.maxByOrNull { it.songCount } ?: playlists.first()

                    playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                        try {
                            Timber.d("Removing duplicate playlist: ${duplicate.playlist.name} (${duplicate.id})")
                            database.clearPlaylist(duplicate.id)
                            database.delete(duplicate.playlist)
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to remove duplicate playlist: ${duplicate.id}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "Error cleaning up duplicate playlists")
        }
    }

    private suspend fun executeClearAllSyncedContent() = withContext(Dispatchers.IO) {
        Timber.d("clearAllSyncedContent: Starting cleanup")

        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Clearing synced content") }

        try {
            database.withTransaction {
                // Bulk SQL UPDATEs — no longer load the entire library into memory and update row-by-row
                // (that was an OOM/multi-second-ANR risk on a 15-20k-song restored library).
                database.clearAllLikedSongs()
                database.clearAllLibrarySongs()
                database.clearAllLikedAlbums()
                database.clearAllBookmarkedArtists()
                database.clearAllUploadedSongs()
                database.clearAllUploadedAlbums()

                // Playlists are few; clear each synced playlist's song-map then delete the playlist row.
                val savedPlaylists = database.playlistsByNameAsc().first()
                savedPlaylists.forEach {
                    if (it.playlist.browseId != null) {
                        database.clearPlaylist(it.playlist.id)
                        database.delete(it.playlist)
                    }
                }
            }

            
            context.dataStore.edit { settings ->
                settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }

            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
            Timber.d("clearAllSyncedContent: Cleanup completed successfully")
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "clearAllSyncedContent: Error during cleanup")
            updateState { copy(overallStatus = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    fun cancelAllSyncs() {
        processingJob?.cancel()
        startProcessingQueue()
        updateState { SyncState() }
    }
}
