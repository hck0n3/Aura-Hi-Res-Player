

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
        
        val baseUrl = this.split("=")[0].split("-s")[0]
        return "$baseUrl=s${width ?: height}"
    }

    
    "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
        val w = width ?: 0
        val h = height ?: width ?: 0
        return "${this.split("=")[0]}=w$w-h$h-p-l90-rj"
    }

    return this
}
