package iad1tya.echo.music.notices

import android.content.Context
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.ReadAnnouncementIdsKey
import iad1tya.echo.music.utils.dataStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

data class OwnerAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val date: String,
    val priority: String = "info",
    val url: String? = null,
    /** ISO-8601 instant when the owner published; optional in JSON. */
    val publishedAt: String? = null,
)

/** Unread count for badges. Active list already excludes read + expired. */
fun unreadOwnerNoticeCount(items: List<OwnerAnnouncement>, readIdsCsv: String): Int {
    if (items.isEmpty()) return 0
    val read = readIdsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return items.count { it.id !in read }
}

/**
 * Owner → users notice inbox. Feed: [REMOTE_URL] on main (no APK needed).
 *
 * Lifetime rules (owner):
 *  - Refresh on every app open and about once per hour while the Activity is resumed.
 *  - A notice lives at most 24h in the app (from firstSeen, and never if published >24h ago).
 *  - Marking read removes it from the inbox immediately.
 *  - Unread fresh notices surface via [popupNotice] for an in-app dialog.
 */
object OwnerAnnouncements {
    private const val TAG = "OwnerAnnouncements"
    const val REMOTE_URL =
        "https://raw.githubusercontent.com/hck0n3/Aura-Hi-Res-Player/main/announcements.json"
    private const val CACHE_FILE = "announcements_cache.json"
    private const val FIRST_SEEN_FILE = "announcements_first_seen.json"
    private const val MAX_BODY_BYTES = 256 * 1024
    val TTL_MS: Long = TimeUnit.HOURS.toMillis(24)
    /** Soft throttle only for non-forced callers; open/resume uses force=true. */
    private const val MIN_REFRESH_MS = 60L * 1_000L

    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<OwnerAnnouncement>>(emptyList())
    val items: StateFlow<List<OwnerAnnouncement>> = _items.asStateFlow()

    private val _popupNotice = MutableStateFlow<OwnerAnnouncement?>(null)
    val popupNotice: StateFlow<OwnerAnnouncement?> = _popupNotice.asStateFlow()

    @Volatile
    private var lastRefreshAtMs: Long = 0L

    fun loadCache(context: Context) {
        runCatching {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return
            val parsed = parse(file.readText())
            // Read ids need a coroutine; prune without read filter here, refresh() will re-prune.
            val pruned = pruneExpiredOnly(context, parsed)
            _items.value = pruned
            writeCache(context, pruned)
            refreshPopup(pruned)
        }.onFailure { Timber.tag(TAG).w(it, "loadCache failed") }
    }

    suspend fun refresh(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val since = System.currentTimeMillis() - lastRefreshAtMs
            if (!force && _items.value.isNotEmpty() && since in 0 until MIN_REFRESH_MS) {
                val pruned = pruneSync(context, _items.value, readIds(context))
                _items.value = pruned
                writeCache(context, pruned)
                refreshPopup(pruned)
                return@withLock
            }
            var remote: List<OwnerAnnouncement>? = null
            runCatching {
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
                    remote = parse(body)
                } finally {
                    conn.disconnect()
                }
            }.onFailure { Timber.tag(TAG).w(it, "refresh failed") }

            val base = remote ?: _items.value.ifEmpty {
                runCatching {
                    val f = File(context.filesDir, CACHE_FILE)
                    if (f.exists()) parse(f.readText()) else emptyList()
                }.getOrDefault(emptyList())
            }
            val pruned = pruneSync(context, base, readIds(context))
            _items.value = pruned
            writeCache(context, pruned)
            if (remote != null) lastRefreshAtMs = System.currentTimeMillis()
            refreshPopup(pruned)
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
            prefs[ReadAnnouncementIdsKey] = set.toList().takeLast(200).joinToString(",")
        }
        // Owner: once read, drop from inbox immediately (same as TTL expiry).
        mutex.withLock {
            val next = _items.value.filterNot { it.id == id }
            _items.value = next
            writeCache(context, next)
            removeFirstSeen(context, id)
            refreshPopup(next)
        }
    }

    suspend fun markAllRead(context: Context) {
        val ids = _items.value.map { it.id }
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[ReadAnnouncementIdsKey] = ids.takeLast(200).joinToString(",")
        }
        mutex.withLock {
            _items.value = emptyList()
            writeCache(context, emptyList())
            clearFirstSeen(context)
            _popupNotice.value = null
        }
    }

    /** Dismiss popup: mark read (removes notice) and advance to the next unread if any. */
    suspend fun acknowledgePopup(context: Context) {
        val current = _popupNotice.value ?: return
        markRead(context, current.id)
    }

    suspend fun readIds(context: Context): Set<String> {
        val raw = context.dataStore.data.first()[ReadAnnouncementIdsKey].orEmpty()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun refreshPopup(active: List<OwnerAnnouncement>) {
        _popupNotice.value = active.firstOrNull()
    }

    private fun writeCache(context: Context, items: List<OwnerAnnouncement>) {
        runCatching {
            val arr = JSONArray()
            items.forEach { n ->
                arr.put(
                    JSONObject().apply {
                        put("id", n.id)
                        put("title", n.title)
                        put("body", n.body)
                        put("date", n.date)
                        put("priority", n.priority)
                        if (!n.url.isNullOrBlank()) put("url", n.url)
                        if (!n.publishedAt.isNullOrBlank()) put("publishedAt", n.publishedAt)
                    },
                )
            }
            File(context.filesDir, CACHE_FILE).writeText(JSONObject().put("items", arr).toString())
        }.onFailure { Timber.tag(TAG).w(it, "writeCache failed") }
    }

    private fun pruneExpiredOnly(
        context: Context,
        incoming: List<OwnerAnnouncement>,
    ): List<OwnerAnnouncement> {
        val now = System.currentTimeMillis()
        val firstSeen = loadFirstSeen(context).toMutableMap()
        val kept = ArrayList<OwnerAnnouncement>(incoming.size)
        for (n in incoming) {
            val published = publishedEpochMs(n)
            if (published != null && now - published > TTL_MS) {
                firstSeen.remove(n.id)
                continue
            }
            val seen = firstSeen[n.id] ?: now.also { firstSeen[n.id] = it }
            if (now - seen > TTL_MS) {
                firstSeen.remove(n.id)
                continue
            }
            kept.add(n)
        }
        firstSeen.keys.retainAll(kept.map { it.id }.toSet())
        saveFirstSeen(context, firstSeen)
        return kept.sortedByDescending { publishedEpochMs(it) ?: 0L }
    }

    private fun pruneSync(
        context: Context,
        incoming: List<OwnerAnnouncement>,
        read: Set<String>,
    ): List<OwnerAnnouncement> {
        val now = System.currentTimeMillis()
        val firstSeen = loadFirstSeen(context).toMutableMap()
        val kept = ArrayList<OwnerAnnouncement>(incoming.size)
        for (n in incoming) {
            if (n.id in read) {
                firstSeen.remove(n.id)
                continue
            }
            val published = publishedEpochMs(n)
            if (published != null && now - published > TTL_MS) {
                firstSeen.remove(n.id)
                continue
            }
            val seen = firstSeen[n.id] ?: now.also { firstSeen[n.id] = it }
            if (now - seen > TTL_MS) {
                firstSeen.remove(n.id)
                continue
            }
            kept.add(n)
        }
        firstSeen.keys.retainAll(kept.map { it.id }.toSet())
        saveFirstSeen(context, firstSeen)
        return kept.sortedByDescending { publishedEpochMs(it) ?: 0L }
    }

    private fun loadFirstSeen(context: Context): Map<String, Long> {
        return runCatching {
            val f = File(context.filesDir, FIRST_SEEN_FILE)
            if (!f.exists()) return emptyMap()
            val o = JSONObject(f.readText())
            buildMap {
                o.keys().forEach { k -> put(k, o.optLong(k, 0L)) }
            }.filterValues { it > 0L }
        }.getOrDefault(emptyMap())
    }

    private fun saveFirstSeen(context: Context, map: Map<String, Long>) {
        runCatching {
            val o = JSONObject()
            map.forEach { (k, v) -> o.put(k, v) }
            File(context.filesDir, FIRST_SEEN_FILE).writeText(o.toString())
        }
    }

    private fun removeFirstSeen(context: Context, id: String) {
        val map = loadFirstSeen(context).toMutableMap()
        map.remove(id)
        saveFirstSeen(context, map)
    }

    private fun clearFirstSeen(context: Context) {
        runCatching { File(context.filesDir, FIRST_SEEN_FILE).delete() }
    }

    internal fun publishedEpochMs(n: OwnerAnnouncement): Long? {
        n.publishedAt?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }
        }
        val idMatch = Regex("""(\d{8})-(\d{6})""").find(n.id)
        if (idMatch != null) {
            val (d, t) = idMatch.destructured
            runCatching {
                val local = LocalDateTime.parse(
                    "$d$t",
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                )
                return local.toInstant(ZoneOffset.UTC).toEpochMilli()
            }
        }
        n.date.trim().takeIf { it.length >= 10 }?.let { d ->
            runCatching {
                val day = LocalDate.parse(d.take(10))
                return day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            }
        }
        return null
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
                    publishedAt = o.optString("publishedAt").takeIf { it.isNotBlank() },
                ),
            )
        }
        return out.sortedByDescending { publishedEpochMs(it) ?: 0L }
    }
}
