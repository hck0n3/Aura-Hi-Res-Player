package iad1tya.echo.music.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import iad1tya.echo.music.R

// Central, de-branded settings-search index (adapted from upstream commit e244ac9).
//
// Each Triple is (settingLabel, sectionLabel, destinationRoute). SettingsScreen filters this list by
// the search query and appends the matches as navigable results. Labels come from the SAME string
// resources the fork's settings screens actually reference, so every entry compiles and routes to a
// real, registered screen.
//
// NOTE: the upstream "scroll-to-highlight the exact row" refinement is intentionally NOT ported here
// -- it would require editing every destination settings screen (out of scope), so a result navigates
// to its parent screen instead of the individual row.
// GHOST-ENTRY RULE (0.6.14x anti-placebo pass): a row here is a PROMISE that the text is visible on
// the destination screen. If a control is deleted, its row MUST go too, or the user searches it, finds
// it, navigates -- and it is not there. Removed on that basis: last_fm_send_likes (the switch is
// deliberately not shipped, see LastFMSettingsScreen), and the Listen Together create/join-room rows
// (those dialogs were unreachable dead code; the live flows live in ListenTogetherScreen / PlayerMenu
// and are indexed under their own screens).
//
// DUPLICATE RULE: a result renders as title + section only, so two rows with the SAME title AND the
// SAME section are indistinguishable. Removed four such pairs (Ecualizador, Predeterminado, Ondulado,
// Aura Hi-Res Update). Same title under DIFFERENT sections is fine and kept -- the section tells them
// apart ("Otros" x4, "Copiar" x2, ...).
//
// DESCRIPTION ROWS ARE KEPT ON PURPOSE: rows whose text is a setting's subtitle ("Salta patrocinios,
// autopromocion ...") are how a user finds a setting by what it DOES rather than by its name. They
// route to a real screen where that exact text is on display, so they are not ghosts. Dialog bodies and
// conditional subtitles are kept for the same reason -- they are one tap from the row they describe.
//
// ---------------------------------------------------------------------------------------------------
// 0.6.14x "Interfaz nueva" pass -- three more rules, 21 rows removed (388 -> 367):
//
// INDEX-ECHO RULE (new): SettingsScreen renders its own 17 index rows FIRST and then appends the
// matches from this list. A row here whose title AND route are identical to one of those 17 therefore
// draws the same destination twice, one above the other, and the second one looks like a bug. Removed
// 11: Cuentas, Scrobbling, Rendimiento, Apariencia, Contenido, Traduccion de letras con IA,
// Almacenamiento, Privacidad, Copias de seguridad y restauracion, Acerca de, Registros. Their
// destinations stay reachable -- they are the top-level rows.
// NOT removed: listen_together -> "settings/integrations/listen_together". Same title, DIFFERENT route
// (the index row goes to the Listen Together MAIN screen), and it is the only entry that reaches the
// Listen Together settings screen at all.
//
// NON-TEXT RULE (new): a row must be a control or a caption the user can READ on the destination, not
// an accessibility label, an unformatted format string, a toast, an error or an empty state. Removed
// 9: cd_back ("Atras", a contentDescription), cd_palette_item ("Paleta %1$s", rendered raw),
// sensitivity_percentage ("%1$d%%", rendered raw), copied_to_clipboard + export_directory_picker_-
// unavailable (toasts), no_logs_yet + listen_together_no_logs (empty states), app_name (the app's own
// name is not a setting), and R.string.settings under Listen Together (a bare "Ajustes" row).
// Also removed "Ecualizador grafico": that string exists nowhere but here -- the row on
// settings/sound is titled echo_equalizer ("Ecualizador"), which is already indexed.
// CORRECTION (anti-placebo follow-up): that removal took the LAST row pointing at settings/equalizer
// with it, so searching the equalizer could no longer reach the equalizer screen -- with either UI. A
// row titled with the screen's own title (echo_equalizer) under the section "Ecualizador" is back
// below. A destination that exists and is registered must stay reachable from this index.
//
// SECTION-NAME RULE (new): the section is the only thing that tells two same-titled results apart, so
// it must be Spanish and unambiguous. "Listen Together" -> "Ajustes de Escuchar juntos" (21 rows), so a
// result reads "<ajuste> / Ajustes de Escuchar juntos" and is clearly not the main Escuchar juntos
// screen. That screen had NO index row anywhere; the new Ajustes index (ui/newui/AuraSettingsScreen.kt)
// adds one inside its "Escuchar juntos" group.
@Composable
fun getAllSearchableSettings(): List<Triple<String, String, String>> {
    return listOf(
            // --- Cuentas / fork-only screens (hardcoded labels; these screens use literal titles) ---
            Triple("YouTube Music", "Cuentas", "settings/accounts"),
            Triple("Spotify", "Cuentas", "settings/accounts"),
            Triple("Iniciar sesión", "Cuentas", "settings/accounts"),
            Triple("Cerrar sesión", "Cuentas", "settings/accounts"),
            Triple("Conectar", "Cuentas", "settings/accounts"),
            Triple("Last.fm", "Cuentas", "settings/accounts"),
            Triple("ListenBrainz", "Cuentas", "settings/accounts"),
            Triple("Scrobbling", "Cuentas", "settings/accounts"),
            Triple("Importar de Spotify", "Cuentas", "settings/spotify_import"),
            // --- Scrobbling (settings/lastfm) ---
            Triple(stringResource(R.string.lastfm_integration), "Scrobbling", "settings/lastfm"),
            Triple(stringResource(R.string.enable_scrobbling), "Scrobbling", "settings/lastfm"),
            Triple(stringResource(R.string.lastfm_now_playing), "Scrobbling", "settings/lastfm"),
            Triple(stringResource(R.string.scrobbling_configuration), "Scrobbling", "settings/lastfm"),
            Triple(stringResource(R.string.scrobble_min_track_duration), "Scrobbling", "settings/lastfm"),
            Triple(stringResource(R.string.scrobble_delay_percent), "Scrobbling", "settings/lastfm"),
            Triple(stringResource(R.string.scrobble_delay_minutes), "Scrobbling", "settings/lastfm"),
            Triple(stringResource(R.string.listenbrainz_scrobbling), "Scrobbling", "settings/lastfm"),
            Triple(stringResource(R.string.set_listenbrainz_token), "Scrobbling", "settings/lastfm"),
            // --- Sonido / Rendimiento extras (these screens use hardcoded Spanish titles) ---
            Triple("Auto-EQ (por auricular)", "Sonido y ecualización", "settings/sound/autoeq"),
            Triple("Volumen seguro", "Sonido y ecualización", "settings/sound"),
            Triple("Modo alto rendimiento", "Rendimiento", "settings/performance"),
            Triple("Vista dividida estilo Spotify", "Rendimiento", "settings/performance"),
            Triple("Mostrar el panel del reproductor", "Rendimiento", "settings/performance"),
            Triple("Panel del reproductor a la izquierda", "Rendimiento", "settings/performance"),
            // --- Apariencia (settings/appearance) : 99 ---
            Triple(stringResource(R.string.global_haptics), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_text_position), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.left), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.center), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.right), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_animation_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.fade), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.glow), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.slide), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.karaoke), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.apple_music_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.apple_music_style_letter), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.echomusic_1), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_v2_fluid), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_animation_metro), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_text_size), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_line_spacing), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.player_buttons_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.default_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.primary_color_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.tertiary_color_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.player_background_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.follow_theme), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.gradient), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.player_background_blur), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.glow_animated), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.apple_music), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.live_mesh), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.liquid_glass), "Apariencia", "settings/appearance/liquidglass"),
            Triple(stringResource(R.string.miniplayer_background_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.default_open_tab), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.home), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.search), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.filter_library), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.default_lib_chips), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.songs), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.artists), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.albums), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.playlists), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.filter_local), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.grid_cell_size), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.big), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.small), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.density_restart_message), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.wavy), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.slim), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.theme), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.theme_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.enable_high_refresh_rate), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.enable_high_refresh_rate_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.enable_dynamic_theme), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.mini_player), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.player), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.hide_player_thumbnail), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.hide_player_thumbnail_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.thumbnail_corner_radius), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.thumbnail_corner_radius_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.crop_album_art), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.crop_album_art_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.player_slider_style), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.enable_swipe_thumbnail), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.echomusic_canvas), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.echomusic_canvas_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.rotating_thumbnail), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.rotating_thumbnail_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_album_canvas), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_album_canvas_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_comment_button), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_comment_button_description), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.swipe_sensitivity), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_glow_effect), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_glow_effect_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.apple_music_lyrics_blur), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.apple_music_lyrics_blur_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.standard_lyrics_blur), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_click_change), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_auto_scroll), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_swipe_to_change_song), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_swipe_to_change_song_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_thumbnail_play_pause), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.lyrics_thumbnail_play_pause_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.hide_status_bar_on_fullscreen), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.hide_status_bar_on_fullscreen_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.misc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.swipe_song_to_add), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.swipe_song_to_remove), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.listen_together_in_top_bar), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.listen_together_in_top_bar_desc), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.display_density), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.auto_playlists), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_liked_playlist), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_downloaded_playlist), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.action_exported), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_top_playlist), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_cached_playlist), "Apariencia", "settings/appearance"),
            Triple(stringResource(R.string.show_uploaded_playlist), "Apariencia", "settings/appearance"),
            // --- Tema (settings/appearance/theme) : 26 ---
            Triple(stringResource(R.string.palette_dynamic), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_crimson), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_rose), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_purple), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_deep_purple), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_indigo), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_blue), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_sky_blue), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_cyan), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_teal), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_green), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_light_green), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_lime), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_yellow), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_amber), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_orange), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_deep_orange), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_brown), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_grey), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.palette_blue_grey), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.theme_colors), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.theme_mode), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.dark_theme_follow_system), "Tema", "settings/appearance/theme"),
            Triple(stringResource(R.string.color_palette), "Tema", "settings/appearance/theme"),
            // --- Contenido (settings/content) : 53 ---
            Triple(stringResource(R.string.config_proxy), "Contenido", "settings/content"),
            Triple(stringResource(R.string.proxy_type), "Contenido", "settings/content"),
            Triple(stringResource(R.string.proxy_url), "Contenido", "settings/content"),
            Triple(stringResource(R.string.enable_authentication), "Contenido", "settings/content"),
            Triple(stringResource(R.string.proxy_username), "Contenido", "settings/content"),
            Triple(stringResource(R.string.proxy_password), "Contenido", "settings/content"),
            Triple(stringResource(R.string.content_language), "Contenido", "settings/content"),
            Triple(stringResource(R.string.system_default), "Contenido", "settings/content"),
            Triple(stringResource(R.string.content_country), "Contenido", "settings/content"),
            Triple(stringResource(R.string.app_language), "Contenido", "settings/content"),
            Triple(stringResource(R.string.lyrics_provider_priority), "Contenido", "settings/content"),
            Triple(stringResource(R.string.lyrics_provider_priority_desc), "Contenido", "settings/content"),
            Triple(stringResource(R.string.set_quick_picks), "Contenido", "settings/content"),
            Triple(stringResource(R.string.quick_picks), "Contenido", "settings/content"),
            Triple(stringResource(R.string.last_song_listened), "Contenido", "settings/content"),
            Triple(stringResource(R.string.top_length), "Contenido", "settings/content"),
            Triple(stringResource(R.string.network_ip_version), "Contenido", "settings/content"),
            Triple(stringResource(R.string.ip_version_auto), "Contenido", "settings/content"),
            Triple(stringResource(R.string.ip_version_ipv4), "Contenido", "settings/content"),
            Triple(stringResource(R.string.ip_version_ipv6), "Contenido", "settings/content"),
            Triple(stringResource(R.string.general), "Contenido", "settings/content"),
            Triple(stringResource(R.string.hide_explicit), "Contenido", "settings/content"),
            Triple(stringResource(R.string.hide_video_songs), "Contenido", "settings/content"),
            Triple(stringResource(R.string.hide_youtube_shorts), "Contenido", "settings/content"),
            Triple(stringResource(R.string.artist_page_settings), "Contenido", "settings/content"),
            Triple(stringResource(R.string.show_artist_description), "Contenido", "settings/content"),
            Triple(stringResource(R.string.show_artist_subscriber_count), "Contenido", "settings/content"),
            Triple(stringResource(R.string.show_artist_monthly_listeners), "Contenido", "settings/content"),
            Triple(stringResource(R.string.show_artist_video), "Contenido", "settings/content"),
            Triple(stringResource(R.string.show_artist_video_desc), "Contenido", "settings/content"),
            Triple(stringResource(R.string.show_artist_background_video), "Contenido", "settings/content"),
            Triple(stringResource(R.string.show_artist_background_video_desc), "Contenido", "settings/content"),
            Triple(stringResource(R.string.proxy), "Contenido", "settings/content"),
            Triple(stringResource(R.string.enable_proxy), "Contenido", "settings/content"),
            Triple(stringResource(R.string.lyrics), "Contenido", "settings/content"),
            Triple(stringResource(R.string.enable_lrclib), "Contenido", "settings/content"),
            Triple(stringResource(R.string.enable_kugou), "Contenido", "settings/content"),
            Triple(stringResource(R.string.enable_better_lyrics), "Contenido", "settings/content"),
            Triple(stringResource(R.string.enable_better_lyrics_desc), "Contenido", "settings/content"),
            Triple(stringResource(R.string.enable_simpmusic), "Contenido", "settings/content"),
            Triple(stringResource(R.string.enable_simpmusic_desc), "Contenido", "settings/content"),
            Triple(stringResource(R.string.lyrics_romanization), "Contenido", "settings/content"),
            Triple(stringResource(R.string.randomize_home_order), "Contenido", "settings/content"),
            Triple(stringResource(R.string.randomize_home_order_desc), "Contenido", "settings/content"),
            Triple(stringResource(R.string.misc), "Contenido", "settings/content"),
            Triple(stringResource(R.string.open_links_title), "Contenido", "settings/content"),
            Triple(stringResource(R.string.open_links_summary), "Contenido", "settings/content"),
            Triple(stringResource(R.string.logs_heading), "Contenido", "settings/content"),
            Triple(stringResource(R.string.playback_logs), "Contenido", "settings/content"),
            Triple(stringResource(R.string.playback_logs_desc), "Contenido", "settings/content"),
            Triple(stringResource(R.string.service_uptime), "Contenido", "settings/content"),
            Triple(stringResource(R.string.service_uptime_desc), "Contenido", "settings/content"),
            // --- Romanizacion (settings/content/romanization) : 20 ---
            Triple(stringResource(R.string.general), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_as_main), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_japanese), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_korean), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_chinese), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_hindi), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_punjabi), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanization_cyrillic), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_russian), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_ukrainian), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_serbian), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_bulgarian), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_belarusian), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_kyrgyz), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_macedonian), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.line_by_line_option_title), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.line_by_line_option_desc), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.line_by_line_dialog_title), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.line_by_line_dialog_desc), "Romanización", "settings/content/romanization"),
            Triple(stringResource(R.string.lyrics_romanize_title), "Romanización", "settings/content/romanization"),
            // --- Traduccion de letras IA (settings/ai) : 28 ---
            Triple(stringResource(R.string.ai_provider_openrouter_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider_openai_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider_perplexity_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider_gemini_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider_xai_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider_mistral_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider_nvidia_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider_deepl_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider_help), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_translation_mode), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_translation_literal), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_translation_literal_desc), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_translation_transcribed), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_translation_transcribed_desc), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_provider), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_target_language), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_api_key), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_deepl_formality), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_deepl_formality_default), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_deepl_formality_more), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_deepl_formality_less), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_base_url), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_model), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_playlist_settings_title), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_playlist_enabled_title), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_playlist_enabled_desc), "Traducción de letras IA", "settings/ai"),
            Triple(stringResource(R.string.ai_setup_guide), "Traducción de letras IA", "settings/ai"),
            Triple("Recomendado para ti (IA)", "Traducción de letras IA", "settings/ai"),
            Triple("Refrescar ahora", "Traducción de letras IA", "settings/ai"),
            // --- Reproductor (settings/player) : 49 ---
            Triple("Ahorro de datos", "Reproductor", "settings/player"),
            Triple("Modo ahorro de datos", "Reproductor", "settings/player"),
            Triple(stringResource(R.string.audio_quality), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.download_quality_title), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.crossfade_beta_title), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.crossfade_beta_message), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.enable_lossless_audio), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.lossless_audio_warning), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.player), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.crossfade), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.crossfade_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.crossfade_duration), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.crossfade_gapless), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.crossfade_gapless_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.history_duration), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.audio_offload), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.audio_offload_disabled_by_crossfade), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.audio_offload_description), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.sponsorblock_title), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.sponsorblock_description), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.google_cast), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.google_cast_description), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.seek_seconds_addup), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.seek_seconds_addup_description), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.queue), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.persistent_queue), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.persistent_queue_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.auto_load_more), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.auto_load_more_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.disable_load_more_when_repeat_all), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.disable_load_more_when_repeat_all_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.enable_similar_content), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.similar_content_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.persistent_shuffle_title), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.persistent_shuffle_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.remember_shuffle_and_repeat), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.remember_shuffle_and_repeat_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.shuffle_playlist_first), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.shuffle_playlist_first_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.prevent_duplicate_tracks_in_queue), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.prevent_duplicate_tracks_in_queue_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.auto_skip_next_on_error), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.auto_skip_next_on_error_desc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.misc), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.stop_music_on_task_clear), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.pause_music_when_media_is_muted), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.resume_on_bluetooth_connect), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.keep_screen_on_when_player_is_expanded), "Reproductor", "settings/player"),
            Triple(stringResource(R.string.player_and_audio), "Reproductor", "settings/player"),
            // --- Descifrado de YouTube (settings/youtube_decryption) : 4 ---
            Triple(stringResource(R.string.youtube_decryption_settings), "Descifrado de reproducción", "settings/youtube_decryption"),
            Triple(stringResource(R.string.youtube_decryption_desc), "Descifrado de reproducción", "settings/youtube_decryption"),
            Triple(stringResource(R.string.force_update_cipher), "Descifrado de reproducción", "settings/youtube_decryption"),
            Triple(stringResource(R.string.cipher_last_updated), "Descifrado de reproducción", "settings/youtube_decryption"),
            // --- Sonido y ecualizacion (settings/sound) : 2 ---
            Triple(stringResource(R.string.echo_equalizer), "Sonido y ecualización", "settings/sound"),
            Triple(stringResource(R.string.echo_equalizer_desc), "Sonido y ecualización", "settings/sound"),
            // --- Ecualizador (settings/equalizer) : 1 ---
            // The EQ screen itself. Removing the old "Ecualizador grafico" row left NOTHING in this index
            // routing to settings/equalizer, so the search shortcut to the equalizer was gone even with the
            // classic UI on -- reachable only via settings/sound or a player menu. The label is the screen's
            // OWN title (AxionEqScreen renders R.string.echo_equalizer), so the row is not a ghost, and the
            // section differs from the row above it, so the two results stay distinguishable per the
            // DUPLICATE RULE: "Ecualizador / Sonido y ecualizacion" (the settings row) vs
            // "Ecualizador / Ecualizador" (the screen).
            Triple(stringResource(R.string.echo_equalizer), "Ecualizador", "settings/equalizer"),
            // --- Rendimiento (settings/performance) : 0 ---
            // --- Almacenamiento (settings/storage) : 21 ---
            Triple(stringResource(R.string.song_cache), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.image_cache), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.clear_all_downloads), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.clear_downloads_dialog), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.clear_song_cache), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.clear_song_cache_dialog), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.clear_image_cache), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.clear_image_cache_dialog), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.cache_size_warning_title), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.cache_size_warning_message), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.cache_size_warning_confirm), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.downloaded_songs), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.export_directory), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.auto_download_on_like), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.auto_download_on_like_desc), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.export_desc), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.max_song_cache_size), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.unlimited), "Almacenamiento", "settings/storage"),
            Triple(stringResource(R.string.max_image_cache_size), "Almacenamiento", "settings/storage"),
            // --- Privacidad (settings/privacy) : 12 ---
            Triple(stringResource(R.string.clear_listen_history_confirm), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.clear_search_history_confirm), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.listen_history), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.pause_listen_history), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.clear_listen_history), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.search_history), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.pause_search_history), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.clear_search_history), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.misc), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.disable_screenshot), "Privacidad", "settings/privacy"),
            Triple(stringResource(R.string.disable_screenshot_desc), "Privacidad", "settings/privacy"),
            // --- Copia y restauracion (settings/backup_restore) : 2 ---
            // --- Acerca de (settings/about) : 1 ---
            // --- Registros (settings/logs) : 6 ---
            Triple(stringResource(R.string.copy), "Registros", "settings/logs"),
            Triple(stringResource(R.string.share), "Registros", "settings/logs"),
            Triple(stringResource(R.string.app_log), "Registros", "settings/logs"),
            Triple(stringResource(R.string.last_crash), "Registros", "settings/logs"),
            // --- Actualizacion (settings/update) : 12 ---
            Triple(stringResource(R.string.app_updates_title), "Actualización", "settings/update"),
            Triple(stringResource(R.string.system_update), "Actualización", "settings/update"),
            Triple(stringResource(R.string.update_available), "Actualización", "settings/update"),
            Triple(stringResource(R.string.app_update_uptodate), "Actualización", "settings/update"),
            Triple(stringResource(R.string.version), "Actualización", "settings/update"),
            Triple(stringResource(R.string.auto_update_check), "Actualización", "settings/update"),
            Triple(stringResource(R.string.auto_update_check_subtitle), "Actualización", "settings/update"),
            Triple(stringResource(R.string.update_notifications), "Actualización", "settings/update"),
            Triple(stringResource(R.string.update_notifications_subtitle), "Actualización", "settings/update"),
            Triple(stringResource(R.string.clear_downloaded_updates), "Actualización", "settings/update"),
            Triple(stringResource(R.string.clear_downloaded_updates_desc), "Actualización", "settings/update"),
            // --- Listen Together (settings/integrations/listen_together) : 31 ---
            Triple(stringResource(R.string.listen_together_username), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_blocked_users), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_blocked_users_count), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_no_blocked_users), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_server_url), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_cannot_edit_username_in_room), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_auto_approval), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_auto_approval_desc), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_sync_volume), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_sync_volume_desc), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_smart_resync), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_smart_resync_desc), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_view_logs), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_view_logs_desc), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_logs), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.copy), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_choose_server), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_custom_server), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.listen_together_use_custom_server), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
            Triple(stringResource(R.string.unblock), "Ajustes de Escuchar juntos", "settings/integrations/listen_together"),
    )
}
