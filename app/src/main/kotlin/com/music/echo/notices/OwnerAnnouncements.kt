package iad1tya.echo.music.notices

import android.content.Context
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.ReadAnnouncementIdsKey
import iad1tya.echo.music.utils.dataStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

data class OwnerAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val date: String,
    val priority: String = "info",
    val url: String? = null,
)

/** Unread count for badges (avatar / account sheet). Cache must be loaded first. */
fun unreadOwnerNoticeCount(items: List<OwnerAnnouncement>, readIdsCsv: String): Int {
    if (items.isEmpty()) return 0
    val read = readIdsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return items.count { it.id !in read }
}

/**
 * Owner → users notice inbox. Feed lives next to [player_configs.json] on main so a comunicado
 * can ship without an APK. Cache-first; failed fetch keeps last good cache.
 */
object OwnerAnnouncements {
    private const val TAG = "OwnerAnnouncements"
    const val REMOTE_URL =
        "https://raw.githubusercontent.com/hck0n3/Aura-Hi-Res-Player/main/announcements.json"
    private const val CACHE_FILE = "announcements_cache.json"
    private const val MAX_BODY_BYTES = 256 * 1024
    /** Soft throttle on open/resume; Notices screen still uses force=true. */
    private const val MIN_REFRESH_MS = 5L * 60L * 1_000L

    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<OwnerAnnouncement>>(emptyList())
    val items: StateFlow<List<OwnerAnnouncement>> = _items.asStateFlow()

    @Volatile
    private var lastRefreshAtMs: Long = 0L

    fun loadCache(context: Context) {
        runCatching {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return
            val parsed = parse(file.readText())
            if (parsed.isNotEmpty()) _items.value = parsed
        }.onFailure { Timber.tag(TAG).w(it, "loadCache failed") }
    }

    suspend fun refresh(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val since = System.currentTimeMillis() - lastRefreshAtMs
            if (!force && _items.value.isNotEmpty() && since in 0 until MIN_REFRESH_MS) return@withLock
            runCatching {
                // Cache-bust so a fresh push to main is not stuck behind raw.githubusercontent CDN.
                val url = "$REMOTE_URL?t=${System.currentTimeMillis()}"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    useCaches = false
                }
                try {
                    if (conn.responseCode !in 200..299) {
                        Timber.tag(TAG).w("HTTP %s", conn.responseCode)
                        return@runCatching
                    }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    if (body.length > MAX_BODY_BYTES) return@runCatching
                    val parsed = parse(body)
                    if (parsed.isNotEmpty()) {
                        _items.value = parsed
                        File(context.filesDir, CACHE_FILE).writeText(body)
                        lastRefreshAtMs = System.currentTimeMillis()
                    }
                } finally {
                    conn.disconnect()
                }
            }.onFailure { Timber.tag(TAG).w(it, "refresh failed") }
        }
    }

    suspend fun unreadCount(context: Context): Int {
        val read = readIds(context)
        return _items.value.count { it.id !in read }
    }

    suspend fun markRead(context: Context, id: String) {
        if (id.isBlank()) return
        context.dataStore.edit { prefs ->
            val cur = prefs[ReadAnnouncementIdsKey].orEmpty()
            val set = cur.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            set.add(id)
            // Cap growth
            prefs[ReadAnnouncementIdsKey] = set.toList().takeLast(200).joinToString(",")
        }
    }

    suspend fun markAllRead(context: Context) {
        val ids = _items.value.map { it.id }
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[ReadAnnouncementIdsKey] = ids.takeLast(200).joinToString(",")
        }
    }

    suspend fun readIds(context: Context): Set<String> {
        val raw = context.dataStore.data.first()[ReadAnnouncementIdsKey].orEmpty()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun parse(body: String): List<OwnerAnnouncement> {
        val root = JSONObject(body)
        val arr = root.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<OwnerAnnouncement>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            val title = o.optString("title").trim()
            val text = o.optString("body").trim()
            if (id.isEmpty() || title.isEmpty() || text.isEmpty()) continue
            out.add(
                OwnerAnnouncement(
                    id = id,
                    title = title,
                    body = text,
                    date = o.optString("date").trim(),
                    priority = o.optString("priority", "info").ifBlank { "info" },
                    url = o.optString("url").takeIf { it.isNotBlank() },
                ),
            )
        }
        return out.sortedByDescending { it.date }
    }
}
