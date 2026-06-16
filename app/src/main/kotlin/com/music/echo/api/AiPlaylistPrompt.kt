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
            {"name": string, "tracks": [{"title": string, "artist": string}]}
            Reglas:
            - Incluye EXACTAMENTE $count canciones en "tracks".
            - "name" es un título corto (máximo 40 caracteres) para la playlist.
            - Cada canción debe ser real, con "title" y "artist" verificables.
            - No repitas canciones ni añadas explicaciones.
        """.trimIndent()
        val user = "Crea una playlist de $count canciones para esta idea: \"$prompt\"."
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        )
    }
}
