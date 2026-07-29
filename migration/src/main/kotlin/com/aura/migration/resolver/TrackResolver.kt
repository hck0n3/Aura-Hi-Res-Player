package com.aura.migration.resolver

import com.aura.migration.db.MatchCache
import com.aura.migration.db.cacheKey
import com.aura.migration.model.MatchResult
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate

/**
 * El corazon del sistema. Convierte un SourceTrack en un videoId de YouTube Music.
 *
 * Se escribe UNA vez y sirve para Tidal, Deezer, Apple, Spotify y archivo.
 * Aqui es donde se gana o se pierde la calidad de la migracion.
 */
class TrackResolver(
    private val ytm: YtmClient,
    private val cache: MatchCache
) {

    suspend fun resolve(track: SourceTrack): MatchResult {

        // 1. Cache: instantaneo, sin red. Las correcciones manuales del
        //    usuario viven aqui y tienen prioridad absoluta.
        cache.get(track.cacheKey())?.let { hit ->
            return MatchResult.Matched(
                source = track,
                videoId = hit.videoId,
                confidence = hit.confidence,
                fromCache = true
            )
        }

        val pool = LinkedHashMap<String, YtmCandidate>()
        fun collect(list: List<YtmCandidate>) =
            list.forEach { pool.putIfAbsent(it.videoId, it) }

        // 2. Query principal: titulo + artista, filtro solo-canciones.
        collect(ytm.searchSongs("${track.title} ${track.primaryArtist}"))

        // 3. Fallback A: version normalizada (quita ruido de titulo).
        if (bestScore(track, pool.values) < Scorer.ACCEPT) {
            val q = "${TextNormalizer.normalize(track.title)} " +
                    TextNormalizer.normalizeArtist(track.primaryArtist)
            collect(ytm.searchSongs(q.trim()))
        }

        // 4. Fallback B: incluir album. Desambigua entre versiones.
        if (bestScore(track, pool.values) < Scorer.ACCEPT && !track.album.isNullOrBlank()) {
            collect(ytm.searchSongs("${track.title} ${track.album}"))
        }

        // 5. Fallback C: sin filtro. Solo si no hay NADA. Ya viene penalizado
        //    por isSong=false, asi que rara vez supera ACCEPT.
        if (pool.isEmpty()) {
            collect(ytm.searchAll("${track.title} ${track.primaryArtist}"))
        }

        return decide(track, pool.values.toList())
    }

    private fun bestScore(track: SourceTrack, cands: Collection<YtmCandidate>): Float =
        cands.maxOfOrNull { Scorer.score(track, it) } ?: Float.NEGATIVE_INFINITY

    private fun decide(track: SourceTrack, cands: List<YtmCandidate>): MatchResult {
        if (cands.isEmpty()) return MatchResult.NotFound(track)

        val ranked = cands
            .map { it to Scorer.score(track, it) }
            .sortedByDescending { it.second }

        val (best, bestScore) = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.second ?: Float.NEGATIVE_INFINITY
        val margin = if (runnerUp == Float.NEGATIVE_INFINITY) Float.MAX_VALUE
                     else bestScore - runnerUp

        return when {
            bestScore >= Scorer.ACCEPT && margin >= Scorer.MARGIN ->
                MatchResult.Matched(
                    source = track,
                    videoId = best.videoId,
                    confidence = (bestScore / 100f).coerceIn(0f, 1f)
                )

            bestScore >= Scorer.REVIEW ->
                MatchResult.Ambiguous(track, ranked.take(4).map { it.first })

            else ->
                MatchResult.NotFound(track, "mejor puntuacion ${bestScore.toInt()}")
        }
    }
}
