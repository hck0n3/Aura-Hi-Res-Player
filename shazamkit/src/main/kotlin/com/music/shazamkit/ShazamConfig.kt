package com.music.shazamkit

/**
 * Self-healing recognition config for the Shazam network layer.
 *
 * WHY: recognition has a SINGLE point of failure — it POSTs the signature to a HARDCODED host
 * (amp.shazam.com) with a hardcoded path/User-Agent set. If Shazam rotates that endpoint, geo-blocks
 * our egress, or bumps the API, recognition breaks for everyone until an app update ships.
 *
 * This object holds those network knobs as MUTABLE state seeded with today's PROVEN defaults, so the
 * app works EXACTLY as before with no remote file present. The app layer
 * ([iad1tya.echo.music.recognition.RemoteRecognitionConfig]) can fetch an optional
 * `shazam_recognition_config.json` and push overrides in via [applyRemote], curing a Shazam rotation
 * WITHOUT an app update — mirroring the YouTube cipher's RemotePlayerConfig self-healing pattern.
 *
 * SAFETY / SCOPE:
 *  - Everything is best-effort and OVERRIDE-ONLY: a missing/blank/invalid remote value keeps the
 *    compiled default. With no remote file the behaviour is byte-for-byte the current one.
 *  - The signature ALGORITHM and the Shazam request BODY format are never touched here — only the
 *    transport (host, path template, UAs, provider order, relay URL) is configurable.
 *  - Reads are lock-free via @Volatile; writes replace whole references atomically.
 */
object ShazamConfig {

    // ── Compiled defaults: today's proven, keyless amp.shazam.com transport ──────────────────────

    const val DEFAULT_HOST = "amp.shazam.com"

    /** {uuid1}/{uuid2} are substituted per request; keep them literal in any override. */
    const val DEFAULT_PATH_TEMPLATE = "/discovery/v5/en/US/android/-/tag/{uuid1}/{uuid2}"

    val DEFAULT_USER_AGENTS: List<String> = listOf(
        "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
        "Dalvik/1.6.0 (Linux; U; Android 4.4.2; SM-T210 Build/KOT49H)",
        "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
        "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
        "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)",
    )

    /**
     * Owner-hosted Aura Worker relay for the Shazam signature POST (proxied to amp.shazam.com from
     * Cloudflare egress). INERT until the owner deploys the `/recognize` route — see the cascade in
     * [Shazam.recognizeWithFallback]. Shares the license Worker (routes /verify and /demo untouched).
     */
    const val DEFAULT_RELAY_URL = "https://round-math-d64e.toberto4000.workers.dev/recognize"

    /** Cascade order of recognition providers. "direct" = keyless amp.shazam.com, "relay" = Aura Worker probe. */
    val DEFAULT_PROVIDER_ORDER: List<String> = listOf("direct", "relay")

    // ── Live config (starts at defaults; overridden only by a valid published value) ─────────────

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var host: String = DEFAULT_HOST

    @Volatile
    var pathTemplate: String = DEFAULT_PATH_TEMPLATE

    @Volatile
    var userAgents: List<String> = DEFAULT_USER_AGENTS

    @Volatile
    var relayUrl: String = DEFAULT_RELAY_URL

    @Volatile
    var providerOrder: List<String> = DEFAULT_PROVIDER_ORDER

    /**
     * Apply a published override. Every parameter is optional/nullable: a null or blank/empty value
     * KEEPS the current default, so a partial or malformed remote file can never brick the transport.
     * Called by the app-layer fetcher on a successful fetch or cache load.
     */
    fun applyRemote(
        enabled: Boolean? = null,
        host: String? = null,
        pathTemplate: String? = null,
        userAgents: List<String>? = null,
        relayUrl: String? = null,
        providerOrder: List<String>? = null,
    ) {
        enabled?.let { this.enabled = it }
        host?.trim()?.takeIf { it.isNotEmpty() }?.let { this.host = it }
        // A path template is only usable if it still carries both placeholders.
        pathTemplate?.trim()?.takeIf {
            it.contains("{uuid1}") && it.contains("{uuid2}")
        }?.let { this.pathTemplate = it }
        userAgents?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            ?.let { this.userAgents = it }
        relayUrl?.trim()?.takeIf { it.startsWith("http") }?.let { this.relayUrl = it }
        providerOrder?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            ?.let { this.providerOrder = it }
    }

    /** Restore compiled defaults (used by tests / a remote "enabled=false → reset" policy). */
    fun resetToDefaults() {
        enabled = true
        host = DEFAULT_HOST
        pathTemplate = DEFAULT_PATH_TEMPLATE
        userAgents = DEFAULT_USER_AGENTS
        relayUrl = DEFAULT_RELAY_URL
        providerOrder = DEFAULT_PROVIDER_ORDER
    }
}
