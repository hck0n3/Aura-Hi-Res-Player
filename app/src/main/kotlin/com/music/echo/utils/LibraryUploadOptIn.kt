package iad1tya.echo.music.utils

/**
 * Decides, ONCE per install, whether Aura may write to the user's real YouTube Music account
 * (`YtmUploadSyncKey`). Extracted from `App` so the rule is unit-testable: getting it wrong means
 * either disobeying the owner's requested default or silently uploading a stranger's library to their
 * Google account, and neither is something to leave un-pinned.
 *
 * See `YtmUploadOptInV1AppliedKey` for the reasoning behind the two branches.
 */
object LibraryUploadOptIn {

    /**
     * @param stored the value already on disk, or null if the user has never had one written.
     * @param freshInstall true when the APK on the device is the one that was first installed — i.e.
     *   somebody setting Aura up now, as opposed to somebody who just tapped "update".
     */
    fun decide(stored: Boolean?, freshInstall: Boolean): Boolean =
        // An explicit choice always wins: a migration must never overwrite a decision the user made.
        stored ?: freshInstall

    /**
     * The value `YtmUploadSyncKey` must hold the moment the YouTube account is DETACHED — both logout
     * buttons, every account switch, and a cookie we can no longer parse. All of them funnel through
     * `App.forgetAccount`, which is the only caller.
     *
     * ### Why consent does not survive the account it was given for
     * The switch is not a preference about Aura, it is permission to write to a specific Google
     * account. Everything else about that account is dropped here (cookie, visitorData, dataSyncId,
     * name, email, handle) and every artist row is cut loose from it
     * (`DatabaseDao.clearArtistAccountSyncMarkers`) — but that clear deliberately KEEPS
     * `followedByUserAt`, because dropping it would destroy deliberate follows that were never pushed
     * upstream (see `ArtistSyncPolicy.afterAccountDetached`).
     *
     * Those kept markers are exactly the pending-SUBSCRIBE shape. Leaving the switch ON therefore
     * carried account A's entire follow list into account B and pushed it there on the first sync:
     * `subscribeChannel(id, true)` per row, 150 a pass, invisible to B's owner until they open
     * YouTube. Keeping `followedByUserAt` is only safe BECAUSE the switch is re-consented per account,
     * which is what this function makes true.
     *
     * It is deliberately a function rather than an inlined `false`: the rule is "revoke on detach",
     * and a caller that has to name it cannot delete it by accident.
     */
    fun onAccountDetached(): Boolean = false

    /**
     * What the switch reads on the first launch AFTER a detach, once the one-time opt-in migration
     * (`App.applyLibraryUploadOptInV1`) has had its chance to run again.
     *
     * [onAccountDetached] is only a real revocation if this is false for every reachable combination,
     * and that is what forces `forgetAccount` to store an EXPLICIT false instead of removing the key:
     * [decide] resolves an absent value to `freshInstall`, which is ON. A stored false survives the
     * migration whether or not its applied-flag is still set.
     *
     * @param optInV1Applied whether `YtmUploadOptInV1AppliedKey` is set (it is, by the time any
     *   account has ever been signed into) — false models the flag being lost or re-issued.
     */
    fun afterDetachAndMigration(optInV1Applied: Boolean, freshInstall: Boolean): Boolean =
        if (optInV1Applied) onAccountDetached()
        else decide(stored = onAccountDetached(), freshInstall = freshInstall)
}
