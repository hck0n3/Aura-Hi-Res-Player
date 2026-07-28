

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
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Reconciliation contract of the artist discography (pure: no network, no Android, unit-testable —
// see DiscographyKeysTest). Kept at file top level, like BackupGate.kt, so the rules that decide which
// releases are "the same album" and which are "already present" can be verified without a device.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

// Instrumental / karaoke / backing-track uploads: a duplicate of a real album with the vocals
// stripped. Dropped during reconciliation UNLESS the artist's genuine iTunes release is itself
// instrumental (see itunesInstrumental in buildCompleteDiscography).
private val INSTRUMENTAL = Regex("(?i)\\b(instrumental|karaoke|backing track|playback)\\b")

// Live / acoustic / unplugged editions are DIFFERENT recordings of the same title (common for the
// app's worship/Latin audience: studio + "En Vivo"). They must NOT collapse into the studio album
// during dedupe — unlike deluxe/remaster/instrumental, which are the same recording and do collapse.
private val LIVE_ACOUSTIC =
    Regex("(?i)\\b(en\\s*vivo|en\\s*directo|live|directo|unplugged|ac[uú]stico|acoustic|en\\s*concierto)\\b")

/** Suffix [reconKey] appends to a live/acoustic edition so it never collapses into the studio one. */
const val LIVE_MARKER = "|live"

/**
 * Structural quality of one YouTube-Music album (song count + whether the median track is long enough to
 * be a real track, not a truncated preview). Kept without the iTunes track-count check so it can be cached
 * independent of which title it matched. Only ever built from a probe that actually RETURNED data — a
 * failed probe is represented by `null`, never by an instance (see fetchAlbumQuality).
 */
data class AlbumQuality(val songCount: Int, val basicOk: Boolean)

/**
 * Dedup key: normalized title PLUS a marker so live/acoustic editions stay distinct from the studio
 * release (same title, different recording). normalizeTitle strips "en vivo/live", so we re-add it.
 *
 * THIS IS THE ONE KEY the whole pipeline must use — have/missing, grouping, assembly and merge. Mixing it
 * with the flat normalized title is what let a live edition mask a studio album of the same name, so the
 * studio release was never searched (see buildCompleteDiscography).
 */
fun reconKey(rawTitle: String): String {
    val base = iTunesDiscography.normalizeTitle(rawTitle)
    return if (LIVE_ACOUSTIC.containsMatchIn(rawTitle)) "$base$LIVE_MARKER" else base
}

/**
 * The plain (marker-free) normalized title behind a [reconKey] — iTunes has no separate live entry, so
 * every iTunes-side lookup (expected/floor track counts, instrumental) keys by this.
 */
fun plainKey(key: String): String = key.removeSuffix(LIVE_MARKER)

/**
 * Apply the album quality verdict: needs a long-enough median track AND (when iTunes gives a track count)
 * at least ~60% of [floor], so a truncated/half upload is rejected. A null quality (the probe never ran or
 * failed) is NOT a verdict and is never "complete"; reconciliation still falls back to showing the ungated
 * candidate rather than an empty discography.
 */
fun isComplete(quality: AlbumQuality?, floor: Int?): Boolean {
    if (quality == null || !quality.basicOk) return false
    if (floor != null && floor > 0 && quality.songCount < ceil(floor * 0.6).toInt()) return false
    return true
}

/**
 * Does a base (already-listed) album count as ALREADY PRESENT, i.e. must NOT be re-searched?
 *
 * Only a probe that actually returned data and then FAILED the gate makes an album re-searchable. A `null`
 * quality means the probe never ran (past the fetch cap) or could not complete (YouTube throttling /
 * timeout) — that is INCONCLUSIVE, and the conservative answer is "assume present". Treating an
 * inconclusive probe as "missing" (0.6.97) meant a throttled run filled `missing` with titles the user
 * already sees and burned the search budget on them, so the genuinely absent albums were never looked up.
 * Degrading to "no supplementation" is always better than supplementing the wrong things.
 */
fun countsAsHave(quality: AlbumQuality?, floor: Int?): Boolean =
    quality == null || isComplete(quality, floor)

/**
 * Acceptance floor per normalized title from the merged iTunes store results: how many tracks the SMALLEST
 * LEGITIMATE edition of that release has. Feeds [isComplete] / [countsAsHave].
 *
 * The "multi-track only" rule is the whole point. [iTunesDiscography.normalizeTitle] strips the "- Single"
 * suffix, so iTunes' 1-track "Look Up Child - Single" lands on the SAME key as the 13-track album
 * "Look Up Child". A plain MIN therefore pinned the floor at 1, which collapsed the gate
 * (`songCount < ceil(1 * 0.6)` only rejects an album with ZERO songs): a 3-of-13 truncated upload passed
 * [isComplete], counted as already-present, was never re-completed, and outranked a complete community
 * upload during reconciliation. Only editions with more than one track may set the floor.
 *
 * A title iTunes knows ONLY as a 1-track release really is a single, so it keeps its own count as the floor.
 * Unknown counts (0) never become the floor; a title with nothing but unknowns maps to 0 = "do not gate".
 *
 * "More than one track" is NOT enough on its own, because the same suffix strip also collapses "- EP":
 * a 4-track "X - EP" shares the key of the 12-track album "X" and pins the floor at 4, whose gate is
 * `ceil(4 * 0.6) = 3` — so a 3-of-12 TRUNCATED upload passes [isComplete], is marked present by
 * [countsAsHave] and can win Phase D, replacing the real album. The MAX-based rule this replaced demanded 8.
 *
 * Hence the ratio rule: an edition with LESS THAN HALF the tracks of the fullest known edition is a
 * mini/EP/sampler pressing, not a legitimate short edition of the album, and may not set the gate. Half is
 * chosen because it separates the two cases cleanly in real catalogs — a standard edition is never under
 * half of its own deluxe (12 vs 24, 13 vs 24: kept, so the MIN-across-stores rule that protects standard
 * editions survives), while an EP/mini is (4 vs 12, 2 vs 12: ignored). A genuinely short album still keeps a
 * real floor: it is the fullest edition of itself, so it always passes its own ratio test.
 *
 * The remaining error is deliberately one-sided. A floor that is too HIGH only wastes one search — the
 * release stays in `missing`, is looked up again, and Phase D still falls back to the best album it has. A
 * floor that is too LOW admits a truncated upload as the winner, which is a visibly broken album.
 */
fun buildFloorTracks(itunesMeta: List<Pair<String, Int>>): Map<String, Int> =
    itunesMeta
        .groupBy { iTunesDiscography.normalizeTitle(it.first) }
        .mapValues { (_, v) ->
            val editions = v.mapNotNull { (_, count) -> count.takeIf { it > 1 } }
            val fullest = editions.maxOrNull() ?: 0
            editions.filter { it * 2 >= fullest }.minOrNull() ?: v.maxOf { it.second }
        }
        .filterKeys { it.isNotBlank() }

/**
 * Ranking reference per normalized title: the FULLEST edition iTunes knows (MAX across stores). Used only to
 * rank candidates ("track count closest to expected wins") so a deluxe upload still beats a truncated one —
 * never as an acceptance threshold, which is [buildFloorTracks]' job.
 */
fun buildExpectedTracks(itunesMeta: List<Pair<String, Int>>): Map<String, Int> =
    itunesMeta
        .groupBy { iTunesDiscography.normalizeTitle(it.first) }
        .mapValues { (_, v) -> v.maxOf { it.second } }
        .filterKeys { it.isNotBlank() }

/**
 * Normalized titles whose GENUINE iTunes release is itself instrumental (e.g. an album actually called
 * "Instrumental Worship"). For those, an instrumental YouTube candidate is the real record, not a karaoke
 * duplicate, so the anti-instrumental guard must stand down.
 *
 * Keyed by the title AFTER normalization and matched AFTER normalization. Matching the RAW title made the
 * guard dead in its most common form: [iTunesDiscography.normalizeTitle] drops parentheticals, so
 * "Lenguaje de Amor (Instrumental)" produced the key of the STUDIO album and whitelisted karaoke uploads for
 * it. A parenthesised "(Instrumental)" is an edition marker, not an instrumental release.
 */
fun buildInstrumentalTitles(itunesMeta: List<Pair<String, Int>>): Set<String> =
    itunesMeta
        .mapNotNull { (raw, _) ->
            iTunesDiscography.normalizeTitle(raw)
                .takeIf { it.isNotBlank() && INSTRUMENTAL.containsMatchIn(it) }
        }
        .toSet()

/**
 * Does [candidateTitle] have the same "liveness" as the release the search asked for ([requestedKey])?
 *
 * Every YouTube lookup targets the marker-free normalized title, so a search for a live edition happily
 * matches the STUDIO album of the same name. The album branch used to accept that hit; the key it was filed
 * under was then discarded and the item regrouped by `reconKey(item.title)`, so the studio album landed in
 * the studio group, no live group was ever created — and because the album search had "succeeded", the
 * community-playlist fallback (which does check liveness) never ran. The live edition stayed missing and the
 * search was wasted. Both branches now gate on this.
 */
fun matchesLiveness(candidateTitle: String, requestedKey: String): Boolean =
    reconKey(candidateTitle).endsWith(LIVE_MARKER) == requestedKey.endsWith(LIVE_MARKER)

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

    companion object {
        private const val TAG = "DISCOGRAPHY"

        // Per-artist cache of the completed (iTunes-driven) discography for this app session, so
        // re-opening the same artist's albums shows the full list instantly instead of re-fetching.
        private val completedCache =
            java.util.concurrent.ConcurrentHashMap<String, List<YTItem>>()

        // Per-browseId cache of album quality for the session (so re-checking the same album — base or a
        // completion candidate — never re-hits the network). Bounded like completedCache. A FAILED probe is
        // never stored here: absence means "unknown", which countsAsHave reads as "assume present".
        private val albumQualityCache =
            java.util.concurrent.ConcurrentHashMap<String, AlbumQuality>()
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
        // Two views of the SAME iTunes track counts, per normalized title (built by the pure, unit-tested
        // helpers at the top of this file — see DiscographyKeysTest):
        //  • expectedTracks = MAX across stores — the fullest known edition. Used ONLY to RANK candidates
        //    ("track count closest to expected wins"), so a deluxe edition still beats a truncated upload.
        //  • floorTracks = smallest MULTI-TRACK edition across stores — the SMALLEST legitimate release.
        //    Used as the ≥60% ACCEPTANCE floor. Using the MAX as the floor rejected a perfectly good
        //    standard edition whenever any single store listed a deluxe/expanded one (12 real tracks vs a
        //    ceil(24*0.6)=15 floor); using a plain MIN let iTunes' 1-track "… - Single" entry (same
        //    normalized key as the album) collapse the floor to 1 and disable the gate entirely.
        val expectedTracks: Map<String, Int> = buildExpectedTracks(itunesMeta)
        val floorTracks: Map<String, Int> = buildFloorTracks(itunesMeta)
        // Norm-titles whose GENUINE iTunes release is itself instrumental — for these we must NOT drop
        // instrumental YouTube candidates (a real instrumental album is not a karaoke duplicate). Matched
        // AFTER normalization, so a parenthesised "(Instrumental)" edition cannot whitelist the studio title.
        val itunesInstrumental: Set<String> = buildInstrumentalTitles(itunesMeta)
        val itunes = itunesMeta.map { it.first }.distinct()

        // 6 concurrent network calls (album fetches + searches): more can itself trip YouTube throttling,
        // which then times out individual lookups and silently drops real albums. Shared across all phases.
        val semaphore = Semaphore(6)

        // Phase A — quality-gate the BASE albums (bounded, cached). A base album whose probe RETURNED data
        // and then failed the gate is left OUT of `have`, so a truncated/preview "official" upload becomes
        // eligible for re-completion and can be replaced by a full album or community upload. Capped at 60
        // fetches so a huge catalog can't turn this into a long network burst (battery/heat).
        val baseUnique = baseAlbums.distinctBy { it.browseId }
        val probedBase = baseUnique.take(60)
        if (baseUnique.size > probedBase.size) {
            // No silent caps: say out loud that the rest were NOT probed (they are trusted as present).
            Timber.tag(TAG).i(
                "quality probe cap for '%s': probed %d of %d base albums; the remaining %d are trusted as present",
                artistName, probedBase.size, baseUnique.size, baseUnique.size - probedBase.size,
            )
        }
        probedBase.map { a ->
            async { semaphore.withPermit { withTimeoutOrNull(12000L) { fetchAlbumQuality(a.browseId) } } }
        }.awaitAll()
        // `have` is keyed by reconKey — the SAME key grouping/assembly/merge use below. Keying it by the flat
        // norm (the 0.6.98 asymmetry) let a live edition on the YouTube page ("Lenguaje de Amor (En Vivo)")
        // share the studio album's key, so the STUDIO release counted as already-present and was never
        // searched — neither as an album nor as a community playlist. countsAsHave() additionally treats an
        // unprobed / failed probe as present, so throttling degrades to "no supplementation", not "wrong
        // supplementation" (see countsAsHave). Real duplicates still collapse: identical titles share a key.
        val have = baseAlbums
            .filter { countsAsHave(albumQualityCache[it.browseId], floorTracks[norm(it.title)]) }
            .map { reconKey(it.title) }
            .toSet()
        val reSearchable = baseUnique.size - baseUnique.count {
            countsAsHave(albumQualityCache[it.browseId], floorTracks[norm(it.title)])
        }
        if (reSearchable > 0) {
            Timber.tag(TAG).i(
                "'%s': %d base album(s) probed as truncated → eligible for re-completion", artistName, reSearchable,
            )
        }

        // Don't mix EPs/Singles into the Albums list (iTunes/Apple keep them separate). iTunes marks them
        // as "Title - EP" / "Title - Single".
        val epOrSingle = Regex("(?i)[-–—]\\s*(ep|single)\\b|\\((?:ep|single)\\)")
        val missingAll = itunes
            // Symmetric with `have`: compare reconKey to reconKey. A live iTunes entry no longer hides the
            // studio release of the same name (and vice versa) — each is looked up on its own.
            .filter { norm(it).isNotBlank() && reconKey(it) !in have }
            // Complete the WHOLE iTunes/Apple catalog for the main (Albums) discography — INCLUDING EPs and
            // singles. iTunes returns most of an artist's releases as "… - Single"/"… - EP" (especially
            // Latin/regional catalogs) and YouTube Music usually omits them; the old EP/Single exclusion
            // (regression 866b4c8) left singles-heavy catalogs with nothing to add → nothing published
            // ("ya estaba y no lo hace"). A dedicated Singles/EP see-all, when it exists, still narrows to
            // EP/Single only. This restores the iTunes-authoritative completion the owner remembers.
            .let { list -> if (isSinglesSection) list.filter { epOrSingle.containsMatchIn(it) } else list }
            // Also reconKey: a studio and a live edition of the same name are two DIFFERENT releases and both
            // deserve a lookup. A true duplicate (same title twice across stores) still collapses here.
            .distinctBy { reconKey(it) }
        val missing = missingAll.take(80)
        if (missingAll.size > missing.size) {
            // No silent caps: name the releases we are NOT looking up (first 20, then a count) so a gap in
            // the published discography is always explainable from the log instead of just disappearing.
            val dropped = missingAll.drop(80)
            Timber.tag(TAG).w(
                "completion cap for '%s': searching %d of %d missing releases; NOT looked up this run: %s%s",
                artistName, missing.size, missingAll.size,
                dropped.take(20).joinToString(", "),
                if (dropped.size > 20) " (+${dropped.size - 20} more)" else "",
            )
        }
        Timber.tag(TAG).i(
            "'%s': %d base albums, %d already present, %d to look up", artistName, baseAlbums.size, have.size, missing.size,
        )

        // Phase B — resolve each missing release on YouTube (album first, else community playlist).
        val found = missing.map { mt ->
            async {
                semaphore.withPermit {
                  // Bound each lookup so one slow YouTube search can't stall the whole completion.
                  // 12s (was 8s) gives a throttled search enough time to return before being dropped.
                  withTimeoutOrNull(12000L) {
                    val target = norm(mt)
                    // Key every hit by reconKey, like the rest of the pipeline, so a live edition's result
                    // can never be filed under (and claimed by) the studio release of the same name.
                    val key = reconKey(mt)
                    val allowInstrumental = target in itunesInstrumental
                    val album = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_ALBUM)
                        .getOrNull()?.items?.filterIsInstance<AlbumItem>()
                        ?.firstOrNull {
                            credited(it) && titleMatch(it.title, target) &&
                                // Same "liveness" as the release we searched for. `target` is the
                                // marker-FREE normalized title, so without this a search for
                                // "X (En Vivo)" matched the STUDIO album "X": the hit was filed under the
                                // live key, that key was then dropped, Phase D regrouped it by
                                // reconKey(item.title) into the STUDIO group — and since the album search
                                // had "succeeded", the community-playlist fallback below (which does check
                                // liveness) never ran. The live edition stayed missing and the lookup was
                                // wasted. A studio request still matches a studio album: both keys are
                                // marker-free.
                                matchesLiveness(it.title, key) &&
                                // Skip a karaoke/instrumental upload here: reconciliation drops an
                                // instrumental-only completion group (Phase D), so accepting one would
                                // consume this slot and leave the title with NOTHING — no album AND no
                                // community playlist, because the fallback below never got to run.
                                (allowInstrumental || !INSTRUMENTAL.containsMatchIn(it.title))
                        }
                    if (album != null) {
                        // Quality is gated later during reconciliation (Phase C/D), so a truncated album
                        // hit here can still lose to a full community upload for the same title.
                        key to (album as YTItem)
                    } else {
                        val pl = YouTube.search("$artistName $mt", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
                            .getOrNull()?.items?.filterIsInstance<PlaylistItem>()
                            ?.firstOrNull { p ->
                                val t = norm(p.title)
                                (t == target || (target.length >= 4 && (t.contains(target) || target.contains(t)))) &&
                                    (p.title.contains(artistName, ignoreCase = true) ||
                                        p.author?.name?.contains(artistName, ignoreCase = true) == true) &&
                                    // Same "liveness" as the release we searched for, so a studio upload is
                                    // never filed as the live edition (or the other way round).
                                    matchesLiveness(p.title, key)
                            }

                        // Quality control: only add a community playlist if its tracks are NOT truncated
                        // previews (the Lauren Daigle problem — list looks complete but songs play for
                        // seconds) and it has roughly the iTunes track count. Full, playable audio only.
                        // The floor is the SMALLEST edition iTunes knows, not the largest (see floorTracks).
                        if (pl != null && isSourceComplete(pl.id, floorTracks[target])) {
                            key to (pl as YTItem)
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
        // `floor` gates acceptance (smallest legit edition), `expected` ranks by closeness (fullest edition).
        fun rank(floor: Int?, expected: Int?): Comparator<AlbumItem> = compareBy<AlbumItem>(
            { if (isComplete(albumQualityCache[it.browseId], floor)) 1 else 0 },
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
        // Keyed by reconKey (same space as albumGroups / winnerByNorm), so the studio group can only claim a
        // studio community upload and a live group can only claim a live one.
        val playlistByKey: Map<String, PlaylistItem> = foundPlaylists.associate { it.first to it.second }
        val winnerByNorm = LinkedHashMap<String, YTItem>()
        for ((nt, group) in albumGroups) {
            // iTunes expected-count / instrumental lookups key by the PLAIN norm (iTunes has no separate
            // live entry), while the group key `nt` may carry the |live marker.
            val plainNorm = norm(group.first().title)
            val expected = expectedTracks[plainNorm]
            val floor = floorTracks[plainNorm]
            val allowInstrumental = plainNorm in itunesInstrumental
            // Drop instrumental/karaoke copies unless iTunes says this release is genuinely instrumental.
            val nonInstr = group.filterNot { !allowInstrumental && INSTRUMENTAL.containsMatchIn(it.title) }
            val hasBase = group.any { it.browseId in baseBrowseIds }
            // Never empty a group that was already visible (had a base album): fall back to the raw group.
            // A completion-only group that is all-instrumental IS dropped (it was never shown → not worse),
            // and Phase B no longer accepts an instrumental album hit, so the community-playlist fallback
            // below still gets its chance for that title instead of the title vanishing entirely.
            val pool = if (nonInstr.isNotEmpty()) nonInstr else if (hasBase) group else emptyList()
            val bestAlbum = pool.maxWithOrNull(rank(floor, expected))
            val bestAlbumComplete = bestAlbum != null && isComplete(albumQualityCache[bestAlbum.browseId], floor)
            val playlist = playlistByKey[nt]
                ?.takeIf { allowInstrumental || !INSTRUMENTAL.containsMatchIn(it.title) }
            val winner: YTItem? = when {
                bestAlbum != null && bestAlbumComplete -> bestAlbum   // a good, full album wins
                playlist != null -> playlist                          // else a full community upload
                bestAlbum != null -> bestAlbum                        // else the best (failing) album as fallback
                else -> null                                          // instrumental-only completion → drop
            }
            if (winner != null) winnerByNorm[nt] = winner
        }
        // Community playlists whose title has NO album group at all (album search found nothing, or the only
        // album hit was a karaoke upload). This is the path that puts "Lenguaje de Amor" back on the screen
        // when YouTube has no proper album for it — exactly the behaviour the owner remembers.
        for ((nt, pl) in playlistByKey) {
            if (nt !in winnerByNorm) {
                // itunesInstrumental holds PLAIN norms, so strip the |live marker before looking it up.
                val allowInstrumental = plainKey(nt) in itunesInstrumental
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
     *
     * Returns null when the probe itself could not complete (YouTube throttled/failed the fetch). That is an
     * INCONCLUSIVE result, not a verdict, so it is deliberately NOT written to the cache — caching it would
     * turn one throttled request into a session-long "this album is truncated" lie for every later phase.
     */
    private suspend fun fetchAlbumQuality(browseId: String): AlbumQuality? {
        albumQualityCache[browseId]?.let { return it }
        val songs = YouTube.album(browseId).getOrNull()?.songs ?: return null
        val quality = if (songs.size < 2) {
            AlbumQuality(songCount = songs.size, basicOk = false)
        } else {
            val durations = songs.mapNotNull { it.duration }.filter { it > 0 }
            val median = if (durations.isEmpty()) 0 else durations.sorted()[durations.size / 2]
            AlbumQuality(songCount = songs.size, basicOk = median >= 90)
        }
        albumQualityCache[browseId] = quality
        return quality
    }

    /** Album quality gate for one [album] against its iTunes [floor] track count. */
    @Suppress("unused")
    private suspend fun isAlbumComplete(album: AlbumItem, floor: Int?): Boolean =
        isComplete(fetchAlbumQuality(album.browseId), floor)

    /**
     * Quality control for a community-uploaded source: reject it if its tracks look truncated/previews
     * (median track under ~90 s), it has too few tracks, it carries NO real duration metadata at all, or it
     * is clearly shorter than the iTunes [floor] track count (the SMALLEST edition iTunes lists, so a
     * standard edition is not rejected for being shorter than some store's deluxe). Real album tracks
     * average a few minutes, so a source full of 20-60 s clips (or with no durations to verify) is bad.
     *
     * Note this stays strict on failure: an unfetchable/unverifiable playlist is rejected, because here the
     * decision is whether to ADD something new — the conservative answer is "don't".
     */
    private suspend fun isSourceComplete(playlistId: String, floor: Int? = null): Boolean {
        val songs = YouTube.playlist(playlistId).getOrNull()?.songs ?: return false
        if (songs.size < 2) return false
        val durations = songs.mapNotNull { it.duration }.filter { it > 0 }
        if (durations.isEmpty()) return false // require REAL durations — an unverifiable source is rejected
        val median = durations.sorted()[durations.size / 2]
        if (median < 90) return false
        if (floor != null && floor > 0 && songs.size < ceil(floor * 0.6).toInt()) return false
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
