

package iad1tya.echo.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.YtmUploadSyncKey
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.extensions.isSyncEnabled
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.logging.Logger

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    onPlaylistCreated: ((String) -> Unit)? = null,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()

    // "Que las playlists que haga no pregunte si las quiero sincronizar — que eso ya lo haga por
    // default." When the library upload sync is on we do NOT ask: a new playlist is created on the
    // YouTube account silently. The opt-out lives in Ajustes ▸ Sincronizar con YouTube Music; turning
    // it OFF brings the old per-playlist switch back (defaulting to off), so the user never loses the
    // ability to keep a playlist local.
    //
    // The fallback here is FALSE and must stay false: the stored value is written explicitly, once,
    // by the YtmUploadOptInV1AppliedKey migration (ON for fresh installs — the owner's default — OFF
    // for installs that were merely updated). Reading a `true` fallback would silently upload a
    // playlist for an existing user before that migration lands.
    val uploadSyncEnabled by rememberPreference(YtmUploadSyncKey, false)
    // `allowSyncing = false` still means "no YouTube playlist from this dialog" (Backup & Restore
    // passes it): honouring it keeps that caller's contract. Those playlists are not lost to the
    // account either — LibraryUploadSync's backfill sweeps every local-only playlist later.
    val autoSync = allowSyncing && uploadSyncEnabled && isSignedIn
    var syncedPlaylist by remember { mutableStateOf(false) }
    val showSyncSwitch = allowSyncing && !uploadSyncEnabled

    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
        onDismiss = onDismiss,
        onDone = { playlistName ->
            coroutineScope.launch(Dispatchers.IO) {
                val wantsRemote = autoSync || (syncedPlaylist && isSignedIn)
                // runCatching is LOAD-BEARING now that this is the default path: YouTube.createPlaylist
                // wraps runBlocking and does NOT catch — an offline tap used to throw here and the
                // playlist was never created locally either ("crear playlist no hace nada"). Falling
                // back to browseId = null keeps the playlist; LibraryUploadSync links it to the account
                // on the next run.
                val browseId = if (wantsRemote) {
                    runCatching { YouTube.createPlaylist(playlistName) }
                        .onFailure {
                            Logger.getLogger("CreatePlaylistDialog")
                                .warning("Remote playlist creation failed, keeping it local: ${it.message}")
                        }
                        .getOrNull()
                } else {
                    if (syncedPlaylist) Logger.getLogger("CreatePlaylistDialog").warning("Not signed in")
                    null
                }

                val playlistEntity = PlaylistEntity(
                    name = playlistName,
                    browseId = browseId,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                )

                database.query {
                    insert(playlistEntity)
                }


                withContext(Dispatchers.Main) {
                    onPlaylistCreated?.invoke(playlistEntity.id)
                }
            }
        },
        extraContent = {
            if (showSyncSwitch) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 40.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.sync_playlist),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.allows_for_sync_witch_youtube),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Switch(
                            checked = syncedPlaylist,
                            onCheckedChange = {
                                // isSyncEnabled() does a blocking DataStore read; run it off the main
                                // thread. The launch body runs on the Compose Main dispatcher, so the
                                // Toast/state update below stay on Main exactly as before.
                                coroutineScope.launch {
                                    val isYtmSyncEnabled = withContext(Dispatchers.IO) {
                                        context.isSyncEnabled()
                                    }
                                    if (!isSignedIn && !syncedPlaylist) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.not_logged_in_youtube),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (!isYtmSyncEnabled) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.sync_disabled),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        syncedPlaylist = !syncedPlaylist
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}
