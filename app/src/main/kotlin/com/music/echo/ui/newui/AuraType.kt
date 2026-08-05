package iad1tya.echo.music.ui.newui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import iad1tya.echo.music.ui.theme.AppFontFamily

/**
 * The "Interfaz nueva" type scale.
 *
 * The reference render draws phones at 258 px wide, i.e. a 360 dp phone scaled by ~1.4. Every size here
 * is the render's px value × 1.4, rounded to a whole sp. Do not invent intermediate sizes — if a screen
 * needs something that is not here, the render did not have it.
 *
 * Two families only:
 *  · [AppFontFamily] (Google Sans Flex, the app-wide face) for everything readable.
 *  · [FontFamily.Monospace] for TECHNICAL DATA and SECTION LABELS — the tracked, low-opacity uppercase
 *    strings that make the app look like an instrument (FLAC / 24 BIT / 96 kHz, EQ ON, −1.0 dBFS,
 *    "A CONTINUACIÓN · DE TU LISTA").
 */
object AuraType {

    /** Screen title: "Inicio", "Biblioteca", "Ajustes". Render `.h` 21px / w680 / ls -.03em. */
    val ScreenTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.9).sp,
    )

    /** Smaller screen title used by the queue sheet ("En cola"). Render 18px. */
    val SheetTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.7).sp,
    )

    /** Now-playing song title in the full player. Render 15.5px / w620. */
    val PlayerTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    )

    /** Now-playing artist in the full player. Render 11px / opacity .55. */
    val PlayerArtist = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 19.sp,
    )

    /** Title line of any list row. Render `.t` 11.5px / w620. */
    val RowTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.15).sp,
    )

    /** Subtitle line of any list row. Render `.a` 10px / opacity .55. */
    val RowSubtitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )

    /** A menu entry label. Render `.mrow span` 11.5px. */
    val MenuLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )

    /** Mini-player title. Render 10.5px. */
    val MiniTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    )

    /** Mini-player artist. Render 9px. */
    val MiniArtist = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    )

    /**
     * Tracked uppercase section label. Render `.lb` mono 8px / ls .16em / opacity .5 —
     * "SONANDO", "A CONTINUACIÓN · DE TU LISTA", "BUENAS NOCHES", "RECOMENDADO PARA TI · IA".
     * Callers pass the string ALREADY uppercase (Spanish accents survive; do not `.uppercase()` a
     * localized string at runtime).
     */
    val SectionLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.8.sp,
    )

    /** Menu group header. Render `.mh` mono 8px / ls .14em / opacity .42 — "REPRODUCCIÓN", "MÁS". */
    val MenuGroupLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
    )

    /**
     * Technical data. Render mono 8px / ls .1em / opacity .5 — "FLAC", "24 BIT", "96 kHz",
     * "EQ ON", "V.SEGURO ON", "−1.0 dBFS", "◆ HI-RES", "DESLIZA PARA QUITAR".
     */
    val Technical = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.1.sp,
    )

    /** Elapsed / total time under the progress bar. Render mono 8.5px. */
    val Timecode = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp,
    )

    /** Per-row quality badge ("24/96", "16/44"). Render `.hz` mono 7.5px. */
    val QualityBadge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
    )

    /** Bottom-navigation label. Render 8.5px. */
    val NavLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
    )

    /** Library filter chip. Render 10px / w620 when selected. */
    val Chip = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )

    /** Title of the "Interfaz nueva" callout card. Render 11px / w620. */
    val CalloutTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
    )

    /** Subtitle of the "Interfaz nueva" callout card. Render 9.5px / opacity .6. */
    val CalloutSubtitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )
}

/** Every text in the new UI is single-line + ellipsis unless a screen explicitly says otherwise. */
val AuraDefaultOverflow = TextOverflow.Ellipsis
