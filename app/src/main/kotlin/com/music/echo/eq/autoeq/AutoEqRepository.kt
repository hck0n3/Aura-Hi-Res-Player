package iad1tya.echo.music.eq.autoeq

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/** One selectable headphone in the AutoEq catalog. [path] is the repo path of its ParametricEQ.txt. */
data class AutoEqEntry(val name: String, val source: String, val path: String)

/**
 * Fetches the AutoEq (jaakkopasanen/AutoEq) catalog and individual ParametricEQ profiles on demand.
 *
 * A snapshot of the catalog (model name → repo path) ships bundled as an asset so the list renders
 * INSTANTLY with zero network on first open. A downloaded copy is cached in filesDir (durable, not
 * OS-evictable like cacheDir) for 30 days; network refresh only happens in the background or when the
 * user forces it. Profiles are downloaded from raw.githubusercontent on selection — an applied
 * profile is stored in the EQ and works offline afterwards.
 */
class AutoEqRepository(private val context: Context) {

    // Durable cache in filesDir. It used to live in cacheDir (OS-evictable, so users lost the index
    // under storage pressure and re-downloaded the multi-MB git tree) — migrate old copies over.
    private val cacheFile: File get() = File(context.filesDir, "autoeq_index.tsv")
    private val legacyCacheFile: File get() = File(context.cacheDir, "autoeq_index.tsv")

    /** Extracts `"path": "results/.../<Model> ParametricEQ.txt"` entries from the git-tree JSON. */
    private val pathRegex = Regex(""""path"\s*:\s*"(results/[^"]+?ParametricEQ\.txt)"""")

    /** `"truncated": true` in the git-tree response means the listing is INCOMPLETE — never index it. */
    private val truncatedRegex = Regex(""""truncated"\s*:\s*true""")

    /**
     * Load order: fresh filesDir cache → bundled asset (instant, no network) → stale cache → network.
     * With [forceRefresh] the network is tried first; on failure (or a truncated tree) the previous
     * cache/asset is kept. Callers should only force-refresh in the background or on user request —
     * the non-forced path never blocks first render on the network when the asset is present.
     */
    suspend fun getIndex(forceRefresh: Boolean = false): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        migrateLegacyCache()
        val raw = if (forceRefresh) {
            runCatching { downloadIndexTsv() }.getOrElse {
                Timber.tag("AUTOEQ").w(it, "Index download failed; keeping previous index")
                readCacheOrNull(requireFresh = false) ?: readBundledAssetOrNull()
                    ?: return@withContext emptyList()
            }
        } else {
            readCacheOrNull(requireFresh = true)
                ?: readBundledAssetOrNull()
                ?: readCacheOrNull(requireFresh = false)
                ?: runCatching { downloadIndexTsv() }.getOrElse {
                    Timber.tag("AUTOEQ").w(it, "Index download failed and no cache/asset available")
                    return@withContext emptyList()
                }
        }
        parseTsv(raw)
    }

    /** True when the on-disk cache exists and is inside the 30-day TTL (no background refresh needed). */
    fun isIndexFresh(): Boolean {
        migrateLegacyCache()
        return cacheFile.exists() && isFresh(cacheFile)
    }

    private fun parseTsv(raw: String): List<AutoEqEntry> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 3) AutoEqEntry(parts[0], parts[1], parts[2]) else null
        }
            // Dedup as the LAST step before returning: a stale/old cache TSV (written before the
            // download-time distinctBy existed, or any duplicate-bearing cache) would otherwise return
            // two rows with the same name|source — colliding on the LazyColumn key and crashing Compose.
            .distinctBy { it.name + "|" + it.source }
            .toList()

    private fun readCacheOrNull(requireFresh: Boolean): String? =
        cacheFile.takeIf { it.exists() && (!requireFresh || isFresh(it)) }
            ?.let { f -> runCatching { f.readText() }.getOrNull() }

    /** Bundled snapshot of the catalog — renders instantly on a cold install, no network needed. */
    private fun readBundledAssetOrNull(): String? = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
    }.getOrNull()

    /** One-time move of the old cacheDir copy (OS-evictable) into filesDir, preserving its TTL clock. */
    private fun migrateLegacyCache() {
        val legacy = legacyCacheFile
        if (!legacy.exists() || cacheFile.exists()) return
        runCatching {
            legacy.copyTo(cacheFile, overwrite = false)
            cacheFile.setLastModified(legacy.lastModified())
            legacy.delete()
            Timber.tag("AUTOEQ").i("Migrated AutoEq index cache from cacheDir to filesDir")
        }.onFailure { Timber.tag("AUTOEQ").w(it, "AutoEq cache migration failed") }
    }

    // Cache the catalog for 30 days (it rarely changes) so it only downloads once, then loads from
    // disk instantly. The user can force a refresh from the "Actualizar base de datos" button.
    private fun isFresh(f: File): Boolean =
        System.currentTimeMillis() - f.lastModified() < 30L * 24 * 60 * 60 * 1000

    private fun downloadIndexTsv(): String {
        val json = httpGet(
            "https://api.github.com/repos/jaakkopasanen/AutoEq/git/trees/master?recursive=1"
        )
        // GitHub truncates huge recursive trees — a truncated listing would silently DROP models, so
        // treat it as a failure and keep the previous/bundled index instead of a partial one.
        if (truncatedRegex.containsMatchIn(json)) error("git tree response truncated; keeping previous index")
        val entries = pathRegex.findAll(json).map { it.groupValues[1] }.map { path ->
            // results/<source>/<rig>/<Model>/<Model> ParametricEQ.txt → name = model folder.
            val segs = path.split('/')
            val source = segs.getOrElse(1) { "" }
            val name = segs.getOrElse(segs.size - 2) { segs.last() }
            AutoEqEntry(name = name, source = source, path = path)
        }.distinctBy { it.name + "|" + it.source }
            .sortedBy { it.name.lowercase() }
            .toList()
        if (entries.isEmpty()) error("git tree response yielded no models; keeping previous index")
        val tsv = entries.joinToString("\n") { "${it.name}\t${it.source}\t${it.path}" }
        runCatching { cacheFile.writeText(tsv) }
        Timber.tag("AUTOEQ").i("AutoEq index built: ${entries.size} models")
        return tsv
    }

    /** Downloads and parses one model's ParametricEQ profile. */
    suspend fun fetchProfile(entry: AutoEqEntry): AutoEqProfile? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = entry.path.split('/').joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val text = httpGet("https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/$encoded")
            AutoEqParser.parse(text)
        }.getOrElse {
            Timber.tag("AUTOEQ").w(it, "Profile fetch failed for ${entry.name}")
            null
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", "AuraHiResPlayer")
            setRequestProperty("Accept", "application/vnd.github+json")
            // The multi-MB git-tree JSON compresses ~10x. Setting the header explicitly disables
            // Android's transparent gunzip, so decode manually based on Content-Encoding below.
            setRequestProperty("Accept-Encoding", "gzip")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} for $url")
            val stream =
                if (conn.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                    GZIPInputStream(conn.inputStream)
                } else {
                    conn.inputStream
                }
            return stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val ASSET_NAME = "autoeq_index.tsv"
    }
}
