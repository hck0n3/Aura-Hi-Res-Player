package iad1tya.echo.music.qobuz

import iad1tya.echo.music.utils.confidence
import iad1tya.echo.music.utils.qobuz.QobuzTrack
import iad1tya.echo.music.utils.qobuzSearchTerms
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Process-global facade the playback path ([iad1tya.echo.music.utils.YTPlayerUtils], an `object` that
 * can't be Hilt-injected) reads from — the same static-holder pattern the app uses for `YouTube.cookie`.
 *
 * It is INERT unless the owner has linked their Qobuz account AND the preference is on ([isActive]); a
 * user without Qobuz sees byte-identical behaviour to before. When active, [resolve] matches the current
 * song to a Qobuz track and returns a signed, entitled FLAC stream from the owner's OWN subscription —
 * completely separate from the third-party squid.wtf proxy in
 * [iad1tya.echo.music.utils.qobuz.QobuzApiClient].
 *
 * HARD RULE: this path only ever returns LOSSLESS. It is consulted BEFORE the proxy, so returning
 * anything less would pre-empt a source that does deliver FLAC. When Qobuz can only serve lossy for this
 * account/track, [resolve] returns null and the caller's normal fallback chain runs untouched.
 */
object QobuzHiRes {

    @Volatile private var store: QobuzTokenStore? = null
    @Volatile private var api: QobuzApi? = null

    /** Mirrors the [iad1tya.echo.music.constants.UseOwnQobuzHiResKey] preference (set by App collector). */
    @Volatile var enabled: Boolean = false

    /**
     * Wired once at startup (App). Idempotent. Owns its OWN OkHttpClient, mirroring how the proxy
     * QobuzApiClient news up its own client.
     *
     * Timeouts are deliberately tighter than the caller's budget: `callTimeout` bounds the WHOLE call
     * (DNS + connect + write + server + body read), which connect/read timeouts alone never did — two
     * consecutive stalls of 8s connect + 8s read each could previously outlive the caller's 9s window
     * several times over. Combined with the cancellable `enqueue` in [QobuzApi], a slow Qobuz now fails
     * fast and the caller falls back on time.
     */
    fun attach(store: QobuzTokenStore) {
        this.store = store
        if (this.api == null) {
            this.api = QobuzApi(
                OkHttpClient.Builder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                    // The one that actually bounds a request: connect/read only limit individual phases,
                    // so DNS + connect + server + body could each take their own timeout and blow past the
                    // caller's window. callTimeout caps the WHOLE call.
                    .callTimeout(6, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }

    /**
     * Whether a Qobuz account is linked. Touches the encrypted vault (disk + AndroidKeyStore), so it is
     * suspend and never throws: this is evaluated on the playback resolve path, where an escaping
     * exception would abort stream resolution for a song Qobuz was never required to serve.
     */
    suspend fun isLinked(): Boolean =
        try {
            store?.isLoggedIn() == true
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            Timber.tag(TAG).w("Qobuz vault unreadable (%s) — treating as not linked", e.javaClass.simpleName)
            false
        }

    /**
     * The owner's own-subscription path is only taken when linked AND enabled.
     *
     * [enabled] is checked FIRST on purpose: it is a plain volatile read, so a user who never linked
     * Qobuz (preference default-off) pays nothing — no disk, no keystore, no coroutine hop.
     */
    suspend fun isActive(): Boolean = enabled && isLinked()

    /**
     * Resolve [title]/[artist] to a signed, LOSSLESS Qobuz stream, or null to let the caller fall back
     * (proxy → Saavn → Opus). NEVER throws — the call site in YTPlayerUtils has no try/catch of its own,
     * and it runs inside the `runBlocking` MusicService resolves streams with, so anything escaping here
     * would surface as a playback failure for a song Qobuz was merely an optimisation for. Cancellation
     * (the caller's timeout, a song change) is propagated, everything else degrades to "miss".
     * [durationMs] is used only to disambiguate matches.
     */
    suspend fun resolve(artist: String, title: String, durationMs: Long?): QobuzStream? {
        if (!enabled) return null
        return try {
            val session = store?.session() ?: return null
            val api = this.api ?: return null
            val match = findBestMatch(api, session, artist, title, durationMs)
            if (match == null) {
                Timber.tag(TAG).d("No confident Qobuz match for own-subscription stream")
                null
            } else {
                fetchStream(api, session, match.id)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            Timber.tag(TAG).w("Qobuz resolve failed (%s) — falling back", e.javaClass.simpleName)
            null
        }
    }

    /** Explicit "reproducir en Qobuz" by an already-known Qobuz track id (song-menu action / phase 2). */
    suspend fun resolveTrackId(trackId: Long): QobuzStream? =
        try {
            val session = store?.session()
            val api = this.api
            if (session == null || api == null) null else fetchStream(api, session, trackId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Throwable) {
            Timber.tag(TAG).w("Qobuz resolveTrackId failed (%s) — falling back", e.javaClass.simpleName)
            null
        }

    private suspend fun findBestMatch(
        api: QobuzApi,
        session: QobuzSession,
        artist: String,
        title: String,
        durationMs: Long?,
    ): QobuzTrack? {
        for (term in qobuzSearchTerms(artist, title)) {
            val candidates = api.searchTracks(session.appId, session.token, term)
                .filter { it.streamable && it.maximumBitDepth >= 16 }
            val top = candidates.maxByOrNull { confidence(artist, title, durationMs, it) } ?: continue
            if (confidence(artist, title, durationMs, top) >= MIN_CONFIDENCE) return top
        }
        return null
    }

    /**
     * Ladder 27 → 7 → 6 (see [QobuzQualityFormat.LADDER]), reading back the DELIVERED format / bit_depth /
     * sampling_rate and accepting ONLY a genuinely lossless delivery.
     *
     * Why the delivered format has to be checked at all: Qobuz does not reject an over-ambitious request,
     * it DOWNGRADES it server-side. A free/lossy-tier account asking for format 27 gets a perfectly
     * "playable" `format_id=5`, `mime_type="audio/mpeg"` answer — accepting that would return MP3 320 as
     * the lossless winner and pre-empt the proxy that would have delivered real FLAC, and would make the
     * same track flip container between passes (MusicService turns that into
     * ERROR_CODE_PARSING_CONTAINER_MALFORMED mid-song — registry #57). So a lossy delivery is a MISS: we
     * stop the ladder (a lower rung can only be lossier) and return null so the caller falls back.
     *
     * A 401 means the token died (link needs refreshing) → give up and fall back.
     */
    private suspend fun fetchStream(api: QobuzApi, session: QobuzSession, trackId: Long): QobuzStream? {
        for (formatId in QobuzQualityFormat.LADDER) {
            when (val r = api.getFileUrl(session.appId, session.appSecret, session.token, trackId, formatId)) {
                is QobuzApi.FileUrlResult.Ok -> {
                    val resp = r.response
                    if (resp.isLosslessDelivery) {
                        // Non-null / in-range by isLosslessDelivery: url present, depth >= 16, rate > 0.
                        val hz = ((resp.samplingRate ?: 0.0) * 1000).toInt()
                        val depth = resp.bitDepth ?: 16
                        Timber.tag(TAG).d(
                            "Qobuz own stream: track=%d requested=%d delivered=%d/%dbit %dHz",
                            trackId, formatId, resp.formatId, depth, hz,
                        )
                        return QobuzStream(
                            url = resp.url!!,
                            formatId = resp.formatId,
                            bitDepth = depth,
                            samplingRateHz = hz,
                            mimeType = resp.mimeType ?: "audio/flac",
                            // Truthful only because the delivery is verified lossless PCM (stereo).
                            bitrateBps = hz * depth * CHANNELS,
                        )
                    }
                    if (resp.isPlayableStream) {
                        // Playable but NOT lossless => the account was downgraded server-side. Every lower
                        // rung would be lossy too, so stop and let the proxy/normal path serve this track.
                        Timber.tag(TAG).d(
                            "Qobuz delivered LOSSY for track=%d (requested=%d delivered=%d mime=%s) — " +
                                "not a win, falling back",
                            trackId, formatId, resp.formatId, resp.mimeType,
                        )
                        return null
                    }
                    // A `sample`/preview at this format (free tier or track not entitled) — try lower.
                }
                QobuzApi.FileUrlResult.Unauthorized -> {
                    Timber.tag(TAG).w("Qobuz token unauthorized during getFileUrl — link may have expired")
                    return null
                }
                QobuzApi.FileUrlResult.BadSignature,
                is QobuzApi.FileUrlResult.Failed -> Unit
            }
        }
        return null
    }

    private const val TAG = "QobuzHiRes"
    private const val MIN_CONFIDENCE = 0.6f
    private const val CHANNELS = 2
}
