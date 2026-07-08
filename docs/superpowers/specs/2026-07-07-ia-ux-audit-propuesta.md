# Propuesta de Reorganización de IA/UX — Aura Hi-Res

_Auditoría del 2026-07-07 (workflow: 5 mapeadores + síntesis). Solo lectura; no se modificó código. Toda afirmación cita `file:line`. Pendiente de aprobación antes de tocar código._

---

## 0. Resumen ejecutivo

Funcionalidad rica pero la **estructura no acompaña**: dos superficies de ajustes en paralelo (`SettingsScreen` + `SettingDialoge`), controles de audio partidos entre dos pantallas, Listen Together enterrado, pantallas muertas registradas en el grafo de navegación, y un hub de Library que compite con sus propias pestañas. La mayor parte del valor se captura **solo re-agrupando y re-enrutando**, sin rediseño visual.

- **Parte A — Agrupación/estructura** (bajo riesgo, alto valor): mover ítems entre grupos, arreglar rutas rotas, deduplicar entradas.
- **Parte B — Rediseño visual** (mayor esfuerzo): unificar player/audio, refundir hub de Library, sistema de menús.

---

## 1. Problemas por impacto

### Alta
1. **Dos superficies de ajustes paralelas.** Hub `SettingsScreen.kt:53` + diálogo `SettingDialoge.kt:38` (avatar de Home, `MainActivity.kt:1044`). `UseLoginForBrowse` y `YtmSyncKey` **solo** en el diálogo (`SettingDialoge.kt:132-162`), ausentes del árbol principal.
2. **Audio partido en dos pantallas con títulos solapados.** Calidad/offload/crossfade en "Player & audio" (`PlayerSettings.kt:1109`); EQ/Safe-Volume en "Sound & EQ" (`SoundSettings.kt:43`).
3. **Listen Together enterrado.** `ListenTogetherInTopBarKey` default `true` lo quita de la barra inferior (`MainActivity.kt:652-655`); única entrada dentro de Settings (`SettingsScreen.kt:184`) y apunta a la pantalla, no a sus ajustes (`ListenTogetherSettings.kt:89`).
4. **Pantallas muertas/huérfanas en nav.** `AccountSettingsScreen` sin `navigate()` (`NavigationBuilder.kt:340`); `IntegrationScreen` body vacío (`IntegrationScreen.kt:34-40`); `ExploreScreen.kt:83`, `charts_screen` (`NavigationBuilder.kt:144`), `new_release` inalcanzables.
5. **Solapamiento Home ↔ Library.** Home "AccountPlaylists" duplica pestaña Playlists; ForgottenFavorites/KeepListening/GenreMix (`HomeScreen.kt:178-191`) solapan "My Top"/liked del Mix hub.
6. **El Mix hub de Library compite con sus pestañas.** Botones auto-playlist Liked/Downloaded/Uploaded/Exported (`LibraryMixScreen.kt:330-414`) = mismos que chips `SongFilter.*`; "Favorite albums" == `AlbumFilter.LIKED`. "Local" es chip Y botón always-on (`LibraryScreen.kt:38`, `LibraryMixScreen.kt:405-413`).

### Media
7. **Lyrics en 3 pantallas** sin hogar único: Appearance (`AppearanceSettings.kt:1461`), Content (`ContentSettings.kt:940`), Romanization (`RomanizationSettings.kt:54`).
8. **Controles de descubrimiento bajo Content**, no cerca de Home: taste-only/rich-home/keep-genre-lane/randomize (`ContentSettings.kt:1113-1211`).
9. **Descargas dispersas.** Auto-download-on-like en Player›Queue (`PlayerSettings.kt:847`), Export-MP3 en Player›Misc (`:1082`), directorio/caché en Storage.
10. **Canvas partido.** Album canvas en Content (`ContentSettings.kt:825`) vs thumbnail/rotating en Appearance (`AppearanceSettings.kt:1282,1303`).
11. **Toggles inertes/fantasma.** Skip-silence 2 switches pero hardcoded OFF (`PlayerSettings.kt:569-618`); `SoundSettings.kt:28-32` importa DSP/visualizer keys no renderizadas; spectrum visualizer (PK:844) sin setter.
12. **Menús contextuales inconsistentes.** "Pin to Speed dial" con 3 etiquetas (`SongMenu.kt:505`, `ArtistMenu.kt:167`, `YouTubeSongMenu.kt:433`); 2 keys para "Add to playlist"/"Start radio"; Share duplicado `PlaylistMenu.kt:388/657`; Shuffle no baraja `YouTubeAlbumMenu.kt:303/322`.

### Baja
13. **"App language" dos veces** en Content (`ContentSettings.kt:621` y `:854`).
14. **Logs en 3 entradas** (Content:1291, top-level, Listen Together).
15. **Strings ES/EN hardcodeados** rompen búsqueda/localización ("Aura Hi-Res Update" `SettingsScreen.kt:77`, "Set as Ringtone", "No recomendar", "Ir al podcast").
16. **Sleep timer sin entrada en Settings** — solo player expandido (`Player.kt:803`).
17. **`DefaultOpenTab=SEARCH` se ignora** (`when` solo HOME/LIBRARY, `MainActivity.kt:1271-1275`).

---

## 2. Navegación top-level propuesta

Mantener 4 pestañas (no romper el modelo mental), corregir entradas:
- **Home**: mantener + acceso a controles de descubrimiento.
- **Search**: honrar `DefaultOpenTab=SEARCH` o quitar la opción.
- **Library**: simplificar (Parte B).
- **Listen Together**: decisión de producto — 4ª pestaña estable o entrada fija en top-bar. Hoy sin entrada visible por defecto.

Limpieza de grafo (des-registrar, no destructivo): `charts_screen`, `settings/integrations` vacío, `settings/account` huérfano.

---

## 3. Agrupación de Settings propuesta

Objetivo: **una sola superficie** (absorber `SettingDialoge`) agrupada por dominio mental:

1. **Cuenta y sincronización** (nuevo) — Login YTM · Use-account-for-browse · YTM sync · Spotify import · Backup/restore · Migración selectiva.
2. **Audio** (fusión) — Calidad (stream/download/offload/fallback) · Ecualizador y volumen (EQ/Auto-EQ/Safe Volume) · Reproducción (Crossfade+gapless/SponsorBlock/Preload).
3. **Cola y comportamiento** — persistent queue, shuffle/repeat, prevent-dup, auto-skip, keep-screen-on + **Sleep timer**.
4. **Descargas y almacenamiento** — downloads/caché/export-dir + Auto-download-on-like (mover) + Export-MP3 (mover).
5. **Letras** (hub nuevo) — Proveedores+prioridad · Apariencia · Romanización · Traducción.
6. **Apariencia** — Tema/dynamic/refresh/haptics + Player UI (todo Canvas aquí, incluye album canvas) + Mini player.
7. **Inicio y descubrimiento** (nuevo) — taste-only/rich-home/keep-genre-lane/randomize/quick-picks/speed-dial.
8. **Contenido y región** — language/country/region · hide explicit/video/shorts · artist toggles · App language (una vez) · proxy.
9. **Rendimiento y pantalla grande** — high-performance mode · battery/autostart · force-split · side-panel.
10. **Privacidad** — listen/search history · disable-screenshot.
11. **Integraciones** — rellenar stub o eliminar; Listen Together ajustes reales.
12. **Acerca de / Actualización / Logs** — unificar los 3 Logs.

---

## 4. Menús contextuales (normalización, no rediseño)
- Una sola string key por acción (unificar add_to_playlist/start_radio duplicadas).
- Localizar strings hardcodeadas (Pin, Set as Ringtone, etc.).
- Paridad local↔YouTube por entidad (Details/Refetch/Edit).
- Quitar Share duplicado `PlaylistMenu.kt:388/657`; arreglar Shuffle `YouTubeAlbumMenu.kt:322`.

## 5. Library (Parte B)
- Un solo modelo: pestañas como fuente de verdad; Mix hub reducido a dashboard sin duplicar chips.
- Quitar "Local" always-on; renombrar sección "Playlists" que renderiza álbumes+artistas (`LibraryMixScreen.kt:462-467`).
- Resolver colisión `LibraryFilter.LIBRARY` vs `SongFilter.LIBRARY`.
- Defaults sorpresa: Songs/Artists abren en LIKED (`LibrarySongsScreen.kt:90`, `LibraryArtistsScreen.kt:91`).
- Deduplicar `FlowRow` LIST vs GRID (`:321-414` vs `:642-734`).

---

## 6. Migración por fases

| Fase | Alcance | Riesgo |
|---|---|---|
| **F0 Higiene** | Des-registrar rutas muertas; ocultar toggles inertes | Bajo |
| **F1 Rutas visibles** | Entrada real a Listen Together; Sleep timer en Settings | Bajo |
| **F2 Consolidar Settings** | Mover Auto-download/Export-MP3/album-canvas/battery/history; quitar App-language dup; grupo Descubrimiento | Bajo-Medio |
| **F3 Superficie única de cuenta** | Grupo Cuenta; migrar UseLoginForBrowse/YtmSync del diálogo; deprecar SettingDialoge | Medio |
| **F4 Fusión Audio + Hub Letras** | Unir Player-audio+Sound; hub Lyrics | Medio |
| **F5 Rediseño Library + Menús** | Parte B completa | Alto |

**Regla transversal no destructiva:** al mover un toggle, **conservar su `PreferenceKey`** (no crear key nueva salvo re-aplicar default forzado — ver memoria "one-time default migrations need a fresh key"). El estado del usuario se preserva; el cambio es solo de ubicación.
