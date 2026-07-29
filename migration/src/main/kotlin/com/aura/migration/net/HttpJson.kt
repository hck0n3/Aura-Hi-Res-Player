package com.aura.migration.net

import com.aura.migration.source.SourceError
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Capa HTTP minima. Existe para que los adaptadores no dependan de una
 * libreria concreta: si tu proyecto ya usa Ktor, reimplementa esta interfaz
 * con Ktor y no cambies nada mas.
 */
interface HttpJson {
    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject
    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String
}

class OkHttpJson(
    private val client: OkHttpClient = OkHttpClient(),
    private val maxRetries: Int = 3
) : HttpJson {

    override suspend fun getJson(url: String, headers: Map<String, String>): JsonObject =
        Json.parseToJsonElement(getText(url, headers)).jsonObject

    override suspend fun getText(url: String, headers: Map<String, String>): String {
        var attempt = 0
        var backoff = 500L
        while (true) {
            try {
                val req = Request.Builder().url(url).apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }.build()

                client.newCall(req).execute().use { res ->
                    when {
                        res.isSuccessful -> return res.body?.string().orEmpty()
                        res.code == 429 -> {
                            if (attempt >= maxRetries) throw SourceError.RateLimited(backoff)
                            delay(backoff); backoff *= 2; attempt++
                        }
                        res.code == 403 -> throw SourceError.PrivatePlaylist()
                        res.code == 404 -> throw SourceError.NotFound()
                        res.code >= 500 -> {
                            if (attempt >= maxRetries) throw SourceError.Network("HTTP ${res.code}")
                            delay(backoff); backoff *= 2; attempt++
                        }
                        else -> throw SourceError.Network("HTTP ${res.code}")
                    }
                }
            } catch (e: IOException) {
                if (attempt >= maxRetries) throw SourceError.Network(e.message ?: "IO")
                delay(backoff); backoff *= 2; attempt++
            }
        }
    }
}

// ---- helpers de lectura tolerante: nunca petar por un campo que falta ----

fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

fun JsonObject.strOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

fun JsonObject.boolOrNull(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
