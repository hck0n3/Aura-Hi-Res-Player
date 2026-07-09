

package iad1tya.echo.music.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.ui.component.TextFieldDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ImportPlaylistDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>, 
    playlistTitle: String,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    val textFieldValue by remember { mutableStateOf(TextFieldValue(text = playlistTitle)) }
    var songIds by remember {
        mutableStateOf<List<String>?>(null) 
    }

    if (isVisible) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            initialTextFieldValue = textFieldValue,
            autoFocus = false,
            onDismiss = onDismiss,
            onDone = { finalName ->
                val newPlaylist = PlaylistEntity(
                    name = finalName
                )

                coroutineScope.launch(Dispatchers.IO) {
                    // Create/commit the playlist row FIRST, on this same thread, so the
                    // read-back below is guaranteed to observe it (deterministic happens-before).
                    // Previously the insert was fired via database.query{} on Room's async
                    // queryExecutor while a separate coroutine read the row through a Flow's
                    // first emission — with no ordering, the SELECT could snapshot before the
                    // INSERT committed, silently producing an empty imported playlist.
                    database.insert(newPlaylist)

                    // Suspend read-back after the synchronous insert has completed.
                    val playlist = database.getPlaylistById(newPlaylist.id)

                    if (playlist != null) {
                        songIds = onGetSong()
                        database.addSongToPlaylist(playlist, songIds!!)
                    }

                    onDismiss()
                }
            }
        )
    }
}
