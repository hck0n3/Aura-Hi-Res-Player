package iad1tya.echo.music.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.LocalAuraFloatingChrome
import iad1tya.echo.music.ui.newui.auraFloatingContainerColor
import iad1tya.echo.music.ui.newui.auraFloatingScrimColor
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryBottomSheet(
    onDismiss: () -> Unit,
    totalPlayTimeMs: Long,
    allTimePlayTimeMs: Long,
    uniqueSongs: Int,
    uniqueArtists: Int,
    uniqueAlbums: Int,
    periodLabel: String,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // StatsScreen itself is built entirely from shared primitives (`ChoiceChipsRow`,
    // `NavigationTitle`, `LocalSongsGrid`/`LocalArtistsGrid`/`LocalAlbumsGrid`), so the `Items.kt`
    // restyle already reaches it. THIS sheet is the one piece of chrome it hand-rolls, so it is the
    // one piece that needed doing. ONE flag read for the whole sheet.
    val skin = rememberAuraPanelSkin()
    val auraDark = skin.enabled && skin.darkGround

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = if (auraDark) auraFloatingContainerColor()
        else MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = if (auraDark) auraFloatingScrimColor() else BottomSheetDefaults.ScrimColor,
        shape = if (auraDark) AuraShapes.Sheet else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp,
    ) {
        CompositionLocalProvider(LocalAuraFloatingChrome provides auraDark) {
        iad1tya.echo.music.ui.newui.AuraFrostWindowIfPremium()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row (Standard settings top app bar aesthetic)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.activity_history),
                        style = if (skin.enabled) AuraType.SheetTitle else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = periodLabel,
                        style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                        color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                        tint = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Total Listening Time Hero Card (Pixel Settings banner style)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(24.dp),
                color = if (skin.enabled) skin.accent.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val durationText = formatDuration(totalPlayTimeMs)
                    val durationFontSize = when {
                        durationText.length <= 7 -> 36.sp
                        durationText.length == 8 -> 30.sp
                        durationText.length == 9 -> 26.sp
                        else -> 22.sp
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.total_listening_time),
                            style = if (skin.enabled) AuraType.SectionLabel else MaterialTheme.typography.labelMedium,
                            color = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                            fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = durationText,
                            // The headline number is TECHNICAL DATA — the render sets those in
                            // monospace. The three responsive sizes are kept exactly, so a long
                            // "1234h 56m" still shrinks the same way on both paths.
                            style = if (skin.enabled)
                                AuraType.Technical.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = durationFontSize,
                                    letterSpacing = (-1).sp,
                                )
                            else
                                MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = durationFontSize,
                                    letterSpacing = (-1).sp
                                ),
                            color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.timer),
                            contentDescription = null,
                            tint = if (auraDark) AuraPalette.OnAccent else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Segmented Pomodoro Timer Settings style row for Unique Counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val segmentBgColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerHigh

                // Songs Segment (Rounded Start)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(112.dp)
                        .background(
                            color = segmentBgColor,
                            shape = RoundedCornerShape(
                                topStart = 20.dp,
                                bottomStart = 20.dp,
                                topEnd = 6.dp,
                                bottomEnd = 6.dp
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val songsText = uniqueSongs.toString()
                        val songsFontSize = when {
                            songsText.length <= 3 -> 26.sp
                            songsText.length == 4 -> 21.sp
                            else -> 17.sp
                        }
                        Text(
                            text = songsText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = songsFontSize,
                                letterSpacing = (-0.5).sp
                            ),
                            color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.songs),
                            style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Artists Segment (Middle)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(112.dp)
                        .background(
                            color = segmentBgColor,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.artist),
                            contentDescription = null,
                            tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val artistsText = uniqueArtists.toString()
                        val artistsFontSize = when {
                            artistsText.length <= 3 -> 26.sp
                            artistsText.length == 4 -> 21.sp
                            else -> 17.sp
                        }
                        Text(
                            text = artistsText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = artistsFontSize,
                                letterSpacing = (-0.5).sp
                            ),
                            color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.artists),
                            style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(3.dp))

                // Albums Segment (Rounded End)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(112.dp)
                        .background(
                            color = segmentBgColor,
                            shape = RoundedCornerShape(
                                topEnd = 20.dp,
                                bottomEnd = 20.dp,
                                topStart = 6.dp,
                                bottomStart = 6.dp
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                            tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val albumsText = uniqueAlbums.toString()
                        val albumsFontSize = when {
                            albumsText.length <= 3 -> 26.sp
                            albumsText.length == 4 -> 21.sp
                            else -> 17.sp
                        }
                        Text(
                            text = albumsText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = albumsFontSize,
                                letterSpacing = (-0.5).sp
                            ),
                            color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.albums),
                            style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // All-Time Total Play Time Card (Clean Wide rounded settings-style row)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(24.dp),
                color = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (skin.enabled) skin.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.timer),
                                contentDescription = null,
                                tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.all_time),
                                style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelMedium,
                                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.total_listening_time),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    val allTimeText = formatDuration(allTimePlayTimeMs)
                    val allTimeFontSize = when {
                        allTimeText.length <= 7 -> 22.sp
                        allTimeText.length == 8 -> 20.sp
                        allTimeText.length == 9 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = allTimeText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = allTimeFontSize,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        }
    }
}
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0m"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
