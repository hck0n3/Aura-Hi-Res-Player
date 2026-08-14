package iad1tya.echo.music.utils

import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * How to treat a local like / album favourite that is NOT on the YouTube account after a successful
 * read of the account's list.
 *
 * Live taps already push immediately ([iad1tya.echo.music.db.entities.SongEntity.toggleLike],
 * [iad1tya.echo.music.db.entities.AlbumEntity.toggleLike]). The old down-sync then RE-LIKED every
 * local-only row, which undid an unlike the user had just made on YouTube itself — the opposite of
 * *"lo que yo hago y deshago con mi cuenta"*.
 *
 * Empty / truncated remote pages must never reach this: [remoteListSafeToDropMissing] is the guard.
 */
object AccountLibraryReconcile {

    /** 15 minutes: covers an in-flight live like that has not landed on LM yet. */
    const val GRACE_MS: Long = 15L * 60L * 1000L

    enum class LocalMissingAction {
        /** Liked moments ago — retry the live write, do not drop the heart. */
        RETRY_PUSH,

        /**
         * Never completed a full account read. Pushing protects a library that only exists in Aura
         * (first backup). Dropping would destroy it.
         */
        PUSH_FIRST_SYNC,

        /** Liked before the last confirmed sync and gone from the account → user undid it on YouTube. */
        DROP_LOCAL,
    }

    fun localMissingAction(
        likedAtEpochMs: Long?,
        lastSuccessfulSyncEpochMs: Long,
        nowEpochMs: Long,
        graceMs: Long = GRACE_MS,
    ): LocalMissingAction {
        val likedAt = likedAtEpochMs ?: 0L
        val age = (nowEpochMs - likedAt).coerceAtLeast(0L)
        return when {
            age < graceMs -> LocalMissingAction.RETRY_PUSH
            lastSuccessfulSyncEpochMs <= 0L -> LocalMissingAction.PUSH_FIRST_SYNC
            else -> LocalMissingAction.DROP_LOCAL
        }
    }

    fun shouldPushLocalMissing(
        likedAtEpochMs: Long?,
        lastSuccessfulSyncEpochMs: Long,
        nowEpochMs: Long,
        graceMs: Long = GRACE_MS,
    ): Boolean = localMissingAction(
        likedAtEpochMs, lastSuccessfulSyncEpochMs, nowEpochMs, graceMs,
    ) != LocalMissingAction.DROP_LOCAL

    /**
     * Same 50 % truncation guard as a single playlist sync: a partial continuation must never be
     * readable as "the user removed the other half". An empty remote is always unsafe (failed fetch
     * and "they unliked everything" are indistinguishable).
     */
    fun remoteListSafeToDropMissing(remoteCount: Int, localConfirmedCount: Int): Boolean {
        if (remoteCount <= 0) return false
        if (localConfirmedCount <= 0) return false
        return remoteCount >= localConfirmedCount / 2
    }

    fun toEpochMilli(value: LocalDateTime?): Long? =
        value?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
}
