package iad1tya.echo.music.playback.sponsorblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** One skippable [startMs, endMs) region of a video, from the SponsorBlock community DB. */
data class SponsorSegment(val startMs: Long, val endMs: Long, val category: String)

/**
 * Fetches skippable non-music segments for a YouTube video from the community SponsorBlock API
 * (https://sponsor.ajay.app). No API key, no auth, privacy-friendly (only the videoID is sent).
 *
 * We ONLY request categories that are non-music by definition — sponsor, self-promo, interaction reminders,
 * and the "non-music" sections of music videos — so skipping can never cut into real song audio. We do NOT
 * request intro/outro/preview because those overlap genuine song intros/outros on music videos.
 */
object SponsorBlockService {

    private val CATEGORIES = listOf("sponsor", "selfpromo", "interaction", "music_offtopic")
    private const val BASE = "https://sponsor.ajay.app/api/skipSegments"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(BASE).append("?videoID=").append(videoId).append("&actionType=skip")
            CATEGORIES.forEach { append("&category=").append(it) }
        }
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Aura-Hi-Res-Player")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                // 404 = "no segments for this video" — the API's normal empty response, not an error.
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
                parse(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parse(body: String): List<SponsorSegment> {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<SponsorSegment>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val seg = obj.optJSONArray("segment") ?: continue
            if (seg.length() < 2) continue
            val startMs = (seg.optDouble(0, -1.0) * 1000.0).toLong()
            val endMs = (seg.optDouble(1, -1.0) * 1000.0).toLong()
            if (startMs < 0 || endMs <= startMs) continue
            out += SponsorSegment(startMs, endMs, obj.optString("category"))
        }
        return out.sortedBy { it.startMs }
    }
}
