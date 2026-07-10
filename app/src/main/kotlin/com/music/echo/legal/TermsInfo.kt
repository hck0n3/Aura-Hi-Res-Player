package iad1tya.echo.music.legal

/**
 * Single source of truth for the in-app Terms & Conditions.
 *
 * The full legal text lives VERBATIM in the app asset [ASSET_PATH]; keep the canonical copy and this
 * version number in sync whenever the terms change materially.
 */
object TermsInfo {
    /**
     * Bump ONLY when the terms text changes materially. The blocking acceptance screen re-appears
     * automatically for every user whose stored TermsAcceptedVersionKey is lower than this.
     */
    const val TERMS_VERSION = 1

    /** App asset holding the full markdown text of the terms (shipped verbatim). */
    const val ASSET_PATH = "legal/TERMINOS_Y_CONDICIONES.md"
}
