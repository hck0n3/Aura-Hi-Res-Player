package iad1tya.echo.music.playback

/**
 * Persistent Enhanced Shuffle buckets ("Aleatorio mejorado").
 *
 * Prefixes are load-bearing: [iad1tya.echo.music.db.DatabaseDao.pruneOrphanEnhancedPlayed] deletes
 * every `PL:%` row whose suffix is not a live `playlist.id`. Albums, YouTube playlists and
 * auto-playlists must NEVER use `PL:` or a restart wipes their memory (registry #88).
 */
object ShuffleContexts {
    fun playlist(localId: String): String = "PL:$localId"

    fun onlinePlaylist(browseId: String): String = "OL:${browseId.removePrefix("VL")}"

    fun album(albumId: String): String = "AL:$albumId"

    /**
     * Owned / created lists keep `PL:<local id>` so existing memory is not orphaned.
     * Followed YouTube lists share `OL:<browseId>` whether opened from Biblioteca (local row) or
     * from Inicio/búsqueda (online screen) — otherwise shuffle memory only existed on lists the
     * user created.
     */
    fun forPlaylist(isEditable: Boolean, localId: String, browseId: String?): String {
        val browse = browseId?.takeIf { it.isNotBlank() }
        return if (!isEditable && browse != null) onlinePlaylist(browse) else playlist(localId)
    }

    /**
     * In-memory seed for a Continue shuffle: songs already in this context's memory PLUS songs
     * the user has actually heard ([totalPlayTime] > 0), so the checkmarks and the no-repeat
     * order agree even if Aleatorio mejorado was off when those songs played.
     * Start-over passes [resetMemory] = true and gets an empty set — history is not re-imported.
     */
    fun seedPlayedIds(
        resetMemory: Boolean,
        songIds: List<String>,
        shufflePlayed: Set<String>,
        playTimeMs: (String) -> Long,
    ): Set<String> {
        if (resetMemory) return emptySet()
        return songIds.filterTo(HashSet()) { id -> id in shufflePlayed || playTimeMs(id) > 0L }
    }
}
