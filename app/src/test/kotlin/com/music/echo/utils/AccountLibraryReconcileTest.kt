package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the rule that stops a YouTube-side unlike from being re-liked on the next Aura sync,
 * without destroying an in-flight heart or a library that has never been read from the account.
 */
class AccountLibraryReconcileTest {

    private val now = 1_000_000_000_000L
    private val grace = AccountLibraryReconcile.GRACE_MS
    private val lastSync = now - 3_600_000L

    @Test
    fun aRecentLocalLikeIsRetriedNotDropped() {
        val likedAt = now - 60_000L
        assertEquals(
            AccountLibraryReconcile.LocalMissingAction.RETRY_PUSH,
            AccountLibraryReconcile.localMissingAction(likedAt, lastSync, now),
        )
        assertTrue(AccountLibraryReconcile.shouldPushLocalMissing(likedAt, lastSync, now))
    }

    @Test
    fun anOlderLocalLikeAfterAConfirmedSyncIsDropped() {
        val likedAt = lastSync - 60_000L
        assertEquals(
            AccountLibraryReconcile.LocalMissingAction.DROP_LOCAL,
            AccountLibraryReconcile.localMissingAction(likedAt, lastSync, now),
        )
        assertFalse(AccountLibraryReconcile.shouldPushLocalMissing(likedAt, lastSync, now))
        // This is the owner's "deshago": unlike on YouTube must not be re-liked by Aura.
    }

    @Test
    fun theFirstSyncPushesRatherThanDestroyingAnUnsyncedLibrary() {
        val likedAt = now - 86_400_000L
        assertEquals(
            AccountLibraryReconcile.LocalMissingAction.PUSH_FIRST_SYNC,
            AccountLibraryReconcile.localMissingAction(likedAt, lastSuccessfulSyncEpochMs = 0L, now),
        )
        assertTrue(AccountLibraryReconcile.shouldPushLocalMissing(likedAt, 0L, now))
    }

    @Test
    fun aLikeExactlyAtTheGraceBoundaryIsDroppedIfPastIt() {
        val likedAt = now - grace
        assertEquals(
            AccountLibraryReconcile.LocalMissingAction.DROP_LOCAL,
            AccountLibraryReconcile.localMissingAction(likedAt, lastSync, now),
        )
    }

    @Test
    fun aLikeOneMillisecondInsideGraceIsRetried() {
        val likedAt = now - grace + 1L
        assertEquals(
            AccountLibraryReconcile.LocalMissingAction.RETRY_PUSH,
            AccountLibraryReconcile.localMissingAction(likedAt, lastSync, now),
        )
    }

    @Test
    fun anEmptyRemotePageMustNeverDropLocalRows() {
        assertFalse(AccountLibraryReconcile.remoteListSafeToDropMissing(0, 4000))
        assertFalse(AccountLibraryReconcile.remoteListSafeToDropMissing(0, 1))
    }

    @Test
    fun aTruncatedRemotePageMustNeverDropLocalRows() {
        // 1000 of 4000 is well under half — the continuation died, do not unlike 3000 songs.
        assertFalse(AccountLibraryReconcile.remoteListSafeToDropMissing(1000, 4000))
    }

    @Test
    fun aNearCompleteRemotePageMayDropTheMissingOnes() {
        assertTrue(AccountLibraryReconcile.remoteListSafeToDropMissing(3900, 4000))
        assertTrue(AccountLibraryReconcile.remoteListSafeToDropMissing(8, 10))
    }

    @Test
    fun nothingToDropIsUnsafeSoCallersSkipThePass() {
        assertFalse(AccountLibraryReconcile.remoteListSafeToDropMissing(100, 0))
    }
}
