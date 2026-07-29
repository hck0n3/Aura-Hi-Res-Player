package com.aura.migration.db

import androidx.room.*
import com.aura.migration.model.SourceTrack
import com.aura.migration.resolver.TextNormalizer
import java.security.MessageDigest

@Entity(tableName = "match_cache")
data class MatchCacheEntity(
    @PrimaryKey val key: String,
    val videoId: String,
    val confidence: Float,
    /** Correccion manual del usuario = verdad absoluta. Nunca se sobrescribe. */
    val verifiedByUser: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MatchCacheDao {

    @Query("SELECT * FROM match_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): MatchCacheEntity?

    @Query("SELECT * FROM match_cache WHERE `key` IN (:keys)")
    suspend fun getAll(keys: List<String>): List<MatchCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MatchCacheEntity)

    @Query("DELETE FROM match_cache WHERE verifiedByUser = 0")
    suspend fun clearAutomatic()
}

/**
 * Fachada sobre el DAO con la regla de negocio importante:
 * un resultado automatico NUNCA pisa una correccion manual del usuario.
 */
open class MatchCache(private val dao: MatchCacheDao) {

    open suspend fun get(key: String): MatchCacheEntity? = dao.get(key)

    open suspend fun put(key: String, videoId: String, confidence: Float, byUser: Boolean = false) {
        val existing = dao.get(key)
        if (existing?.verifiedByUser == true && !byUser) return  // proteccion
        dao.upsert(
            MatchCacheEntity(
                key = key,
                videoId = videoId,
                confidence = if (byUser) 1f else confidence,
                verifiedByUser = byUser
            )
        )
    }

    /** El usuario corrigio un match: se guarda como verdad permanente. */
    suspend fun confirmByUser(key: String, videoId: String) =
        put(key, videoId, 1f, byUser = true)
}

/**
 * Clave de cache.
 *
 * Con ISRC: identifica la grabacion exacta -> un acierto vale para siempre
 * y para cualquier usuario.
 * Sin ISRC: hash de titulo+artista+duracion redondeada a 5 s. La redondez
 * absorbe pequenas diferencias de metadatos entre plataformas.
 */
fun SourceTrack.cacheKey(): String =
    isrc?.takeIf { it.isNotBlank() }
        ?.let { "isrc:${it.uppercase().replace("-", "")}" }
        ?: run {
            val raw = buildString {
                append(TextNormalizer.normalize(title)); append('|')
                append(TextNormalizer.normalizeArtist(primaryArtist)); append('|')
                append((durationMs ?: 0L) / 5_000L)
            }
            "h:" + sha1(raw)
        }

private fun sha1(s: String): String =
    MessageDigest.getInstance("SHA-1")
        .digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
