package iad1tya.echo.music.api

/**
 * Builds the chat messages for a text-to-playlist request. Pure (no Android/network) so it is
 * unit-testable; [AiPlaylistService] turns the returned messages into the provider's JSON body.
 */
object AiPlaylistPrompt {
    fun buildMessages(prompt: String, count: Int): List<ChatMessage> {
        val system = """
            Eres un curador musical que OBEDECE la petición del usuario al pie de la letra.
            Devuelve SOLO un objeto JSON válido, sin texto adicional y sin formato markdown, con esta
            forma exacta:
            {"name": string, "tracks": [{"title": string, "artist": string, "year": number}]}
            Reglas OBLIGATORIAS (si dudas, omite la canción; NUNCA improvises):
            - Incluye EXACTAMENTE $count canciones en "tracks".
            - "name" es un título corto (máximo 40 caracteres) que refleja la petición, no un invento.
            - Cada canción debe ser real, con "title" y "artist" verificables.
            - "year" es el año (4 dígitos) de la publicación ORIGINAL de esa grabación.
            - Cumple ESTRICTAMENTE lo pedido: género, subgénero, artista(s), idioma, época, mood y
              cualquier restricción ("solo X", "sin Y", "nada de Z"). No añadas temas, géneros ni
              artistas que el usuario NO pidió.
            - Si piden UN artista o grupo concreto, TODAS las canciones deben ser de ese artista/grupo
              (o colaboraciones claras donde ese artista es crédito principal). No rellenes con
              "similares", "recomendados" ni del mismo género.
            - Si piden un GÉNERO o subgénero concreto, NO sustituyas por uno "relacionado" ni más amplio
              (ej.: no cambies punk por rock alternativo; no cambies salsa por latín genérico).
            - NO improvises ni "mejores" ni "clásicos" que salgan del brief. Ante la duda, elige otra
              canción REAL que encaje con certeza — o deja hueco (el sistema rellenará después).
            - Si la petición indica época/década/año, comprueba el "year" ANTES de incluirla.
            - No repitas canciones ni añadas explicaciones.
            - Ejemplo de ERROR que NO debes cometer: piden "solo Bad Bunny" y devuelves a J Balvin o
              reggaeton genérico; o piden "punk de los 70" y devuelves rock alternativo de 2010.
        """.trimIndent()
        val user = """
            Petición del usuario (OBLIGATORIA — no reinterpretarla ni ampliarla): "$prompt"
            Crea una playlist de EXACTAMENTE $count canciones que cumpla ESA petición y nada más.
            No improvises géneros, artistas ni temas fuera de lo pedido.
        """.trimIndent().trim()
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        )
    }

    /**
     * Upper bound on how many tracks of the playlist are serialized into a modify request. Caps the
     * prompt size (tokens ≈ battery/heat) and keeps the reply small. Honest limitation: on a playlist
     * longer than this, only the first [MAX_MODIFY_TRACKS] are visible to the AI, so only those can be
     * removed. Additions are unaffected.
     */
    const val MAX_MODIFY_TRACKS = 200

    /**
     * Builds the chat messages for "edit this playlist with a text instruction".
     *
     * PRIVACY / CORRECTNESS: the playlist is serialized as a 1-based NUMBERED list and the model is
     * asked to answer with those same positions. Room's autoincrement `PlaylistSongMap.id` (or any
     * other internal identifier) is NEVER sent — the caller maps positions back to rows itself, so a
     * hallucinated number can only ever be an out-of-range position, never a pointer at an unrelated
     * database row.
     *
     * [currentTracks] must be the playlist in display order; the caller applies the returned indices
     * against that exact same snapshot.
     */
    fun buildModifyMessages(currentTracks: List<TrackQuery>, prompt: String): List<ChatMessage> {
        val visible = currentTracks.take(MAX_MODIFY_TRACKS)
        val system = """
            Eres un editor de playlists. Recibes una playlist NUMERADA y una instrucción del usuario.
            Devuelve SOLO un objeto JSON válido, sin texto adicional y sin formato markdown, con esta
            forma exacta:
            {"remove": [number], "additions": [{"title": string, "artist": string}]}
            Reglas:
            - "remove" contiene los NÚMEROS de posición de la lista (empezando en 1) de las canciones
              que hay que quitar. Usa EXACTAMENTE los números mostrados; no inventes otros, no uses
              identificadores y no devuelvas títulos en "remove".
            - "additions" contiene canciones reales que hay que añadir, con "title" y "artist"
              verificables. No añadas ninguna que ya esté en la lista.
            - Haz el cambio MÍNIMO que cumpla la instrucción: no quites ni añadas nada que no se pida.
              No improvises ni "mejores" extras.
            - Si la instrucción solo pide quitar, deja "additions" vacío; si solo pide añadir, deja
              "remove" vacío.
            - Ejemplo de ERROR que NO debes cometer: ante "quita las lentas", quitar media playlist o
              quitar canciones que no son lentas. Quita SOLO las que cumplen claramente la condición.
            - No añadas explicaciones.
        """.trimIndent()
        val numbered = visible.mapIndexed { index, track ->
            val artist = track.artist.ifBlank { "?" }
            "${index + 1}. \"${track.title}\" — $artist"
        }.joinToString("\n")
        val user = """
            Playlist actual (${visible.size} canciones):
            $numbered

            Instrucción del usuario (OBLIGATORIA — cambio mínimo, sin improvisar): "$prompt"
        """.trimIndent()
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        )
    }
}
