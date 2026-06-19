package iad1tya.echo.music.reco

/**
 * Lightweight "style lane" inference so autoplay can keep the same line as what you're listening to
 * (e.g. Christian → Christian, not jumping to secular). YouTube doesn't tag genre/lyrical content, so
 * this is a best-effort keyword classifier over a track's title + artist + album text. It only makes a
 * POSITIVE call for lanes with unambiguous markers (currently "christian"); everything else is
 * UNKNOWN, and the caller only enforces the lane when it has enough same-lane candidates (never
 * dead-ends playback). Combined with "No me gusta", the more you correct it the cleaner it stays.
 */
object GenreLane {
    const val CHRISTIAN = "christian"

    // Unambiguous Christian markers (Spanish + English). Kept to words that rarely appear in secular
    // music so we don't mislabel. Artist names are intentionally avoided (too brittle / ambiguous).
    private val CHRISTIAN_MARKERS = listOf(
        "cristian", "cristo", " jesus", " jesús", "jesucristo", "evangelio", "evangélic", "evangelic",
        "gospel", "adoracion", "adoración", "worship", "alabanza", "aleluya", "hallelujah",
        "espiritu santo", "espíritu santo", "santo espiritu", "iglesia", "salmo", "redentor",
        "musica cristiana", "música cristiana", "christian", "reggaeton cristiano", "rap cristiano",
        "tu presencia", "dios es", "señor jesus", "el shaddai", "jehova", "jehová", "yahweh",
    )

    private fun normalize(text: String?): String =
        (text ?: "").lowercase()

    /** Returns a lane id (e.g. [CHRISTIAN]) when the text clearly belongs to one, else null. */
    fun laneOf(vararg parts: String?): String? {
        val t = normalize(parts.joinToString(" "))
        if (t.isBlank()) return null
        if (CHRISTIAN_MARKERS.any { t.contains(it) }) return CHRISTIAN
        return null
    }
}
