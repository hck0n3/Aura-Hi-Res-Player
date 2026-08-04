package iad1tya.echo.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library upload writes to the user's REAL Google account. This pins the one rule that keeps that
 * honest: the owner's "sync my playlists without asking me" default applies to people setting Aura up
 * now, and nobody is enrolled into remote writes by the mere act of installing an update.
 */
class LibraryUploadOptInTest {

    @Test
    fun aFreshInstallGetsTheOwnersRequestedDefault() {
        assertTrue(LibraryUploadOptIn.decide(stored = null, freshInstall = true))
    }

    @Test
    fun updatingAnExistingInstallNeverEnrolsTheUser() {
        // The single most important assertion in this file: tapping "update" must not start writing
        // playlists and channel subscriptions into a paying customer's YouTube account.
        assertFalse(LibraryUploadOptIn.decide(stored = null, freshInstall = false))
    }

    @Test
    fun anExplicitChoiceAlwaysWins() {
        // Someone who turned it ON keeps it on even though their install is an update...
        assertTrue(LibraryUploadOptIn.decide(stored = true, freshInstall = false))
        // ...and someone who turned it OFF is not re-enabled by a reinstall.
        assertFalse(LibraryUploadOptIn.decide(stored = false, freshInstall = true))
    }

    // ── consent is per-ACCOUNT: the cross-account mass SUBSCRIBE ─────────────────────────────────

    /**
     * The owner's original complaint ("me aparecen muchas suscripciones de cantantes que no sigo"),
     * aimed at somebody else's account.
     *
     * Reachable on all defaults, no dev tools: fresh install turns the upload switch ON without asking
     * (`App.applyLibraryUploadOptInV1` + [decide]); signing into account A stamps `followedByUserAt` on
     * every channel A is subscribed to (`DatabaseDao.markArtistsSubscribedOnYtm`); "cerrar sesión
     * (mantener datos)" deliberately KEEPS those markers; sign into account B and the next sync pushes
     * A's entire follow list onto B, 150 `subscribeChannel(id, true)` a pass.
     *
     * The only thing between that chain and B's account is this switch, so detaching an account has to
     * revoke it.
     */
    @Test
    fun detachingTheAccountRevokesTheUploadConsent() {
        assertFalse(
            "The library-upload switch survived a logout. It is permission to WRITE to one specific " +
                "Google account, and `clearArtistAccountSyncMarkers` keeps `followedByUserAt` on " +
                "purpose — so leaving it on carries the detached account's whole follow list into " +
                "whoever signs in next and subscribes them to it.",
            LibraryUploadOptIn.onAccountDetached(),
        )
    }

    /**
     * ...and the revocation has to survive the one-time opt-in migration, which is why
     * `forgetAccount` stores an EXPLICIT false rather than removing the key.
     *
     * [decide] resolves an ABSENT value to `freshInstall`, so a `remove()` would be re-defaulted back
     * ON the moment `YtmUploadOptInV1AppliedKey` was reissued or lost — the same "one-time migration
     * needs a fresh key" trapdoor that already bit this codebase. A stored false is inert in both
     * branches.
     */
    @Test
    fun theRevocationSurvivesTheOneTimeOptInMigration() {
        listOf(true, false).forEach { optInApplied ->
            listOf(true, false).forEach { freshInstall ->
                assertFalse(
                    "The upload switch came back ON after a logout (optInV1Applied=$optInApplied, " +
                        "freshInstall=$freshInstall). Detaching an account must revoke its consent " +
                        "unconditionally — store an explicit false, never remove the key.",
                    LibraryUploadOptIn.afterDetachAndMigration(optInApplied, freshInstall),
                )
            }
        }
    }

    /**
     * The revocation must not become a way to disable the LIVE requirement. It cannot, by
     * construction — nothing on the live path reads this switch — so this test guards the shape of the
     * rule rather than a value: `onAccountDetached` applies to a DETACHED account, and there is no
     * live follow/unfollow/like to deliver while no account is attached.
     *
     * If you ever add a parameter here that lets a SIGNED-IN state turn the switch off as a side
     * effect, you have broken *"mientras estés con la sesión iniciada, si yo me suscribo, me
     * desuscribo, o doy like o quito like, eso se tiene que sincronizar con mi cuenta de YouTube"*.
     */
    @Test
    fun revokingConsentDoesNotTouchAnAttachedSession() {
        // A user who deliberately turned the switch ON and is still signed in keeps it on: nothing in
        // this object can flip a stored choice while the account is attached.
        assertTrue(LibraryUploadOptIn.decide(stored = true, freshInstall = false))
        assertTrue(LibraryUploadOptIn.decide(stored = true, freshInstall = true))
    }
}
