package com.aura.migration

import com.aura.migration.db.MatchCache
import com.aura.migration.db.cacheKey
import com.aura.migration.model.*
import com.aura.migration.resolver.TrackResolver
import com.aura.migration.resolver.YtmClient
import com.aura.migration.source.PlaylistSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Orquestador. La UNICA clase que la app necesita conocer.
 *
 * Uso tipico:
 *   val engine = MigrationEngine(ytm, resolver, cache)
 *   engine.import(deezerSource, playlistId).collect { render(it) }
 */
class MigrationEngine(
    private val ytm: YtmClient,
    private val resolver: TrackResolver,
    private val cache: MatchCache
) {

    sealed class Progress {
        data class Started(val playlistName: String, val total: Int) : Progress()
        data class Track(val done: Int, val total: Int, val current: String) : Progress()
        data class Finished(val report: ImportReport, val ytmPlaylistId: String?) : Progress()
        data class Failed(val error: Throwable) : Progress()
    }

    /**
     * Importa una playlist emitiendo progreso.
     *
     * PRINCIPIO INNEGOCIABLE: una cancion que falla NUNCA tumba la importacion
     * entera. Los errores se acumulan en el informe y el proceso sigue.
     */
    fun import(
        source: PlaylistSource,
        playlistId: String,
        playlistName: String? = null,
        createInYtm: Boolean = true
    ): Flow<Progress> = flow {
        try {
            val tracks = source.fetchTracks(playlistId)
            val name = playlistName ?: "Importada de ${source.displayName}"
            emit(Progress.Started(name, tracks.size))

            val matched = mutableListOf<MatchResult.Matched>()
            val ambiguous = mutableListOf<MatchResult.Ambiguous>()
            val notFound = mutableListOf<MatchResult.NotFound>()

            tracks.forEachIndexed { i, track ->
                emit(Progress.Track(i + 1, tracks.size, "${track.primaryArtist} - ${track.title}"))
                val result = try {
                    resolver.resolve(track)
                } catch (e: Exception) {
                    MatchResult.NotFound(track, e.message ?: "error")
                }
                when (result) {
                    is MatchResult.Matched -> {
                        matched += result
                        if (!result.fromCache) {
                            cache.put(track.cacheKey(), result.videoId, result.confidence)
                        }
                    }
                    is MatchResult.Ambiguous -> ambiguous += result
                    is MatchResult.NotFound  -> notFound += result
                }
            }

            val report = ImportReport(name, matched, ambiguous, notFound)

            val ytmId = if (createInYtm && matched.isNotEmpty()) {
                val id = ytm.createPlaylist(name, "Importada desde ${source.displayName}")
                // orden original preservado
                ytm.addToPlaylist(id, matched.sortedBy { it.source.sourcePosition }.map { it.videoId })
                id
            } else null

            emit(Progress.Finished(report, ytmId))

        } catch (e: Exception) {
            emit(Progress.Failed(e))
        }
    }

    /**
     * El usuario resolvio un ambiguo. Se guarda como verdad permanente:
     * ninguna ejecucion automatica futura lo va a sobrescribir.
     */
    suspend fun confirmMatch(track: SourceTrack, videoId: String) {
        cache.confirmByUser(track.cacheKey(), videoId)
    }

    /**
     * Crea la playlist destino bajo demanda. Necesario cuando la importacion dio 0 coincidencias
     * automaticas (asi que [import] no la creo, por diseno: nunca una lista vacia) y el usuario acaba
     * de resolver un ambiguo — recien ahora hay algo que anadir. Devuelve el id de la playlist YTM.
     */
    suspend fun createPlaylist(name: String, description: String? = null): String =
        ytm.createPlaylist(name, description)

    /** Anade a una playlist ya creada los ambiguos que el usuario resolvio. */
    suspend fun appendResolved(ytmPlaylistId: String, videoIds: List<String>) {
        if (videoIds.isNotEmpty()) ytm.addToPlaylist(ytmPlaylistId, videoIds)
    }
}
