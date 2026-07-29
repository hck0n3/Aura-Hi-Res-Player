package com.aura.migration.source

import com.aura.migration.model.SourcePlaylist
import com.aura.migration.model.SourceTrack
import com.aura.migration.model.SourceType

/**
 * Contrato unico para toda fuente de playlists.
 *
 * Anadir una plataforma nueva = implementar esta interfaz. Nada mas.
 * El resolver, la cache y la UI no cambian.
 */
interface PlaylistSource {

    val type: SourceType

    /** Nombre legible para la UI. */
    val displayName: String

    /** true si esta fuente sabe manejar esa entrada (URL, URI, ruta de archivo). */
    fun accepts(input: String): Boolean

    /**
     * Playlists disponibles.
     * - Fuentes con OAuth (Tidal, Spotify): la biblioteca del usuario.
     * - Fuentes sin auth (Deezer, archivo): se deriva de [input].
     */
    suspend fun listPlaylists(input: String? = null): List<SourcePlaylist>

    /** Tracks en orden original. Pagina internamente. */
    suspend fun fetchTracks(playlistId: String): List<SourceTrack>
}

/** Errores tipados: la UI necesita distinguirlos para dar mensajes utiles. */
sealed class SourceError(message: String) : Exception(message) {
    class NotAuthenticated(val type: SourceType) :
        SourceError("Necesitas iniciar sesion")
    class PrivatePlaylist :
        SourceError("La playlist es privada. Hazla publica y vuelve a intentar")
    class NotFound :
        SourceError("No se encontro la playlist")
    class RateLimited(val retryAfterMs: Long) :
        SourceError("Demasiadas peticiones, reintentando")
    class Network(cause: String) :
        SourceError("Error de red: $cause")
    class Unsupported(val what: String) :
        SourceError("Formato no soportado: $what")
}
