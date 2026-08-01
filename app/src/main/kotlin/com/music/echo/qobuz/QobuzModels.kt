package iad1tya.echo.music.qobuz

import androidx.annotation.StringRes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for the OFFICIAL Qobuz API (https://www.qobuz.com/api.json/0.2/).
 *
 * Search/track models are deliberately REUSED from [iad1tya.echo.music.utils.qobuz] (QobuzTrack /
 * QobuzSearchData …): the official `track/search` payload is exactly the shape those already describe
 * (`{query, tracks:{items:[…]}}`), and the existing `confidence()` matcher in YTPlayerUtils is typed
 * against them — so reusing them keeps the playback matcher untouched. Only the auth (login) and the
 * signed getFileUrl responses, which the proxy path never modelled, are defined here.
 */

// ── Auth ──

@Serializable
data class QobuzLoginResponse(
    @SerialName("user_auth_token") val userAuthToken: String? = null,
    val user: QobuzUser? = null,
)

@Serializable
data class QobuzUser(
    val id: Long? = null,
    val email: String? = null,
    val login: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val credential: QobuzCredential? = null,
)

@Serializable
data class QobuzCredential(
    val id: Long? = null,
    val label: String? = null,
    val description: String? = null,
    // parameters == null is Qobuz's own signal for a FREE account (no lossless/hi-res entitlement).
    val parameters: QobuzCredentialParameters? = null,
)

@Serializable
data class QobuzCredentialParameters(
    val label: String? = null,
    @SerialName("short_label") val shortLabel: String? = null,
    @SerialName("lossy_streaming") val lossyStreaming: Boolean = false,
    @SerialName("lossless_streaming") val losslessStreaming: Boolean = false,
    @SerialName("hires_streaming") val hiresStreaming: Boolean = false,
    @SerialName("hires_purchases_streaming") val hiresPurchasesStreaming: Boolean = false,
)

// ── Signed stream URL ──

@Serializable
data class QobuzFileUrlResponse(
    @SerialName("track_id") val trackId: Long? = null,
    val url: String? = null,
    @SerialName("format_id") val formatId: Int = 0,
    @SerialName("mime_type") val mimeType: String? = null,
    // Qobuz returns sampling rate in kHz as a float (e.g. 44.1, 96.0, 192.0).
    @SerialName("sampling_rate") val samplingRate: Double? = null,
    @SerialName("bit_depth") val bitDepth: Int? = null,
    val duration: Int = 0,
    // A free/unentitled account (or a preview-only track) comes back as a 30s `sample`.
    val sample: Boolean = false,
    val restrictions: List<QobuzRestriction> = emptyList(),
) {
    /**
     * A real, entitled full-length stream carries a url AND a sampling rate and is not flagged `sample`.
     * Anything else (missing url, missing sampling_rate, or sample=true) is a 30s preview / not entitled.
     */
    val isPlayableStream: Boolean
        get() = !sample && !url.isNullOrBlank() && (samplingRate ?: 0.0) > 0.0

    /**
     * The ONLY thing the playback path is allowed to treat as a Qobuz "win".
     *
     * Qobuz downgrades SERVER-SIDE: asking for format 27 on an account without the matching entitlement
     * does not fail, it answers with whatever that account *is* entitled to — which for a free/lossy tier
     * is `format_id=5`, `mime_type="audio/mpeg"` (MP3 320). Accepting that would (a) hand back a LOSSY
     * stream as the lossless winner, pre-empting the squid.wtf proxy that would have delivered real FLAC,
     * and (b) make the SAME track resolve as audio/mpeg on one pass and audio/flac on the next — which is
     * exactly the container flip MusicService turns into ERROR_CODE_PARSING_CONTAINER_MALFORMED mid-song
     * (registry #57). So every one of the DELIVERED fields has to agree that this is FLAC:
     *  - `format_id` is one of the lossless ids (6 / 7 / 27) — 5 (MP3 320) is never lossless,
     *  - `mime_type` actually says flac (this is the exact field MusicService keys `isFinalLossless` on),
     *  - a real PCM word length and sample rate came back.
     * A response that cannot prove all of that is treated as a MISS, not as a downgrade.
     */
    val isLosslessDelivery: Boolean
        get() = isPlayableStream &&
            formatId in QobuzQualityFormat.LOSSLESS_IDS &&
            mimeType?.contains("flac", ignoreCase = true) == true &&
            (bitDepth ?: 0) >= 16 &&
            (samplingRate ?: 0.0) > 0.0
}

@Serializable
data class QobuzRestriction(
    val code: String? = null,
)

// ── Domain results (not wire types) ──

/**
 * A resolved, ready-to-play Qobuz stream from the owner's subscription. Sampling rate is normalised to Hz
 * here so the playback layer never has to remember Qobuz's kHz convention.
 *
 * INVARIANT: this type only ever describes a genuinely LOSSLESS delivery — it is constructed exclusively
 * from a response that passed [QobuzFileUrlResponse.isLosslessDelivery]. That is what makes [bitrateBps]
 * (a PCM rate derived from depth × rate) truthful; a lossy delivery never becomes a QobuzStream at all.
 */
data class QobuzStream(
    val url: String,
    val formatId: Int,
    val bitDepth: Int,
    val samplingRateHz: Int,
    val mimeType: String,
    /**
     * Bits per second computed from the DELIVERED bit depth and sample rate (stereo PCM). Valid only
     * because of the lossless invariant above — the same arithmetic on an MP3 320 response would claim
     * 1,411,200 bps for a 320 kbps stream.
     */
    val bitrateBps: Int,
)

/**
 * Everything persisted after a successful link: the user's token PLUS the working app_id/app_secret pair
 * that signed a valid getFileUrl. Keeping the credentials with the token means the playback hot path never
 * has to re-run the (network) scraper/secret discovery — it just signs with what already worked.
 */
data class QobuzSession(
    val token: String,
    val appId: String,
    val appSecret: String,
    val email: String? = null,
    val tierLabel: String? = null,
    val hiresEntitled: Boolean = false,
)

/** Outcome of a link attempt (email+password or pasted token), surfaced to the settings screen. */
sealed interface QobuzLoginResult {
    data class Success(
        val session: QobuzSession,
        /** True when the account has NO hi-res (Studio/Sublime) entitlement — the UI shows an honest note. */
        val freeOrLossyOnly: Boolean,
    ) : QobuzLoginResult

    /**
     * Carries a STRING RESOURCE id, not a literal: the message is user-facing and has to follow the app's
     * locale (values/, values-es/, values-es-rUS/), and this class lives outside any Composable/Context.
     */
    data class Error(@StringRes val messageRes: Int) : QobuzLoginResult
}
