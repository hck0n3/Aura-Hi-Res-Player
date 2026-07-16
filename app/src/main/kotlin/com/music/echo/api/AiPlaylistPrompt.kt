package iad1tya.echo.music.api

/**
 * Builds the chat messages for a text-to-playlist request. Pure (no Android/network) so it is
 * unit-testable; [AiPlaylistService] turns the returned messages into the provider's JSON body.
 */
object AiPlaylistPrompt {
    fun buildMessages(prompt: String, count: Int): List<ChatMessage> {
        val system = """
            Eres un curador musical experto. Devuelve SOLO un objeto JSON válido, sin texto adicional
            y sin formato markdown, con esta forma exacta:
            {"name": string, "tracks": [{"title": string, "artist": string, "year": number}]}
            Reglas:
            - Incluye EXACTAMENTE $count canciones en "tracks".
            - "name" es un título corto (máximo 40 caracteres) para la playlist.
            - Cada canción debe ser real, con "title" y "artist" verificables.
            - "year" es el año (4 dígitos) de la publicación ORIGINAL de esa grabación, no el de una
              recopilación, reedición ni regrabación posterior. Úsalo también para distinguir la
              versión original de una versión de otro artista.
            - Si la petición indica una época, década, año o artista concreto, TODAS las canciones
              deben cumplirlo: comprueba el "year" de cada canción ANTES de incluirla y descártala
              si no encaja.
            - No repitas canciones ni añadas explicaciones.
            - Respeta EXACTAMENTE el género, subgénero, estado de ánimo, idioma y época pedidos.
            - NO sustituyas por un subgénero relacionado ni más amplio.
            - Ejemplo de ERROR que NO debes cometer: si piden "punk de los 70", devolver un tema de
              rock alternativo de 2010 (género más amplio y década equivocada), o la regrabación de
              2015 de un tema de 1977. Lo correcto es una canción publicada realmente en esa década
              y en ese subgénero exacto.
            - Cada canción debe pertenecer claramente al tema pedido; ante la duda, elige una canción real que encaje con certeza.
            - Respeta el idioma implícito de la petición.
        """.trimIndent()
        val user = "Crea una playlist de $count canciones que cumpla ESTRICTAMENTE esta petición: \"$prompt\". Todas deben encajar en el género/idioma/tono pedido."
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

            Instrucción del usuario: "$prompt"
        """.trimIndent()
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        )
    }
}
