

package iad1tya.echo.music.utils

import android.net.ConnectivityManager
import android.util.Log
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import iad1tya.echo.music.utils.BotDetectionMitigator
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import iad1tya.echo.music.constants.AudioQuality
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.YTPlayerUtils.MAIN_CLIENT
import iad1tya.echo.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import iad1tya.echo.music.utils.YTPlayerUtils.validateStatus
import iad1tya.echo.music.utils.potoken.PoTokenGenerator
import iad1tya.echo.music.utils.potoken.PoTokenResult
import iad1tya.echo.music.utils.sabr.EjsNTransformSolver
import iad1tya.echo.music.utils.PlaybackLogLevel
import iad1tya.echo.music.utils.PlaybackLogManager
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"
    private var hasShownLosslessToast = false
    private var hasShownSaavnToast = false

    // The signature timestamp (sts) is a per-PLAYER-VERSION constant — identical for every video until
    // YouTube rotates player.js (rare, ~weekly). Recomputing it for every song runs NewPipe's JS engine
    // over the ~2.8 MB player.js each time, which is multi-second on weak (TV) CPUs and needlessly
    // repeats work on the critical path of every song change. Memoize the successful value for a bounded
    // window (same 6 h horizon the player.js disk cache already tolerates) so only the first song pays
    // it. Only SUCCESSES are cached — a failure (which also carries the early age-restriction hint)
    // always re-runs, so nothing is lost.
    @Volatile private var cachedSignatureTimestamp: Int? = null
    @Volatile private var cachedSignatureTimestampAtMs: Long = 0L
    private const val SIGNATURE_TIMESTAMP_TTL_MS = 6 * 60 * 60 * 1000L

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Timber.tag(TAG).e(ioe, "Proxy connection failed for URI: $uri")
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // FIX B2 (#28.1): the validateStatus() HEAD probe gets its OWN short-timeout client so a slow/blocked
    // candidate URL fails FAST (≈4-5s) instead of stalling start-up all the way to RESOLVE_TIMEOUT_MS (30s).
    // Shares the connection pool / dns / proxy config of httpClient (newBuilder), only the timeouts differ —
    // the MAIN streaming client's 15s timeouts (used by the real byte fetch) are left untouched.
    private val validateHttpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /**
     * Warm up the poToken WebView ahead of the first playback so the first song starts faster. Safe to
     * call any time (no-ops if the session isn't ready yet); never throws.
     */
    fun prewarmPoToken() {
        runCatching {
            if (!MAIN_CLIENT.useWebPoTokens) return@runCatching
            val isLoggedIn = YouTube.cookie != null
            val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
            if (!sessionId.isNullOrBlank()) poTokenGenerator.prewarm(sessionId)
        }
    }

    /**
     * Warm the cipher player.js + WebView ahead of the first play so the first song's URL resolution
     * doesn't pay the ~2.8 MB player.js fetch + WebView-create + JS discovery on the critical path.
     * This fills [iad1tya.echo.music.utils.cipher.PlayerJsFetcher]'s cache (shared by the sig-deobf and
     * EJS n-transform paths) and pre-creates the reused cipher WebView; both stay warm and are reused
     * for every subsequent song. Best-effort — never throws, never blocks. Call off the main thread.
     */
    suspend fun prewarmCipher() {
        runCatching { CipherDeobfuscator.prewarm() }
            .onFailure { Timber.tag(TAG).d("Cipher prewarm skipped: ${it.message}") }
    }

    
    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32

    // For VIDEO mode we need a client that returns muxed (video+audio) progressive formats — the music/VR
    // clients only return adaptive (separate) streams, so the video URL came back null and only audio
    // played. TVHTML5 reliably exposes itag 18 (360p) / 22 (720p) muxed without web PoTokens.
    private val VIDEO_CLIENT: YouTubeClient = TVHTML5


    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48,
        WEB_REMIX,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,  
        TVHTML5,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        IOS,
        WEB,
        WEB_CREATOR
    )
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val isSaavnStream: Boolean = false,
    )

    /**
     * Thrown when a song genuinely CANNOT be served by any client — region-locked, premium/members-only,
     * deleted-but-listed, age-restricted-for-guests, some music-video-only / podcast ids, no playable
     * format / no stream URL, or the whole resolution timed out. This is a DEAD-END, NOT a network
     * problem: the loader maps it to [MusicService.ERROR_CODE_NO_STREAM] (never a network code), so the
     * song fails FAST, ONCE, with a clear message + auto-skip instead of blocking the loader for minutes
     * or looping in a fake "no internet" state. [reason] carries the real playability reason for the UI.
     */
    class StreamResolutionException(
        val reason: String,
        cause: Throwable? = null,
    ) : Exception(reason, cause)

    // Hard cap on the WALL-CLOCK time the YouTube resolve (the 12-client fallback loop) may spend before
    // we give up and surface a NO_STREAM dead-end. Only the YouTube resolve is bounded by this — the
    // Qobuz/Saavn budgets in playerResponseForPlayback are independent and unchanged.
    private const val RESOLVE_TIMEOUT_MS = 30_000L

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null,
        knownDurationMs: Long? = null,
        isDownload: Boolean = false,
        // RINGTONE-ONLY opt-in: pick the SMALLEST audio format instead of the Hi-Res one (the ringtone
        // trimmer keeps a few seconds anyway, so transfer size matters more than bitrate). Every other
        // caller keeps the default (false) and its selection is byte-identical to before.
        preferSmallestAudio: Boolean = false
    ): Result<PlaybackData> {
        val showFallbackToast = context?.let { 
            it.dataStore.data.first()[iad1tya.echo.music.constants.ShowAudioFallbackToastKey] 
        } ?: true

        var losslessFailed = false
        if (audioQuality == AudioQuality.LOSSLESS) {
            var qobuzAttempt: Result<PlaybackData>? = null
            var lastException: Exception? = null
            // 2×9s (was 3×15s): caps the worst-case lossless wait at ~18s before falling back to
            // Saavn/Opus, so a slow/down Qobuz no longer makes every HiFi track take up to 45s to start.
            for (attempt in 1..2) {
                try {
                    qobuzAttempt = kotlinx.coroutines.withTimeoutOrNull(9000L) {
                        val metadata = playerResponseForMetadata(videoId).getOrNull()
                        val title = knownTitle ?: metadata?.videoDetails?.title
                        val author = knownArtist ?: metadata?.videoDetails?.author?.replace(" - Topic", "")
                        if (title != null && author != null) {
                            val qobuzClient = iad1tya.echo.music.utils.qobuz.QobuzApiClient()
                            val queryArtist = author
                            val queryTitle = title
                            val durationSeconds = metadata?.videoDetails?.lengthSeconds?.toLongOrNull()
                            val durationMs = knownDurationMs ?: (if (durationSeconds != null) durationSeconds * 1000L else null)
                            
                            var bestMatch: iad1tya.echo.music.utils.qobuz.QobuzTrack? = null
                            for (term in qobuzSearchTerms(queryArtist, queryTitle)) {
                                val searchResult = runCatching { qobuzClient.search(term) }.getOrNull() ?: continue
                                val candidates = searchResult.tracks?.items ?: continue
                                val validCandidates = candidates.filter {
                                    val streamable = it.streamable ?: false
                                    val maxDepth = it.maximumBitDepth ?: 0
                                    streamable && maxDepth >= 16
                                }
                                val sorted = validCandidates.sortedByDescending { confidence(queryArtist, queryTitle, durationMs, it) }
                                if (sorted.isNotEmpty()) {
                                    val top = sorted.first()
                                    if (confidence(queryArtist, queryTitle, durationMs, top) >= 0.6f) {
                                        bestMatch = top
                                        break
                                    }
                                }
                            }
    
                            if (bestMatch != null) {
                                val downloadData = qobuzClient.getFileUrl(bestMatch.id)
                                val url = downloadData.url
                                if (url != null) {
                                    val format = PlayerResponse.StreamingData.Format(
                                        itag = 0,
                                        mimeType = "audio/flac; codecs=\"flac\"",
                                        bitrate = (bestMatch.maximumSamplingRate * 1000 * bestMatch.maximumBitDepth * 2).toInt(),
                                        audioSampleRate = (bestMatch.maximumSamplingRate * 1000).toInt(),
                                        contentLength = 0L,
                                        url = url,
                                        cipher = null,
                                        signatureCipher = null,
                                        audioQuality = "LOSSLESS",
                                        fps = null,
                                        width = null,
                                        height = null,
                                        quality = "lossless",
                                        qualityLabel = null,
                                        averageBitrate = null,
                                        approxDurationMs = null,
                                        audioChannels = null,
                                        loudnessDb = null,
                                        lastModified = null,
                                        audioTrack = null
                                    )
                                    val playbackData = PlaybackData(
                                        audioConfig = null,
                                        videoDetails = metadata?.videoDetails,
                                        playbackTracking = null,
                                        format = format,
                                        streamUrl = url,
                                        streamExpiresInSeconds = 3600 // 1 hour for squid
                                    )
                                    return@withTimeoutOrNull Result.success(playbackData)
                                } else {
                                    throw Exception("Download URL is null")
                                }
                            } else {
                                throw Exception("No streamable match found on Qobuz")
                            }
                        } else {
                            throw Exception("Missing title or artist for lookup")
                        }
                    }
                    if (qobuzAttempt == null) {
                        lastException = Exception("Timeout fetching Qobuz stream")
                    }
                } catch (e: Exception) {
                    lastException = e
                }
                
                if (qobuzAttempt != null && qobuzAttempt.isSuccess) {
                    break
                }
            }
            if (qobuzAttempt != null && qobuzAttempt.isSuccess) {
                return qobuzAttempt
            } else {
                losslessFailed = true
                Timber.tag(TAG).e(lastException, "Qobuz resolution failed, falling back to Saavn")
                context?.let {
                    if (showFallbackToast && !hasShownLosslessToast) {
                        hasShownLosslessToast = true
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (isDownload) {
                                android.widget.Toast.makeText(it, "Descarga sin pérdida no disponible; se usa Saavn (320 kbps)", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(it, "Transmisión sin pérdida no disponible; se usa Saavn (320 kbps)", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        
        var saavnFailed = false
        if (audioQuality == AudioQuality.SAAVN || losslessFailed) {
            var saavnAttempt: Result<PlaybackData>? = null
            var lastException: Exception? = null
            
            Timber.tag(TAG).d("JioSaavn streaming enabled (via SAAVN) — trying Saavn for videoId=$videoId")
            try {
                saavnAttempt = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    val metadata = playerResponseForMetadata(videoId).getOrNull()
                    val title = knownTitle ?: metadata?.videoDetails?.title.orEmpty()
                    val artist = knownArtist ?: metadata?.videoDetails?.author?.replace(" - Topic", "").orEmpty()

                    if (title.isBlank()) throw Exception("Title is blank")

                    val query = "$title $artist"
                        .replace("&", " ")
                        .replace(",", " ")
                        .replace(Regex("(?i)\\s*-\\s*topic\\b"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    Timber.tag(TAG).d("Saavn search query: \"$query\" (original: \"$title $artist\")")

                    val songs = com.music.jiosaavn.SaavnService.searchSongs(query).getOrNull()
                    if (songs.isNullOrEmpty()) {
                        throw Exception("Saavn: no results for \"$query\"")
                    }

                    val ytDuration = knownDurationMs?.let { it / 1000L } ?: metadata?.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0L

                    fun normalize(s: String): Set<String> =
                        s.lowercase()
                            .replace(Regex("[^a-z0-9\\s]"), " ")
                            .split(Regex("\\s+"))
                            .filter { it.length > 1 }
                            .toSet()

                    fun wordOverlapScore(a: String, b: String, maxPts: Int): Int {
                        val setA = normalize(a)
                        val setB = normalize(b)
                        if (setA.isEmpty() || setB.isEmpty()) return 0
                        val common = setA.intersect(setB).size
                        val ratio  = common.toDouble() / maxOf(setA.size, setB.size)
                        return (ratio * maxPts).toInt()
                    }

                    data class ScoredSong(val song: com.music.jiosaavn.SaavnSong, val score: Int, val artistOk: Boolean)

                    val scored = songs.map { candidate ->
                        var score = 0
                        score += wordOverlapScore(title, candidate.name, maxPts = 50)
                        val saavnDuration = candidate.duration?.toLong() ?: 0L
                        if (ytDuration > 0 && saavnDuration > 0) {
                            val diff = Math.abs(ytDuration - saavnDuration)
                            score += when {
                                diff <= 5  -> 30
                                diff <= 15 -> 15
                                else       -> 0
                            }
                        }
                        val saavnArtists = candidate.artists.primary.joinToString(" ") { it.name }
                        val artistScore = wordOverlapScore(artist, saavnArtists, maxPts = 20)
                        score += artistScore
                        if (candidate.explicitContent) score += 5
                        // Penalize karaoke/instrumental/remix/etc. variants unless the YT title itself
                        // asks for that variant — avoids substituting a karaoke version of the song.
                        score += com.music.jiosaavn.SaavnMatcher.variantPenalty(title, candidate.name)
                        // Require a real artist match (when we know the YT artist). Title alone could
                        // otherwise pass the threshold and substitute a DIFFERENT artist's same-titled
                        // song (e.g. another "Dirt") — the wrong-song bug.
                        val artistOk = artist.isBlank() || artistScore > 0
                        ScoredSong(candidate, score, artistOk)
                    }

                    val MIN_CONFIDENCE = 40
                    val bestSong = scored
                        .filter { it.artistOk }
                        .maxByOrNull { it.score }
                        ?.takeIf { it.score >= MIN_CONFIDENCE }
                        ?.song

                    if (bestSong == null) {
                        throw Exception("Saavn: no same-artist match >= $MIN_CONFIDENCE (won't substitute a different song)")
                    }

                    Timber.tag(TAG).d("Saavn best match: id=${bestSong.id}, name=${bestSong.name}")

                    val streamUrl = com.music.jiosaavn.SaavnService.getBestStreamUrl(bestSong.id, "320kbps")
                    if (streamUrl.isNullOrBlank()) {
                        throw Exception("Saavn: no stream URL for songId=${bestSong.id}")
                    }

                    val format = PlayerResponse.StreamingData.Format(
                        itag = 0,
                        url = streamUrl,
                        mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                        bitrate = 320_000,
                        width = null,
                        height = null,
                        contentLength = null,
                        quality = "320kbps",
                        fps = null,
                        qualityLabel = null,
                        averageBitrate = null,
                        audioQuality = "320kbps",
                        approxDurationMs = null,
                        audioSampleRate = null,
                        audioChannels = null,
                        loudnessDb = null,
                        lastModified = null,
                        signatureCipher = null,
                        cipher = null,
                        audioTrack = null
                    )

                    val playbackData = PlaybackData(
                        audioConfig = metadata?.playerConfig?.audioConfig,
                        videoDetails = metadata?.videoDetails,
                        playbackTracking = metadata?.playbackTracking,
                        format = format,
                        streamUrl = streamUrl,
                        streamExpiresInSeconds = 3600,
                        isSaavnStream = true
                    )
                    Result.success(playbackData)
                }
                
                if (saavnAttempt == null) {
                    lastException = Exception("Timeout fetching Saavn stream")
                }
            } catch (e: Exception) {
                lastException = e
            }
            
            if (saavnAttempt != null && saavnAttempt.isSuccess) {
                return saavnAttempt
            } else {
                saavnFailed = true
                Timber.tag(TAG).e(lastException, "Saavn resolution failed, falling back to YouTube Opus")
                context?.let {
                    if (showFallbackToast && !hasShownSaavnToast) {
                        hasShownSaavnToast = true
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            val msg = if (losslessFailed) {
                                if (isDownload) "Lossless & Saavn unavailable, downloading Opus" else "Lossless & Saavn unavailable, playing Opus"
                            } else {
                                if (isDownload) "Saavn unavailable, downloading Opus" else "Saavn unavailable, playing Opus"
                            }
                            android.widget.Toast.makeText(it, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        
        // BOUND the YouTube resolve (fix #4). The 12-client fallback loop can, for an unserveable song,
        // spend a long time hopping clients + HEAD-validating dead URLs — which the user saw as an endless
        // "loading" / fake "no internet". Cap the wall-clock: on timeout, surface a NO_STREAM dead-end so
        // the loader SKIPS the song instead of hanging. NOTE: resolvePlaybackData's internal runCatching
        // swallows the timeout's CancellationException into a Result.failure, so withTimeoutOrNull returns
        // that failed Result (not null); we detect the swallowed cancellation and remap it to a typed
        // StreamResolutionException so the loader routes it to NO_STREAM (never a network error).
        suspend fun boundedResolve(): Result<PlaybackData> {
            val timeoutReason = "La canción tardó demasiado en resolverse"
            val r = kotlinx.coroutines.withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager, preferSmallestAudio = preferSmallestAudio)
            } ?: return Result.failure(StreamResolutionException(timeoutReason))
            return if (r.exceptionOrNull() is java.util.concurrent.CancellationException) {
                Result.failure(StreamResolutionException(timeoutReason))
            } else r
        }

        val firstAttempt = boundedResolve()

        if (firstAttempt.isFailure && YouTube.cookie == null) {
            Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
            PlaybackLogManager.log(PlaybackLogLevel.BOT, "Playback failed for guest", "Triggering bot detection mitigation (rotating guest session)")
            BotDetectionMitigator.rotateGuestSession()
            val retryResult = boundedResolve()
            retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return retryResult
        }

        firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
        return firstAttempt
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferVideo: Boolean = false,
        videoMaxHeight: Int? = null,
        preferSmallestAudio: Boolean = false,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data", "Video: $videoId")
        
        
        println("[PLAYBACK_DEBUG] playerResponseForPlayback called: videoId=$videoId, playlistId=$playlistId")
        
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).d("Signature timestamp: ${signatureTimestamp.timestamp}")

        
        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            Timber.tag(logTag).d("Generating PoToken for MAIN_CLIENT with sessionId")
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully")
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
            }
        }

        
        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying ${MAIN_CLIENT.clientName} (Main)")

        // Run the main stream request and the optional metadata request IN PARALLEL — they are independent
        // network calls, so overlapping them (instead of back-to-back) cuts the start time. The metadata
        // (watch-history + loudness audioConfig) needs no stream poToken; it is time-capped so it can never
        // delay playback, and audioConfig/videoDetails fall back to the main response when it is null.
        val resolved = kotlinx.coroutines.coroutineScope {
            val metadataDeferred = if (isLoggedIn) {
                async(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        runCatching {
                            YouTube.player(
                                videoId, playlistId, METADATA_CLIENT,
                                signatureTimestamp.timestamp, null
                            ).getOrNull()
                        }.getOrNull()
                    }
                }
            } else null
            // Video mode needs a client that actually returns VIDEO adaptive formats. MAIN_CLIENT
            // (ANDROID_VR) is audio-focused and returns no usable video, so for video we query
            // VIDEO_CLIENT (TVHTML5), which reliably returns adaptive video. The player then merges this
            // video-only track with the (separately resolved, MAIN_CLIENT) audio track. Audio is untouched.
            // getOrNull (NOT getOrThrow): a MAIN_CLIENT failure / non-OK response must NOT abort the whole
            // resolution (fix #5). A null (or non-OK) main response now falls through to the
            // STREAM_FALLBACK_CLIENTS loop (startIndex is forced to 0 below when main is null), so
            // region-locked / members-only / deleted-but-listed songs still get EVERY fallback client
            // instead of dead-ending on the very first client.
            val main = if (preferVideo) {
                YouTube.player(
                    videoId, playlistId, VIDEO_CLIENT,
                    signatureTimestamp.timestamp, null,
                ).getOrNull()
            } else {
                YouTube.player(
                    videoId, playlistId, MAIN_CLIENT,
                    signatureTimestamp.timestamp, poToken?.playerRequestPoToken,
                ).getOrNull()
            }
            main to metadataDeferred?.await()
        }
        var mainPlayerResponse = resolved.first
        var metadataResponse: PlayerResponse? = resolved.second

        
        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse?.playabilityStatus?.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse?.playabilityStatus?.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse?.videoDetails?.title}, videoId=${mainPlayerResponse?.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse?.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse?.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        
        
        
        
        
        val mainStatus = mainPlayerResponse?.playabilityStatus?.status
        val isAgeRestrictedFromResponse = mainStatus != null && mainStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {

            Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
            Log.i(TAG, "Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        } else if (isAgeRestrictedFromResponse && !isLoggedIn) {
            // GUEST age-restricted path (fix #5): WEB_CREATOR is login-gated, so signed-out users used to
            // dead-end on age-restricted content. The TV embedded player commonly serves age-gated streams
            // WITHOUT auth — try it so age restriction isn't a guaranteed dead-end for guests.
            Timber.tag(logTag).d("Age-restricted (guest), trying embedded player TVHTML5_SIMPLY_EMBEDDED_PLAYER")
            Log.i(TAG, "Age-restricted (guest): using TVHTML5_SIMPLY_EMBEDDED_PLAYER for videoId=$videoId")
            val embedResponse = YouTube.player(videoId, playlistId, TVHTML5_SIMPLY_EMBEDDED_PLAYER, null, null).getOrNull()
            if (embedResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("Embedded player works for age-restricted (guest) content")
                mainPlayerResponse = embedResponse
                usedAgeRestrictedClient = TVHTML5_SIMPLY_EMBEDDED_PLAYER
            }
        }

        // NOTE (fix #5): a null mainPlayerResponse is NO LONGER a hard failure/abort. We fall through to
        // the STREAM_FALLBACK_CLIENTS loop (startIndex forced to 0 below when main is null) so every
        // fallback client still gets a chance instead of dead-ending here.
        if (mainPlayerResponse == null) {
            Timber.tag(logTag).w("MAIN_CLIENT returned no response; continuing into fallback clients from index 0")
        }



        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse?.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse?.videoDetails
        val playbackTracking = metadataResponse?.playbackTracking ?: mainPlayerResponse?.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        // Carries the most recent real playability reason seen while iterating clients, so an
        // all-clients-exhausted dead-end can surface WHY (region-locked, members-only, …) to the user.
        var lastPlayabilityReason: String? = null
        var retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null


        val currentStatus = mainPlayerResponse?.playabilityStatus?.status
        var isAgeRestricted = currentStatus != null && currentStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content is still age-restricted (status: $currentStatus), will try fallback clients")
            Log.i(TAG, "Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }


        val isPrivateTrack = mainPlayerResponse?.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"




        val startIndex = when {
            mainPlayerResponse == null -> 0   // no main response to reuse → straight into the fallback clients
            isPrivateTrack -> 1
            isAgeRestricted -> 0
            else -> -1
        }

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            
            val client: YouTubeClient
            if (clientIndex == -1) {
                
                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying fallback [${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}]", client.clientName)

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    
                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                
                if (client.useWebPoTokens && poToken == null && sessionId != null) {
                    Timber.tag(logTag).d("Lazily generating PoToken for fallback web client: ${client.clientName}")
                    try {
                        poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Lazy PoToken generation failed")
                    }
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")
                
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken).getOrNull()
            }

            
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.INFO, "Player response OK", if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName)

                
                val hasDirectUrls = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.url.isNullOrEmpty() } == true
                val hasSignatureCipher = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() } == true

                Timber.tag(logTag).d("URL check: hasDirectUrls=$hasDirectUrls, hasSignatureCipher=$hasSignatureCipher")

                
                val responseToUse = streamPlayerResponse

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                        preferVideo,
                        videoMaxHeight,
                        preferSmallestAudio,
                    )

                if (format == null) {
                    Timber.tag(logTag).d("No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    continue
                }

                Timber.tag(logTag).d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                if (streamUrl == null) {
                    Timber.tag(logTag).d("Stream URL not found for format")
                    continue
                }

                
                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                
                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                
                if (currentClient.useWebPoTokens) {
                    try {
                        Timber.tag(logTag).d("Applying n-transform to stream URL for ${currentClient.clientName}")
                        val transformed = EjsNTransformSolver.transformNParamInUrl(streamUrl!!)
                        if (transformed != streamUrl) {
                            streamUrl = transformed
                            Timber.tag(logTag).d("N-transform applied successfully")
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "N-transform failed: ${e.message}")
                    }
                }

                
                
                if (currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                    Timber.tag(logTag).d("Appending pot= parameter to stream URL")
                    val separator = if ("?" in streamUrl!!) "&" else "?"
                    streamUrl = "${streamUrl}${separator}pot=${poToken.streamingDataPoToken}"
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).d("Stream expiration time not found")
                    continue
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                
                val urlHost = try { java.net.URL(streamUrl).host } catch (e: Exception) { "unknown" }
                Timber.tag(logTag).d("Stream URL host: $urlHost, pot length: ${poToken?.streamingDataPoToken?.length ?: 0}")

                
                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1 || isPrivatelyOwned) {
                    
                    if (isPrivatelyOwned) {
                        Timber.tag(logTag).d("Skipping validation for privately owned track: ${currentClient.clientName}")
                        println("[PLAYBACK_DEBUG] Using stream without validation for PRIVATELY_OWNED_TRACK")
                    } else {
                        Timber.tag(logTag).d("Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    }
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId, private=$isPrivatelyOwned")
                    break
                }

                if (validateStatus(streamUrl!!)) {
                    // FIX B3 (#28.1): SHORT-CIRCUIT — the moment ANY client (including the MAIN client at
                    // clientIndex == -1, tried FIRST) yields a validated, directly-usable URL we break out of
                    // the loop and return it immediately, WITHOUT probing the remaining fallback clients. The
                    // full fallback chain still runs only when the main client fails to validate.
                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    PlaybackLogManager.log(PlaybackLogLevel.INFO, "Stream validated", currentClient.clientName)
                    
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")

                    
                    if (currentClient.useWebPoTokens) {
                        var nTransformWorked = false

                        
                        try {
                            val nTransformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                            if (nTransformed != streamUrl) {
                                Timber.tag(logTag).d("CipherDeobfuscator n-transform applied, re-validating...")
                                if (validateStatus(nTransformed)) {
                                    Timber.tag(logTag).d("N-transformed URL VALIDATED OK!")
                                    streamUrl = nTransformed
                                    nTransformWorked = true
                                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId (cipher n-transform)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(logTag).e(e, "CipherDeobfuscator n-transform error")
                        }

                        if (nTransformWorked) break
                    }
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status ?: "Unknown"
                val reason = streamPlayerResponse?.playabilityStatus?.reason ?: "No reason"
                // Remember the real reason (e.g. region/premium/members) so an all-clients-exhausted
                // dead-end can tell the user WHY instead of a generic failure.
                streamPlayerResponse?.playabilityStatus?.reason?.let { lastPlayabilityReason = it }
                Timber.tag(logTag).d("Player response status not OK: $status, reason: $reason")
                PlaybackLogManager.log(PlaybackLogLevel.WARNING, "Client failed: ${client.clientName}", "$status: $reason")
                
                
                Timber.tag(logTag).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            // DEAD-END (fix #1): no client could serve this song. Typed so the loader maps it to NO_STREAM
            // (skip + message), NEVER a network code — carry the real reason when we captured one.
            throw StreamResolutionException(lastPlayabilityReason ?: "No hay ninguna fuente disponible para esta canción")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            // DEAD-END (fix #1): carry the real playability reason (region/premium/members/…) so the loader
            // maps it to NO_STREAM with that reason instead of a generic REMOTE_ERROR silent pause.
            throw StreamResolutionException(errorReason ?: lastPlayabilityReason ?: "Esta canción no está disponible")
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw StreamResolutionException(lastPlayabilityReason ?: "No se pudo obtener el stream de esta canción")
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw StreamResolutionException(lastPlayabilityReason ?: "No hay un formato reproducible para esta canción")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            throw StreamResolutionException(lastPlayabilityReason ?: "No se pudo obtener el enlace de esta canción")
        }

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl?.take(100)}...")
        }
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }.onFailure { e ->
        Timber.tag(logTag).e(e, "Playback resolution failed")
        PlaybackLogManager.log(PlaybackLogLevel.ERROR, "Playback failed", "${e::class.simpleName}: ${e.message}")
        
        
        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }
    
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) 
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    /**
     * Resolves a muxed (video+audio) progressive stream URL for [videoId], reusing the same
     * multi-client + cipher pipeline as audio. Returns null if no muxed format is available.
     */
    suspend fun videoStreamUrl(
        videoId: String,
        connectivityManager: ConnectivityManager,
        videoMaxHeight: Int? = null,
    ): String? = resolvePlaybackData(
        videoId = videoId,
        audioQuality = AudioQuality.OPUS,
        connectivityManager = connectivityManager,
        preferVideo = true,
        videoMaxHeight = videoMaxHeight,
    ).getOrNull()?.streamUrl

    /**
     * Diagnostic variant of [videoStreamUrl]: returns the failure REASON instead of swallowing it, plus
     * a count of how many video adaptive formats the chosen client actually exposed — so the UI can show
     * exactly why video mode failed (no format vs network vs playability vs decipher).
     */
    suspend fun videoStreamUrlDiag(
        videoId: String,
        connectivityManager: ConnectivityManager,
        videoMaxHeight: Int? = null,
    ): Result<String> = resolvePlaybackData(
        videoId = videoId,
        audioQuality = AudioQuality.OPUS,
        connectivityManager = connectivityManager,
        preferVideo = true,
        videoMaxHeight = videoMaxHeight,
    ).mapCatching { it.streamUrl }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferVideo: Boolean = false,
        videoMaxHeight: Int? = null,
        preferSmallestAudio: Boolean = false,
    ): PlayerResponse.StreamingData.Format? {
        if (preferVideo) {
            // Video mode resolves an ADAPTIVE VIDEO-ONLY stream (no audio) — MusicService MERGES it with the
            // track's normal audio source, giving real HD. Muxed (streamingData.formats) only reliably offers
            // 360p (itag 18); the 720p muxed (itag 22) is gone for most videos. Video-only adaptive, by
            // contrast, exposes 360/480/720/1080 for virtually every video. Pick by REAL pixel height and the
            // connection: WiFi up to 720p, mobile data up to 360p (lighter, fewer stalls). Prefer H.264/mp4
            // (widest ExoPlayer compatibility, smooth on low-end), else any video-only. null only if a video
            // has no video-only format at all (extremely rare) → caller keeps audio + "no disponible".
            val metered = connectivityManager.isActiveNetworkMetered
            // On TV (big screen) the caller passes an explicit cap (1080p Full HD) so video mode reaches true FHD
            // via a VIDEO-ONLY adaptive stream (merged with a separate audio track in MusicService). Phones/tablets
            // pass null → keep the existing metered-aware cap (720p WiFi / 360p data) EXACTLY as before.
            val targetHeight = videoMaxHeight ?: if (metered) 360 else 720
            val videoOnly = playerResponse.streamingData?.adaptiveFormats
                ?.filter { !it.url.isNullOrEmpty() || !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() }
                ?.filter { !it.isAudio && it.mimeType.startsWith("video/") }
            if (videoOnly.isNullOrEmpty()) return null
            // H.264 ONLY = widest hardware-decode compatibility on low-end. Match the codec token (avc1/avc3),
            // NOT the "mp4" container — AV1 is ALSO delivered as video/mp4 (codecs="av01...") and many low-end
            // devices can't hardware-decode it (→ decode error or stuttering software decode). VP9 excluded too.
            val avc = videoOnly.filter {
                val mt = it.mimeType.lowercase()
                (mt.contains("avc1") || mt.contains("avc3")) && !mt.contains("av01") && !mt.contains("vp9") && !mt.contains("vp09")
            }
            val pool = if (avc.isNotEmpty()) avc else videoOnly
            // Highest quality at or below the target height; if none qualifies, the lowest available.
            return pool.filter { (it.height ?: 0) <= targetHeight }.maxByOrNull { it.height ?: 0 }
                ?: pool.minByOrNull { it.height ?: Int.MAX_VALUE }
        }

        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        // NOTE (audit L10): we deliberately do NOT cap audio bitrate on mobile data — this is a Hi-Res player,
        // so audio always streams at full quality (only VIDEO downgrades on metered, above). The `when` below
        // is intentionally uniform; audio quality is honoured via the original-stream preference.
        //
        // REGION-SAFE fallback: prefer the ORIGINAL (untagged) audio track, BUT on auto-dub regions/accounts
        // YouTube tags EVERY adaptive audio format with an audioTrack, so `isOriginal` matches nothing and the
        // old code returned null → the song failed to play ("works on my device, not on others"). Fall back to
        // a non-auto-dubbed track, then to ANY audio, so playback always resolves. The dev-device path is
        // unchanged (original still wins when it exists).
        val audioFormats = playerResponse.streamingData?.adaptiveFormats?.filter { it.isAudio }
        val audioPool = audioFormats?.filter { it.isOriginal }?.takeIf { it.isNotEmpty() }
            ?: audioFormats?.filter { it.audioTrack?.isAutoDubbed == false }?.takeIf { it.isNotEmpty() }
            ?: audioFormats
        if (audioPool != null && audioPool.none { it.isOriginal }) {
            Timber.tag(logTag).w("No original audio track (auto-dub region) — using non-dubbed/any fallback")
        }

        // RINGTONE-ONLY (preferSmallestAudio): the trimmer keeps a few seconds, so fetch the smallest
        // transferable audio stream (e.g. ~50kbps Opus itag 249) instead of the Hi-Res pick. Applied
        // after the same original/non-dubbed pool selection so region behaviour is identical.
        if (preferSmallestAudio) {
            val smallest = audioPool?.minByOrNull { it.bitrate }
            if (smallest != null) {
                Timber.tag(logTag).d("Selected SMALLEST format (ringtone): ${smallest.mimeType}, bitrate: ${smallest.bitrate}")
            } else {
                Timber.tag(logTag).d("No suitable audio format found (ringtone/smallest)")
            }
            return smallest
        }

        val format = audioPool
            ?.maxByOrNull {
                var score = it.bitrate.toFloat()
                // If Opus is requested, Opus (audio/webm) is vastly superior in codec efficiency.
                // We multiply its bitrate by 2.0 to ensure 160kbps Opus (itag 251) definitively
                // beats 256kbps AAC (itag 141), preserving the true Hi-Res Opus stream.
                if (audioQuality == AudioQuality.OPUS && it.mimeType.startsWith("audio/webm")) {
                    score *= 2.0f
                }
                score
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }
    
    private fun validateStatus(url: String): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)

            // Do NOT attach YouTube.cookie here. The main resolver client (ANDROID_VR) is loginSupported=false
            // and the real ExoPlayer byte fetch (OkHttpDataSource) sends NO cookie, so this validation HEAD
            // must mirror it. Attaching a stale/foreign cookie (e.g. one reinstalled by a backup restore, or an
            // expired session) makes googlevideo answer 401/403 on the HEAD → a perfectly playable URL is
            // discarded → all clients exhausted → NO_STREAM → "no reproduce". An invalid cookie here is
            // strictly worse than none, and the cookie adds nothing to a HEAD on a session-less googlevideo URL.

            // Close the Response on every path (.use) — a HEAD still carries a body/connection that
            // otherwise leaks into the pool on each stream validation.
            // FIX B2: use the SHORT-timeout validation client so a dead/slow candidate fails fast.
            validateHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val isSuccessful = response.isSuccessful
                Timber.tag(logTag).d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})")
                return isSuccessful
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        // Reuse the memoized sts if still fresh — it's the same for every video until the player rotates,
        // so this skips the per-song hop into NewPipe (regex over the ~2.8 MB player JS, plus the
        // first-call download/parse) for a value that never changes between songs.
        val cached = cachedSignatureTimestamp
        if (cached != null &&
            android.os.SystemClock.elapsedRealtime() - cachedSignatureTimestampAtMs < SIGNATURE_TIMESTAMP_TTL_MS) {
            Timber.tag(logTag).d("Signature timestamp (cached): $cached")
            return SignatureTimestampResult(cached, isAgeRestricted = false)
        }
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                cachedSignatureTimestamp = timestamp
                cachedSignatureTimestampAtMs = android.os.SystemClock.elapsedRealtime()
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Log.i(TAG, "Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        
        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }

        
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }

        
        if (skipNewPipe) {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            return null
        }

        
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        
        Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                return streamUrl
            }

            
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}




fun qobuzSearchTerms(artist: String, title: String): List<String> {
    val full = "$artist $title".trim()
    val primary = artist.substringBefore(",").trim()
    return if (primary.isNotEmpty() && !primary.equals(artist.trim(), ignoreCase = true)) {
        listOf(full, "$primary $title".trim())
    } else {
        listOf(full)
    }
}

private fun normalize(s: String): String =
    s.lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\[[^]]*\\]"), " ")
        .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring)\\b.*"), " ")
        .replace(Regex("[''`]"), "")
        .replace(Regex("[^\\p{L}\\p{N}\\p{S}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun jaccard(a: String, b: String): Float {
    val setA = a.split(" ").filter { it.isNotEmpty() }.toSet()
    val setB = b.split(" ").filter { it.isNotEmpty() }.toSet()
    if (setA.isEmpty() || setB.isEmpty()) return 0f
    val intersection = setA.intersect(setB).size.toFloat()
    val union = setA.union(setB).size.toFloat()
    return intersection / union
}

private fun artistSimilarity(a: String, b: String): Float {
    val setA = a.split(" ").filter { it.isNotEmpty() }.toSet()
    val setB = b.split(" ").filter { it.isNotEmpty() }.toSet()
    if (setA.isEmpty() || setB.isEmpty()) return 0f

    val intersection = setA.intersect(setB)
    val union = setA.union(setB)
    val jaccardScore = intersection.size.toFloat() / union.size.toFloat()

    val smallerSize = minOf(setA.size, setB.size)
    val smallerFullyCovered = intersection.size == smallerSize
    val hasDistinctiveOverlap = intersection.any { token ->
        token.length > 3 || token.any { ch -> !ch.isLetterOrDigit() }
    }

    val coverageScore = if (smallerFullyCovered && hasDistinctiveOverlap) 1.0f else 0f
    return maxOf(jaccardScore, coverageScore)
}

fun confidence(queryArtist: String, queryTitle: String, queryDuration: Long?, candidate: iad1tya.echo.music.utils.qobuz.QobuzTrack): Float {
    if (!candidate.streamable) return 0f

    val titleSim = jaccard(normalize(queryTitle), normalize(candidate.title))
    val artistSim = artistSimilarity(
        normalize(queryArtist),
        normalize(candidate.performer?.name.orEmpty()),
    )

    val durationFactor: Float = run {
        val queryMs = queryDuration ?: return@run 1.0f
        if (queryMs <= 0 || candidate.duration <= 0) return@run 1.0f
        val candidateMs = candidate.duration * 1000L
        val drift = kotlin.math.abs(queryMs - candidateMs).toDouble() / queryMs.toDouble()
        when {
            drift < 0.05 -> 1.0f      
            drift < 0.10 -> 0.85f     
            drift < 0.20 -> 0.6f      
            else -> 0.3f              
        }
    }

    return (titleSim * artistSim * durationFactor)
}
