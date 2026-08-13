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

/** Unread count for badges. Active list keeps read notices until the 24h publish TTL. */
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
 *  - A notice stays in Avisos until 24h after its **launch** ([OwnerAnnouncement.publishedAt]);
 *    then it disappears automatically. Marking read does NOT remove it from the list.
 *  - Read state only drives the unread badge, card styling, and [popupNotice].
 *  - Dismissing / acknowledging the popup marks it read (no more popup) but leaves it in Avisos.
 *  - First contact with the feed (fresh install): older items in the remote JSON are marked read
 *    once so only the newest pops up; they still appear in Avisos until their publish TTL ends.
 *  - Fresh install with no notice published in the last 24h → empty inbox.
 */
object OwnerAnnouncements {
    private const val TAG = "OwnerAnnouncements"
    const val REMOTE_URL =
        "https://raw.githubusercontent.com/hck0n3/Aura-Hi-Res-Player/main/announcements.json"
    /** Fallback when raw.githubusercontent is slow/blocked (same file on main). */
    private const val REMOTE_FALLBACK_URL =
        "https://cdn.jsdelivr.net/gh/hck0n3/Aura-Hi-Res-Player@main/announcements.json"
    /** Last successful FULL remote payload — never overwrite with a pruned inbox. */
    private const val REMOTE_CACHE_FILE = "announcements_remote.json"
    private const val FIRST_SEEN_FILE = "announcements_first_seen.json"
    /** One-shot: after this exists, backlog auto-consume will never run again on this install. */
    private const val BOOTSTRAP_FILE = "announcements_inbox_bootstrapped"
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

    /** User dismissed the popup without reading; stay quiet until the next forced refresh (app open). */
    @Volatile
    private var popupSnoozed: Boolean = false

    /**
     * Hydrate the inbox from the on-disk remote snapshot, TTL-pruned and bootstrap-aware.
     * Read-ids are applied only for [popupNotice] / badges — never to hide rows from Avisos.
     * Never drives [popupNotice] from cache alone (registry #135 flash class).
     */
    suspend fun loadCache(context: Context) {
        runCatching {
            _popupNotice.value = null
            val parsed = loadRemoteSnapshot(context)
            if (parsed.isEmpty()) {
                _items.value = emptyList()
                return
            }
            val read0 = readIds(context)
            val read = consumeHistoricalBacklogOnce(context, parsed, read0)
            val pruned = pruneByPublishTtl(context, parsed)
            _items.value = pruned
            applyPopup(pruned, read, forced = false)
        }.onFailure { Timber.tag(TAG).w(it, "loadCache failed") }
    }

    suspend fun refresh(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Clear any stale popup immediately on a forced open/resume pull so Compose never paints
            // a previously-read notice for one frame while we wait on network/DataStore.
            if (force) _popupNotice.value = null

            val since = System.currentTimeMillis() - lastRefreshAtMs
            if (!force && _items.value.isNotEmpty() && since in 0 until MIN_REFRESH_MS) {
                val base = mergeBase(context, null)
                val read = consumeHistoricalBacklogOnce(context, base, readIds(context))
                val pruned = pruneByPublishTtl(context, base)
                _items.value = pruned
                applyPopup(pruned, read, forced = false)
                return@withLock
            }

            val remoteBody = fetchRemoteBody()
            val remote = remoteBody?.let { parse(it) }
            if (!remoteBody.isNullOrBlank() && !remote.isNullOrEmpty()) {
                runCatching {
                    File(context.filesDir, REMOTE_CACHE_FILE).writeText(remoteBody)
                }.onFailure { Timber.tag(TAG).w(it, "save remote cache failed") }
                lastRefreshAtMs = System.currentTimeMillis()
                Timber.tag(TAG).i("Fetched %d notice(s) from remote", remote.size)
            } else {
                Timber.tag(TAG).w(
                    "Remote fetch empty (body=%s parsed=%s)",
                    remoteBody?.length ?: -1,
                    remote?.size ?: -1,
                )
            }

            val read0 = readIds(context)
            val base = mergeBase(context, remote)
            val read = consumeHistoricalBacklogOnce(context, base, read0)
            val pruned = pruneByPublishTtl(context, base)
            _items.value = pruned
            applyPopup(pruned, read, forced = force)
            Timber.tag(TAG).i("Active notices after prune: %d (read=%d)", pruned.size, read.size)
        }
    }

    /**
     * Fresh install (or first open of a build that introduced this rule): the remote feed may still
     * list every notice from the last 24h. Replaying that backlog as unread popups is wrong — mark
     * everything except the newest id as read once. Rows still stay in Avisos until publish TTL.
     */
    private suspend fun consumeHistoricalBacklogOnce(
        context: Context,
        incoming: List<OwnerAnnouncement>,
        read: Set<String>,
    ): Set<String> {
        if (incoming.isEmpty()) return read
        val marker = File(context.filesDir, BOOTSTRAP_FILE)
        if (marker.exists()) return read
        val newestId = incoming
            .maxWithOrNull(
                compareBy<OwnerAnnouncement> { publishedEpochMs(it) ?: Long.MIN_VALUE }
                    .thenBy { it.id },
            )
            ?.id
            ?: return read.also {
                runCatching { marker.writeText("1") }
            }
        val backlog = incoming.map { it.id }.filter { it != newestId && it !in read }
        if (backlog.isNotEmpty()) {
            context.dataStore.edit { prefs ->
                val set = (read + backlog).toList().takeLast(200).toMutableSet()
                prefs[ReadAnnouncementIdsKey] = set.joinToString(",")
            }
            Timber.tag(TAG).i(
                "Inbox bootstrap: kept newest=%s, marked %d older notice(s) read",
                newestId,
                backlog.size,
            )
        }
        runCatching { marker.writeText(newestId) }
        return read + backlog.toSet()
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
        mutex.withLock {
            // Keep the row in Avisos until the 24h publish TTL — only clear unread chrome / popup.
            val read = readIds(context)
            applyPopup(_items.value, read, forced = true)
        }
    }

    suspend fun markAllRead(context: Context) {
        val ids = _items.value.map { it.id }
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val cur = prefs[ReadAnnouncementIdsKey].orEmpty()
            val set = cur.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            set.addAll(ids)
            prefs[ReadAnnouncementIdsKey] = set.toList().takeLast(200).joinToString(",")
        }
        mutex.withLock {
            _popupNotice.value = null
            popupSnoozed = true
        }
    }

    /** Dismiss popup without consuming the notice (stays in inbox + badge). */
    fun snoozePopup() {
        popupSnoozed = true
        _popupNotice.value = null
    }

    /** Confirm popup: mark read (badge/popup only) and advance to the next unread if any. */
    suspend fun acknowledgePopup(context: Context) {
        val current = _popupNotice.value ?: return
        markRead(context, current.id)
    }

    suspend fun readIds(context: Context): Set<String> {
        val raw = context.dataStore.data.first()[ReadAnnouncementIdsKey].orEmpty()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun applyPopup(
        active: List<OwnerAnnouncement>,
        read: Set<String>,
        forced: Boolean,
    ) {
        val head = active.firstOrNull { it.id !in read }
        if (head == null) {
            _popupNotice.value = null
            return
        }
        if (forced) {
            // Re-arm after snooze only when there is still an unread notice.
            popupSnoozed = false
            _popupNotice.value = head
            return
        }
        if (popupSnoozed) {
            _popupNotice.value = null
            return
        }
        // Do not replace an already-visible popup with the same id (avoids recomposition flicker).
        if (_popupNotice.value?.id == head.id) return
        _popupNotice.value = head
    }

    private fun mergeBase(
        context: Context,
        remote: List<OwnerAnnouncement>?,
    ): List<OwnerAnnouncement> {
        if (!remote.isNullOrEmpty()) return remote
        val snap = loadRemoteSnapshot(context)
        if (snap.isNotEmpty()) return snap
        return _items.value
    }

    private fun loadRemoteSnapshot(context: Context): List<OwnerAnnouncement> {
        return runCatching {
            val file = File(context.filesDir, REMOTE_CACHE_FILE)
            val legacy = File(context.filesDir, "announcements_cache.json")
            val src = when {
                file.exists() -> file
                legacy.exists() -> legacy
                else -> return emptyList()
            }
            parse(src.readText())
        }.getOrDefault(emptyList())
    }

    private fun fetchRemoteBody(): String? {
        val urls = listOf(
            "$REMOTE_URL?t=${System.currentTimeMillis()}",
            "$REMOTE_FALLBACK_URL?t=${System.currentTimeMillis()}",
        )
        for (url in urls) {
            val body = runCatching { httpGet(url) }.onFailure {
                Timber.tag(TAG).w(it, "GET failed %s", url.substringBefore('?'))
            }.getOrNull()
            if (!body.isNullOrBlank()) return body
        }
        return null
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            useCaches = false
        }
        try {
            if (conn.responseCode !in 200..299) {
                Timber.tag(TAG).w("HTTP %s for %s", conn.responseCode, url.substringBefore('?'))
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.length > MAX_BODY_BYTES) return null
            return body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Keep notices published within the last [TTL_MS]. Read state is ignored here — Avisos is a
     * 24h bulletin board from launch time, not a clear-on-read inbox.
     * Undated items use first-seen as a stand-in launch clock so they still expire.
     */
    private fun pruneByPublishTtl(
        context: Context,
        incoming: List<OwnerAnnouncement>,
    ): List<OwnerAnnouncement> {
        val now = System.currentTimeMillis()
        val firstSeen = loadFirstSeen(context).toMutableMap()
        val kept = ArrayList<OwnerAnnouncement>(incoming.size)
        for (n in incoming) {
            val published = publishedEpochMs(n)
            if (published != null) {
                if (now - published > TTL_MS) {
                    firstSeen.remove(n.id)
                    continue
                }
            } else {
                val seen = firstSeen[n.id] ?: now.also { firstSeen[n.id] = it }
                if (now - seen > TTL_MS) {
                    firstSeen.remove(n.id)
                    continue
                }
            }
            if (published != null) {
                firstSeen.putIfAbsent(n.id, published)
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
