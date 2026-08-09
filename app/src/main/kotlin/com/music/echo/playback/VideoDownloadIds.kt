package iad1tya.echo.music.playback

/** Suffix for the companion offline video download keyed separately from audio. */
const val VIDEO_DOWNLOAD_SUFFIX = "::video"

fun videoDownloadMediaId(id: String): String = "$id$VIDEO_DOWNLOAD_SUFFIX"

fun isVideoDownloadId(mediaId: String): Boolean = mediaId.endsWith(VIDEO_DOWNLOAD_SUFFIX)

fun baseSongIdFromVideoDownload(mediaId: String): String =
    if (isVideoDownloadId(mediaId)) mediaId.removeSuffix(VIDEO_DOWNLOAD_SUFFIX) else mediaId

/** URI scheme for serving a fully downloaded video stream from [downloadCache]. */
fun offlineVideoCacheUri(songId: String): String = "echo-cache-video://$songId"

fun isOfflineVideoCacheUri(uri: String): Boolean = uri.startsWith("echo-cache-video://")

fun songIdFromOfflineVideoCacheUri(uri: String): String? =
    if (isOfflineVideoCacheUri(uri)) uri.removePrefix("echo-cache-video://").takeIf { it.isNotEmpty() }
    else null
