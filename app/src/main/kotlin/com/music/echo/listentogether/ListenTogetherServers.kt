

package iad1tya.echo.music.listentogether

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@Serializable
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String
)

object ListenTogetherServers {
    private const val TAG = "ListenTogetherServers"
    private const val SERVER_JSON_URL = "https://raw.githubusercontent.com/EchoMusicApp/Echo-Music/refs/heads/main/app/server.json"

    /**
     * Aura's own room server. It is seeded first and is NEVER removed or reordered: [SERVER_JSON_URL]
     * is a third-party file we do not control, so the remote list may only ADD servers. Replacing the
     * list wholesale (the previous behaviour) let that third party silently redirect every room, and it
     * also made [defaultServerUrl] and [findByUrl] stop resolving Aura's own server.
     */
    private val DEFAULT_SERVER = ListenTogetherServer(
        name = "Aura Hi-Res Player Server",
        url = "wss://iad1tya-echomusic.hf.space/ws",
        location = "Global",
        operator = "ECHO"
    )

    private val _servers = MutableStateFlow(listOf(DEFAULT_SERVER))

    val serversFlow: StateFlow<List<ListenTogetherServer>> = _servers

    val servers: List<ListenTogetherServer>
        get() = _servers.value

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(SERVER_JSON_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag(TAG).w("Remote server list fetch failed: HTTP ${response.code}")
                        return@launch
                    }
                    val jsonString = response.body?.string()
                    if (jsonString.isNullOrBlank()) {
                        Timber.tag(TAG).w("Remote server list returned an empty body")
                        return@launch
                    }
                    val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
                    // No hardcoded fallbacks: an entry without a usable serverUrl is simply ignored,
                    // rather than inventing a foreign host we never verified.
                    val url = jsonObject["serverUrl"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    if (url == null) {
                        Timber.tag(TAG).w("Remote server list has no usable serverUrl; keeping defaults")
                        return@launch
                    }
                    val name = jsonObject["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: url
                    val region = jsonObject["region"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Global"
                    addServerIfAbsent(
                        ListenTogetherServer(name = name, url = url, location = region, operator = "")
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not fetch the remote server list; keeping the built-in defaults")
            }
        }
    }

    /** Appends [server] only when its URL is not already listed, so Aura's default stays first. */
    private fun addServerIfAbsent(server: ListenTogetherServer) {
        _servers.update { current ->
            if (current.any { it.url == server.url }) current else current + server
        }
    }

    val defaultServerUrl: String
        get() = servers.firstOrNull()?.url ?: DEFAULT_SERVER.url

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}
