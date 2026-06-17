package iad1tya.echo.music.eq.autoeq

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One selectable headphone in the AutoEq catalog. [path] is the repo path of its ParametricEQ.txt. */
data class AutoEqEntry(val name: String, val source: String, val path: String)

/**
 * Fetches the AutoEq (jaakkopasanen/AutoEq) catalog and individual ParametricEQ profiles on demand.
 *
 * The catalog (model name → repo path) is built once from the recursive git tree and cached to disk
 * for a day; profiles are downloaded from raw.githubusercontent on selection. Network only happens on
 * refresh/select — an applied profile is stored in the EQ and works offline afterwards.
 */
class AutoEqRepository(private val context: Context) {

    private val cacheFile: File get() = File(context.cacheDir, "autoeq_index.tsv")

    /** Extracts `"path": "results/.../<Model> ParametricEQ.txt"` entries from the git-tree JSON. */
    private val pathRegex = Regex(""""path"\s*:\s*"(results/[^"]+?ParametricEQ\.txt)"""")

    suspend fun getIndex(forceRefresh: Boolean = false): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        val cached = cacheFile.takeIf { it.exists() && !forceRefresh && isFresh(it) }
        val raw = if (cached != null) {
            cached.readText()
        } else {
            runCatching { downloadIndexTsv() }.getOrElse {
                Timber.tag("AUTOEQ").w(it, "Index download failed; using stale cache if any")
                cacheFile.takeIf { f -> f.exists() }?.readText() ?: return@withContext emptyList()
            }
        }
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 3) AutoEqEntry(parts[0], parts[1], parts[2]) else null
        }.toList()
    }

    private fun isFresh(f: File): Boolean =
        System.currentTimeMillis() - f.lastModified() < 24L * 60 * 60 * 1000

    private fun downloadIndexTsv(): String {
        val json = httpGet(
            "https://api.github.com/repos/jaakkopasanen/AutoEq/git/trees/master?recursive=1"
        )
        val entries = pathRegex.findAll(json).map { it.groupValues[1] }.map { path ->
            // results/<source>/<rig>/<Model>/<Model> ParametricEQ.txt → name = model folder.
            val segs = path.split('/')
            val source = segs.getOrElse(1) { "" }
            val name = segs.getOrElse(segs.size - 2) { segs.last() }
            AutoEqEntry(name = name, source = source, path = path)
        }.distinctBy { it.name + "|" + it.source }
            .sortedBy { it.name.lowercase() }
            .toList()
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
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} for $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
