package iad1tya.echo.music.api

/**
 * Builds the chat messages for a text-to-playlist request. Pure (no Android/network) so it is
 * unit-testable; [AiPlaylistService] turns the returned messages into the provider's JSON body.
 */
object AiPlaylistPrompt {
    fun buildMessages(prompt: String, count: Int): List<ChatMessage> {
        val system = """
            Eres un ejecutor EXACTO de peticiones musicales. NO eres un recomendador.
            Devuelve SOLO un objeto JSON válido, sin texto adicional y sin formato markdown:
            {"name": string, "tracks": [{"title": string, "artist": string, "year": number}]}

            PROHIBIDO (si lo haces, falla la tarea):
            - Improvisar, "mejorar", ampliar, reinterpretar o "sugerir similares".
            - Añadir artistas, géneros, épocas o moods que el usuario NO escribió.
            - Sustituir un género por uno "relacionado" (punk≠rock, salsa≠latín, etc.).
            - Rellenar con éxitos genéricos o "clásicos" fuera del brief.
            - Inventar canciones falsas.

            OBLIGATORIO:
            - Cumple la petición al pie de la letra: cada canción debe ser lógica y verificable
              respecto a lo pedido (artista/género/idioma/época/mood/restricciones).
            - Incluye hasta $count canciones. Si no puedes completar $count SIN salirte del brief,
              devuelve MENOS — NUNCA rellenes con material ajeno.
            - "name" ≤ 40 caracteres, fiel a la petición.
            - "year" = año ORIGINAL (4 dígitos) de esa grabación.
            - Si piden UN artista/grupo: TODAS las pistas son de ese artista (crédito principal).
              "artist" debe ser ese nombre (o colaboración donde figure primero).
            - Si piden "sin X" / "nada de Y": cero track puede ser X/Y.
            - No repitas canciones. No añadas explicaciones.

            Error típico PROHIBIDO: "solo Bad Bunny" → devolver J Balvin / reggaeton genérico;
            "punk de los 70" → devolver rock alternativo de 2010.
        """.trimIndent()
        val solo = AiPlaylistConstraints.extractSoloArtist(prompt)
        val soloLock = if (solo != null) {
            """
            RESTRICCIÓN BLOQUEANTE: artista único = "$solo".
            Cada "artist" DEBE ser "$solo" (o colaboración con $solo primero).
            Cero artistas distintos. Mejor pocas canciones correctas que $count incorrectas.
            """.trimIndent()
        } else {
            ""
        }
        val user = """
            Petición EXACTA (copia literal — no la cambies): "$prompt"
            Devuelve canciones que cumplan ESA petición y NADA más.
            Cero improvisación. Cero similares. Cero ampliación del brief.
            $soloLock
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
            Eres un editor EXACTO de playlists. NO improvises.
            Devuelve SOLO JSON válido, sin markdown:
            {"remove": [number], "additions": [{"title": string, "artist": string}]}
            Reglas:
            - "remove" = números de posición (desde 1) EXACTOS de la lista. Nada inventado.
            - "additions" = canciones reales pedidas; no añadas extras "porque quedan bien".
            - Cambio MÍNIMO: solo lo que la instrucción pide. Cero improvisación.
            - Si solo pide quitar → "additions": []. Si solo pide añadir → "remove": [].
            - Error prohibido: ante "quita las lentas", borrar media playlist o canciones que no lo son.
        """.trimIndent()
        val numbered = visible.mapIndexed { index, track ->
            val artist = track.artist.ifBlank { "?" }
            "${index + 1}. \"${track.title}\" — $artist"
        }.joinToString("\n")
        val user = """
            Playlist actual (${visible.size} canciones):
            $numbered

            Instrucción EXACTA (cero improvisación): "$prompt"
        """.trimIndent()
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        )
    }
}
