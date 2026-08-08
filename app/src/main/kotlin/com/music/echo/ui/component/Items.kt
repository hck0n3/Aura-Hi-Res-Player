

package iad1tya.echo.music.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.CropAlbumArtKey
import iad1tya.echo.music.constants.GridItemSize
import iad1tya.echo.music.constants.GridItemsSizeKey
import iad1tya.echo.music.constants.GridThumbnailHeight
import iad1tya.echo.music.constants.ListItemHeight
import iad1tya.echo.music.constants.ListThumbnailSize
import iad1tya.echo.music.constants.SmallGridThumbnailHeight
import iad1tya.echo.music.constants.SwipeToSongKey
import iad1tya.echo.music.constants.ThumbnailCornerRadius
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playback.queues.LocalAlbumRadio
import iad1tya.echo.music.ui.newui.AuraIcons
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraQualityBadge
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.LocalAuraFloatingChrome
import iad1tya.echo.music.ui.newui.rememberNewUiEnabled
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.ui.utils.tvFocusableItem
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.joinByBullet
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

const val ActiveBoxAlpha = 0.6f

// ── "Interfaz nueva" skin for every row and card ──────────────────────────────────────────────────
//
// This file is the single owner of every song / album / artist / playlist row and card in the app:
// 56 call sites across 27 screen files (search, álbum, artista, historial, sin conexión, every
// Biblioteca sub-screen, every playlist screen, the menus…). Restyling HERE is what makes those
// screens adopt the redesign without rewriting them; `Library.kt` is only a set of wrappers that
// delegate straight into these composables, so it comes along for free.
//
// The rules this seam exists to keep:
//  · ONE resolution point. [rememberAuraItemSkin] is read exactly once per row / per card, published
//    down through [LocalAuraItemSkin], and every downstream piece (the thumbnail's corner radius, the
//    badges, the `Icon.*` glyphs — including the badge lambdas the 27 screens pass in themselves)
//    reads it for free. Scattering `if (newUi)` through the layout, or calling `rememberPreference`
//    again in each helper, would both bloat the classic path and add a DataStore subscription per row.
//  · With the flag OFF every expression below reduces to the original literal, so the classic path is
//    provably unchanged rather than merely believed to be.
//  · The redesign is a DARK design (AuraPalette has no light variant, and all six rebuilt screens
//    paint `AuraPalette.Ground`). These rows, unlike those screens, are drawn on whatever background
//    the classic screen has. So the skin resolves its ink against the ambient surface: on a dark
//    ground it is the render's `#EAF2FF` / teal; on a light one it keeps Material's ink and only the
//    type, the rhythm and the corner radii change. Painting `OnGround` unconditionally would have put
//    near-white text on a near-white surface for anyone running the beta in light mode.

/**
 * Everything the redesigned row needs, resolved once. [enabled] is the "Interfaz nueva" master
 * switch; [darkGround] says whether the ambient surface can carry the render's own palette.
 */
@Immutable
data class AuraItemSkin(
    val enabled: Boolean = false,
    val darkGround: Boolean = false,
    /** Row/card title ink. */
    val ink: Color = Color.Unspecified,
    /** Subtitle / secondary ink. */
    val inkMuted: Color = Color.Unspecified,
    /** Teal on a dark ground, the theme's primary on a light one. Badges, ticks, active markers. */
    val accent: Color = Color.Unspecified,
    /**
     * Resting row fill. The render's list is a FLAT sheet, so this is the SCREEN's own surface rather
     * than the classic per-row `surfaceContainer` card — but it is still OPAQUE, never transparent: a
     * see-through row shows the list underneath it while it is being dragged in a playlist, and shows
     * the sheet behind it in the queue.
     */
    val fill: Color = Color.Transparent,
    /**
     * "SONANDO" row fill + hairline — the render's `rgba(63,231,206,.10)` / `.25`, already composited
     * over [fill] so the wash stays opaque.
     */
    val activeFill: Color = Color.Transparent,
    val activeLine: Color = Color.Transparent,
    /** Multi-select highlight, likewise composited. */
    val selectedFill: Color = Color.Transparent,
)

/** Classic. The default of [LocalAuraItemSkin], so anything composed outside a row stays classic. */
private val ClassicItemSkin = AuraItemSkin()

/**
 * The skin in force for the current row / card. Provided by [ListItem] and [GridItem]; defaults to
 * classic so a thumbnail or a badge composed on its own is never half-restyled.
 */
val LocalAuraItemSkin = staticCompositionLocalOf { ClassicItemSkin }

/**
 * Resolves the skin. The ONLY place in this file that reads the flag — one DataStore subscription per
 * row instead of one per glyph.
 */
@Composable
fun rememberAuraItemSkin(): AuraItemSkin {
    val newUi = rememberNewUiEnabled()
    val floating = LocalAuraFloatingChrome.current
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    return remember(newUi, floating, surface, onSurface, onSurfaceVariant, primary) {
        if (!newUi) {
            ClassicItemSkin
        } else {
            val darkGround = surface.luminance() < 0.4f
            val wash = if (darkGround) AuraPalette.Teal else primary
            // Inside frost dialogs/sheets the plate IS the surface — opaque Ground row cards would
            // erase the translucency the owner asked for. Screens keep opaque [surface] (drag safety).
            val resting = if (floating) Color.Transparent else surface
            val washBase = if (floating) Color.Transparent else surface
            AuraItemSkin(
                enabled = true,
                darkGround = darkGround,
                ink = if (darkGround) AuraPalette.OnGround else onSurface,
                inkMuted = if (darkGround) AuraPalette.OnGroundMuted else onSurfaceVariant,
                accent = wash,
                fill = resting,
                activeFill = if (floating) wash.copy(alpha = 0.14f)
                else wash.copy(alpha = 0.10f).compositeOver(washBase),
                activeLine = wash.copy(alpha = 0.25f),
                selectedFill = if (floating) wash.copy(alpha = 0.22f)
                else wash.copy(alpha = 0.22f).compositeOver(washBase),
            )
        }
    }
}

/**
 * The artwork corner radius of the redesign (`border-radius:8px` in the render, ×1.4 = 11 dp), applied
 * to whatever [shape] the caller asked for — except a circle, which is an ARTIST avatar and must stay
 * one. Off the new skin the caller's shape is returned untouched.
 */
private fun AuraItemSkin.thumbnailShape(shape: Shape): Shape =
    if (enabled && shape != CircleShape) AuraShapes.Artwork else shape

@Composable
fun currentGridThumbnailHeight(): Dp {
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    return if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
}


/**
 * The universal list row. **Not `inline`** — it has to publish [LocalAuraItemSkin] to its own slots
 * (`thumbnailContent`, `subtitle`, `trailingContent`), and an inline function cannot invoke its inline
 * lambda parameters from inside `CompositionLocalProvider`'s lambda. Dropping `inline` gives the row a
 * restart scope of its own; it changes nothing that is drawn.
 */
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    isAvailable: Boolean = true,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) {
    val skin = rememberAuraItemSkin()
    // The render's sounding row is a rounded 10 px card (×1.4 = `AuraShapes.Highlight`). Only the
    // DEFAULT `RectangleShape` is upgraded: a caller that asked for a shape of its own (the grouped
    // playlist cards) chose it deliberately and keeps it. Off the new skin this IS `shape`.
    val rowShape = if (skin.enabled && shape == RectangleShape) AuraShapes.Highlight else shape
    CompositionLocalProvider(LocalAuraItemSkin provides skin) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // TV/car: a white focus ring wraps the caller's own clickable (placed outermost so onFocusChanged
            // observes it) so the D-pad user can see which row is selected. No-op + zero overhead off-TV.
            modifier = Modifier
                .tvFocusableItem(iad1tya.echo.music.ui.utils.rememberIsTvOrCar())
                .then(modifier)
                .padding(vertical = 2.dp)
                .height(ListItemHeight)
                .padding(horizontal = horizontalPadding)
                .clip(rowShape)
                .background(
                    // New skin: the render's list is a FLAT sheet — the resting row takes the screen's
                    // own surface instead of the classic `surfaceContainer` card — with the sounding row
                    // lifted by the "SONANDO" teal wash. Classic: the three Material fills, unchanged.
                    color = if (skin.enabled) when {
                        isActive -> skin.activeFill
                        isSelected == true && drawHighlight -> skin.selectedFill
                        else -> skin.fill
                    } else when {
                        isActive -> MaterialTheme.colorScheme.secondaryContainer
                        isSelected == true && drawHighlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    }
                )
                .then(
                    // `.then(Modifier)` is identity, so the classic chain is byte-for-byte the old one.
                    if (skin.enabled && isActive) Modifier.border(1.dp, skin.activeLine, rowShape)
                    else Modifier
                )
        ) {
            Box(
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                thumbnailContent()
                if (!isAvailable) {
                    Box(
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .align(Alignment.Center)
                            .background(
                                Color.Black.copy(alpha = 0.25f),
                                skin.thumbnailShape(RoundedCornerShape(ThumbnailCornerRadius))
                            )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(ListThumbnailSize / 2)
                                .align(Alignment.Center)
                                .graphicsLayer { alpha = 1f }
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = title,
                    style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyMedium,
                    color = if (skin.enabled) skin.ink else Color.Unspecified,
                    fontWeight = if (skin.enabled) null else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitle != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        subtitle()
                    }
                }
            }

            trailingContent()
        }
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: AnnotatedString?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) = ListItem(
    title = title,
    subtitle = {
        // The render puts the per-row markers at the RIGHT edge ("Calidad a la derecha. Marca de
        // verificación = descargada"), so on the new skin `badges` is composed in the trailing cluster
        // below instead of ahead of the subtitle. Both lambdas run INSIDE `ListItem`, i.e. inside the
        // skin provider, so this costs no extra preference read. Off the skin, the order is the old one.
        if (!LocalAuraItemSkin.current.enabled) badges()
        if (subtitle != null) {
            val skin = LocalAuraItemSkin.current
            Text(
                text = subtitle,
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = {
        if (LocalAuraItemSkin.current.enabled) badges()
        trailingContent()
    },
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive,
    shape = shape,
    drawHighlight = drawHighlight,
    horizontalPadding = horizontalPadding
)


@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) = ListItem(
    title = title,
    subtitle = {
        // See the AnnotatedString overload above: on the new skin the badges move to the trailing
        // cluster, which is where the render draws them.
        if (!LocalAuraItemSkin.current.enabled) badges()

        if (!subtitle.isNullOrEmpty()) {
            val skin = LocalAuraItemSkin.current
            Text(
                text = subtitle,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.secondary,
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = {
        if (LocalAuraItemSkin.current.enabled) badges()
        trailingContent()
    },
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive,
    shape = shape,
    drawHighlight = drawHighlight,
    horizontalPadding = horizontalPadding
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
    thumbnailHeightOverride: Dp? = null,
) {
    val skin = rememberAuraItemSkin()
    // `GridItemsSizeKey` (Ajustes › Apariencia, GRANDE/PEQUEÑO) still decides the card size on BOTH
    // skins — the redesign restyles the card, it does not take the setting over.
    val gridHeight = thumbnailHeightOverride ?: currentGridThumbnailHeight()
    // TV/car: a focus ring + scale pop wraps the caller's own clickable (placed outermost so onFocusChanged
    // observes it) so the D-pad user sees which card is selected. No-op + zero overhead off-TV.
    val isTvOrCarCard = iad1tya.echo.music.ui.utils.rememberIsTvOrCar()
    CompositionLocalProvider(LocalAuraItemSkin provides skin) {
        Column(
            modifier = Modifier
                .tvFocusable(isTvOrCarCard, RoundedCornerShape(12.dp), scaleFocused = 1.12f)
                .then(
                    if (fillMaxWidth) {
                        modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                    } else {
                        modifier
                            .padding(12.dp)
                            .width(gridHeight * thumbnailRatio)
                    }
                )
        ) {
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = if (fillMaxWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.height(gridHeight)
                }
                    .aspectRatio(thumbnailRatio)
            ) {
                thumbnailContent()
            }

            // Render: the shelf card leaves 6 px between the cover and its title (×1.4 ≈ 8 dp).
            Spacer(modifier = Modifier.height(if (skin.enabled) 8.dp else 6.dp))

            title()

            Row(verticalAlignment = Alignment.CenterVertically) {
                badges()

                subtitle()
            }
        }
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = {
        val skin = LocalAuraItemSkin.current
        Text(
            text = title,
            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
            color = if (skin.enabled) skin.ink else Color.Unspecified,
            fontWeight = if (skin.enabled) null else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        val skin = LocalAuraItemSkin.current
        Text(
            text = subtitle,
            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    showSize: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        // Render, Biblioteca: the bordered LOSSLESS / 320KBPS pill becomes the mono quality badge at the
        // right edge ("24/96" teal for hi-res, "16/44" dimmed). `AuraQualityBadge` is the SAME badge the
        // rebuilt screens already use — it prints only what `FormatEntity` actually knows, so no number
        // is invented. It carries the render's own low-opacity ink, so it is used only where that ink is
        // legible; on a light ground the classic pill stays.
        val qualitySkin = LocalAuraItemSkin.current
        if (qualitySkin.enabled && qualitySkin.darkGround) {
            AuraQualityBadge(
                format = song.format,
                modifier = Modifier.padding(end = 4.dp)
            )
        } else {
            val isLossless = song.format?.codecs == "flac"
            val is320 = song.format?.codecs?.contains("mp4a.40.2") == true && song.format.bitrate >= 320000

            if (isLossless) {
                Text(
                    text = "LOSSLESS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 8.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp)
                )
            } else if (is320) {
                Text(
                    text = "320KBPS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 8.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp)
                )
            }
        }

        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (song.song.explicit) {
            Icon.Explicit()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id)
                .collectAsState(initial = null)
            Icon.Download(download?.state)
        }
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    onSelectionChange: (Boolean) -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
    // Apple-Music-style artwork tap (e.g. in-place preview) — null keeps the thumbnail non-clickable.
    onThumbnailClick: (() -> Unit)? = null,
    drawHighlight: Boolean = true,
    shape: Shape = RectangleShape,
    horizontalPadding: Dp = 16.dp,
    // Enhanced Shuffle ("Aleatorio mejorado"): this song already played in the current no-repeat cycle of the
    // active context -> dim the row + show a check, so the user sees at a glance what's already sounded.
    playedInShuffle: Boolean = false,
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)

    val content: @Composable () -> Unit = {
        ListItem(
            title = song.song.title,
            subtitle = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L),
                if (showSize && song.format?.contentLength != null) {
                    android.text.format.Formatter.formatFileSize(LocalContext.current, song.format.contentLength)
                } else null
            ),
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.song.thumbnailUrl,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                    modifier = Modifier
                        .size(ListThumbnailSize)
                        .then(
                            if (onThumbnailClick != null) Modifier.clickable(onClick = onThumbnailClick)
                            else Modifier
                        )
                )
            },
            trailingContent = {
                // "Ya reproducida" marker — suppressed on the ACTIVE row: the currently-sounding song is
                // technically in the played-set from its first second, but dimming what's playing looks broken.
                if (playedInShuffle && !isActive) {
                    val shuffleSkin = LocalAuraItemSkin.current
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = "Ya reproducida en aleatorio",
                        tint = if (shuffleSkin.enabled) shuffleSkin.accent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(16.dp)
                    )
                }
                trailingContent()
            },
            modifier = if (playedInShuffle && !isActive) modifier.alpha(0.5f) else modifier,
            isSelected = isSelected,
            isActive = isActive,
            shape = shape,
            drawHighlight = drawHighlight,
            horizontalPadding = horizontalPadding
        )
    }

    if (isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = song.toMediaItem(),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
            Icon.Download(download?.state)
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        val skin = LocalAuraItemSkin.current
        Text(
            text = song.song.title,
            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
            color = if (skin.enabled) skin.ink else Color.Unspecified,
            fontWeight = if (skin.enabled) null else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee().fillMaxWidth()
        )
    },
    subtitle = {
        val skin = LocalAuraItemSkin.current
        Text(
            text = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L)
            ),
            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    },
    badges = badges,
    thumbnailContent = {
        val gridHeight = currentGridThumbnailHeight()
        ItemThumbnail(
            thumbnailUrl = song.song.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.size(gridHeight)
        )
        if (!isActive) {
            OverlayPlayButton(
                visible = true
            )
        }
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            val skin = LocalAuraItemSkin.current
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
            )
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.artist.thumbnailUrl?.resize(544, 544))
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(CircleShape),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.artist.thumbnailUrl?.resize(544, 544))
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val downloadUtil = LocalDownloadUtil.current
        val database = LocalDatabase.current

        val songs by produceState<List<Song>>(initialValue = emptyList(), album.id) {
            withContext(Dispatchers.IO) {
                value = database.albumSongs(album.id).first()
            }
        }

        val allDownloads by downloadUtil.downloads.collectAsState()

        val downloadState by remember(songs, allDownloads) {
            androidx.compose.runtime.mutableIntStateOf(
                if (songs.isEmpty()) {
                    Download.STATE_STOPPED
                } else {
                    when {
                        songs.all { allDownloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.any { allDownloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            )
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
        album.album.year?.toString()
    ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.size(ListThumbnailSize)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    badges: @Composable RowScope.() -> Unit = {
        val downloadUtil = LocalDownloadUtil.current
        val database = LocalDatabase.current

        val songs by produceState<List<Song>>(initialValue = emptyList(), album.id) {
            withContext(Dispatchers.IO) {
                value = database.albumSongs(album.id).first()
            }
        }

        val allDownloads by downloadUtil.downloads.collectAsState()

        val downloadState by remember(songs, allDownloads) {
            androidx.compose.runtime.mutableIntStateOf(
                if (songs.isEmpty()) {
                    Download.STATE_STOPPED
                } else {
                    when {
                        songs.all { allDownloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.any { allDownloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            )
        }

        if (album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    // When true the subtitle shows just the year (used on an artist's own albums grid, where repeating
    // the artist on every card is redundant). Default keeps the artist name everywhere else.
    subtitleYearOnly: Boolean = false,
) = GridItem(
    title = {
        val skin = LocalAuraItemSkin.current
        Text(
            text = album.album.title,
            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
            color = if (skin.enabled) skin.ink else Color.Unspecified,
            fontWeight = if (skin.enabled) null else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee().fillMaxWidth()
        )
    },
    subtitle = {
        val skin = LocalAuraItemSkin.current
        Text(
            text = if (subtitleYearOnly) (album.album.year?.toString() ?: "")
                   else album.artists.joinToString { it.name },
            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem
        val scope = rememberCoroutineScope()

        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
        )

        AlbumPlayButton(
            visible = !isActive,
            onClick = {
                scope.launch {
                    val albumWithSongs = withContext(Dispatchers.IO) {
                        database.albumWithSongs(album.id).firstOrNull()
                    }
                    albumWithSongs?.let {
                        playerConnection.playQueue(LocalAlbumRadio(it))
                    }
                }
            }
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        val downloadUtil = LocalDownloadUtil.current
        val database = LocalDatabase.current

        val songs by produceState<List<Song>>(initialValue = emptyList(), playlist.id) {
            withContext(Dispatchers.IO) {
                value = database.playlistSongs(playlist.id).first().map { it.song }
            }
        }

        val allDownloads by downloadUtil.downloads.collectAsState()

        val downloadState by remember(songs, allDownloads) {
            androidx.compose.runtime.mutableIntStateOf(
                if (songs.isEmpty()) {
                    Download.STATE_STOPPED
                } else {
                    when {
                        songs.all { allDownloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.any { allDownloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            )
        }

        Icon.Download(downloadState)
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
    shape: Shape = androidx.compose.ui.graphics.RectangleShape,
) = ListItem(
    title = playlist.playlist.name,
    subtitle = if (autoPlaylist) {
        ""
    } else {
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
            pluralStringResource(
                R.plurals.n_song,
                playlist.playlist.remoteSongCount,
                playlist.playlist.remoteSongCount
            )
        } else {
            pluralStringResource(
                R.plurals.n_song,
                playlist.songCount,
                playlist.songCount
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            fallbackThumbnails = playlist.songCovers,
            size = ListThumbnailSize,
            placeHolder = {
                val painter = when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    
                    stringResource(R.string.uploaded_playlist) -> R.drawable.backup
                    else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.ic_launcher_nobg
                }
                Icon(
                    painter = painterResource(painter),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.size(ListThumbnailSize / 2)
                )
            },
            shape = RoundedCornerShape(ThumbnailCornerRadius)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
    shape = shape
)

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        val downloadUtil = LocalDownloadUtil.current
        val database = LocalDatabase.current

        val songs by produceState<List<Song>>(initialValue = emptyList(), playlist.id) {
            withContext(Dispatchers.IO) {
                value = database.playlistSongs(playlist.id).first().map { it.song }
            }
        }

        val allDownloads by downloadUtil.downloads.collectAsState()

        val downloadState by remember(songs, allDownloads) {
            mutableIntStateOf(
                if (songs.isEmpty()) {
                    Download.STATE_STOPPED
                } else {
                    when {
                        songs.all { allDownloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                        songs.any { allDownloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING) } -> STATE_DOWNLOADING
                        else -> Download.STATE_STOPPED
                    }
                }
            )
        }

        Icon.Download(downloadState)
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        val skin = LocalAuraItemSkin.current
        Text(
            text = playlist.playlist.name,
            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
            color = if (skin.enabled) skin.ink else Color.Unspecified,
            fontWeight = if (skin.enabled) null else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee().fillMaxWidth()
        )
    },
    subtitle = {
        val skin = LocalAuraItemSkin.current
        val subtitle = if (autoPlaylist) {
            ""
        } else {
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.playlist.remoteSongCount,
                    playlist.playlist.remoteSongCount
                )
            } else {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.songCount,
                    playlist.songCount
                )
            }
        }
        Text(
            text = subtitle,
            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    },
    badges = badges,
    thumbnailContent = {
        val width = maxWidth
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            fallbackThumbnails = playlist.songCovers,
            size = width,
            placeHolder = {
                val painter = when (playlist.playlist.name) {
                    stringResource(R.string.liked) -> R.drawable.favorite_border
                    stringResource(R.string.offline) -> R.drawable.offline
                    stringResource(R.string.cached_playlist) -> R.drawable.cached
                    
                    stringResource(R.string.uploaded_playlist) -> R.drawable.backup
                    else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.ic_launcher_nobg
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = painterResource(painter),
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = 0.8f),
                        modifier = Modifier.size(width / 2)
                    )
                }
            },
            shape = RoundedCornerShape(ThumbnailCornerRadius)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shape: Shape = RectangleShape,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    ListItem(
        title = mediaMetadata.title,
        subtitle = if (mediaMetadata.suggestedBy != null) {
            buildAnnotatedString {
                append(mediaMetadata.artists.joinToString { it.name })
                append(" • ")
                append(makeTimeString(mediaMetadata.duration * 1000L))
                append(" • ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(mediaMetadata.suggestedBy)
                }
            }
        } else {
            AnnotatedString(
                joinByBullet(
                    mediaMetadata.artists.joinToString { it.name },
                    makeTimeString(mediaMetadata.duration * 1000L)
                )
            )
        },
        badges = {
            if (mediaMetadata.explicit) Icon.Explicit()
        },
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumIndex = null,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier.size(ListThumbnailSize)
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive,
        shape = shape
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
    // Apple-Music-style artwork tap (e.g. in-place preview) — null keeps the thumbnail non-clickable.
    onThumbnailClick: (() -> Unit)? = null,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by produceState<Song?>(initialValue = null, item.id) {
            if (item is SongItem) value = database.song(item.id).firstOrNull()
        }
        val album by produceState<Album?>(initialValue = null, item.id) {
            if (item is AlbumItem) value = database.album(item.id).firstOrNull()
        }

        if ((item is SongItem && song?.song?.liked == true) ||
            (item is AlbumItem && album?.album?.bookmarkedAt != null)
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()

        if (item is SongItem) {
            val download by LocalDownloadUtil.current.getDownload(item.id).collectAsState(null)
            Icon.Download(download?.state)
        }
    },
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    // 16:9 widens video-song rows (e.g. the search results' Videos items); 1f keeps every existing
    // caller's square thumbnail untouched.
    thumbnailRatio: Float = 1f,
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)

    val content: @Composable () -> Unit = {
        ListItem(
            title = item.title,
            subtitle = when (item) {
                is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
                is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            },
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    thumbnailRatio = thumbnailRatio,
                    shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius),
                    resize = item !is ArtistItem,
                    modifier = Modifier
                        .height(ListThumbnailSize)
                        .width(ListThumbnailSize * thumbnailRatio)
                        .then(
                            if (onThumbnailClick != null) Modifier.clickable(onClick = onThumbnailClick)
                            else Modifier
                        )
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isSelected = isSelected,
            isActive = isActive,
            shape = shape,
            drawHighlight = drawHighlight
        )
    }

    if (item is SongItem && isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = item.copy(thumbnail = item.thumbnail.resize(544,544)).toMediaItem(),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by produceState<Song?>(initialValue = null, item.id) {
            if (item is SongItem) value = database.song(item.id).firstOrNull()
        }
        val album by produceState<Album?>(initialValue = null, item.id) {
            if (item is AlbumItem) value = database.album(item.id).firstOrNull()
        }

        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        
        if (item is SongItem) {
            val download by LocalDownloadUtil.current.getDownload(item.id).collectAsState(null)
            Icon.Download(download?.state)
        }
    },
    thumbnailRatio: Float = if (item is SongItem) 16f / 9 else 1f,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    thumbnailHeightOverride: Dp? = null,
    // When true an album's subtitle is just its year (used on an artist's own albums grid, where the
    // artist name on every card is redundant). Default keeps "artist • year" everywhere else.
    albumSubtitleYearOnly: Boolean = false,
) = GridItem(
    title = {
        val skin = LocalAuraItemSkin.current
        Text(
            text = item.title,
            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
            color = if (skin.enabled) skin.ink else Color.Unspecified,
            fontWeight = if (skin.enabled) null else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.basicMarquee().fillMaxWidth()
        )
    },
    subtitle = {
        val skin = LocalAuraItemSkin.current
        val subtitle = when (item) {
            is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
            is AlbumItem -> if (albumSubtitleYearOnly) item.year?.toString()
                            else joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
            is ArtistItem -> null
            is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem
        val scope = rememberCoroutineScope()

        ItemThumbnail(
            thumbnailUrl = item.thumbnail,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius),
            resize = item !is ArtistItem,
        )

        if (item is SongItem && !isActive) {
            OverlayPlayButton(
                visible = true
            )
        }

        AlbumPlayButton(
            visible = item is AlbumItem && !isActive,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    var albumWithSongs = database.albumWithSongs(item.id).first()
                    if (albumWithSongs?.songs.isNullOrEmpty()) {
                        YouTube.album(item.id).onSuccess { albumPage ->
                            database.transaction { insert(albumPage) }
                            albumWithSongs = database.albumWithSongs(item.id).first()
                        }.onFailure { reportException(it) }
                    }
                    albumWithSongs?.let {
                        withContext(Dispatchers.Main) {
                            playerConnection.playQueue(LocalAlbumRadio(it))
                        }
                    }
                }
            }
        )
    },
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
    thumbnailHeightOverride = thumbnailHeightOverride,
    modifier = modifier
)

@Composable
fun LocalSongsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = true,
            playButtonVisible = false
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalArtistsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = CircleShape,
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = false
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun LocalAlbumsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = false,
            playButtonVisible = true
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    thumbnailRatio: Float = 1f,
    // Artist AVATARS are yt3.ggpht.com "…=s…" URLs; resize() truncates that token at "-s" and produces a
    // 404 (blank circle) — album/song lh3 covers are fine. Pass resize=false for artists to render raw
    // (like the working Onboarding "pick artists" screen). Default true keeps album/song behavior.
    resize: Boolean = true
) {
    // "Recortar las portadas" (default OFF) keeps deciding Crop vs Fit on BOTH skins — the redesign only
    // changes the FRAME the cover sits in, never whether the image is cropped inside it.
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    // Render: `border-radius:8px` on the artwork (×1.4 = 11 dp) instead of the classic 6 dp. Artist
    // avatars stay circular. Resolved once and used for every clip below; off the new skin it IS `shape`.
    val frameShape = LocalAuraItemSkin.current.thumbnailShape(shape)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(thumbnailRatio)
            .clip(frameShape)
    ) {
        if (albumIndex == null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(if (resize) thumbnailUrl?.resize(544, 544) else thumbnailUrl)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(frameShape)
            )
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .clip(frameShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null
                )
            }
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (albumIndex != null)
                        Color.Transparent
                    else
                        Color.Black.copy(alpha = ActiveBoxAlpha),
                    shape = frameShape
                )
        )
    }
}

@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f
) {
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    // Same 11 dp artwork radius as `ItemThumbnail`; identity off the new skin.
    val frameShape = LocalAuraItemSkin.current.thumbnailShape(shape)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(thumbnailRatio)
            .clip(frameShape)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailUrl)
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Same gate as ItemThumbnail: PlayingIndicatorBox adds the STATE_READY/casting check, so the
        // infinite bar animation never runs while the track is merely BUFFERING (heat/battery rule) —
        // the previous inline copy animated on isPlaying alone.
        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f), frameShape)
        )

        if (showCenterPlay) {
            AnimatedVisibility(
                visible = !(isActive && isPlaying),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }

        if (playButtonVisible) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
    cacheKey: String? = null,
    fallbackThumbnails: List<String> = emptyList()
) {
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    // Same 11 dp artwork radius as `ItemThumbnail`; identity off the new skin.
    val frameShape = LocalAuraItemSkin.current.thumbnailShape(shape)

    // A single non-null thumbnailUrl can still be DEAD (purged content:// custom cover, rotted
    // Spotify/YT mosaic link). Playlist.thumbnails can't know that — only coil finds out at load
    // time — so on primary failure fall back to the song-cover mosaic instead of painting the
    // error logo. Keyed on the URL list: a genuinely new cover retries the primary.
    var primaryFailed by remember(thumbnails) { mutableStateOf(false) }
    val effectiveThumbnails =
        if (thumbnails.size == 1 && primaryFailed) fallbackThumbnails else thumbnails

    when (effectiveThumbnails.size) {
        0 -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(frameShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            placeHolder()
        }
        1 -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(effectiveThumbnails[0].resize(544, 544))
                .apply {  }
                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
            placeholder = painterResource(R.drawable.ic_launcher_nobg),
            error = painterResource(R.drawable.ic_launcher_nobg),
            onError = { primaryFailed = true },
            modifier = Modifier
                .size(size)
                .clip(frameShape)
        )
        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(frameShape)
        ) {
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd
            ).fastForEachIndexed { index, alignment ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        // Modulo, not getOrNull: with 2-3 covers the spare quadrants repeat real
                        // covers instead of painting the error logo (data=null → error painter).
                        .data(effectiveThumbnails[index % effectiveThumbnails.size].resize(544, 544))
                        .apply {  }
                        .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                    placeholder = painterResource(R.drawable.ic_launcher_nobg),
                    error = painterResource(R.drawable.ic_launcher_nobg),
                    modifier = Modifier
                        .align(alignment)
                        .size(size / 2)
                )
            }
        }
    }
}

@Composable
fun BoxScope.OverlayPlayButton(
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.Center)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BoxScope.OverlayEditButton(
    visible: Boolean,
    onClick: () -> Unit,
    alignment: Alignment = Alignment.Center,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(alignment)
            .then(if (alignment == Alignment.BottomEnd) Modifier.padding(8.dp) else Modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                .padding(0.dp)
                .clickable(onClick = onClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                .clickable(onClick = onClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
fun SwipeToSongBox(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val offset = remember { mutableFloatStateOf(0f) }
    val threshold = 300f

    val dragState = rememberDraggableState { delta ->
        offset.floatValue = (offset.floatValue + delta).coerceIn(-threshold, threshold)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                state = dragState,
                onDragStopped = {
                    when {
                        offset.floatValue >= threshold -> {
                            player?.playNext(listOf(mediaItem))
                            Toast.makeText(ctx, R.string.play_next, Toast.LENGTH_SHORT).show()
                            reset(offset, scope)
                        }

                        offset.floatValue <= -threshold -> {
                            player?.addToQueue(listOf(mediaItem))
                            Toast.makeText(ctx, R.string.add_to_queue, Toast.LENGTH_SHORT).show()
                            reset(offset, scope)
                        }

                        else -> reset(offset, scope)
                    }
                }
            )
    ) {
        if (offset.floatValue != 0f) {
            val (iconRes, bg, tint, align) = if (offset.floatValue > 0)
                Quadruple(
                    R.drawable.playlist_play,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.onSecondary,
                    Alignment.CenterStart
                ) else
                Quadruple(
                    R.drawable.ic_launcher_nobg,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                    Alignment.CenterEnd
                )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.Center)
                    .background(bg),
                contentAlignment = align
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .size(30.dp)
                        .alpha(0.9f),
                    tint = tint
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            content = content
        )
    }
}


private fun reset(offset: MutableState<Float>, scope: CoroutineScope) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300)
        ) { value, _ -> offset.value = value }
    }
}


data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * The per-row markers. Called from this file's default `badges` lambdas AND from the custom ones the
 * 27 screens pass in, which is why they are restyled here rather than at each call site: one edit and
 * every screen's badges follow the render.
 *
 * Each one reads [LocalAuraItemSkin], published by the enclosing [ListItem] / [GridItem], so the skin
 * costs no extra preference read per glyph. Composed outside a row the local is [ClassicItemSkin] and
 * the classic glyph is drawn — which is exactly what a menu or a dialog should keep showing.
 */
object Icon {
    @Composable
    fun Favorite() {
        val skin = LocalAuraItemSkin.current
        if (skin.enabled) {
            // Render: `#i-heart-f` filled, in the accent — not the Material error red.
            Icon(
                imageVector = AuraIcons.HeartFilled,
                contentDescription = null,
                tint = skin.accent,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    }

    @Composable
    fun Library() {
        val skin = LocalAuraItemSkin.current
        if (skin.enabled) {
            Icon(
                imageVector = AuraIcons.Library,
                contentDescription = null,
                tint = skin.inkMuted,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.library_add_check),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    }

    @Composable
    fun Download(state: Int?) {
        val skin = LocalAuraItemSkin.current
        when (state) {
            // Render, Biblioteca: "Marca de verificación = descargada".
            STATE_COMPLETED -> if (skin.enabled) {
                Icon(
                    imageVector = AuraIcons.Check,
                    contentDescription = null,
                    tint = skin.accent,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 2.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 2.dp)
                )
            }
            STATE_QUEUED, STATE_DOWNLOADING -> if (skin.enabled) {
                CircularProgressIndicator(
                    color = skin.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 2.dp)
                )
            } else {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 2.dp)
                )
            }
            else -> {  }
        }
    }

    @Composable
    fun Explicit() {
        val skin = LocalAuraItemSkin.current
        if (skin.enabled) {
            Icon(
                painter = painterResource(R.drawable.explicit),
                contentDescription = null,
                tint = skin.inkMuted,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.explicit),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    }
}

@Composable
fun rememberQobuzMatch(
    id: String,
    artist: String,
    title: String,
    durationMs: Long?,
    audioQuality: iad1tya.echo.music.constants.AudioQuality,
    cachedFlac: Boolean
): androidx.compose.runtime.State<Boolean?> {
    return androidx.compose.runtime.produceState<Boolean?>(initialValue = if (cachedFlac) true else null, id) {
        if (cachedFlac) {
            value = true
            return@produceState
        }
        kotlinx.coroutines.delay(300) // Debounce fast scrolling
        val qobuzClient = iad1tya.echo.music.utils.qobuz.QobuzApiClient()
        var found = false
        for (term in iad1tya.echo.music.utils.qobuzSearchTerms(artist, title)) {
            val searchResult = runCatching { qobuzClient.search(term) }.getOrNull() ?: continue
            val candidates = searchResult.tracks?.items.orEmpty()
            if (candidates.isEmpty()) continue
            val scored = candidates.map { it to iad1tya.echo.music.utils.confidence(artist, title, durationMs, it) }
            val match = scored.filter { it.second >= 0.5f }.maxByOrNull { it.second }
            if (match != null) {
                found = true
                break
            }
        }
        value = found
    }
}


