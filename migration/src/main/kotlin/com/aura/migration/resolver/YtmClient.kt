package com.aura.migration.resolver

import com.aura.migration.model.YtmCandidate

/**
 * Unico punto de contacto con YouTube Music.
 *
 * Se define como interfaz a proposito: el modulo `migration` NO depende de
 * `innertube`. Eso permite testear el resolver con un fake y cambiar de
 * backend sin tocar nada mas.
 *
 * Implementalo en tu app envolviendo tu cliente de InnerTube existente.
 * Ver YtmClientInnerTube.kt.template
 */
interface YtmClient {

    /** Busqueda con filtro "solo canciones". Es la ruta principal. */
    suspend fun searchSongs(query: String, limit: Int = 8): List<YtmCandidate>

    /** Busqueda sin filtro. Ultimo recurso: incluye videos, se penaliza. */
    suspend fun searchAll(query: String, limit: Int = 8): List<YtmCandidate>

    /** Crea la playlist destino y devuelve su id. */
    suspend fun createPlaylist(name: String, description: String? = null): String

    /** Anade tracks en lote. Respeta el orden de [videoIds]. */
    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>)
}
