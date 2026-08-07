package iad1tya.echo.music.ui.screens.playlist

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.music.innertube.YouTube
import com.yalantis.ucrop.UCrop
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.ui.component.ActionPromptDialog
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.CustomThumbnailMenu
import iad1tya.echo.music.ui.theme.rememberEffectiveDarkTheme
import iad1tya.echo.music.utils.reportException
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * # "Editar la portada" de una lista — one implementation, two headers
 *
 * Picking a cover is not decoration: it crops through uCrop, writes a stable file under `filesDir`,
 * uploads to YouTube for a synced playlist, falls back to a LOCAL cover when that upload is rejected,
 * and updates the `PlaylistEntity` either way. That machinery used to live inside the classic
 * `LocalPlaylistHeader`. The redesigned header needs exactly the same behaviour, so it was lifted here
 * instead of being written a second time — one copy, one fix.
 *
 * Everything below is the classic code, moved: the same `filesDir/playlist_covers/<id>.jpg`
 * destination, the same uCrop options, the same `YouTube.uploadCustomThumbnailLink` /
 * `removeThumbnailPlaylist` calls, the same snackbar on a `ClientRequestException`, the same
 * "the user's explicit choice wins" fallback.
 */
@Stable
class PlaylistCoverEditor internal constructor(
    private val overrideState: MutableState<String?>,
    private val thumbnails: List<String>,
    /** Opens the system picker straight away (the "Cambiar" entry of [CustomThumbnailMenu]). */
    val pickCover: () -> Unit,
    /** Drops the custom cover: locally for a local playlist, through YouTube for a synced one. */
    val removeCover: () -> Unit,
    /** The ✎ on the cover: the menu when there already is a custom cover, the note prompt otherwise. */
    val onEditCoverClick: () -> Unit,
) {
    /**
     * The cover to DRAW: the just-chosen one while the database row catches up, else the stored one.
     */
    val displayThumbnail: String?
        get() = overrideState.value ?: thumbnails.firstOrNull()

    /**
     * Whether the current cover is one the user chose. The two markers are the classic ones: a YouTube
     * custom upload (`studio_square_thumbnail`) or a locally cropped file.
     */
    val isCustomThumbnail: Boolean
        get() = displayThumbnail?.let {
            it.contains("studio_square_thumbnail") || it.contains("content://com.echomusic.music")
        } ?: false
}

/**
 * Builds the cover editor for [playlist] and hosts its "before you pick" prompt.
 *
 * @param snackbarHostState where a YouTube rejection is reported. The cover is still kept locally in
 *   that case, so the user's choice is never silently dropped.
 */
@Composable
fun rememberPlaylistCoverEditor(
    playlist: Playlist,
    snackbarHostState: SnackbarHostState,
): PlaylistCoverEditor {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()

    val overrideThumbnail = remember(playlist.id) { mutableStateOf<String?>(null) }
    val result = remember { mutableStateOf<Uri?>(null) }
    var pendingCropDestUri by remember { mutableStateOf<Uri?>(null) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val output = res.data?.let { UCrop.getOutput(it) } ?: pendingCropDestUri
            if (output != null) result.value = output
        }
    }

    val cropColor = MaterialTheme.colorScheme
    // The APP's dark/light — see [rememberEffectiveDarkTheme]. Its ONE consumer is
    // `setStatusBarLight(!darkTheme)` below, while the cropper's toolbar and root come from
    // `cropColor` (the live ColorScheme, already forced dark under "Interfaz nueva").
    val darkTheme = rememberEffectiveDarkTheme()

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { sourceUri ->
            // filesDir, NOT cacheDir: Android purges cacheDir at will, which killed the persisted
            // content:// URI and left the playlist painting the error logo forever. One stable file
            // per playlist (overwritten on re-crop; the URI string stays the same, so a same-session
            // re-change may briefly show coil's cached image — next load reads the fresh bytes).
            val coversDir = java.io.File(context.filesDir, "playlist_covers").apply { mkdirs() }
            val destFile = java.io.File(coversDir, "${playlist.id}.jpg")
            val destUri =
                FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", destFile)
            pendingCropDestUri = destUri

            val options = UCrop.Options().apply {
                setCompressionFormat(Bitmap.CompressFormat.JPEG)
                setCompressionQuality(90)
                setHideBottomControls(true)
                setToolbarTitle(context.getString(R.string.edit_playlist_cover))

                setStatusBarLight(!darkTheme)

                setToolbarColor(cropColor.surface.toArgb())
                setToolbarWidgetColor(cropColor.inverseSurface.toArgb())
                setRootViewBackgroundColor(cropColor.surface.toArgb())
                setLogoColor(cropColor.surface.toArgb())
            }

            val intent = UCrop.of(sourceUri, destUri)
                .withAspectRatio(1f, 1f)
                .withOptions(options)
                .getIntent(context)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            cropLauncher.launch(intent)
        }
    }

    LaunchedEffect(result.value) {
        val uri = result.value ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            when {
                playlist.playlist.browseId == null -> {
                    overrideThumbnail.value = uri.toString()
                    database.query {
                        update(playlist.playlist.copy(thumbnailUrl = uri.toString()))
                    }
                }

                else -> {
                    val bytes = uriToByteArray(context, uri)
                    YouTube.uploadCustomThumbnailLink(
                        playlist.playlist.browseId,
                        bytes!!,
                    ).onSuccess { newThumbnailUrl ->
                        overrideThumbnail.value = newThumbnailUrl
                        database.query {
                            update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                        }
                    }.onFailure {
                        if (it is ClientRequestException) {
                            snackbarHostState.showSnackbar(
                                "${it.response.status.value} ${it.response.status.description} — portada guardada en local",
                            )
                        }
                        reportException(it)
                        // Fallback: the YouTube thumbnail upload failed (often a 403). Keep the user's
                        // chosen cover LOCALLY (same path as a local playlist) instead of a silent no-op.
                        overrideThumbnail.value = uri.toString()
                        database.query {
                            update(playlist.playlist.copy(thumbnailUrl = uri.toString()))
                        }
                    }
                }
            }
        }
    }

    val pickCover: () -> Unit = {
        pickLauncher.launch(
            PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val removeCover: () -> Unit = {
        when {
            playlist.playlist.browseId == null -> {
                overrideThumbnail.value = null
                database.query {
                    update(playlist.playlist.copy(thumbnailUrl = null))
                }
            }

            else -> {
                scope.launch(Dispatchers.IO) {
                    YouTube.removeThumbnailPlaylist(playlist.playlist.browseId)
                        .onSuccess { newThumbnailUrl ->
                            overrideThumbnail.value = newThumbnailUrl
                            database.query {
                                update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                            }
                        }
                }
            }
        }
    }

    // The same test [PlaylistCoverEditor.isCustomThumbnail] applies, read at TAP time so a cover chosen
    // moments ago already counts.
    val isCustomNow: () -> Boolean = {
        (overrideThumbnail.value ?: playlist.thumbnails.firstOrNull())?.let {
            it.contains("studio_square_thumbnail") || it.contains("content://com.echomusic.music")
        } ?: false
    }

    val onEditCoverClick: () -> Unit = {
        if (isCustomNow()) {
            menuState.show {
                CustomThumbnailMenu(
                    onEdit = pickCover,
                    onRemove = removeCover,
                    onDismiss = menuState::dismiss,
                )
            }
        } else {
            showEditNoteDialog = true
        }
    }

    if (showEditNoteDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.edit_playlist_cover),
            onDismiss = { showEditNoteDialog = false },
            onConfirm = {
                showEditNoteDialog = false
                pickCover()
            },
            onCancel = { showEditNoteDialog = false },
        ) {
            if (playlist.playlist.browseId != null) {
                Text(
                    text = stringResource(R.string.edit_playlist_cover_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = stringResource(R.string.edit_playlist_cover_note_wait),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }

    return PlaylistCoverEditor(
        overrideState = overrideThumbnail,
        thumbnails = playlist.thumbnails,
        pickCover = pickCover,
        removeCover = removeCover,
        onEditCoverClick = onEditCoverClick,
    )
}
