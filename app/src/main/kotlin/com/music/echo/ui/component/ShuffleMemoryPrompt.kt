package iad1tya.echo.music.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shuffle button gate for the Enhanced Shuffle ("Aleatorio mejorado") no-repeat memory.
 *
 * Every Shuffle button that owns a `contextId` ("PL:", "OL:", "AP:", "AL:", "AR:", "LIB:") routes its onClick
 * through this. When that context already has played-song memory the user is asked whether to CONTINUE the
 * current no-repeat lap or START OVER; with no memory (or with the feature off, since
 * [rememberPlayedShuffleSet] then returns an empty set) nothing is asked and the button behaves exactly as
 * before — no dialog, no extra tap.
 *
 * "Start over" wipes the context via `clearEnhancedContext` on [Dispatchers.IO] and AWAITS it before
 * invoking `onShuffle(true)`, so the fresh lap really starts from an empty memory. Because the call site's
 * reactive played-set has not necessarily refreshed by then, `resetMemory = true` call sites must order the
 * queue as if nothing had been played (a plain shuffle of the full list) instead of reusing the
 * unplayed-first partition.
 *
 * The dialog is hosted by this composable, so call sites stay one-liners. A ··· menu action must NOT
 * dismiss its bottom sheet before the trigger runs: pass the `onDismiss()` inside [onShuffle] so the sheet
 * only closes once the user has chosen (matching how the rename/delete dialogs already work there).
 *
 * @param contextId the shuffle context whose memory is inspected/cleared; `null` disables the prompt.
 * @param playedCount how many of the [totalCount] visible songs are already in the memory.
 * @param totalCount size of the list the button shuffles.
 * @param onShuffle starts playback; `resetMemory` tells the call site which ordering to build.
 * @return the lambda to hand to the button's `onClick`.
 */
@Composable
fun rememberShuffleMemoryPrompt(
    contextId: String?,
    playedCount: Int,
    totalCount: Int,
    onShuffle: (resetMemory: Boolean) -> Unit,
): () -> Unit {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    var showDialog by rememberSaveable { mutableStateOf(false) }

    // Keep the trigger's captures fresh: it is remembered once, but the list/memory behind it changes.
    val currentContextId by rememberUpdatedState(contextId)
    val currentPlayedCount by rememberUpdatedState(playedCount)
    val currentOnShuffle by rememberUpdatedState(onShuffle)

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.shuffle_memory_title)) },
            text = {
                Text(stringResource(R.string.shuffle_memory_message, playedCount, totalCount))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        currentOnShuffle(false)
                    },
                ) {
                    Text(stringResource(R.string.shuffle_memory_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        val id = currentContextId
                        if (id == null) {
                            currentOnShuffle(true)
                        } else {
                            coroutineScope.launch {
                                // AWAIT the wipe: starting the queue first would let the old memory
                                // re-order (and re-dim) the very lap the user asked to restart.
                                withContext(Dispatchers.IO) { database.clearEnhancedContext(id) }
                                currentOnShuffle(true)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.shuffle_memory_reset))
                }
            },
        )
    }

    return remember {
        {
            if (currentContextId == null || currentPlayedCount == 0) {
                // Untouched legacy path: no memory here, so no question — play immediately.
                currentOnShuffle(false)
            } else {
                showDialog = true
            }
        }
    }
}
