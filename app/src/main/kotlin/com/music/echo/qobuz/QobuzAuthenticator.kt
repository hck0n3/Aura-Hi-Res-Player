package iad1tya.echo.music.qobuz

import iad1tya.echo.music.R
import iad1tya.echo.music.utils.qobuz.QobuzTrack
import timber.log.Timber

/**
 * One-time LINK flow: turns an email+password (or a pasted user_auth_token) into a validated
 * [QobuzSession] the playback path can sign with. This is where the (network-heavy) app_id/app_secret
 * discovery happens, so the hot path never pays it.
 *
 * Secret discovery: we sign a REAL `getFileUrl` for a probe track against each candidate app_secret. The
 * wrong secret comes back HTTP 400 (bad signature) → try the next; a bad token comes back 401 → stop; the
 * secret that returns 200 (even a free-account `sample`) is the working one and is what we persist.
 */
class QobuzAuthenticator(
    private val api: QobuzApi,
    private val configProvider: QobuzConfigProvider,
) {
    /** Link with email + password. */
    suspend fun authenticate(email: String, password: String): QobuzLoginResult {
        val config = configProvider.resolve()
        val appId = config.appId
            ?: return QobuzLoginResult.Error(ERR_NO_APP_ID)
        if (config.secrets.isEmpty()) return QobuzLoginResult.Error(ERR_NO_SECRET)

        val login = api.login(appId, email, password)
            ?: return QobuzLoginResult.Error(ERR_BAD_CREDENTIALS)
        val token = login.userAuthToken
            ?: return QobuzLoginResult.Error(ERR_BAD_CREDENTIALS)

        val params = login.user?.credential?.parameters
        val freeAccount = params == null
        val hiresEntitled = params?.hiresStreaming == true
        val tierLabel = params?.shortLabel ?: params?.label
            ?: login.user?.credential?.label
            ?: if (freeAccount) "Qobuz Free" else null

        val secret = discoverSecret(appId, token, config.secrets)
            ?: return QobuzLoginResult.Error(ERR_NO_SECRET)

        val session = QobuzSession(
            token = token,
            appId = appId,
            appSecret = secret,
            email = email,
            tierLabel = tierLabel,
            hiresEntitled = hiresEntitled,
        )
        return QobuzLoginResult.Success(session, freeOrLossyOnly = freeAccount || !hiresEntitled)
    }

    /** Link by pasting an existing user_auth_token (no password). */
    suspend fun authenticateWithToken(token: String): QobuzLoginResult {
        val config = configProvider.resolve()
        val appId = config.appId
            ?: return QobuzLoginResult.Error(ERR_NO_APP_ID)
        if (config.secrets.isEmpty()) return QobuzLoginResult.Error(ERR_NO_SECRET)

        // Prefer a probe track that IS hi-res so the delivered bit depth is a reliable entitlement signal.
        val probe = pickProbeTrack(appId, token)
            ?: return QobuzLoginResult.Error(ERR_PROBE)

        var workingSecret: String? = null
        var delivered: QobuzFileUrlResponse? = null
        var sawUnauthorized = false
        for (secret in config.secrets) {
            when (val r = api.getFileUrl(appId, secret, token, probe.id, QobuzQualityFormat.HIRES_192)) {
                is QobuzApi.FileUrlResult.Ok -> { workingSecret = secret; delivered = r.response; break }
                QobuzApi.FileUrlResult.Unauthorized -> { sawUnauthorized = true }
                QobuzApi.FileUrlResult.BadSignature -> Unit // wrong secret, keep trying
                is QobuzApi.FileUrlResult.Failed -> Unit
            }
        }
        if (workingSecret == null) {
            return QobuzLoginResult.Error(if (sawUnauthorized) ERR_BAD_TOKEN else ERR_NO_SECRET)
        }

        val response = delivered
        val playable = response?.isPlayableStream == true
        // 24-bit is only claimed when the DELIVERED response is really FLAC at 24-bit — a lossy tier gets
        // format 5 / audio/mpeg back from this very same request, and that must not read as "hi-res ok".
        val hires = response != null &&
            response.isLosslessDelivery &&
            (response.bitDepth ?: 0) >= 24 &&
            probe.hires
        val session = QobuzSession(
            token = token,
            appId = appId,
            appSecret = workingSecret,
            email = null,
            tierLabel = if (playable) null else "Qobuz Free",
            hiresEntitled = hires,
        )
        // Only-previews (no playable stream) => free; otherwise "lossy only" if we couldn't confirm 24-bit.
        return QobuzLoginResult.Success(session, freeOrLossyOnly = !playable || !hires)
    }

    /**
     * Find the working app_secret by signing a getFileUrl for a probe track against each candidate.
     * Returns null when none validates (wrong secret) — the token itself is assumed valid here because it
     * came straight from a successful login.
     */
    private suspend fun discoverSecret(appId: String, token: String, secrets: List<String>): String? {
        val probe = pickProbeTrack(appId, token) ?: return null
        for (secret in secrets) {
            when (api.getFileUrl(appId, secret, token, probe.id, QobuzQualityFormat.HIRES_192)) {
                is QobuzApi.FileUrlResult.Ok -> return secret
                else -> Unit
            }
        }
        return null
    }

    private suspend fun pickProbeTrack(appId: String, token: String): QobuzTrack? {
        for (query in PROBE_QUERIES) {
            val tracks = api.searchTracks(appId, token, query, limit = 25)
            if (tracks.isEmpty()) continue
            // Prefer a genuinely hi-res, streamable track so bit-depth read-back is meaningful.
            tracks.firstOrNull { it.streamable && it.hires && it.maximumBitDepth >= 24 }?.let { return it }
            tracks.firstOrNull { it.streamable }?.let { return it }
        }
        Timber.tag(TAG).w("No Qobuz probe track found for secret discovery")
        return null
    }

    private companion object {
        const val TAG = "QobuzAuthenticator"
        val PROBE_QUERIES = listOf("Daft Punk", "Adele", "Coldplay", "music")
        // User-facing copy lives in strings.xml (values/, values-es/, values-es-rUS/) — never inline here.
        val ERR_NO_APP_ID = R.string.qobuz_err_no_app_id
        val ERR_NO_SECRET = R.string.qobuz_err_no_secret
        val ERR_BAD_CREDENTIALS = R.string.qobuz_err_bad_credentials
        val ERR_BAD_TOKEN = R.string.qobuz_err_bad_token
        val ERR_PROBE = R.string.qobuz_err_probe
    }
}

/** Qobuz format_id ladder: request the top, step down on rejection. */
internal object QobuzQualityFormat {
    const val MP3_320 = 5           // LOSSY — never a valid own-subscription result
    const val FLAC_CD = 6            // 16-bit / 44.1
    const val HIRES_96 = 7          // 24-bit up to 96 kHz
    const val HIRES_192 = 27        // 24-bit up to 192 kHz

    /** The format ids that actually carry FLAC. [MP3_320] is deliberately NOT here. */
    val LOSSLESS_IDS = setOf(FLAC_CD, HIRES_96, HIRES_192)

    /**
     * Descending playback ladder: 27 → 7 → 6. It stops at FLAC_CD ON PURPOSE.
     *
     * Requesting 5 (MP3 320) could only ever produce a LOSSY answer, which the resolver must reject
     * anyway (see [QobuzFileUrlResponse.isLosslessDelivery]) — so asking for it was a guaranteed-wasted
     * round trip inside the caller's time budget. Note this does NOT stop a lossy account from being
     * detected: Qobuz downgrades server-side, so a free/lossy tier answers the format-27 request itself
     * with format 5 / audio/mpeg, which is rejected and falls through to the normal proxy path.
     */
    val LADDER = listOf(HIRES_192, HIRES_96, FLAC_CD)
}
