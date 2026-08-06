package iad1tya.echo.music.ui.newui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.CropAlbumArtKey
import iad1tya.echo.music.constants.EnableLyricsThumbnailPlayPauseKey
import iad1tya.echo.music.constants.LyricsLineSpacingKey
import iad1tya.echo.music.constants.LyricsTextPositionKey
import iad1tya.echo.music.constants.LyricsTextSizeKey
import iad1tya.echo.music.extensions.togglePlayPause
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.models.rememberResolvedAlbum
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.LyricsMenu
import iad1tya.echo.music.ui.player.HideStatusBarOnFullscreenEffect
import iad1tya.echo.music.ui.player.InlineLyricsView
import iad1tya.echo.music.ui.player.rememberSwipeLyricsEnabled
import iad1tya.echo.music.ui.player.swipeLyricsGestureArmed
import iad1tya.echo.music.ui.player.swipeLyricsToChangeSong
import iad1tya.echo.music.ui.screens.settings.LyricsPosition
import iad1tya.echo.music.ui.utils.ShowOffsetDialog
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import java.util.Locale
import kotlin.math.roundToInt

/**
 * # Letra a pantalla completa — "Interfaz nueva"
 *
 * The redesigned container for the FULL-SCREEN lyrics (the surface the classic player reaches with
 * `isFullScreen = true`, `ui/player/Player.kt:1646/1776` — **not** the queue's inline lyrics tab).
 *
 * ## What this file is, and what it deliberately is not
 * It is a CONTAINER. Not one line of lyrics is laid out, timed, romanised, translated, selected or
 * shared here: the body is [InlineLyricsView] → `ui/component/Lyrics.kt`, exactly the composable the
 * classic player and the queue tab render. That file is under repair by other agents, so wrapping it
 * (instead of re-deriving its behaviour) is what keeps both UIs on one implementation — every fix
 * lands in both, and a lyrics regression can never be "only in the new UI".
 *
 * Everything the delegated view already owns, and which therefore survives untouched here:
 *  · **Tocar una línea → salta a ese momento** (`LyricsClickKey`, `Lyrics.kt:1274`), with the
 *    animated re-centring, the auto-scroll suspension and the "Volver a sincronizar" button.
 *  · **Selección de líneas (long-press) → compartir como TEXTO o como IMAGEN**, with the colour
 *    customiser (`Lyrics.kt:2193`/`:2377`) and the 5-line selection limit.
 *  · **Traducción** banners (traduciendo / error / traducidas) and the "¿Traducir la letra?" prompt.
 *  · **Romanización**, the per-provider "Lyrics from …" line, the loading shimmer, the
 *    "Letras no encontradas" state and the per-frame-cost gates (Performance Mode / thermal).
 *
 * ## The controls this container owns
 * | Control | Classic origin |
 * |---|---|
 * | Cerrar la letra | `Player.kt:1646` (the fullscreen toggle, in reverse) |
 * | Miniatura 56 dp → reproducir/pausar | `Player.kt:1581`, gated on `EnableLyricsThumbnailPlayPause` |
 * | Menú de la letra (⋯) | `Player.kt:1732` → [LyricsMenu] (editar / cargar de nuevo / **buscar y adjuntar** / traducción IA / desfase / romanizar) |
 * | Título → álbum, artista → artista, pulsación larga → copiar | `Player.kt:1888`/`:1919` |
 * | Deslizar sobre título/artista → canción anterior/siguiente | `SwipeLyricsKey`, `PlayerAppearancePrefs.kt:346` |
 * | Ocultar la barra de estado | `HideStatusBarOnFullscreenKey`, same effect the classic calls |
 *
 * ## The two controls that were NOT reachable from the classic full-screen lyrics
 * Both are real, both write the SAME preference keys the settings screens write, and both were named
 * in the order for this screen — so they are added here rather than left as a trip to Ajustes:
 *  · **Tamaño / interlineado / alineación del texto** — `LyricsTextSizeKey`, `LyricsLineSpacingKey`,
 *    `LyricsTextPositionKey`, i.e. the very keys `Lyrics.kt:283-284` and `:243` read. Committed on
 *    release (`onValueChangeFinished`), never per drag frame: a DataStore write per frame is exactly
 *    the kind of thing the heat/battery gate forbids.
 *  · **Prioridad de proveedores de letras** — the picker itself is `DraggableLyricsProviderList`
 *    inside Ajustes ▸ Contenido, and a second copy of a drag-to-reorder list would be a second
 *    implementation. This is a NAVIGATION entry to that one list. The per-song provider choice stays
 *    where it lives: the ⋯ menu's "Buscar", whose results are labelled by provider.
 *
 * @param onClose leave the full-screen lyrics. The host decides what that means (collapse back to the
 *   player's inline lyrics, or `navigateUp`).
 * @param positionProvider mirrors [InlineLyricsView]: returning `null` lets the lyrics follow the LIVE
 *   player position on their own 8 ms ticker (smooth word-by-word). Only feed a value while scrubbing
 *   or casting — a throttled 500 ms ticker here makes the highlight jump in steps.
 * @param showTransport draw the compact previous / play-pause / next row at the foot. Pass `false`
 *   when the host already shows a transport under this surface, so there are never two.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraLyricsScreen(
    navController: NavController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    positionProvider: () -> Long? = { null },
    showTransport: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val isTvOrCar = rememberIsTvOrCar()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRole = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)?.value
        ?: RoomRole.NONE
    val isListenTogetherGuest = listenTogetherRole == RoomRole.GUEST

    val enableLyricsThumbnailPlayPause by rememberPreference(EnableLyricsThumbnailPlayPauseKey, false)
    val swipeLyrics = rememberSwipeLyricsEnabled()
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)

    // Same effect the classic player calls, so "Ocultar la barra de estado a pantalla completa"
    // (HideStatusBarOnFullscreenKey) keeps working here instead of being ignored.
    HideStatusBarOnFullscreenEffect(isFullScreen = true)

    var showAppearance by rememberSaveable { mutableStateOf(false) }

    // Back closes the appearance panel first; only then does it leave the lyrics. Lyrics.kt installs
    // its own higher-priority BackHandler while lines are selected, so selection is still what back
    // cancels first of all.
    BackHandler {
        if (showAppearance) showAppearance = false else onClose()
    }

    val bloom = rememberAuraBloom(mediaMetadata?.id)

    Box(
        modifier = modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.55f),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Cabecera ──────────────────────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .padding(horizontal = 8.dp),
            ) {
                AuraIconButton(
                    icon = AuraIcons.ChevronDown,
                    contentDescription = "Cerrar la letra",
                    onClick = onClose,
                    size = 22.dp,
                    tint = AuraPalette.OnGround.copy(alpha = 0.6f),
                )
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AuraSectionLabel(text = stringResource(R.string.lyrics).uppercase(Locale.ROOT))
                    // The provider is REAL data or nothing: an empty/"Unknown" row prints no line at
                    // all rather than a placeholder name.
                    val provider = currentLyrics
                        ?.takeIf { it.id == mediaMetadata?.id }
                        ?.provider
                        ?.takeIf { it.isNotBlank() && it != "Unknown" }
                    if (provider != null) {
                        AuraTechnicalText(text = provider, color = AuraPalette.OnGroundGhost)
                    }
                }
                AuraIconButton(
                    icon = AuraIcons.Settings,
                    contentDescription = stringResource(R.string.aura_lyrics_appearance),
                    onClick = { showAppearance = !showAppearance },
                    size = 20.dp,
                    tint = if (showAppearance) AuraPalette.Teal else AuraPalette.OnGround.copy(alpha = 0.6f),
                )
                AuraIconButton(
                    icon = AuraIcons.More,
                    contentDescription = "Menú de la letra",
                    onClick = {
                        val meta = mediaMetadata ?: return@AuraIconButton
                        menuState.show {
                            LyricsMenu(
                                lyricsProvider = { currentLyrics },
                                songProvider = { currentSong?.song },
                                mediaMetadataProvider = { meta },
                                onDismiss = menuState::dismiss,
                                onShowOffsetDialog = {
                                    bottomSheetPageState.show {
                                        ShowOffsetDialog(songProvider = { currentSong?.song })
                                    }
                                },
                            )
                        }
                    },
                    size = 22.dp,
                    tint = AuraPalette.OnGround.copy(alpha = 0.6f),
                )
            }

            // ── Cuerpo: la vista de letra COMPARTIDA ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart,
            ) {
                val meta = mediaMetadata
                if (meta == null) {
                    AuraEmpty(
                        text = stringResource(R.string.aura_no_song_playing),
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(horizontal = AuraSpacing.Gutter)) {
                        InlineLyricsView(
                            mediaMetadata = meta,
                            showLyrics = true,
                            positionProvider = positionProvider,
                        )
                    }
                    if (enableLyricsThumbnailPlayPause) {
                        // The 56 dp cover that doubles as play/pause while the lyrics are full screen
                        // (Player.kt:1581) — same preference gate, same single action.
                        AuraArtwork(
                            size = 56.dp,
                            placeholderSeed = meta.id,
                            modifier = Modifier
                                .padding(AuraSpacing.Gutter)
                                .pointerInput(Unit) {
                                    detectTapGestures { playerConnection.togglePlayPause() }
                                },
                        ) {
                            AsyncImage(
                                model = meta.thumbnailUrl,
                                contentDescription = null,
                                // "Recortar las portadas" (CropAlbumArtKey), as every classic
                                // renderer does.
                                contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // ── Título y artista (con el gesto de deslizar) ───────────────────────────────────────
            val meta = mediaMetadata
            if (meta != null) {
                val resolvedAlbum = rememberResolvedAlbum(
                    songId = meta.id,
                    initial = meta.album,
                    dbAlbumId = null,
                    dbAlbumName = null,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // "Deslizar en la letra para cambiar de canción" (SwipeLyricsKey). The four-term
                        // gate is the shared one; `lyricsFullScreen = true` because this surface IS the
                        // full-screen lyrics.
                        .swipeLyricsToChangeSong(
                            enabled = swipeLyricsGestureArmed(
                                swipeLyricsEnabled = swipeLyrics,
                                lyricsVisible = true,
                                lyricsFullScreen = true,
                                isListenTogetherGuest = isListenTogetherGuest,
                            ),
                            onPrevious = { if (canSkipPrevious) playerConnection.seekToPrevious() },
                            onNext = { if (canSkipNext) playerConnection.seekToNext() },
                        )
                        .padding(horizontal = AuraSpacing.Gutter),
                ) {
                    Text(
                        text = meta.title,
                        style = AuraType.PlayerTitle,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                            .tvFocusable(isTvOrCar, RoundedCornerShape(6.dp))
                            .combinedClickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                role = Role.Button,
                                onClick = {
                                    resolvedAlbum?.let { album ->
                                        navController.navigate("album/${album.id}")
                                        onClose()
                                    }
                                },
                                onLongClick = {
                                    val label = context.getString(R.string.copied_title)
                                    clipboardManager.setPrimaryClip(
                                        ClipData.newPlainText(label, meta.title),
                                    )
                                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                },
                            ),
                    )
                    if (meta.artists.any { it.name.isNotBlank() }) {
                        val artistText = meta.artists.joinToString(", ") { it.name }
                        Text(
                            text = artistText,
                            style = AuraType.PlayerArtist,
                            color = AuraPalette.OnGroundMuted,
                            maxLines = 1,
                            overflow = AuraDefaultOverflow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                .tvFocusable(isTvOrCar, RoundedCornerShape(6.dp))
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    role = Role.Button,
                                    onClick = {
                                        meta.artists.firstOrNull { !it.id.isNullOrBlank() }?.id
                                            ?.let { artistId ->
                                                navController.navigate("artist/$artistId")
                                                onClose()
                                            }
                                    },
                                    onLongClick = {
                                        val label = context.getString(R.string.copied_artist)
                                        clipboardManager.setPrimaryClip(
                                            ClipData.newPlainText(label, artistText),
                                        )
                                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                    },
                                ),
                        )
                    }
                }
            }

            if (showTransport) {
                AuraLyricsTransport(
                    isPlaying = isPlaying,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    onPrevious = { playerConnection.seekToPrevious() },
                    onTogglePlayPause = { playerConnection.togglePlayPause() },
                    onNext = { playerConnection.seekToNext() },
                )
            }

            Spacer(
                Modifier.windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
                ),
            )
        }

        AnimatedVisibility(
            visible = showAppearance,
            enter = fadeIn(AuraMotion.float) +
                slideInVertically(AuraMotion.intOffset) { it },
            exit = fadeOut(AuraMotion.float) +
                slideOutVertically(AuraMotion.intOffset) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AuraLyricsAppearancePanel(
                onOpenProviderPriority = {
                    showAppearance = false
                    navController.navigate("settings/content")
                },
                onDismiss = { showAppearance = false },
            )
        }
    }
}

/**
 * Previous / play-pause / next, calling the SAME `PlayerConnection` methods every other transport in
 * the app calls. Nothing is re-implemented: the disabled states are `canSkipPrevious`/`canSkipNext`,
 * which is what the player already publishes.
 */
@Composable
private fun AuraLyricsTransport(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
    ) {
        AuraIconButton(
            icon = AuraIcons.SkipPrevious,
            contentDescription = stringResource(R.string.previous),
            onClick = onPrevious,
            enabled = canSkipPrevious,
            size = 26.dp,
        )
        AuraPlayButton(
            isPlaying = isPlaying,
            onClick = onTogglePlayPause,
            diameter = 56.dp,
        )
        AuraIconButton(
            icon = AuraIcons.SkipNext,
            contentDescription = stringResource(R.string.next),
            onClick = onNext,
            enabled = canSkipNext,
            size = 26.dp,
        )
    }
}

/**
 * "Aspecto de la letra": the three appearance keys the lyrics renderer actually reads, plus the way in
 * to the one provider-priority list.
 *
 * The sliders hold a LOCAL value while the finger is down and commit on `onValueChangeFinished`, so a
 * drag is a handful of frames of pure state and exactly ONE DataStore write — not one write per frame.
 */
@Composable
private fun AuraLyricsAppearancePanel(
    onOpenProviderPriority: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (textSize, onTextSizeChange) = rememberPreference(LyricsTextSizeKey, 24f)
    val (lineSpacing, onLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, 1.3f)
    val (textPosition, onTextPositionChange) =
        rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.LEFT)

    // Seeded from the stored value and re-seeded whenever it changes from the outside (e.g. Ajustes).
    var pendingTextSize by remember(textSize) { mutableFloatStateOf(textSize) }
    var pendingLineSpacing by remember(lineSpacing) { mutableFloatStateOf(lineSpacing) }

    val sliderColors = SliderDefaults.colors(
        thumbColor = AuraPalette.Teal,
        activeTrackColor = AuraPalette.Teal,
        inactiveTrackColor = AuraPalette.TrackEmpty,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AuraSpacing.Gutter)
            .background(AuraPalette.GroundRaised, AuraShapes.Card)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuraSectionLabel(
                text = stringResource(R.string.aura_lyrics_appearance).uppercase(),
                modifier = Modifier.weight(1f),
            )
            AuraIconButton(
                icon = AuraIcons.ChevronDown,
                contentDescription = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                size = 18.dp,
                tint = AuraPalette.OnGroundFaint,
            )
        }

        // Tamaño del texto — the same 16..36 sp range and 19 steps Ajustes ▸ Apariencia offers.
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.lyrics_text_size),
                style = AuraType.MenuLabel,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier.weight(1f),
            )
            AuraTechnicalText(text = "${pendingTextSize.roundToInt()} sp")
        }
        Slider(
            value = pendingTextSize,
            onValueChange = { pendingTextSize = it },
            onValueChangeFinished = { onTextSizeChange(pendingTextSize) },
            valueRange = 16f..36f,
            steps = 19,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )

        // Interlineado — same 1.0..4.0 range and 59 steps.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.lyrics_line_spacing),
                style = AuraType.MenuLabel,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier.weight(1f),
            )
            AuraTechnicalText(text = "%.1fx".format(Locale.ROOT, pendingLineSpacing))
        }
        Slider(
            value = pendingLineSpacing,
            onValueChange = { pendingLineSpacing = it },
            onValueChangeFinished = { onLineSpacingChange(pendingLineSpacing) },
            valueRange = 1f..4f,
            steps = 59,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.lyrics_text_position),
            style = AuraType.MenuLabel,
            color = AuraPalette.OnGround,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                LyricsPosition.LEFT to R.string.left,
                LyricsPosition.CENTER to R.string.center,
                LyricsPosition.RIGHT to R.string.right,
            ).forEach { (position, labelRes) ->
                AuraChip(
                    text = stringResource(labelRes),
                    selected = textPosition == position,
                    onClick = { onTextPositionChange(position) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        AuraDivider()
        // The provider PICKER itself is `DraggableLyricsProviderList` in Ajustes ▸ Contenido; this is
        // the way in, not a second copy of that list.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(AuraShapes.Highlight)
                .auraClickableInternal(
                    onClick = onOpenProviderPriority,
                    contentDescription = stringResource(R.string.lyrics_provider_priority),
                )
                .padding(vertical = 14.dp),
        ) {
            AuraIconGlyph(
                icon = AuraIcons.Lyrics,
                contentDescription = null,
                size = 20.dp,
                tint = AuraPalette.OnGround.copy(alpha = 0.75f),
            )
            Text(
                text = stringResource(R.string.lyrics_provider_priority),
                style = AuraType.MenuLabel,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
                modifier = Modifier.weight(1f),
            )
            AuraIconGlyph(
                icon = AuraIcons.ChevronRight,
                contentDescription = null,
                size = 16.dp,
                tint = AuraPalette.OnGroundDisabled,
            )
        }
        AuraDivider()

        Text(
            text = stringResource(R.string.reset),
            style = AuraType.Chip,
            color = AuraPalette.Teal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(AuraShapes.Pill)
                .auraClickableInternal(
                    onClick = {
                        pendingTextSize = 24f
                        pendingLineSpacing = 1.3f
                        onTextSizeChange(24f)
                        onLineSpacingChange(1.3f)
                        onTextPositionChange(LyricsPosition.LEFT)
                    },
                    contentDescription = stringResource(R.string.reset),
                )
                .padding(vertical = 12.dp),
        )
    }
}
