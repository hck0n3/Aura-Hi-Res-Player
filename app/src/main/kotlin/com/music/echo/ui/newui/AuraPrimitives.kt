package iad1tya.echo.music.ui.newui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * Shared row / list / control primitives for the "Interfaz nueva".
 *
 * Every measurement here comes from the reference render, scaled ×1.4 (the render draws phones at
 * 258 px for a 360 dp device). Screen agents compose these — they must NOT re-derive paddings, corner
 * radii or alphas, or the six screens will drift apart.
 *
 * ## Accessibility rule, enforced here
 * The render draws 13–20 px glyphs, i.e. 16–24 dp icons. Android's minimum touch target is 48 dp.
 * Every interactive primitive below therefore separates the DRAWN size from the TOUCHED size:
 * [AuraIconButton] draws at `size` and reserves at least 48×48 dp. Do not hand-roll a
 * `Modifier.clickable` on a bare `Icon`.
 */

// ── Layout constants ──────────────────────────────────────────────────────────────────────────────

object AuraSpacing {
    /** Horizontal screen gutter — render 15px. */
    val Gutter = 21.dp

    /** Vertical gap between rows of a list — render 9–11px. */
    val RowGap = 14.dp

    /** Gap between a row's artwork and its text — render 9px. */
    val RowInner = 13.dp

    /** Space above a section label — Apple-like breathing room before a shelf. */
    val SectionTop = 24.dp

    /** Space between a section label and its first row. */
    val SectionGap = 14.dp

    /** Horizontal gap between shelf cards (Apple/YTM). */
    val ShelfItemGap = 14.dp

    /** Minimum touch target. Non-negotiable. */
    val MinTouchTarget = 48.dp
}

object AuraShapes {
    /**
     * Artwork in a list row — render `border-radius:8px`, i.e. 11 dp scaled.
     *
     * NOT a constant any more: "Radio de esquina de la miniatura" (Ajustes ▸ Apariencia ▸ Reproductor)
     * scales both cover radii, and this getter is the single place the redesign reads that. See
     * [AuraCoverCorners] — the shapes are cut once per settings change, so this hands back an existing
     * object rather than allocating per row.
     */
    val Artwork: RoundedCornerShape get() = AuraPalette.coverCorners.row

    /** Player artwork — render `border-radius:14px`, i.e. 20 dp scaled by the same control. */
    val PlayerArtwork: RoundedCornerShape get() = AuraPalette.coverCorners.player

    /** Cards, the mini-player pill and the "Interfaz nueva" callout — render 11–12px. */
    val Card = RoundedCornerShape(16.dp)

    /** Modal sheets / menus that rise from the bottom — top corners only. */
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /**
     * Corner radius of [Highlight], published because [AuraRow] draws that shape itself (its fill and
     * border are ANIMATED, so they are painted in the draw phase rather than handed to
     * `Modifier.background`/`Modifier.border`). One source, so the drawn corner can never drift from
     * the clipped one.
     */
    val HighlightCorner = 14.dp

    /** The highlighted "SONANDO" row — render 10px. */
    val Highlight = RoundedCornerShape(HighlightCorner)

    /** Filter chips and switches — fully round. */
    val Pill = CircleShape
}

// ── Icons ─────────────────────────────────────────────────────────────────────────────────────────

/**
 * A non-interactive glyph from [AuraIcons]. Use for decoration and for the leading icon of a row that
 * is itself clickable.
 */
@Composable
fun AuraIconGlyph(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = AuraPalette.OnGround,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * An interactive glyph. Draws at [size] but always reserves at least
 * [AuraSpacing.MinTouchTarget] for the finger.
 *
 * @param contentDescription Spanish, and mandatory — the inventory flagged a long list of
 *   icon-only controls with no description; the new UI does not repeat that.
 */
@Composable
fun AuraIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = AuraPalette.OnGround,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .sizeIn(
                minWidth = AuraSpacing.MinTouchTarget,
                minHeight = AuraSpacing.MinTouchTarget,
            )
            .clip(CircleShape)
            .auraClickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                contentDescription = contentDescription,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // announced by the clickable above
            tint = if (enabled) tint else tint.copy(alpha = tint.alpha * 0.35f),
            modifier = Modifier.size(size),
        )
    }
}

/**
 * The gradient play/pause button — **the only full-colour element on a screen**. Render: a 50 px
 * circle filled with `linear-gradient(140deg, teal, blue 55%, violet)`, ink `#061018`, with a blue
 * glow underneath.
 *
 * [fill] and [ink] are parameters, not constants, because "Estilo de los botones del reproductor"
 * (PlayerButtonsStyleKey) repaints this button: DEFAULT keeps the render's gradient, PRIMARY and
 * TERTIARY take the flat theme colours the classic transport uses. The caller derives them through
 * `ui.player.rememberPlayerButtonColors` — never re-derive them here.
 */
@Composable
fun AuraPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 70.dp,
    contentDescription: String = if (isPlaying) "Pausar" else "Reproducir",
    fill: Brush = AuraPalette.PlayButtonGradient,
    ink: Color = AuraPalette.OnAccent,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(fill)
            .auraClickable(onClick = onClick, role = Role.Button, contentDescription = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) AuraIcons.Pause else AuraIcons.Play,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(diameter * 0.48f),
        )
    }
}

// ── Text ──────────────────────────────────────────────────────────────────────────────────────────

/**
 * A tracked, low-opacity, monospace section label: "SONANDO", "A CONTINUACIÓN · DE TU LISTA",
 * "BUENAS NOCHES", "DESPUÉS · RADIO INFINITA".
 *
 * Pass the string ALREADY uppercase and already in Spanish; never `.uppercase()` a localized literal
 * at runtime and never retype a label the classic UI already owns.
 */
@Composable
fun AuraSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AuraPalette.OnGroundFaint,
) {
    Text(
        text = text,
        style = AuraType.SectionLabel,
        color = color,
        maxLines = 1,
        overflow = AuraDefaultOverflow,
        modifier = modifier,
    )
}

/** A group header inside the merged player menu: "REPRODUCCIÓN", "BIBLIOTECA", "IR A", "MÁS". */
@Composable
fun AuraMenuGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = AuraType.MenuGroupLabel,
        color = AuraPalette.OnGroundGhost,
        maxLines = 1,
        overflow = AuraDefaultOverflow,
        modifier = modifier.padding(
            start = AuraSpacing.Gutter,
            end = AuraSpacing.Gutter,
            top = 15.dp,
            bottom = 7.dp,
        ),
    )
}

/** Technical data in monospace at low opacity: "FLAC", "24 BIT", "96 kHz", "−1.0 dBFS", "◆ HI-RES". */
@Composable
fun AuraTechnicalText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AuraPalette.OnGroundFaint,
    style: TextStyle = AuraType.Technical,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = AuraDefaultOverflow,
        modifier = modifier,
    )
}

// ── Rows ──────────────────────────────────────────────────────────────────────────────────────────

/**
 * The universal list row of the new UI: `[leading] [artwork] title / subtitle [trailing]`.
 *
 * Used by Inicio, Biblioteca, Cola and the wide player's queue list. Every slot is optional:
 *  · [leading] — the drag handle in the queue.
 *  · [artwork] — the cover; pass [AuraArtwork] or your own composable (the player/album covers must
 *    be able to host VIDEO, so this is a slot, not a URL).
 *  · [trailing] — quality badge ("24/96"), download tick, playing bars, an [AuraIconButton]…
 *
 * @param onLongClick wired because the inventory records long-press behaviours (copy title/artist,
 *   open the item menu) that no mockup shows. Leave it null only if the classic row truly has none.
 * @param highlighted the "SONANDO" state. It is NOT a hard cut: fill, border and padding all cross a
 *   single [updateTransition], because on every track change the queue hands this flag from one row to
 *   another and an instant 0→11 dp padding jump reflows — and visibly drags — every row below it. The
 *   animated values are read inside `drawBehind` and inside a `Modifier.layout` block, i.e. in the draw
 *   and measure phases: a row in transition repaints and re-measures, it does not recompose, so the
 *   artwork/leading/trailing slots are never re-invoked.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    leading: (@Composable () -> Unit)? = null,
    artwork: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = AuraShapes.Highlight
    val transition = updateTransition(targetState = highlighted, label = "auraRowHighlight")
    // The "off" ends are the SAME hues at alpha 0, never Color.Transparent: interpolating towards a
    // transparent black would drag the fill through a grey it never has.
    val fillColor by transition.animateColor(
        transitionSpec = { AuraMotion.color() },
        label = "auraRowFill",
    ) { on -> if (on) AuraPalette.NowPlayingFill else AuraPalette.NowPlayingFill.copy(alpha = 0f) }
    val lineColor by transition.animateColor(
        transitionSpec = { AuraMotion.color() },
        label = "auraRowLine",
    ) { on -> if (on) AuraPalette.NowPlayingLine else AuraPalette.NowPlayingLine.copy(alpha = 0f) }
    val horizontalPadding by transition.animateDp(
        transitionSpec = { AuraMotion.standard() },
        label = "auraRowPaddingH",
    ) { on -> if (on) 11.dp else 0.dp }
    val verticalPadding by transition.animateDp(
        transitionSpec = { AuraMotion.standard() },
        label = "auraRowPaddingV",
    ) { on -> if (on) 11.dp else 4.dp }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.RowInner),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Fill + hairline, painted here instead of via background()/border() so the two animated
            // colours are read in the DRAW phase. Both are skipped outright while fully transparent,
            // which is the state of every row that is not the current one.
            .drawBehind {
                val radius = CornerRadius(AuraShapes.HighlightCorner.toPx())
                val fill = fillColor
                if (fill.alpha > 0f) {
                    drawRoundRect(color = fill, cornerRadius = radius)
                }
                val line = lineColor
                if (line.alpha > 0f) {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        color = line,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(
                            (AuraShapes.HighlightCorner.toPx() - stroke / 2f).coerceAtLeast(0f),
                        ),
                        style = Stroke(stroke),
                    )
                }
            }
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                        role = Role.Button,
                    )
                } else Modifier
            )
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            // The animated padding, as a measure-phase read. `Modifier.padding(animatedDp)` would read
            // it in composition and recompose the whole row ~18 frames per track change.
            .layout { measurable, constraints ->
                val h = horizontalPadding.roundToPx()
                val v = verticalPadding.roundToPx()
                val placeable = measurable.measure(constraints.offset(-2 * h, -2 * v))
                layout(placeable.width + 2 * h, placeable.height + 2 * v) {
                    placeable.place(h, v)
                }
            },
    ) {
        leading?.invoke()
        artwork?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround.copy(alpha = if (dimmed) 0.6f else 1f),
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted.copy(
                        alpha = AuraPalette.OnGroundMuted.alpha * (if (dimmed) 0.6f else 1f)
                    ),
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * A row of the merged player menu / the Ajustes index: `icon  label  [trailing]`.
 * Render `.mrow`: 17 px icon at opacity .75, 11.5 px label, 8×15 px padding.
 */
@Composable
fun AuraMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = AuraPalette.OnGround.copy(alpha = 0.75f),
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .auraClickable(onClick = onClick, enabled = enabled, role = Role.Button, contentDescription = label)
            .padding(horizontal = AuraSpacing.Gutter, vertical = 11.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) iconTint else iconTint.copy(alpha = iconTint.alpha * 0.4f),
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = AuraType.MenuLabel,
            color = AuraPalette.OnGround.copy(alpha = if (enabled) 1f else 0.4f),
            maxLines = 1,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

// ── Controls ──────────────────────────────────────────────────────────────────────────────────────

/**
 * The render's switch: a 26×15 px pill, teal when on, `rgba(255,255,255,.18)` when off, with an
 * 11 px knob in [AuraPalette.OnAccent] (on) or `#8fa0b8` (off). Scaled ×1.4.
 *
 * The knob SLIDES and both colours cross-fade — it used to teleport, because the knob's position was a
 * `contentAlignment` and the track a bare `if`. This is the most-used control in the new Ajustes, so it
 * is the one place where a hard cut is felt every session. Position on [AuraMotion.standard], colours
 * on [AuraMotion.color], all three read inside `offset {}` / `drawBehind {}`: the switch animates
 * without recomposing.
 */
@Composable
fun AuraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val trackWidth = 36.dp
    val trackHeight = 21.dp
    val knob = 15.dp
    val inset = 3.dp

    val transition = updateTransition(targetState = checked, label = "auraSwitch")
    val knobX by transition.animateDp(
        transitionSpec = { AuraMotion.standard() },
        label = "auraSwitchKnobX",
    ) { on -> if (on) trackWidth - inset - knob else inset }
    val trackColor by transition.animateColor(
        transitionSpec = { AuraMotion.color() },
        label = "auraSwitchTrack",
    ) { on -> if (on) AuraPalette.Teal else Color.White.copy(alpha = 0.18f) }
    val knobColor by transition.animateColor(
        transitionSpec = { AuraMotion.color() },
        label = "auraSwitchKnobColor",
    ) { on -> if (on) AuraPalette.OnAccent else Color(0xFF8FA0B8) }

    Box(
        modifier = modifier
            .sizeIn(minWidth = AuraSpacing.MinTouchTarget, minHeight = AuraSpacing.MinTouchTarget)
            .auraClickable(
                onClick = { onCheckedChange(!checked) },
                enabled = enabled,
                role = Role.Switch,
                contentDescription = contentDescription,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(AuraShapes.Pill)
                // Clipped to the pill by the modifier above, so a plain rect is the whole track.
                .drawBehind { drawRect(trackColor) },
            // CenterStart for the vertical centring only; the horizontal position is [knobX].
            contentAlignment = Alignment.CenterStart,
        ) {
            Spacer(
                Modifier
                    .offset { IntOffset(knobX.roundToPx(), 0) }
                    .size(knob)
                    .clip(CircleShape)
                    .drawBehind { drawRect(knobColor) }
            )
        }
    }
}

/**
 * A library filter chip: teal + dark ink when selected, `rgba(255,255,255,.08)` otherwise.
 *
 * Both ends cross-fade on [AuraMotion.color] instead of snapping. This primitive is also the Cola tab
 * and the Biblioteca filter, so the three of them move together. The fill is read in `drawBehind`; the
 * label colour is a `Text` parameter, so a chip does recompose while its ~200 ms colour transition runs
 * — one `Box` and one `Text`, and only on the chip that was tapped.
 */
@Composable
fun AuraChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = selected, label = "auraChip")
    val fillColor by transition.animateColor(
        transitionSpec = { AuraMotion.color() },
        label = "auraChipFill",
    ) { on -> if (on) AuraPalette.Teal else Color.White.copy(alpha = 0.08f) }
    val inkColor by transition.animateColor(
        transitionSpec = { AuraMotion.color() },
        label = "auraChipInk",
    ) { on -> if (on) AuraPalette.OnAccent else AuraPalette.OnGround }

    Box(
        modifier = modifier
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .padding(vertical = 6.dp)
            .clip(AuraShapes.Pill)
            .drawBehind { drawRect(fillColor) }
            .auraClickable(onClick = onClick, role = Role.Tab, contentDescription = text)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AuraType.Chip,
            color = inkColor,
            maxLines = 1,
        )
    }
}

// NOTE — there is deliberately NO progress-bar primitive here.
// The render draws a 3 px gradient track, but the timeline is a CONTROL, and its shape belongs to
// "Estilo de la barra de progreso" (DEFAULT / ONDULADO / SQUIGGLY / FINO). Every player shape must
// therefore call the shared `ui.component.PlayerProgressSlider`, which is the single read site of
// SliderStyleKey/SquigglySliderKey. A private Aura track here was how that setting became a control
// that changed nothing while the new UI was on; the new player passes AuraPalette colours to the
// shared slider instead. Do not re-add one.

/** A hairline separator: `rgba(255,255,255,.09)`. */
@Composable
fun AuraDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AuraPalette.Divider)
    )
}

/**
 * Artwork placeholder / container. Pass [content] to host real cover art — or a **video surface**:
 * canvas is content, not decoration, so the player's artwork area must be able to hold a video.
 *
 * @param size the WIDTH of the frame. [ratio] (width ÷ height) gives the height, exactly as the classic
 *   renderers do — `Items.ItemThumbnail`/`LocalThumbnail` take a `thumbnailRatio` and apply
 *   `Modifier.aspectRatio`, and `YouTubeGridItem` defaults a SONG card to 16:9. This used to be a hard
 *   `Modifier.size(size)`, so a 16:9 cover handed to it lost ~44 % of its frame to the square. Every
 *   current caller is 1:1 (the classic counterparts are), and 1f reproduces `size(size)` exactly.
 */
@Composable
fun AuraArtwork(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: androidx.compose.ui.graphics.Shape = AuraShapes.Artwork,
    placeholderSeed: String? = null,
    ratio: Float = 1f,
    content: (@Composable () -> Unit)? = null,
) {
    // Read straight, not `remember`ed on the seed: the variants are pre-built inside [AuraAccent], so
    // this is a hash + an index and no allocation — and a `remember` keyed only on the seed would have
    // pinned the placeholder to whatever accent was in force when the row first composed.
    val brush: Brush = AuraPalette.coverPlaceholder(placeholderSeed)
    Box(
        modifier = modifier
            .width(size)
            .aspectRatio(ratio)
            .clip(shape)
            .background(brush),
    ) {
        content?.invoke()
    }
}

// ── Internals ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The one clickable used by every primitive above: the app's default indication, an explicit [Role]
 * and an explicit Spanish [contentDescription] for TalkBack (the inventory flagged a long list of
 * icon-only controls that announce nothing today).
 */
private fun Modifier.auraClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = null,
    contentDescription: String? = null,
): Modifier = this.clickable(
    enabled = enabled,
    onClickLabel = contentDescription,
    role = role,
    onClick = onClick,
)
