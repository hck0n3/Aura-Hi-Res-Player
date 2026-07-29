package com.aura.migration.source.file

import com.aura.migration.model.*
import com.aura.migration.source.PlaylistSource
import com.aura.migration.source.SourceError
import kotlinx.serialization.json.*

/**
 * Import desde archivo. EL SEGURO DE VIDA del sistema.
 *
 * Cubre el 100% de plataformas presentes y futuras: cualquier servicio con
 * export GDPR, TuneMyMusic/Soundiiz free, o cualquier reproductor local.
 * Cero OAuth, cero API que se rompa, cero terminos que violar.
 *
 * Si manana Spotify revoca el client id o Deezer cierra el catalogo publico,
 * esto sigue funcionando.
 */
class FileSource : PlaylistSource {

    override val type = SourceType.FILE
    override val displayName = "Archivo (CSV, M3U, JSPF)"

    override fun accepts(input: String) =
        input.substringAfterLast('.', "").lowercase() in
            setOf("csv", "tsv", "m3u", "m3u8", "jspf", "json", "xspf")

    override suspend fun listPlaylists(input: String?) = listOf(
        SourcePlaylist(id = input.orEmpty(), name = "Importada de archivo", origin = type)
    )

    override suspend fun fetchTracks(playlistId: String): List<SourceTrack> =
        throw SourceError.Unsupported("usa parse(contenido, extension)")

    fun parse(content: String, extension: String): List<SourceTrack> =
        when (extension.lowercase()) {
            "csv", "tsv"    -> parseCsv(content, if (extension == "tsv") '\t' else ',')
            "m3u", "m3u8"   -> parseM3u(content)
            "jspf", "json"  -> parseJspf(content)
            else -> throw SourceError.Unsupported(extension)
        }

    // ---------- CSV ----------

    private val TITLE_KEYS  = setOf("track name","title","name","song","song name","cancion","canción","titulo","título","tema")
    private val ARTIST_KEYS = setOf("artist name","artist","artists","artista","artistas","interprete","intérprete")
    private val ALBUM_KEYS  = setOf("album name","album","álbum","disco")
    private val ISRC_KEYS   = setOf("isrc","isrc code")
    private val DUR_KEYS    = setOf("duration","duration (ms)","duration_ms","length","duracion","duración")

    /**
     * Deteccion de columnas POR NOMBRE, nunca por posicion: cada exportador
     * (TuneMyMusic, Soundiiz, Apple, Spotify GDPR) usa un orden distinto.
     */
    private fun parseCsv(content: String, delimiter: Char): List<SourceTrack> {
        val rows = readCsv(content, delimiter)
        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { it.trim().lowercase().removeSurrounding("\"") }
        fun idx(keys: Set<String>) = header.indexOfFirst { it in keys }
            .takeIf { it >= 0 }
            ?: header.indexOfFirst { h -> keys.any { h.contains(it) } }.takeIf { it >= 0 }

        val iTitle  = idx(TITLE_KEYS)  ?: throw SourceError.Unsupported("no encuentro la columna de titulo")
        val iArtist = idx(ARTIST_KEYS) ?: throw SourceError.Unsupported("no encuentro la columna de artista")
        val iAlbum  = idx(ALBUM_KEYS)
        val iIsrc   = idx(ISRC_KEYS)
        val iDur    = idx(DUR_KEYS)

        return rows.drop(1).mapIndexedNotNull { i, row ->
            val title = row.getOrNull(iTitle)?.trim().orEmpty()
            if (title.isBlank()) return@mapIndexedNotNull null
            SourceTrack(
                title = title,
                artists = row.getOrNull(iArtist)?.split(Regex("[,;&]|\\bfeat\\.?\\b"))
                    ?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                album = iAlbum?.let { row.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() },
                isrc = iIsrc?.let { row.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() },
                durationMs = iDur?.let { row.getOrNull(it)?.trim()?.let(::parseDuration) },
                sourcePosition = i
            )
        }
    }

    /** Parser CSV real: respeta comillas y comas dentro de campos. */
    private fun readCsv(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                inQuotes && c == '"' && i + 1 < content.length && content[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> { row.add(cell.toString()); cell.clear() }
                (c == '\n') && !inQuotes -> {
                    row.add(cell.toString()); cell.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row.toList())
                    row.clear()
                }
                c == '\r' -> {}
                else -> cell.append(c)
            }
            i++
        }
        row.add(cell.toString())
        if (row.any { it.isNotBlank() }) rows.add(row.toList())
        return rows
    }

    private fun parseDuration(raw: String): Long? {
        raw.toLongOrNull()?.let { return if (it > 10_000) it else it * 1000 }
        // formato mm:ss o hh:mm:ss
        val parts = raw.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            else -> null
        }
    }

    // ---------- M3U ----------

    private fun parseM3u(content: String): List<SourceTrack> {
        val out = mutableListOf<SourceTrack>()
        var pending: Pair<Long?, String>? = null
        var pos = 0

        content.lineSequence().forEach { line ->
            val l = line.trim()
            when {
                l.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val payload = l.removePrefix("#EXTINF:").removePrefix("#extinf:")
                    val secs = payload.substringBefore(',').trim().toDoubleOrNull()
                    val label = payload.substringAfter(',', "").trim()
                    pending = (secs?.times(1000)?.toLong()?.takeIf { it > 0 }) to label
                }
                l.isBlank() || l.startsWith("#") -> {}
                else -> {
                    val (dur, label) = pending ?: (null to l.substringAfterLast('/')
                        .substringBeforeLast('.'))
                    // convencion universal: "Artista - Titulo"
                    val artist = label.substringBefore(" - ", "").trim()
                    val title  = if (artist.isNotBlank()) label.substringAfter(" - ").trim() else label
                    if (title.isNotBlank()) {
                        out += SourceTrack(
                            title = title,
                            artists = if (artist.isBlank()) emptyList() else listOf(artist),
                            durationMs = dur,
                            sourcePosition = pos++
                        )
                    }
                    pending = null
                }
            }
        }
        return out
    }

    // ---------- JSPF ----------

    private fun parseJspf(content: String): List<SourceTrack> {
        val root = Json.parseToJsonElement(content).jsonObject
        val list = root["playlist"]?.jsonObject ?: root
        val tracks = list["track"]?.jsonArray ?: return emptyList()

        return tracks.mapIndexedNotNull { i, el ->
            val t = el as? JsonObject ?: return@mapIndexedNotNull null
            fun s(k: String) = (t[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val title = s("title") ?: return@mapIndexedNotNull null
            SourceTrack(
                title = title,
                artists = listOfNotNull(s("creator")),
                album = s("album"),
                durationMs = (t["duration"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull(),
                sourcePosition = i
            )
        }
    }
}
