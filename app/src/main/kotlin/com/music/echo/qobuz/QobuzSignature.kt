package iad1tya.echo.music.qobuz

import java.security.MessageDigest

/**
 * MD5 signing for the OFFICIAL Qobuz API (streams the owner's OWN subscription).
 *
 * Only [trackGetFileUrl] is a *signed* request. The signature is an MD5 over the request's object +
 * method + its parameters sorted alphabetically (key then value, no separators), followed by the unix
 * timestamp and the app_secret:
 *
 *   md5("track" + "getFileUrl" + "format_id" + {fid} + "intent" + "stream" + "track_id" + {id} + {ts} + {secret})
 *
 * i.e. `"trackgetFileUrlformat_id"+fid+"intentstreamtrack_id"+id+ts+secret`. The same `ts` value must be
 * sent as `request_ts` and the digest as `request_sig`. This mirrors the public web player exactly, so a
 * correctly-signed request against the user's token returns a temporary, DRM-free signed CDN FLAC url.
 */
internal object QobuzSignature {

    /** Lower-case hex MD5 of [input] (UTF-8). */
    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xff
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    /**
     * Signature for `track/getFileUrl?intent=stream`. [requestTs] is unix SECONDS and MUST equal the
     * `request_ts` query parameter sent alongside `request_sig`.
     */
    fun trackGetFileUrl(
        trackId: Long,
        formatId: Int,
        requestTs: Long,
        appSecret: String,
    ): String {
        val toHash = buildString {
            append("trackgetFileUrl")
            append("format_id").append(formatId)
            append("intentstream")
            append("track_id").append(trackId)
            append(requestTs)
            append(appSecret)
        }
        return md5Hex(toHash)
    }
}
