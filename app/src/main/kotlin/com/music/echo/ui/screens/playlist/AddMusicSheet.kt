package iad1tya.echo.music.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.innertube.models.SongItem
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.ui.component.SongListItem
import iad1tya.echo.music.ui.component.YouTubeListItem
import iad1tya.echo.music.utils.listItemShape
import iad1tya.echo.music.viewmodels.LocalPlaylistViewModel

/**
 * Apple-Music-style "Add Music" bottom sheet: a global YouTube-Music song search, plus From-Replay,
 * Recently-Added and Suggested sections, and a multi-select of the user's liked library. Each row has
 * a "+" (or, in the library section, a checkbox with a batch "Add (N)" action). Rows preview in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMusicSheet(
    viewModel: LocalPlaylistViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val previewController = rememberSongPreviewController()

    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val replaySongs by viewModel.replaySongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val suggested by viewModel.sheetSuggestedSongs.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()

    val selection = remember { mutableStateListOf<String>() }

    // Rows whose song was added through THIS sheet — flips their "+" to a check (instant feedback).
    val addedIds = remember { mutableStateListOf<String>() }

    // Prune ids whose song left the library so a stale id can't inflate the "Add (N)" count.
    LaunchedEffect(librarySongs) {
        selection.retainAll { id -> librarySongs.any { it.id == id } }
    }

    // Resolve section titles here (composable scope) — they can't be read inside the LazyColumn builder.
    val suggestedTitle = stringResource(R.string.suggested_songs)
    val replayTitle = stringResource(R.string.from_replay)
    val recentTitle = stringResource(R.string.recently_added)
    val libraryTitle = stringResource(R.string.your_library)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = stringResource(R.string.add_music),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQuery,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text(stringResource(R.string.search_youtube_music)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQuery("") }) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (query.isNotBlank()) {
                        // --- Global search results ---
                        itemsIndexed(searchResults, key = { _, it -> "search-${it.id}" }) { index, item ->
                            YouTubeListItem(
                                item = item,
                                isActive = previewController.currentPreviewId == item.id,
                                isPlaying = previewController.currentPreviewId == item.id && !previewController.isLoading,
                                isSwipeable = false,
                                shape = listItemShape(index = index, count = searchResults.size),
                                trailingContent = {
                                    AddOrAddedButton(
                                        added = item.id in addedIds,
                                        onAdd = {
                                            viewModel.addOnlineSongs(listOf(item)) { addedIds.add(item.id) }
                                        },
                                    )
                                },
                                onThumbnailClick = { previewController.toggle(item.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { previewController.toggle(item.id) },
                            )
                        }
                        if (searchResults.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (searchLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    } else {
                                        Text(
                                            text = stringResource(R.string.add_music_no_results),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // --- Suggested ---
                        localSongSection(
                            title = suggestedTitle,
                            songs = suggested,
                            keyPrefix = "sug",
                            previewController = previewController,
                            addedIds = addedIds,
                            onAdd = { song -> viewModel.addLocalSongs(listOf(song.id)) { addedIds.add(song.id) } },
                        )
                        // --- From Replay ---
                        localSongSection(
                            title = replayTitle,
                            songs = replaySongs,
                            keyPrefix = "replay",
                            previewController = previewController,
                            addedIds = addedIds,
                            onAdd = { song -> viewModel.addLocalSongs(listOf(song.id)) { addedIds.add(song.id) } },
                        )
                        // --- Recently Added ---
                        localSongSection(
                            title = recentTitle,
                            songs = recentlyAdded,
                            keyPrefix = "recent",
                            previewController = previewController,
                            addedIds = addedIds,
                            onAdd = { song -> viewModel.addLocalSongs(listOf(song.id)) { addedIds.add(song.id) } },
                        )
                        // --- Library multi-select ---
                        if (librarySongs.isNotEmpty()) {
                            item(key = "library-header") {
                                SectionHeader(libraryTitle)
                            }
                            itemsIndexed(librarySongs, key = { _, it -> "lib-${it.id}" }) { index, song ->
                                val checked = selection.contains(song.id)
                                SongListItem(
                                    song = song,
                                    isActive = previewController.currentPreviewId == song.id,
                                    isPlaying = previewController.currentPreviewId == song.id && !previewController.isLoading,
                                    showDownloadIcon = false,
                                    shape = listItemShape(index = index, count = librarySongs.size),
                                    trailingContent = {
                                        // Instant single add — ADDITIONAL to the multi-select checkbox.
                                        AddOrAddedButton(
                                            added = song.id in addedIds,
                                            onAdd = {
                                                viewModel.addLocalSongs(listOf(song.id)) { addedIds.add(song.id) }
                                            },
                                        )
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                if (it) selection.add(song.id) else selection.remove(song.id)
                                            },
                                        )
                                    },
                                    onThumbnailClick = { previewController.toggle(song.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { previewController.toggle(song.id) },
                                )
                            }
                        }
                    }
                }
            }

            if (selection.isNotEmpty()) {
                Button(
                    onClick = {
                        val ids = selection.toList()
                        viewModel.addLocalSongs(ids) { addedIds.addAll(ids) }
                        selection.clear()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp),
                ) {
                    Text(
                        text = stringResource(R.string.add_selected, selection.size),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** A section title header + a "+"-per-row local-song list, added to a LazyColumn scope. */
private fun androidx.compose.foundation.lazy.LazyListScope.localSongSection(
    title: String,
    songs: List<Song>,
    keyPrefix: String,
    previewController: SongPreviewController,
    addedIds: List<String>,
    onAdd: (Song) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "$keyPrefix-header") {
        SectionHeader(title)
    }
    itemsIndexed(songs, key = { _, it -> "$keyPrefix-${it.id}" }) { index, song ->
        SongListItem(
            song = song,
            isActive = previewController.currentPreviewId == song.id,
            isPlaying = previewController.currentPreviewId == song.id && !previewController.isLoading,
            showDownloadIcon = false,
            shape = listItemShape(index = index, count = songs.size),
            trailingContent = {
                AddOrAddedButton(
                    added = song.id in addedIds,
                    onAdd = { onAdd(song) },
                )
            },
            onThumbnailClick = { previewController.toggle(song.id) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { previewController.toggle(song.id) },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}
