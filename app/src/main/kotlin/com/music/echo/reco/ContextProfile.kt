package iad1tya.echo.music.reco

/**
 * GENRE-AWARE CONTINUATION — the profile of a FINISHED finite context (playlist / album / EP / single,
 * uniformly), built from the WHOLE pool the user started from so the infinite radio can keep steering
 * toward what that collection actually was (its artists + its real genre mix) instead of drifting off
 * with whatever YouTube relates to the last song. Owner case: a salsa playlist whose continuation
 * wandered into generic Latin/pop.
 *
 * Pure and unit-testable (no Android, no network, no player): the caller passes the pool and ONE
 * [GenreCache] snapshot; genre inference and normalization are delegated to [GenreLane.laneOfTrack] so
 * the profile's genre ids agree with the lane-keeping path (same lane vocabulary, same primary-artist
 * splitting, same Christian collapse).
 *
 * FAIL-NEUTRAL BY DESIGN (registry #39/#41 — a cache-derived signal inherits the cache's bias):
 *  - [Profile.active] gates EVERY consumer: below minimum signal (coverage and known artists both low)
 *    the profile must be treated as absent and behavior stays byte-identical to today.
 *  - [steerTerm] is a BOUNDED additive score nudge ([STEER_MIN]..[STEER_MAX]) for [MusicService]'s
 *    orderedByTaste key — NEVER a filter, NEVER a drop.
 *  - A candidate whose genre is UNKNOWN carries [UNKNOWN_GENRE_PUSH] (+1, ONE rank on an index-dominated
 *    key) — see that constant for why "exactly 0.0" was the hole behind the owner's intermittent
 *    genre mixing, and why +1 cannot collapse the queue onto the library.
 */
object ContextProfile {

    /** Minimum fraction of context tracks with a KNOWN genre for the profile to activate on coverage. */
    const val MIN_COVERAGE = 0.3

    /** Alternative activation: at least this many DISTINCT context artists with a known genre. */
    const val MIN_KNOWN_ARTISTS = 3

    /** Bounds of [steerTerm] — an additive nudge on a positional sort key, same class as the existing
     *  soft "Menos de esto" push (+6) and the capped taste pull (8). Index must stay dominant. */
    const val STEER_MIN = -4.0
    const val STEER_MAX = 6.0

    private const val ARTIST_PULL = -3.0
    private const val GENRE_PULL_SCALE = -4.0
    private const val OFF_GENRE_PUSH = 6.0
    private const val LANGUAGE_TIEBREAK = -1.0

    /**
     * The nudge for a candidate whose genre is UNKNOWN, while a genre-bearing profile is steering.
     *
     * It used to be exactly 0.0, and that WAS the owner's "la cola inteligente me mezcla géneros":
     * an unknown candidate scored 0.0 while an on-genre one earned up to [STEER_MIN] (-4), so a
     * wrong-genre song at relatedness rank 2 outranked a right-genre song at rank 8. Because the cache
     * fills in batches (WiFi-gated, capped) the SAME artist is unknown on one append and known on the
     * next — which is exactly why he heard it as right / wrong / right / wrong instead of consistently
     * wrong.
     *
     * ONE, deliberately: the sort key is `index - pull + soft + jitter + ctx`, where `index` is the
     * relatedness rank and steps by exactly 1.0 per rank. +1 therefore moves an unknown candidate back
     * by ONE rank — enough to lose a tie to a known on-genre candidate at the same rank, and nothing
     * more. Registry #39/#41 still holds by construction:
     *  - it is a NUDGE on a positional key, never a filter and never a drop — an unknown candidate is
     *    still eligible, still played, and a batch made entirely of unknowns comes out in YouTube's
     *    own relatedness order (every item shifted by the same +1);
     *  - it is 6x smaller than [OFF_GENRE_PUSH], so "we don't know" is never treated as "wrong genre";
     *  - a candidate by a CONTEXT ARTIST is matched cache-free and takes [ARTIST_PULL] instead, so the
     *    push can never fire on the collection's own artists;
     *  - it only applies when the profile actually knows some genres ([Profile.genreShare] non-empty)
     *    and passed [Profile.active] — a cache that knows nothing steers nothing;
     *  - and it buys NO exclusion anywhere: see [blocksExploration], which is deliberately gated on a
     *    KNOWN genre so this push can never become a filter by the back door.
     */
    const val UNKNOWN_GENRE_PUSH = 1.0

    /**
     * May a candidate the steer pushed BACK be kept out of the exploration reserve (MusicService's
     * `withExplorationQuota`, ~1 slot in 5 held for an artist the taste profile does not know)?
     * ONLY when we actually KNOW its genre and know it to be off-context — [steerTerm] > 0 with a
     * non-null [candidateGenre].
     *
     * REGISTRY #39/#41, the exact collision this exists to prevent: GenreCache only ever fills with
     * artists already around the user, so an artist the radio surfaces for the FIRST time is unknown
     * BY CONSTRUCTION. When an ABSENT genre also bought a block, `steerTerm > 0` matched EVERY new
     * artist on a cold or partial cache — the fresh partition emptied, the quota no-opped for every
     * automatic continuation, and each batch head was left to the taste pull, i.e. to artists the
     * owner already has. The +1 stays a one-rank sort nudge; only a genre we can NAME excludes.
     */
    fun blocksExploration(steerTerm: Double, candidateGenre: String?): Boolean =
        steerTerm > 0.0 && candidateGenre != null

    /** Spanish for "the profile speaks Spanish" — the only language the genre names can safely prove. */
    const val LANG_ES = "es"

    // Genre-name markers that PROVE Spanish-language music (iTunes: "Salsa y Tropical", "Urbano latino",
    // "Música Mexicana", "Pop Latino", "Latin"...). Deliberately conservative: "rock"/"pop" prove nothing.
    private val ES_GENRE_MARKERS = listOf(
        "latin", "salsa", "tropical", "urbano", "mexican", "reggaeton", "bachata", "merengue",
        "cumbia", "banda", "ranchera", "mariachi", "norteñ", "corrido", "flamenco", "español",
    )

    /** A context track reduced to what the profile needs — keeps build() free of player/media3 types. */
    data class Track(
        val artists: List<String>,
        val title: String?,
        val album: String? = null,
    )

    /**
     * What the finished context WAS:
     *  - [artistSet]: every artist name in the pool, lowercased — cache-free membership signal.
     *  - [genreShare]: normalized genre (lane id) -> fraction of the KNOWN-genre tracks in that genre
     *    (shares sum to 1.0), so a pure salsa playlist yields {"salsa y tropical": 1.0}.
     *  - [coverage]: fraction of ALL pool tracks whose genre is known — the honesty measure that gates
     *    activation (a profile that knows 1 track of 40 must not steer anything).
     *  - [knownArtists]: distinct primary artists with a known genre (alternative activation signal).
     *  - [languageHint]: [LANG_ES] when the MAJORITY of known tracks carry a Spanish-proving genre,
     *    else null. A WEAK tie-breaker ([LANGUAGE_TIEBREAK]) — never a filter.
     */
    data class Profile(
        val artistSet: Set<String>,
        val genreShare: Map<String, Double>,
        val coverage: Double,
        val knownArtists: Int,
        val languageHint: String?,
    ) {
        /** Below this gate EVERY consumer must no-op (behavior byte-identical to no profile at all). */
        val active: Boolean
            get() = coverage >= MIN_COVERAGE || knownArtists >= MIN_KNOWN_ARTISTS
    }

    /**
     * Build the profile of a whole finished context from its [pool] and ONE [GenreCache] snapshot
     * ([genres]). Pure CPU, bounded by the pool size; null for an empty pool (nothing to profile —
     * every consumer then behaves exactly as today).
     */
    fun build(pool: List<Track>, genres: Map<String, String>): Profile? {
        if (pool.isEmpty()) return null
        val artistSet = HashSet<String>()
        val genreCounts = HashMap<String, Int>()
        val knownArtistNames = HashSet<String>()
        var knownTracks = 0
        pool.forEach { track ->
            track.artists.forEach { name ->
                val n = name.trim().lowercase()
                if (n.isNotEmpty()) artistSet.add(n)
            }
            val primary = track.artists.firstOrNull()
            val lane = GenreLane.laneOfTrack(genres, primary, track.title, track.album)
            if (lane != null) {
                knownTracks++
                genreCounts.merge(lane, 1, Int::plus)
                primary?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { knownArtistNames.add(it) }
            }
        }
        val genreShare = if (knownTracks == 0) emptyMap() else
            genreCounts.mapValues { (_, count) -> count.toDouble() / knownTracks }
        val esShare = genreShare.entries.sumOf { (genre, share) -> if (isSpanishGenre(genre)) share else 0.0 }
        return Profile(
            artistSet = artistSet,
            genreShare = genreShare,
            coverage = knownTracks.toDouble() / pool.size,
            knownArtists = knownArtistNames.size,
            languageHint = if (esShare > 0.5) LANG_ES else null,
        )
    }

    /**
     * The BOUNDED additive steering term for one radio candidate, added to orderedByTaste's positional
     * sort key (lower = earlier). [candidateGenre] is the candidate's normalized lane (from
     * [GenreLane.laneOfTrack] with the same snapshot vocabulary as [build]); null = unknown.
     *
     *  - context artist          -> [ARTIST_PULL] (a few spots earlier — strongest, cache-free signal)
     *  - context genre           -> [GENRE_PULL_SCALE] * that genre's share (dominant genre pulls hardest)
     *  - KNOWN off-context genre -> [OFF_GENRE_PUSH] (a few spots later — the anti-drift push; NEVER a drop)
     *  - UNKNOWN genre           -> [UNKNOWN_GENRE_PUSH] (exactly ONE rank later — a tie-break against
     *    known on-genre candidates, never a filter; see that constant for the #39/#41 argument)
     *  - profile with no genres at all -> 0.0 (nothing known, nothing steered)
     *
     * The weak language tie-break ([LANGUAGE_TIEBREAK]) only ever SOFTENS/pulls (a Spanish-proving
     * candidate genre under a Spanish context), never punishes. Result clamped to [STEER_MIN]..[STEER_MAX].
     * Inactive/null profiles must be handled by the caller (no call at all) — but an inactive profile
     * passed anyway steers nothing.
     */
    fun steerTerm(profile: Profile, candidateArtists: List<String>, candidateGenre: String?): Double {
        if (!profile.active) return 0.0
        val artistMatch = candidateArtists.any { it.trim().lowercase() in profile.artistSet }
        val share = candidateGenre?.let { profile.genreShare[it] }
        var term = when {
            artistMatch -> ARTIST_PULL
            share != null -> GENRE_PULL_SCALE * share
            profile.genreShare.isEmpty() -> 0.0
            candidateGenre != null -> OFF_GENRE_PUSH
            // Genre unknown, not a context artist, and the profile DOES know what the collection was.
            else -> UNKNOWN_GENRE_PUSH
        }
        if (profile.languageHint == LANG_ES && candidateGenre != null && isSpanishGenre(candidateGenre)) {
            term += LANGUAGE_TIEBREAK
        }
        return term.coerceIn(STEER_MIN, STEER_MAX)
    }

    /** True when the genre NAME itself proves Spanish-language music (conservative marker list). */
    fun isSpanishGenre(normalizedGenre: String): Boolean {
        val g = normalizedGenre.lowercase()
        return ES_GENRE_MARKERS.any { g.contains(it) }
    }
}
