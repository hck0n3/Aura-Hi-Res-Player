package iad1tya.echo.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnhancedShuffleKey
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Enhanced Shuffle ("Aleatorio mejorado") UI helpers, shared by every playlist/library screen so the
 * indicator looks and behaves identically everywhere.
 *
 * [rememberPlayedShuffleSet] reactively reads the persistent per-context no-repeat memory (updates live as
 * songs sound). Pass its result to [SongListItem]'s `playedInShuffle` for the dim + check per row, and its
 * size ∩ the visible list to [EnhancedShuffleChip] for the "activo · X/Y" header pill.
 *
 * Both honour the master [EnhancedShuffleKey] toggle: with the feature OFF the set is empty and the chip
 * hides. Visual "already played" checkmarks must NOT use this set alone — OR it with
 * [rememberHeardSongIds] / `totalPlayTime > 0` so the ticks still show when Aleatorio mejorado is off.
 */
@Composable
fun rememberPlayedShuffleSet(contextId: String?): Set<String> {
    val database = LocalDatabase.current
    val enhancedOn by rememberPreference(EnhancedShuffleKey, defaultValue = true)
    // Hooks stay unconditional (Compose rule); a null context yields a stable empty flow.
    val flow = remember(contextId) {
        if (contextId == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else database.playedSongIdsForContextFlow(contextId)
    }
    val playedIds by flow.collectAsState(initial = emptyList())
    return remember(playedIds, enhancedOn) { if (enhancedOn) playedIds.toHashSet() else emptySet() }
}

/**
 * Lifetime-heard song ids (`totalPlayTime > 0`). Independent of [EnhancedShuffleKey] — this is the
 * checkmark the owner asked for even when Aleatorio mejorado is off.
 */
@Composable
fun rememberHeardSongIds(songIds: List<String>): Set<String> {
    val database = LocalDatabase.current
    val stableIds = remember(songIds) { songIds.distinct() }
    val flow = remember(stableIds) {
        if (stableIds.isEmpty()) {
            flowOf(emptySet<String>())
        } else {
            val chunks = stableIds.chunked(500)
            if (chunks.size == 1) {
                database.songIdsWithPlayTimeFlow(chunks.first()).map { it.toHashSet() }
            } else {
                combine(chunks.map { chunk -> database.songIdsWithPlayTimeFlow(chunk) }) { arrays: Array<List<String>> ->
                    buildSet<String> { arrays.forEach { addAll(it) } }
                }
            }
        }
    }
    val heard by flow.collectAsState(initial = emptySet())
    return heard
}

/**
 * Header pill: "🔀 Aleatorio mejorado · X/Y reproducidas". Its very presence is the "está activo" signal the
 * user asked for; the count shows the no-repeat cycle progressing. Hidden when the feature is off or the list
 * is empty.
 */
@Composable
fun EnhancedShuffleChip(
    playedCount: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val enhancedOn by rememberPreference(EnhancedShuffleKey, defaultValue = true)
    if (!enhancedOn || total == 0) return
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.shuffle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Aleatorio mejorado · $playedCount/$total reproducidas",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
