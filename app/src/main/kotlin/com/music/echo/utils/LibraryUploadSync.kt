package iad1tya.echo.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.parseCookieString
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.YtmUploadLastCompletedKey
import iad1tya.echo.music.constants.YtmUploadProgressKey
import iad1tya.echo.music.constants.YtmUploadSyncKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.extensions.isInternetConnected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** How far along one category of the library upload is. All three numbers survive app restarts. */
@Serializable
data class UploadCategoryProgress(
    /** Items the YouTube account already has. */
    val synced: Int = 0,
    /** Items still waiting to be pushed up. */
    val pending: Int = 0,
    /** True while this category is the one being worked on right now. */
    val running: Boolean = false,
    /**
     * True once this category's state has actually been established. Without it an untouched category
     * reads as 0 pending — i.e. "done" — and a pass that ran out of budget before reaching it would
     * report a completion it never verified.
     */
    val verified: Boolean = false,
) {
    val total: Int get() = synced + pending
    val isComplete: Boolean get() = verified && pending == 0
}

/**
 * The whole "is my library backed up to YouTube Music yet?" report. Persisted as JSON in DataStore
 * ([YtmUploadProgressKey]) after every step, so reopening the app shows real numbers instead of
 * starting from zero.
 */
@Serializable
data class LibraryUploadProgress(
    val playlists: UploadCategoryProgress = UploadCategoryProgress(),
    val artists: UploadCategoryProgress = UploadCategoryProgress(),
    val likedSongs: UploadCategoryProgress = UploadCategoryProgress(),
    val likedAlbums: UploadCategoryProgress = UploadCategoryProgress(),
    val running: Boolean = false,
    /** Epoch millis of the last pass that finished with NOTHING pending; 0 = never. */
    val lastCompletedEpochMs: Long = 0L,
    /** Network writes spent by the most recent pass — surfaced so the cost is never invisible. */
    val requestsLastRun: Int = 0,
    /** Non-null when the last pass stopped early (budget spent, offline, signed out, sync off). */
    val stoppedReason: String? = null,
    /** True when the last pass ran out of its per-run request budget with work still pending. */
    val moreWorkPending: Boolean = false,
    /**
     * False until the counts have been computed at least once. Without it the all-zero default would
     * read as "everything is synced" and the screen would flash a completion notice it cannot back up.
     */
    val counted: Boolean = false,
) {
    val everythingSynced: Boolean
        get() = counted && playlists.isComplete && artists.isComplete &&
            likedSongs.isComplete && likedAlbums.isComplete

    val totalPending: Int
        get() = playlists.pending + artists.pending + likedSongs.pending + likedAlbums.pending
}

/**
 * Pushes the local library UP to the user's YouTube Music account, so a new phone plus their account
 * already has everything: playlists (created or linked), deliberate artist follows (real channel
 * subscriptions), liked songs and liked albums.
 *
 * ### The one rule that must never be broken
 * An artist is uploaded as a subscription ONLY if `ArtistEntity.followedByUserAt != null` — a follow
 * the user actually made. `bookmarkedAt` is NOT that: `DatabaseDao.followArtistsWithContent()` sets it
 * on every artist that has so much as one song in the library, and pushing that set up is exactly what
 * produced the owner's report *"me aparecen muchas suscripciones de cantantes que no sigo"*.
 *
 * ### Cost control (the owner has a standing thermal/battery gate)
 * - Never runs on app start. It runs on an explicit request, at the end of a full sync, or as a
 *   bounded background continuation of a pass that ran out of budget.
 * - Hard ceiling of [MAX_REQUESTS_PER_RUN] network writes per pass, plus a small fixed number of
 *   read fetches used to make the whole thing idempotent.
 * - [REQUEST_DELAY_MS] between writes so a big library trickles instead of hammering.
 * - Fully resumable: progress lives in the DB (`playlist.browseId`, `artist.ytmSyncedAt`), so a pass
 *   that is killed mid-way simply continues where it stopped.
 *
 * ### Idempotency
 * Every step reads the account's real state FIRST and only writes the difference:
 * subscriptions are diffed against `FEmusic_library_corpus_artists`, playlists against
 * `FEmusic_liked_playlists` (matched by name so an already-existing remote playlist is LINKED rather
 * than duplicated), likes against `LM`, albums against `FEmusic_liked_albums`. A playlist that already
 * has a `browseId` is already linked and is never re-created.
 *
 * ### Destructiveness
 * The only thing that can ever be removed from the account is a subscription the user explicitly
 * unfollowed inside Aura — a row carrying the POSITIVE `unfollowedByUserAt` marker (see
 * [ArtistSyncPolicy]). Nothing else is ever deleted, unliked, or emptied.
 *
 * An unsubscribe is never inferred from an absence. Absence of a local follow marker is what a logout,
 * a "borrar contenido sincronizado", a restore from an old backup, an account switch, a database
 * migration and a sync that timed out half-written all produce — and a local clear becoming a remote
 * destruction is irreversible for the customer and invisible until they open YouTube.
 */
@Singleton
class LibraryUploadSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    companion object {
        /**
         * Ceiling on network WRITES per pass. Worst case for one pass is this many writes plus a
         * handful of paginated read fetches — a full library is spread over several spaced-out passes
         * rather than fired at the account in one burst. At [REQUEST_DELAY_MS] this is ~2 minutes of
         * throttled trickle, which comfortably fits inside a WorkManager job.
         */
        const val MAX_REQUESTS_PER_RUN = 600

        /** Gentle throttle between writes (rate limits + battery). */
        private const val REQUEST_DELAY_MS = 200L

        /** Playlists created per pass. Each one also costs one write per song it contains. */
        private const val MAX_PLAYLISTS_PER_RUN = 20

        /**
         * Largest playlist we will upload. A playlist is ALWAYS uploaded atomically — all of its songs
         * in the same pass as its creation — because a half-filled remote playlist could later be
         * pulled back down by `SyncUtils.executeSyncPlaylist`, whose clear-and-replace would then drop
         * the local songs that had not been pushed yet. Anything bigger is left pending and logged
         * rather than half-uploaded.
         */
        private const val MAX_PLAYLIST_SONGS_ATOMIC = 500

        /** Subscribes per pass. */
        private const val MAX_SUBSCRIBES_PER_RUN = 150

        /** Unsubscribes per pass. Deliberately small — this is the only destructive direction. */
        private const val MAX_UNSUBSCRIBES_PER_RUN = 50

        /** Likes pushed up per pass. */
        private const val MAX_LIKES_PER_RUN = 200

        /** Album likes pushed up per pass. */
        private const val MAX_ALBUM_LIKES_PER_RUN = 100

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    private val _progress = MutableStateFlow(LibraryUploadProgress())

    /** Collected by the sync screen. Seeded from DataStore on first read so it is never a blank slate. */
    val progress: StateFlow<LibraryUploadProgress> = _progress.asStateFlow()

    /** One pass at a time. A second request while a pass runs is a no-op, not a parallel hammering. */
    private val passMutex = Mutex()

    /** Writes spent by the pass currently running. */
    private var requestBudget = 0

    // ---- lifecycle -----------------------------------------------------------------------------

    /** Restore the last persisted report so the UI has real numbers before any pass runs. */
    suspend fun restorePersistedProgress() {
        // Never overwrite the live state of a pass that is currently running with a stale snapshot
        // (the screen and the worker can both ask for this at the same time).
        if (passMutex.isLocked || _progress.value.running) return
        val stored = runCatching {
            context.dataStore.data.map { it[YtmUploadProgressKey] }.first()
        }.getOrNull()
        if (stored.isNullOrBlank()) return
        runCatching { json.decodeFromString<LibraryUploadProgress>(stored) }
            .onSuccess { _progress.value = it.copy(running = false) }
            .onFailure { Timber.w(it, "Could not read the stored upload progress; starting fresh") }
    }

    private suspend fun persistProgress() {
        runCatching {
            val snapshot = _progress.value.copy(running = false)
            context.dataStore.edit { it[YtmUploadProgressKey] = json.encodeToString(snapshot) }
        }.onFailure { Timber.w(it, "Could not persist the upload progress") }
    }

    private suspend fun isLoggedIn(): Boolean = runCatching {
        val cookie = context.dataStore.data.map { it[InnerTubeCookieKey] }.first()
        cookie?.let { "SAPISID" in parseCookieString(it) } ?: false
    }.getOrDefault(false)

    /**
     * Fails CLOSED. An absent key means "we have not established consent yet" — the one-time
     * [iad1tya.echo.music.constants.YtmUploadOptInV1AppliedKey] migration in `App` writes the real
     * value on the first launch (ON for a fresh install, OFF for an install that was merely updated).
     * Until it has, and if the DataStore read fails for any reason, we do not write to anybody's
     * Google account.
     */
    private suspend fun isUploadEnabled(): Boolean = runCatching {
        context.dataStore.data.map { it[YtmUploadSyncKey] ?: false }.first()
    }.getOrDefault(false)

    // ---- the pass ------------------------------------------------------------------------------

    /**
     * Recompute the report from the database WITHOUT touching the network. Cheap enough to call every
     * time the sync screen opens; it never uploads anything.
     */
    suspend fun refreshCounts() = withContext(Dispatchers.IO) {
        runCatching {
            val playlistsSynced = database.countPlaylistsLinkedToYtm()
            val playlistsPending = database.countPlaylistsPendingUpload()
            val followsTotal = database.countDeliberateFollows()
            val followsSynced = database.countFollowsSyncedToYtm()
            val pendingUnsubs = database.countPendingUnsubscribes()
            // Playlists and artists are `verified` straight from the database: a playlist row with a
            // browseId IS linked to the account, and an artist row with ytmSyncedAt IS subscribed —
            // both are only ever written after the account confirmed it. No network needed.
            _progress.value = _progress.value.copy(
                counted = true,
                playlists = _progress.value.playlists
                    .copy(synced = playlistsSynced, pending = playlistsPending, verified = true),
                artists = _progress.value.artists.copy(
                    synced = followsSynced,
                    // A pending unfollow is work left to do too, so the report is not "todo listo"
                    // while an unsubscribe is still queued.
                    pending = (followsTotal - followsSynced).coerceAtLeast(0) + pendingUnsubs,
                    verified = true,
                ),
            )
            persistProgress()
        }.onFailure {
            if (it is CancellationException) throw it
            Timber.w(it, "refreshCounts failed")
        }
        Unit
    }

    /**
     * Run one bounded upload pass. Safe to call any time: it returns immediately when the user is
     * signed out, offline, has the feature switched off, or when a pass is already running.
     *
     * @return true when the whole library is now in sync (nothing pending left).
     */
    suspend fun runUploadPass(): Boolean = withContext(Dispatchers.IO) {
        if (passMutex.isLocked) {
            Timber.d("LibraryUploadSync: a pass is already running; skipping")
            return@withContext false
        }
        passMutex.withLock { runUploadPassLocked() }
    }

    private suspend fun runUploadPassLocked(): Boolean {
        // The switch governs BULK LIBRARY UPLOAD — pushing a library the user did not ask to push,
        // which is why tapping "update" must not enable it. It does NOT govern honouring an action the
        // user personally took: "si me suscribo, me desuscribo, o doy like o quito like, eso se tiene
        // que sincronizar con mi cuenta de YouTube". Those two concepts were the same flag, so with the
        // switch off — the state of every existing install after this release — an unfollow whose live
        // call did not land was never repaired. See the pending-unsubscribe flush below.
        val uploadEnabled = isUploadEnabled()
        if (!isLoggedIn()) {
            // Not an error and not a crash: the work simply stays queued in the DB until the user
            // signs in, and the report says why nothing is moving.
            stop("Inicia sesión en YouTube Music para sincronizar tu biblioteca")
            return false
        }
        if (!context.isInternetConnected()) {
            stop("Sin conexión — se reanudará automáticamente")
            return false
        }

        requestBudget = 0
        _progress.value = _progress.value.copy(running = true, stoppedReason = null, moreWorkPending = false)

        try {
            // ORDER MATTERS: read the account's real subscription list first (it makes the subscribe
            // step idempotent), then flush pending unfollows before adding anything. The read-back is
            // what stamps `ytmSyncedAt`, so an unfollow that never reached YouTube is re-armed here
            // and honoured in the same pass instead of drifting. It can no longer create a pending
            // unsubscribe out of a row that merely lost its local follow marker — see
            // fetchRemoteSubscriptionIds and ArtistSyncPolicy.
            val remoteArtistIds = fetchRemoteSubscriptionIds()

            // ALWAYS, switch or no switch. `unfollowedByUserAt` is written in exactly one place —
            // ArtistEntity.localToggleLike, i.e. the user tapping the follow button — so every row this
            // touches is provably an action the user took themselves, against the account that was
            // signed in at the time. Repairing one that did not reach YouTube (offline, expired cookie,
            // unresolved channelId) is finishing their instruction, not enrolling them into anything.
            //
            // The opposite direction stays behind the switch on purpose: `followedByUserAt` is ALSO
            // written by the onboarding picker, by Spotify/Tidal/Deezer migrations and by the
            // subscription read-back, so it is not provably tap-origin and a bulk of it is exactly what
            // the opt-in exists to hold back.
            flushPendingUnsubscribes()

            if (uploadEnabled) {
                uploadArtistSubscriptions(remoteArtistIds)
                uploadPlaylists()
                uploadLikedSongs()
                uploadLikedAlbums()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LibraryUploadSync: pass failed")
            _progress.value = _progress.value.copy(
                running = false,
                stoppedReason = "Se interrumpió la sincronización; se reanudará",
                requestsLastRun = requestBudget,
            )
            persistProgress()
            return false
        }

        if (!uploadEnabled) {
            // The user's own pending unfollows have been honoured; the bulk upload has not run and
            // must not be reported as finished. `stop` sets a reason, which is also what stops
            // YtmSyncWorker from chaining a continuation for work it is not allowed to do.
            refreshCounts()
            _progress.value = _progress.value.copy(
                running = false,
                requestsLastRun = requestBudget,
                stoppedReason = "La sincronización con YouTube Music está desactivada",
            )
            persistProgress()
            return false
        }

        refreshCounts()
        val done = _progress.value.everythingSynced
        _progress.value = _progress.value.copy(
            running = false,
            requestsLastRun = requestBudget,
            moreWorkPending = !done,
            lastCompletedEpochMs = if (done) System.currentTimeMillis()
            else _progress.value.lastCompletedEpochMs,
        )
        if (done) {
            runCatching {
                context.dataStore.edit { it[YtmUploadLastCompletedKey] = System.currentTimeMillis() }
            }
        }
        persistProgress()
        Timber.d(
            "LibraryUploadSync: pass finished — $requestBudget writes, " +
                if (done) "library fully synced" else "${_progress.value.totalPending} items still pending",
        )
        return done
    }

    private suspend fun stop(reason: String) {
        _progress.value = _progress.value.copy(running = false, stoppedReason = reason)
        persistProgress()
    }

    /** Spend one unit of the per-pass budget. Returns false when the pass must stop. */
    private suspend fun spend(): Boolean {
        if (requestBudget >= MAX_REQUESTS_PER_RUN) return false
        requestBudget++
        delay(REQUEST_DELAY_MS)
        return true
    }

    private fun budgetLeft() = requestBudget < MAX_REQUESTS_PER_RUN

    // ---- artists ------------------------------------------------------------------------------

    /**
     * The account's REAL channel subscriptions. Reading them first is what makes the subscribe step
     * idempotent: anything already there is stamped as synced and never sent again.
     */
    private suspend fun fetchRemoteSubscriptionIds(): Set<String> {
        val page = runCatching { YouTube.library("FEmusic_library_corpus_artists").completed() }
            .getOrNull()?.getOrNull() ?: return emptySet()
        val ids = page.items.filterIsInstance<ArtistItem>().map { it.id }
        if (ids.isNotEmpty()) {
            // Backfill for the v39->v40 columns: an artist the account is genuinely subscribed to IS a
            // deliberate follow, so mark it as such AND as already synced. This is the ONLY backfill —
            // we never guess a follow from a local bookmark.
            //
            // This statement is the one that shipped the blocker. Its old version read `bookmarkedAt`
            // and, for every row without one, stamped `ytmSyncedAt` while leaving `followedByUserAt`
            // NULL — the exact signature the unsubscribe queue used to look for, and flushed two lines
            // later in the SAME pass. It now cannot produce that shape at all (see
            // DatabaseDao.markArtistsSubscribedOnYtm / ArtistSyncPolicy.afterRemoteSubscriptionSeen).
            runCatching {
                ids.chunked(500).forEach { database.markArtistsSubscribedOnYtm(it, LocalDateTime.now()) }
            }.onFailure { Timber.w(it, "Could not backfill subscribed artists") }
        }
        return ids.toSet()
    }

    private suspend fun uploadArtistSubscriptions(remoteIds: Set<String>) {
        _progress.value = _progress.value.copy(artists = _progress.value.artists.copy(running = true))
        val pending = runCatching { database.artistsPendingSubscribe(MAX_SUBSCRIBES_PER_RUN) }
            .getOrNull().orEmpty()
            // Same in-Kotlin re-check as the unsubscribe path: the DAO query and the policy must agree
            // before anything is written to someone's account, in either direction.
            .filter { ArtistSyncPolicy.maySubscribe(it) }
            // Belt and braces on top of the DB filter: never re-subscribe something the account
            // already has (it would still be a wasted write even though YouTube is idempotent).
            .filterNot { it.id in remoteIds }

        val subscribed = ArrayList<String>()
        for (artist in pending) {
            if (!budgetLeft()) break
            try {
                val channelId = artist.channelId?.takeIf { it.isNotBlank() }
                    ?: run {
                        if (!spend()) return@run null
                        YouTube.getChannelId(artist.id).takeIf { it.isNotEmpty() }
                    }
                if (channelId.isNullOrBlank()) {
                    // No channel to subscribe to (local/uploaded artist). Leave it pending rather than
                    // faking success — but don't burn the budget retrying it forever either.
                    Timber.d("LibraryUploadSync: no channelId for ${artist.name}; skipping")
                    continue
                }
                if (artist.channelId.isNullOrBlank()) {
                    // Remember the resolved channel id so the next pass doesn't pay for it again.
                    runCatching { database.update(artist.copy(channelId = channelId)) }
                }
                if (!spend()) break
                YouTube.subscribeChannel(channelId, true)
                    .onSuccess { subscribed.add(artist.id) }
                    .onFailure { Timber.w(it, "Could not subscribe to ${artist.name}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not subscribe to ${artist.name}")
            }
        }

        if (subscribed.isNotEmpty()) {
            runCatching { database.markArtistsSyncedToYtm(subscribed, LocalDateTime.now()) }
        }
        _progress.value = _progress.value.copy(artists = _progress.value.artists.copy(running = false))
        Timber.d("LibraryUploadSync: subscribed to ${subscribed.size} artists")
    }

    /**
     * Honour unfollows the user made in Aura. This is the ONLY code path in the whole feature that
     * removes something from the account.
     *
     * Every row is re-checked against [ArtistSyncPolicy] in Kotlin before anything is sent, even
     * though the DAO query already filters on the same predicate. That redundancy is deliberate: the
     * blocker this feature shipped with was a SQL statement in a different file quietly rewriting rows
     * into the shape this query looks for, so one layer alone is not enough to protect an irreversible,
     * invisible-to-the-user action. A rejected row is logged and skipped, never "cleaned up" — we do
     * not know why it is in that state, and guessing is what caused the incident.
     */
    private suspend fun flushPendingUnsubscribes() {
        val fromDb = runCatching { database.artistsPendingUnsubscribe(MAX_UNSUBSCRIBES_PER_RUN) }
            .getOrNull().orEmpty()
        val now = LocalDateTime.now()
        val cleared = ArrayList<String>()
        /** Rows we really did send `subscribeChannel(id, false)` for, as opposed to markers retired. */
        var unsubscribed = 0
        val pending = fromDb.filter { artist ->
            val refusal = ArtistSyncPolicy.refuseUnsubscribe(artist, now)
            when (refusal) {
                null -> Unit
                ArtistSyncPolicy.UnsubscribeRefusal.STALE_INTENT -> {
                    // The one refusal we are allowed to act on, and only in the harmless direction:
                    // an instruction this old is not pending anything, and leaving it in place would
                    // occupy a queue slot on every pass forever AND could be re-armed years later if
                    // the user subscribes to that artist directly on YouTube. Retiring it removes the
                    // marker; it never touches the account.
                    Timber.i(
                        "LibraryUploadSync: retiring a stale queued unfollow for '${artist.name}' " +
                            "(older than ${ArtistSyncPolicy.PENDING_UNFOLLOW_TTL_DAYS} days)",
                    )
                    cleared.add(artist.id)
                }
                else -> Timber.w(
                    "LibraryUploadSync: REFUSING to unsubscribe '${artist.name}' ($refusal). The query " +
                        "returned a row the policy does not authorise — a local state change was about " +
                        "to be pushed up as a real unsubscribe.",
                )
            }
            refusal == null
        }
        if (pending.isEmpty()) {
            if (cleared.isNotEmpty()) runCatching { database.clearArtistsSyncedToYtm(cleared) }
            return
        }

        for (artist in pending) {
            if (!budgetLeft()) break
            try {
                // RESOLVE the channel id exactly like the subscribe path 60 lines above, instead of
                // treating a missing one as "nothing to do".
                //
                // `channelId` is routinely NULL on rows that are nonetheless genuinely subscribed:
                // fetchRemoteSubscriptionIds stamps ytmSyncedAt straight from the account's id list
                // and never writes a channelId, and the artist down-sync leaves it null whenever its
                // per-artist getChannelId() call failed. The previous version added those rows to
                // `cleared`, which runs clearArtistsSyncedToYtm and drops BOTH ytmSyncedAt and the
                // unfollowedByUserAt intent marker — so YouTube was never contacted and the user's
                // unfollow was destroyed permanently, with no retry and no trace.
                //
                // Resolution is by the artist's OWN id. Never by name: a name-matched channel belongs
                // to whoever happens to share the name, and persisting it would point every future
                // subscribe/unsubscribe at a stranger's channel.
                var channelId = artist.channelId?.takeIf { it.isNotBlank() }
                if (channelId == null &&
                    ArtistSyncPolicy.onMissingChannelId(artist, resolutionAttempted = false) ==
                    ArtistSyncPolicy.MissingChannelAction.RESOLVE_FROM_ID
                ) {
                    if (!spend()) break
                    // NOT wrapped in runCatching: that would swallow CancellationException and let the
                    // loop keep running after its job was cancelled. The enclosing try handles both.
                    channelId = YouTube.getChannelId(artist.id)
                        .takeIf { it.isNotEmpty() }
                        ?.also { resolved ->
                            // Remember it so the next pass doesn't pay for the lookup again.
                            runCatching { database.update(artist.copy(channelId = resolved)) }
                        }
                }
                if (channelId.isNullOrBlank()) {
                    when (ArtistSyncPolicy.onMissingChannelId(artist, resolutionAttempted = true)) {
                        ArtistSyncPolicy.MissingChannelAction.RETIRE_INTENT -> cleared.add(artist.id)
                        else -> Timber.w(
                            "LibraryUploadSync: could not resolve a channelId for '${artist.name}'; " +
                                "leaving the unsubscribe queued rather than discarding it",
                        )
                    }
                    continue
                }
                if (!spend()) break
                YouTube.subscribeChannel(channelId, false)
                    .onSuccess { cleared.add(artist.id); unsubscribed++ }
                    .onFailure { Timber.w(it, "Could not unsubscribe from ${artist.name}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not unsubscribe from ${artist.name}")
            }
        }
        if (cleared.isNotEmpty()) {
            runCatching { database.clearArtistsSyncedToYtm(cleared) }
        }
        // `cleared` also carries rows retired without a network call (stale intent, no channel to
        // unsubscribe from), so count the real ones separately rather than overstating what was sent.
        Timber.d(
            "LibraryUploadSync: unsubscribed from $unsubscribed artists " +
                "(${cleared.size - unsubscribed} markers retired without contacting YouTube)",
        )
    }

    // ---- playlists ----------------------------------------------------------------------------

    private suspend fun uploadPlaylists() {
        if (!budgetLeft()) return
        _progress.value = _progress.value.copy(playlists = _progress.value.playlists.copy(running = true))

        val pending = runCatching { database.playlistsPendingUpload(MAX_PLAYLISTS_PER_RUN) }
            .getOrNull().orEmpty()
        if (pending.isEmpty()) {
            _progress.value = _progress.value.copy(playlists = _progress.value.playlists.copy(running = false))
            return
        }

        // Read the account's playlists ONCE. A local playlist whose name already exists up there is
        // LINKED to it instead of being created again — this is what stops the backfill from filling
        // the account with duplicates when the same library was migrated on another device.
        val remoteByName = HashMap<String, PlaylistItem>()
        runCatching { YouTube.library("FEmusic_liked_playlists").completed() }
            .getOrNull()?.getOrNull()?.items?.filterIsInstance<PlaylistItem>()
            ?.filterNot { it.id == "LM" || it.id == "SE" }
            ?.forEach { remoteByName.putIfAbsent(normalizeName(it.title), it) }

        // browseIds already claimed locally must never be handed to a second local playlist.
        val claimed = runCatching {
            database.playlistsWithBrowseIdBlocking().mapNotNull { it.playlist.browseId }.toHashSet()
        }.getOrDefault(HashSet())

        for (playlist in pending) {
            if (!budgetLeft()) break
            try {
                val songIds = runCatching { database.playlistSongIdsInOrder(playlist.id) }
                    .getOrNull().orEmpty()
                if (songIds.size > MAX_PLAYLIST_SONGS_ATOMIC) {
                    Timber.w(
                        "LibraryUploadSync: '${playlist.name}' has ${songIds.size} songs " +
                            "(> $MAX_PLAYLIST_SONGS_ATOMIC); leaving it pending rather than half-uploading it",
                    )
                    continue
                }

                val match = remoteByName[normalizeName(playlist.name)]
                    ?.takeIf { it.isEditable && it.id !in claimed }
                if (match != null) {
                    // Already on the account under the same name (e.g. the same library was migrated
                    // on another device): LINK instead of creating a second copy. That is the whole
                    // anti-duplicate guarantee for the backfill.
                    //
                    // Top up first: any song that exists locally but not upstream is pushed BEFORE we
                    // set browseId. Once browseId is set, SyncUtils.executeSyncPlaylist may rebuild the
                    // local playlist from the remote one, so linking a local copy that is a superset of
                    // the remote one would silently drop the extra songs.
                    if (!topUpRemotePlaylist(match.id, playlist.name, songIds)) continue
                    database.update(playlist.copy(browseId = match.id, isAutoSync = true))
                    claimed.add(match.id)
                    Timber.d("LibraryUploadSync: linked '${playlist.name}' to existing ${match.id}")
                    continue
                }

                // Atomic-or-nothing: only create it if this pass can also carry ALL of its songs.
                // A created-but-half-filled playlist is exactly the state a later down-sync could use
                // to truncate the local copy.
                if (requestBudget + songIds.size + 1 > MAX_REQUESTS_PER_RUN) {
                    Timber.d("LibraryUploadSync: not enough budget left for '${playlist.name}'; next pass")
                    break
                }

                if (!spend()) break
                val browseId = runCatching { YouTube.createPlaylist(playlist.name) }
                    .onFailure { Timber.w(it, "Could not create '${playlist.name}' on YouTube") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: continue

                // Persist the link IMMEDIATELY, before pushing songs. If the process is killed here the
                // playlist is already associated, so the next pass tops it up instead of creating a
                // second copy — never a duplicate.
                database.update(playlist.copy(browseId = browseId, isAutoSync = true))
                claimed.add(browseId)

                for (songId in songIds) {
                    if (!spend()) break
                    YouTube.addToPlaylist(browseId, songId)
                        .onFailure { Timber.w(it, "Could not add $songId to '${playlist.name}'") }
                }
                Timber.d("LibraryUploadSync: created '${playlist.name}' as $browseId with ${songIds.size} songs")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not upload playlist '${playlist.name}'")
            }
        }
        _progress.value = _progress.value.copy(playlists = _progress.value.playlists.copy(running = false))
    }

    /** Name key used to match a local playlist against one that already exists on the account. */
    private fun normalizeName(name: String) = name.trim().lowercase()

    /**
     * Make the remote playlist [browseId] a superset of [localSongIds] by adding only what it is
     * missing — never removing anything. Returns false when the top-up could not be completed (remote
     * unreadable, or not enough budget left this pass), in which case the caller must NOT link the
     * local playlist yet: linking a local superset to a smaller remote copy would let the next
     * down-sync drop the difference.
     */
    private suspend fun topUpRemotePlaylist(
        browseId: String,
        name: String,
        localSongIds: List<String>,
    ): Boolean {
        val remoteIds = runCatching { YouTube.playlist(browseId).completed() }
            .getOrNull()?.getOrNull()?.songs?.map { it.id }?.toSet()
            ?: run {
                Timber.w("LibraryUploadSync: could not read remote playlist $browseId; not linking '$name' yet")
                return false
            }
        val missing = localSongIds.filterNot { it in remoteIds }
        if (missing.isEmpty()) return true
        if (requestBudget + missing.size > MAX_REQUESTS_PER_RUN) {
            Timber.d("LibraryUploadSync: not enough budget to top up '$name' (${missing.size} songs); next pass")
            return false
        }
        for (songId in missing) {
            if (!spend()) return false
            YouTube.addToPlaylist(browseId, songId)
                .onFailure { Timber.w(it, "Could not add $songId to '$name'") }
        }
        return true
    }

    // ---- likes --------------------------------------------------------------------------------

    private suspend fun uploadLikedSongs() {
        if (!budgetLeft()) return
        _progress.value = _progress.value.copy(likedSongs = _progress.value.likedSongs.copy(running = true))

        val localLiked = runCatching { database.likedSongIdsSyncable() }.getOrNull().orEmpty()
        if (localLiked.isEmpty()) {
            _progress.value = _progress.value.copy(
                likedSongs = UploadCategoryProgress(
                    synced = 0, pending = 0, running = false, verified = true,
                ),
            )
            return
        }

        val remoteIds = runCatching { YouTube.playlist("LM").completed() }
            .getOrNull()?.getOrNull()?.songs?.map { it.id }?.toSet()
        if (remoteIds == null) {
            // Could not read the account's favourites. Treat that as "unknown", never as "nothing is
            // synced" — reporting 0/N here would be a lie and would re-send every like.
            _progress.value = _progress.value.copy(likedSongs = _progress.value.likedSongs.copy(running = false))
            return
        }

        val missing = localLiked.filterNot { it in remoteIds }
        var pushed = 0
        for (songId in missing) {
            if (pushed >= MAX_LIKES_PER_RUN || !budgetLeft()) break
            if (!spend()) break
            pushed++
            YouTube.likeVideo(songId, true)
                .onFailure { Timber.w(it, "Could not like $songId on YouTube") }
        }

        _progress.value = _progress.value.copy(
            likedSongs = UploadCategoryProgress(
                synced = localLiked.size - missing.size + pushed,
                pending = (missing.size - pushed).coerceAtLeast(0),
                running = false,
                verified = true,
            ),
        )
        Timber.d("LibraryUploadSync: pushed $pushed likes (${missing.size} were missing)")
    }

    private suspend fun uploadLikedAlbums() {
        if (!budgetLeft()) return
        _progress.value = _progress.value.copy(likedAlbums = _progress.value.likedAlbums.copy(running = true))

        val localAlbums = runCatching { database.likedAlbumsSyncable() }.getOrNull().orEmpty()
        val totalLiked = runCatching { database.countLikedAlbumsSyncable() }.getOrNull() ?: localAlbums.size
        if (localAlbums.isEmpty()) {
            _progress.value = _progress.value.copy(
                likedAlbums = UploadCategoryProgress(
                    synced = totalLiked, pending = 0, running = false, verified = true,
                ),
            )
            return
        }

        val remote = runCatching { YouTube.library("FEmusic_liked_albums").completed() }
            .getOrNull()?.getOrNull()?.items?.filterIsInstance<AlbumItem>()
        if (remote == null) {
            _progress.value = _progress.value.copy(likedAlbums = _progress.value.likedAlbums.copy(running = false))
            return
        }
        // Match on BOTH ids: the local row keys albums by browseId while the like itself is applied to
        // the album's audio playlistId, and the two are not interchangeable.
        val remoteIds = HashSet<String>(remote.size * 2)
        remote.forEach { remoteIds.add(it.id); remoteIds.add(it.playlistId) }

        val missing = localAlbums.filterNot { album ->
            album.id in remoteIds || album.playlistId?.let { it in remoteIds } == true
        }
        var pushed = 0
        for (album in missing) {
            if (pushed >= MAX_ALBUM_LIKES_PER_RUN || !budgetLeft()) break
            val playlistId = album.playlistId ?: continue
            if (!spend()) break
            pushed++
            YouTube.likePlaylist(playlistId, true)
                .onFailure { Timber.w(it, "Could not favourite album '${album.title}' on YouTube") }
        }

        // Albums with no playlistId can never be pushed up (there is nothing to like), so they are
        // counted as synced rather than leaving the report permanently stuck below 100%.
        val unpushable = (totalLiked - localAlbums.size).coerceAtLeast(0)
        _progress.value = _progress.value.copy(
            likedAlbums = UploadCategoryProgress(
                synced = localAlbums.size - missing.size + pushed + unpushable,
                pending = (missing.size - pushed).coerceAtLeast(0),
                running = false,
                verified = true,
            ),
        )
        Timber.d("LibraryUploadSync: pushed $pushed album favourites (${missing.size} were missing)")
    }
}
