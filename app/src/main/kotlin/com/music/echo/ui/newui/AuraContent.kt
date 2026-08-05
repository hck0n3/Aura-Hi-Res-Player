package iad1tya.echo.music.ui.newui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.CropAlbumArtKey
import iad1tya.echo.music.constants.SwipeToSongKey
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.utils.rememberPreference
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * # Content primitives shared by Inicio and Biblioteca
 *
 * [AuraPrimitives] owns the generic shapes (rows, chips, switches, icons). This file owns the pieces
 * that carry MUSIC data: cover cards, song rows, the per-track quality badge, the download tick and
 * the swipe actions.
 *
 * Everything here is presentation. Every action is a lambda supplied by the screen, and every screen
 * hands over the SAME lambda the classic screen uses (`playerConnection.playQueue(...)`,
 * `navController.navigate(...)`, `menuState.show { SongMenu(...) }`). No queue is built here, no
 * database is written here, no preference is written here.
 */

// ── Headers ───────────────────────────────────────────────────────────────────────────────────────

/**
 * The screen header of the render: a tracked mono label over a big title, plus an optional trailing
 * action ("Biblioteca" has a search glyph at its right).
 *
 * ```
 * BUENAS NOCHES
 * Inicio
 * ```
 */
@Composable
fun AuraScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter, end = AuraSpacing.Gutter, top = 20.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (label != null) {
                AuraSectionLabel(label)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = title,
                style = AuraType.ScreenTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
        }
        trailing?.invoke()
    }
}

/**
 * A content-section header: the render's tracked mono rule ("RECOMENDADO PARA TI · IA") plus the two
 * affordances the classic `NavigationTitle` carries and the render does not draw — "Reproducir todo"
 * and "open this section".
 *
 * [title] is passed through verbatim from the SAME `stringResource` the classic Home uses and is only
 * re-cased for the tracked-label look ([Locale.ROOT], so Spanish accents are preserved and no
 * locale-sensitive mapping — Turkish dotless i — can corrupt it). The words themselves are never
 * retyped here.
 */
@Composable
fun AuraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    accent: Color = AuraPalette.OnGroundFaint,
    onClick: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter, end = 6.dp, top = AuraSpacing.SectionTop),
    ) {
        leading?.invoke()
        Column(
            modifier = if (onClick != null) {
                Modifier
                    .weight(1f)
                    .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
                    .clip(AuraShapes.Highlight)
                    .auraClickableInternal(onClick = onClick, contentDescription = title)
                    .padding(vertical = 4.dp)
            } else {
                Modifier.weight(1f)
            },
            verticalArrangement = Arrangement.Center,
        ) {
            AuraSectionLabel(
                text = title.uppercase(Locale.ROOT),
                color = accent,
            )
            if (label != null) {
                Text(
                    text = label,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
        if (onClick != null) {
            AuraIconGlyph(
                icon = AuraIcons.ChevronRight,
                contentDescription = null,
                size = 16.dp,
                tint = AuraPalette.OnGroundDisabled,
            )
        }
        if (onPlayAll != null) {
            AuraIconButton(
                icon = AuraIcons.Play,
                contentDescription = stringResource(R.string.play_all),
                onClick = onPlayAll,
                size = 17.dp,
                tint = AuraPalette.Teal,
            )
        }
    }
}

// ── Artwork ───────────────────────────────────────────────────────────────────────────────────────

/**
 * Real cover art on top of the deterministic gradient placeholder, so a slow/missing image never
 * leaves a hole — the render's `.cv` gradient IS the loading state.
 *
 * @param decodeTo pixel size the thumbnail is decoded at. Small rows decode small: a list of 40
 *   full-resolution covers is the classic way to heat a phone, and the thermal gate forbids it.
 * @param ratio width ÷ height of the drawn frame. The classic renderers are aspect-ratio aware
 *   (`ItemThumbnail`/`LocalThumbnail` take a `thumbnailRatio` and apply `Modifier.aspectRatio`), so
 *   this one is too: [size] is the WIDTH and the height follows. Every current caller is 1:1 because
 *   every classic counterpart is — `HomeScreen.kt:985` overrides `YouTubeGridItem`'s 16:9 default back
 *   to `thumbnailRatio = 1f`, and playlists/albums/artists are square everywhere — but the parameter
 *   is what lets a non-square caller be non-square instead of silently losing 44 % of its frame.
 */
@Composable
fun AuraCover(
    thumbnailUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    seed: String? = thumbnailUrl,
    shape: Shape = AuraShapes.Artwork,
    decodeTo: Int = 256,
    ratio: Float = 1f,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val brush = remember(seed) { AuraPalette.coverPlaceholder(seed) }
    // "Recortar las portadas" (CropAlbumArtKey, default OFF). EVERY classic renderer reads it —
    // Items.kt:1371/1453/1546, Thumbnail.kt:341, MiniPlayer.kt:857 — and the first cut of the new UI
    // hard-coded ContentScale.Crop, so a wide cover lost its edges no matter what the user had chosen.
    // That is the "las portadas salen recortadas" of the beta verdict. OFF = Fit: the whole frame is
    // shown and the gradient placeholder fills whatever the image does not cover, which is exactly the
    // render's `.cv` behaviour.
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    Box(
        modifier = modifier
            .width(size)
            .aspectRatio(ratio)
            .clip(shape)
            .background(brush),
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl.resize(decodeTo, decodeTo),
                contentDescription = null,
                contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        overlay?.invoke(this)
    }
}

/** The three teal bars of the render's "SONANDO" row — the app's "this is sounding" marker. */
@Composable
fun AuraPlayingBars(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.height(17.dp),
    ) {
        listOf(0.6f, 1f, 0.45f).forEach { fraction ->
            Spacer(
                Modifier
                    .width(3.dp)
                    .height(17.dp * fraction)
                    .background(AuraPalette.Teal),
            )
        }
    }
}

// ── Badges ────────────────────────────────────────────────────────────────────────────────────────

/**
 * The per-track quality badge of the render ("24/96" in teal for hi-res, "16/44" dimmed otherwise).
 *
 * **Honest data only.** The render's left number is a BIT DEPTH, and bit depth is stored nowhere in
 * this app: [FormatEntity] carries `codecs`, `bitrate`, `mimeType` and `sampleRate`, and the local
 * scanner writes `codecs = ""`, `bitrate = 0`, `sampleRate = null`. Printing "24" would be a made-up
 * number — exactly the placebo the removal pass deleted. So the badge keeps the render's shape
 * (`left/right`, mono, teal when hi-res) and fills it with what is actually known:
 *  · left  — `FLAC` when the codec is lossless, otherwise the real kbps.
 *  · right — the real sample rate in kHz.
 *  · teal  — lossless AND ≥ 88.2 kHz, i.e. genuinely hi-res. Everything else is dimmed.
 *
 * A track with no format row yet (never played, never downloaded) shows nothing rather than a guess.
 */
@Composable
fun AuraQualityBadge(
    format: FormatEntity?,
    modifier: Modifier = Modifier,
) {
    if (format == null) return
    val lossless = format.codecs.contains("flac", ignoreCase = true) ||
        format.mimeType.contains("flac", ignoreCase = true)
    val kHz = format.sampleRate?.takeIf { it > 0 }?.let { it / 1000 }
    val left = when {
        lossless -> "FLAC"
        format.bitrate > 0 -> (format.bitrate / 1000).toString()
        else -> null
    }
    val text = when {
        left != null && kHz != null -> "$left/$kHz"
        left != null -> left
        kHz != null -> "$kHz kHz"
        else -> return
    }
    val hiRes = lossless && (format.sampleRate ?: 0) >= 88_200
    AuraTechnicalText(
        text = text,
        modifier = modifier,
        color = if (hiRes) AuraPalette.Teal else AuraPalette.OnGroundGhost,
        style = AuraType.QualityBadge,
    )
}

/**
 * The render's teal tick = "descargada". Reads the SAME `DownloadUtil` flow the classic badge reads,
 * so a download that completes updates both UIs from one source.
 */
@Composable
fun AuraDownloadTick(
    songId: String,
    modifier: Modifier = Modifier,
) {
    val download by LocalDownloadUtil.current.getDownload(songId).collectAsState(initial = null)
    if (download?.state == Download.STATE_COMPLETED) {
        AuraIconGlyph(
            icon = AuraIcons.Check,
            contentDescription = stringResource(R.string.filter_downloaded),
            modifier = modifier,
            size = 16.dp,
            tint = AuraPalette.Teal,
        )
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────────────────────────

/**
 * The horizontal-shelf card of the render's Inicio: a square cover with a title and a subtitle under
 * it ("Cumbias / Mezcla diaria"). Every Home shelf uses this — quick picks, the daily mixes, the
 * time-of-day mix, the AI playlist, new releases, the account playlists, the genre mix, speed dial.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraCoverCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnailUrl: String? = null,
    seed: String? = thumbnailUrl,
    width: Dp = 120.dp,
    /** See [AuraCover.ratio]. 1:1 matches every classic grid item the new shelves replace. */
    ratio: Float = 1f,
    shape: Shape = AuraShapes.Artwork,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    contentDescription: String = title,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(AuraShapes.Highlight)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onClickLabel = contentDescription,
                        onLongClick = onLongClick,
                    )
                } else Modifier,
            )
            .padding(vertical = 4.dp),
    ) {
        AuraCover(
            thumbnailUrl = thumbnailUrl,
            size = width,
            seed = seed,
            shape = shape,
            decodeTo = 512,
            ratio = ratio,
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AuraPalette.Ground.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) {
                        AuraPlayingBars()
                    } else {
                        AuraIconGlyph(
                            icon = AuraIcons.Pause,
                            contentDescription = null,
                            size = 14.dp,
                            tint = AuraPalette.Teal,
                        )
                    }
                }
            }
            badge?.invoke(this)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = AuraType.RowTitle,
            color = AuraPalette.OnGround,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
        }
    }
}

/**
 * The render's Ajustes-style tile reused by Biblioteca for the auto-playlists ("Me gusta",
 * "Descargado", "En caché", "Mi Top N", …): icon + label on a `SurfaceFill` card.
 */
@Composable
fun AuraTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = AuraPalette.Teal,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(onClick = onClick, contentDescription = label)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        AuraIconGlyph(icon = icon, contentDescription = null, size = 19.dp, tint = iconTint)
        Text(
            text = label,
            style = AuraType.Chip,
            color = AuraPalette.OnGround,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Song row ──────────────────────────────────────────────────────────────────────────────────────

/**
 * The list row of the render's Biblioteca and of Inicio's "RECOMENDADO PARA TI · IA":
 * `[cover] título / artista  ·  ♥  ✓  24/96  ⋯`.
 *
 * The trailing cluster is exactly what the render draws (liked heart, download tick, quality badge)
 * plus the overflow `⋯`. The `⋯` is NOT in the render, and it is here on purpose: the inventory lists
 * "⋯ de canción → SongMenu" as a daily control on every library row, and a control that only exists as
 * an undiscoverable long-press is a control the user loses. It is drawn at [AuraPalette.OnGroundDisabled]
 * so it never competes with the artwork.
 *
 * @param playedInShuffle "Aleatorio mejorado" memory: dim the row and show the tick, as today. Never on
 *   the active row — dimming what is currently sounding reads as broken.
 */
@Composable
fun AuraSongRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnailUrl: String? = null,
    seed: String? = thumbnailUrl,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    liked: Boolean = false,
    explicit: Boolean = false,
    inLibrary: Boolean = false,
    downloadId: String? = null,
    format: FormatEntity? = null,
    playedInShuffle: Boolean = false,
    swipeMediaItem: MediaItem? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    menuContentDescription: String = title,
) {
    val dimmed = playedInShuffle && !isActive

    val row: @Composable () -> Unit = {
        AuraRow(
            title = title,
            subtitle = subtitle,
            highlighted = isActive,
            dimmed = dimmed,
            onClick = onClick,
            onLongClick = onLongClick,
            artwork = {
                AuraCover(
                    thumbnailUrl = thumbnailUrl,
                    size = 50.dp,
                    seed = seed,
                ) {
                    if (isActive && isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AuraPalette.Ground.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) { AuraPlayingBars() }
                    }
                }
            },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (dimmed) {
                        AuraIconGlyph(
                            icon = AuraIcons.Check,
                            contentDescription = "Ya reproducida en aleatorio",
                            size = 16.dp,
                            tint = AuraPalette.Teal,
                        )
                    }
                    if (explicit) {
                        AuraTechnicalText(
                            text = "E",
                            color = AuraPalette.OnGroundDisabled,
                            style = AuraType.QualityBadge,
                        )
                    }
                    if (inLibrary) {
                        AuraIconGlyph(
                            icon = AuraIcons.Library,
                            contentDescription = null,
                            size = 15.dp,
                            tint = AuraPalette.OnGroundDisabled,
                        )
                    }
                    if (liked) {
                        AuraIconGlyph(
                            icon = AuraIcons.HeartFilled,
                            contentDescription = null,
                            size = 15.dp,
                            tint = AuraPalette.Teal,
                        )
                    }
                    if (downloadId != null) {
                        AuraDownloadTick(songId = downloadId)
                    }
                    AuraQualityBadge(format = format)
                    if (onMenuClick != null) {
                        AuraIconButton(
                            icon = AuraIcons.More,
                            contentDescription = menuContentDescription,
                            onClick = onMenuClick,
                            size = 18.dp,
                            tint = AuraPalette.OnGroundDisabled,
                        )
                    }
                }
            },
        )
    }

    if (swipeMediaItem != null) {
        AuraSwipeSongBox(mediaItem = swipeMediaItem, modifier = modifier) { row() }
    } else {
        Box(modifier.fillMaxWidth()) { row() }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────────────────────────

/** The new UI's empty placeholder. Text only — the classic `EmptyPlaceholder` has no action either. */
@Composable
fun AuraEmpty(
    text: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 48.dp),
    ) {
        Text(
            text = text,
            style = AuraType.RowTitle,
            color = AuraPalette.OnGroundMuted,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = AuraDefaultOverflow,
        )
        if (secondary != null) {
            Text(
                text = secondary,
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundGhost,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = AuraDefaultOverflow,
            )
        }
    }
}

// ── Swipe actions ─────────────────────────────────────────────────────────────────────────────────

/**
 * Swipe right = "Reproducir a continuación", swipe left = "Añadir a la cola".
 *
 * **Preference-gated, exactly as today.** The classic rows wrap themselves in `SwipeToSongBox` only
 * when [SwipeToSongKey] is on (default OFF, toggled from Ajustes › Apariencia); this wrapper reads the
 * SAME key and is inert when it is off. The threshold (300 px), the two actions
 * (`playerConnection.playNext` / `addToQueue`) and the two toasts (`R.string.play_next` /
 * `R.string.add_to_queue`) are the classic ones — only the drawn background is re-skinned, because
 * `SwipeToSongBox` paints `MaterialTheme.colorScheme.surface` behind the row, which would put an
 * opaque Material rectangle on top of the ambient bloom.
 */
@Composable
fun AuraSwipeSongBox(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = false)
    if (!enabled || !swipeEnabled) {
        Box(modifier = modifier.fillMaxWidth(), content = content)
        return
    }

    val context = LocalContext.current
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
                            android.widget.Toast
                                .makeText(context, R.string.play_next, android.widget.Toast.LENGTH_SHORT)
                                .show()
                            auraResetSwipe(offset, scope)
                        }

                        offset.floatValue <= -threshold -> {
                            player?.addToQueue(listOf(mediaItem))
                            android.widget.Toast
                                .makeText(context, R.string.add_to_queue, android.widget.Toast.LENGTH_SHORT)
                                .show()
                            auraResetSwipe(offset, scope)
                        }

                        else -> auraResetSwipe(offset, scope)
                    }
                },
            ),
    ) {
        if (offset.floatValue != 0f) {
            val goingRight = offset.floatValue > 0
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(AuraShapes.Highlight)
                    .background(if (goingRight) AuraPalette.NowPlayingFill else AuraPalette.BetaFill),
                contentAlignment = if (goingRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                AuraIconGlyph(
                    icon = if (goingRight) AuraIcons.Queue else AuraIcons.Plus,
                    contentDescription = null,
                    size = 22.dp,
                    tint = if (goingRight) AuraPalette.Teal else AuraPalette.Violet,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .alpha(0.9f),
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .fillMaxWidth(),
            content = content,
        )
    }
}

private fun auraResetSwipe(offset: MutableState<Float>, scope: CoroutineScope) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300),
        ) { value, _ -> offset.value = value }
    }
}

// ── Internals ─────────────────────────────────────────────────────────────────────────────────────

/** Mirrors `AuraPrimitives`' private clickable so the content primitives announce themselves too. */
internal fun Modifier.auraClickableInternal(
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String? = null,
): Modifier = this.clickable(
    enabled = enabled,
    onClickLabel = contentDescription,
    role = Role.Button,
    onClick = onClick,
)
