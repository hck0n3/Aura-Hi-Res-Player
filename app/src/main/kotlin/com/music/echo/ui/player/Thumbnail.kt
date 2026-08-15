

package iad1tya.echo.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.CropAlbumArtKey
import iad1tya.echo.music.constants.HidePlayerThumbnailKey
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.constants.PlayerBackgroundStyleKey
import iad1tya.echo.music.constants.PlayerHorizontalPadding
import iad1tya.echo.music.constants.RotatingThumbnailKey
import iad1tya.echo.music.constants.SeekExtraSeconds
import iad1tya.echo.music.constants.SwipeThumbnailKey
import iad1tya.echo.music.constants.ThumbnailCornerRadiusKey
import iad1tya.echo.music.constants.UseNewPlayerDesignKey
import iad1tya.echo.music.constants.ThumbnailCornerRadius
import iad1tya.echo.music.listentogether.RoomRole
// The motion grammar of the redesigned interface. Imported by the ONE host-gated animation below; the
// classic paths in this file never reach it.
import iad1tya.echo.music.ui.newui.AuraMotion
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.constants.CanvasThumbnailAnimationKey
import iad1tya.echo.music.canvas.TidalCanvasProvider
import iad1tya.echo.music.canvas.CanvasArtwork
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.ui.utils.rememberIsWideLayout
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import iad1tya.echo.music.applecanvas.AppleMusicCanvasProvider
import iad1tya.echo.music.echomusiccanvas.echomusicCanvasProvider
import iad1tya.echo.music.canvas.AppleMusicArtistBackgroundProvider
import java.util.Locale


@Immutable
data class ThumbnailDimensions(
    val itemWidth: Dp,
    val containerSize: Dp,
    val thumbnailSize: Dp,
    val cornerRadius: Dp
)


@Immutable
data class MediaItemsData(
    val items: List<MediaItem>,
    val currentIndex: Int
)


// `internal`, not private, only so ThumbnailDimensionsTest can pin the two clamps (the #50 width cap
// and the slot-height cap). Nothing outside this file calls it.
@Stable
internal fun calculateThumbnailDimensions(
    containerWidth: Dp,
    containerHeight: Dp = containerWidth,
    horizontalPadding: Dp = PlayerHorizontalPadding,
    cornerRadius: Dp = ThumbnailCornerRadius,
    isLandscape: Boolean = false,
    /**
     * Upper bound for the ARTWORK only (#50 — "todo lo pone gigante" on an unfolded foldable). Portrait sized
     * the cover as the FULL container width minus padding with no ceiling, so a ~700-930dp-wide window grew a
     * ~650-880dp square. [Dp.Unspecified] (the default) keeps the old uncapped behaviour, so ordinary phones
     * are byte-for-byte unchanged; the caller passes a real value only when [rememberIsWideLayout] is true.
     *
     * Only [thumbnailSize] is capped — [itemWidth] stays the full container width, so the horizontal pager's
     * page size and snapping are untouched and the smaller cover simply centers inside its page.
     */
    maxThumbnailSize: Dp = Dp.Unspecified
): ThumbnailDimensions {

    val rawSize = if (isLandscape) {
        minOf(containerWidth, containerHeight) - (horizontalPadding * 2)
    } else {
        containerWidth - (horizontalPadding * 2)
    }
    // `isSpecified`, NOT `!= Dp.Unspecified`: Dp.Unspecified is Dp(Float.NaN), and NaN equality is exactly the
    // kind of thing that silently flips behaviour depending on how the value class generates equals. Compose
    // ships isSpecified/isUnspecified for this reason — use them.
    val effectiveSize = if (maxThumbnailSize.isSpecified) {
        rawSize.coerceAtMost(maxThumbnailSize)
    } else {
        rawSize
    }
    return ThumbnailDimensions(
        itemWidth = containerWidth,
        containerSize = containerWidth,
        thumbnailSize = effectiveSize,
        cornerRadius = cornerRadius * 2
    )
}

/**
 * Largest the player artwork is allowed to get on a wide screen (#50). Roughly a comfortable phone-sized
 * cover; beyond this the square stops being "the album art" and becomes a wall. Deliberately a CAP and not a
 * scale factor — the complaint was that things get too BIG, so nothing here ever makes the cover larger.
 */
private val WIDE_MAX_THUMBNAIL_SIZE = 420.dp

/**
 * Breathing room left around the artwork of an [ThumbnailHost.OPAQUE_DARK] host when its slot is SHORTER
 * than it is wide, so the elevation below has somewhere to fall. Classic hosts never see it.
 */
private val OPAQUE_DARK_COVER_INSET = 8.dp

/** Elevation of the artwork card on an [ThumbnailHost.OPAQUE_DARK] host. */
private val OPAQUE_DARK_COVER_ELEVATION = 16.dp

/**
 * Scale the artwork of an [ThumbnailHost.OPAQUE_DARK] host starts from when the track changes, before it
 * settles back to 1 through [iad1tya.echo.music.ui.newui.AuraMotion.standard]. 3.5 % — a settle, not a pop.
 */
private const val OPAQUE_DARK_COVER_SETTLE_FROM = 0.965f


@Stable
private fun getMediaItems(
    player: Player,
    swipeThumbnail: Boolean
): MediaItemsData {
    val timeline = player.currentTimeline
    val currentIndex = player.currentMediaItemIndex
    val shuffleModeEnabled = player.shuffleModeEnabled
    
    val currentMediaItem = try {
        player.currentMediaItem
    } catch (e: Exception) { null }
    
    val previousMediaItem = if (swipeThumbnail && !timeline.isEmpty) {
        val previousIndex = timeline.getPreviousWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (previousIndex != C.INDEX_UNSET) {
            try { player.getMediaItemAt(previousIndex) } catch (e: Exception) { null }
        } else null
    } else null

    val nextMediaItem = if (swipeThumbnail && !timeline.isEmpty) {
        val nextIndex = timeline.getNextWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (nextIndex != C.INDEX_UNSET) {
            try { player.getMediaItemAt(nextIndex) } catch (e: Exception) { null }
        } else null
    } else null

    val items = listOfNotNull(previousMediaItem, currentMediaItem, nextMediaItem)
    val currentMediaIndex = items.indexOf(currentMediaItem)

    return MediaItemsData(items, currentMediaIndex)
}


// The queue window is a fixed [prev, current, next] slice, so two adjacent slots can hold the SAME song
// (e.g. a song queued twice in a row). Keying LazyHorizontalGrid items by mediaId alone then yields a
// duplicate key and Compose crashes ("Key ... was already used"). Folding the (stable) window index into
// the key guarantees uniqueness while staying stable across recompositions.
@Stable
private fun thumbnailItemKey(index: Int, item: MediaItem): String =
    "${index}_${item.mediaId.ifEmpty { "unknown_${item.hashCode()}" }}"


@Stable
@Composable
private fun getTextColor(playerBackground: PlayerBackgroundStyle): Color {
    return when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.LIVE_MESH, PlayerBackgroundStyle.LIQUID_GLASS -> Color.White
    }
}


object CanvasArtworkPlaybackCache {
    private const val defaultMaxSize = 256
    private val map = LinkedHashMap<String, CanvasArtwork>(defaultMaxSize, 0.75f, true)
    @Volatile private var maxSize = defaultMaxSize

    @Synchronized
    fun get(mediaId: String): CanvasArtwork? {
        if (maxSize <= 0) return null
        return map[mediaId]
    }

    @Synchronized
    fun put(mediaId: String, artwork: CanvasArtwork) {
        val limit = maxSize
        if (limit <= 0) return
        if (mediaId.isBlank()) return
        map[mediaId] = artwork
        while (map.size > limit) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun setMaxSize(value: Int) {
        maxSize = value.coerceAtLeast(0)
        if (maxSize == 0) {
            map.clear()
            return
        }
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }
}

/**
 * What the HOST layout paints behind [Thumbnail]. **The host decides, not this composable.**
 *
 * The cover/canvas block used to be hidden by a rule written here — `playerBackground == APPLE_MUSIC &&
 * !isLandscape` — as if that were a property of the SETTING. It is not: it is a property of the CLASSIC
 * portrait layout, which under Apple Music style draws the cover full-screen as its own background
 * (Player.kt:1255-1332), so a square cover card in front of it would be a cover on a cover. Any other
 * host that does not do that inherited a blank hole.
 *
 *  · [CLASSIC] — the thirteen classic shapes. Byte-for-byte the historical behaviour: the cover hides
 *    itself in portrait under APPLE_MUSIC, and the text colour follows [getTextColor].
 *  · [OPAQUE_DARK] — a host that paints its own opaque dark ground and NEVER draws the artwork
 *    full-screen behind the player (the "Interfaz nueva" portrait player, whose ground is the ambient
 *    bloom over [iad1tya.echo.music.ui.newui.AuraPalette.Ground]). The artwork and the canvas are
 *    therefore always drawn here, at every one of the seven [PlayerBackgroundStyle] values, and the
 *    text is light-on-dark — [PlayerBackgroundStyle.DEFAULT] resolves to `onBackground`, which on a
 *    light theme would be near-black ink on a near-black ground.
 */
enum class ThumbnailHost { CLASSIC, OPAQUE_DARK }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    isPlayerExpanded: () -> Boolean = { true },
    isLandscape: Boolean = false,
    isListenTogetherGuest: Boolean = false,
    /** See [ThumbnailHost]. The default keeps every classic call site unchanged. */
    host: ThumbnailHost = ThumbnailHost.CLASSIC,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    // #50 — cap the cover on big screens ONLY. Width-based (never [rememberIsTvOrCar]): this is a pure layout
    // question, and using the D-pad gate here would light the TV focus ring on a wide phone (registry #12/#26).
    val isWideLayout = rememberIsWideLayout()

    
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val error by playerConnection.error.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    
    
    val swipeThumbnailPref by rememberPreference(SwipeThumbnailKey, true)
    val swipeThumbnail = swipeThumbnailPref && !isListenTogetherGuest
    val hidePlayerThumbnail by rememberPreference(HidePlayerThumbnailKey, false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.GRADIENT
    )
    val thumbnailCornerRadius by rememberPreference(ThumbnailCornerRadiusKey, defaultValue = 3f)
    // "Inspirado en Apple Music" (UseNewPlayerDesignKey == false). In the classic player this flips the
    // whole layout; in the new UI (OPAQUE_DARK host) it must ONLY restyle the cover — never buttons —
    // per owner rule. See [appleMusicCoverStyle] below.
    val useNewPlayerDesign by rememberPreference(UseNewPlayerDesignKey, defaultValue = true)
    val appleMusicCoverStyle = host == ThumbnailHost.OPAQUE_DARK && !useNewPlayerDesign

    // The host owns BOTH of these; see [ThumbnailHost].
    val classicTextColor = getTextColor(playerBackground)
    val textBackgroundColor = when (host) {
        ThumbnailHost.CLASSIC -> classicTextColor
        ThumbnailHost.OPAQUE_DARK -> Color.White
    }
    // "Does the host already paint the artwork full-screen behind me?" — only the CLASSIC portrait
    // layout ever does, and only under Apple Music style.
    val coverDrawnByHost = host == ThumbnailHost.CLASSIC &&
        playerBackground == PlayerBackgroundStyle.APPLE_MUSIC &&
        !isLandscape

    
    val thumbnailLazyGridState = rememberLazyGridState()
    
    
    val mediaItemsData by remember(
        playerConnection.player.currentMediaItemIndex,
        playerConnection.player.shuffleModeEnabled,
        swipeThumbnail,
        mediaMetadata
    ) {
        derivedStateOf {
            getMediaItems(playerConnection.player, swipeThumbnail)
        }
    }
    
    val mediaItems = mediaItemsData.items
    val currentMediaIndex = mediaItemsData.currentIndex

    
    val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
        ThumbnailSnapLayoutInfoProvider(
            lazyGridState = thumbnailLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize / 2f - itemSize / 2f)
            },
            velocityThreshold = 500f
        )
    }

    
    val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
    val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }
    // Swipe/snap already slid the cover in; skip OPAQUE_DARK settle so it doesn't snap-shrink vs distance scale.
    // Also skip animateScrollToItem after a swipe — re-animating the grid onto the same index caused the
    // post-swipe cover flicker the owner reported.
    var suppressCoverSettle by remember { mutableStateOf(false) }

    
    LaunchedEffect(itemScrollOffset) {
        if (!thumbnailLazyGridState.isScrollInProgress || !swipeThumbnail || itemScrollOffset != 0 || currentMediaIndex < 0) return@LaunchedEffect

        if (currentItem > currentMediaIndex && canSkipNext) {
            suppressCoverSettle = true
            playerConnection.seekToNextOrStartRadio()
        } else if (currentItem < currentMediaIndex && canSkipPrevious) {
            suppressCoverSettle = true
            playerConnection.player.seekToPreviousMediaItem()
        }
    }

    
    LaunchedEffect(mediaMetadata, canSkipPrevious, canSkipNext) {
        val index = maxOf(0, currentMediaIndex)
        if (index >= 0 && index < mediaItems.size) {
            try {
                if (suppressCoverSettle) {
                    thumbnailLazyGridState.scrollToItem(index)
                    suppressCoverSettle = false
                } else {
                    thumbnailLazyGridState.animateScrollToItem(index)
                }
            } catch (e: Exception) {
                thumbnailLazyGridState.scrollToItem(index)
                suppressCoverSettle = false
            }
        }
    }

    LaunchedEffect(playerConnection.player.currentMediaItemIndex) {
        val index = mediaItemsData.currentIndex
        if (index >= 0 && index != currentItem) {
            thumbnailLazyGridState.scrollToItem(index)
        }
    }

    
    LaunchedEffect(mediaItems) {
        mediaItems.forEach { item ->
            val artworkUri = item.mediaMetadata.artworkUri?.toString()?.resize(1200, 1200) ?: return@forEach
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            SingletonImageLoader.get(context).enqueue(request)
        }
    }

    
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }

    val videoModeOn by playerConnection.videoMode.collectAsState()
    val videoUrl by playerConnection.videoUrl.collectAsState()

    // NOTE: the Android back gesture must NOT exit video (the user wants it to just minimize the player,
    // keeping video mode on) — so there is intentionally no BackHandler here.

    Box(
        // fillMaxSize is REQUIRED: the only steady-state child below is a matchParentSize Box (the cover +
        // carousel), which by definition does NOT contribute to this Box's own measured size. Without an
        // explicit size, in normal audio playback (video spinner / seek overlay absent) this Box has no
        // size-contributing child and, with minHeight=0 from the parent, collapses to 0×0 — so the
        // LazyHorizontalGrid gets 0 height and the cover/carousel vanish on most devices. All call sites
        // pass a parent with a bounded max height (Box(weight(1f)) portrait / landscape), so fillMaxSize is
        // safe (no unbounded-height risk). Matches upstream, which sizes the cover child directly.
        modifier = modifier.fillMaxSize()
    ) {
        // The video itself is rendered by the player's IMMERSIVE (portrait) / FULLSCREEN (landscape) branch,
        // NEVER here — so this Thumbnail never attaches a second TextureView to the single player (which would
        // fight the immersive surface during the video↔song crossfade). While the stream is still resolving
        // (no URL yet), show a spinner over the still-visible cover; once it's ready the immersive branch
        // takes over (and this Thumbnail isn't composed there at all).
        if (videoModeOn && videoUrl.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .padding(32.dp)
                    .align(Alignment.Center),
            ) {
                error?.let { playbackError ->
                    PlaybackError(
                        error = playbackError,
                        retry = playerConnection.player::prepare,
                    )
                }
            }

        
        // When the HOST paints the cover full-screen as the background (classic portrait + Apple Music
        // style) the square cover card is hidden — the user wants a single full-screen animation, NOT a
        // full-screen one plus a square cover in front. Every other host draws the artwork here, always;
        // see [ThumbnailHost] for why this is the host's call and not this composable's.
        AnimatedVisibility(
            // The cover/canvas stays visible here (the spinner overlays it while resolving). The actual video
            // is shown by the immersive/fullscreen branch, which crossfades in over this cover.
            visible = error == null && !coverDrawnByHost,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                // OPAQUE_DARK hosts lay this block out BELOW their own inset-padded header, so a second
                // status-bar pad would push the artwork down and shrink it for nothing.
                .then(
                    if (!isLandscape && host == ThumbnailHost.CLASSIC) Modifier.statusBarsPadding()
                    else Modifier
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Top
            ) {
                
                // CLASSIC-only. This header is two lines of `titleMedium` plus 16 dp of padding — ~60 dp
                // taken off the top of the artwork slot. The classic portrait player has that height to
                // spare; an OPAQUE_DARK host does not (its slot is a `weight(1f)` box between a title
                // block, a transport row, a quick-access row, an engine bar and the queue bar), so the
                // square below was being clamped into a rectangle by it. The two facts it states —
                // "reproduciendo desde …" and the Listen Together banner — are drawn by that host's own
                // header instead (AuraPlayer.kt), so nothing is lost; only the classic layout keeps them
                // here, byte-for-byte.
                if (!isLandscape && host == ThumbnailHost.CLASSIC) {
                    ThumbnailHeader(
                        queueTitle = queueTitle,
                        albumTitle = mediaMetadata?.album?.title,
                        textColor = textBackgroundColor
                    )
                }
                
                
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = if (isLandscape) {
                        Modifier.weight(1f, false)
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) {
                    
                    val dimensions = remember(
                        maxWidth, maxHeight, isLandscape, thumbnailCornerRadius, isWideLayout, host,
                        appleMusicCoverStyle,
                    ) {
                        calculateThumbnailDimensions(
                            containerWidth = maxWidth,
                            containerHeight = maxHeight,
                            cornerRadius = thumbnailCornerRadius.dp,
                            isLandscape = isLandscape,
                            // The slot's OWN HEIGHT is a cap for every host.
                            //
                            // The PORTRAIT branch of calculateThumbnailDimensions sizes the square off the
                            // WIDTH alone and ignores the height it was given (landscape already does
                            // `minOf(width, height)`). Where the slot is shorter than it is wide,
                            // `Modifier.size(thumbnailSize)` is coerced by the incoming max-height
                            // constraint, so the "square" is measured as a wide RECTANGLE: the artwork
                            // (`ContentScale.Fit` by default) is fitted by height and the leftover
                            // left/right bars are painted with the backdrop plate, with the user's corner
                            // radius rounding the rectangle instead of the cover.
                            //
                            // That is every OPAQUE_DARK host (the new player spends its height on a title
                            // block, a transport, a quick row, an engine bar and the queue bar) — but it is
                            // NOT a new-player-only problem, which is why this is no longer a host
                            // privilege: the CLASSIC portrait slot is short in split-screen / multi-window
                            // portrait, on foldable cover screens, and on any phone once Ajustes ▸ Tamaño
                            // de pantalla / fuente shrinks the dp height (the controls column is fixed dp,
                            // so the slot loses height faster than the cover loses width) — and classic
                            // spends ~60 dp of the slot on ThumbnailHeader, so it clamps SOONER than the
                            // new player would at equal size.
                            //
                            // `calculateThumbnailDimensions` only ever coerces DOWN, so wherever the slot
                            // is tall enough — the normal tall-phone case — the value is byte-for-byte
                            // what it was and the classic player is untouched. `maxHeight` is Dp.Infinity
                            // if a caller is ever unbounded, and `coerceAtMost(Dp.Infinity)` is a no-op.
                            maxThumbnailSize = run {
                                val slotCap = if (host == ThumbnailHost.OPAQUE_DARK && !appleMusicCoverStyle) {
                                    // Card look keeps breathing room for elevation. Apple Music cover
                                    // style in New UI fills the slot (cover-only effect, no button change).
                                    (maxHeight - OPAQUE_DARK_COVER_INSET * 2).coerceAtLeast(0.dp)
                                } else {
                                    maxHeight
                                }
                                // #50 unchanged: on a wide screen the 420 dp ceiling still applies — it is
                                // now simply the SMALLER of the two caps, because a 420 dp square in a
                                // 300 dp-tall slot letterboxes exactly the same way.
                                if (isWideLayout) minOf(WIDE_MAX_THUMBNAIL_SIZE, slotCap) else slotCap
                            }
                        )
                    }

                    
                    val onSeekCallback = remember {
                        { direction: String, showEffect: Boolean ->
                            seekDirection = direction
                            showSeekEffect = showEffect
                        }
                    }
                    
                    
                    val isScrollEnabled by remember(swipeThumbnail) {
                        derivedStateOf { swipeThumbnail && isPlayerExpanded() }
                    }
                    
                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = isScrollEnabled,
                        modifier = if (isLandscape) {
                            Modifier.size(dimensions.thumbnailSize + (PlayerHorizontalPadding * 2))
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ) {
                        itemsIndexed(
                            items = mediaItems,
                            key = { index, item -> thumbnailItemKey(index, item) }
                        ) { index, item ->
                            ThumbnailItem(
                                item = item,
                                itemKey = thumbnailItemKey(index, item),
                                dimensions = dimensions,
                                hidePlayerThumbnail = hidePlayerThumbnail,
                                cropAlbumArt = cropAlbumArt || appleMusicCoverStyle,
                                textBackgroundColor = textBackgroundColor,
                                layoutDirection = layoutDirection,
                                onSeek = onSeekCallback,
                                playerConnection = playerConnection,
                                context = context,
                                lazyGridState = thumbnailLazyGridState,
                                isLandscape = isLandscape,
                                isListenTogetherGuest = isListenTogetherGuest,
                                currentMediaId = mediaMetadata?.id,
                                currentMediaThumbnail = mediaMetadata?.thumbnailUrl,
                                playerBackground = playerBackground,
                                host = host,
                                appleMusicCoverStyle = appleMusicCoverStyle,
                                suppressCoverSettle = suppressCoverSettle,
                                onCoverSettleSuppressed = { suppressCoverSettle = false },
                            )
                        }
                    }
                }
            }
        }

        
        }

        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SeekEffectOverlay(seekDirection = seekDirection)
        }
    }
}


@Composable
private fun ThumbnailHeader(
    queueTitle: String?,
    albumTitle: String?,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp)
        ) {
            
            if (listenTogetherRoleState?.value != RoomRole.NONE) {
                Text(
                    text = if (listenTogetherRoleState?.value == RoomRole.HOST) "Hosting Listen Together" else "Listening Together",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
            } else {
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
            }
            val playingFrom = albumTitle ?: queueTitle 
            androidx.compose.animation.AnimatedContent(
                targetState = playingFrom,
                transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                label = "NowPlayingAnimation"
            ) { text ->
                if (!text.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }

        // CAST: the cast button moved OUT of this header — it is now pinned to the TOP-RIGHT of the whole
        // expanded player (Player.kt, over the sheet content) so it stays visible in EVERY layout (lyrics,
        // Apple-Music canvas, landscape, wide/TV split), not just when this portrait header is composed.
    }
}


/**
 * THE condition under which an animated canvas cover plays. One definition, shared by everything that
 * draws a canvas AND by anything that *claims* one is playing.
 *
 * A label about the canvas must never be able to contradict the canvas: the "CANVAS ▸ EN MOVIMIENTO"
 * badge of the new player used to gate on its own, differently-defaulted copy of
 * [CanvasThumbnailAnimationKey] plus a data-saver term this gate has never had, so the badge could
 * appear with no canvas behind it (and vanish with one playing). Both now call this.
 *
 * Terms, and why:
 *  · [CanvasThumbnailAnimationKey] — the user's switch. Default **false**; it is opt-in.
 *    `rememberPerfGatedBoolean` additionally forces it off in High-Performance Mode.
 *  · `rememberDeviceThrottle()` — anti-overheating: the OS reporting MODERATE+ thermal stops the video.
 *    Always false below API 29 / while cool, so a cool device is byte-identical.
 *  · [RotatingThumbnailKey] — the rotating cover and the canvas are the same surface; the canvas yields.
 */
@Composable
internal fun rememberCanvasAnimationEnabled(): Boolean {
    val canvasPref by iad1tya.echo.music.utils.rememberPerfGatedBoolean(
        CanvasThumbnailAnimationKey,
        defaultValue = false,
    )
    val rotatingThumbnail by iad1tya.echo.music.utils.rememberPerfGatedBoolean(
        RotatingThumbnailKey,
        defaultValue = false,
    )
    val deviceThrottle = iad1tya.echo.music.utils.rememberDeviceThrottle()
    return canvasPref && !deviceThrottle && !rotatingThumbnail
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThumbnailItem(
    item: MediaItem,
    itemKey: Any,
    dimensions: ThumbnailDimensions,
    hidePlayerThumbnail: Boolean,
    cropAlbumArt: Boolean,
    textBackgroundColor: Color,
    layoutDirection: LayoutDirection,
    onSeek: (String, Boolean) -> Unit,
    playerConnection: iad1tya.echo.music.playback.PlayerConnection,
    context: android.content.Context,
    lazyGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    isLandscape: Boolean = false,
    isListenTogetherGuest: Boolean = false,
    currentMediaId: String? = null,
    currentMediaThumbnail: String? = null,
    playerBackground: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
    /** See [ThumbnailHost]. The default keeps every classic call site unchanged. */
    host: ThumbnailHost = ThumbnailHost.CLASSIC,
    /**
     * New UI only: "Inspirado en Apple Music" restyles the cover (fuller, no card chrome) and must
     * not change transport/buttons. Classic hosts ignore this.
     */
    appleMusicCoverStyle: Boolean = false,
    suppressCoverSettle: Boolean = false,
    onCoverSettleSuppressed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rotatingThumbnail by iad1tya.echo.music.utils.rememberPerfGatedBoolean(RotatingThumbnailKey, defaultValue = false)
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isCurrentItem = item.mediaId == currentMediaId
    val isOpaqueDark = host == ThumbnailHost.OPAQUE_DARK
    val useCardChrome = isOpaqueDark && !appleMusicCoverStyle
    
    val infiniteTransition = rememberInfiniteTransition(label = "ThumbnailRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying && rotatingThumbnail && isCurrentItem) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val incrementalSeekSkipEnabled by rememberPreference(SeekExtraSeconds, defaultValue = false)
    var skipMultiplier by remember { mutableIntStateOf(1) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val canvasThumbnailAnimation = rememberCanvasAnimationEnabled()

    // High-Performance Mode: skip the Apple-Music dynamic zoom/fade cover transition entirely and keep the
    // solid/instant path (covers stay full-size, full-alpha). Mirrors the perf-mode "no cover crossfade" rule.
    val highPerfMode by rememberPreference(iad1tya.echo.music.constants.HighPerformanceModeKey, false)

    // ── The settle (OPAQUE_DARK only) ─────────────────────────────────────────────────────────────
    // One spring, once per track change, on ONE element: the cover comes in 3.5 % small and settles. The
    // value is read inside `graphicsLayer` below, i.e. at DRAW time, so it invalidates a render layer and
    // never the composition — no per-frame recomposition, no allocation while it runs. The `Animatable` is
    // remembered for CLASSIC hosts too (composable call order must not depend on the host) but nothing ever
    // starts it there, so it stays at 1f and the classic layer maths is term-for-term what it was.
    val coverSettle = remember(item.mediaId) { Animatable(1f) }
    // Settle snap (0.965→1) on every track change stacked with Coil decode and read as cover flicker.
    // Keep the Animatable for call-order stability; do not animate it.
    LaunchedEffect(item.mediaId, isCurrentItem, isOpaqueDark, highPerfMode, suppressCoverSettle) {
        if (suppressCoverSettle) onCoverSettleSuppressed()
    }

    // The artwork plate. `surfaceVariant` comes from the APP theme, which on a light theme is a pale grey —
    // fine behind the classic player, a bright rectangle behind a cover that is floating on a near-black
    // ground. Same reason `textBackgroundColor` is forced to white for this host.
    val artworkBackdrop = when (host) {
        ThumbnailHost.CLASSIC -> MaterialTheme.colorScheme.surfaceVariant
        ThumbnailHost.OPAQUE_DARK -> Color.White.copy(alpha = 0.06f)
    }

    Box(
        modifier = modifier
            .then(
                if (isLandscape) {
                    Modifier.size(dimensions.thumbnailSize + (PlayerHorizontalPadding * 2))
                } else {
                    Modifier
                        .width(dimensions.itemWidth)
                        .fillMaxSize()
                }
            )
            .padding(horizontal = if (appleMusicCoverStyle) 4.dp else PlayerHorizontalPadding)
            .graphicsLayer {
                // Apple-Music style: the further a cover is from the viewport center, the more it shrinks and
                // fades — so as songs change (grid scrolls the new cover to center) the artwork zooms in +
                // crossfades. Gated OFF in perf mode (highPerfMode) to keep the instant, no-transform path.
                if (!highPerfMode) {
                    val layoutInfo = lazyGridState.layoutInfo
                    val visibleItem = layoutInfo.visibleItemsInfo.find { it.key == itemKey }
                    if (visibleItem != null) {
                        val center = layoutInfo.viewportEndOffset / 2f
                        val itemCenter = visibleItem.offset.x + (visibleItem.size.width / 2f)
                        val distance = kotlin.math.abs(itemCenter - center)
                        val fraction = (distance / visibleItem.size.width.toFloat()).coerceIn(0f, 1f)

                        val targetScale = androidx.compose.ui.util.lerp(1f, 0.85f, fraction)
                        scaleX = targetScale
                        scaleY = targetScale
                        alpha = androidx.compose.ui.util.lerp(1f, 0.3f, fraction)
                    }
                }
                // MULTIPLIES the carousel's own distance scale rather than replacing it, so the settle and
                // the swipe never fight over the same property. Stays at 1f for CLASSIC hosts.
                if (isOpaqueDark) {
                    val settle = coverSettle.value
                    scaleX *= settle
                    scaleY *= settle
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (isListenTogetherGuest) return@detectTapGestures

                        // Center band → play/pause; the left/right halves keep the incremental seek.
                        if (offset.x in (size.width * 0.35f)..(size.width * 0.65f)) {
                            playerConnection.togglePlayPause()
                            return@detectTapGestures
                        }

                        val currentPosition = playerConnection.player.currentPosition
                        val duration = playerConnection.player.duration

                        val now = System.currentTimeMillis()
                        if (incrementalSeekSkipEnabled && now - lastTapTime < 1000) {
                            skipMultiplier++
                        } else {
                            skipMultiplier = 1
                        }
                        lastTapTime = now

                        val skipAmount = 5000 * skipMultiplier

                        val isLeftSide = (layoutDirection == LayoutDirection.Ltr && offset.x < size.width / 2) ||
                                (layoutDirection == LayoutDirection.Rtl && offset.x > size.width / 2)

                        if (isLeftSide) {
                            playerConnection.player.seekTo((currentPosition - skipAmount).coerceAtLeast(0))
                            onSeek(context.getString(R.string.seek_backward_dynamic, skipAmount / 1000), true)
                        } else {
                            playerConnection.player.seekTo((currentPosition + skipAmount).coerceAtMost(duration))
                            onSeek(context.getString(R.string.seek_forward_dynamic, skipAmount / 1000), true)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // One shape for the clip, the elevation and the hairline — three outlines that disagreed would show
        // as a halo offset from the corner. Identical to the expression that used to be inline in `clip`,
        // so the "rotating cover" setting still swaps in its clover for both hosts.
        val coverShape = if (rotatingThumbnail) {
            MaterialShapes.Clover8Leaf.toShape()
        } else {
            RoundedCornerShape(dimensions.cornerRadius)
        }
        Box(
            modifier = Modifier
                .size(dimensions.thumbnailSize)
                .graphicsLayer {
                    rotationZ = rotation
                }
                // OPAQUE_DARK only: the cover reads as a CARD lifted off the ground instead of a picture
                // pasted onto it. TWO parts, because on this ground one of them alone would not carry it:
                //  · the elevation is a real one and is a BLACK shadow, so it only reads where the ambient
                //    bloom lifts the ground above [AuraPalette.Ground] (#060A12) — which is most of the
                //    artwork slot, since the bloom band covers the top ~52 % of the screen, but not all of
                //    it, and not at all under a pure-black setting;
                //  · the hairline is what guarantees the edge EVERYWHERE, including over pure black and
                //    for a cover whose own border is dark. It is the trick dark premium UIs use.
                // `border` wraps `clip`, so it strokes ON TOP of the clipped artwork.
                // The CORNER RADIUS is deliberately NOT touched: it is the user's own setting
                // ("Radio de las esquinas de la portada", presets 0/8/16/24/32/40 → doubled), and
                // overriding it here would make that control dead in the new player.
                .then(
                    if (useCardChrome) {
                        Modifier
                            .shadow(OPAQUE_DARK_COVER_ELEVATION, coverShape, clip = false)
                            .border(1.dp, Color.White.copy(alpha = 0.07f), coverShape)
                    } else Modifier
                )
                .clip(coverShape)
                .graphicsLayer {
                    rotationZ = -rotation
                }
        ) {
            if (hidePlayerThumbnail) {
                HiddenThumbnailPlaceholder(
                    textBackgroundColor = textBackgroundColor,
                    backdrop = artworkBackdrop,
                )
            } else {
                val artworkUriToUse = if (item.mediaId == currentMediaId && !currentMediaThumbnail.isNullOrBlank()) {
                    currentMediaThumbnail
                } else {
                    item.mediaMetadata.artworkUri?.toString()
                }

                ThumbnailImage(
                    artworkUri = artworkUriToUse?.resize(1200, 1200),
                    cropArtwork = cropAlbumArt,
                    backdrop = artworkBackdrop
                )
            }
            
            // The rotating-cover / thermal / perf-mode terms all live in rememberCanvasAnimationEnabled().
            if (canvasThumbnailAnimation && item.mediaId == currentMediaId) {
                var canvasArtwork by remember(item.mediaId) { mutableStateOf<CanvasArtwork?>(null) }
                var canvasFetchInFlight by remember(item.mediaId) { mutableStateOf(false) }
                val storefront = remember {
                    val country = Locale.getDefault().country
                    if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
                }

                LaunchedEffect(item.mediaId) {
                    CanvasArtworkPlaybackCache.get(item.mediaId)?.let { cached ->
                        canvasArtwork = cached
                        return@LaunchedEffect
                    }

                    if (canvasFetchInFlight) return@LaunchedEffect
                    canvasFetchInFlight = true

                    // Overall cap: a slow/hanging canvas provider used to leave canvasFetchInFlight stuck
                    // true forever (had to leave/re-enter to retry). The timeout guarantees it resets.
                    val fetched = withTimeoutOrNull(8000L) { withContext(Dispatchers.IO) {
                        val metadata = item.metadata
                        val albumName = (metadata?.album?.title ?: item.mediaMetadata.albumTitle)?.toString()
                        val duration = metadata?.duration
                        
                        val songTitleRaw = item.mediaMetadata.title?.toString() ?: ""
                        val artistNameRaw = item.mediaMetadata.artist?.toString() ?: ""
                        
                        val songTitle = normalizeCanvasSongTitle(songTitleRaw)
                        val artistName = normalizeCanvasArtistName(artistNameRaw)
                        
                        println("CanvasFetch: Song='$songTitle' (raw='$songTitleRaw'), Artist='$artistName' (raw='$artistNameRaw'), Album='$albumName'")
                        
                        linkedSetOf(
                            songTitle to artistName,
                            songTitleRaw to artistName,
                            songTitle to artistNameRaw,
                            songTitleRaw to artistNameRaw,
                        ).filter { (s, a) -> s.isNotBlank() && a.isNotBlank() }
                            .firstNotNullOfOrNull { (s, a) ->
                                
                                
                                if (!albumName.isNullOrBlank()) {
                                    AppleMusicCanvasProvider.getByAlbumArtist(
                                        album = albumName,
                                        artist = a,
                                        storefront = storefront
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }?.let { return@firstNotNullOfOrNull it }
                                }

                                echomusicCanvasProvider.getBySongArtist(
                                    song = s,
                                    artist = a
                                )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                    ?: TidalCanvasProvider.getBySongArtist(
                                        song = s,
                                        artist = a,
                                        album = albumName
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                    ?: AppleMusicCanvasProvider.getBySongArtist(
                                        song = s,
                                        artist = a,
                                        album = albumName,
                                        storefront = storefront
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                            }
                    } }


                    val requestedArtist = item.mediaMetadata.artist?.toString() ?: ""
                    val requestedTitle = item.mediaMetadata.title?.toString() ?: ""
                    
                    val validated = fetched?.let { artwork ->
                        val resultArtist = artwork.artist
                        val resultName = artwork.name
                        
                        
                        val artistMatches = if (resultArtist != null && requestedArtist.isNotBlank()) {
                            val normalizedResult = normalizeCanvasArtistName(resultArtist)
                            val normalizedRequested = normalizeCanvasArtistName(requestedArtist)
                            resultArtist.contains(requestedArtist, ignoreCase = true) || 
                            requestedArtist.contains(resultArtist, ignoreCase = true) ||
                            normalizedResult.contains(normalizedRequested, ignoreCase = true) ||
                            normalizedRequested.contains(normalizedResult, ignoreCase = true)
                        } else true

                        
                        
                        
                        
                        val requestedAlbum = item.mediaMetadata.albumTitle?.toString() ?: ""
                        val canvasAlbumName = artwork.albumName
                        val canvasSongName = artwork.name

                        val titleMatches = when {
                            
                            canvasAlbumName != null && requestedAlbum.isNotBlank() -> {
                                val normalizedCanvasAlbum = normalizeCanvasSongTitle(canvasAlbumName)
                                val normalizedRequestedAlbum = normalizeCanvasSongTitle(requestedAlbum)
                                canvasAlbumName.contains(requestedAlbum, ignoreCase = true) ||
                                requestedAlbum.contains(canvasAlbumName, ignoreCase = true) ||
                                normalizedCanvasAlbum.contains(normalizedRequestedAlbum, ignoreCase = true) ||
                                normalizedRequestedAlbum.contains(normalizedCanvasAlbum, ignoreCase = true)
                            }
                            
                            canvasSongName != null && requestedTitle.isNotBlank() -> {
                                val normalizedCanvasSong = normalizeCanvasSongTitle(canvasSongName)
                                val normalizedRequestedTitle = normalizeCanvasSongTitle(requestedTitle)
                                val normalizedRequestedAlbum = if (requestedAlbum.isNotBlank()) normalizeCanvasSongTitle(requestedAlbum) else ""
                                canvasSongName.contains(requestedTitle, ignoreCase = true) ||
                                requestedTitle.contains(canvasSongName, ignoreCase = true) ||
                                normalizedCanvasSong.contains(normalizedRequestedTitle, ignoreCase = true) ||
                                normalizedRequestedTitle.contains(normalizedCanvasSong, ignoreCase = true) ||
                                (requestedAlbum.isNotBlank() && (
                                    canvasSongName.contains(requestedAlbum, ignoreCase = true) ||
                                    requestedAlbum.contains(canvasSongName, ignoreCase = true) ||
                                    normalizedCanvasSong.contains(normalizedRequestedAlbum, ignoreCase = true) ||
                                    normalizedRequestedAlbum.contains(normalizedCanvasSong, ignoreCase = true)
                                ))
                            }
                            
                            else -> true
                        }

                        if (artistMatches && titleMatches) {
                            artwork
                        } else {
                            println("CanvasFetch: Validation failed artistMatch=$artistMatches, titleMatches=$titleMatches for '${artwork.name}' by '${artwork.artist}'")
                            null
                        }
                    }
                    
                    // Fallback: when no per-song/album canvas passed validation, use the artist's
                    // motion backdrop (same logic as Player.kt ~747-755) so the player still shows
                    // a moving background. Cached inside AppleMusicArtistBackgroundProvider (24 h).
                    val finalArtwork = validated ?: run {
                        val a = requestedArtist
                        if (a.isNotBlank()) {
                            val artistUrl = runCatching {
                                withContext(Dispatchers.IO) {
                                    AppleMusicArtistBackgroundProvider.getByArtistName(a, storefront)
                                }
                            }.getOrNull()
                            if (!artistUrl.isNullOrBlank()) {
                                CanvasArtwork(artist = a, animated = artistUrl, videoUrl = artistUrl)
                            } else null
                        } else null
                    }

                    canvasArtwork = finalArtwork
                    if (finalArtwork != null) {
                        CanvasArtworkPlaybackCache.put(item.mediaId, finalArtwork)
                    }
                    canvasFetchInFlight = false
                }

                canvasArtwork?.let { artwork ->
                    CanvasArtworkPlayer(
                        primaryUrl = artwork.animated,
                        fallbackUrl = artwork.videoUrl,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}


@Composable
private fun HiddenThumbnailPlaceholder(
    textBackgroundColor: Color,
    /** The plate behind the glyph. The caller resolves it from the host; see [ThumbnailHost]. */
    backdrop: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backdrop),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_nobg),
            contentDescription = stringResource(R.string.hide_player_thumbnail),
            tint = textBackgroundColor.copy(alpha = 0.7f),
            modifier = Modifier.size(120.dp)
        )
    }
}


@Composable
private fun ThumbnailImage(
    artworkUri: String?,
    cropArtwork: Boolean,
    /**
     * What shows around a cover that is not square (with "Recortar las portadas" off, the default, the
     * image is `Fit`, so this plate is visible). The caller resolves it from the host — see [ThumbnailHost];
     * `MaterialTheme.colorScheme.surfaceVariant`, which used to be hard-coded here, is an APP-theme colour
     * and comes out pale grey behind the always-dark new player.
     */
    backdrop: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backdrop)
    ) {
        fun upgradeYoutube(raw: String): String {
            // For YouTube VIDEO thumbnails the passed url is sddefault (640×480) — pixelated as a big player
            // cover. Start from hqdefault (480x360, guaranteed to exist) instead of maxresdefault which can 
            // sometimes return a 120x90 gray placeholder instead of 404ing.
            return if (raw.contains("ytimg.com") || raw.contains("img.youtube.com")) {
                raw.replace(
                    Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.(jpg|webp)"),
                    "hqdefault.$2",
                )
            } else raw
        }

        var currentUrl by remember(artworkUri) {
            mutableStateOf(artworkUri?.let(::upgradeYoutube))
        }

        if (currentUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentUrl)
                    .size(coil3.size.Size.ORIGINAL)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit,
                error = painterResource(R.drawable.ic_launcher_nobg),
                fallback = painterResource(R.drawable.ic_launcher_nobg),
                onError = {
                    val url = currentUrl
                    if (url != null) {
                        currentUrl = when {
                            url.contains("maxresdefault") -> url.replace("maxresdefault", "sddefault")
                            url.contains("sddefault") -> url.replace("sddefault", "hqdefault")
                            url.contains("hqdefault") -> url.replace("hqdefault", "mqdefault")
                            url.contains("mqdefault") -> url.replace("mqdefault", "default")
                            else -> null
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_launcher_nobg),
                contentDescription = null,
                contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}


@Composable
private fun SeekEffectOverlay(
    seekDirection: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = seekDirection,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    )
}

internal fun normalizeCanvasSongTitle(raw: String): String {
    val stripped =
        raw
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(
                Regex(
                    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(
                Regex(
                    "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(
                Regex(
                    "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("\\s+"), " ")
            .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun normalizeCanvasArtistName(raw: String): String {
    val first =
        raw
            .split(
                Regex(
                    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                    RegexOption.IGNORE_CASE,
                ),
                limit = 2,
            ).firstOrNull().orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}
