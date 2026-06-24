package iad1tya.echo.music.reco

import iad1tya.echo.music.db.entities.EventWithSong
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.dislike.DislikeStore
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * On-device "taste model". Builds an explainable affinity profile purely from the local listening
 * history — every play weighted by how recently it happened (recency decay) and how much of the song
 * actually played (a track skipped after a few seconds counts AGAINST it, a full listen counts FOR it),
 * plus likes and "No me gusta". Nothing leaves the phone: fast, private, and easy to reason about.
 *
 * This is the single source of truth that Home, autoplay/radio, search ranking and smart shuffle all
 * reuse, so "more of what you like" means the same thing everywhere in the app.
 *
 * Phase 1 models per-artist affinity (the strongest signal) plus a light genre-lane nudge. Later phases
 * generalise the lanes and add context (time of day, novelty/exploration).
 */
object AffinityEngine {

    /** A play from this many days ago counts half as much as one today. */
    private const val HALF_LIFE_DAYS = 30.0

    /** Don't scan the entire history forever; the most recent N events dominate taste anyway. */
    private const val MAX_EVENTS = 3000

    /** Below this fraction of the song, a play is treated as a skip (negative signal). */
    private const val SKIP_PIVOT = 0.30

    /** Baseline weight given to each onboarding-selected genre so brand-new users still get genre affinity. */
    private const val ONBOARDING_GENRE_SEED = 2.0

    suspend fun buildProfile(
        events: List<EventWithSong>,
        disliked: DislikeStore.Disliked,
        now: Long = System.currentTimeMillis(),
        // Optional "artist name (lowercase) -> primary genre" map (from GenreCache/iTunes). When present,
        // the engine also learns per-genre affinity; when empty it falls back to artist + lane only.
        artistGenres: Map<String, String> = emptyMap(),
        // The user's onboarding genre picks (already mapped to iTunes genre names) — seeded as baseline
        // affinity so the first-run "¿Qué géneros te gustan?" step actually influences recommendations.
        onboardingGenres: List<String> = emptyList(),
    ): TasteProfile {
        val byId = HashMap<String, Double>()
        val byName = HashMap<String, Double>()
        val lane = HashMap<String, Double>()
        val genre = HashMap<String, Double>()
        val ln2 = ln(2.0)
        val nowHour = runCatching {
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        }.getOrDefault(12)

        events.asSequence().take(MAX_EVENTS).forEach { ews ->
            val ev = ews.event
            val song = ews.song
            val playedAt = runCatching {
                ev.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrDefault(now)
            val ageDays = ((now - playedAt) / 86_400_000.0).coerceAtLeast(0.0)
            val decay = exp(-ln2 * ageDays / HALF_LIFE_DAYS)

            // Time-of-day context: what you play around THIS hour counts a bit more, so mornings feel
            // like mornings and nights like nights.
            val hourDist = runCatching { circularHourDistance(ev.timestamp.hour, nowHour) }.getOrDefault(12)
            val timeBoost = if (hourDist <= 3) 1.25 else 1.0

            val durMs = song.song.duration.takeIf { it > 0 }?.let { it.toLong() * 1000L } ?: 0L
            val completion = if (durMs > 0) (ev.playTime.toDouble() / durMs).coerceIn(0.0, 1.0) else 0.5
            // Map completion to a [-?..+1] quality: a skip (completion < SKIP_PIVOT) is negative, a full
            // listen is +1. This turns the play-time we already store into a free "skip" signal.
            val quality = (completion - SKIP_PIVOT) / (1.0 - SKIP_PIVOT)
            var w = decay * quality * timeBoost
            if (song.song.liked) w += decay * 0.5

            song.artists.forEach { a ->
                byId.merge(a.id, w, Double::plus)
                if (a.name.isNotBlank()) {
                    byName.merge(a.name.lowercase(), w, Double::plus)
                    artistGenres[a.name.lowercase()]?.takeIf { it.isNotBlank() }?.let { g ->
                        genre.merge(g, w, Double::plus)
                    }
                }
            }
            GenreLane.laneOf(song.song.title, song.artists.joinToString(" ") { it.name })?.let { l ->
                lane.merge(l, w, Double::plus)
            }
        }

        // Seed onboarding genre picks: strong signal for a new user with no history, a light nudge once
        // real listening history accumulates (history weights grow past the seed over time).
        onboardingGenres.forEach { g -> if (g.isNotBlank()) genre.merge(g, ONBOARDING_GENRE_SEED, Double::plus) }

        val maxW = byId.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val maxG = genre.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        return TasteProfile(byId, byName, lane, genre, artistGenres, disliked, maxW, maxG)
    }

    /** Smallest distance between two hours on a 24h clock (0..12). */
    private fun circularHourDistance(a: Int, b: Int): Int {
        val d = abs(a - b)
        return min(d, 24 - d)
    }
}

/**
 * Immutable snapshot of the user's taste. [score] / [scoreNames] return higher numbers for a better fit;
 * ~0 is neutral/unknown and a disliked item returns [AVOID] so it sinks to the bottom everywhere.
 */
class TasteProfile internal constructor(
    private val artistWeightById: Map<String, Double>,
    private val artistWeightByName: Map<String, Double>,
    private val laneWeight: Map<String, Double>,
    private val genreWeight: Map<String, Double>,
    private val artistGenres: Map<String, String>,
    private val disliked: DislikeStore.Disliked,
    private val maxArtistWeight: Double,
    private val maxGenreWeight: Double,
) {
    /** Score a locally-known song (best signal: we have its real artist ids). */
    fun score(song: Song): Double {
        val e = song.song
        if (e.id in disliked.songs) return AVOID
        if (e.albumId != null && e.albumId in disliked.albums) return AVOID
        if (song.artists.any { it.id in disliked.artists }) return AVOID
        val names = song.artists.map { it.name }
        val raw = song.artists.maxOfOrNull {
            artistWeightById[it.id] ?: artistWeightByName[it.name.lowercase()] ?: 0.0
        } ?: 0.0
        var s = norm(raw)
        s += genreScore(names)
        if (laneFits(e.title, names.joinToString(" "))) s += LANE_BONUS
        if (e.liked) s += LIKE_BONUS
        return s
    }

    /** Score a remote item (YouTube) by artist name + title, when we only have text. */
    fun scoreNames(artistNames: List<String>, title: String?): Double {
        val raw = artistNames.maxOfOrNull { artistWeightByName[it.lowercase()] ?: 0.0 } ?: 0.0
        var s = norm(raw)
        s += genreScore(artistNames)
        if (laneFits(title, artistNames.joinToString(" "))) s += LANE_BONUS
        return s
    }

    /** Affinity for the artist's real genre (Latin, Rock, Hip-Hop...), when known via GenreCache. */
    private fun genreScore(artistNames: List<String>): Double {
        if (genreWeight.isEmpty()) return 0.0
        val g = artistNames.firstNotNullOfOrNull { artistGenres[it.lowercase()] } ?: return 0.0
        val w = genreWeight[g] ?: return 0.0
        return (w / maxGenreWeight).coerceIn(-1.0, 1.0) * GENRE_BONUS
    }

    private fun laneFits(title: String?, artistText: String): Boolean {
        val l = GenreLane.laneOf(title, artistText) ?: return false
        return (laneWeight[l] ?: 0.0) > 0
    }

    private fun norm(w: Double): Double =
        if (maxArtistWeight > 0) (w / maxArtistWeight).coerceIn(-1.0, 1.0) else 0.0

    companion object {
        const val AVOID = -1_000_000.0
        private const val LANE_BONUS = 0.3
        private const val LIKE_BONUS = 0.3
        private const val GENRE_BONUS = 0.4
    }
}
