/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import iad1tya.echo.music.constants.ExportedFileUrisKey
import java.util.Locale
import kotlinx.coroutines.flow.first

fun String.isLocalMediaId(): Boolean {
    return runCatching {
        when (toUri().scheme?.lowercase(Locale.US)) {
            "content", "file", "android.resource" -> true
            else -> false
        }
    }.getOrDefault(false)
}

fun shareLocalAudio(
    context: Context,
    mediaId: String,
    mimeType: String? = null,
): Boolean {
    val uri = mediaId.toUri()
    val scheme = uri.scheme?.lowercase(Locale.US)
    if (scheme != "content" && scheme != "android.resource") return false

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType?.takeIf(String::isNotBlank) ?: "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, null))
    return true
}

/** Share an already-resolved content/file URI (e.g. from [lookupExportedFileUri]). */
fun shareContentUri(
    context: Context,
    uriString: String,
    mimeType: String? = null,
): Boolean {
    if (uriString.isBlank()) return false
    val uri = uriString.toUri()
    val scheme = uri.scheme?.lowercase(Locale.US)
    if (scheme != "content" && scheme != "file" && scheme != "android.resource") return false

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType?.takeIf(String::isNotBlank) ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

/**
 * Parses the persisted export map (`id\u001Furi\u001E…`, see [ExportedFileUrisKey]).
 */
fun parseExportedFileUriMap(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.split('\u001E')
        .mapNotNull { entry ->
            val sep = entry.indexOf('\u001F')
            if (sep <= 0) null
            else entry.substring(0, sep) to entry.substring(sep + 1)
        }
        .filter { it.first.isNotBlank() && it.second.isNotBlank() }
        .toMap()
}

/** True when [uriString] is a readable content/file export the player can open offline. */
fun exportedFileUriExists(context: Context, uriString: String): Boolean {
    if (uriString.isBlank()) return false
    val uri = uriString.toUri()
    return when (uri.scheme?.lowercase(Locale.US)) {
        "content" -> {
            DocumentFile.fromSingleUri(context, uri)?.exists() == true ||
                runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
                    true
                }.getOrDefault(false)
        }
        "file" -> {
            DocumentFile.fromSingleUri(context, uri)?.exists() == true ||
                runCatching { java.io.File(uri.path ?: return false).exists() }.getOrDefault(false)
        }
        else -> false
    }
}

/**
 * Looks up the SAF URI persisted after a successful export for [songId].
 * Encoding: `id\u001Furi\u001Eid\u001Furi` (see [ExportedFileUrisKey]).
 */
suspend fun lookupExportedFileUri(context: Context, songId: String): String? {
    if (songId.isBlank()) return null
    val raw = context.dataStore.data.first()[ExportedFileUrisKey].orEmpty()
    return parseExportedFileUriMap(raw)[songId]
}
