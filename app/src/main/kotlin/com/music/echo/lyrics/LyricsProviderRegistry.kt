

package iad1tya.echo.music.lyrics

import iad1tya.echo.music.constants.PreferredLyricsProvider


object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "YouLyPlus"       to YouLyPlusLyricsProvider,
        "Paxsenix"        to PaxSenixLyricsProvider,
        "BetterLyrics"    to BetterLyricsProvider,
        "SimpMusic"       to SimpMusicLyricsProvider,
        "LrcLib"          to LrcLibLyricsProvider,
        "Kugou"           to KuGouLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic"    to YouTubeLyricsProvider,
        "Unison"          to UnisonLyricsProvider,
    )

    val providerNames = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        return orderString.split(",").map { it.trim() }.filter { it in providerNames }
    }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    fun getDefaultProviderOrder(): List<String> = listOf(
        "YouLyPlus",
        "BetterLyrics",
        "SimpMusic",
        "LrcLib",
        "Kugou",
        "YouTubeSubtitle",
        "YouTubeMusic",
        // Paxsenix next-to-last: its public endpoint 403s / rate-limits often, so it is a
        // last-resort fallback tried only after the reliable providers.
        "Paxsenix",
        // Unison DEAD last: its crowd-sourced DB is currently very sparse (404s for most
        // popular songs), so it is a true final fallback. Being the very last entry also makes
        // it the LAZY last-resort in LyricsHelper (only started when everything ahead of it is
        // empty), so it never slows the common path.
        "Unison",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }

    
    fun getProviderNameForEnum(enum: PreferredLyricsProvider): String = when (enum) {
        PreferredLyricsProvider.LRCLIB        -> "LrcLib"
        PreferredLyricsProvider.KUGOU         -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC     -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS     -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX      -> "Paxsenix"
    }

    
    fun getDisplayName(name: String): String = when (name) {
        "YouLyPlus"       -> "YouLyPlus"
        "Paxsenix"        -> "PaxSenix"
        "BetterLyrics"    -> "Better Lyrics"
        "SimpMusic"       -> "SimpMusic"
        "LrcLib"          -> "LrcLib"
        "Kugou"           -> "KuGou"
        "YouTubeSubtitle" -> "YouTube Subtitle"
        "YouTubeMusic"    -> "YouTube Music"
        "Unison"          -> "Unison"
        else              -> name
    }
}
