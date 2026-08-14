package iad1tya.echo.music.ui.newui

import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.CropAlbumArtKey
import iad1tya.echo.music.constants.SwipeToSongKey
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.ui.component.PlayingIndicator
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.ui.utils.tvFocusRestorer
import iad1tya.echo.music.utils.rememberPreference
import java.util.Locale
import kotlin.math.abs
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
            .padding(start = AuraSpacing.Gutter, end = AuraSpacing.Gutter, top = 8.dp),
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
    accent: Color = AuraPalette.OnGround,
    onClick: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        // The clickable region now covers the WHOLE row (title + chevron), matching the classic
        // `NavigationTitle`'s `.clickable` on its own outer Row. It used to sit only on the title
        // Column below, leaving the chevron a purely decorative sibling with no click handler of its
        // own — "ver todos" worked when tapping the text but silently did nothing on the arrow next
        // to it. `onPlayAll`'s own AuraIconButton still consumes its own taps first (innermost
        // clickable wins in Compose), so it is unaffected by this.
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
                        .clip(AuraShapes.Highlight)
                        .auraClickableInternal(onClick = onClick, contentDescription = title)
                } else {
                    Modifier
                },
            )
            .padding(start = AuraSpacing.Gutter, end = 6.dp, top = AuraSpacing.SectionTop),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            // Apple Music / YTM: large readable section title (not mono uppercase tracking).
            Text(
                text = title,
                style = AuraType.ContentSection,
                color = accent,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
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
                size = 18.dp,
                tint = AuraPalette.OnGroundMuted,
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
    seed: String? = thumbnailUrl ?: "aura-cover",
    shape: Shape = AuraShapes.Artwork,
    decodeTo: Int = 256,
    ratio: Float = 1f,
    /**
     * When true, the image always fills the clipped frame (YTM/Apple exploration cards).
     * When false, honour "Recortar las portadas" — Fit leaves gradient bars (horrible on video posters).
     */
    fillBleed: Boolean = false,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val effectiveUrl = thumbnailUrl?.takeIf { it.isNotBlank() }
    val effectiveSeed = seed?.takeIf { it.isNotBlank() } ?: effectiveUrl ?: "aura-cover"
    val brush = remember(effectiveSeed) { AuraPalette.coverPlaceholder(effectiveSeed) }
    // Exploration cards ([fillBleed], videos, non-square frames) must Crop — Fit + sddefault 4:3
    // inside a 16:9 poster is exactly the purple side bars in the owner's screenshots.
    // Player / queue / hero keep [CropAlbumArtKey] when fillBleed is false and ratio is 1:1.
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val contentScale = when {
        fillBleed || ratio != 1f -> ContentScale.Crop
        cropAlbumArt -> ContentScale.Crop
        else -> ContentScale.Fit
    }
    Box(
        modifier = modifier
            .width(size)
            .aspectRatio(ratio)
            .clip(shape)
            .background(brush)
            .border(0.5.dp, AuraPalette.ArtworkEdge, shape),
    ) {
        AuraStableCoverImage(
            url = effectiveUrl,
            contentScale = contentScale,
            decodeTo = decodeTo,
            seed = effectiveSeed,
            modifier = Modifier.fillMaxSize(),
        )
        overlay?.invoke(this)
    }
}

/**
 * Cover swap without the empty-plate flash: keep the last decoded bitmap painted underneath while the
 * next URL loads, and never ask Coil for a 250 ms crossfade (that fade is what the owner reads as
 * "parpadeo al cambiar de canción").
 *
 * YouTube thumbnails upgrade maxresdefault → sddefault → hqdefault (same chain as [Thumbnail.kt]).
 */
@Composable
fun AuraStableCoverImage(
    url: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    decodeTo: Int? = 256,
    /** When this changes (track id), drop the previous bitmap so the mini never keeps the last song. */
    seed: String? = null,
) {
    val context = LocalContext.current
    val identity = seed?.takeIf { it.isNotBlank() } ?: url
    var paintedUrl by remember(identity) { mutableStateOf(url?.let(::upgradeAuraYoutubeCover)) }
    var loadUrl by remember(identity) { mutableStateOf(url?.let(::upgradeAuraYoutubeCover)) }
    LaunchedEffect(url) {
        loadUrl = url?.let(::upgradeAuraYoutubeCover)
        if (url == null) paintedUrl = null
    }
    fun requestData(raw: String) =
        if (decodeTo != null) raw.resize(decodeTo, decodeTo) else raw

    Box(modifier) {
        paintedUrl?.let { old ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(requestData(old))
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (loadUrl != null && loadUrl != paintedUrl) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(requestData(loadUrl!!))
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                onSuccess = { paintedUrl = loadUrl },
                onError = {
                    loadUrl = loadUrl?.let(::downgradeAuraYoutubeCover)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (url != null && paintedUrl == null && loadUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(requestData(loadUrl!!))
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                onSuccess = { paintedUrl = loadUrl },
                onError = {
                    loadUrl = loadUrl?.let(::downgradeAuraYoutubeCover)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Upgrade ytimg URLs to maxresdefault for sharp exploration/player covers. */
internal fun upgradeAuraYoutubeCover(raw: String): String {
    if (!raw.contains("ytimg.com") && !raw.contains("img.youtube.com")) return raw
    return raw.replace(
        Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.(jpg|webp)"),
        "maxresdefault.$2",
    )
}

internal fun downgradeAuraYoutubeCover(url: String): String? = when {
    url.contains("maxresdefault") -> url.replace("maxresdefault", "sddefault")
    url.contains("sddefault") -> url.replace("sddefault", "hqdefault")
    else -> null
}

/**
 * "This is sounding" marker — same behaviour as classic [PlayingIndicatorBox]: animated teal bars
 * while audio is actually ready/playing; a play glyph while the track is still buffering so the
 * indicator does not lie about silence. Only one active row composes this at a time.
 */
@Composable
fun AuraPlayingBars(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current
    val playbackState by (
        playerConnection?.playbackState?.collectAsState()
            ?: remember { mutableStateOf(Player.STATE_READY) }
        )
    val isCasting by (
        playerConnection?.service?.castConnectionHandler?.isCasting?.collectAsState()
            ?: remember { mutableStateOf(false) }
        )
    val animateBars = isPlaying && (playbackState == Player.STATE_READY || isCasting)
    Box(
        modifier = modifier.height(17.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (animateBars) {
            PlayingIndicator(
                color = AuraPalette.Teal,
                modifier = Modifier.height(17.dp),
                barWidth = 3.dp,
                cornerRadius = 1.dp,
            )
        } else {
            Icon(
                imageVector = AuraIcons.Play,
                contentDescription = null,
                tint = AuraPalette.Teal,
                modifier = Modifier.size(14.dp),
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
 * Apple-style download chrome for song rows: filling ring while downloading, tray+arrow when done.
 * Never a checkmark — that glyph means "already played" elsewhere in Aura.
 */
@Composable
fun AuraDownloadTick(
    songId: String,
    modifier: Modifier = Modifier,
) {
    val downloadUtil = LocalDownloadUtil.current
    val download by downloadUtil.getDownload(songId).collectAsState(initial = null)
    val liveMap by downloadUtil.liveProgress.collectAsState()
    val progressFraction = liveMap[songId]?.takeIf { it >= 0f }?.let { (it / 100f).coerceIn(0f, 1f) }
    when (download?.state) {
        Download.STATE_COMPLETED -> {
            AuraIconGlyph(
                icon = AuraIcons.Download,
                contentDescription = stringResource(R.string.filter_downloaded),
                modifier = modifier,
                size = 16.dp,
                tint = AuraPalette.Teal,
            )
        }
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
            Box(
                modifier = modifier.size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (progressFraction != null) {
                    CircularProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 2.dp,
                        color = AuraPalette.Teal,
                        trackColor = AuraPalette.OnGround.copy(alpha = 0.18f),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 2.dp,
                        color = AuraPalette.Teal,
                        trackColor = AuraPalette.OnGround.copy(alpha = 0.18f),
                    )
                }
            }
        }
        else -> Unit
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
    fillBleed: Boolean = true,
    decodeTo: Int = 512,
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
            seed = seed ?: title,
            shape = shape,
            decodeTo = decodeTo,
            ratio = ratio,
            fillBleed = fillBleed,
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
        Spacer(Modifier.height(10.dp))
        // Fixed 2-line title + 1-line subtitle so every shelf card shares the same baseline
        // (short titles used to collapse the stack and make double-row grids look uneven).
        val density = LocalDensity.current
        // +2.dp slack: a Box height equal to lineHeight alone clips glyph bottoms (album years).
        val titleBlockHeight = with(density) { (AuraType.CoverTitle.lineHeight * 2).toDp() } + 2.dp
        val subtitleBlockHeight = with(density) { AuraType.RowSubtitle.lineHeight.toDp() } + 2.dp
        Text(
            text = title,
            style = AuraType.CoverTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            modifier = Modifier
                .fillMaxWidth()
                .height(titleBlockHeight),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(subtitleBlockHeight),
        ) {
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
}

/**
 * Full-width grid poster: cover fills the cell, title + subtitle sit ON the art over a bottom
 * gradient (Singles / EPs / vídeos / directos / listas "ver todos"). Shelf [AuraCoverCard] keeps
 * titles underneath — this one is for dense section grids where under-title text was getting
 * clipped or looking empty.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraPosterCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnailUrl: String? = null,
    seed: String? = thumbnailUrl,
    ratio: Float = 1f,
    shape: Shape = AuraShapes.Artwork,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    typeIcon: ImageVector? = null,
    typeLabel: String? = null,
    contentDescription: String = title,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val brush = remember(seed) { AuraPalette.coverPlaceholder(seed) }
    // Posters always fill-bleed — never Fit letterboxes (owner: "portadas que no cubren todo").
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(brush)
            .border(0.5.dp, AuraPalette.ArtworkEdge, shape)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onClickLabel = contentDescription,
                        onLongClick = onLongClick,
                    )
                } else Modifier,
            ),
    ) {
        AuraStableCoverImage(
            url = thumbnailUrl,
            contentScale = ContentScale.Crop,
            decodeTo = if (ratio != 1f) null else 512,
            seed = seed,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.58f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, AuraPalette.Ground.copy(alpha = 0.92f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = AuraType.CoverTitle,
                color = AuraPalette.OnGround,
                maxLines = 2,
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
        if (typeIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(AuraPalette.Ground.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                AuraIconGlyph(
                    icon = typeIcon,
                    contentDescription = typeLabel,
                    size = 12.dp,
                    tint = AuraPalette.Teal,
                )
            }
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
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
    /** Dim the row without the "ya reproducida" check (e.g. hidden explicit). */
    dimContent: Boolean = false,
    swipeMediaItem: MediaItem? = null,
    /** Apple/YTM typed thumb: circle artists, 16:9 videos, softer playlist cards. */
    artworkShape: Shape = AuraShapes.Artwork,
    artworkRatio: Float = 1f,
    artworkSize: Dp = 50.dp,
    /** Small type chip on the cover (Vídeo / EP / Playlist…); null = none. */
    typeChip: String? = null,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    menuContentDescription: String = title,
    contentDescription: String? = title,
) {
    val playedCheck = playedInShuffle && !isActive
    val dimmed = dimContent || playedCheck

    val row: @Composable () -> Unit = {
        AuraRow(
            title = title,
            subtitle = subtitle,
            highlighted = isActive,
            dimmed = dimmed,
            contentDescription = contentDescription,
            leading = leading,
            onClick = onClick,
            onLongClick = onLongClick,
            artwork = {
                AuraCover(
                    thumbnailUrl = thumbnailUrl,
                    size = artworkSize,
                    seed = seed ?: title,
                    shape = artworkShape,
                    ratio = artworkRatio,
                    fillBleed = true,
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AuraPalette.Ground.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) { AuraPlayingBars(isPlaying = isPlaying) }
                    }
                    if (!typeChip.isNullOrBlank() && !isActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(3.dp)
                                .clip(AuraShapes.Pill)
                                .background(AuraPalette.Ground.copy(alpha = 0.78f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = typeChip,
                                style = AuraType.QualityBadge,
                                color = AuraPalette.Teal,
                                maxLines = 1,
                            )
                        }
                    }
                }
            },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (playedCheck) {
                        AuraIconGlyph(
                            icon = AuraIcons.Check,
                            contentDescription = "Ya reproducida",
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
                    if (selected != null) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = onSelectedChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = AuraPalette.Teal,
                                uncheckedColor = AuraPalette.OnGroundDisabled,
                                checkmarkColor = AuraPalette.OnAccent,
                            ),
                        )
                    } else if (onMenuClick != null) {
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

/** Apple Music song pages: 4 full rows, swipe L–R, next page peeks so the user knows to scroll. */
internal const val AuraSongPageSize = 4
private val AuraSongPagePeek = 64.dp
private val AuraSongPageGap = 12.dp
private val AuraSongRowSlot = 66.dp
private val AuraSongPageHeight = AuraSongRowSlot * AuraSongPageSize
private val AuraSongDividerInset = 50.dp + AuraSpacing.RowInner

@Composable
fun AuraSongPages(
    itemCount: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int) -> Unit,
) {
    if (itemCount <= 0) return
    val pageCount = (itemCount + AuraSongPageSize - 1) / AuraSongPageSize
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val peek = if (pageCount > 1) AuraSongPagePeek else AuraSpacing.Gutter
        val pageWidth = maxWidth - AuraSpacing.Gutter - peek
        val (listState, fling) = rememberAuraShelfFlingBehavior()
        LazyRow(
            state = listState,
            flingBehavior = fling,
            userScrollEnabled = pageCount > 1,
            contentPadding = PaddingValues(
                start = AuraSpacing.Gutter,
                end = AuraSpacing.Gutter,
            ),
            horizontalArrangement = Arrangement.spacedBy(AuraSongPageGap),
            modifier = Modifier
                .fillMaxWidth()
                .height(AuraSongPageHeight + AuraSpacing.SectionGap)
                .padding(top = AuraSpacing.SectionGap)
                .tvFocusRestorer(),
        ) {
            items(
                count = pageCount,
                key = { page -> "aura_song_page_$page" },
            ) { page ->
                val start = page * AuraSongPageSize
                val end = minOf(start + AuraSongPageSize, itemCount)
                Column(
                    modifier = Modifier
                        .width(pageWidth)
                        .height(AuraSongPageHeight),
                ) {
                    for (i in start until end) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AuraSongRowSlot),
                        ) {
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                itemContent(i)
                            }
                            if (i < end - 1) {
                                AuraDivider(Modifier.padding(start = AuraSongDividerInset))
                            }
                        }
                    }
                }
            }
        }
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
        // BOTH action backgrounds are composed, always, and the draw phase decides which one is
        // visible. The previous `if (offset.floatValue != 0f)` read the drag offset in COMPOSITION, so
        // this whole composable — [content] included — recomposed on every frame of the drag and on
        // every frame of the return animation. Neither box below carries a gesture modifier, so
        // neither can consume a pointer: the drag stays on the parent and the content box, drawn last,
        // sits on top of them.
        AuraSwipeActionBackground(offset = offset, threshold = threshold, goingRight = true)
        AuraSwipeActionBackground(offset = offset, threshold = threshold, goingRight = false)

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .fillMaxWidth(),
            content = content,
        )
    }
}

/**
 * One of the two swipe backgrounds. Its opacity is a function of the live drag offset, evaluated
 * inside `graphicsLayer` — the draw phase — so it never recomposes and never invalidates layout. A
 * fully transparent layer is not drawn at all, which is the state of both boxes at rest.
 */
@Composable
private fun BoxScope.AuraSwipeActionBackground(
    offset: MutableFloatState,
    threshold: Float,
    goingRight: Boolean,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                val value = offset.floatValue
                val matches = if (goingRight) value > 0f else value < 0f
                alpha = if (matches) (abs(value) / (threshold * 0.5f)).coerceIn(0f, 1f) else 0f
            }
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

/**
 * Returns the row to rest. A spring from [AuraMotion], not a `tween` — the redesign speaks one motion
 * dialect and a linear/eased 300 ms return was the only curve left outside it.
 */
private fun auraResetSwipe(offset: MutableState<Float>, scope: CoroutineScope) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = AuraMotion.float,
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
