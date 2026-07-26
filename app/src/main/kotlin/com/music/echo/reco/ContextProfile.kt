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
 *    orderedByTaste key — NEVER a filter, NEVER a drop. A candidate whose artist/genre is UNKNOWN
 *    scores exactly 0.0 (strictly neutral): GenreCache only covers known artists, so anything stronger
 *    would collapse the infinite queue onto the library.
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
     *  - unknown artist + genre  -> exactly 0.0 (registry #39/#41: unknown stays neutral and eligible,
     *    so the exploration quota still fills and the infinite queue can never collapse)
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
            candidateGenre != null && profile.genreShare.isNotEmpty() -> OFF_GENRE_PUSH
            else -> 0.0
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
