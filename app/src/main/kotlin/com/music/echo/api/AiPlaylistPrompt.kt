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
            - Respeta EXACTAMENTE el género, subgénero, estado de ánimo, idioma y época pedidos.
            - NO sustituyas por un subgénero relacionado ni más amplio.
            - Cada canción debe pertenecer claramente al tema pedido; ante la duda, elige una canción real que encaje con certeza.
            - Respeta el idioma implícito de la petición.
        """.trimIndent()
        val user = "Crea una playlist de $count canciones que cumpla ESTRICTAMENTE esta petición: \"$prompt\". Todas deben encajar en el género/idioma/tono pedido."
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        )
    }
}
