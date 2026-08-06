

package iad1tya.echo.music.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val id: String,
    val lyrics: String,
    @ColumnInfo(defaultValue = "Unknown") val provider: String = "Unknown",
    @ColumnInfo(defaultValue = "") val translatedLyrics: String = "",
    @ColumnInfo(defaultValue = "") val translationLanguage: String = "",
    @ColumnInfo(defaultValue = "") val translationMode: String = "",
    /**
     * TRUE only when a human typed, pasted or edited this text. The ONLY writer is the lyrics edit
     * dialog. Nothing in the app may ever overwrite or delete a row with this set - the repair pass
     * skips it in Kotlin AND the repair UPDATE carries `AND userEdited = 0` so a caller bug cannot
     * reach it either.
     *
     * Why this exists: `provider` was NOT usable as provenance. The edit dialog saves a hand-edit
     * under the provider name of the row it was editing ("LrcLib", ...), so a user-corrected row was
     * byte-for-byte indistinguishable from an auto-fetched one. Only a row edited when NO lyrics
     * existed got the "Manual" marker.
     */
    @ColumnInfo(defaultValue = "0") val userEdited: Boolean = false,
    /**
     * Which generation of the provider-matching rules produced this row.
     *
     * The Kotlin default is [CURRENT_MATCH_RULES] on purpose: anything constructed by this (fixed)
     * build was matched under the current rules, so every existing fetch call site is marked correct
     * without being touched. The SQL default is "0" and applies only to rows ALREADY on disk at
     * migration time - those were matched under the broken rules and are re-verified lazily.
     */
    @ColumnInfo(defaultValue = "0") val matchRulesVersion: Int = CURRENT_MATCH_RULES,
    /**
     * The text a repair displaced, kept verbatim so a repair can never destroy anything.
     *
     * Legacy rows carry no provenance, so "auto-fetched by the broken matcher" and "hand-corrected
     * by the user before this build shipped" are indistinguishable. Repairing in place would delete
     * the second kind. The old text is parked here instead and is restorable from the lyrics menu.
     */
    @ColumnInfo(defaultValue = "") val supersededLyrics: String = "",
) {
    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"

        /**
         * Bump this when provider matching changes in a way that invalidates stored results. Rows
         * below it are re-verified once, lazily, the next time they are about to be displayed.
         *
         * 1 = LrcLib no longer accepts a duration-only match on a result set that was not
         *     constrained by artist (the wrong-artist / wrong-song lyrics bug).
         */
        const val CURRENT_MATCH_RULES = 1
    }
}
