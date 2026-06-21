

package iad1tya.echo.music.ui.utils

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    
    
    
    
    if (this.contains("i.ytimg.com")) {
        // Always use hqdefault for YouTube VIDEO thumbnails: maxresdefault.jpg does NOT exist for many
        // videos (404 / gray placeholder), which left the player cover and background BLACK when playing a
        // video's audio. hqdefault.jpg exists for every video, so the thumbnail always shows.
        return this.replace(
            Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg"),
            "hqdefault.jpg",
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
