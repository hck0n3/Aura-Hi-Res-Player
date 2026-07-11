

package iad1tya.echo.music.ui.utils

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    
    
    
    
    if (this.contains("i.ytimg.com")) {
        // Use sddefault (640×480) for YouTube VIDEO thumbnails — clearly sharper than hqdefault (480×360)
        // so covers aren't pixelated on big screens, while still existing for virtually every video
        // (unlike maxresdefault, which 404s for many → black cover/background).
        return this.replace(
            Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg"),
            "sddefault.jpg",
        )
    }

    
    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = this.split("=w")[0]
        val w = width ?: 0
        val h = height ?: width ?: 0
        
        return "$baseUrl=w$w-h$h-p-l90-rj"
    }

    
    if (this.contains("yt3.ggpht.com")) {
        // Artist/channel AVATARS. The old code did split("=")[0].split("-s")[0], and split("-s") chops
        // the opaque token at any "-s" inside it → a 404 → blank circle (bug: suggested AND local artist
        // photos didn't load). The token is not safely rewritable, so return the URL RAW — Coil samples
        // the native avatar down to the (small, circular) target bounds. Fixes every artist-image site.
        return this
    }

    
    "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
        val w = width ?: 0
        val h = height ?: width ?: 0
        return "${this.split("=")[0]}=w$w-h$h-p-l90-rj"
    }

    return this
}
