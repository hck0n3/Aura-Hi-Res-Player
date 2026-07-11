

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import iad1tya.echo.music.utils.iTunesDiscography
import iad1tya.echo.music.utils.systemRegionCode
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.models.ItemsPage
import kotlinx.coroutines.flow.first
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil

@HiltViewModel
class ArtistItemsViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")
    private val artistId = savedStateHandle.get<String>("artistId")

    val title = MutableStateFlow("")
    val itemsPage = MutableStateFlow<ItemsPage?>(null)
    // Terminal-failure flag: when YouTube.artistItems fails (e.g. the Videos "more" browse), flip this so
    // the screen STOPS the shimmer and offers Retry instead of spinning forever (itemsPage stays null).
    val hasFailed = MutableStateFlow(false)

    // Structural quality of one YouTube-Music album (song count + whether the median track is long
    // enough to be a real track, not a truncated preview). Kept without the iTunes `expected` check so
    // it can be cached independent of which title it matched.
    private data class AlbumQuality(val songCount: Int, val basicOk: Boolean)

    companion object {
        // Per-artist cache of the completed (iTunes-driven) discography for this app session, so
        // re-opening the same artist's albums shows the full list instantly instead of re-fetching.
        private val completedCache =
            java.util.concurrent.ConcurrentHashMap<String, List<YTItem>>()

        // Per-browseId cache of album quality for the session (so re-checking the same album — base or a
        // completion candidate — never re-hits the network). Bounded like completedCache.
        private val albumQualityCache =
            java.util.concurrent.ConcurrentHashMap<String, AlbumQuality>()

        // Instrumental / karaoke / backing-track uploads: a duplicate of a real album with the vocals
        // stripped. Dropped during reconciliation UNLESS the artist's genuine iTunes release is itself
        // instrumental (see itunesInstrumental in buildCompleteDiscography).
        private val INSTRUMENTAL = Regex("(?i)\\b(instrumental|karaoke|backing track|playback)\\b")

        // Live / acoustic / unplugged editions are DIFFERENT recordings of the same title (common for the
        // app's worship/Latin audience: studio + "En Vivo"). They must NOT collapse into the studio album
        // during dedupe — unlike deluxe/remaster/instrumental, which are the same recording and do collapse.
        private val LIVE_ACOUSTIC =
            Regex("(?i)\\b(en\\s*vivo|en\\s*directo|live|directo|unplugged|ac[uú]stico|acoustic|en\\s*concierto)\\b")
    }

    /** Dedup key: normalized title PLUS a marker so live/acoustic editions stay distinct from the studio
     *  release (same title, different recording). normalizeTitle strips "en vivo/live", so we re-add it. */
    private fun reconKey(rawTitle: String): String {
        val base = iTunesDiscography.normalizeTitle(rawTitle)
        return if (LIVE_ACOUSTIC.containsMatchIn(rawTitle)) "$base|live" else base
    }

    init {
        load()
    }

    fun load() {
        hasFailed.value = false
        viewModelScope.launch {
            YouTube
                .artistItems(
                    BrowseEndpoint(
                        browseId = browseId,
                        params = params,
                    ),
                ).onSuccess { artistItemsPage ->
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                    title.value = artistItemsPage.title
                    // Is THIS see-all the Singles/EP section (vs Albums)? Drives which iTunes releases the
                    // completion targets, so Singles/EPs get completed too (the owner wants ALL of them).
                    val isSinglesSection =
                        Regex("(?i)single|sencillo|\\bep\\b").containsMatchIn(artistItemsPage.title ?: "")
                    val baseItems = artistItemsPage.items
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                    // For the albums list, build the COMPLETE discography up front (iTunes-driven, in
                    // parallel) and publish it in ONE shot, so the whole discography shows at once instead
                    // of extra albums popping in seconds later. Other lists publish as-is.
                    // Show the YouTube list IMMEDIATELY (fast), then complete it against iTunes/Apple Music
                    // in the background and publish the full discography in ONE update (not in batches).
                    itemsPage.value = ItemsPage(items = baseItems, continuation = artistItemsPage.continuation)
                    if (baseItems.any { it is AlbumItem }) {
                        // Key by artist AND browse: the same artist's Albums and Singles/EP see-all screens
                        // share artistId but differ by browseId. Keying by artistId alone made opening one
                        // section serve the OTHER's cached list (albums showing under Singles).
                        val cacheKey = "${artistId ?: ""}:$browseId"
                        val cached = completedCache[cacheKey]
                        if (cached != null) {
                            // Re-opening the same section: show the full discography instantly. Merge with
                            // whatever is already shown and KEEP the live continuation — never rewind paging.
                            // The reconciled `cached` is the authority, so mergeDiscography also drops any
                            // base duplicate (e.g. instrumental) that the fresh base publish re-introduced.
                            val current = itemsPage.value
                            itemsPage.value = ItemsPage(
                                items = mergeDiscography(cached, current?.items ?: emptyList()),
                                continuation = current?.continuation ?: artistItemsPage.continuation,
                            )
                        } else {
                            viewModelScope.launch {
                                val complete =
                                    runCatching { buildCompleteDiscography(baseItems, hideExplicit, isSinglesSection) }
                                        .getOrDefault(baseItems)
                                // Publish whenever reconciliation CHANGED the list — it may add albums, but it
                                // may also just de-duplicate (drop an instrumental/truncated copy) or swap a
                                // truncated upload for a full one, which need not grow the count. `!=` covers
                                // all three; identical means nothing to do (never publish an empty/no-op).
                                if (complete.isNotEmpty() && complete != baseItems) {
                                    completedCache[cacheKey] = complete
                                    // Merge with whatever the user has scrolled in by now and keep the LIVE
                                    // continuation, so the completion never overwrites loaded pages or
                                    // rewinds paging back to page 1.
                                    val current = itemsPage.value
                                    itemsPage.value = ItemsPage(
                                        items = mergeDiscography(complete, current?.items ?: emptyList()),
                                        continuation = current?.continuation ?: artistItemsPage.continuation,
                                    )
                                }
                            }
                        }
                    }
                }.onFailure {
                    reportException(it)
                    hasFailed.value = true
                }
        }
    }

    private suspend fun resolveArtistName(): String? {
        val id = artistId ?: return null
        runCatching { database.artist(id).first()?.artist?.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching { YouTube.artist(id).getOrNull()?.artist?.title }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * Complete the artist's discography using iTunes / Apple Music as the authoritative source of which
     * releases exist (albums, EPs AND singles) AND how many tracks each has, then resolve each missing one
     * on YouTube / YouTube Music IN PARALLEL — first as a proper album, otherwise as a community/user
     * upload. Every album candidate (base list included) is quality-gated (median track length + iTunes
     * track count) and reconciled to ONE winner per logical album, so truncated/instrumental duplicates
     * (e.g. Lauren Daigle self-titled) are dropped. Returns the full, de-duplicated list to publish at once.
     */
    private suspend fun buildCompleteDiscography(
        baseItems: List<YTItem>,
        hideExplicit: Boolean,
        isSinglesSection: Boolean,
    ): List<YTItem> = coroutineScope {
        val artistName = resolveArtistName() ?: return@coroutineScope baseItems
        val norm = iTunesDiscography::normalizeTitle
        val baseAlbums = baseItems.filterIsInstance<AlbumItem>()

        fun credited(a: AlbumItem): Boolean {
            val arts = a.artists
            if (arts.isNullOrEmpty()) return true
            return arts.any {
                it.id == artistId ||
                    it.name.contains(artistName, ignoreCase = true) ||
                    artistName.contains(it.name, ignoreCase = true)
            }
        }
        fun titleMatch(ytTitle: String, target: String): Boolean {
            val yt = norm(ytTitle)
            // Short titles (now that singles feed Albums completion) need an EXACT match — a fuzzy substring
            // would let a single like "Amor" match the album "Lenguaje de Amor". Only titles ≥6 chars use
            // the fuzzy contains() so genuinely long titles still tolerate minor punctuation/edition diffs.
            return yt == target || (target.length >= 6 && (yt.contains(target) || target.contains(yt)))
        }

        // iTunes / Apple Music is the authority on which releases exist AND how many tracks each has. Query
        // several stores in parallel and merge — the US store, the device's local store, and the main
        // Spanish-speaking markets — so a Latin/regional artist's catalog (e.g. Alex Campos) isn't cut
        // short by the US store alone. Each store is one cheap request; only releases that ALSO resolve on
        // YouTube are actually added, so extra stores can only make the list more complete, never add junk.
        val stores = listOf("us", systemRegionCode(), "mx", "es", "co", "ar", "cl").distinct()
        val itunesMeta = stores
            .map { store -> async { iTunesDiscography.fetchAlbumMeta(artistName, store) } }
            .awaitAll()
            .flatten()
        // Expected track count per normalized title = MAX across the queried stores (regional editions
        // differ; the fullest edition is what "complete" means). Used to detect truncated uploads.
        val expectedTracks: Map<String, Int> = itunesMeta
            .groupBy { norm(it.first) }
            .mapValues { (_, v) -> v.maxOf { it.second } }
            .filterKeys { it.isNotBlank() }
        // Norm-titles whose GENUINE iTunes release is itself instrumental — for these we must NOT drop
        // instrumental YouTube candidates (a real instrumental album is not a karaoke duplicate).
        val itunesInstrumental: Set<String> = itunesMeta
            .filter { INSTRUMENTAL.containsMatchIn(it.first) }
            .mapNotNull { norm(it.first).takeIf { n -> n.isNotBlank() } }
            .toSet()
        val itunes = itunesMeta.map { it.first }.distinct()

        // 6 concurrent network calls (album fetches + searches): more can itself trip YouTube throttling,
        // which then times out individual lookups and silently drops real albums. Shared across all phases.
        val semaphore = Semaphore(6)

        // Phase A — quality-gate the BASE albums (bounded, cached). A base album that FAILS the gate is left
        // OUT of `have`, so a truncated/preview "official" upload becomes eligible for re-completion and can
        // be replaced by a full album or community upload. Capped at 60 fetches so a huge catalog can't turn
        // this into a long network burst (battery/heat). Beyond the cap, base albums are trusted as-is.
        baseAlbums.distinctBy { it.browseId }.take(60).map { a ->
            async { semaphore.withPermit { withTimeoutOrNull(12000L) { fetchAlbumQuality(a.browseId) } } }
        }.awaitAll()
        val have = baseAlbums
            .filter { isComplete(albumQualityCache[it.browseId], expectedTracks[norm(it.title)]) }
            .map { norm(it.title) }
            .toMutableSet()

        // Don't mix EPs/Singles into the Albums list (iTunes/Apple keep them separate). iTunes marks them
        // as "Title - EP" / "Title - Single".
        val epOrSingle = Regex("(?i)[-–—]\\s*(ep|single)\\b|\\((?:ep|single)\\)")
        val missing = itunes
            .filter { norm(it).isNotBlank() && norm(it) !in have }
            // Complete the WHOLE iTunes/Apple catalog for the main (Albums) discography — INCLUDING EPs and
            // singles. iTunes returns most of an artist's releases as "… - Single"/"… - EP" (especially
            // Latin/regional catalogs) and YouTube Music usually omits them; the old EP/Single exclusion
            // (regression 866b4c8) left singles-heavy catalogs with nothing to add → nothing published
            // ("ya estaba y no lo hace"). A dedicated Singles/EP see-all, when it exists, still narrows to
            // EP/Single only. This restores the iTunes-authoritative completion the owner remembers.
            .let { list -> if (isSinglesSection) list.filter { epOrSingle.containsMatchIn(it) } else list }
            .distinctBy { norm(it) }
            .take(80)

        // Phase B — resolve each missing release on YouTube (album first, else community playlist).
        val found = missing.map { mt ->
            async {
                semaphore.withPermit {
                  // Bound each lookup so one slow YouTube search can't stall the whole completion.
                  // 12s (was 8s) gives a throttled search enough time to return before being dropped.
                  withTimeoutOrNull(12000L) {
                    val target = norm(mt)
                    val album = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_ALBUM)
                        .getOrNull()?.items?.filterIsInstance<AlbumItem>()
                        ?.firstOrNull { credited(it) && titleMatch(it.title, target) }
                    if (album != null) {
                        // Quality is gated later during reconciliation (Phase C/D), so a truncated album
                        // hit here can still lose to a full community upload for the same title.
                        target to (album as YTItem)
                    } else {
                        val pl = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
                            .getOrNull()?.items?.filterIsInstance<PlaylistItem>()
                            ?.firstOrNull { p ->
                                val t = norm(p.title)
                                (t == target || (target.length >= 4 && (t.contains(target) || target.contains(t)))) &&
                                    (p.title.contains(artistName, ignoreCase = true) ||
                                        p.author?.name?.contains(artistName, ignoreCase = true) == true)
                            }

                        // Quality control: only add a community playlist if its tracks are NOT truncated
                        // previews (the Lauren Daigle problem — list looks complete but songs play for
                        // seconds) and it has roughly the iTunes track count. Full, playable audio only.
                        if (pl != null && isSourceComplete(pl.id, expectedTracks[target])) {
                            target to (pl as YTItem)
                        } else {
                            null
                        }
                    }
                  }
                }
            }
        }.awaitAll().filterNotNull()

        val foundAlbums = found.mapNotNull { it.second as? AlbumItem }
        val foundPlaylists = found.mapNotNull { (t, item) -> (item as? PlaylistItem)?.let { t to it } }

        // Phase C — quality-gate the found albums too (bounded, cached), so reconciliation can rank them
        // against the base albums by real track count.
        foundAlbums.distinctBy { it.browseId }.map { a ->
            async { semaphore.withPermit { withTimeoutOrNull(12000L) { fetchAlbumQuality(a.browseId) } } }
        }.awaitAll()

        // Phase D — reconcile per normalized title: ONE winner per logical album. Ranking for albums:
        //   passes quality gate > track count closest to iTunes expected > non-instrumental > more tracks.
        val baseBrowseIds = baseAlbums.mapTo(HashSet()) { it.browseId }
        fun rank(expected: Int?): Comparator<AlbumItem> = compareBy<AlbumItem>(
            { if (isComplete(albumQualityCache[it.browseId], expected)) 1 else 0 },
            {
                val tc = albumQualityCache[it.browseId]?.songCount ?: 0
                if (expected != null && expected > 0) -abs(tc - expected) else 0
            },
            { if (INSTRUMENTAL.containsMatchIn(it.title)) 0 else 1 },
            { albumQualityCache[it.browseId]?.songCount ?: 0 },
        )

        // Group by reconKey so studio and live/acoustic editions of the same title are SEPARATE groups
        // (each keeps its own winner); iTunes lookups below still use the plain norm.
        val albumGroups: Map<String, List<AlbumItem>> = (baseAlbums + foundAlbums).groupBy { reconKey(it.title) }
        val playlistByNorm: Map<String, PlaylistItem> = foundPlaylists.associate { it.first to it.second }
        val winnerByNorm = LinkedHashMap<String, YTItem>()
        for ((nt, group) in albumGroups) {
            // iTunes expected-count / instrumental / playlist lookups key by the PLAIN norm (iTunes has no
            // separate live entry), while the group key `nt` may carry the |live marker.
            val plainNorm = norm(group.first().title)
            val expected = expectedTracks[plainNorm]
            val allowInstrumental = plainNorm in itunesInstrumental
            // Drop instrumental/karaoke copies unless iTunes says this release is genuinely instrumental.
            val nonInstr = group.filterNot { !allowInstrumental && INSTRUMENTAL.containsMatchIn(it.title) }
            val hasBase = group.any { it.browseId in baseBrowseIds }
            // Never empty a group that was already visible (had a base album): fall back to the raw group.
            // A completion-only group that is all-instrumental IS dropped (it was never shown → not worse).
            val pool = if (nonInstr.isNotEmpty()) nonInstr else if (hasBase) group else emptyList()
            val bestAlbum = pool.maxWithOrNull(rank(expected))
            val bestAlbumComplete = bestAlbum != null && isComplete(albumQualityCache[bestAlbum.browseId], expected)
            // Only the studio group claims the community-playlist fallback (a live group keeps its album).
            val playlist = (if (nt.endsWith("|live")) null else playlistByNorm[plainNorm])
                ?.takeIf { allowInstrumental || !INSTRUMENTAL.containsMatchIn(it.title) }
            val winner: YTItem? = when {
                bestAlbum != null && bestAlbumComplete -> bestAlbum   // a good, full album wins
                playlist != null -> playlist                          // else a full community upload
                bestAlbum != null -> bestAlbum                        // else the best (failing) album as fallback
                else -> null                                          // instrumental-only completion → drop
            }
            if (winner != null) winnerByNorm[nt] = winner
        }
        // Community playlists whose title has NO album group at all (album search found nothing).
        for ((nt, pl) in playlistByNorm) {
            if (nt !in winnerByNorm) {
                val allowInstrumental = nt in itunesInstrumental
                if (allowInstrumental || !INSTRUMENTAL.containsMatchIn(pl.title)) winnerByNorm[nt] = pl
            }
        }

        // Assemble preserving the original base order: replace each album with its reconciled winner (first
        // occurrence only → de-dupes base duplicates), keep non-album base items (singles/videos) in place,
        // then append completion winners for titles not present in the base list.
        val emitted = HashSet<String>()
        val result = ArrayList<YTItem>()
        for (item in baseItems) {
            if (item is AlbumItem) {
                val nt = reconKey(item.title)
                if (!emitted.add(nt)) continue
                result.add(winnerByNorm[nt] ?: item) // fallback: original base item (never make it empty)
            } else {
                result.add(item)
            }
        }
        for ((nt, w) in winnerByNorm) {
            if (emitted.add(nt)) result.add(w)
        }
        if (hideExplicit) result.filterExplicit(true) else result
    }

    /**
     * Structural quality of a proper YouTube-Music album, cached per browseId for the session. Fetches the
     * album once (bounded by the caller's semaphore/timeout). Song durations are in SECONDS (innertube
     * parseTime maps "m:ss" → seconds), so a real track is ≥ 90 s at the median; a source full of ~1:03
     * clips (the truncated Lauren Daigle "official") has a low median and fails. `basicOk` excludes the
     * iTunes track-count check so the result is independent of which title it matched.
     */
    private suspend fun fetchAlbumQuality(browseId: String): AlbumQuality {
        albumQualityCache[browseId]?.let { return it }
        val songs = YouTube.album(browseId).getOrNull()?.songs
        val quality = if (songs == null || songs.size < 2) {
            AlbumQuality(songCount = songs?.size ?: 0, basicOk = false)
        } else {
            val durations = songs.mapNotNull { it.duration }.filter { it > 0 }
            val median = if (durations.isEmpty()) 0 else durations.sorted()[durations.size / 2]
            AlbumQuality(songCount = songs.size, basicOk = median >= 90)
        }
        albumQualityCache[browseId] = quality
        return quality
    }

    /**
     * Apply the album quality verdict: needs a long-enough median track AND (when iTunes gives a track
     * count) at least ~60% of that count, so a truncated/half upload is rejected. A null quality (fetch
     * failed/timed out) is treated as NOT complete, but reconciliation still falls back to showing the
     * ungated candidate rather than an empty discography.
     */
    private fun isComplete(quality: AlbumQuality?, expected: Int?): Boolean {
        if (quality == null || !quality.basicOk) return false
        if (expected != null && expected > 0 && quality.songCount < ceil(expected * 0.6).toInt()) return false
        return true
    }

    /** Album quality gate for one [album] against its iTunes [expected] track count. */
    @Suppress("unused")
    private suspend fun isAlbumComplete(album: AlbumItem, expected: Int?): Boolean =
        isComplete(fetchAlbumQuality(album.browseId), expected)

    /**
     * Quality control for a community-uploaded source: reject it if its tracks look truncated/previews
     * (median track under ~90 s), it has too few tracks, it carries NO real duration metadata at all, or it
     * is clearly shorter than the iTunes [expected] track count. Real album tracks average a few minutes,
     * so a source full of 20-60 s clips (or with no durations to verify) is a bad/incomplete upload.
     */
    private suspend fun isSourceComplete(playlistId: String, expected: Int? = null): Boolean {
        val songs = YouTube.playlist(playlistId).getOrNull()?.songs ?: return false
        if (songs.size < 2) return false
        val durations = songs.mapNotNull { it.duration }.filter { it > 0 }
        if (durations.isEmpty()) return false // require REAL durations — an unverifiable source is rejected
        val median = durations.sorted()[durations.size / 2]
        if (median < 90) return false
        if (expected != null && expected > 0 && songs.size < ceil(expected * 0.6).toInt()) return false
        return true
    }

    /**
     * Merge the reconciled discography [primary] (the authority) with whatever the user has already scrolled
     * in [secondary], keeping every primary item and appending only secondary items that are NOT already
     * present (by id) and are NOT a duplicate album of a title primary already covers (by normalized title).
     * This keeps live continuation pages while preventing a base duplicate (e.g. an instrumental copy that
     * the fast base publish showed) from creeping back in.
     */
    private fun mergeDiscography(primary: List<YTItem>, secondary: List<YTItem>): List<YTItem> {
        val ids = primary.mapTo(HashSet()) { it.id }
        val norms = primary.mapTo(HashSet()) { reconKey(it.title) }
        val extras = secondary.filter { item ->
            item.id !in ids &&
                !(item is AlbumItem && reconKey(item.title) in norms)
        }
        return primary + extras
    }

    fun loadMore() {
        viewModelScope.launch {
            val oldItemsPage = itemsPage.value ?: return@launch
            val continuation = oldItemsPage.continuation ?: return@launch
            YouTube
                .artistItemsContinuation(continuation)
                .onSuccess { artistItemsContinuationPage ->
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                    itemsPage.update {
                        ItemsPage(
                            items =
                            (oldItemsPage.items + artistItemsContinuationPage.items)
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs),
                            continuation = artistItemsContinuationPage.continuation,
                        )
                    }
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
