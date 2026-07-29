package com.aura.migration.model

/**
 * Una cancion tal y como viene de la plataforma de origen.
 * Todas las fuentes (Tidal, Deezer, Apple, Spotify, archivo) producen esto.
 */
data class SourceTrack(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    /** ISRC: identifica la GRABACION exacta. No sirve para buscar en YTM,
     *  pero es la clave de cache perfecta. Guardalo siempre que exista. */
    val isrc: String? = null,
    val explicit: Boolean? = null,
    val year: Int? = null,
    val sourcePosition: Int = 0
) {
    val primaryArtist: String get() = artists.firstOrNull().orEmpty()
}

data class SourcePlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val trackCount: Int = 0,
    val artworkUrl: String? = null,
    val origin: SourceType
)

enum class SourceType { TIDAL, DEEZER, APPLE, SPOTIFY, FILE }

/** Un resultado de busqueda de YouTube Music, ya normalizado. */
data class YtmCandidate(
    val videoId: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val explicit: Boolean? = null,
    /** true si vino del filtro "Songs"; false si es un video suelto. */
    val isSong: Boolean = true,
    val thumbnailUrl: String? = null
) {
    val primaryArtist: String get() = artists.firstOrNull().orEmpty()
}

sealed class MatchResult {
    abstract val source: SourceTrack

    /** Confianza alta: se inserta automaticamente. */
    data class Matched(
        override val source: SourceTrack,
        val videoId: String,
        val confidence: Float,
        val fromCache: Boolean = false
    ) : MatchResult()

    /** Hay candidatos pero ninguno claro: lo decide el usuario. */
    data class Ambiguous(
        override val source: SourceTrack,
        val candidates: List<YtmCandidate>
    ) : MatchResult()

    /** Nada razonable. Va al informe de fallos. */
    data class NotFound(
        override val source: SourceTrack,
        val reason: String = "sin resultados"
    ) : MatchResult()
}

/** Resumen de una importacion completa, para la pantalla de informe. */
data class ImportReport(
    val playlistName: String,
    val matched: List<MatchResult.Matched> = emptyList(),
    val ambiguous: List<MatchResult.Ambiguous> = emptyList(),
    val notFound: List<MatchResult.NotFound> = emptyList()
) {
    val total: Int get() = matched.size + ambiguous.size + notFound.size
    val coverage: Float get() = if (total == 0) 0f else matched.size.toFloat() / total
}
