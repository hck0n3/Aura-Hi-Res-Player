package iad1tya.echo.music.lyrics

import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.LyricsEntity
import iad1tya.echo.music.db.entities.LyricsEntity.Companion.CURRENT_MATCH_RULES
import iad1tya.echo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import iad1tya.echo.music.models.MediaMetadata
import timber.log.Timber
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException

/**
 * Repairs lyrics that are ALREADY WRONG in a user's database, lazily, one song at a time.
 *
 * ## Why a repair is needed at all
 * LrcLib used to accept a match on DURATION ALONE from a result set that the server had not
 * constrained by artist, so a query for one song could return another artist's fully time-synced
 * lyrics. The matcher is fixed now, but the wrong result was WRITTEN TO THE DATABASE and the fetch
 * paths only ever fetch when no row exists. Without this, every already-affected user keeps the
 * wrong lyrics forever and the fix helps nobody who already hit the bug.
 *
 * ## Why lazy and not a bulk wipe
 * A one-shot "delete every fetched row" would be a mass deletion aimed at a population we cannot
 * describe: rows written before this build carry no usable provenance (see [LyricsEntity.userEdited]),
 * so a hand-corrected row looks exactly like an auto-fetched one. Re-verifying at the moment a lyric
 * is about to be shown costs nothing for the rows that were right, needs no table scan, and heals
 * the rest as the user listens.
 *
 * ## Why nothing can be lost
 * Three independent guards, any one of which is sufficient:
 *  1. a row the user typed or edited is skipped here;
 *  2. the repair UPDATE itself carries `AND userEdited = 0`, so it is unreachable even from a bug;
 *  3. a repair never deletes - the displaced text is parked in `supersededLyrics` and the user can
 *     put it back.
 * A failed or offline re-check changes NOTHING. The stored lyric is only ever replaced when the
 * fixed matcher positively returned different lyrics for this song.
 */
object LyricsMatchRepair {
    /**
     * Providers whose stored rows can be a product of the duration-only match.
     *
     * "Unknown" is in here for a second, separate reason: the lyrics PRELOAD path used to write rows
     * without passing a provider at all, so the column fell back to its "Unknown" default. An LrcLib
     * result can therefore be hiding under "Unknown" (as can any row predating the provider column).
     */
    private val SUSPECT_PROVIDERS = setOf("LrcLib", "Unknown")

    /**
     * One re-check attempt per song per process. Bounds the cost when the re-check cannot conclude -
     * offline, or every provider timed out. Deliberately NOT persisted: an inconclusive attempt must
     * be retried on a later run, because the row is still unverified and may still be wrong.
     */
    private val attemptedThisSession = Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Ceiling on the set above so a marathon offline session cannot grow it without bound. Songs
     * that conclude are marked in the database and never come back here, so this is only ever
     * reached by inconclusive attempts; dropping them costs at most one repeated check.
     */
    private const val MAX_TRACKED_ATTEMPTS = 512

    /**
     * Cheap, allocation-free pre-check so the common path (a row already verified, or one the user
     * owns) costs a couple of field reads and no coroutine, no query and no network.
     */
    fun needsVerification(stored: LyricsEntity?): Boolean {
        if (stored == null) return false
        if (stored.userEdited) return false
        if (stored.matchRulesVersion >= CURRENT_MATCH_RULES) return false
        return true
    }

    /**
     * Re-verifies one stored lyric against the fixed matching rules and repairs it if it disagrees.
     *
     * Safe to call on every song change: it returns immediately for rows that are already verified,
     * user-owned, or already attempted. Call from a background coroutine - it performs network I/O.
     * Database writes are posted to Room's executor, never the caller's thread.
     */
    suspend fun verifyAndRepair(
        database: MusicDatabase,
        lyricsHelper: LyricsHelper,
        mediaMetadata: MediaMetadata,
        stored: LyricsEntity,
    ) {
        if (!needsVerification(stored)) return

        // A stored "we looked and found nothing" is not a wrong-lyrics problem - there is no text on
        // screen to be wrong. The fixed rules are strictly STRICTER, so a re-check could only turn
        // more results away. Mark it settled so it never costs a request.
        if (stored.lyrics == LYRICS_NOT_FOUND || stored.lyrics.isBlank()) {
            database.query { markLyricsMatchVerified(stored.id, CURRENT_MATCH_RULES) }
            return
        }

        // Rows from providers the bug could not have produced are settled without a request. This is
        // what keeps the repair from re-fetching a whole library's worth of correct lyrics.
        if (stored.provider !in SUSPECT_PROVIDERS) {
            database.query { markLyricsMatchVerified(stored.id, CURRENT_MATCH_RULES) }
            return
        }

        if (attemptedThisSession.size >= MAX_TRACKED_ATTEMPTS) attemptedThisSession.clear()
        if (!attemptedThisSession.add(stored.id)) return

        val refetched =
            try {
                lyricsHelper.getLyrics(mediaMetadata)
            } catch (e: CancellationException) {
                // The user skipped songs. Not a verdict - let a later play try again.
                attemptedThisSession.remove(stored.id)
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Lyrics re-verification failed for %s", stored.id)
                return
            }

        // INCONCLUSIVE, NOT A VERDICT. Offline, every provider timed out, or the song genuinely has
        // no lyrics under the stricter rules - these are indistinguishable from here, and a stored
        // lyric that happens to be correct must not be thrown away because the network was down.
        // Leave the row untouched and unverified so a future play re-checks it.
        if (refetched.lyrics == LYRICS_NOT_FOUND || refetched.lyrics.isBlank()) return

        // The fixed rules reproduce what is stored: the row was right all along. Settle it.
        // Compared trimmed so that a difference of nothing but surrounding whitespace does not stage
        // a "repair" of text that is already correct - that would show the user a pointless
        // "restore previous" action and consume the single superseded slot for no reason.
        if (refetched.lyrics.trim() == stored.lyrics.trim()) {
            database.query { markLyricsMatchVerified(stored.id, CURRENT_MATCH_RULES) }
            return
        }

        // Disagreement: the stored text is not what this song resolves to any more. Swap it in and
        // keep the old text - `repairMismatchedLyrics` moves it to supersededLyrics and refuses the
        // update outright if the row turns out to be user-owned.
        database.query {
            val updated =
                repairMismatchedLyrics(
                    id = stored.id,
                    newLyrics = refetched.lyrics,
                    provider = refetched.provider,
                    rulesVersion = CURRENT_MATCH_RULES,
                )
            if (updated > 0) {
                Timber.i("Replaced mismatched lyrics for %s (was %s)", stored.id, stored.provider)
            }
        }
    }
}
