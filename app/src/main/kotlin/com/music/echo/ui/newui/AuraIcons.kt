package iad1tya.echo.music.ui.newui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The "Interfaz nueva" icon set — every `<symbol>` of the reference render
 * (`8-DISENO-REAL-con-iconos.html`) transcribed path-for-path into an [ImageVector].
 *
 * These exist because the earlier mockups used Apple SF Symbols, which rendered as empty boxes on the
 * owner's machine. They are drawn by hand, on a 24×24 viewport, with a 1.9 stroke, round caps and round
 * joins — that stroke weight *is* the look. Do not substitute a Material icon for one of these.
 *
 * ## Rules for screen agents
 *  · Names describe WHAT THE ICON DOES, in English. `AuraIcons.Download`, not `AuraIcons.ArrowDown`.
 *  · Render them with [AuraIconButton] / [AuraIconGlyph] so the tint, the 1.9 stroke scaling and the
 *    48 dp minimum touch target come for free.
 *  · Colour comes from the `tint` of the `Icon` composable (a `ColorFilter`, so it recolours strokes
 *    and fills alike). The vectors themselves are black on purpose; never bake a palette colour in.
 *  · Each vector is built lazily and cached — accessing `AuraIcons.Play` a thousand times allocates once.
 */
object AuraIcons {

    // ── Transport ─────────────────────────────────────────────────────────────────────────────────

    /** `#i-play` — filled triangle. Goes inside the gradient play button. */
    val Play: ImageVector by lazy {
        filled("Play") {
            // M8 5.5v13l11-6.5z
            moveTo(8f, 5.5f); verticalLineToRelative(13f); lineToRelative(11f, -6.5f); close()
        }
    }

    /** `#i-pause` — two filled bars. */
    val Pause: ImageVector by lazy {
        filled("Pause") {
            // M8 5h3v14H8z
            moveTo(8f, 5f); horizontalLineToRelative(3f); verticalLineToRelative(14f); horizontalLineTo(8f); close()
            // M13 5h3v14h-3z
            moveTo(13f, 5f); horizontalLineToRelative(3f); verticalLineToRelative(14f); horizontalLineToRelative(-3f); close()
        }
    }

    /** `#i-prev` — bar + left triangle. */
    val SkipPrevious: ImageVector by lazy {
        filled("SkipPrevious") {
            // M7 6h2.2v12H7z
            moveTo(7f, 6f); horizontalLineToRelative(2.2f); verticalLineToRelative(12f); horizontalLineTo(7f); close()
            // M19 6v12l-9-6z
            moveTo(19f, 6f); verticalLineToRelative(12f); lineToRelative(-9f, -6f); close()
        }
    }

    /** `#i-next` — right triangle + bar. */
    val SkipNext: ImageVector by lazy {
        filled("SkipNext") {
            // M14.8 6H17v12h-2.2z
            moveTo(14.8f, 6f); horizontalLineTo(17f); verticalLineToRelative(12f); horizontalLineToRelative(-2.2f); close()
            // M5 6l9 6-9 6z
            moveTo(5f, 6f); lineToRelative(9f, 6f); lineToRelative(-9f, 6f); close()
        }
    }

    /** `#i-shuffle` — crossing arrows. Teal when enhanced shuffle is on. */
    val Shuffle: ImageVector by lazy {
        stroked("Shuffle") {
            // M17 4l3 3-3 3
            moveTo(17f, 4f); lineToRelative(3f, 3f); lineToRelative(-3f, 3f)
            // M17 14l3 3-3 3
            moveTo(17f, 14f); lineToRelative(3f, 3f); lineToRelative(-3f, 3f)
            // M3 7h4l4 5
            moveTo(3f, 7f); horizontalLineToRelative(4f); lineToRelative(4f, 5f)
            // M3 17h4l3-4
            moveTo(3f, 17f); horizontalLineToRelative(4f); lineToRelative(3f, -4f)
            // M14 7h6
            moveTo(14f, 7f); horizontalLineToRelative(6f)
            // M14 17h6
            moveTo(14f, 17f); horizontalLineToRelative(6f)
        }
    }

    /** `#i-repeat` — looping arrows. */
    val Repeat: ImageVector by lazy {
        stroked("Repeat") {
            // M17 2l3 3-3 3
            moveTo(17f, 2f); lineToRelative(3f, 3f); lineToRelative(-3f, 3f)
            // M20 5H8a4 4 0 0 0-4 4v1
            moveTo(20f, 5f); horizontalLineTo(8f)
            arcToRelative(4f, 4f, 0f, false, false, -4f, 4f)
            verticalLineToRelative(1f)
            // M7 22l-3-3 3-3
            moveTo(7f, 22f); lineToRelative(-3f, -3f); lineToRelative(3f, -3f)
            // M4 19h12a4 4 0 0 0 4-4v-1
            moveTo(4f, 19f); horizontalLineToRelative(12f)
            arcToRelative(4f, 4f, 0f, false, false, 4f, -4f)
            verticalLineToRelative(-1f)
        }
    }

    // ── Library actions ───────────────────────────────────────────────────────────────────────────

    /** `#i-heart` — "Me gusta", not yet liked. */
    val Heart: ImageVector by lazy {
        stroked("Heart", cap = StrokeCap.Butt) {
            heartPath()
        }
    }

    /** `#i-heart-f` — "Me gusta", liked. Rendered in [AuraPalette.Teal]. */
    val HeartFilled: ImageVector by lazy {
        filled("HeartFilled") { heartPath() }
    }

    /** `#i-thumbdown` — "Menos de esto" / "No me gusta". */
    val ThumbDown: ImageVector by lazy {
        stroked("ThumbDown", cap = StrokeCap.Butt) {
            // M7 4h8.5l2.5 8H12l1 5.5a2 2 0 0 1-3.7 1L7 12z
            moveTo(7f, 4f); horizontalLineToRelative(8.5f); lineToRelative(2.5f, 8f); horizontalLineTo(12f)
            lineToRelative(1f, 5.5f)
            arcToRelative(2f, 2f, 0f, false, true, -3.7f, 1f)
            lineTo(7f, 12f); close()
            // M4 4h3v8H4z
            moveTo(4f, 4f); horizontalLineToRelative(3f); verticalLineToRelative(8f); horizontalLineTo(4f); close()
        }
    }

    /** `#i-down` — "Descargar" (arrow into a tray). Also the Almacenamiento settings icon. */
    val Download: ImageVector by lazy {
        stroked("Download") {
            // M12 4v11
            moveTo(12f, 4f); verticalLineToRelative(11f)
            // M7.5 11l4.5 4.5L16.5 11
            moveTo(7.5f, 11f); lineTo(12f, 15.5f); lineTo(16.5f, 11f)
            // M5 20h14
            moveTo(5f, 20f); horizontalLineToRelative(14f)
        }
    }

    /** `#i-export` — "Exportar como MP3" (arrow out of a tray). */
    val Export: ImageVector by lazy {
        stroked("Export") {
            // M12 16V5
            moveTo(12f, 16f); verticalLineTo(5f)
            // M8 8.5L12 4.5l4 4
            moveTo(8f, 8.5f); lineTo(12f, 4.5f); lineToRelative(4f, 4f)
            // M5 19h14
            moveTo(5f, 19f); horizontalLineToRelative(14f)
        }
    }

    /** `#i-plus` — "Añadir a la cola". */
    val Plus: ImageVector by lazy {
        stroked("Plus", width = 2f, join = StrokeJoin.Miter) {
            moveTo(12f, 5f); verticalLineToRelative(14f)
            moveTo(5f, 12f); horizontalLineToRelative(14f)
        }
    }

    /** `#i-check` — "descargada" tick in the library. */
    val Check: ImageVector by lazy {
        stroked("Check", width = 2.2f) {
            // M5 12.5l4.5 4.5L19 7
            moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7f)
        }
    }

    /** `#i-list` — "Añadir a lista". */
    val PlaylistAdd: ImageVector by lazy {
        stroked("PlaylistAdd", join = StrokeJoin.Miter) {
            // M4 6h10M4 12h10M4 18h7
            moveTo(4f, 6f); horizontalLineToRelative(10f)
            moveTo(4f, 12f); horizontalLineToRelative(10f)
            moveTo(4f, 18f); horizontalLineToRelative(7f)
            // M18 9v8M14 13h8
            moveTo(18f, 9f); verticalLineToRelative(8f)
            moveTo(14f, 13f); horizontalLineToRelative(8f)
        }
    }

    // ── Player surfaces ───────────────────────────────────────────────────────────────────────────

    /** `#i-queue` — "Cola" / "Reproducir a continuación" (lines + a filled play arrow). */
    val Queue: ImageVector by lazy {
        ImageVector.Builder(
            name = "Queue",
            defaultWidth = ICON_SIZE.dp,
            defaultHeight = ICON_SIZE.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                // M4 7h11M4 12h11M4 17h7
                moveTo(4f, 7f); horizontalLineToRelative(11f)
                moveTo(4f, 12f); horizontalLineToRelative(11f)
                moveTo(4f, 17f); horizontalLineToRelative(7f)
            }
            path(fill = SolidColor(Color.Black)) {
                // M18 12v7l4-3.5z
                moveTo(18f, 12f); verticalLineToRelative(7f); lineToRelative(4f, -3.5f); close()
            }
        }.build()
    }

    /** `#i-lyrics` — "Letra" (three shrinking lines). */
    val Lyrics: ImageVector by lazy {
        stroked("Lyrics", join = StrokeJoin.Miter) {
            // M4 6h16M4 11h12M4 16h9
            moveTo(4f, 6f); horizontalLineToRelative(16f)
            moveTo(4f, 11f); horizontalLineToRelative(12f)
            moveTo(4f, 16f); horizontalLineToRelative(9f)
        }
    }

    /** `#i-vol` — "Volumen" (filled cone + two waves). */
    val Volume: ImageVector by lazy {
        ImageVector.Builder(
            name = "Volume",
            defaultWidth = ICON_SIZE.dp,
            defaultHeight = ICON_SIZE.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // M4 9.5h3.5L12 5.5v13L7.5 14.5H4z
                moveTo(4f, 9.5f); horizontalLineToRelative(3.5f); lineTo(12f, 5.5f)
                verticalLineToRelative(13f); lineTo(7.5f, 14.5f); horizontalLineTo(4f); close()
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // M16 9.2a4 4 0 0 1 0 5.6
                moveTo(16f, 9.2f); arcToRelative(4f, 4f, 0f, false, true, 0f, 5.6f)
                // M18.6 6.6a7.6 7.6 0 0 1 0 10.8
                moveTo(18.6f, 6.6f); arcToRelative(7.6f, 7.6f, 0f, false, true, 0f, 10.8f)
            }
        }.build()
    }

    /** `#i-chev-d` — collapse the player. */
    val ChevronDown: ImageVector by lazy {
        stroked("ChevronDown", width = 2f) {
            moveTo(6f, 9.5f); lineToRelative(6f, 6f); lineToRelative(6f, -6f)
        }
    }

    /** `#i-chev-r` — "go into this settings group". */
    val ChevronRight: ImageVector by lazy {
        stroked("ChevronRight", width = 2f) {
            moveTo(9.5f, 6f); lineToRelative(6f, 6f); lineToRelative(-6f, 6f)
        }
    }

    /** `#i-more` — opens the merged player menu. */
    val More: ImageVector by lazy {
        filled("More") {
            circle(5.5f, 12f, 1.7f)
            circle(12f, 12f, 1.7f)
            circle(18.5f, 12f, 1.7f)
        }
    }

    /** `#i-drag` — the visible drag handle on every queue row. */
    val DragHandle: ImageVector by lazy {
        filled("DragHandle") {
            circle(9f, 6f, 1.5f); circle(15f, 6f, 1.5f)
            circle(9f, 12f, 1.5f); circle(15f, 12f, 1.5f)
            circle(9f, 18f, 1.5f); circle(15f, 18f, 1.5f)
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────────────────────────

    /** `#i-home` — "Inicio". */
    val Home: ImageVector by lazy {
        stroked("Home", cap = StrokeCap.Butt) {
            // M4 10.5L12 4l8 6.5V20h-5.5v-5.5h-5V20H4z
            moveTo(4f, 10.5f); lineTo(12f, 4f); lineToRelative(8f, 6.5f); verticalLineTo(20f)
            horizontalLineToRelative(-5.5f); verticalLineToRelative(-5.5f)
            horizontalLineToRelative(-5f); verticalLineTo(20f); horizontalLineTo(4f); close()
        }
    }

    /** `#i-search` — "Buscar". */
    val Search: ImageVector by lazy {
        stroked("Search", join = StrokeJoin.Miter) {
            circle(11f, 11f, 6f)
            moveTo(15.5f, 15.5f); lineTo(20f, 20f)
        }
    }

    /** `#i-lib` — "Biblioteca" (two spines + a tilted book). */
    val Library: ImageVector by lazy {
        stroked("Library", join = StrokeJoin.Miter) {
            // M5 4v16M10 4v16
            moveTo(5f, 4f); verticalLineToRelative(16f)
            moveTo(10f, 4f); verticalLineToRelative(16f)
            // M14.5 5.5l4.5 1.2-3.6 13-4.5-1.2z
            moveTo(14.5f, 5.5f); lineToRelative(4.5f, 1.2f); lineToRelative(-3.6f, 13f)
            lineToRelative(-4.5f, -1.2f); close()
        }
    }

    /** `#i-cog` — "Ajustes"; also the Privacidad group icon. */
    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = ICON_SIZE.dp,
            defaultHeight = ICON_SIZE.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                circle(12f, 12f, 3f)
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                moveTo(12f, 3f); verticalLineToRelative(2.2f)
                moveTo(12f, 18.8f); verticalLineTo(21f)
                moveTo(21f, 12f); horizontalLineToRelative(-2.2f)
                moveTo(5.2f, 12f); horizontalLineTo(3f)
                moveTo(18.4f, 5.6f); lineToRelative(-1.6f, 1.6f)
                moveTo(7.2f, 16.8f); lineToRelative(-1.6f, 1.6f)
                moveTo(18.4f, 18.4f); lineToRelative(-1.6f, -1.6f)
                moveTo(7.2f, 7.2f); lineTo(5.6f, 5.6f)
            }
        }.build()
    }

    // ── Destinations & modes ──────────────────────────────────────────────────────────────────────

    /** `#i-eq` — "Ecualizador"; also the Reproductor y sonido settings group. */
    val Equalizer: ImageVector by lazy {
        stroked("Equalizer", join = StrokeJoin.Miter) {
            // M6 20V10M12 20V4M18 20v-7
            moveTo(6f, 20f); verticalLineTo(10f)
            moveTo(12f, 20f); verticalLineTo(4f)
            moveTo(18f, 20f); verticalLineToRelative(-7f)
            // M3.5 10h5M9.5 8h5M15.5 13h5
            moveTo(3.5f, 10f); horizontalLineToRelative(5f)
            moveTo(9.5f, 8f); horizontalLineToRelative(5f)
            moveTo(15.5f, 13f); horizontalLineToRelative(5f)
        }
    }

    /** `#i-radio` — "Iniciar radio" / the infinite radio marker. */
    val Radio: ImageVector by lazy {
        ImageVector.Builder(
            name = "Radio",
            defaultWidth = ICON_SIZE.dp,
            defaultHeight = ICON_SIZE.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply {
            path(fill = SolidColor(Color.Black)) { circle(12f, 12f, 2.4f) }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                // M7.8 7.8a6 6 0 0 0 0 8.4
                moveTo(7.8f, 7.8f); arcToRelative(6f, 6f, 0f, false, false, 0f, 8.4f)
                // M16.2 16.2a6 6 0 0 0 0-8.4
                moveTo(16.2f, 16.2f); arcToRelative(6f, 6f, 0f, false, false, 0f, -8.4f)
                // M4.9 4.9a10 10 0 0 0 0 14.2
                moveTo(4.9f, 4.9f); arcToRelative(10f, 10f, 0f, false, false, 0f, 14.2f)
                // M19.1 19.1a10 10 0 0 0 0-14.2
                moveTo(19.1f, 19.1f); arcToRelative(10f, 10f, 0f, false, false, 0f, -14.2f)
            }
        }.build()
    }

    /** `#i-timer` — "Temporizador" de apagado. */
    val Timer: ImageVector by lazy {
        stroked("Timer", join = StrokeJoin.Miter) {
            circle(12f, 13f, 7.5f)
            // M12 9.5V13l2.5 1.8
            moveTo(12f, 9.5f); verticalLineTo(13f); lineToRelative(2.5f, 1.8f)
            // M9.5 2.5h5
            moveTo(9.5f, 2.5f); horizontalLineToRelative(5f)
        }
    }

    /** `#i-speed` — "Velocidad y tono" (tempo/pitch). */
    val Speed: ImageVector by lazy {
        stroked("Speed", join = StrokeJoin.Miter) {
            // M4.5 17a8.5 8.5 0 1 1 15 0
            moveTo(4.5f, 17f); arcToRelative(8.5f, 8.5f, 0f, true, true, 15f, 0f)
            // M12 13l4-3.2
            moveTo(12f, 13f); lineToRelative(4f, -3.2f)
        }
    }

    /** `#i-artist` — "Ver artista"; also the Cuentas y sincronización settings group. */
    val Artist: ImageVector by lazy {
        stroked("Artist", join = StrokeJoin.Miter) {
            circle(12f, 8f, 3.6f)
            // M4.8 20a7.2 7.2 0 0 1 14.4 0
            moveTo(4.8f, 20f); arcToRelative(7.2f, 7.2f, 0f, false, true, 14.4f, 0f)
        }
    }

    /** `#i-album` — "Ver álbum"; also the Copias y migración settings group. */
    val Album: ImageVector by lazy {
        ImageVector.Builder(
            name = "Album",
            defaultWidth = ICON_SIZE.dp,
            defaultHeight = ICON_SIZE.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
            ) {
                circle(12f, 12f, 8.2f)
            }
            path(fill = SolidColor(Color.Black)) { circle(12f, 12f, 2.2f) }
        }.build()
    }

    /** `#i-video` — "Vídeo" (the canvas/video-mode toggle). */
    val Video: ImageVector by lazy {
        stroked("Video", cap = StrokeCap.Butt) {
            // rect x=3 y=6 w=12 h=12 rx=2.5
            roundedRect(3f, 6f, 12f, 12f, 2.5f)
            // M15 10.5l6-3v9l-6-3z
            moveTo(15f, 10.5f); lineToRelative(6f, -3f); verticalLineToRelative(9f)
            lineToRelative(-6f, -3f); close()
        }
    }

    /** `#i-cast` — "Transmitir" (the audio-output / Cast picker). */
    val Cast: ImageVector by lazy {
        stroked("Cast", join = StrokeJoin.Miter) {
            // M3 18.5a2.5 2.5 0 0 1 2.5 2.5
            moveTo(3f, 18.5f); arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, 2.5f)
            // M3 14a7 7 0 0 1 7 7
            moveTo(3f, 14f); arcToRelative(7f, 7f, 0f, false, true, 7f, 7f)
            // M3 9.5a11.5 11.5 0 0 1 11.5 11.5
            moveTo(3f, 9.5f); arcToRelative(11.5f, 11.5f, 0f, false, true, 11.5f, 11.5f)
            // M3 7.5v-2A1.5 1.5 0 0 1 4.5 4h15A1.5 1.5 0 0 1 21 5.5v13a1.5 1.5 0 0 1-1.5 1.5H15
            moveTo(3f, 7.5f); verticalLineToRelative(-2f)
            arcTo(1.5f, 1.5f, 0f, false, true, 4.5f, 4f)
            horizontalLineToRelative(15f)
            arcTo(1.5f, 1.5f, 0f, false, true, 21f, 5.5f)
            verticalLineToRelative(13f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
            horizontalLineTo(15f)
        }
    }

    /** `#i-people` — "Escuchar juntos". */
    val People: ImageVector by lazy {
        stroked("People", join = StrokeJoin.Miter) {
            circle(9f, 8f, 3.2f)
            // M3 19.5a6 6 0 0 1 12 0
            moveTo(3f, 19.5f); arcToRelative(6f, 6f, 0f, false, true, 12f, 0f)
            // M16 5.4a3.2 3.2 0 0 1 0 5.2
            moveTo(16f, 5.4f); arcToRelative(3.2f, 3.2f, 0f, false, true, 0f, 5.2f)
            // M17.5 14.5a6 6 0 0 1 3.5 5
            moveTo(17.5f, 14.5f); arcToRelative(6f, 6f, 0f, false, true, 3.5f, 5f)
        }
    }

    /** `#i-share` — "Compartir". */
    val Share: ImageVector by lazy {
        stroked("Share", join = StrokeJoin.Miter) {
            circle(18f, 5.5f, 2.5f)
            circle(6f, 12f, 2.5f)
            circle(18f, 18.5f, 2.5f)
            // M8.2 10.8l7.6-4M8.2 13.2l7.6 4
            moveTo(8.2f, 10.8f); lineToRelative(7.6f, -4f)
            moveTo(8.2f, 13.2f); lineToRelative(7.6f, 4f)
        }
    }

    // ── Builders ──────────────────────────────────────────────────────────────────────────────────

    private const val VIEWPORT = 24f
    private const val ICON_SIZE = 24f

    /** A fill-only symbol (`fill="currentColor"` in the render). */
    private fun filled(name: String, body: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = ICON_SIZE.dp,
            defaultHeight = ICON_SIZE.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) { body() }
        }.build()

    /**
     * A stroke-only symbol. The render's default is `stroke-width:1.9`, `stroke-linecap:round`,
     * `stroke-linejoin:round` — that weight is the whole visual identity of the set.
     */
    private fun stroked(
        name: String,
        width: Float = 1.9f,
        cap: StrokeCap = StrokeCap.Round,
        join: StrokeJoin = StrokeJoin.Round,
        body: PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = ICON_SIZE.dp,
        defaultHeight = ICON_SIZE.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = cap,
            strokeLineJoin = join,
        ) { body() }
    }.build()
}

/** `<circle cx cy r>` as two half arcs — Compose vectors have no circle primitive. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2f * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2f * r, 0f)
    close()
}

/** `<rect x y width height rx>`. */
private fun PathBuilder.roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    horizontalLineTo(x + w - r)
    arcToRelative(r, r, 0f, false, true, r, r)
    verticalLineTo(y + h - r)
    arcToRelative(r, r, 0f, false, true, -r, r)
    horizontalLineTo(x + r)
    arcToRelative(r, r, 0f, false, true, -r, -r)
    verticalLineTo(y + r)
    arcToRelative(r, r, 0f, false, true, r, -r)
    close()
}

/**
 * `M12 20.5s-7.5-4.7-7.5-9.7A4.3 4.3 0 0 1 12 8.2a4.3 4.3 0 0 1 7.5 2.6c0 5-7.5 9.7-7.5 9.7z`
 * — shared by the outline and filled hearts so they stay identical shapes.
 */
private fun PathBuilder.heartPath() {
    moveTo(12f, 20.5f)
    // s-7.5-4.7-7.5-9.7  →  smooth curve; expanded to an explicit cubic (no reflected control point
    // exists after a moveTo, so the first control point equals the current point).
    curveToRelative(0f, 0f, -7.5f, -4.7f, -7.5f, -9.7f)
    // A4.3 4.3 0 0 1 12 8.2
    arcTo(4.3f, 4.3f, 0f, false, true, 12f, 8.2f)
    // a4.3 4.3 0 0 1 7.5 2.6
    arcToRelative(4.3f, 4.3f, 0f, false, true, 7.5f, 2.6f)
    // c0 5-7.5 9.7-7.5 9.7
    curveToRelative(0f, 5f, -7.5f, 9.7f, -7.5f, 9.7f)
    close()
}
