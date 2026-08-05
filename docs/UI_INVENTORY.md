# Inventario completo de la interfaz — Aura Hi-Res Player

> **Para qué existe este documento.** Antes de reescribir una sola línea de la interfaz hay que saber
> exactamente qué botones, acciones, gestos y pantallas expone la app HOY. Cada fila de este inventario
> es una función que el rediseño tiene que colocar en algún sitio de forma explícita. Lo que no aparezca
> en el diseño nuevo y esté en esta lista, se habrá perdido en silencio.
>
> Este documento **no propone ningún diseño** y **no sugiere quitar nada**. Sólo enumera.

---

## Cómo leer este inventario

**Fuente de las etiquetas.** La carpeta `app/src/main/res/values/` está en **inglés** (es el idioma base).
El texto que el dueño ve en el móvil viene de `app/src/main/res/values-es/`. Comprobado:
**1.598 strings en inglés, 1.599 en español, 0 sin traducir.** Todas las etiquetas de abajo se han
resuelto contra `values-es/strings.xml` y `values-es/echo_strings.xml`.

Marcas que aparecen en la columna *Nombre*:

| Marca | Significado |
|---|---|
| `(R.string.x)` | La etiqueta viene de recursos y está traducida. |
| `(hardcoded)` | El texto está escrito a mano en Kotlin. **No es traducible**: si algún día se añade otro idioma, se quedará como está. |
| `(hardcoded, en inglés)` | Igual, pero además el usuario español ve inglés hoy mismo. |
| `(solo icono)` | El control no tiene texto; se indica su `contentDescription` si existe. |

**Columna Tipo**
- *acción primaria* — el motivo por el que existe la pantalla.
- *acción secundaria* — útil pero no es el eje de la pantalla.
- *conmutador* — enciende/apaga o cicla entre estados.
- *navegación* — lleva a otra pantalla, menú o diálogo.
- *destructiva* — borra, quita o interrumpe algo difícil de deshacer.
- *informativo* — no se pulsa; muestra estado. Se incluye porque si el rediseño lo borra, el usuario pierde información.

**Columna Frecuencia** — cuántas veces lo toca un usuario normal: *constante* (varias veces por sesión),
*diaria*, *ocasional* (algunas veces al mes), *rara* (se configura una vez o casi nunca).

**Columna Condicional** — `siempre` significa que el control está visible en todos los estados.
Cualquier otra cosa es una condición: ese control **no sale en una captura de pantalla normal** y es
justo el tipo de cosa que un mockup se come sin que nadie lo note.

**Rutas.** Todas las rutas son relativas a la raíz del repo. Ojo con el desfase entre paquete y disco:
el paquete Kotlin es `iad1tya.echo.music` pero la carpeta en disco es `com/music/echo/`.

---

## Índice

**El inventario, pantalla por pantalla**

1. [Esqueleto de la app y mapa de navegación](#1-esqueleto-de-la-app-y-mapa-de-navegación) — las 70 rutas, la barra inferior, el rail, las puertas de términos y licencia, el onboarding, la migración
2. [**Reproductor**](#2-reproductor) — 13 superficies distintas
3. [Reproductor > menú «Más»](#3-reproductor--menú-más-chip--oldplayermenu)
4. [Cola](#4-cola)
5. [Cola > menú ⋮](#5-cola--menú---playermenu)
6. [Cola > menú de canción](#6-cola--menú-de-canción--queuemenu)
7. [Letras](#7-letras)
8. [Diálogos auxiliares del reproductor](#8-diálogos-auxiliares-del-reproductor)
9. [Menús de elemento](#9-menús-de-elemento-canción-álbum-artista-lista)
10. [Inicio](#10-inicio)
11. [Búsqueda](#11-búsqueda)
12. [Descubrimiento](#12-descubrimiento-explorar-géneros-novedades-podcasts)
13. [Historial, Estadísticas, Cuenta, Sesión](#13-historial-estadísticas-cuenta-sesión)
14. [Escuchar juntos y chat](#14-escuchar-juntos-y-chat)
15. [Reconocer música](#15-reconocer-música)
16. [Modo Ambiente](#16-modo-ambiente)
17. [Biblioteca, Artista, Álbum y Listas](#17-biblioteca-artista-álbum-y-listas-de-reproducción)
18. [Ajustes](#18-ajustes) — 31 pantallas

**Las cuatro listas que el rediseño necesita**

19. [**Recuento total**](#19-recuento-total) — cuántos controles hay exactamente
20. [**Controles condicionales**](#20-controles-condicionales--los-más-fáciles-de-perder) — los que no salen en una captura normal
21. [**Gestos**](#21-gestos--lo-que-ningún-mockup-puede-enseñar) — pulsación larga, deslizar, arrastrar, doble toque
22. [Diálogos y hojas modales](#22-diálogos-y-hojas-modales-apéndice)
23. [**Controles muertos y placebos**](#23-controles-muertos-pantallas-inalcanzables-y-placebos)
24. [Lo que no se ha podido determinar](#24-lo-que-no-se-ha-podido-determinar)
25. [Anexo: widgets, mosaico, atajos, notificación y Android Auto](#25-anexo--superficies-fuera-de-la-interfaz-compose)

---

# 1. ESQUELETO DE LA APP Y MAPA DE NAVEGACIÓN

## 1.1 Cadena de arranque (qué envuelve a qué)

`MainActivity.onCreate` → `setContent` (`app/src/main/kotlin/com/music/echo/MainActivity.kt:568-582`):

```
TermsGate            (legal/TermsScreens.kt:99)     ← bloquea TODA la app si no se han aceptado los términos
  └─ LicenseGate     (license/LicenseGate.kt:19)    ← bloquea TODA la app según el estado de la licencia
       └─ echomusicApp                              (MainActivity.kt:588)
            └─ Scaffold
                 ├─ topBar    = TopAppBar                     (MainActivity.kt:1224-1347)
                 ├─ bottomBar = BottomSheetPlayer + FloatingNavigationToolbar   (móvil, :1375-1436)
                 │              o solo BottomSheetPlayer                        (tablet/TV, :1437-1490)
                 └─ content   = [ AppNavigationRail? | NowPlayingSidePanel? | NavHost | NowPlayingSidePanel? ]
                 + BottomSheetMenu (:1663) + BottomSheetPage (:1668) + capa PiP (:1677) + 5 diálogos
```

**Consecuencia para el rediseño:** el reproductor NO es una pantalla del `NavHost`. Es una hoja
(`BottomSheet`) que vive en el `bottomBar` del `Scaffold` y se superpone a cualquier pantalla.
Cambiar eso cambia el esqueleto entero.

## 1.2 Destinos de la barra inferior

Definidos en `ui/screens/Screens.kt:17-47`. La etiqueta solo se dibuja en el ítem **seleccionado**
(`ui/component/FloatingNavigationToolbar.kt:131`, `:451-486`).

| Orden | Ruta | Etiqueta (ES) | Icono inactivo / activo | archivo:línea |
|---|---|---|---|---|
| 1 | `home` | **Inicio** | `home_outlined` / `home_filled` | `ui/screens/Screens.kt:17-22` |
| 2 | `search_input` | **Buscar** | `search` / `search` (idéntico) | `ui/screens/Screens.kt:24-29` |
| 3 | `listen_together` | **Juntos** | `group_outlined` / `group_filled` | `ui/screens/Screens.kt:31-36` |
| 4 | `library` | **Biblioteca** | `library_music_outlined` / `library_music_filled` | `ui/screens/Screens.kt:38-43` |

**Ojo:** «Juntos» solo aparece en la barra si el ajuste *«Escuchar juntos en la barra superior»* está
**desactivado**. Por defecto está ACTIVADO, así que **la barra que ve el dueño hoy tiene 3 ítems, no 4**.

## 1.3 Personalización del esqueleto que el usuario SÍ puede cambiar

| Ajuste (ES) | Efecto | Preferencia | Se define en | Se aplica en |
|---|---|---|---|---|
| **Escuchar juntos en la barra superior** | Mueve «Juntos» de la barra inferior a un icono del top bar (barra de 3 ítems) | `ListenTogetherInTopBarKey` (ON por defecto) | `ui/screens/settings/AppearanceSettings.kt:1818-1819` | `MainActivity.kt:792-799`, icono `:1273-1284` |
| **Pestaña abierta predeterminada** (Inicio / Buscar / Biblioteca) | Cambia el destino de arranque | `DefaultOpenTabKey` (HOME) | `ui/screens/settings/AppearanceSettings.kt:1747-1754` | `MainActivity.kt:801-803`, `:1557-1562` |
| **Vista dividida estilo Spotify** | Fuerza rail lateral + panel a cualquier ancho | `ForceSplitViewKey` | `ui/screens/settings/PerformanceSettings.kt:147` | `ui/utils/TvUi.kt:60-63`, `:98-101` |
| **Mostrar el panel del reproductor** | Muestra/oculta `NowPlayingSidePanel` | `ShowNowPlayingPanelKey` (ON) | `ui/screens/settings/PerformanceSettings.kt:200` | `MainActivity.kt:879`, `:900-903` |
| **Panel del reproductor a la izquierda** | Mueve el panel al lado izquierdo | `SidePanelOnLeftKey` (OFF) | `ui/screens/settings/PerformanceSettings.kt:174` | `MainActivity.kt:878`, `:1540`, `:1648` |

**No configurable:** el orden de los destinos, sus iconos, ni añadir o quitar otros.

**Barra ↔ rail automático** (`MainActivity.kt:867-870`): el rail sustituye a la barra inferior cuando
la ventana es apaisada o ≥ 600 dp de ancho, salvo en la pantalla de Búsqueda y en el Modo Ambiente.

## 1.4 Las 70 rutas registradas

Todas en `app/src/main/kotlin/com/music/echo/ui/screens/NavigationBuilder.kt`.

| # | Ruta | Pantalla | Registro | ¿Alcanzable? |
|---|---|---|---|---|
| 1 | `home` | `HomeScreen` | `:76` | sí (barra/rail, arranque) |
| 2 | `search_input` | `SearchScreen` | `:80` | sí (barra/rail, atajo de app) |
| 3 | `library` | `LibraryScreen` | `:96` | sí (barra/rail, atajo de app) |
| 4 | `listen_together` | `ListenTogetherScreen` | `:100` | sí (top bar / barra) |
| 5 | `listen_together_from_topbar` | `ListenTogetherScreen` (con top bar) | `:105` | ⚠️ **NO — ruta muerta** |
| 6 | `listen_together/chat` | `CommentTogetherScreen` | `:110` | sí (`ListenTogetherScreen.kt:736`) |
| 7 | `history` | `HistoryScreen` | `:114` | sí (`MainActivity.kt:1286`) |
| 8 | `local_songs` | `LocalSongScreen` | `:118` | sí (Biblioteca Mix) |
| 9 | `favorite_albums` | `FavoriteAlbumsScreen` | `:122` | sí (Biblioteca Mix) |
| 10 | `release_radar` | `ReleaseRadarScreen` | `:126` | sí (Biblioteca Mix) |
| 11 | `stats` | `StatsScreen` | `:130` | sí (`SettingsScreen.kt:293`) |
| 12 | `mood_and_genres` | `MoodAndGenresScreen` | `:134` | sí (Inicio) |
| 13 | `account` | `AccountScreen` | `:138` | sí (`HomeScreen.kt:1881`) |
| 14 | `new_release` | `NewReleaseScreen` | `:142` | ⚠️ **NO — ruta muerta** |
| 15 | `browse/{browseId}` | `BrowseScreen` | `:148` | sí (Inicio, deep link) |
| 16 | `search/{query}` | `OnlineSearchResult` | `:163` | sí (25 sitios) |
| 17 | `album/{albumId}` | `AlbumScreen` | `:194` | sí (~35 sitios + deep links) |
| 18 | `artist/{artistId}` | `ArtistScreen` | `:205` | sí (~45 sitios + deep links) |
| 19 | `artist/{id}/songs` | `ArtistSongsScreen` | `:216` | sí |
| 20 | `artist/{id}/albums` | `ArtistAlbumsScreen` | `:227` | sí |
| 21 | `artist/{id}/items?...` | `ArtistItemsScreen` | `:238` | sí |
| 22 | `artist/section_buffer` | `ArtistAlbumsGridScreen` | `:257` | sí |
| 23 | `online_playlist/{id}` | `OnlinePlaylistScreen` | `:261` | sí (+ widget, deep links) |
| 24 | `local_playlist/{id}` | `LocalPlaylistScreen` | `:272` | sí (+ widget) |
| 25 | `auto_playlist/{playlist}` | `AutoPlaylistScreen` | `:283` | sí (`liked`/`downloaded`/`exported`/`uploaded`) |
| 26 | `cache_playlist/{playlist}` | `CachePlaylistScreen` | `:294` | sí (`cached`) |
| 27 | `top_playlist/{top}` | `TopPlaylistScreen` | `:305` | sí (+ widget) |
| 28 | `youtube_browse/{id}?params=` | `YouTubeBrowseScreen` | `:316` | sí |
| 29 | `settings` | `SettingsScreen` | `:332` | sí |
| 30 | `settings/update` | `UpdateSettings` | `:336` | sí |
| 31 | `settings/accounts` | `AccountsScreen` | `:342` | sí |
| 32 | `settings/lastfm` | `LastFMSettingsScreen` | `:346` | sí |
| 33 | `settings/qobuz` | `QobuzSettingsScreen` | `:350` | sí |
| 34 | `settings/appearance` | `AppearanceSettings` | `:354` | sí |
| 35 | `settings/appearance/theme` | `ThemeScreen` | `:358` | sí |
| 36 | `settings/appearance/liquidglass` | `GlassEffectSettings` | `:362` | sí (solo si el dispositivo es elegible) |
| 37 | `settings/content` | `ContentSettings` | `:366` | sí |
| 38 | `uptime` | `UptimeScreen` | `:370` | sí (`ContentSettings.kt:1361`) |
| 39 | `settings/content/romanization` | `RomanizationSettings` | `:374` | sí |
| 40 | `settings/ai` | `AiSettings` | `:378` | sí |
| 41 | `settings/player` | `PlayerSettings` | `:382` | sí |
| 42 | `settings/youtube_decryption` | `YoutubeDecryptionSettings` | `:386` | sí |
| 43 | `settings/sound` | `SoundSettings` | `:390` | sí (+ menú del reproductor) |
| 44 | `settings/performance` | `PerformanceSettings` | `:394` | sí |
| 45 | `settings/sound/autoeq` | `AutoEqScreen` | `:398` | sí |
| 46 | `settings/storage?...` | `StorageSettings` | `:402` | sí |
| 47 | `settings/equalizer` | `AxionEqScreen` | `:420` | sí (Ajustes, chip «Audio» del reproductor, menú de la cola) |
| 48 | `settings/privacy` | `PrivacySettings` | `:424` | sí |
| 49 | `settings/backup_restore` | `BackupAndRestore` | `:428` | sí |
| 50 | `settings/spotify_import?...` | `SpotifyImportScreen` | `:435` | sí |
| 51 | `migration` | `MigrationScreen` | `:451` | sí |
| 52 | `migration/tidal` | `MigrationTidalScreen` | `:455` | sí |
| 53 | `migration/apple` | `MigrationAppleScreen` | `:459` | sí |
| 54 | `settings/ytm_sync?...` | `YtmSyncScreen` | `:463` | sí |
| 55 | `settings/integrations/listen_together` | `ListenTogetherSettings` | `:474` | sí |
| 56 | `settings/about` | `AboutScreen` | `:478` | sí |
| 57 | `settings/terms` | `TermsScreen` | `:482` | sí (Acerca de) |
| 58 | `settings/logs` | `LogsScreen` | `:486` | sí |
| 59 | `update` | `UpdateScreen` | `:490` | sí |
| 60 | `login` | `LoginScreen` | `:494` | sí |
| 61 | `onboarding_artists` | `OnboardingArtistsScreen` | `:498` | sí (solo primera ejecución) |
| 62 | `onboarding_genres` | `OnboardingGenresScreen` | `:502` | sí |
| 63 | `onboarding_spotify` | `OnboardingSpotifyScreen` | `:506` | sí |
| 64 | `onboarding_youtube` | `OnboardingYouTubeScreen` | `:510` | sí |
| 65 | `podcasts?feedUrl=` | `PodcastScreen` | `:514` | sí |
| 66 | `recognition?autoStart=` | `RecognitionScreen` | `:527` | sí (FAB micrófono, tile, widget) |
| 67 | `recognition_history` | `RecognitionHistoryScreen` | `:542` | sí |
| 68 | `settings/changelog` | `ChangelogScreen` | `:545` | sí |
| 69 | `settings/commits` | `CommitScreen` | `:548` | ⚠️ **NO — ruta muerta** |
| 70 | `ambient_mode` | `AmbientModeScreen` | `:553` | sí (menú de la cola) |

## 1.5 Barra superior del esqueleto

Visible solo en `home`, `library` y `listen_together` — nunca en Ajustes (`MainActivity.kt:1043-1050`).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Wordmark «AURA HI-RES» | Barra superior | `MainActivity.kt:1226-1258` | informativo | constante | solo en `home` |
| Título de pantalla («Buscar» / «Biblioteca» / «Juntos») | Barra superior | `MainActivity.kt:1128-1134`, `:1259-1267` | informativo | constante | en el resto de pantallas principales |
| **Juntos** | Barra superior | `MainActivity.kt:1273-1284` | navegación | ocasional | solo si `ListenTogetherInTopBar` ON (por defecto sí) |
| **Historial** | Barra superior | `MainActivity.kt:1285-1292` | navegación | diaria | solo si no está pausado el historial o hay eventos |
| **Modo sin conexión** *(icono nube/offline + snackbar)* | Barra superior | `MainActivity.kt:1293-1308` | conmutador | ocasional | siempre |
| **Cuenta** *(avatar remoto o engranaje)* → `SettingDialoge` | Barra superior | `MainActivity.kt:1309-1327` | navegación | diaria | siempre; avatar solo si hay imagen de cuenta |

## 1.6 Barra inferior flotante

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Inicio** | Barra flotante | `ui/component/FloatingNavigationToolbar.kt:366-487` · `MainActivity.kt:1402` | navegación | constante | siempre |
| **Buscar** | Barra flotante | ídem | navegación | constante | siempre |
| **Juntos** | Barra flotante | ídem | navegación | diaria | solo si `ListenTogetherInTopBar` OFF |
| **Biblioteca** | Barra flotante | ídem | navegación | constante | siempre |
| **Reconocimiento** *(FAB micrófono)* → `recognition?autoStart=true` | Barra flotante > FAB | `ui/component/FloatingNavigationToolbar.kt:314-330` · handler `MainActivity.kt:929-940` | acción primaria | ocasional | siempre |
| Píldora deslizante del ítem activo | Barra flotante | `ui/component/FloatingNavigationToolbar.kt:222-252` | informativo | constante | siempre |

**Comportamiento oculto:** tocar el ítem YA seleccionado **no navega** — hace *scroll-to-top* de la
pantalla y resetea la barra superior (`MainActivity.kt:1358-1362`, `:1503-1507`).

## 1.7 Rail lateral (pantalla ancha / TV / coche)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Inicio / Buscar / Juntos / Biblioteca *(solo icono, sin etiqueta)* | Rail lateral | `ui/component/AppNavigation.kt:105-121` | navegación | constante | pantalla ancha o apaisada, fuera de `update` |
| **Mantener pulsado en «Buscar» → Reconocimiento** | Rail lateral | `ui/component/AppNavigation.kt:80-103` · handler `MainActivity.kt:1520-1527` | acción secundaria | rara | **solo en el rail — la barra inferior NO tiene este gesto** |
| Anillo de foco D-pad | Rail lateral | `ui/utils/TvUi.kt:142-154` | informativo | — | solo TV/coche |

## 1.8 Hoja de cuenta (`SettingDialoge`, se abre desde el avatar)

**Todo el texto está escrito a mano en Kotlin.** Hay una mezcla de idiomas (dos grupos en inglés).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| «Aura Hi-Res Player» *(hardcoded)* | Hoja de cuenta > cabecera | `ui/screens/SettingDialoge.kt:88-96` | informativo | constante | siempre |
| Cerrar ✕ *(contentDescription «Close», hardcoded en inglés)* | Hoja de cuenta > cabecera | `ui/screens/SettingDialoge.kt:98-108` | acción secundaria | diaria | siempre |
| Grupo **«Cuenta»** *(hardcoded)* | Hoja de cuenta | `ui/screens/SettingDialoge.kt:115` | informativo | — | solo sin sesión |
| **«Iniciar sesión»** *(hardcoded)* → `login` | Hoja de cuenta | `ui/screens/SettingDialoge.kt:119-122` | navegación | rara | solo sin sesión |
| Grupo **«Preferences»** *(hardcoded, en inglés)* | Hoja de cuenta | `ui/screens/SettingDialoge.kt:129` | informativo | — | solo con sesión |
| **«Usar la cuenta para explorar»** *(hardcoded)* + interruptor | Hoja de cuenta | `ui/screens/SettingDialoge.kt:132-150` | conmutador | rara | solo con sesión |
| **«Sincronización con YouTube Music»** *(hardcoded)* + interruptor | Hoja de cuenta | `ui/screens/SettingDialoge.kt:151-162` | conmutador | rara | solo con sesión |
| Grupo **«App»** *(hardcoded)* | Hoja de cuenta | `ui/screens/SettingDialoge.kt:168` | informativo | — | siempre |
| **«Ajustes»** *(hardcoded)* → `settings` | Hoja de cuenta | `ui/screens/SettingDialoge.kt:171-175` | navegación | diaria | siempre |
| **«Acerca de»** *(hardcoded)* + número de versión → `settings/about` | Hoja de cuenta | `ui/screens/SettingDialoge.kt:176-181` | navegación | rara | siempre |

## 1.9 Diálogos del esqueleto (arranque)

### `WelcomeDialog` — bienvenida y novedades
Se muestra en la primera ejecución **y en cada actualización** (`MainActivity.kt:1072-1076`). **Todo el texto es hardcoded en español.**

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| «¡Bienvenido a AURA HI-RES» + «Tu música, tu sonido, tu estilo.» + chip de versión | Bienvenida > cabecera | `ui/screens/WelcomeDialog.kt:184-251` | informativo | rara | siempre que se muestre |
| «Todo lo que puedes hacer» + **15 filas de características** (Música ilimitada, Audio de alta calidad, Transiciones suaves, Ecualizador a tu medida, Letras sincronizadas, Aleatorio que no repite, Descubrimiento con IA, Tu biblioteca de siempre, Migra desde otros servicios, Sin conexión y ahorro de datos, Reconocer canción y buscar por voz, Escuchar juntos, Video/fondos animados y temas, En el coche/la tele/pantalla de inicio, Podcasts y música local) | Bienvenida > cuerpo | `ui/screens/WelcomeDialog.kt:75-165` | informativo | rara | siempre |
| **«Cerrar»** | Bienvenida | `ui/screens/WelcomeDialog.kt:167-177` · efecto `MainActivity.kt:1756-1769` | acción primaria | rara | siempre; en la PRIMERA ejecución encadena con `onboarding_artists` |

### `BackgroundReliabilityDialog` — batería y arranque automático
Aparece **una sola vez en la vida de la instalación**, 1,5 s tras arrancar, solo en fabricantes agresivos sin exención de batería (`MainActivity.kt:1078-1090`). **Todo hardcoded en español.**

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| «Que Aura no se corte en segundo plano» + cuerpo explicativo | Diálogo de fiabilidad | `ui/screens/BackgroundReliabilityDialog.kt:35`, `:38-42` | informativo | rara | siempre que se muestre |
| **«1) Permitir batería sin restricción»** | Diálogo de fiabilidad | `ui/screens/BackgroundReliabilityDialog.kt:51-55` · handler `MainActivity.kt:1773-1778` | acción primaria | rara | igual |
| **«2) Activar "Inicio automático"»** | Diálogo de fiabilidad | `ui/screens/BackgroundReliabilityDialog.kt:43-48` · handler `MainActivity.kt:1779-1783` | acción secundaria | rara | igual |
| **«Ahora no»** | Diálogo de fiabilidad | `ui/screens/BackgroundReliabilityDialog.kt:56-60` | acción secundaria | rara | igual |

## 1.10 Puerta legal (`TermsGate`) — bloquea toda la app

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Wordmark «AURA HI-RES» *(hardcoded)* | Aceptación de términos | `legal/TermsScreens.kt:150-161` | informativo | rara | solo si la versión aceptada < versión actual de los términos |
| **Términos y condiciones** *(título)* | Aceptación de términos | `legal/TermsScreens.kt:163-168` | informativo | rara | igual |
| Documento completo, scrollable (renderizador markdown propio) | Aceptación de términos | `legal/TermsScreens.kt:171-177` · renderizador `:322-433` | informativo | rara | igual; el texto vive en `assets/legal/TERMINOS_Y_CONDICIONES.md` |
| **Salir** *(cierra la app)* | Aceptación de términos | `legal/TermsScreens.kt:185-193` | destructiva | rara | igual |
| **Aceptar y continuar** | Aceptación de términos | `legal/TermsScreens.kt:195-215` | acción primaria | rara | igual; recibe el foco inicial en TV/coche |
| Volver *(solo icono; **pulsación larga = volver a la pantalla principal**)* | Ajustes > Acerca de > Términos | `legal/TermsScreens.kt:257-264` | navegación | rara | siempre |
| **«Aceptados el %1$s (términos v%2$d)»** | Ajustes > Acerca de > Términos | `legal/TermsScreens.kt:290-298` | informativo | rara | solo si ya se aceptaron |

## 1.11 Puerta de licencia (`LicenseGate`) — bloquea toda la app

Es la superficie que sostiene el negocio. **Todo el texto está hardcoded en español**, salvo el precio.
Estados posibles: `FIRST_RUN`, `DEMO`, `SUBSCRIPTION_ACTIVE`, `DEMO_EXPIRED`, `SUBSCRIPTION_EXPIRED`,
`DEVICE_BLOCKED`, `NEEDS_CONNECTION` (`license/LicenseLogic.kt:71-87`).
Con `-Pnosub=true` la puerta se salta entera (`license/LicenseGate.kt:21-24`).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| «Verificando licencia…» | Carga a pantalla completa | `license/LicenseScreens.kt:187` | informativo | rara | primera ejecución, sin estado cacheado |
| «Bienvenido» / «Tu prueba terminó» *(subtítulo)* | Activación | `license/LicenseScreens.kt:200` | informativo | rara | según sea `FIRST_RUN` o `DEMO_EXPIRED` |
| «Prueba gratis 3 días o activa tu suscripción.» / «Tu demo de 3 días terminó…» | Activación | `license/LicenseScreens.kt:204` / `:202` | informativo | rara | igual |
| **«Ya me suscribí»** | Activación | `license/LicenseScreens.kt:207` | acción primaria | rara | siempre en esta pantalla |
| **«Probar gratis (3 días)»** | Activación | `license/LicenseScreens.kt:210-214` | acción primaria | rara | **solo en `FIRST_RUN`** |
| «Activar suscripción» + explicación | Alta de suscripción | `license/LicenseScreens.kt:282-283` | informativo | rara | siempre |
| **Clave de licencia** *(campo, teclado en mayúsculas)* | Alta de suscripción | `license/LicenseScreens.kt:290-291` | acción primaria | rara | siempre |
| **«Activar»** / «Verificando…» | Alta de suscripción | `license/LicenseScreens.kt:302-307` | acción primaria | rara | habilitado solo con la clave escrita |
| **«Suscribirme por $3.74/mes»** *(abre Gumroad en Chrome Custom Tab)* | Alta de suscripción | `license/LicenseScreens.kt:309-313` | acción primaria | rara | siempre |
| **«Volver»** | Alta de suscripción | `license/LicenseScreens.kt:323` | navegación | rara | siempre |
| «Licencia detectada, activando…» | Alta de suscripción | `license/LicenseScreens.kt:272` | informativo | rara | al volver a la app con una clave en el portapapeles |
| Mensajes de error: cancelada/vencida, clave en otro equipo, clave inválida, sin conexión | Alta de suscripción | `license/LicenseScreens.kt:243`, `:247`, `:251`, `:255` | informativo | rara | según el resultado |
| «Suscripción vencida» + **«Renovar en Gumroad»** + **«Ya pagué, reintentar»** | Renovación | `license/LicenseScreens.kt:335`, `:342`, `:344-358` | acción primaria | rara | solo `SUBSCRIPTION_EXPIRED` |
| «Conéctate a internet» + **«Reintentar»** | Sin conexión | `license/LicenseScreens.kt:365-368` | acción primaria | rara | solo `NEEDS_CONNECTION` |
| «Suscripción en otro equipo» + **«Reintentar»** + **«Suscribirme ($3.74/mes)»** | Equipo bloqueado | `license/LicenseScreens.kt:376-386` | acción primaria | rara | solo `DEVICE_BLOCKED` |

## 1.12 Onboarding (4 pasos, todo hardcoded en español)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| «Elige tus artistas favoritos» + «Mínimo 3…» | Onboarding 1/4 | `ui/screens/OnboardingArtistsScreen.kt:80-85` | informativo | rara | solo primera ejecución |
| **«Buscar artista»** *(campo)* | Onboarding 1/4 | `ui/screens/OnboardingArtistsScreen.kt:125-132` | acción primaria | rara | igual |
| 8 chips de sugerencia (Pop, Rock, Reggaetón, Hip-Hop, Electrónica, Latina, K-Pop, Cristiana) | Onboarding 1/4 | `ui/screens/OnboardingArtistsScreen.kt:60-62`, `:147-154` | acción secundaria | rara | solo con el campo vacío |
| Rejilla de artistas (círculo + check) | Onboarding 1/4 | `ui/screens/OnboardingArtistsScreen.kt:166-211` | conmutador | rara | solo con resultados |
| «Seleccionados: N (faltan M)» | Onboarding 1/4 | `ui/screens/OnboardingArtistsScreen.kt:91-98` | informativo | rara | siempre |
| **«Siguiente»** / «Guardando…» | Onboarding 1/4 | `ui/screens/OnboardingArtistsScreen.kt:100-114` | acción primaria | rara | habilitado a partir de 3 artistas |
| **«Omitir»** | Onboarding 1/4 | `ui/screens/OnboardingArtistsScreen.kt:116-120` | acción secundaria | rara | siempre |
| «¿Qué géneros te gustan?» + **24 chips de género** | Onboarding 2/4 | `ui/screens/OnboardingGenresScreen.kt:41-45`, `:104-112` | conmutador | rara | siempre |
| **«Continuar»** / **«Omitir»** | Onboarding 2/4 | `ui/screens/OnboardingGenresScreen.kt:83-88` / `:89-92` | acción primaria / secundaria | rara | siempre |
| «Migra tu Spotify» + **«Conectar Spotify y elegir qué migrar»** | Onboarding 3/4 | `ui/screens/OnboardingSpotifyScreen.kt:43`, `:48-51` | acción primaria | rara | siempre |
| **«Siguiente: YouTube Music»** | Onboarding 3/4 | `ui/screens/OnboardingSpotifyScreen.kt:53-58` | acción secundaria | rara | siempre |
| «Migra tu YouTube Music» + **«Sincronizar YouTube Music»** | Onboarding 4/4 | `ui/screens/OnboardingYouTubeScreen.kt:45`, `:50-55` | acción primaria | rara | siempre |
| **«Comenzar»** *(limpia todo el onboarding y va a Inicio)* | Onboarding 4/4 | `ui/screens/OnboardingYouTubeScreen.kt:57-64` | acción primaria | rara | siempre |

## 1.13 Migración (pantalla de 6 fases)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **«Migrar lista»** + volver *(pulsación larga = ir a la principal)* | Migración > barra | `ui/screens/migration/MigrationScreen.kt:65-81` | navegación | rara | siempre |
| Aviso «Inicia sesión en YouTube Music…» + **«Iniciar sesión en YouTube Music»** | Migración > fase Elegir | `ui/screens/migration/MigrationScreen.kt:229-235` | acción primaria | rara | solo sin sesión de YouTube; **oculta todas las fuentes** |
| **«Archivo (CSV, M3U, JSPF)»** | Migración > fase Elegir | `ui/screens/migration/MigrationScreen.kt:249-254` | navegación | rara | solo con sesión |
| **«Deezer»** | Migración > fase Elegir | `ui/screens/migration/MigrationScreen.kt:255-260` | navegación | rara | igual |
| **«Tidal»** | Migración > fase Elegir | `ui/screens/migration/MigrationScreen.kt:261-266` | navegación | rara | igual |
| **«Apple Music»** | Migración > fase Elegir | `ui/screens/migration/MigrationScreen.kt:267-272` | navegación | rara | igual |
| Diálogo Deezer: campo de enlace + **«Continuar»** / **«Cancelar»** | Migración > Deezer | `ui/screens/migration/MigrationScreen.kt:154-189` | acción primaria | rara | al elegir Deezer |
| Fase Colección: filas de colección + **«Elegir otro origen»** | Migración > Colección | `ui/screens/migration/MigrationScreen.kt:336-393`, `:324-326` | navegación | rara | solo con perfil Deezer |
| Fase Confirmar: recuento, destino, avisos + **«Cancelar»** / **«Continuar»** | Migración > Confirmar | `ui/screens/migration/MigrationScreen.kt:420-464` | acción primaria | rara | fase CONFIRM |
| Fase Migrando: «%1$d de %2$d» + pista actual + barra | Migración > Migrando | `ui/screens/migration/MigrationScreen.kt:484-510` | informativo | rara | fase RUNNING |
| Fase Hecho: recuentos + **«Revisar %d ambiguas»** / **«Abrir en Biblioteca»** / **«Migrar otra»** | Migración > Hecho | `ui/screens/migration/MigrationScreen.kt:584-613` | acción primaria | rara | fase DONE |
| Revisión de ambiguas: candidatas con radio + **«Omitir»** + **«Añadir seleccionadas»** | Migración > Revisión | `ui/screens/migration/MigrationScreen.kt:666-793` | acción primaria | rara | solo si hay ambiguas |
| Diálogo «Error de migración» + **«Aceptar»** | Migración | `ui/screens/migration/MigrationScreen.kt:193-211` | informativo | rara | fase ERROR |
| **Tidal**: campo Client ID, **«Abrir panel»**, **«Guardar»**, **«Cambiar»**, **«Iniciar sesión en Tidal»**, **«Cerrar sesión de Tidal»**, **«Recargar listas»**, campo de enlace, **«Importar»** | Migración > Tidal | `ui/screens/migration/MigrationTidalScreen.kt:158-370` | acción primaria | rara | según estado de sesión |
| **Apple Music**: guía de pasos + **«Abrir privacy.apple.com»** + limitaciones | Migración > Apple | `ui/screens/migration/MigrationAppleScreen.kt:77-152` | navegación | rara | siempre |

## 1.14 Importación desde Spotify

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **«Importar desde Spotify»** + volver *(long-press = principal)* | Spotify > barra | `ui/screens/SpotifyImportScreen.kt:102-110` | navegación | ocasional | siempre |
| **«Continuar (siguiente paso)»** *(hardcoded)* | Spotify > barra inferior | `ui/screens/SpotifyImportScreen.kt:117-124` | acción primaria | rara | solo dentro del onboarding |
| **«Conectar Spotify»** → hoja WebView de login | Spotify | `ui/screens/SpotifyImportScreen.kt:144-150` | acción primaria | rara | solo sin sesión |
| **«Importar lista por link»** | Spotify | `ui/screens/SpotifyImportScreen.kt:153-159`, `:189-195` | acción primaria | ocasional | con y sin sesión |
| «Conectado como %1$s» | Spotify | `ui/screens/SpotifyImportScreen.kt:163-176` | informativo | ocasional | solo con sesión |
| **«Seleccionar listas de Spotify»** | Spotify | `ui/screens/SpotifyImportScreen.kt:177-188` | navegación | ocasional | habilitado solo si hay fuentes |
| **«Importar seleccionadas»** | Spotify | `ui/screens/SpotifyImportScreen.kt:196-202` | acción primaria | ocasional | habilitado solo si se puede importar |
| **«Actualizar biblioteca de Spotify»** | Spotify | `ui/screens/SpotifyImportScreen.kt:203-209` | acción secundaria | ocasional | habilitado sin importación en curso |
| **«Finalizar sesión»** | Spotify | `ui/screens/SpotifyImportScreen.kt:210-215` | destructiva | rara | solo con sesión |
| **«Frecuencia»** *(Diaria / Semanal / Desactivada)* | Spotify > Sincronización programada | `ui/screens/SpotifyImportScreen.kt:230-236` | navegación | rara | solo con sesión |
| **«Listas a sincronizar»** | Spotify > Sincronización programada | `ui/screens/SpotifyImportScreen.kt:237-248` | navegación | rara | habilitado solo si hay fuentes |
| Hoja de login (WebView), diálogo de enlace, hoja de selección (**Limpiar** / **Seleccionar todo** / casillas), diálogo de frecuencia, hoja de listas programadas, diálogos de error/resumen/progreso (**Segundo plano** / **Cancelar**) | Spotify > diálogos | `ui/screens/SpotifyImportScreen.kt:266-298`, `:314-344`, `:375-398`, `:402-503`, `:546-630`, `:633-687`, `:788-854` | acción primaria | ocasional | según estado |

## 1.15 Pantalla de fallo (`CrashActivity`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **«La app falló»** | Fallo > barra | `ui/screens/CrashActivity.kt:129` | informativo | rara | solo tras un fallo |
| **«Copiar registros»** | Fallo > barra | `ui/screens/CrashActivity.kt:134-139` | acción secundaria | rara | siempre |
| **«Cerrar»** *(cierra la app)* | Fallo > barra | `ui/screens/CrashActivity.kt:140-145` | destructiva | rara | siempre |
| **«Compartir registros»** *(FAB; genera `aura_crash_<ts>.txt`)* | Fallo | `ui/screens/CrashActivity.kt:152-165` | acción primaria | rara | siempre |
| Bloque monoespaciado con el registro | Fallo > cuerpo | `ui/screens/CrashActivity.kt:184-201` | informativo | rara | siempre |

## 1.16 Actualizaciones (`UpdateScreen`, `ChangelogScreen`)

> ⚠️ **`app/src/main/res/values/updater_strings.xml` (77 cadenas) NO tiene traducción al español.**
> Toda esta pantalla se ve en inglés.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| «New update <versión>» / «Check for updates» *(en inglés)* | Actualizar > barra | `echomusic/updater/echomusicupdater.kt:245-258` | informativo | ocasional | según haya o no actualización |
| **Cancelar** *(volver)* | Actualizar > barra | `echomusic/updater/echomusicupdater.kt:264-270` | navegación | ocasional | siempre |
| **«Check for update»** *(en inglés)* | Actualizar | `echomusic/updater/echomusicupdater.kt:295-300` | acción primaria | ocasional | solo si no hay actualización pendiente |
| **«Later»** *(en inglés)* | Actualizar | `echomusic/updater/echomusicupdater.kt:308-314` | acción secundaria | ocasional | solo con actualización disponible |
| **«Update» / «Install» / «NN%»** *(en inglés)* | Actualizar | `echomusic/updater/echomusicupdater.kt:315-374` | acción primaria | ocasional | solo con actualización disponible |
| Permiso «instalar apps desconocidas» | Actualizar | `echomusic/updater/echomusicupdater.kt:328-333` | navegación | rara | Android 8+ sin el permiso |
| Metadatos (fecha, tamaño), imagen, descripción con enlaces, changelog agregado, barra de descarga | Actualizar > cuerpo | `echomusic/updater/echomusicupdater.kt:471-560` | informativo | ocasional | solo con actualización disponible |
| **«Registro de cambios»** + chips de versión + tirar para refrescar + badge «En caché» | Registro de cambios | `echomusic/changelog/changelogscreen.kt:278-453` | navegación | rara | siempre |
| Diálogo «Información de la actualización» + **«Aceptar»** | Ajustes > Actualizaciones > ⓘ | `echomusic/component/UpdateInfoDialog.kt:92-166` | informativo | rara | al tocar el icono ⓘ |

# 2. REPRODUCTOR

Es la pantalla que se rediseña y la que más profundidad oculta tiene. Se divide aquí en 13 superficies
porque el reproductor **cambia de forma** según ajustes (`useNewPlayerDesign`, estilo de barra, diseño del
mini reproductor), según el contenido (audio / vídeo / canvas), según la orientación y según el dispositivo
(móvil / tablet / TV / coche). Un mockup solo puede enseñar una de esas formas.

## 2.1 Reproductor > cabecera

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Miniatura 56 dp → reproducir/pausar *(solo icono, sin contentDescription)* | Reproductor > cabecera, modo letras | `app/.../ui/player/Player.kt:1633` | acción primaria | ocasional | solo con letras abiertas (`showInlineLyrics`) **y** `isFullScreen` **y** ajuste `EnableLyricsThumbnailPlayPause` ON **y** `!hidePlayerThumbnail` |
| Pantalla completa de letras *(solo icono `fullscreen`)* | Reproductor > cabecera | `app/.../ui/player/Player.kt:1698` | conmutador | ocasional | solo si `showInlineLyrics` **y** `useNewPlayerDesign` (por defecto true) |
| Descargar / quitar descarga *(solo icono `download`/`offline`)* | Reproductor > cabecera | `app/.../ui/player/Player.kt:1714` | conmutador | diaria | solo si `!showInlineLyrics` **y** `useNewPlayerDesign` |
| Menú de letras *(solo icono `more_horiz`)* | Reproductor > cabecera, modo letras | `app/.../ui/player/Player.kt:1782` | navegación | ocasional | solo si `showInlineLyrics` **y** `useNewPlayerDesign` |
| Pantalla completa de letras — diseño antiguo | Reproductor > cabecera | `app/.../ui/player/Player.kt:1828` | conmutador | ocasional | solo si `showInlineLyrics` **y** `!useNewPlayerDesign` |
| Menú de letras — diseño antiguo | Reproductor > cabecera | `app/.../ui/player/Player.kt:1855` | navegación | ocasional | solo si `showInlineLyrics` **y** `!useNewPlayerDesign` |

## 2.2 Reproductor > título y artista

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Título de la canción → abre el álbum | Reproductor > bloque de título | `app/.../ui/player/Player.kt:1926` | navegación | ocasional | solo si se resuelve un álbum (`rememberResolvedAlbum` ≠ null); oculto en vídeo inmersivo |
| Copiar título *(mantener pulsado; Toast «Título copiado»)* | Reproductor > bloque de título | `app/.../ui/player/Player.kt:1932` | acción secundaria | rara | siempre que haya título |
| Nombre del artista → abre el artista | Reproductor > bloque de título | `app/.../ui/player/Player.kt:1958` | navegación | diaria | solo si algún artista tiene id y la lista no está vacía |
| Copiar artista *(mantener pulsado; Toast «Artista copiado»)* | Reproductor > bloque de título | `app/.../ui/player/Player.kt:1964` | acción secundaria | rara | siempre que haya artista |
| Cambiar a vídeo / volver a audio *(solo icono `videocam`/`music_note`)* | Reproductor > fin de la fila del título | `app/.../ui/player/Player.kt:1990` | conmutador | ocasional | solo si hay vídeo (`isVideoSong` o `podcastVideoUrl`) **y** (`!highPerfMode` **o** pantalla ancha/TV) |

## 2.3 Reproductor > fila de chips de acción

Fila con scroll horizontal y degradado en el borde derecho. **Todas las etiquetas están escritas a mano en Kotlin**, no en recursos: no son traducibles.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Me gusta** *(mitad izquierda de un pill dividido)* | Reproductor > chips | `app/.../ui/player/Player.kt:3655` (comp.) · llamada `:2036`/`:2042` | conmutador | diaria | siempre |
| **No me gusta** *(mitad derecha del pill)* | Reproductor > chips | `app/.../ui/player/Player.kt:3678` (comp.) · llamada `:2043` | conmutador | ocasional | siempre |
| **Agregar** *(hardcoded)* → diálogo de listas | Reproductor > chips | `app/.../ui/player/Player.kt:2047` | acción secundaria | diaria | siempre |
| **Compartir** *(hardcoded)* → chooser de Android | Reproductor > chips | `app/.../ui/player/Player.kt:2050` | acción secundaria | ocasional | siempre |
| **Descargar** *(hardcoded)* → descarga / cancela | Reproductor > chips | `app/.../ui/player/Player.kt:2064` | conmutador | diaria | siempre |
| **Mix** *(hardcoded)* → radio infinita (`startRadioSeamlessly`) | Reproductor > chips | `app/.../ui/player/Player.kt:2091` | acción primaria | diaria | siempre; el chip se rellena cuando `mixActive` |
| **Audio** *(hardcoded)* → ecualizador (`settings/equalizer`), colapsa el reproductor | Reproductor > chips | `app/.../ui/player/Player.kt:2095` | navegación | ocasional | siempre |
| **Más** *(hardcoded, icono `add`)* → abre `OldPlayerMenu` (§3) | Reproductor > chips | `app/.../ui/player/Player.kt:2109` | navegación | diaria | siempre |

## 2.4 Reproductor > barra de progreso, tiempos e indicadores

La barra tiene **cuatro implementaciones distintas** según el ajuste de estilo. Hoy es una preferencia visible del usuario.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Barra de progreso — estilo **DEFAULT** | Reproductor > línea de tiempo | `app/.../ui/player/Player.kt:2140` | acción primaria | constante | `SliderStyle = DEFAULT`; deshabilitada si `isListenTogetherGuest` |
| Barra ondulada **«squiggly»** | Reproductor > línea de tiempo | `app/.../ui/player/Player.kt:2178` (impl. `ui/component/SquigglySlider.kt:40`) | acción primaria | constante | `SliderStyle = WAVY` **y** `SquigglySlider` ON **y** sin Modo Rendimiento |
| Barra ondulada **M3 «wavy»** | Reproductor > línea de tiempo | `app/.../ui/player/Player.kt:2207` (impl. `ui/component/WavySlider.kt:37`) | acción primaria | constante | `SliderStyle = WAVY` **y** `SquigglySlider` OFF (o Modo Rendimiento) |
| Barra fina **SLIM** (engorda al tocarla) | Reproductor > línea de tiempo | `app/.../ui/player/Player.kt:2253` | acción primaria | constante | `SliderStyle = SLIM`; deshabilitada si `isListenTogetherGuest` |
| Tiempo transcurrido | Reproductor > bajo la barra, izq. | `app/.../ui/player/Player.kt:2305` | informativo | constante | siempre |
| Duración total | Reproductor > bajo la barra, der. | `app/.../ui/player/Player.kt:2448` | informativo | constante | solo si `duration != C.TIME_UNSET` |
| Chip **«Fundido cruzado»** (parpadea) | Reproductor > bajo la barra, centro | `app/.../ui/player/Player.kt:2333` | informativo | ocasional | solo mientras `isCrossfading` |
| Chip del temporizador (cuenta atrás) → abre el diálogo | Reproductor > bajo la barra, centro | `app/.../ui/player/Player.kt:2379` | navegación | rara | solo si `!useNewPlayerDesign` **y** temporizador activo |
| Códec / bitrate / **«Lossless»** | Reproductor > bajo la barra, centro | `app/.../ui/player/Player.kt:2419-2445` | informativo | ocasional | solo si `ShowCodecOnPlayer` ON y hay texto de formato |
| Visualizador de espectro | Reproductor > sobre la barra | `app/.../ui/player/Player.kt:2131` | informativo | rara | `SpectrumVisualizerEnabled` ON y sin throttling — ⚠️ **hoy no dibuja nada, ver §23** |

## 2.5 Reproductor > barra de transporte

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Anterior *(solo icono `skip_previous`)* | Reproductor > transporte, diseño nuevo | `app/.../ui/player/Player.kt:2513` | acción primaria | constante | `useNewPlayerDesign`; deshabilitado si `!canSkipPrevious` o invitado de Escuchar juntos |
| Reproducir / Pausar *(o Silenciar / Activar sonido como invitado)* | Reproductor > transporte, diseño nuevo | `app/.../ui/player/Player.kt:2536` | acción primaria | constante | `useNewPlayerDesign`; como invitado de Escuchar juntos silencia en vez de pausar; con Cast controla el dispositivo remoto; si `STATE_ENDED` reinicia |
| Siguiente *(solo icono `skip_next`)* | Reproductor > transporte, diseño nuevo | `app/.../ui/player/Player.kt:2591` | acción primaria | constante | `useNewPlayerDesign`; deshabilitado si `!canSkipNext` o invitado |
| Anterior *(icono `apple_skip_previous`)* | Reproductor > transporte, diseño antiguo | `app/.../ui/player/Player.kt:2650` | acción primaria | constante | `!useNewPlayerDesign` |
| Reproducir / Pausar / Reiniciar | Reproductor > transporte, diseño antiguo | `app/.../ui/player/Player.kt:2663` | acción primaria | constante | `!useNewPlayerDesign` |
| Siguiente *(icono `apple_skip_next`)* | Reproductor > transporte, diseño antiguo | `app/.../ui/player/Player.kt:2722` | acción primaria | constante | `!useNewPlayerDesign` |
| Volumen del sistema (deslizador) | Reproductor > bajo el transporte | `app/.../ui/player/Player.kt:2806` | acción secundaria | diaria | solo si `!useNewPlayerDesign` **y** `!hidePlayerSlider`; con Cast cambia el volumen remoto |
| Iconos de volumen mín./máx. | Reproductor > fila de volumen | `app/.../ui/player/Player.kt:2793` y `:2832` | informativo | diaria | igual que arriba |
| Nombre del dispositivo Bluetooth + icono (altavoz / auriculares / buds) | Reproductor > bajo el volumen | `app/.../ui/player/Player.kt:2852-2894` | informativo | ocasional | solo si `!useNewPlayerDesign` y hay dispositivo BT conectado |

## 2.6 Reproductor > portada y carrusel

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Carrusel de portadas *(anterior · actual · siguiente)* — deslizar cambia de canción | Reproductor > portada | `app/.../ui/player/Thumbnail.kt:525` (grid), `:529` (scroll), `:363-371` (salto) | acción primaria | constante | `SwipeThumbnail` ON **y** reproductor expandido **y** no invitado |
| **«Reproduciendo»** (cabecera de portada) | Reproductor > cabecera de portada | `app/.../ui/player/Thumbnail.kt:616` | informativo | constante | solo en retrato; con sala activa muestra *«Hosting Listen Together» / «Listening Together»* **(hardcoded, en inglés)** en `:610` |
| Origen de la reproducción (álbum o título de la cola) | Reproductor > cabecera de portada | `app/.../ui/player/Thumbnail.kt:621-635` | informativo | constante | solo si hay álbum o `queueTitle` |
| Placeholder con logo (miniatura oculta) | Reproductor > portada | `app/.../ui/player/Thumbnail.kt:786` (impl. `:953`) | informativo | rara | solo si `HidePlayerThumbnail` ON |
| Portada animada (canvas Apple/Tidal) | Reproductor > portada | `app/.../ui/player/Thumbnail.kt:938` · `ui/player/CanvasArtworkPlayer.kt:65` | informativo | ocasional | `CanvasThumbnailAnimation` ON, sin throttling, sin portada giratoria, es la canción actual y existe canvas |
| Indicador de carga de vídeo | Reproductor > sobre la portada | `app/.../ui/player/Thumbnail.kt:437` | informativo | ocasional | `videoMode` ON y todavía sin URL |
| Portada giratoria (forma trébol) | Reproductor > portada | `app/.../ui/player/Thumbnail.kt:774-780` | informativo | rara | `RotatingThumbnail` ON (limitado por rendimiento) |
| **«Reproducción fallida»** + mensaje + código + **«Reintentar»** | Reproductor > sobre la portada | `app/.../ui/player/Thumbnail.kt:457` → `ui/player/PlaybackError.kt:121` | acción primaria | rara | solo si `playerConnection.error != null` |
| Overlay **«−N segundos hacia atrás» / «+N segundos hacia adelante»** | Reproductor > centro de la portada | `app/.../ui/player/Thumbnail.kt:580` (impl. `:1036`) | informativo | ocasional | 1 s tras un doble toque lateral |

## 2.7 Reproductor > vídeo inmersivo (retrato)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Superficie de vídeo | Reproductor > vídeo retrato | `app/.../ui/player/Player.kt:3394` · impl. `ui/player/PlayerVideoSurface.kt:32` | informativo | ocasional | `videoMode` con URL y sin PiP |
| Volver a audio *(solo icono `music_note`, esquina inf. der.)* | Reproductor > vídeo retrato | `app/.../ui/player/Player.kt:3401` | conmutador | ocasional | solo con controles visibles y sin PiP |
| Título + artista sobre el vídeo | Reproductor > vídeo retrato | `app/.../ui/player/Player.kt:3356-3373` | informativo | ocasional | con controles visibles o en PiP |
| Controles completos superpuestos (los mismos chips + barra + transporte) | Reproductor > vídeo retrato | `app/.../ui/player/Player.kt:3440` | — | ocasional | con controles visibles y sin PiP |

## 2.8 Reproductor > vídeo a pantalla completa (apaisado)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Vídeo a pantalla completa (recortado, sin barras del sistema) | Reproductor > vídeo apaisado | `app/.../ui/player/Player.kt:2973` | informativo | ocasional | landscape real + `videoMode` con URL + sin PiP |
| Anterior *(solo icono)* | Reproductor > vídeo apaisado, barra inferior | `app/.../ui/player/Player.kt:3035` | acción primaria | ocasional | con controles visibles y sin PiP |
| Reproducir / Pausar *(solo icono)* | Reproductor > vídeo apaisado, barra inferior | `app/.../ui/player/Player.kt:3041` | acción primaria | ocasional | igual |
| Siguiente *(solo icono)* | Reproductor > vídeo apaisado, barra inferior | `app/.../ui/player/Player.kt:3049` | acción primaria | ocasional | igual |
| Salir del modo vídeo *(solo icono `music_note`)* | Reproductor > vídeo apaisado, barra inferior | `app/.../ui/player/Player.kt:3056` | conmutador | ocasional | igual |
| Título + artista (vista limpia PiP) | Reproductor > vídeo apaisado en PiP | `app/.../ui/player/Player.kt:2996-3011` | informativo | rara | solo en PiP |

## 2.9 Reproductor > canvas inmersivo al rotar (opcional)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Anterior *(solo icono)* | Reproductor > canvas apaisado | `app/.../ui/player/Player.kt:3121` | acción primaria | rara | hay canvas **y** `ImmersiveCanvasOnRotate` ON (por defecto OFF) **y** landscape **y** controles visibles |
| Reproducir / Pausar *(solo icono)* | Reproductor > canvas apaisado | `app/.../ui/player/Player.kt:3127` | acción primaria | rara | igual |
| Siguiente *(solo icono)* | Reproductor > canvas apaisado | `app/.../ui/player/Player.kt:3135` | acción primaria | rara | igual |

## 2.10 Reproductor > diseño ancho / dividido (tablet, TV, coche, plegable abierto)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Fila de la cola → salta a esa canción (o pausa/reanuda si ya es la actual) | Reproductor ancho > panel izquierdo | `app/.../ui/player/Player.kt:3736` · `:3775` | acción primaria | diaria | `isWideLayout` **y** sin letras **y** sin vídeo |
| Portada grande (máx. 420 dp) | Reproductor ancho > panel derecho | `app/.../ui/player/Player.kt:3218` | informativo | constante | solo pantalla ancha |
| Controles completos (mismo bloque `controlsContent`) | Reproductor ancho > panel derecho | `app/.../ui/player/Player.kt:3228` / `:3277` | — | constante | landscape real o pantalla ancha sin vídeo |
| Letras a pantalla partida | Reproductor apaisado > panel izquierdo | `app/.../ui/player/Player.kt:3250` | informativo | ocasional | `showInlineLyrics` en landscape/ancho |

## 2.11 Reproductor > botón Cast (superpuesto)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Cast** / **Stop casting** *(hardcoded, en inglés — contentDescription)* | Reproductor > esquina sup. der., todos los layouts | colocación `app/.../ui/player/Player.kt:3549` · onClick `app/src/gms/kotlin/com/music/echo/ui/component/CastButton.kt:138` | navegación | ocasional | **solo en la compilación `gms`** (en FOSS es un stub vacío), `EnableGoogleCast` ON, SDK disponible, sin PiP, cola no expandida, controles visibles |
| Selector de dispositivos Cast (hoja) | Reproductor > Cast | `app/src/gms/kotlin/com/music/echo/ui/component/CastButton.kt:186` | navegación | ocasional | igual |

## 2.12 Mini reproductor

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Tocar la barra → abrir el reproductor completo | Mini reproductor (toda la barra) | `app/.../ui/component/BottomSheet.kt:136-139` · contenido `ui/player/MiniPlayer.kt:165` | navegación | constante | siempre que haya canción |
| Anterior *(solo icono)* | Mini reproductor, diseño nuevo | `app/.../ui/player/MiniPlayer.kt:440` | acción primaria | constante | `UseNewMiniPlayerDesign` ON (por defecto); deshabilitado si `!canSkipPrevious` o invitado |
| Reproducir / Pausar *(o silenciar como invitado)* | Mini reproductor | `app/.../ui/player/MiniPlayer.kt:445` → impl. `:818` | acción primaria | constante | siempre |
| Siguiente *(solo icono)* | Mini reproductor, diseño nuevo | `app/.../ui/player/MiniPlayer.kt:456` | acción primaria | constante | deshabilitado si `!canSkipNext` o invitado |
| Siguiente *(solo icono)* | Mini reproductor, diseño legacy | `app/.../ui/player/MiniPlayer.kt:773` | acción primaria | constante | solo si `UseNewMiniPlayerDesign` OFF |
| Anillo de progreso alrededor de la miniatura | Mini reproductor, diseño nuevo | `app/.../ui/player/MiniPlayer.kt:486-516` | informativo | constante | diseño nuevo |
| Barra de progreso inferior de 2 dp | Mini reproductor, legacy | `app/.../ui/player/MiniPlayer.kt:737` | informativo | constante | diseño legacy |
| Título + artista (marquesina) | Mini reproductor | `app/.../ui/player/MiniPlayer.kt:566`/`:581` (nuevo), `:920`/`:931` (legacy) | informativo | constante | siempre |
| Insignia **Explicit** | Mini reproductor | `app/.../ui/player/MiniPlayer.kt:579` | informativo | ocasional | solo si `metadata.explicit` |
| **«Error al reproducir»** | Mini reproductor | `app/.../ui/player/MiniPlayer.kt:594` | informativo | rara | solo con error de reproducción |
| Icono de error sobre la carátula | Mini reproductor, legacy | `app/.../ui/player/MiniPlayer.kt:896-911` | informativo | rara | legacy + error |
| **«Casting»** *(hardcoded, en inglés)* | Mini reproductor | `app/.../ui/player/MiniPlayer.kt:429-433` | informativo | rara | solo si `isCasting` |
| Vídeo en miniatura circular | Mini reproductor | `app/.../ui/player/MiniPlayer.kt:534` (nuevo) / `:881` (legacy) | informativo | rara | `videoMode` con URL **y** hoja totalmente colapsada **y** sin PiP |

## 2.13 Panel lateral «Sonando ahora» (pantalla ancha)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Portada → abre el reproductor completo | Panel lateral derecho | `app/.../ui/player/NowPlayingSidePanel.kt:104` | navegación | diaria | solo pantalla ancha (colocado desde `MainActivity.kt:1545`/`:1652`) con canción activa |
| Barra de progreso / buscar | Panel lateral derecho | `app/.../ui/player/NowPlayingSidePanel.kt:135` | acción primaria | diaria | igual |
| No me gusta | Panel lateral derecho | `app/.../ui/player/NowPlayingSidePanel.kt:168` | conmutador | ocasional | igual |
| Anterior | Panel lateral derecho | `app/.../ui/player/NowPlayingSidePanel.kt:179` | acción primaria | constante | igual |
| Reproducir / Pausar | Panel lateral derecho | `app/.../ui/player/NowPlayingSidePanel.kt:188` | acción primaria | constante | igual |
| Siguiente | Panel lateral derecho | `app/.../ui/player/NowPlayingSidePanel.kt:200` | acción primaria | constante | igual |
| Me gusta | Panel lateral derecho | `app/.../ui/player/NowPlayingSidePanel.kt:207` | conmutador | diaria | igual |
| Tiempos transcurrido / total | Panel lateral derecho | `app/.../ui/player/NowPlayingSidePanel.kt:148`/`:153` | informativo | constante | igual |

---

# 3. REPRODUCTOR > MENÚ «Más» (chip `+`) — `OldPlayerMenu`

> **Aviso importante para el rediseño.** Existen DOS menús de reproductor y **los dos están vivos**, pero
> sus nombres engañan:
>
> | Composable | Se abre desde | ¿Vivo? |
> |---|---|---|
> | `OldPlayerMenu` | chip **«Más»** del reproductor (`ui/player/Player.kt:2111`) | **SÍ — es el menú real del REPRODUCTOR** |
> | `PlayerMenu` | botón ⋮ de la barra de la cola (`ui/player/Queue.kt:475`) y ⋮ de la cabecera de la Cola (`Queue.kt:891`) | **SÍ — pero solo desde la COLA** |
> | `MoreActionsButton` (`Player.kt:3918`) y `PlayerMoreMenuButton` (`Player.kt:3960`) | nada | **NO — huérfanos** |
>
> Los dos menús tienen contenidos **parecidos pero no idénticos**: el de la cola tiene *Modo Ambiente*,
> *Ecualizador* y *Avanzado (tempo y tono)*; el del reproductor tiene *Aleatorio*, *Repetir* y *Sonido*.
> Si el rediseño unifica en un solo menú hay que fusionar ambas listas o se perderán entradas.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Transmitiendo a %s | Reproductor > menú «Más» > cabecera | `ui/menu/OldPlayerMenu.kt:239` | informativo | rara | solo si `isCasting && castDeviceName != null` |
| Deslizador de volumen *(solo icono)* | Reproductor > menú «Más» > cabecera | `ui/menu/OldPlayerMenu.kt:246` | acción primaria | diaria | siempre (controla el volumen de Cast si `isCasting`) |
| Iniciar radio | Reproductor > menú «Más» > rejilla | `ui/menu/OldPlayerMenu.kt:289` | acción primaria | diaria | solo si no eres invitado de Escuchar juntos |
| Añadir a lista de reproducción | Reproductor > menú «Más» > rejilla | `ui/menu/OldPlayerMenu.kt:306` | acción primaria | diaria | siempre |
| Compartir | Reproductor > menú «Más» > rejilla | `ui/menu/OldPlayerMenu.kt:318` | acción secundaria | ocasional | siempre |
| Aleatorio | Reproductor > menú «Más» > grupo 1 | `ui/menu/OldPlayerMenu.kt:348` | conmutador | diaria | solo si no eres invitado |
| Eliminar descarga | Reproductor > menú «Más» > grupo 1 | `ui/menu/OldPlayerMenu.kt:370` | destructiva | ocasional | solo si `download.state == COMPLETED` |
| Descargando *(pulsar cancela)* | Reproductor > menú «Más» > grupo 1 | `ui/menu/OldPlayerMenu.kt:388` | destructiva | rara | solo si `QUEUED`/`DOWNLOADING` |
| Descargar | Reproductor > menú «Más» > grupo 1 | `ui/menu/OldPlayerMenu.kt:402` | acción primaria | diaria | si no hay descarga en curso ni completada |
| Exportando | Reproductor > menú «Más» > grupo 1 | `ui/menu/OldPlayerMenu.kt:428` | informativo (`onClick` vacío) | rara | solo si `enableExportAsMp3` y el id está en `ExportingSongIds` |
| Exportado | Reproductor > menú «Más» > grupo 1 | `ui/menu/OldPlayerMenu.kt:440` | informativo (`onClick` vacío) | rara | solo si `enableExportAsMp3` y el id está en `ExportedSongIds` |
| Exportar | Reproductor > menú «Más» > grupo 1 | `ui/menu/OldPlayerMenu.kt:453` | acción secundaria | rara | solo si `enableExportAsMp3` |
| Repetir *(cicla off → todo → una)* | Reproductor > menú «Más» > grupo 1 | `ui/menu/OldPlayerMenu.kt:490` | conmutador (3 estados) | diaria | solo si no eres invitado |
| Ver artista | Reproductor > menú «Más» > grupo 2 | `ui/menu/OldPlayerMenu.kt:524` | navegación | diaria | solo si `artists.isNotEmpty()` |
| Ver álbum | Reproductor > menú «Más» > grupo 2 | `ui/menu/OldPlayerMenu.kt:559` | navegación | diaria | solo si `resolvedAlbum != null` |
| Ir al podcast | Reproductor > menú «Más» > grupo 2 | `ui/menu/OldPlayerMenu.kt:587` | navegación | rara | solo si el id empieza por `http` (episodio de podcast) |
| Quitar de la biblioteca / Añadir a la biblioteca | Reproductor > menú «Más» > grupo 2 | `ui/menu/OldPlayerMenu.kt:625-626` | conmutador | diaria | siempre; texto e icono según `inLibrary` |
| Cargar de nuevo *(«Obtener los metadatos más recientes de Aura Hi-Res»)* | Reproductor > menú «Más» > grupo 2 | `ui/menu/OldPlayerMenu.kt:650` | acción secundaria | rara | siempre |
| Establecer como tono de llamada | Reproductor > menú «Más» > grupo 3 | `ui/menu/OldPlayerMenu.kt:680` | acción secundaria | rara | siempre |
| Escuchar juntos | Reproductor > menú «Más» > grupo 4 | `ui/menu/OldPlayerMenu.kt:710` | navegación (diálogo) | ocasional | siempre |
| Re sincronizar | Reproductor > menú «Más» > grupo 4 | `ui/menu/OldPlayerMenu.kt:724` | acción secundaria | rara | solo si eres invitado de Escuchar juntos |
| Detalles *(«Ver la información de la canción»)* | Reproductor > menú «Más» > grupo 5 | `ui/menu/OldPlayerMenu.kt:751` | navegación | ocasional | siempre |
| **Sonido** *(hardcoded)* — «Ajustes de audio y efectos» → `settings/sound` | Reproductor > menú «Más» > grupo 5 | `ui/menu/OldPlayerMenu.kt:769-770` | navegación | ocasional | siempre |
| Ajustes → `settings` | Reproductor > menú «Más» > grupo 5 | `ui/menu/OldPlayerMenu.kt:789` | navegación | ocasional | siempre |
| *(nombre del artista)* — diálogo de selección | Reproductor > menú «Más» > Ver artista | `ui/menu/OldPlayerMenu.kt:188-197` | navegación | ocasional | solo si la canción tiene más de un artista |

---

# 4. COLA

## 4.1 Cola > barra colapsada — diseño NUEVO (por defecto)

Es la fila de iconos que se ve siempre bajo los controles del reproductor.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Abrir cola *(icono `queue_music`, sin contentDescription)* | Reproductor > barra de la cola | `ui/player/Queue.kt:366-368` | navegación | constante | siempre |
| Temporizador de apagado *(icono `bedtime`; muestra el tiempo restante cuando está activo)* | Reproductor > barra de la cola | `ui/player/Queue.kt:379-398` | conmutador | ocasional | `enabled` solo si no eres invitado; si está activo lo cancela, si no abre el diálogo |
| Letras *(icono `lyrics`)* | Reproductor > barra de la cola | `ui/player/Queue.kt:400-411` | conmutador | diaria | siempre |
| Comentarios *(icono `chat_msg`)* → `CommentSheet` | Reproductor > barra de la cola | `ui/player/Queue.kt:413-426` | navegación | ocasional | **solo si `ShowCommentButton` está ON (por defecto OFF)** |
| Aleatorio *(icono `shuffle`)* | Reproductor > barra de la cola | `ui/player/Queue.kt:429-443` | conmutador | constante | `enabled` solo si no eres invitado |
| Repetir *(icono `repeat`/`repeat_one`)* | Reproductor > barra de la cola | `ui/player/Queue.kt:446-464` | conmutador (3 estados) | constante | `enabled` solo si no eres invitado |
| Más *(icono `more_vert`)* → `PlayerMenu` (§5) | Reproductor > barra de la cola | `ui/player/Queue.kt:468-498` | navegación | diaria | siempre |

## 4.2 Cola > barra colapsada — diseño ANTIGUO

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Abrir cola *(icono `apple_queue`)* | Reproductor > barra de la cola (antiguo) | `ui/player/Queue.kt:513-537` | navegación | constante | solo si `!useNewPlayerDesign` |
| **Salida de audio / dispositivo** *(icono `headset_applemusic` / `speaker_apple`)* → `AudioDeviceBottomSheet` | Reproductor > barra de la cola (antiguo) | `ui/player/Queue.kt:543-566` | navegación | ocasional | ⚠️ **único acceso al selector de dispositivo de audio en toda la app**, y solo existe en el diseño antiguo |
| Temporizador de apagado *(icono `sleep_timer` + tiempo restante)* | Reproductor > barra de la cola (antiguo) | `ui/player/Queue.kt:568-609` | conmutador | ocasional | acción bloqueada si eres invitado |
| Letras *(icono `apple_music_me`)* | Reproductor > barra de la cola (antiguo) | `ui/player/Queue.kt:612-638` | conmutador | diaria | solo si `!useNewPlayerDesign` |

## 4.3 Cola > diálogo «Temporizador de apagado»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Temporizador de apagado *(título)* | Cola > diálogo del temporizador | `ui/player/Queue.kt:653` | informativo | ocasional | solo si el diálogo está abierto |
| «%d minutos» | Cola > diálogo del temporizador | `ui/player/Queue.kt:674-678` | informativo | ocasional | igual |
| Deslizador 5–120 min (pasos de 5) | Cola > diálogo del temporizador | `ui/player/Queue.kt:684-690` | acción primaria | ocasional | igual |
| Al finalizar la canción | Cola > diálogo del temporizador | `ui/player/Queue.kt:694-701` | acción primaria | ocasional | igual |
| Confirmar / Cancelar / Restablecer | Cola > diálogo del temporizador | `ui/player/Queue.kt:661` / `:665` / `:668` | acción primaria / navegación / destructiva | ocasional | igual; «Restablecer» vuelve a 30 min |

*(El reproductor tiene además su PROPIO diálogo de temporizador, definido en `ui/player/Player.kt:861-918`
con slider 5–120 min, «Al finalizar la canción» `:913` y Aceptar/Cancelar `:879`/`:885`. Son dos diálogos
distintos para la misma función.)*

## 4.4 Cola > cabecera de la hoja

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Portada + título + artistas de la canción actual | Cola > cabecera | `ui/player/Queue.kt:817-841` | informativo | constante | siempre |
| Me gusta / Quitar me gusta *(icono + tooltip)* | Cola > cabecera | `ui/player/Queue.kt:844-863` | conmutador | diaria | siempre |
| **Bloquear cola / Desbloquear cola** *(icono + tooltip; persiste en `QueueEditLock`)* | Cola > cabecera | `ui/player/Queue.kt:865-879` | conmutador | ocasional | siempre; bloquea reordenar y deslizar |
| Más opciones *(icono `more_vert` + tooltip)* → `PlayerMenu` | Cola > cabecera | `ui/player/Queue.kt:882-912` | navegación | diaria | siempre |
| Pestaña **SIGUIENTE** | Cola > pestañas | `ui/player/Queue.kt:942`, `:946-953` | navegación | constante | siempre (por defecto) |
| Pestaña **LETRA** | Cola > pestañas | `ui/player/Queue.kt:943`, `:946-953` | navegación | diaria | siempre |
| Pestaña **RELACIONADOS** | Cola > pestañas | `ui/player/Queue.kt:944`, `:946-953` | navegación | ocasional | siempre |

## 4.5 Cola > pestaña SIGUIENTE

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Aleatorio | Cola > Siguiente > barra de acciones | `ui/player/Queue.kt:969-998` (texto `:993`) | conmutador | constante | `enabled` solo si no eres invitado |
| Repetir | Cola > Siguiente > barra de acciones | `ui/player/Queue.kt:1000-1035` (texto `:1030`) | conmutador (3 estados) | constante | `enabled` solo si no eres invitado |
| Radio | Cola > Siguiente > barra de acciones | `ui/player/Queue.kt:1037-1069` (texto `:1063`) | acción primaria | diaria | `enabled` solo si no eres invitado; toast «Iniciando radio» |
| Continuar reproduciendo / Siguiente en la cola | Cola > Siguiente > cabecera de lista | `ui/player/Queue.kt:1085` / `:1090` | informativo | constante | siempre |
| «%d canciones» + duración total | Cola > Siguiente > cabecera de lista | `ui/player/Queue.kt:1101-1113` | informativo | constante | siempre |
| Tocar una fila → reproducir esa canción (o pausar/reanudar si ya es la actual) | Cola > Siguiente > fila | `ui/player/Queue.kt:1416-1452` | acción primaria | constante | bloqueado si eres invitado; ruta distinta con Cast |
| Menú de la canción *(icono `more_vert`)* → `QueueMenu` (§6) | Cola > Siguiente > fila | `ui/player/Queue.kt:1373-1396` | navegación | diaria | solo fuera del modo selección **y** si no eres invitado |
| Asa de arrastre *(icono `drag_handle`)* | Cola > Siguiente > fila | `ui/player/Queue.kt:1399-1407` | acción secundaria (gesto) | ocasional | solo si la cola no está bloqueada, no eres invitado y no estás en selección |
| Snackbar «Eliminado «%s» de la lista de reproducción» | Cola > Siguiente | `ui/player/Queue.kt:1325-1328` | informativo | ocasional | tras deslizar para eliminar |
| **Deshacer** *(restaura la canción en su posición)* | Cola > Siguiente > snackbar | `ui/player/Queue.kt:1329` | acción primaria | ocasional | igual |

## 4.6 Cola > pestaña SIGUIENTE, modo selección

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Salir de la selección *(icono `close`)* | Cola > barra de selección | `ui/player/Queue.kt:1136-1143` | navegación | ocasional | solo en modo selección |
| «%d seleccionadas» | Cola > barra de selección | `ui/player/Queue.kt:1145` | informativo | ocasional | igual |
| Seleccionar todo / ninguno *(casilla)* | Cola > barra de selección | `ui/player/Queue.kt:1148-1160` | conmutador | ocasional | igual |
| Acciones de selección *(icono `more_vert`)* → `SelectionMediaMetadataMenu` | Cola > barra de selección | `ui/player/Queue.kt:1161-1179` | navegación | ocasional | `enabled` solo si hay algo seleccionado |
| Casilla de la fila | Cola > Siguiente > fila | `ui/player/Queue.kt:1367-1370` | conmutador | ocasional | solo en modo selección |

## 4.7 Cola > pie «Reproducción automática» y pestaña RELACIONADOS

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Reproducción automática *(título)* | Cola > Siguiente > pie | `ui/player/Queue.kt:1498` | informativo | constante | solo si no eres invitado o hay automix |
| Interruptor de reproducción automática | Cola > Siguiente > pie | `ui/player/Queue.kt:1505-1513` | conmutador | ocasional | solo si no eres invitado. **Es la MISMA preferencia que Ajustes → «Cargar más canciones automáticamente»** |
| Chips de orientación del autoplay *(etiquetas dinámicas del servicio)* | Cola > Siguiente > pie | `ui/player/Queue.kt:1523-1530` | conmutador / filtro | ocasional | solo si no eres invitado, autoplay ON y hay chips |
| «Las canciones relacionadas aparecerán aquí cuando la reproducción automática las cargue» | Cola > Relacionados (vacío) | `ui/player/Queue.kt:1227` | informativo | ocasional | solo si el automix está vacío |
| Reproducir a continuación *(icono `playlist_play`)* | Cola > Relacionados > fila | `ui/player/Queue.kt:1614-1626` | acción primaria | ocasional | solo si no eres invitado |
| Añadir a la cola *(icono `queue_music`)* | Cola > Relacionados > fila | `ui/player/Queue.kt:1627-1639` | acción primaria | ocasional | solo si no eres invitado |

## 4.8 Cola > pestaña LETRA

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Vista de letras completa (`InlineLyricsView`, mismos gestos que en el reproductor) | Cola > Letra | `ui/player/Queue.kt:1206-1210` | acción primaria (contenedor) | diaria | solo con la pestaña 1 activa y la hoja no colapsada |

---

# 5. COLA > MENÚ ⋮ — `PlayerMenu`

Se abre desde `ui/player/Queue.kt:475` (barra colapsada) y `ui/player/Queue.kt:891` (cabecera de la Cola).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Transmitiendo a %s | Cola > menú ⋮ > cabecera | `ui/menu/PlayerMenu.kt:291` | informativo | rara | solo con Cast activo |
| Deslizador de volumen *(solo icono)* | Cola > menú ⋮ > cabecera | `ui/menu/PlayerMenu.kt:298` | acción primaria | diaria | solo si `isQueueTrigger != true` (hoy siempre visible) |
| Iniciar radio | Cola > menú ⋮ > rejilla | `ui/menu/PlayerMenu.kt:344` | acción primaria | diaria | solo si no eres invitado (la rejilla pasa de 3 a 2 columnas si lo eres) |
| Añadir a lista de reproducción | Cola > menú ⋮ > rejilla | `ui/menu/PlayerMenu.kt:361` | acción primaria | diaria | siempre |
| Compartir | Cola > menú ⋮ > rejilla | `ui/menu/PlayerMenu.kt:373` | acción secundaria | ocasional | siempre |
| Ver artista | Cola > menú ⋮ > grupo 1 | `ui/menu/PlayerMenu.kt:399` | navegación | diaria | solo si hay artistas |
| Ver álbum | Cola > menú ⋮ > grupo 1 | `ui/menu/PlayerMenu.kt:434` | navegación | diaria | solo si se resuelve el álbum |
| Ir al podcast | Cola > menú ⋮ > grupo 1 | `ui/menu/PlayerMenu.kt:462` | navegación | rara | solo si el id empieza por `http` |
| Quitar de la biblioteca / Añadir a la biblioteca | Cola > menú ⋮ > grupo 1 | `ui/menu/PlayerMenu.kt:498-501` | conmutador | diaria | siempre |
| Cargar de nuevo | Cola > menú ⋮ > grupo 1 | `ui/menu/PlayerMenu.kt:524` | acción secundaria | rara | siempre |
| Eliminar descarga | Cola > menú ⋮ > grupo 2 | `ui/menu/PlayerMenu.kt:555` | destructiva | ocasional | solo si `COMPLETED` |
| Descargando *(pulsar cancela)* | Cola > menú ⋮ > grupo 2 | `ui/menu/PlayerMenu.kt:578` | destructiva | rara | solo si `QUEUED`/`DOWNLOADING` |
| Descargar | Cola > menú ⋮ > grupo 2 | `ui/menu/PlayerMenu.kt:598` | acción primaria | diaria | resto |
| Exportando | Cola > menú ⋮ > grupo 3 | `ui/menu/PlayerMenu.kt:637` | informativo (`onClick` vacío) | rara | solo si `enableExportAsMp3` |
| Exportado | Cola > menú ⋮ > grupo 3 | `ui/menu/PlayerMenu.kt:647` | informativo (`onClick` vacío) | rara | igual |
| Exportar *(«Exportar como MP3»)* | Cola > menú ⋮ > grupo 3 | `ui/menu/PlayerMenu.kt:657` | acción secundaria | rara | igual |
| Establecer como tono de llamada | Cola > menú ⋮ > grupo 4 | `ui/menu/PlayerMenu.kt:695` | acción secundaria | rara | siempre |
| Escuchar juntos *(+ badge numérico de sugerencias)* | Cola > menú ⋮ > grupo 5 | `ui/menu/PlayerMenu.kt:727` (badge `:736-751`) | navegación (diálogo) | ocasional | siempre; el badge solo si hay sugerencias pendientes |
| Re sincronizar | Cola > menú ⋮ > grupo 5 | `ui/menu/PlayerMenu.kt:760` | acción secundaria | rara | solo si eres invitado |
| Detalles *(«Ver la información de la canción»)* | Cola > menú ⋮ > grupo 6 | `ui/menu/PlayerMenu.kt:786` | navegación | ocasional | siempre |
| **Modo Ambiente** *(«Vista a pantalla completa con un resplandor animado»)* → `ambient_mode` | Cola > menú ⋮ > grupo 6 | `ui/menu/PlayerMenu.kt:805` | navegación | ocasional | siempre |
| **Ecualizador** *(«Ajustar el ecualizador de audio»)* → `settings/equalizer` | Cola > menú ⋮ > grupo 6 | `ui/menu/PlayerMenu.kt:825` | navegación | ocasional | solo si `isQueueTrigger != true` |
| **Avanzado** *(«Cambiar el tempo y el tono de la canción»)* | Cola > menú ⋮ > grupo 6 | `ui/menu/PlayerMenu.kt:845` | navegación (diálogo) | rara | solo si `isQueueTrigger != true` |
| *(nombre del artista)* — diálogo de selección | Cola > menú ⋮ > Ver artista | `ui/menu/PlayerMenu.kt:218-234` | navegación | ocasional | solo si hay más de un artista |

## 5.1 Diálogo «Tempo y tono»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Tempo y tono *(título)* | Cola > menú ⋮ > Avanzado | `ui/menu/PlayerMenu.kt:886` | informativo | rara | siempre |
| Restablecer *(a ×1 / 0 semitonos)* | Cola > menú ⋮ > Avanzado | `ui/menu/PlayerMenu.kt:896` | destructiva | rara | siempre |
| Aceptar | Cola > menú ⋮ > Avanzado | `ui/menu/PlayerMenu.kt:903` | acción primaria | rara | siempre |
| **Velocidad ×0,25 … ×2,0** | Cola > menú ⋮ > Avanzado | `ui/menu/PlayerMenu.kt:909-919` | acción primaria | rara | **solo si NO estás en una sala de Escuchar juntos** |
| **Tono ±12 semitonos** | Cola > menú ⋮ > Avanzado | `ui/menu/PlayerMenu.kt:921-930` | acción primaria | rara | siempre |
| − *(decrementar)* | Cola > menú ⋮ > Avanzado | `ui/menu/PlayerMenu.kt:956-966` | acción secundaria | rara | deshabilitado en el primer valor |
| + *(incrementar)* | Cola > menú ⋮ > Avanzado | `ui/menu/PlayerMenu.kt:975-985` | acción secundaria | rara | deshabilitado en el último valor |

## 5.2 Diálogo «Escuchar juntos» *(compartido por ambos menús)*

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Aceptar | Escuchar juntos > estado «no configurado» | `ui/menu/PlayerMenu.kt:1037` | acción primaria | rara | solo si no hay gestor |
| **Expulsar** *(«Eliminar a esta persona de la sesión»)* | Escuchar juntos > Administrar usuario | `ui/menu/PlayerMenu.kt:1140` (fila `:1117`) | destructiva | rara | solo el anfitrión, al tocar el avatar de otro |
| **Bloquear permanentemente** | Escuchar juntos > Administrar usuario | `ui/menu/PlayerMenu.kt:1189` (fila `:1163`) | destructiva | rara | igual |
| **Transferir propiedad** *(«Convertir a esta persona en el anfitrión»)* | Escuchar juntos > Administrar usuario | `ui/menu/PlayerMenu.kt:1235` (fila `:1212`) | destructiva | rara | igual |
| Conectar | Escuchar juntos > tarjeta de estado | `ui/menu/PlayerMenu.kt:1406` | acción primaria | ocasional | solo si desconectado o error |
| Desconectar | Escuchar juntos > tarjeta de estado | `ui/menu/PlayerMenu.kt:1416` | destructiva | ocasional | solo si conectado/conectando |
| **Reconectar** *(hardcoded)* | Escuchar juntos > tarjeta de estado | `ui/menu/PlayerMenu.kt:1422` | acción secundaria | rara | igual |
| Copiar enlace | Escuchar juntos > sala | `ui/menu/PlayerMenu.kt:1509` | acción secundaria | ocasional | solo en sala y siendo anfitrión |
| Copiar código | Escuchar juntos > sala | `ui/menu/PlayerMenu.kt:1528` | acción secundaria | ocasional | igual |
| Avatar de usuario conectado → «Administrar usuario» | Escuchar juntos > usuarios | `ui/menu/PlayerMenu.kt:1566-1572` | navegación | rara | `enabled` solo si eres anfitrión y no eres tú |
| Aprobar solicitud | Escuchar juntos > Solicitudes pendientes | `ui/menu/PlayerMenu.kt:1733-1737` | acción primaria | rara | solo anfitrión con solicitudes |
| Rechazar solicitud | Escuchar juntos > Solicitudes pendientes | `ui/menu/PlayerMenu.kt:1743-1747` | destructiva | rara | igual |
| Aprobar sugerencia | Escuchar juntos > Sugerencias pendientes | `ui/menu/PlayerMenu.kt:1819-1823` | acción primaria | rara | solo anfitrión con sugerencias |
| Rechazar sugerencia | Escuchar juntos > Sugerencias pendientes | `ui/menu/PlayerMenu.kt:1829-1833` | destructiva | rara | igual |
| Cancelar | Escuchar juntos > pie (en sala) | `ui/menu/PlayerMenu.kt:1858` | navegación | ocasional | solo en sala |
| **Salir de la sala** | Escuchar juntos > pie (en sala) | `ui/menu/PlayerMenu.kt:1878` | destructiva | ocasional | solo en sala |
| Nombre de usuario *(campo, «Ingresar nombre de usuario»)* | Escuchar juntos > alta | `ui/menu/PlayerMenu.kt:1905-1908` | acción primaria | rara | solo fuera de sala |
| Limpiar nombre *(solo icono)* | Escuchar juntos > alta | `ui/menu/PlayerMenu.kt:1919-1920` | acción secundaria | rara | solo si el campo no está vacío |
| Código de sala *(campo, «ABCD1234», contador n/8)* | Escuchar juntos > alta | `ui/menu/PlayerMenu.kt:1943-1946` | acción primaria | rara | solo fuera de sala |
| Crear | Escuchar juntos > alta | `ui/menu/PlayerMenu.kt:2067` | acción primaria | ocasional | `enabled` solo con nombre de usuario |
| Unirse | Escuchar juntos > alta | `ui/menu/PlayerMenu.kt:2104` | acción primaria | ocasional | `enabled` solo con código de 8 caracteres y nombre |
| Cancelar | Escuchar juntos > alta | `ui/menu/PlayerMenu.kt:2114` | navegación | ocasional | solo fuera de sala |
| Esperando la aprobación del anfitrión | Escuchar juntos > alta | `ui/menu/PlayerMenu.kt:1986` | informativo | rara | mientras te unes |
| *(mensaje de error de unión)* | Escuchar juntos > alta | `ui/menu/PlayerMenu.kt:2013` | informativo | rara | solo si hay error |

---

# 6. COLA > MENÚ DE CANCIÓN — `QueueMenu`

Se abre con el ⋮ de una fila de la cola (`ui/player/Queue.kt:1376`) y con una pulsación larga en la pestaña Relacionados (`Queue.kt:1649`).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Me gusta / quitar me gusta *(solo icono)* | Cola > menú de canción > cabecera | `ui/menu/QueueMenu.kt:213-241` | conmutador | diaria | siempre |
| Iniciar radio | Cola > menú de canción > rejilla | `ui/menu/QueueMenu.kt:273` | acción primaria | diaria | siempre |
| Añadir a lista de reproducción | Cola > menú de canción > rejilla | `ui/menu/QueueMenu.kt:290` | acción primaria | diaria | siempre |
| Compartir | Cola > menú de canción > rejilla | `ui/menu/QueueMenu.kt:302` | acción secundaria | ocasional | siempre |
| Reproducir a continuación *(«Añadir al inicio de la cola»)* | Cola > menú de canción > grupo 1 | `ui/menu/QueueMenu.kt:326` | acción primaria | diaria | siempre |
| Añadir a la cola *(«Añadir al final de la cola»)* | Cola > menú de canción > grupo 1 | `ui/menu/QueueMenu.kt:344` | acción primaria | diaria | siempre |
| Eliminar descarga | Cola > menú de canción > grupo 2 | `ui/menu/QueueMenu.kt:375` | destructiva | ocasional | solo si `COMPLETED` |
| Descargando *(pulsar cancela)* | Cola > menú de canción > grupo 2 | `ui/menu/QueueMenu.kt:397` | destructiva | rara | solo si `QUEUED`/`DOWNLOADING` |
| Descargar *(«Hacer disponible para reproducir sin conexión»)* | Cola > menú de canción > grupo 2 | `ui/menu/QueueMenu.kt:417` | acción primaria | diaria | resto |
| Ver artista | Cola > menú de canción > grupo 3 | `ui/menu/QueueMenu.kt:459` | navegación | diaria | solo si hay artistas |
| Ver álbum | Cola > menú de canción > grupo 3 | `ui/menu/QueueMenu.kt:494` | navegación | diaria | solo si se resuelve el álbum |
| Cargar de nuevo | Cola > menú de canción > grupo 4 | `ui/menu/QueueMenu.kt:529` | acción secundaria | rara | siempre; también purga el audio cacheado |
| Detalles | Cola > menú de canción > grupo 4 | `ui/menu/QueueMenu.kt:566` | navegación | ocasional | siempre |
| *(nombre del artista, con miniatura)* | Cola > menú de canción > Ver artista | `ui/menu/QueueMenu.kt:164-204` | navegación | ocasional | solo si hay más de un artista |

---

# 7. LETRAS

## 7.1 Letras > menú ⋯ — `LyricsMenu`

Se abre con el botón `more_horiz` del reproductor **solo cuando las letras están visibles** (`ui/player/Player.kt:1784` diseño nuevo, `:1857` diseño antiguo).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Editar | Letras > menú ⋯ > rejilla | `ui/menu/LyricsMenu.kt:401` | acción secundaria | rara | siempre |
| Cargar de nuevo | Letras > menú ⋯ > rejilla | `ui/menu/LyricsMenu.kt:415` | acción secundaria | ocasional | siempre |
| Buscar | Letras > menú ⋯ > rejilla | `ui/menu/LyricsMenu.kt:430` | navegación (diálogo) | ocasional | siempre |
| **Traducción de letras con IA** *(fila + interruptor)* | Letras > menú ⋯ > grupo 1 | `ui/menu/LyricsMenu.kt:447` (interruptor `:471-488`) | conmutador | ocasional | **solo si hay clave API** (DeepL u OpenRouter según proveedor) |
| Desfase de la letra *(+ valor «±NNN ms»)* | Letras > menú ⋯ > grupo 1 | `ui/menu/LyricsMenu.kt:496` (valor `:509`) | navegación (diálogo) | ocasional | siempre |
| Romanizar la pista actual *(fila + interruptor)* | Letras > menú ⋯ > grupo 1 | `ui/menu/LyricsMenu.kt:519` (interruptor `:535-545`) | conmutador | ocasional | siempre |

### Diálogo de edición de letra

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Campo multilínea *(título = nombre de la canción; guarda como proveedor «Manual»)* | Letras > menú ⋯ > Editar | `ui/menu/LyricsMenu.kt:117-134` | acción primaria | rara | solo si el diálogo está abierto |

### Diálogo «Buscar letra»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Buscar letra *(título)* | Letras > menú ⋯ > Buscar | `ui/menu/LyricsMenu.kt:177` | informativo | ocasional | siempre en el diálogo |
| Cancelar | Letras > menú ⋯ > Buscar | `ui/menu/LyricsMenu.kt:182` | navegación | ocasional | igual |
| **Buscar en línea** *(abre el navegador con `ACTION_WEB_SEARCH`)* | Letras > menú ⋯ > Buscar | `ui/menu/LyricsMenu.kt:204` | navegación externa | rara | igual |
| Aceptar | Letras > menú ⋯ > Buscar | `ui/menu/LyricsMenu.kt:228` | acción primaria | ocasional | igual; toast «No hay conexión a internet» si no hay red |
| Título *(campo)* | Letras > menú ⋯ > Buscar | `ui/menu/LyricsMenu.kt:232-237` | acción primaria | ocasional | igual |
| Artistas *(campo)* | Letras > menú ⋯ > Buscar | `ui/menu/LyricsMenu.kt:241-246` | acción primaria | ocasional | igual |

### Diálogo de resultados

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Tocar un resultado → aplica esa letra | Letras > resultados | `ui/menu/LyricsMenu.kt:266-278` | acción primaria | ocasional | siempre en el diálogo |
| Proveedor + icono `sync` si está sincronizada | Letras > resultados | `ui/menu/LyricsMenu.kt:302-317` | informativo | ocasional | icono solo si la letra lleva marcas de tiempo |
| Expandir / contraer resultado | Letras > resultados | `ui/menu/LyricsMenu.kt:322-331` | acción secundaria | ocasional | por cada resultado |
| Letras no encontradas | Letras > resultados | `ui/menu/LyricsMenu.kt:349` | informativo | ocasional | si no hay resultados |

## 7.2 Letras > vista

Alcanzable desde el Reproductor, desde Cola > pestaña LETRA y desde el Modo Ambiente.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Traduciendo letras... | Letras > banner | `ui/component/Lyrics.kt:927` | informativo | ocasional | mientras traduce |
| *(mensaje de error de traducción)* | Letras > banner | `ui/component/Lyrics.kt:954` | informativo | rara | si falla |
| Letras traducidas | Letras > banner | `ui/component/Lyrics.kt:981` | informativo | ocasional | si tuvo éxito |
| Letras no encontradas | Letras > cuerpo | `ui/component/Lyrics.kt:1000` | informativo | ocasional | si no hay letra |
| **«Lyrics from <proveedor>»** *(hardcoded, en inglés)* | Letras > primera fila | `ui/component/Lyrics.kt:1061` | informativo | constante | si hay proveedor y no estás en selección |
| **«¿Traducir la letra?»** *(hardcoded)* | Letras > diálogo | `ui/component/Lyrics.kt:650-651` | informativo | rara | solo si `AskTranslateLyricsOnOpen` (por defecto OFF), la letra parece inglés, el idioma destino no es inglés y no se ha respondido antes esta sesión |
| **Traducir** *(hardcoded)* | Letras > diálogo | `ui/component/Lyrics.kt:657` | acción primaria | rara | igual |
| **No** *(hardcoded)* | Letras > diálogo | `ui/component/Lyrics.kt:663` | navegación | rara | igual |
| **Volver a sincronizar** *(botón flotante)* | Letras > pie | `ui/component/Lyrics.kt:2090-2103` | acción primaria | diaria | solo si el auto-scroll está desactivado, la letra está sincronizada y no estás en selección |
| Cancelar selección *(icono `close`)* | Letras > pie (selección) | `ui/component/Lyrics.kt:2115-2126` | navegación | ocasional | solo en modo selección |
| Compartir selección *(icono `share`)* | Letras > pie (selección) | `ui/component/Lyrics.kt:2127-2156` | acción primaria | ocasional | `enabled` solo con líneas seleccionadas |
| Toast «Límite máximo de selección» | Letras | `ui/component/Lyrics.kt:749` | informativo | rara | al intentar seleccionar más de 5 líneas |
| Generando imagen / Por favor, espera | Letras > diálogo de progreso | `ui/component/Lyrics.kt:2170` | informativo | rara | mientras genera la imagen |

### Diálogo «Compartir letra»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Compartir letra *(título)* | Letras > Compartir | `ui/component/Lyrics.kt:2193` | informativo | ocasional | siempre en el diálogo |
| Compartir como texto | Letras > Compartir | `ui/component/Lyrics.kt:2233` | acción primaria | ocasional | igual |
| Compartir como imagen | Letras > Compartir | `ui/component/Lyrics.kt:2258` | navegación | ocasional | igual |
| Cancelar | Letras > Compartir | `ui/component/Lyrics.kt:2271` | navegación | ocasional | igual |

### Diálogo «Personalizar colores» (previo a compartir como imagen)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Personalizar colores *(título)* | Letras > Compartir como imagen | `ui/component/Lyrics.kt:2377` | informativo | rara | siempre en el diálogo |
| Estilo de fondo del reproductor *(etiqueta)* | Letras > Compartir como imagen | `ui/component/Lyrics.kt:2385` | informativo | rara | igual |
| **Sólido / Desenfoque / Gradiente** *(chips)* | Letras > Compartir como imagen | `ui/component/Lyrics.kt:2392-2402` | conmutador | rara | igual |
| Vista previa de la tarjeta | Letras > Compartir como imagen | `ui/component/Lyrics.kt:2413-2421` | informativo | rara | igual |
| Color de fondo *(12 círculos, scroll horizontal)* | Letras > Compartir como imagen | `ui/component/Lyrics.kt:2426` / clic `:2434` | conmutador | rara | igual; los primeros salen de la paleta de la portada |
| Color del texto | Letras > Compartir como imagen | `ui/component/Lyrics.kt:2445` / clic `:2453` | conmutador | rara | igual |
| Color del texto secundario | Letras > Compartir como imagen | `ui/component/Lyrics.kt:2464` / clic `:2472` | conmutador | rara | igual |
| Compartir *(genera el PNG y abre el selector)* | Letras > Compartir como imagen | `ui/component/Lyrics.kt:2484-2530` | acción primaria | rara | igual |

---

# 8. DIÁLOGOS AUXILIARES DEL REPRODUCTOR

## 8.1 «Información de la canción» (Detalles)

Abierto desde «Detalles» de cualquiera de los tres menús. **Cada dato se copia al portapapeles al tocarlo.**

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Información de la canción *(título)* | Detalles > cabecera | `ui/utils/ShowMediaInfo.kt:105` | informativo | ocasional | siempre |
| Listo *(cierra)* | Detalles > cabecera | `ui/utils/ShowMediaInfo.kt:111` | navegación | ocasional | siempre |
| Portada grande | Detalles | `ui/utils/ShowMediaInfo.kt:128-133` | informativo | ocasional | siempre |
| Título / Artista | Detalles > datos | `ui/utils/ShowMediaInfo.kt:149` / `:154` | informativo + copiar | ocasional | siempre |
| Duración / ID multimedia | Detalles > datos | `ui/utils/ShowMediaInfo.kt:173` / `:178` | informativo + copiar | ocasional | siempre |
| Visitas / Me gustas | Detalles > datos | `ui/utils/ShowMediaInfo.kt:191` / `:197` | informativo + copiar | ocasional | «N/A» si no hay datos |
| No me gustas / Suscriptores | Detalles > datos | `ui/utils/ShowMediaInfo.kt:209` / `:214` | informativo + copiar | ocasional | igual |
| Itag / Intensidad | Detalles > datos | `ui/utils/ShowMediaInfo.kt:226` / `:231` | informativo + copiar | ocasional | igual |
| Tipo de MIME / Tasa de bits | Detalles > datos | `ui/utils/ShowMediaInfo.kt:243` / `:248` | informativo + copiar | ocasional | igual |
| Códecs / Frecuencia | Detalles > datos | `ui/utils/ShowMediaInfo.kt:260` / `:265` | informativo + copiar | ocasional | igual |
| Tamaño del archivo / Volumen | Detalles > datos | `ui/utils/ShowMediaInfo.kt:277` / `:284` | informativo + copiar | ocasional | igual |
| Descripción *(o «No hay descripción disponible»)* | Detalles | `ui/utils/ShowMediaInfo.kt:298` / `:316` | informativo | ocasional | indicador de carga mientras llega |

## 8.2 «Desfase de la letra»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Desfase de la letra *(título)* | Letras > Desfase | `ui/utils/ShowOffsetDialog.kt:86` | informativo | ocasional | siempre |
| Campo numérico ±9999 con sufijo «ms» *(guarda al vuelo)* | Letras > Desfase | `ui/utils/ShowOffsetDialog.kt:98-157` | acción primaria | ocasional | siempre |
| Restablecer a 0 *(contentDescription «Reset», en inglés)* | Letras > Desfase | `ui/utils/ShowOffsetDialog.kt:171-182` | destructiva | ocasional | **solo si el desfase no es 0** |
| −50 ms *(contentDescription «Decrease», en inglés)* | Letras > Desfase | `ui/utils/ShowOffsetDialog.kt:192-202` | acción secundaria | ocasional | siempre; límite −3000 |
| Deslizador −3000…+3000 ms (pasos de 100) | Letras > Desfase | `ui/utils/ShowOffsetDialog.kt:204-214` | acción primaria | ocasional | siempre |
| +50 ms *(contentDescription «Increase», en inglés)* | Letras > Desfase | `ui/utils/ShowOffsetDialog.kt:216-226` | acción secundaria | ocasional | siempre; límite +3000 |

## 8.3 Tono de llamada (recortador + progreso)

Alcanzable desde «Establecer como tono de llamada» de ambos menús (`PlayerMenu.kt:707`, `OldPlayerMenu.kt:689`); los diálogos están montados en `MainActivity.kt:1721` y `:1732`.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **«Trim Ringtone»** *(hardcoded, en inglés)* | Tono > recortador | `ui/component/RingtoneTrimmerDialog.kt:94` | informativo | rara | siempre en el diálogo |
| **«Select the part of "…" to use as ringtone»** *(hardcoded, en inglés)* | Tono > recortador | `ui/component/RingtoneTrimmerDialog.kt:102` | informativo | rara | igual |
| Reproducir / Pausar la previsualización *(contentDescription en inglés)* | Tono > recortador | `ui/component/RingtoneTrimmerDialog.kt:117-134` | acción primaria | rara | oculto mientras carga |
| Selector de rango inicio/fin | Tono > recortador | `ui/component/RingtoneTrimmerDialog.kt:156-168` | acción primaria | rara | siempre |
| **«Selected duration: …»** *(hardcoded, en inglés; rojo si supera 40 s)* | Tono > recortador | `ui/component/RingtoneTrimmerDialog.kt:174` | informativo | rara | siempre |
| **Establecer como tono** *(hardcoded)* | Tono > recortador | `ui/component/RingtoneTrimmerDialog.kt:188` | acción primaria | rara | siempre |
| **Cancelar** *(hardcoded)* | Tono > recortador | `ui/component/RingtoneTrimmerDialog.kt:196` | navegación | rara | siempre |
| **«Setting Ringtone…» / «Success!» / «Failed»** *(hardcoded, en inglés)* | Tono > progreso | `ui/component/RingtoneProgressDialog.kt:36-38` | informativo | rara | siempre en el diálogo |
| Barra de progreso | Tono > progreso | `ui/component/RingtoneProgressDialog.kt:53-56` | informativo | rara | mientras no termina |
| Conceder permiso *(pide WRITE_SETTINGS)* | Tono > progreso | `ui/component/RingtoneProgressDialog.kt:75` | acción primaria | rara | solo si terminó con éxito y no se aplicó directo |
| **«Open Settings» / «Close»** *(hardcoded, en inglés)* | Tono > progreso | `ui/component/RingtoneProgressDialog.kt:83` | navegación | rara | solo al terminar |
| **Cerrar** *(hardcoded)* | Tono > progreso | `ui/component/RingtoneProgressDialog.kt:91` | navegación | rara | solo al terminar con éxito |

---

# 9. MENÚS DE ELEMENTO (canción, álbum, artista, lista)

Todos estos menús se muestran dentro de **una única hoja modal global**
(`BottomSheetMenu`, montada en `MainActivity.kt:1663`). Se abren de dos formas equivalentes en casi
todas las pantallas: el botón **⋯** de la fila **y** una **pulsación larga** sobre ella.

## 9.1 Canción local — `SongMenu`

Alcanzable desde 24 sitios: Álbum, Artista, Artista > Canciones, Historial, Inicio, Sin conexión,
Estadísticas, Biblioteca > Canciones, Biblioteca > Local, auto-listas (Me gustan / Descargadas / Subidas),
Caché, Lista local, Top, Búsqueda local.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Me gusta *(corazón, solo icono)* | Canción > menú > cabecera | `ui/menu/SongMenu.kt:338` | conmutador | constante | siempre |
| Iniciar radio | Canción > menú > rejilla | `ui/menu/SongMenu.kt:387` | acción primaria | diaria | solo si no eres invitado de Escuchar juntos |
| Añadir a lista de reproducción | Canción > menú > rejilla | `ui/menu/SongMenu.kt:403` | acción primaria | diaria | siempre |
| Compartir | Canción > menú > rejilla | `ui/menu/SongMenu.kt:415` | acción secundaria | ocasional | siempre |
| Sugerir alojar | Canción > menú > grupo 1 | `ui/menu/SongMenu.kt:436` | acción primaria | rara | solo en una sala de Escuchar juntos siendo invitado |
| Editar *(«Editar canción»)* | Canción > menú > grupo 1 | `ui/menu/SongMenu.kt:459-460` | navegación | ocasional | siempre |
| Reproducir a continuación | Canción > menú > grupo 1 | `ui/menu/SongMenu.kt:473-474` | acción primaria | diaria | solo si no eres invitado |
| Añadir a la cola | Canción > menú > grupo 1 | `ui/menu/SongMenu.kt:489-490` | acción primaria | diaria | solo si no eres invitado |
| Fijar / Quitar de marcación rápida | Canción > menú > grupo 2 | `ui/menu/SongMenu.kt:516` | conmutador | ocasional | siempre |
| Añadir / Quitar de la biblioteca | Canción > menú > grupo 2 | `ui/menu/SongMenu.kt:552-557` | conmutador | diaria | siempre |
| Eliminar del historial | Canción > menú > grupo 2 | `ui/menu/SongMenu.kt:588` | destructiva | rara | **solo si el menú se abrió desde el Historial** |
| Quitar de la lista de reproducción | Canción > menú > grupo 2 | `ui/menu/SongMenu.kt:607` | destructiva | ocasional | **solo si se abrió desde una lista** |
| Eliminar de la caché | Canción > menú > grupo 2 | `ui/menu/SongMenu.kt:642` | destructiva | rara | **solo si se abrió desde la lista de Caché** |
| Eliminar descarga | Canción > menú > grupo 3 | `ui/menu/SongMenu.kt:670` | destructiva | ocasional | solo si está descargada |
| Descargando *(pulsar cancela)* | Canción > menú > grupo 3 | `ui/menu/SongMenu.kt:691` | destructiva | ocasional | solo mientras descarga |
| Descargar | Canción > menú > grupo 3 | `ui/menu/SongMenu.kt:710-711` | acción primaria | diaria | solo si no está descargada |
| Exportando / Exportado *(`onClick` vacío)* | Canción > menú > grupo 4 | `ui/menu/SongMenu.kt:746` / `:756` | informativo | rara | solo con «Exportar como MP3» activado |
| Exportar | Canción > menú > grupo 4 | `ui/menu/SongMenu.kt:766-767` | acción secundaria | rara | solo con «Exportar como MP3» activado (OFF por defecto) |
| Establecer como tono de llamada | Canción > menú > grupo 5 | `ui/menu/SongMenu.kt:804` | acción secundaria | rara | siempre |
| Ver artista | Canción > menú > grupo 6 | `ui/menu/SongMenu.kt:831-832` | navegación | ocasional | siempre |
| Ver álbum | Canción > menú > grupo 6 | `ui/menu/SongMenu.kt:856-859` | navegación | ocasional | solo si se resuelve el álbum |
| Cargar de nuevo | Canción > menú > grupo 6 | `ui/menu/SongMenu.kt:877-878` | acción secundaria | rara | siempre |
| Detalles | Canción > menú > grupo 6 | `ui/menu/SongMenu.kt:913-914` | informativo | ocasional | siempre |
| Diálogo **Editar canción**: campos Título y Nombre del artista | Canción > menú > Editar | `ui/menu/SongMenu.kt:186-224` | acción primaria | ocasional | al pulsar Editar |
| Diálogo **elegir artista** | Canción > menú > Ver artista | `ui/menu/SongMenu.kt:282-330` | navegación | ocasional | solo con más de un artista |

## 9.2 Canción online — `YouTubeSongMenu`

Idéntico al anterior salvo lo indicado. Alcanzable desde Búsqueda online, Inicio, Charts, Historial,
Artista, Lista online, Explorar, y desde un **enlace compartido** (`MainActivity.kt:1709`).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Me gusta *(corazón)* | Canción online > cabecera | `ui/menu/YouTubeSongMenu.kt:243` | conmutador | constante | siempre |
| Iniciar radio | Canción online > rejilla | `ui/menu/YouTubeSongMenu.kt:300` | acción primaria | diaria | solo si no eres invitado |
| Añadir a lista de reproducción | Canción online > rejilla | `ui/menu/YouTubeSongMenu.kt:316` | acción primaria | diaria | siempre |
| Compartir | Canción online > rejilla | `ui/menu/YouTubeSongMenu.kt:330` | acción secundaria | ocasional | siempre |
| Sugerir alojar | Canción online > grupo 1 | `ui/menu/YouTubeSongMenu.kt:352` | acción primaria | rara | solo como invitado en una sala |
| Reproducir a continuación | Canción online > grupo 1 | `ui/menu/YouTubeSongMenu.kt:376-377` | acción primaria | diaria | solo si no eres invitado |
| Añadir a la cola | Canción online > grupo 1 | `ui/menu/YouTubeSongMenu.kt:392-393` | acción primaria | diaria | solo si no eres invitado |
| **«Menos de esto»** *(hardcoded)* — «Se recomendará menos parecido a esto» | Canción online > grupo 1 | `ui/menu/YouTubeSongMenu.kt:410-411` | conmutador (feedback) | ocasional | siempre; toast «Se mostrará menos de esto» `:424` |
| Eliminar del historial | Canción online > grupo 2 | `ui/menu/YouTubeSongMenu.kt:442` | destructiva | rara | solo si el ítem trae token de historial |
| Fijar / Quitar de marcación rápida | Canción online > grupo 2 | `ui/menu/YouTubeSongMenu.kt:464` | conmutador | ocasional | siempre |
| Añadir / Quitar de la biblioteca | Canción online > grupo 2 | `ui/menu/YouTubeSongMenu.kt:488-490` | conmutador | diaria | siempre |
| Eliminar descarga / Descargando / Descargar | Canción online > grupo 3 | `ui/menu/YouTubeSongMenu.kt:537` / `:558` / `:577-578` | destructiva / destructiva / primaria | diaria | según estado |
| Exportando / Exportado / Exportar | Canción online > grupo 4 | `ui/menu/YouTubeSongMenu.kt:615` / `:625` / `:635-636` | acción secundaria | rara | solo con «Exportar como MP3» activado |
| Establecer como tono de llamada | Canción online > grupo 5 | `ui/menu/YouTubeSongMenu.kt:673` | acción secundaria | rara | siempre |
| Ver artista | Canción online > grupo 6 | `ui/menu/YouTubeSongMenu.kt:701-702` | navegación | ocasional | solo si hay artistas |
| Ver álbum | Canción online > grupo 6 | `ui/menu/YouTubeSongMenu.kt:727-728` | navegación | ocasional | solo si se resuelve el álbum |
| Detalles | Canción online > grupo 6 | `ui/menu/YouTubeSongMenu.kt:744-745` | informativo | ocasional | siempre |

## 9.3 Álbum local — `AlbumMenu`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Guardar álbum *(corazón; descarga automática si el ajuste lo pide)* | Álbum > menú > cabecera | `ui/menu/AlbumMenu.kt:318` | conmutador | diaria | siempre |
| Reproducir | Álbum > menú > rejilla | `ui/menu/AlbumMenu.kt:372` | acción primaria | diaria | solo si no eres invitado |
| Aleatorio | Álbum > menú > rejilla | `ui/menu/AlbumMenu.kt:395` | acción primaria | diaria | solo si no eres invitado; puede abrir el diálogo de memoria de aleatorio |
| Compartir | Álbum > menú > rejilla | `ui/menu/AlbumMenu.kt:408` | acción secundaria | ocasional | siempre |
| Reproducir a continuación | Álbum > menú > grupo 1 | `ui/menu/AlbumMenu.kt:429-430` | acción primaria | diaria | solo si no eres invitado |
| Añadir a la cola | Álbum > menú > grupo 1 | `ui/menu/AlbumMenu.kt:445-446` | acción primaria | diaria | solo si no eres invitado |
| Añadir a lista de reproducción | Álbum > menú > grupo 1 | `ui/menu/AlbumMenu.kt:460-461` | acción primaria | diaria | siempre |
| Fijar / Quitar de marcación rápida | Álbum > menú > grupo 1 | `ui/menu/AlbumMenu.kt:475` | conmutador | ocasional | siempre |
| Eliminar descarga / Descargando / Descargar | Álbum > menú > grupo 2 | `ui/menu/AlbumMenu.kt:519` / `:542` / `:563-564` | destructiva / destructiva / primaria | diaria | según estado |
| Ver artista | Álbum > menú > grupo 3 | `ui/menu/AlbumMenu.kt:600-601` | navegación | ocasional | siempre |
| Cargar de nuevo | Álbum > menú > grupo 3 | `ui/menu/AlbumMenu.kt:622-623` | acción secundaria | rara | siempre |

*(La rejilla superior pasa de 3 a 1 columna si eres invitado de Escuchar juntos, `ui/menu/AlbumMenu.kt:421`.)*

## 9.4 Álbum online — `YouTubeAlbumMenu`

Igual que el anterior, más: **No recomendar** («Ocultar este álbum de tus recomendaciones»,
`ui/menu/YouTubeAlbumMenu.kt:412-413`, destructiva, rara) y **Ver álbum** (`:557-558`).
Guardar `:251`, Reproducir `:298`, Aleatorio `:317`, Compartir `:349`, Reproducir a continuación `:375-376`,
Añadir a la cola `:394-395`, Añadir a lista `:428-429`, Fijar `:443`,
descarga `:477`/`:500`/`:521-522`, Ver artista `:580-581` (solo si el álbum trae artistas).

## 9.5 Artista local — `ArtistMenu`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Reproducir | Artista > menú | `ui/menu/ArtistMenu.kt:148` | acción primaria | diaria | solo si no eres invitado **y** el artista tiene canciones |
| Aleatorio | Artista > menú | `ui/menu/ArtistMenu.kt:179` | acción primaria | diaria | igual; puede abrir el diálogo de memoria de aleatorio |
| Fijar / Quitar de marcación rápida | Artista > menú | `ui/menu/ArtistMenu.kt:196` | conmutador | ocasional | siempre |
| Compartir | Artista > menú | `ui/menu/ArtistMenu.kt:229` | acción secundaria | ocasional | **solo si es artista de YouTube** |
| Suscribirme / Suscrito | Artista > menú | `ui/menu/ArtistMenu.kt:256` | conmutador | ocasional | siempre |

## 9.6 Artista online — `YouTubeArtistMenu`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Iniciar radio | Artista online > menú | `ui/menu/YouTubeArtistMenu.kt:98` | acción primaria | diaria | solo si no eres invitado y el artista trae endpoint de radio |
| Fijar / Quitar de marcación rápida | Artista online > menú | `ui/menu/YouTubeArtistMenu.kt:118` | conmutador | ocasional | siempre |
| Compartir | Artista online > menú | `ui/menu/YouTubeArtistMenu.kt:142` | acción secundaria | ocasional | siempre |
| Suscribirme / Suscrito | Artista online > menú | `ui/menu/YouTubeArtistMenu.kt:165` | conmutador | ocasional | siempre |
| No recomendar | Artista online > menú | `ui/menu/YouTubeArtistMenu.kt:198-199` | destructiva | rara | siempre |

## 9.7 Lista local — `PlaylistMenu`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Guardar lista *(corazón)* | Lista > menú > cabecera | `ui/menu/PlaylistMenu.kt:421` | conmutador | ocasional | **solo si la lista NO es editable** (listas ajenas o suscritas) |
| Reproducir | Lista > menú > rejilla | `ui/menu/PlaylistMenu.kt:469` | acción primaria | diaria | solo si no eres invitado |
| Aleatorio | Lista > menú > rejilla | `ui/menu/PlaylistMenu.kt:491` | acción primaria | constante | igual; puede abrir el diálogo de memoria de aleatorio |
| Compartir | Lista > menú > rejilla | `ui/menu/PlaylistMenu.kt:504` | acción secundaria | ocasional | siempre |
| **«Exportar playlist»** *(hardcoded)* — «Guardar las canciones en un archivo» (JSON) | Lista > menú > grupo 1 | `ui/menu/PlaylistMenu.kt:526-527` | acción secundaria | rara | siempre |
| **«Exportar CSV»** *(hardcoded)* — «Título y artistas (para hojas de cálculo)» | Lista > menú > grupo 1 | `ui/menu/PlaylistMenu.kt:542-543` | acción secundaria | rara | siempre |
| Iniciar radio | Lista > menú > grupo 1 | `ui/menu/PlaylistMenu.kt:560-561` | acción primaria | ocasional | solo si no eres invitado **y** la lista está sincronizada |
| Sincronizar ahora | Lista > menú > grupo 1 | `ui/menu/PlaylistMenu.kt:589-590` | acción secundaria | ocasional | igual; sin sesión muestra «Inicia sesión en YouTube Music» |
| Reproducir a continuación | Lista > menú > grupo 1 | `ui/menu/PlaylistMenu.kt:633-634` | acción primaria | diaria | solo si no eres invitado |
| Añadir a la cola | Lista > menú > grupo 1 | `ui/menu/PlaylistMenu.kt:653-654` | acción primaria | diaria | solo si no eres invitado |
| Editar *(«Cambiar el título o el artista»)* | Lista > menú > grupo 2 | `ui/menu/PlaylistMenu.kt:680-681` | navegación | ocasional | solo si la lista es editable, no es auto-lista y no eres invitado |
| Fijar / Quitar de marcación rápida | Lista > menú > grupo 2 | `ui/menu/PlaylistMenu.kt:698` | conmutador | ocasional | siempre |
| Eliminar descarga / Descargando / Descargar | Lista > menú > grupo 2 | `ui/menu/PlaylistMenu.kt:734` / `:750` / `:764-765` | destructiva / destructiva / primaria | diaria | según estado |
| **Eliminar** *(«Eliminar este elemento de forma permanente»)* | Lista > menú > grupo 2 | `ui/menu/PlaylistMenu.kt:796-797` | destructiva | ocasional | solo si no es auto-lista y no eres invitado |
| Diálogo **Editar lista**: campo + Aceptar/Cancelar | Lista > menú > Editar | `ui/menu/PlaylistMenu.kt:235-260` | acción primaria | ocasional | al pulsar Editar |
| Diálogo **Eliminar descarga**: Cancelar / Aceptar | Lista > menú | `ui/menu/PlaylistMenu.kt:266-305` | destructiva | ocasional | al pulsar Eliminar descarga |
| Diálogo **Eliminar lista**: **«Eliminar también de YouTube»** / **«Solo eliminar de la app»** / Cancelar / Aceptar | Lista > menú > Eliminar | `ui/menu/PlaylistMenu.kt:311-415` (`:386`, `:392`, `:402`, `:410`) | destructiva | ocasional | las dos primeras solo si la lista está sincronizada |

## 9.8 Lista online — `YouTubePlaylistMenu`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Guardar lista *(corazón)* | Lista online > cabecera | `ui/menu/YouTubePlaylistMenu.kt:150` | conmutador | diaria | solo si no es «Me gusta» y no es editable |
| Reproducir | Lista online > rejilla | `ui/menu/YouTubePlaylistMenu.kt:363` | acción primaria | diaria | solo si no eres invitado y hay endpoint |
| Aleatorio | Lista online > rejilla | `ui/menu/YouTubePlaylistMenu.kt:382` | acción primaria | diaria | igual |
| Iniciar radio | Lista online > rejilla | `ui/menu/YouTubePlaylistMenu.kt:401` | acción primaria | ocasional | igual |
| No recomendar | Lista online > grupo 1 | `ui/menu/YouTubePlaylistMenu.kt:419-420` | destructiva | rara | siempre |
| Reproducir a continuación | Lista online > grupo 1 | `ui/menu/YouTubePlaylistMenu.kt:436-437` | acción primaria | diaria | solo si no eres invitado |
| Añadir a la cola | Lista online > grupo 1 | `ui/menu/YouTubePlaylistMenu.kt:469-470` | acción primaria | diaria | solo si no eres invitado |
| Añadir a lista de reproducción | Lista online > grupo 1 | `ui/menu/YouTubePlaylistMenu.kt:498-499` | acción primaria | diaria | siempre |
| Fijar / Quitar de marcación rápida | Lista online > grupo 1 | `ui/menu/YouTubePlaylistMenu.kt:513` | conmutador | ocasional | siempre |
| Eliminar descarga / Descargando / Descargar | Lista online > grupo 2 | `ui/menu/YouTubePlaylistMenu.kt:549` / `:565` / `:579-580` | destructiva / destructiva / primaria | diaria | solo si la lista trae canciones |
| Compartir | Lista online > grupo 2 | `ui/menu/YouTubePlaylistMenu.kt:609-610` | acción secundaria | ocasional | siempre |
| Seleccionar todo | Lista online > grupo 2 | `ui/menu/YouTubePlaylistMenu.kt:631` | acción secundaria | — | ⚠️ **MUERTO — ver §23** |

## 9.9 Menús ⋯ de la cabecera de las pantallas de lista

### Lista local (`LocalPlaylistMenu`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Editar | Lista local > ⋯ | `ui/menu/PlaylistScreenMenus.kt:95-96` | navegación | ocasional | siempre |
| **Editar con IA** *(«Describe un cambio y revísalo antes de aplicarlo»)* | Lista local > ⋯ | `ui/menu/PlaylistScreenMenus.kt:113-114` | acción primaria | rara | solo con IA activada, lista editable y **puramente local** (no sincronizada) |
| Sincronizar | Lista local > ⋯ | `ui/menu/PlaylistScreenMenus.kt:133-134` | acción secundaria | ocasional | solo si es lista de YouTube |
| Añadir a la cola | Lista local > ⋯ | `ui/menu/PlaylistScreenMenus.kt:152-153` | acción primaria | diaria | solo si no eres invitado |
| Eliminar descarga / Descargando / Descargar | Lista local > ⋯ | `ui/menu/PlaylistScreenMenus.kt:47-48` / `:61-62` / `:75-76` | destructiva / destructiva / primaria | diaria | según estado |
| Compartir | Lista local > ⋯ | `ui/menu/PlaylistScreenMenus.kt:172-173` | acción secundaria | ocasional | siempre |
| **Eliminar** | Lista local > ⋯ | `ui/menu/PlaylistScreenMenus.kt:200-201` | destructiva | ocasional | siempre |

### Auto-listas (`AutoPlaylistMenu`) — Me gustan / Descargadas / Subidas

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Sincronizar *(«Traer los últimos cambios de YouTube Music», hardcoded)* | Auto-lista > ⋯ | `ui/menu/PlaylistScreenMenus.kt:280-281` | acción secundaria | ocasional | solo en Me gustan / Subidas |
| Añadir a la cola | Auto-lista > ⋯ | `ui/menu/PlaylistScreenMenus.kt:296-297` | acción primaria | diaria | solo si no eres invitado |
| Eliminar descarga / Descargando / Descargar | Auto-lista > ⋯ | `ui/menu/PlaylistScreenMenus.kt:233` / `:247` / `:261` | destructiva / destructiva / primaria | diaria | según estado |

### Top N (`TopPlaylistMenu`) y Caché (`CachePlaylistMenu`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Añadir a la cola | Top > ⋯ | `ui/menu/PlaylistScreenMenus.kt:375-376` | acción primaria | diaria | solo si no eres invitado |
| Eliminar descarga / Descargando / Descargar | Top > ⋯ | `ui/menu/PlaylistScreenMenus.kt:328` / `:342` / `:356` | destructiva / destructiva / primaria | diaria | según estado |
| Añadir a la cola | Caché > ⋯ | `ui/menu/PlaylistScreenMenus.kt:454-455` | acción primaria | diaria | solo si no eres invitado |
| Descargar | Caché > ⋯ | `ui/menu/PlaylistScreenMenus.kt:435` | acción primaria | ocasional | en la práctica es la **única** opción de descarga que aparece aquí (ver §23) |

## 9.10 Menús de selección múltiple

### Canciones locales (`SelectionSongMenu`) — Álbum, Auto-lista, Caché, Lista local, Top

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Reproducir | Selección > rejilla | `ui/menu/SelectionSongsMenu.kt:210` | acción primaria | ocasional | solo si no eres invitado |
| Aleatorio | Selección > rejilla | `ui/menu/SelectionSongsMenu.kt:231` | acción primaria | ocasional | igual |
| Añadir a lista de reproducción | Selección > rejilla | `ui/menu/SelectionSongsMenu.kt:252` | acción primaria | ocasional | siempre |
| Reproducir a continuación | Selección > grupo 1 | `ui/menu/SelectionSongsMenu.kt:267-268` | acción primaria | ocasional | solo si no eres invitado |
| Aleatorio *(duplicado de la rejilla; descripción equivocada)* | Selección > grupo 1 | `ui/menu/SelectionSongsMenu.kt:284-285` | acción primaria | ocasional | igual |
| Añadir a la cola | Selección > grupo 1 | `ui/menu/SelectionSongsMenu.kt:306-307` | acción primaria | ocasional | igual |
| Añadir a lista de reproducción *(duplicado de la rejilla)* | Selección > grupo 1 | `ui/menu/SelectionSongsMenu.kt:324-325` | acción primaria | ocasional | siempre |
| Añadir / Quitar de la biblioteca | Selección > grupo 1 | `ui/menu/SelectionSongsMenu.kt:341-343` | conmutador | ocasional | siempre |
| Eliminar descarga / Descargando / Descargar | Selección > grupo 2 | `ui/menu/SelectionSongsMenu.kt:400` / `:416` / `:430` | destructiva / destructiva / primaria | ocasional | según estado |
| Me gusta a todo / No me gusta a todo | Selección > grupo 2 | `ui/menu/SelectionSongsMenu.kt:461-463` | conmutador | ocasional | siempre |
| **Eliminar** | Selección > grupo 2 | `ui/menu/SelectionSongsMenu.kt:492` | destructiva | ocasional | **solo si se abrió desde una lista de reproducción** |

### Cola e Historial (`SelectionMediaMetadataMenu`) — sin rejilla superior

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Eliminar** | Selección (Cola/Historial) | `ui/menu/SelectionSongsMenu.kt:653` | destructiva | ocasional | solo desde la Cola y si no eres invitado |
| Reproducir | Selección (Cola/Historial) | `ui/menu/SelectionSongsMenu.kt:679` | acción primaria | ocasional | solo si no eres invitado |
| Aleatorio | Selección (Cola/Historial) | `ui/menu/SelectionSongsMenu.kt:700` | acción primaria | ocasional | igual |
| Reproducir a continuación | Selección (Cola/Historial) | `ui/menu/SelectionSongsMenu.kt:722` | acción primaria | ocasional | igual |
| Añadir a la cola | Selección (Cola/Historial) | `ui/menu/SelectionSongsMenu.kt:738` | acción primaria | ocasional | igual |
| Añadir a lista de reproducción | Selección (Cola/Historial) | `ui/menu/SelectionSongsMenu.kt:756` | acción primaria | ocasional | siempre |
| Me gusta a todo | Selección (Cola/Historial) | `ui/menu/SelectionSongsMenu.kt:781` | conmutador | ocasional | siempre — ⚠️ el texto nunca cambia a «No me gusta a todo» aunque el icono sí |
| Eliminar descarga / Descargando / Descargar | Selección (Cola/Historial) | `ui/menu/SelectionSongsMenu.kt:824` / `:845` / `:859` | destructiva / destructiva / primaria | ocasional | según estado |

### Canciones online (`YouTubeSelectionSongMenu`) — solo Lista online

Reproducir `:220`, Aleatorio `:234`, Reproducir a continuación `:248`, Añadir a la cola `:257`,
Añadir a lista `:266`, Añadir/Quitar de la biblioteca `:274-276`,
descarga `:325`/`:341`/`:355`, Me gusta a todo / No me gusta a todo `:386-388`
(todos en `ui/menu/YouTubeSelectionSongMenu.kt`; los de cola requieren no ser invitado).

## 9.11 Diálogos de listas

### «Añadir a lista de reproducción» (`AddToPlaylistDialog`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Crear lista de reproducción** | Añadir a lista > primera fila | `ui/menu/AddToPlaylistDialog.kt:133` | acción primaria | ocasional | siempre |
| **Buscar** *(campo)* | Añadir a lista | `ui/menu/AddToPlaylistDialog.kt:156` | acción secundaria | diaria | solo si hay listas |
| Limpiar búsqueda ✕ | Añadir a lista | `ui/menu/AddToPlaylistDialog.kt:170` | acción secundaria | ocasional | solo con texto escrito |
| Ordenar: **Fecha añadida / Nombre / Número de canciones / Fecha de actualización** + invertir ↑↓ | Añadir a lista > cabecera | `ui/menu/AddToPlaylistDialog.kt:202-212` | conmutador | ocasional | solo si hay listas |
| Fila de lista *(pulsable)* | Añadir a lista > cuerpo | `ui/menu/AddToPlaylistDialog.kt:234-257` | acción primaria | diaria | siempre |
| Diálogo **Duplicados**: **Saltar duplicados** / **Añadir de todas formas** / Cancelar | Añadir a lista > Duplicados | `ui/menu/AddToPlaylistDialog.kt:299` / `:322` / `:330` | acción primaria | ocasional | solo si la lista ya contiene esas canciones |

### «Añadir a lista» versión online (`AddToPlaylistDialogOnline`)

Mismos controles (`ui/menu/AddToPlaylistDialogOnline.kt:133-297`) más una fila final
**«Canciones que te gustan»** (`:362`, marca «me gusta» en vez de añadir a una lista) y una nota sobre
listas sincronizadas (`:377`). Se usa desde la selección múltiple online y desde la importación de Spotify.

### «Crear lista de reproducción» (`CreatePlaylistDialog`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Crear lista de reproducción *(campo + Aceptar/Cancelar)* | Crear lista | `ui/component/CreatePlaylistDialog.kt:75` | acción primaria | diaria | siempre |
| **Sincronizar lista de reproducción** *(interruptor)* — «Esto NO se puede cambiar más tarde» | Crear lista | `ui/component/CreatePlaylistDialog.kt:123-135` | conmutador | ocasional | **solo si la sincronización global con YouTube Music está DESACTIVADA**; con ella activa el interruptor no sale y la lista se sube en silencio |

### «Lista AI» (`AiPlaylistDialog`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Lista AI *(título)* | Lista AI | `ui/component/AiPlaylistDialog.kt:104` | informativo | ocasional | siempre |
| «Describe tu playlist (ej. rock para correr de noche)» *(campo)* | Lista AI | `ui/component/AiPlaylistDialog.kt:110` | acción primaria | ocasional | deshabilitado mientras genera |
| Número de canciones: **10 / 20 / 30 / 50** *(chips)* | Lista AI | `ui/component/AiPlaylistDialog.kt:118-129` | conmutador | ocasional | igual |
| «Consultando a la IA…» / «Buscando canciones %1$d/%2$d…» | Lista AI | `ui/component/AiPlaylistDialog.kt:135` / `:140` | informativo | ocasional | mientras genera |
| **Abrir Ajustes de IA** | Lista AI | `ui/component/AiPlaylistDialog.kt:155` | navegación | rara | solo con error de servicio o proveedor no compatible |
| **Generar** | Lista AI | `ui/component/AiPlaylistDialog.kt:171` | acción primaria | ocasional | habilitado con texto y sin generación en curso |
| Cancelar | Lista AI | `ui/component/AiPlaylistDialog.kt:179` | acción secundaria | ocasional | deshabilitado mientras genera |

### Portada personalizada (`CustomThumbnailMenu`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Elija de la biblioteca | Lista local > lápiz de la portada | `ui/menu/CustomThumbnailMenu.kt:40` | acción primaria | rara | **solo si la lista es editable Y ya tiene portada personalizada**; si no, el lápiz abre el selector directamente |
| Eliminar imagen personalizada | Lista local > lápiz de la portada | `ui/menu/CustomThumbnailMenu.kt:57` | destructiva | rara | igual |

### Importación CSV (`CsvColumnMappingDialog`) — desde Ajustes > Copia de seguridad

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Mapear columnas CSV *(título)* | Importar CSV | `ui/menu/CsvColumnMappingDialog.kt:70` | informativo | rara | siempre |
| **«Preview»** *(hardcoded, en inglés)* | Importar CSV | `ui/menu/CsvColumnMappingDialog.kt:86` | informativo | rara | solo con filas de muestra |
| La primera fila es el encabezado *(casilla)* | Importar CSV | `ui/menu/CsvColumnMappingDialog.kt:140-145` | conmutador | rara | siempre |
| Columna de nombre del artista / de título / de URL de YouTube (opcional) | Importar CSV | `ui/menu/CsvColumnMappingDialog.kt:153` / `:160` / `:167` | conmutador | rara | siempre |
| **Ninguno** *(dentro del selector de URL)* | Importar CSV | `ui/menu/CsvColumnMappingDialog.kt:234`/`:241` | conmutador | rara | solo en el selector de URL |
| **Col %d** *(un botón por columna detectada)* | Importar CSV | `ui/menu/CsvColumnMappingDialog.kt:253`/`:263` | conmutador | rara | una por columna |
| Cancelar / **Continuar** | Importar CSV | `ui/menu/CsvColumnMappingDialog.kt:182` / `:197` | secundaria / primaria | rara | siempre |
| Diálogo de progreso «Importar CSV» + % + «Recientemente convertidos» | Importar CSV | `ui/menu/CsvColumnMappingDialog.kt:295-324` | informativo | rara | ⚠️ **no se puede cerrar manualmente** (sin botones, sin descarte) |

## 9.12 Controles que aportan las filas y tarjetas compartidas (`Items.kt`)

Estas piezas se reutilizan en TODAS las listas de la app. Si el rediseño cambia `Items.kt`, cambia
la app entera de golpe.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Ranura del botón **⋯** (`trailingContent`) | Todas las filas | `ui/component/Items.kt:146, 453, 609, 706, 858, 1023, 1077` | acción secundaria | constante | siempre |
| Toque en la portada de la fila (`onThumbnailClick`) | «Añadir música» | `ui/component/Items.kt:487` / `:1133` | acción secundaria | ocasional | **solo lo usa la hoja «Añadir música»**, donde alterna la previsualización |
| **Deslizar fila a la derecha → «Reproducir a continuación»** *(+ toast)* | Todas las listas de canciones | `ui/component/Items.kt:1732-1736` | acción primaria | ocasional | **solo si «Deslizar canción» está ON (OFF por defecto)** |
| **Deslizar fila a la izquierda → «Añadir a la cola»** *(+ toast)* | Todas las listas de canciones | `ui/component/Items.kt:1738-1742` | acción primaria | ocasional | igual |
| Botón ▶ sobre la portada del álbum | Rejilla de álbumes | `ui/component/Items.kt:807-819` (def. `:1679-1706`) | acción primaria | diaria | solo si el álbum no está sonando |
| Botón ▶ sobre la portada del álbum online | Rejilla online | `ui/component/Items.kt:1243-1261` | acción primaria | diaria | solo si es álbum y no está sonando |
| Botón ▶ **decorativo** (sin acción) | Tarjetas de canción y de miniatura local | `ui/component/Items.kt:584`, `:1238`, `:1485-1508`, `:1510-1533` | informativo | constante | ⚠️ no es pulsable; el toque lo captura la tarjeta |
| Botón lápiz sobre la portada | Lista local | `ui/component/Items.kt:1646-1676` | acción secundaria | rara | solo en la portada de una lista editable |
| Insignia **LOSSLESS** *(hardcoded)* | Fila de canción | `ui/component/Items.kt:405` | informativo | ocasional | solo si el códec es FLAC |
| Insignia **320KBPS** *(hardcoded)* | Fila de canción | `ui/component/Items.kt:419` | informativo | ocasional | solo si es AAC ≥ 320 kbps |
| Insignias: corazón, explícito (E), en biblioteca, descarga/spinner, sin conexión | Todas las filas | `ui/component/Items.kt:434-445`, `:188`, `:1815-1858` | informativo | constante | según estado |
| **Check «Ya reproducida en aleatorio»** *(hardcoded)* + fila atenuada al 50 % | Fila de canción | `ui/component/Items.kt:496-503`, alpha `:507` | informativo | diaria | solo con el Aleatorio Mejorado y si no es la canción activa |
| Check de selección sobre la portada | Todas las filas | `ui/component/Items.kt:1409-1423` | informativo | ocasional | solo en modo selección |
| «%d canción / %d canciones» | Artista, Álbum, Lista | `ui/component/Items.kt:612, 644, 711, 866-876, 959-969` | informativo | constante | siempre |
| Placeholder de portada de auto-lista: Canciones que me gustan / Descargado / En caché / Subidas | Listas | `ui/component/Items.kt:887-892`, `:989-994` | informativo | ocasional | según la auto-lista |

---

# 10. INICIO

Ruta `home`. Todas las secciones son **condicionales**: cada una aparece solo si su fuente de datos trae
algo. Una captura de pantalla nunca muestra más de la mitad.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Chips de estado de ánimo de YouTube *(texto dinámico)* | Inicio > fila de chips | `ui/screens/HomeScreen.kt:1094-1100` | conmutador | diaria | solo si YouTube devuelve chips |
| **Tus podcasts** *(cabecera)* | Inicio > podcasts fijados | `ui/screens/HomeScreen.kt:1107` | informativo | diaria | solo con podcasts fijados |
| Tarjeta de podcast fijado → `podcasts?feedUrl=` | Inicio > podcasts fijados | `ui/screens/HomeScreen.kt:1121-1123` | navegación | diaria | igual |
| **Reproducido recientemente** *(cabecera)* | Inicio > recientes | `ui/screens/HomeScreen.kt:1149` | informativo | constante | solo si hay recientes |
| **Reproducir todo** | Inicio > recientes | `ui/screens/HomeScreen.kt:1152-1159` | acción primaria | diaria | igual |
| Portada reciente → reproducir / pausar | Inicio > recientes | `ui/screens/HomeScreen.kt:1180-1184` | acción primaria | diaria | igual |
| Portada reciente *(pulsación larga)* → menú de canción | Inicio > recientes | `ui/screens/HomeScreen.kt:1185-1194` | acción secundaria | ocasional | igual |
| **Marcación rápida** *(cabecera)* | Inicio > marcación rápida | `ui/screens/HomeScreen.kt:1247` | informativo | diaria | **solo si el ajuste está ON (por defecto OFF)** y hay elementos |
| Baldosa de marcación rápida → reproducir / abrir álbum, artista o lista | Inicio > marcación rápida | `ui/screens/HomeScreen.kt:1341-1354` | acción primaria | diaria | igual |
| Baldosa *(pulsación larga)* → menú según el tipo | Inicio > marcación rápida | `ui/screens/HomeScreen.kt:1355-1381` | acción secundaria | ocasional | igual |
| Insignia de fijado | Inicio > marcación rápida | `ui/component/SpeedDialGridItem.kt:59-69` | informativo | constante | solo si está fijado |
| **Botón «aleatorio» de 5 puntos** | Inicio > marcación rápida, última celda de la pág. 1 | `ui/screens/HomeScreen.kt:1297-1321` | acción primaria | ocasional | solo en la primera página; **pulsarlo mientras carga lo cancela** |
| Puntos de paginación | Inicio > marcación rápida | `ui/screens/HomeScreen.kt:1393-1415` | informativo | diaria | solo con más de una página |
| **Para ti** *(cabecera)* + **Reproducir todo** | Inicio > Para ti | `ui/screens/HomeScreen.kt:1425`, `:1428-1435` | acción primaria | diaria | solo si hay recomendaciones |
| Portada del carrusel héroe → reproducir / pausar | Inicio > Para ti | `ui/screens/HomeScreen.kt:1514-1520` | acción primaria | constante | **solo si NO está el Modo Rendimiento** |
| Portada héroe *(pulsación larga)* → menú de canción | Inicio > Para ti | `ui/screens/HomeScreen.kt:1521-1530` | acción secundaria | diaria | igual |
| Insignia «sonando» | Inicio > Para ti | `ui/screens/HomeScreen.kt:1560-1579` | informativo | diaria | solo en la canción actual |
| Portada compacta «Para ti» → reproducir / pausar | Inicio > Para ti | `ui/screens/HomeScreen.kt:1457-1460` | acción primaria | constante | **solo con el Modo Rendimiento**; sin pulsación larga |
| **De la comunidad** *(cabecera)* | Inicio > comunidad | `ui/screens/HomeScreen.kt:1611` | informativo | ocasional | solo si el Inicio no está en «solo mi gusto» |
| Tarjeta de lista comunitaria → `online_playlist/{id}` | Inicio > comunidad | `ui/screens/HomeScreen.kt:1625-1627` | navegación | ocasional | igual |
| Fila de canción dentro de la tarjeta → reproducir | Inicio > comunidad | `ui/screens/HomeScreen.kt:324` (uso `:1628-1635`) | acción primaria | ocasional | las 3 primeras |
| Reproducir lista *(solo icono)* | Inicio > comunidad | `ui/screens/HomeScreen.kt:361-377` | acción primaria | ocasional | solo si la lista trae endpoint de reproducción |
| Radio de la lista *(solo icono)* | Inicio > comunidad | `ui/screens/HomeScreen.kt:379-395` | acción secundaria | ocasional | solo si trae endpoint de radio |
| Guardar / quitar lista *(solo icono)* | Inicio > comunidad | `ui/screens/HomeScreen.kt:397-451` | conmutador | ocasional | siempre en la tarjeta |
| **Mix diario N** + **Porque escuchas %s** + **Reproducir todo** | Inicio > mixes diarios | `ui/screens/HomeScreen.kt:1648`, `:1651-1654`, `:1656-1669` | acción primaria | diaria | hasta 3 mixes; con Modo Rendimiento solo el primero |
| Tarjeta de descubrimiento diario → reproducir | Inicio > mixes diarios | `ui/screens/HomeScreen.kt:1729-1740` | acción primaria | constante | solo sin Modo Rendimiento |
| Tarjeta diaria *(pulsación larga)* → menú de canción online | Inicio > mixes diarios | `ui/screens/HomeScreen.kt:479-490` | acción secundaria | ocasional | igual, y solo si es canción |
| Frase «Suena como / Porque escuchas / Similar a / Basado en / Para fans de %s» | Inicio > mixes diarios | `ui/screens/HomeScreen.kt:549-567` | informativo | constante | solo si la tarjeta mide más de 200 dp |
| Portada compacta de mix diario → reproducir | Inicio > mixes diarios | `ui/screens/HomeScreen.kt:1687-1693` | acción primaria | constante | **solo con Modo Rendimiento** |
| **Seguir escuchando** + **Reproducir todo** | Inicio > seguir escuchando | `ui/screens/HomeScreen.kt:1752`, `:1755-1763` | acción primaria | diaria | solo si hay datos |
| Tarjeta ancha «▶ Título» → reproducir / abrir álbum / abrir artista | Inicio > seguir escuchando | `ui/screens/HomeScreen.kt:1804-1812` | acción primaria | diaria | igual; **sin pulsación larga, sin menú** |
| **Sus listas de reproducción de YouTube** | Inicio > listas de la cuenta | `ui/screens/HomeScreen.kt:1853` | informativo | ocasional | solo con listas de cuenta |
| Cabecera de cuenta *(avatar + nombre)* → `account` | Inicio > listas de la cuenta | `ui/screens/HomeScreen.kt:1880-1882` | navegación | ocasional | igual |
| Tarjeta de lista de la cuenta → `online_playlist/{id}` | Inicio > listas de la cuenta | `ui/screens/HomeScreen.kt:827` (uso `:1898`) | navegación | ocasional | igual |
| **Novedades de tus artistas** + tarjeta → `album/{id}` | Inicio > novedades de artistas | `ui/screens/HomeScreen.kt:1908`, `:825` (uso `:1923`) | navegación | diaria | solo si hay novedades |
| **Álbumes recién lanzados** + tarjeta → `album/{id}` | Inicio > nuevos lanzamientos | `ui/screens/HomeScreen.kt:1936`, `:825` (uso `:1951`) | navegación | diaria | solo sin Modo Rendimiento |
| **Mix de la mañana / de la tarde / de la noche** + **Reproducir todo** | Inicio > mix por hora | `ui/screens/HomeScreen.kt:1962-1968`, `:1972-1980` | acción primaria | diaria | el texto cambia con la franja horaria |
| Tarjeta local del mix por hora → reproducir *(y pulsación larga → menú)* | Inicio > mix por hora | `ui/screens/HomeScreen.kt:728-748` (uso `:1995`) | acción primaria | diaria | igual |
| Título de la lista de IA → `local_playlist/{id}` | Inicio > Recomendado (IA) | `ui/screens/HomeScreen.kt:2019-2024` | navegación | diaria | solo si hay recomendaciones de IA |
| **«Actualizado: hace X»** *(hardcoded)* | Inicio > Recomendado (IA) | `ui/screens/HomeScreen.kt:2012-2018` | informativo | constante | solo si hay marca de tiempo |
| **Reproducir todo** + tarjetas | Inicio > Recomendado (IA) | `ui/screens/HomeScreen.kt:2025-2032`, `:2048` | acción primaria | diaria | igual |
| **Tu mix de %s** + **Reproducir todo** + tarjetas | Inicio > mix de género | `ui/screens/HomeScreen.kt:2058`, `:2062-2069`, `:2084` | acción primaria | diaria | solo si hay mix de género |
| **Favoritos olvidados** + **Reproducir todo** | Inicio > favoritos olvidados | `ui/screens/HomeScreen.kt:2094`, `:2098-2105` | acción primaria | ocasional | solo si hay datos |
| Fila → reproducir / pausar; pulsación larga → menú; **⋯** | Inicio > favoritos olvidados | `ui/screens/HomeScreen.kt:2164-2184`, `:2141-2159` | acción primaria / secundaria | ocasional | igual |
| **Similares a** + cabecera navegable + **Reproducir todo** + tarjetas | Inicio > similares | `ui/screens/HomeScreen.kt:2200-2248` | acción primaria | ocasional | máx. 3 secciones; **la cabecera solo es pulsable si la semilla es álbum o artista** |
| Cabecera de sección cruda de YouTube + navegación + **Reproducir todo** | Inicio > secciones de YouTube | `ui/screens/HomeScreen.kt:2265-2304` | navegación | ocasional | **solo si «solo mi gusto» está OFF** (por defecto está ON → ocultas) |
| Fila de canción de sección → reproducir; pulsación larga → menú; **⋯** | Inicio > secciones de YouTube | `ui/screens/HomeScreen.kt:2334-2373` | acción primaria / secundaria | ocasional | solo si la sección es 100 % canciones |
| Tarjeta mixta de sección → navegar según tipo | Inicio > secciones de YouTube | `ui/screens/HomeScreen.kt:2389` | navegación | ocasional | si la sección no es solo canciones |
| **Estado de ánimo y géneros** *(cabecera)* → `mood_and_genres` + botones de género | Inicio > géneros | `ui/screens/HomeScreen.kt:2400-2421` | navegación | ocasional | **solo si «solo mi gusto» está OFF** |
| **«Parece que no tienes conexión a internet.»** + **«Continuar sin conexión»** | Inicio > superposición sin conexión | `ui/screens/HomeScreen.kt:2504-2518` | acción primaria | rara | solo sin red y sin datos cargados |

## 10.1 Inicio sin conexión

Sustituye el cuerpo entero del Inicio cuando el modo sin conexión está activo. **Todo hardcoded.**

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **«Modo sin conexión»** *(banner, hardcoded)* | Inicio sin conexión | `ui/screens/OfflineHome.kt:166` | informativo | constante | siempre en este modo |
| **«Solo descargas. Desactívalo en Ajustes…»** *(hardcoded)* — ⚠️ **no es pulsable** | Inicio sin conexión | `ui/screens/OfflineHome.kt:173` | informativo | constante | siempre |
| **«Aún no tienes canciones descargadas…»** *(hardcoded)* — sin botón de acción | Inicio sin conexión > vacío | `ui/screens/OfflineHome.kt:83` | informativo | rara | sin descargas |
| Fila de canción → reproducir la cola «Descargas» | Inicio sin conexión | `ui/screens/OfflineHome.kt:114-126` | acción primaria | constante | con descargas |
| Fila *(pulsación larga)* → menú de canción; **⋯** | Inicio sin conexión | `ui/screens/OfflineHome.kt:127-136`, `:96-111` | acción secundaria | diaria | igual |

---

# 11. BÚSQUEDA

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Campo **«Buscar en Aura Hi-Res…»** / **«Buscar en la biblioteca…»** | Búsqueda > barra | `ui/screens/search/SearchScreen.kt:366-389` | acción primaria | constante | el texto depende de la fuente activa |
| Acción «Buscar» del teclado | Búsqueda > barra | `ui/screens/search/SearchScreen.kt:369-373` | acción primaria | constante | ⚠️ sin conexión no navega ni cierra la barra |
| Descartar / lupa *(solo icono)* | Búsqueda > barra | `ui/screens/search/SearchScreen.kt:391-404` | navegación | constante | siempre |
| Borrar texto *(solo icono)* | Búsqueda > barra | `ui/screens/search/SearchScreen.kt:409-415` | acción secundaria | diaria | solo con texto |
| **Búsqueda por voz** *(micrófono)* | Búsqueda > barra | `ui/screens/search/SearchScreen.kt:417-438` | acción primaria | ocasional | siempre |
| **Conmutar fuente Online / Local** *(solo icono)* | Búsqueda > barra | `ui/screens/search/SearchScreen.kt:439-455` | conmutador | ocasional | siempre visible; sin efecto visible en modo sin conexión |
| Pestaña **Explorar** | Búsqueda > pestañas | `ui/screens/search/SearchScreen.kt:516-522` | navegación | diaria | solo con la búsqueda inactiva |
| Pestaña **Sugerencias** | Búsqueda > pestañas | `ui/screens/search/SearchScreen.kt:523-529` | navegación | diaria | igual |
| Pestaña **Álbum** | Búsqueda > pestañas | `ui/screens/search/SearchScreen.kt:530-536` | navegación | diaria | igual |
| Diálogo **Búsqueda por voz** + **Cancelar** | Búsqueda > diálogo | `ui/screens/search/SearchScreen.kt:341-358` | informativo | rara | respaldo para Android TV |
| Baldosa de género/mood → `youtube_browse/…` | Búsqueda > Explorar | `ui/screens/search/SearchScreen.kt:637-641` | navegación | diaria | por elemento |
| **«No se pudieron cargar las recomendaciones…»** + **Reintentar** | Búsqueda > Explorar > vacío | `ui/screens/search/SearchScreen.kt:673`, `:677-679` | acción primaria | rara | solo si falla la carga |
| **«No se pudieron cargar los nuevos lanzamientos…»** / **«No hay nuevos lanzamientos por ahora.»** + **Reintentar** | Búsqueda > Álbum > vacío | `ui/screens/search/SearchScreen.kt:728-736` | acción primaria | rara | según el motivo |
| Tarjeta de álbum → `album/{id}` *(pulsación larga → menú)* | Búsqueda > Álbum | `ui/screens/search/SearchScreen.kt:762-774` | navegación | diaria | por álbum |

## 11.1 Panel de sugerencias e historial de búsqueda

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Historial de búsqueda** *(cabecera)* | Búsqueda > panel | `ui/screens/search/OnlineSearchScreen.kt:139` | informativo | constante | solo con historial |
| Entrada de historial → buscar | Búsqueda > panel | `ui/screens/search/OnlineSearchScreen.kt:153-156` | acción primaria | diaria | por entrada |
| **Borrar entrada del historial** *(solo icono)* | Búsqueda > panel | `ui/screens/search/OnlineSearchScreen.kt:392-400` | destructiva | ocasional | solo en el historial |
| Rellenar el campo con la sugerencia *(flecha diagonal)* | Búsqueda > panel | `ui/screens/search/OnlineSearchScreen.kt:403-411` | acción secundaria | ocasional | en cada fila |
| **Sugerencias** *(cabecera)* + sugerencia → buscar | Búsqueda > panel | `ui/screens/search/OnlineSearchScreen.kt:179`, `:193-196` | acción primaria | diaria | solo si YouTube devuelve sugerencias |
| **Mejor resultado** / **Desde enlace** + resultado directo | Búsqueda > panel | `ui/screens/search/OnlineSearchScreen.kt:214`, `:284-308` | acción primaria | diaria | solo si hay resultado directo |
| **⋯** y pulsación larga → menú del elemento | Búsqueda > panel | `ui/screens/search/OnlineSearchScreen.kt:236-281`, `:310-347` | acción secundaria | ocasional | en cada resultado |

## 11.2 Resultados de búsqueda online

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Campo de búsqueda + descartar + borrar + voz + acción del teclado | Resultados > barra | `ui/screens/search/OnlineSearchResult.kt:325-387` | acción primaria | constante | siempre |
| Chip **Todo** | Resultados > filtros | `ui/screens/search/OnlineSearchResult.kt:420` | conmutador | diaria | siempre |
| Chip **Canciones** | Resultados > filtros | `ui/screens/search/OnlineSearchResult.kt:421` | conmutador | diaria | siempre |
| Chip **Vídeos** | Resultados > filtros | `ui/screens/search/OnlineSearchResult.kt:424` | conmutador | ocasional | siempre (ignora a propósito el ajuste «ocultar vídeos») |
| Chip **Álbumes** | Resultados > filtros | `ui/screens/search/OnlineSearchResult.kt:425` | conmutador | diaria | siempre |
| Chip **Artistas** | Resultados > filtros | `ui/screens/search/OnlineSearchResult.kt:426` | conmutador | diaria | siempre |
| Chip **Listas de la comunidad** | Resultados > filtros | `ui/screens/search/OnlineSearchResult.kt:427` | conmutador | ocasional | siempre |
| Chip **Listas destacadas** | Resultados > filtros | `ui/screens/search/OnlineSearchResult.kt:428` | conmutador | ocasional | siempre |
| Chip **Podcasts** | Resultados > filtros | `ui/screens/search/OnlineSearchResult.kt:429` | conmutador | rara | siempre |
| Resultado → reproducir / navegar; **⋯**; pulsación larga → menú | Resultados > lista | `ui/screens/search/OnlineSearchResult.kt:292-310`, `:279-288`, `:234-266` | acción primaria / secundaria | constante | según el tipo |
| **Podcasts** *(sección)* + fila → `podcasts?feedUrl=` | Resultados > lista | `ui/screens/search/OnlineSearchResult.kt:486`, `:492-494` | navegación | rara | solo con filtro «Todo» |
| **No se han encontrado resultados** | Resultados > vacío | `ui/screens/search/OnlineSearchResult.kt:521`, `:547` | informativo | ocasional | sin resultados |

## 11.3 Búsqueda local

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Chips **Todo / Canciones / Álbumes / Artistas / Listas de reproducción** | Búsqueda local > filtros | `ui/screens/search/LocalSearchScreen.kt:131-135` | conmutador | diaria | siempre |
| Cabecera de grupo + chevron → aplica ese filtro | Búsqueda local > lista | `ui/screens/search/LocalSearchScreen.kt:145-174` | navegación | diaria | solo con el filtro «Todo» |
| Canción → reproducir la cola «Canciones buscadas» | Búsqueda local > lista | `ui/screens/search/LocalSearchScreen.kt:213-228` | acción primaria | constante | por canción |
| **⋯** y pulsación larga *(sin háptica)* → menú de canción | Búsqueda local > fila | `ui/screens/search/LocalSearchScreen.kt:190-209`, `:230-242` | acción secundaria | diaria | igual |
| Álbum / Artista / Lista → su pantalla | Búsqueda local > lista | `ui/screens/search/LocalSearchScreen.kt:252-275` | navegación | diaria | según el tipo |
| **No se han encontrado resultados** | Búsqueda local > vacío | `ui/screens/search/LocalSearchScreen.kt:286` | informativo | ocasional | sin resultados |

## 11.4 Búsqueda > pestaña Sugerencias

⚠️ Es la pantalla con más texto **sin traducir** de la app y muestra **cifras de reproducciones inventadas** (ver §23).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Deslizar hacia abajo para actualizar | Sugerencias | `ui/screens/search/suggestions/TabNewsSuggestion.kt:99-113` | acción primaria | diaria | siempre |
| **«Top global»** *(hardcoded)* + fila de canción → reproducir | Sugerencias > Top global | `ui/screens/search/suggestions/TabNewsSuggestion.kt:136-141` | acción primaria | diaria | 29 primeras |
| **«Ver más»** *(hardcoded)* → navegador | Sugerencias > Top global | `ui/screens/search/suggestions/TabNewsSuggestion.kt:142-144`, `:305` | navegación externa | rara | solo con ≥29 pistas |
| **«Apple Music Top 100»** *(hardcoded, en inglés)* + filas + **«Ver más en Apple Music»** | Sugerencias > Apple | `ui/screens/search/suggestions/TabNewsSuggestion.kt:262`, `:154-161` | acción primaria | diaria | solo con datos |
| **«#rank» + «X.XM plays»** *(hardcoded, en inglés)* | Sugerencias > filas | `ui/screens/search/suggestions/TabNewsSuggestion.kt:319-329` | informativo | constante | ⚠️ **cifras fabricadas, ver §23** |
| **«Previous» / «Next»** *(en inglés)* + **«%1$d de %2$d»** | Sugerencias > paginador | `ui/screens/search/suggestions/TabNewsSuggestion.kt:351-357` | navegación | ocasional | siempre |
| **«Álbumes populares»** *(hardcoded)* + tarjeta → `album/{id}` + **«Más»** | Sugerencias > Álbumes | `ui/screens/search/suggestions/TabNewsSuggestion.kt:424`, `:170-177` | navegación | diaria | solo con datos |
| **«Trending Artists»** *(hardcoded, en inglés)* + círculo → `artist/{id}` | Sugerencias > Artistas | `ui/screens/search/suggestions/TabNewsSuggestion.kt:372`, `:186-189` | navegación | diaria | solo con datos |
| **«Trending Music Videos»** *(en inglés)* + **«More»** + tarjeta → reproducir | Sugerencias > Vídeos | `ui/screens/search/suggestions/TabNewsSuggestion.kt:516`, `:198-205` | acción primaria | diaria | solo con datos |
| **«No suggestions available at the moment.»** *(en inglés)* + **«Actualizar»** | Sugerencias > vacío | `ui/screens/search/suggestions/TabNewsSuggestion.kt:218`, `:223-225` | acción primaria | rara | si todo falla |
| **«Data from Apple Music» / «echo-music»** *(en inglés)* | Sugerencias > pie | `ui/screens/search/suggestions/TabNewsSuggestion.kt:239`, `:244` | informativo | constante | ⚠️ «echo-music» contradice la marca «solo Aura» |

## 11.5 Hoja de región de sugerencias

⚠️ **No se abre desde Búsqueda.** Su único punto de entrada es **Ajustes > Contenido > «Región de sugerencias»**. Todo el texto está en inglés.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **«Choose Suggestions Region»** *(en inglés)* | Región de sugerencias | `ui/screens/search/suggestions/SuggestionRegionSheet.kt:52` | informativo | rara | siempre |
| **«Buscar regiones...»** *(campo, hardcoded)* | Región de sugerencias | `ui/screens/search/suggestions/SuggestionRegionSheet.kt:56-78` | acción secundaria | rara | siempre |
| **«System»** + **«Predeterminado del sistema»** | Región de sugerencias | `ui/screens/search/suggestions/SuggestionRegionSheet.kt:90`, `:98-109` | acción primaria | rara | siempre |
| **«Countries & Regions»** *(en inglés)* + fila de país | Región de sugerencias | `ui/screens/search/suggestions/SuggestionRegionSheet.kt:119`, `:130-147` | acción primaria | rara | según la búsqueda |

---

# 12. DESCUBRIMIENTO (Explorar, Géneros, Novedades, Podcasts)

## 12.1 Explorar genérico

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Título dinámico + flecha atrás *(pulsación larga = volver al inicio)* | Explorar > cabecera | `ui/screens/BrowseScreen.kt:137-147` | navegación | constante | siempre |
| Tarjeta de álbum / lista / artista → su pantalla | Explorar > rejilla | `ui/screens/BrowseScreen.kt:83-85` | navegación | constante | según el tipo |
| Pulsación larga *(sin háptica)* → menú del elemento | Explorar > rejilla | `ui/screens/BrowseScreen.kt:91-114` | acción secundaria | ocasional | igual |

## 12.2 Explorar YouTube

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Título dinámico + flecha atrás *(pulsación larga = inicio)* | Explorar YT > cabecera | `ui/screens/YouTubeBrowseScreen.kt:151-161` | navegación | constante | siempre |
| Tarjeta de canción → radio / pausar | Explorar YT > rejilla | `ui/screens/YouTubeBrowseScreen.kt:100-108` | acción primaria | constante | si es canción |
| Tarjeta de álbum / artista / lista → su pantalla | Explorar YT > rejilla | `ui/screens/YouTubeBrowseScreen.kt:109-111` | navegación | constante | según el tipo |
| Pulsación larga *(con háptica)* → menú | Explorar YT > rejilla | `ui/screens/YouTubeBrowseScreen.kt:114-143` | acción secundaria | diaria | siempre |

## 12.3 Estado de ánimo y géneros

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Estado de ánimo y géneros** + flecha atrás *(long-press = inicio)* | Géneros > cabecera | `ui/screens/MoodAndGenresScreen.kt:108-118` | navegación | constante | siempre |
| Título de bloque *(dinámico)* | Géneros | `ui/screens/MoodAndGenresScreen.kt:79-81` | informativo | constante | siempre |
| Botón de género/mood → `youtube_browse/…` | Géneros > cuadrícula | `ui/screens/MoodAndGenresScreen.kt:85-94` (clic real `:136`) | navegación | constante | 3 columnas en horizontal |

## 12.4 Podcasts

**Todo el texto está escrito a mano en Kotlin.**

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **«Podcasts»** / título del programa | Podcasts > cabecera | `ui/screens/PodcastScreen.kt:86` | informativo | constante | según haya programa abierto |
| Flecha atrás → cerrar programa o salir | Podcasts > cabecera | `ui/screens/PodcastScreen.kt:88-90` | navegación | constante | siempre |
| **Guardar** *(corazón)* | Podcasts > cabecera | `ui/screens/PodcastScreen.kt:94-100` | conmutador | ocasional | solo con programa abierto |
| Campo **«Buscar podcast»** *(hardcoded)* | Podcasts > catálogo | `ui/screens/PodcastScreen.kt:108-117` | acción primaria | diaria | solo sin programa abierto; ⚠️ **la búsqueda solo se dispara con la tecla del teclado, no al escribir** |
| Fila de resultado → abrir programa | Podcasts > resultados | `ui/screens/PodcastScreen.kt:127-129` (clic `:386`) | navegación | diaria | con texto y resultados |
| **«Lo más escuchado en: {País}»** *(hardcoded)* → diálogo de región | Podcasts > catálogo | `ui/screens/PodcastScreen.kt:133-146` | navegación | rara | con el buscador vacío |
| **«Continuar escuchando»** *(hardcoded)* + tarjeta → reanudar en su minuto | Podcasts > catálogo | `ui/screens/PodcastScreen.kt:154`, `:160-172` | acción primaria | diaria | máx. 15 episodios empezados |
| **«Guardados»** *(hardcoded)* + tarjeta → abrir | Podcasts > catálogo | `ui/screens/PodcastScreen.kt:178`, `:181` | navegación | diaria | solo con guardados |
| Cabecera de categoría + tarjeta en tendencia → abrir | Podcasts > catálogo | `ui/screens/PodcastScreen.kt:186-187` | navegación | diaria | por categoría con resultados |
| **«No se pudieron cargar los episodios.»** *(hardcoded)* — ⚠️ sin reintento | Podcasts > programa | `ui/screens/PodcastScreen.kt:201` | informativo | rara | feed sin episodios |
| **«Temporada N»** / **«Ep. N · N min»** / **«✓ Finalizado»** / **«▶ Continuar (min N)»** | Podcasts > episodio | `ui/screens/PodcastScreen.kt:208`, `:241-247` | informativo | constante | según el feed y el progreso |
| Fila de episodio → reproducir desde ahí reanudando el minuto | Podcasts > lista | `ui/screens/PodcastScreen.kt:217-230` | acción primaria | diaria | siempre |
| **«Región de los podcasts»** + fila de país + **«Cerrar»** | Podcasts > diálogo de región | `ui/screens/PodcastScreen.kt:272-291` | acción primaria | rara | con el diálogo abierto |

---

# 13. HISTORIAL, ESTADÍSTICAS, CUENTA, SESIÓN

## 13.1 Historial

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Chip **Local** | Historial > chips | `ui/screens/HistoryScreen.kt:215` | conmutador | ocasional | siempre |
| Chip **Remoto** | Historial > chips | `ui/screens/HistoryScreen.kt:216` | conmutador | ocasional | **solo con sesión de YouTube iniciada** |
| Fila remota → reproducir; pulsación larga → menú; **⋯** | Historial remoto | `ui/screens/HistoryScreen.kt:273-294`, `:251-269` | acción primaria / secundaria | diaria | solo en modo Remoto |
| **Hoy / Ayer / Esta semana / La semana pasada / `aaaa/MM`** *(cabeceras pegajosas)* | Historial local | `ui/screens/HistoryScreen.kt:302-309` | informativo | constante | solo en modo Local |
| Fila local → reproducir / marcar | Historial local | `ui/screens/HistoryScreen.kt:357-372` | acción primaria | diaria | siempre |
| Fila local *(pulsación larga)* → entra en modo selección | Historial local | `ui/screens/HistoryScreen.kt:373-379` | acción secundaria | ocasional | fuera del modo selección |
| Casilla de selección / **⋯** de fila | Historial local | `ui/screens/HistoryScreen.kt:331-334`, `:336-352` | conmutador / secundaria | diaria | según el modo |
| **FAB aleatorio** | Historial > FAB | `ui/screens/HistoryScreen.kt:392-420` | acción primaria | diaria | solo con resultados y al desplazar hacia arriba |
| **Historial** *(título)* / **%d seleccionadas** / campo **Buscar** | Historial > barra | `ui/screens/HistoryScreen.kt:452`, `:426`, `:428-450` | informativo / primaria | constante | según el modo |
| Cerrar selección / Volver *(long-press = inicio)* / Seleccionar todo / Acciones de selección / Buscar | Historial > barra | `ui/screens/HistoryScreen.kt:457-527` | navegación / conmutador / primaria | ocasional | según el modo; **acciones deshabilitadas con selección vacía** |

## 13.2 Estadísticas

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Chip desplegable de modo → **Continuo / Semanas / Meses / Años** | Estadísticas > chips | `ui/screens/StatsScreen.kt:186`, `:225-228` | conmutador | ocasional | siempre |
| Chips **1 semana / 1 mes / 3 meses / 6 meses / 1 año / Todo** | Estadísticas > periodo | `ui/screens/StatsScreen.kt:194-219` | conmutador | ocasional | **solo en modo Continuo** |
| Chips de semana / mes / año concretos | Estadísticas > periodo | `ui/screens/StatsScreen.kt:189-191` | conmutador | ocasional | según el modo y si hay historial |
| **N Canciones** + tarjeta → reproducir *(pulsación larga → menú)* | Estadísticas > canciones | `ui/screens/StatsScreen.kt:242`, `:271-292` | acción primaria | diaria | siempre |
| **N Artistas** + tarjeta → `artist/{id}` *(pulsación larga → menú)* | Estadísticas > artistas | `ui/screens/StatsScreen.kt:302`, `:328-340` | navegación | ocasional | siempre |
| **N Álbumes** + tarjeta → `album/{id}` *(pulsación larga → menú)* | Estadísticas > álbumes | `ui/screens/StatsScreen.kt:350`, `:380-392` | navegación | ocasional | solo si hay álbumes |
| **FAB aleatorio** | Estadísticas > FAB | `ui/screens/StatsScreen.kt:404-416` | acción primaria | diaria | solo con canciones y al desplazar hacia arriba |
| Volver *(long-press = inicio)* | Estadísticas > barra | `ui/screens/StatsScreen.kt:422-430` | navegación | diaria | siempre |
| **Resumen de escucha** *(icono historial)* → hoja | Estadísticas > barra | `ui/screens/StatsScreen.kt:433-440` | acción secundaria | ocasional | siempre |

### Hoja «Resumen de escucha»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Resumen de escucha** + etiqueta de periodo | Resumen > cabecera | `ui/screens/ActivityHistory.kt:75`, `:83` | informativo | constante | con la hoja abierta |
| **Cerrar** | Resumen > cabecera | `ui/screens/ActivityHistory.kt:91-97` | navegación | ocasional | siempre |
| **Tiempo total de escucha** + duración del periodo | Resumen > tarjeta principal | `ui/screens/ActivityHistory.kt:122`, `:131` | informativo | constante | siempre |
| Segmentos **Canciones / Artistas / Álbumes** | Resumen > fila segmentada | `ui/screens/ActivityHistory.kt:169-333` | informativo | constante | siempre |
| **Desde siempre** + tiempo total histórico | Resumen > tarjeta inferior | `ui/screens/ActivityHistory.kt:369-403` | informativo | constante | siempre |

## 13.3 Cuenta

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Chips **Listas de reproducción / Álbumes / Artistas** | Cuenta > chips | `ui/screens/AccountScreen.kt:73-75` | conmutador | ocasional | siempre |
| Tarjeta de lista / álbum / artista → su pantalla *(pulsación larga → menú)* | Cuenta > cuadrícula | `ui/screens/AccountScreen.kt:93-176` | navegación / secundaria | diaria | según el chip activo |
| Volver *(long-press = inicio)* | Cuenta > barra | `ui/screens/AccountScreen.kt:195-203` | navegación | diaria | siempre |

## 13.4 Inicio de sesión de YouTube

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| WebView de Google *(email, contraseña, 2FA — contenido remoto)* | Sesión > cuerpo | `ui/screens/LoginScreen.kt:124-252` | acción primaria | rara | siempre |
| Zoom con pellizco | Sesión > cuerpo | `ui/screens/LoginScreen.kt:229-231` | acción secundaria | rara | siempre |
| Volver *(long-press = inicio)* | Sesión > barra | `ui/screens/LoginScreen.kt:257-265` | navegación | rara | siempre |
| **Usar cuenta del teléfono** → selector del sistema | Sesión > barra | `ui/screens/LoginScreen.kt:271-273` | acción secundaria | rara | siempre; ⚠️ **falla en silencio** si el selector no existe |

---

# 14. ESCUCHAR JUNTOS Y CHAT

## 14.1 Pantalla «Juntos»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Conectar** / **Desconectar** / indicador de conexión | Juntos > barra | `ui/screens/ListenTogetherScreen.kt:487-501` | acción primaria | ocasional | según el estado |
| Aviso de desconexión al crear sala sin música | Juntos > aviso | `ui/screens/ListenTogetherScreen.kt:282` | informativo | ocasional | conectado y sin sala |
| **Código de sala** *(texto grande)* — ⚠️ **no es copiable al tocarlo** | Juntos > tarjeta de sala | `ui/screens/ListenTogetherScreen.kt:707-721` | informativo | constante | solo en sala |
| **Eres el anfitrión** / **Eres un invitado** | Juntos > tarjeta de sala | `ui/screens/ListenTogetherScreen.kt:724-727` | informativo | constante | igual |
| **Comentarios** *(icono chat)* → `listen_together/chat` | Juntos > tarjeta de sala | `ui/screens/ListenTogetherScreen.kt:735-754` | navegación | diaria | solo en sala |
| **Copiar enlace** | Juntos > tarjeta de sala | `ui/screens/ListenTogetherScreen.kt:768-786` | acción secundaria | ocasional | **solo el anfitrión** |
| **Copiar código** | Juntos > tarjeta de sala | `ui/screens/ListenTogetherScreen.kt:788-804` | acción secundaria | ocasional | **solo el anfitrión** |
| **Usuarios conectados (N)** + avatar → diálogo de gestión | Juntos > usuarios | `ui/screens/ListenTogetherScreen.kt:829-868` | acción secundaria | ocasional | el avatar solo es pulsable siendo anfitrión y sobre otro usuario |
| Insignias **Anfitrión** / **Tú** | Juntos > avatar | `ui/screens/ListenTogetherScreen.kt:898-948` | informativo | constante | según el rol |
| **Solicitudes de conexión** + **Aprobar** / **Rechazar** | Juntos > solicitudes | `ui/screens/ListenTogetherScreen.kt:969-1023` | acción primaria / destructiva | ocasional | solo anfitrión con solicitudes |
| **Sugerencias pendientes** + **Aprobar** / **Rechazar** | Juntos > sugerencias | `ui/screens/ListenTogetherScreen.kt:1047-1100` | acción primaria / destructiva | ocasional | solo anfitrión con sugerencias |
| Pestañas **Crear** / **Unirse** | Juntos > selector | `ui/screens/ListenTogetherScreen.kt:1146-1181` | navegación | diaria | solo fuera de sala |
| Campo **Nombre de usuario** + borrar | Juntos > formulario | `ui/screens/ListenTogetherScreen.kt:1184-1214`, `:1196-1202` | acción primaria | diaria | igual |
| Campo **Código de sala** *(máx. 8, mayúsculas)* + borrar | Juntos > formulario | `ui/screens/ListenTogetherScreen.kt:1221-1252` | acción primaria | diaria | **solo en la pestaña Unirse** |
| **Esperando la aprobación del anfitrión** | Juntos > banner | `ui/screens/ListenTogetherScreen.kt:1255-1287` | informativo | ocasional | mientras te unes |
| Banner de error: **Solicitud de unión denegada** / **Código de sala no válido** / **No se pudo conectar…** / **El anfitrión te expulsó** | Juntos > banner | `ui/screens/ListenTogetherScreen.kt:1289-1322` | informativo | ocasional | según el error |
| **Crear** | Juntos > acción | `ui/screens/ListenTogetherScreen.kt:1328-1344` | acción primaria | diaria | habilitado solo con nombre de usuario |
| **Unirse** | Juntos > acción | `ui/screens/ListenTogetherScreen.kt:1346-1362` | acción primaria | diaria | habilitado solo con nombre y código de 8 caracteres |
| **Salir de la sala** | Juntos > acción | `ui/screens/ListenTogetherScreen.kt:344-362` | destructiva | ocasional | solo en sala |
| **Ajustes** → `settings/integrations/listen_together` | Juntos > tarjeta de ajustes | `ui/screens/ListenTogetherScreen.kt:422-426`, `:1369-1379` | navegación | ocasional | siempre |
| **«How it Works»** + 3 pasos *(hardcoded, en inglés)* | Juntos > ayuda | `ui/screens/ListenTogetherScreen.kt:444-464` | informativo | rara | solo fuera de sala |
| Diálogo **Administrar usuario**: **Expulsar** / **Bloquear permanentemente** / **Transferir propiedad** / **Cancelar** | Juntos > diálogo | `ui/screens/ListenTogetherScreen.kt:1399-1523` | destructiva | ocasional | ⚠️ **las tres acciones destructivas se ejecutan a un solo toque, sin confirmación** |

## 14.2 Chat de la sala

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Comentarios** + **«Room: {código}»** *(hardcoded, en inglés)* | Chat > barra | `ui/screens/CommentTogether.kt:74-85` | informativo | constante | siempre |
| Flecha atrás | Chat > barra | `ui/screens/CommentTogether.kt:88-92` | navegación | diaria | siempre |
| Previsualización de respuesta + cancelar | Chat > barra inferior | `ui/screens/CommentTogether.kt:112-157` | acción secundaria | ocasional | solo respondiendo |
| Campo **«Escribe un mensaje…»** *(máx. 4 líneas)* + **Enviar** | Chat > entrada | `ui/screens/CommentTogether.kt:332-366` | acción primaria | constante | siempre |
| Enlace de YouTube Music dentro de un mensaje → reproduce | Chat > burbuja | `ui/screens/CommentTogether.kt:430-450` | navegación | ocasional | solo si el mensaje trae esa URL |
| **«No messages yet» / «Start the conversation!»** *(en inglés)* | Chat > vacío | `ui/screens/CommentTogether.kt:400-410` | informativo | ocasional | sin mensajes |

## 14.3 Comentarios de YouTube

⚠️ Solo alcanzable si el ajuste `ShowCommentButton` está activado — **por defecto está apagado**.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Comentarios** + **Cerrar** | Comentarios > cabecera | `ui/screens/CommentSheet.kt:108-130` | navegación | ocasional | con la hoja abierta |
| **No se encontraron comentarios** — sin acción | Comentarios > vacío | `ui/screens/CommentSheet.kt:146-164` | informativo | ocasional | error o lista vacía |
| Fila de comentario → abre respuestas | Comentarios > lista | `ui/screens/CommentSheet.kt:182-189`, `:239-243` | navegación | ocasional | siempre |
| Contador de «me gusta» — ⚠️ **no es un botón** | Comentarios > comentario | `ui/screens/CommentSheet.kt:286-301` | informativo | ocasional | si hay votos |
| **«Replies»** *(en inglés)* → abre respuestas | Comentarios > comentario | `ui/screens/CommentSheet.kt:304-326` | navegación | ocasional | si hay respuestas |
| **«Back» / «N Replies»** *(en inglés)* | Comentarios > respuestas | `ui/screens/CommentSheet.kt:371-381` | navegación | ocasional | con un hilo abierto |

---

# 15. RECONOCER MÚSICA

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Reconocer música** + flecha atrás *(long-press = inicio)* | Reconocimiento > barra | `ui/screens/recognition/RecognitionScreen.kt:344-354` | navegación | constante | siempre |
| **Historial de reconocimiento** → `recognition_history` | Reconocimiento > barra | `ui/screens/recognition/RecognitionScreen.kt:357-362` | navegación | ocasional | siempre |
| Botón circular del micrófono → iniciar escucha | Reconocimiento > centro | `ui/screens/recognition/RecognitionScreen.kt:452` | acción primaria | diaria | estado listo |
| **Tap para reconocer** | Reconocimiento > centro | `ui/screens/recognition/RecognitionScreen.kt:472` | informativo | constante | igual |
| Botón pulsante → **cancelar** + **Escuchando…** + **Cancelar** | Reconocimiento > escuchando | `ui/screens/recognition/RecognitionScreen.kt:527-547` | acción secundaria | ocasional | mientras escucha |
| **Procesando…** | Reconocimiento > procesando | `ui/screens/recognition/RecognitionScreen.kt:599` | informativo | ocasional | mientras procesa |
| Portada + título + artista + álbum del resultado | Reconocimiento > resultado | `ui/screens/recognition/RecognitionScreen.kt:716-761` | informativo | constante | con resultado |
| **Reproducir en Aura Hi-Res Player** | Reconocimiento > resultado | `ui/screens/recognition/RecognitionScreen.kt:770-781` | acción primaria | diaria | igual |
| **Me gusta** | Reconocimiento > resultado | `ui/screens/recognition/RecognitionScreen.kt:789-809` | conmutador | ocasional | **habilitado solo cuando la canción se ha resuelto** |
| **Añadir a lista de reproducción** | Reconocimiento > resultado | `ui/screens/recognition/RecognitionScreen.kt:811-828` | acción secundaria | ocasional | igual |
| **Volver a escuchar** / **Cerrar** | Reconocimiento > resultado | `ui/screens/recognition/RecognitionScreen.kt:831-856` | acción secundaria | ocasional | igual |
| **No se encontró ninguna coincidencia** + **Inténtalo de nuevo** | Reconocimiento > sin coincidencia | `ui/screens/recognition/RecognitionScreen.kt:886`, `:899-906` | acción primaria | ocasional | sin coincidencia |
| **Error de reconocimiento** + **Inténtalo de nuevo** | Reconocimiento > error | `ui/screens/recognition/RecognitionScreen.kt:936`, `:949-956` | acción primaria | rara | con error |

## 15.1 Historial de reconocimiento

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| **Historial de reconocimiento** + flecha atrás *(long-press = inicio)* | Historial recon. > barra | `ui/screens/recognition/RecognitionHistoryScreen.kt:188-196` | navegación | constante | siempre |
| **Borrar historial de reconocimiento** | Historial recon. > barra | `ui/screens/recognition/RecognitionHistoryScreen.kt:202-205` | destructiva | rara | solo con historial |
| Campo **Buscar** + borrar texto | Historial recon. > buscador | `ui/screens/recognition/RecognitionHistoryScreen.kt:219-260` | acción primaria | ocasional | siempre |
| Cabecera de fecha *(pegajosa)* | Historial recon. > lista | `ui/screens/recognition/RecognitionHistoryScreen.kt:322-329` | informativo | constante | solo sin búsqueda activa |
| Fila → `search/{título artista}` | Historial recon. > lista | `ui/screens/recognition/RecognitionHistoryScreen.kt:336-357` | navegación | diaria | siempre |
| **Eliminar del historial** *(por fila)* | Historial recon. > fila | `ui/screens/recognition/RecognitionHistoryScreen.kt:433-437` | destructiva | ocasional | siempre |
| **«No recognition history» / «No results for …»** *(en inglés)* | Historial recon. > vacío | `ui/screens/recognition/RecognitionHistoryScreen.kt:281`, `:305` | informativo | rara | según el caso |
| Diálogo de borrado total: **Limpiar** / **Cancelar** | Historial recon. > diálogo | `ui/screens/recognition/RecognitionHistoryScreen.kt:124-144` | destructiva | rara | al pulsar borrar |
| Diálogo de borrado de un elemento: **Eliminar** / **Cancelar** | Historial recon. > diálogo | `ui/screens/recognition/RecognitionHistoryScreen.kt:156-180` | destructiva | ocasional | ⚠️ **usa el texto de confirmación de borrar una LISTA DE REPRODUCCIÓN** |

---

# 16. MODO AMBIENTE

Solo se llega desde **Cola > menú ⋮ > Modo Ambiente**.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Portada grande *(contentDescription «Album Art», en inglés)* — **doble toque = reproducir/pausar** | Modo ambiente > izquierda | `ui/screens/ambient/AmbientModeScreen.kt:136-151` | conmutador | diaria | siempre |
| Letras sincronizadas en vivo | Modo ambiente > derecha | `ui/screens/ambient/AmbientModeScreen.kt:162-166` | informativo | constante | siempre |
| Fondo de resplandor animado | Modo ambiente > fondo | `ui/screens/ambient/AmbientGlowBackground.kt:38` | informativo | constante | siempre |
| Bloqueo en horizontal + pantalla siempre encendida + barras ocultas | Modo ambiente > efecto | `ui/screens/ambient/AmbientModeScreen.kt:50-71` | informativo | constante | mientras esté abierto |

---

# 17. BIBLIOTECA, ARTISTA, ÁLBUM Y LISTAS DE REPRODUCCIÓN

Las cinco pantallas de Biblioteca (hub, Canciones, Artistas, Álbumes, Listas) **no tienen ruta propia**:
viven dentro de `LibraryScreen` y se cambian con los chips de filtro.

## 17.1 BIBLIOTECA

### 17.1.1 Biblioteca (hub) — `LibraryScreen` (ruta `library`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Listas de reproducción (chip) | Biblioteca > fila de chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:79 | navegación | diaria | siempre |
| Canciones (chip) | Biblioteca > fila de chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:80 | navegación | diaria | siempre |
| Álbumes (chip) | Biblioteca > fila de chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:81 | navegación | diaria | siempre |
| Artistas (chip) | Biblioteca > fila de chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:82 | navegación | diaria | siempre |
| Local (chip) | Biblioteca > fila de chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:83 | navegación | ocasional | siempre |
| (tocar el chip activo → vuelve al hub) | Biblioteca > fila de chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:86-93 | navegación | diaria | solo si el chip pulsado ya es `filterType` |
| Lista AI (solo icono, FAB pequeño) | Biblioteca > botones flotantes | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:177-188 (cD :185) | acción primaria | ocasional | `AiPlaylistEnabledKey` ON (:176) y `filterType ∈ {LIBRARY, PLAYLISTS}` (:102) |
| Crear lista de reproducción (FAB extendido) | Biblioteca > botones flotantes | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:191-198 (texto :192) | acción primaria | diaria | solo si `filterType ∈ {LIBRARY, PLAYLISTS}` |
| Importar lista de reproducción (FAB extendido) | Biblioteca > botones flotantes | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:201-208 (texto :202) | acción secundaria | ocasional | solo si `filterType ∈ {LIBRARY, PLAYLISTS}` |
| Importar desde Spotify | Biblioteca > menú del FAB Importar | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:214-220 | acción secundaria | ocasional | solo con el menú abierto |
| Importar desde YouTube Music | Biblioteca > menú del FAB Importar | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:221-227 | acción secundaria | ocasional | solo con el menú abierto |
| Migrar lista → `migration` | Biblioteca > menú del FAB Importar | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:228-234 | navegación | rara | solo con el menú abierto |
| (Atrás del sistema → vuelve al hub) | Biblioteca | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryScreen.kt:70-72 | navegación | diaria | solo si `filterType != LIBRARY` |

### 17.1.2 Biblioteca > contenido del hub — `LibraryMixScreen` (código duplicado lista/cuadrícula)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (botón de orden) | Biblioteca > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:263 | acción secundaria | ocasional | siempre |
| Fecha añadida (orden) | Biblioteca > desplegable de orden | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:270 | conmutador | ocasional | solo con el desplegable abierto |
| Fecha de actualización (orden) | Biblioteca > desplegable de orden | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:271 | conmutador | ocasional | solo con el desplegable abierto |
| Nombre (orden) | Biblioteca > desplegable de orden | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:272 | conmutador | ocasional | solo con el desplegable abierto |
| "Toggle sort order" (hardcoded, sin traducir) — invertir asc/desc | Biblioteca > cabecera | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:85-119 | conmutador | ocasional | siempre |
| Canciones que me gustan → `auto_playlist/liked` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:333-339 (lista) / :655-661 (cuadrícula) | navegación | diaria | solo si `ShowLikedPlaylistKey` ON |
| Descargado → `auto_playlist/downloaded` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:342-348 / :664-670 | navegación | diaria | solo si `ShowDownloadedPlaylistKey` ON |
| Exportado → `auto_playlist/exported` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:351-357 / :673-679 | navegación | ocasional | solo si `ShowExportedPlaylistKey` ON |
| En caché → `cache_playlist/cached` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:360-366 / :682-688 | navegación | ocasional | solo si `ShowCachedPlaylistKey` ON |
| Subidas → `auto_playlist/uploaded` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:369-375 / :691-697 | navegación | rara | solo si `ShowUploadedPlaylistKey` ON |
| Mi Top {N} → `top_playlist/{N}` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:378-384 / :700-706 | navegación | ocasional | solo si `ShowTopPlaylistKey` ON |
| Álbumes favoritos → `favorite_albums` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:386-392 / :708-714 | navegación | ocasional | siempre |
| Novedades → `release_radar` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:393-399 / :715-721 | navegación | ocasional | siempre |
| Podcasts → `podcasts` | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:400-406 / :722-728 | navegación | ocasional | siempre |
| Local → `local_songs` (media fila) | Biblioteca > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:407-415 / :729-737 | navegación | diaria | siempre (duplica el chip "Local") |
| Artistas (título de sección) | Biblioteca > separador | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:421-426 / :743-748 | informativo | constante | solo si `artistItems.isNotEmpty()` |
| Fila/tarjeta de artista → `artist/{id}` | Biblioteca > sección Artistas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:447 / :761 | navegación | diaria | siempre que haya artistas |
| ⋯ del artista → `ArtistMenu` (solo icono, cD = null) | Biblioteca > sección Artistas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:436-442 | acción secundaria | ocasional | solo en vista lista |
| Listas de reproducción (título de sección) | Biblioteca > separador | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:464-469 / :779-784 | informativo | constante | siempre (la sección contiene listas y álbumes) |
| Fila/tarjeta de lista → abrir lista | Biblioteca > sección Listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:503-505 / :801-803 | navegación | diaria | siempre que haya listas |
| ⋯ de la lista → `PlaylistMenu` (solo icono, cD = null) | Biblioteca > sección Listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:482-497 | acción secundaria | ocasional | solo en vista lista |
| Fila/tarjeta de álbum → `album/{id}` | Biblioteca > sección Listas (mezclada) | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:591-593 / :856-858 | navegación | diaria | siempre que haya álbumes |
| ⋯ del álbum → `AlbumMenu` (solo icono, cD = null) | Biblioteca > sección Listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryMixScreen.kt:570-585 | acción secundaria | ocasional | solo en vista lista |
| ▶ sobre la portada del álbum (solo icono, cD = null) | Biblioteca > tarjeta de álbum | app/src/main/kotlin/com/music/echo/ui/component/Items.kt:807-819 (desde LibraryMixScreen.kt:846) | acción primaria | diaria | solo en cuadrícula y solo si el álbum no suena |

### 17.1.3 Biblioteca > Canciones — `LibrarySongsScreen`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Canciones (chip con ✕; cD = "" vacío) | Canciones > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:192-205 (✕ :199-202) | navegación | diaria | siempre |
| Me gusta (sub-filtro) | Canciones > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:209 | conmutador | diaria | siempre |
| Biblioteca (sub-filtro) | Canciones > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:210 | conmutador | diaria | siempre |
| Subido (sub-filtro) | Canciones > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:211 | conmutador | ocasional | siempre |
| Descargado (sub-filtro) | Canciones > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:212 | conmutador | diaria | siempre |
| Exportado (sub-filtro) | Canciones > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:213 | conmutador | ocasional | siempre |
| Buscar en la biblioteca… (campo) | Canciones > búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:225-244 (placeholder :239) | acción primaria | diaria | siempre |
| (borrar búsqueda) (solo icono, cD = null) | Canciones > búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:234-236 | acción secundaria | diaria | solo si `searchQuery.isNotEmpty()` |
| (botón de orden) | Canciones > cabecera de lista | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:253-266 | acción secundaria | ocasional | siempre |
| Fecha añadida (orden) | Canciones > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:260 | conmutador | ocasional | solo con el desplegable abierto |
| Nombre (orden) | Canciones > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:261 | conmutador | ocasional | solo con el desplegable abierto |
| Artista (orden) | Canciones > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:262 | conmutador | ocasional | solo con el desplegable abierto |
| Tiempo de reproducción (orden) | Canciones > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:263 | conmutador | ocasional | solo con el desplegable abierto |
| "Toggle sort order" (hardcoded) — invertir asc/desc | Canciones > cabecera | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:85-119 | conmutador | ocasional | siempre |
| «%d canciones» (contador) | Canciones > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:270-278 | informativo | constante | siempre |
| Vista lista (solo icono, cD = null) | Canciones > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:280-283 | conmutador | ocasional | siempre |
| Vista cuadrícula (solo icono, cD = null) | Canciones > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:280-283 | conmutador | ocasional | siempre |
| "Aleatorio mejorado · X/Y reproducidas" (hardcoded en español) | Canciones > píldora | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:286-297 → EnhancedShuffleIndicator.kt:79 | informativo | constante | `libraryContextId != null` (sin búsqueda) + `EnhancedShuffleKey` ON + lista no vacía |
| Fila de canción → reproducir / pausar | Canciones > vista lista | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:369-382 | acción primaria | constante | solo en vista lista |
| ⋯ de canción → `SongMenu` (solo icono, cD = null) | Canciones > fila | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:349-364 | acción secundaria | diaria | solo en vista lista |
| Tarjeta de canción → reproducir / pausar | Canciones > cuadrícula | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:442-455 | acción primaria | constante | solo en cuadrícula |
| ✓ "Ya reproducida en aleatorio" (hardcoded, cD) + fila atenuada | Canciones > fila | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:346 → Items.kt:495-504 | informativo | constante | solo si la canción está en la memoria del contexto |
| Aleatorio (FAB, cD = null) | Canciones > FAB | app/src/main/kotlin/com/music/echo/ui/screens/library/LibrarySongsScreen.kt:502-507 (lista) / :510-515 (cuadrícula) | acción primaria | diaria | solo si `filteredSongs.isNotEmpty()` y `isScrollingUp` |

### 17.1.4 Biblioteca > Artistas — `LibraryArtistsScreen`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Artistas (chip con ✕; cD = "" vacío) | Artistas > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:106-116 | navegación | diaria | siempre |
| Me gusta (sub-filtro) | Artistas > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:120 | conmutador | diaria | siempre |
| Biblioteca (sub-filtro) | Artistas > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:121 | conmutador | diaria | siempre |
| Buscar en la biblioteca… (campo) | Artistas > búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:130-149 | acción primaria | diaria | siempre |
| (borrar búsqueda) (solo icono, cD = null) | Artistas > búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:139-141 | acción secundaria | diaria | solo si `searchQuery.isNotEmpty()` |
| (botón de orden) | Artistas > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:184-197 | acción secundaria | ocasional | siempre |
| Fecha añadida (orden) | Artistas > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:191 | conmutador | ocasional | solo con el desplegable abierto |
| Nombre (orden) | Artistas > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:192 | conmutador | ocasional | solo con el desplegable abierto |
| Número de canciones (orden) | Artistas > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:193 | conmutador | ocasional | solo con el desplegable abierto |
| Tiempo de reproducción (orden) | Artistas > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:194 | conmutador | ocasional | solo con el desplegable abierto |
| "Toggle sort order" (hardcoded) — invertir asc/desc | Artistas > cabecera | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:85-119 | conmutador | ocasional | siempre |
| «%d artistas» (contador) | Artistas > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:201-209 | informativo | constante | siempre |
| Vista lista (solo icono, cD = null) | Artistas > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:211-214 | conmutador | ocasional | siempre |
| Vista cuadrícula (solo icono, cD = null) | Artistas > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:211-214 | conmutador | ocasional | siempre |
| Fila de artista → `artist/{id}` | Artistas > vista lista | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:259-265 → Library.kt:55-57 | navegación | diaria | solo en vista lista |
| ⋯ del artista → `ArtistMenu` (solo icono, cD = null) | Artistas > fila | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:36-51 | acción secundaria | ocasional | solo en vista lista |
| Tarjeta de artista → `artist/{id}` | Artistas > cuadrícula | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:318-324 → Library.kt:74-76 | navegación | diaria | solo en cuadrícula |
| No se han encontrado resultados | Artistas > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:247 / :306 | informativo | rara | lista vacía y hay texto de búsqueda |
| Los artistas de la biblioteca aparecerán aquí | Artistas > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryArtistsScreen.kt:248 / :307 | informativo | rara | lista vacía y búsqueda en blanco |

### 17.1.5 Biblioteca > Álbumes — `LibraryAlbumsScreen` (sin campo de búsqueda)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Álbumes (chip con ✕; cD = "" vacío) | Álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:105-115 | navegación | diaria | siempre |
| Me gusta (sub-filtro) | Álbumes > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:119 | conmutador | diaria | siempre |
| Biblioteca (sub-filtro) | Álbumes > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:120 | conmutador | diaria | siempre |
| Subido (sub-filtro) | Álbumes > chips | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:121 | conmutador | ocasional | siempre |
| (botón de orden) | Álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:163-179 | acción secundaria | ocasional | siempre |
| Fecha añadida (orden) | Álbumes > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:170 | conmutador | ocasional | solo con el desplegable abierto |
| Nombre (orden) | Álbumes > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:171 | conmutador | ocasional | solo con el desplegable abierto |
| Artista (orden) | Álbumes > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:172 | conmutador | ocasional | solo con el desplegable abierto |
| Año (orden) | Álbumes > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:173 | conmutador | ocasional | solo con el desplegable abierto |
| Número de canciones (orden) | Álbumes > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:174 | conmutador | ocasional | solo con el desplegable abierto |
| Duración (orden) | Álbumes > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:175 | conmutador | ocasional | solo con el desplegable abierto |
| Tiempo de reproducción (orden) | Álbumes > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:176 | conmutador | ocasional | solo con el desplegable abierto |
| "Toggle sort order" (hardcoded) — invertir asc/desc | Álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:85-119 | conmutador | ocasional | siempre |
| «%d álbumes» (contador) | Álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:183-187 | informativo | constante | siempre |
| Vista lista (solo icono, cD = null) — escribe `AlbumViewTypeKey`, que también gobierna el hub | Álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:189-192 | conmutador | ocasional | siempre |
| Vista cuadrícula (solo icono, cD = null) — ídem | Álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:189-192 | conmutador | ocasional | siempre |
| Fila de álbum → `album/{id}` | Álbumes > vista lista | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:241-249 → Library.kt:121-123 | navegación | diaria | solo en vista lista |
| ⋯ del álbum → `AlbumMenu` (solo icono, cD = null) | Álbumes > fila | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:102-117 | acción secundaria | ocasional | solo en vista lista |
| Tarjeta de álbum → `album/{id}` | Álbumes > cuadrícula | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:301-310 → Library.kt:147-149 | navegación | diaria | solo en cuadrícula |
| Los álbumes de la biblioteca aparecerán aquí | Álbumes > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryAlbumsScreen.kt:225 / :285 | informativo | rara | solo si `albums.isEmpty()` |

### 17.1.6 Biblioteca > Listas — `LibraryPlaylistsScreen` (sin conmutador lista/cuadrícula; la rama lista es inalcanzable)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (fila de chips de Biblioteca) | Listas > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:310 / :442 (definidos en LibraryScreen.kt:74-97) | navegación | diaria | siempre |
| Buscar en tus playlists (campo) | Listas > búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:235-259 (placeholder :255) | acción primaria | diaria | siempre; solo filtra las listas del usuario, no las automáticas |
| (borrar búsqueda) (solo icono, cD = null) | Listas > búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:247-252 | acción secundaria | diaria | solo si hay texto |
| (botón de orden) | Listas > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:264-277 | acción secundaria | ocasional | siempre |
| Fecha añadida (orden) | Listas > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:271 | conmutador | ocasional | solo con el desplegable abierto |
| Nombre (orden) | Listas > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:272 | conmutador | ocasional | solo con el desplegable abierto |
| Número de canciones (orden) | Listas > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:273 | conmutador | ocasional | solo con el desplegable abierto |
| Fecha de actualización (orden) | Listas > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:274 | conmutador | ocasional | solo con el desplegable abierto |
| "Toggle sort order" (hardcoded) — invertir asc/desc | Listas > cabecera | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:85-119 | conmutador | ocasional | siempre |
| «%d listas de reproducción» (contador) | Listas > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:281-289 | informativo | constante | siempre |
| Canciones que me gustan (tarjeta) | Listas > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:468-474 (duplicado muerto :334-340) | navegación | diaria | solo si `ShowLikedPlaylistKey` ON |
| Descargado (tarjeta) | Listas > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:477-483 (muerto :343-349) | navegación | diaria | solo si `ShowDownloadedPlaylistKey` ON |
| Exportado (tarjeta) | Listas > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:486-492 (muerto :352-358) | navegación | ocasional | solo si `ShowExportedPlaylistKey` ON |
| En caché (tarjeta) | Listas > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:495-501 (muerto :361-367) | navegación | ocasional | solo si `ShowCachedPlaylistKey` ON |
| Subidas (tarjeta) | Listas > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:504-510 (muerto :370-376) | navegación | ocasional | solo si `ShowUploadedPlaylistKey` ON |
| Mi Top {N} (tarjeta) | Listas > rejilla de auto-listas | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:513-521 (muerto :379-387) | navegación | ocasional | solo si `ShowTopPlaylistKey` ON |
| "Playlists" (hardcoded, sin traducir) | Listas > cabecera de sección | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:531-536 (muerto :396-401) | informativo | constante | siempre |
| Tarjeta de lista → abrir lista | Listas > cuadrícula | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:550-556 → Library.kt:242-247 | navegación | diaria | bifurcación online/local |
| Fila de lista + ⋯ | Listas > vista lista (INALCANZABLE) | app/src/main/kotlin/com/music/echo/ui/screens/library/LibraryPlaylistsScreen.kt:415-421 | navegación | rara | rama muerta |

### 17.1.7 Local — `LocalSongScreen` (ruta `local_songs` y empotrada en el chip "Local")

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Local (título) | Local > barra superior grande | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:337-342 | informativo | constante | solo si `!isSearchActive` |
| (atrás) (solo icono, cD = null) | Local > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:345-350 | navegación | constante | `!isSearchActive`; empotrada vuelve al hub, en ruta propia `navigateUp` |
| Buscar (solo icono) | Local > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:353-358 | acción secundaria | diaria | solo si `!isSearchActive` |
| Ajustes (solo icono) — abre la hoja de escaneo | Local > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:359-364 | navegación | ocasional | solo si `!isSearchActive` |
| Buscar en la biblioteca… (campo) | Local > barra de búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:288-333 (placeholder :297) | acción primaria | diaria | solo si `isSearchActive` |
| Atrás (cD traducido) — cierra la búsqueda | Local > barra de búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:300-310 | navegación | diaria | solo si `isSearchActive` |
| Cerrar — borra el texto | Local > barra de búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:312-320 | acción secundaria | diaria | `isSearchActive` y texto no vacío |
| (acción "buscar" del teclado) | Local > barra de búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:292 | acción primaria | diaria | solo si `isSearchActive` |
| (botón de orden) | Local > tarjeta de controles | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:531 | acción secundaria | ocasional | siempre |
| Fecha de actualización (orden) | Local > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:538 | conmutador | ocasional | solo con el desplegable abierto |
| Nombre (orden) | Local > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:539 | conmutador | ocasional | solo con el desplegable abierto |
| Artista (orden) | Local > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:540 | conmutador | ocasional | solo con el desplegable abierto |
| Álbum (orden) | Local > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:541 | conmutador | ocasional | solo con el desplegable abierto |
| "Toggle sort order" (hardcoded) — invertir asc/desc | Local > tarjeta de controles | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:85-119 | conmutador | ocasional | siempre |
| «%d canciones» | Local > tarjeta de controles | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:548-552 | informativo | constante | siempre |
| No se encontraron canciones locales | Local > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:580 | informativo | ocasional | lista vacía y búsqueda en blanco — sin botón |
| Abre los ajustes desde la barra superior y ejecuta un escaneo del dispositivo… | Local > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:589 | informativo | ocasional | ídem |
| No hay canciones locales que coincidan | Local > estado vacío de búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:582 | informativo | ocasional | lista vacía y hay texto |
| Prueba con otro nombre de canción, artista o álbum. | Local > estado vacío de búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:591 | informativo | ocasional | ídem |
| Fila de canción → reproducir / pausar | Local > lista | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:449-465 | acción primaria | constante | cola "Local" o "Canciones buscadas" (:455-459) |
| ⋯ de canción → `SongMenu` (solo icono, cD = null) | Local > fila | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:429-444 | acción secundaria | diaria | siempre |
| Escanear canciones locales (título de la hoja) | Local > hoja de escaneo | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:716 | informativo | ocasional | solo con la hoja abierta |
| Actualiza las canciones de este dispositivo y los metadatos de la biblioteca local. | Local > hoja de escaneo | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:725 | informativo | ocasional | solo con la hoja abierta |
| Escaneando dispositivo… (banner) | Local > hoja de escaneo | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:750-760 | informativo | rara | solo si `scanState.isScanning` |
| Acceso al almacenamiento + Permitido / No permitido | Local > hoja > fila de estado | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:773-820 | informativo | ocasional | siempre en la hoja |
| Último escaneo + estado dinámico | Local > hoja | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:827-832 (texto :650-660) | informativo | ocasional | siempre en la hoja |
| Filtros de escaneo | Local > hoja | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:849-853 | informativo | ocasional | siempre en la hoja |
| Los cambios se aplican la próxima vez que escanees este dispositivo. | Local > hoja | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:855-858 | informativo | ocasional | siempre en la hoja |
| Excluir canciones cortas | Local > hoja > tarjeta de duración | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:862 | informativo | ocasional | siempre en la hoja |
| Omite las canciones más cortas que la duración seleccionada. | Local > hoja > tarjeta de duración | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:863 | informativo | ocasional | siempre en la hoja |
| Desactivado / «%d segundos» (valor actual) | Local > hoja > tarjeta de duración | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:622-626, :865-870 | informativo | ocasional | "Desactivado" si el valor ≤ 0 (reutiliza `R.string.dark_theme_off`) |
| (deslizador de duración mínima, 0-180 s, 11 pasos) | Local > hoja > tarjeta de duración | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:871-878 | conmutador | ocasional | deshabilitado mientras escanea (:876) |
| Carpetas excluidas | Local > hoja > tarjeta de carpetas | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:883 | informativo | ocasional | siempre en la hoja |
| Omite las canciones guardadas en carpetas que coincidan con cualquiera de estas rutas. | Local > hoja > tarjeta de carpetas | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:884 | informativo | ocasional | siempre en la hoja |
| Añadir carpeta — abre el selector SAF | Local > hoja > tarjeta de carpetas | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:885-890 (lanzador :190-197) | acción secundaria | rara | no hace nada mientras escanea (:887) |
| Aún no hay carpetas excluidas. | Local > hoja > tarjeta de carpetas | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:892-897 | informativo | rara | solo si no hay carpetas |
| (chip de carpeta excluida con la ruta) | Local > hoja > tarjeta de carpetas | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:905-916 → :1114-1161 | informativo | rara | una por carpeta |
| (✕ quitar carpeta) (solo icono, cD = null) | Local > hoja > chip de carpeta | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:908-915 / :1140-1157 | destructiva | rara | deshabilitado mientras escanea |
| Escanear dispositivo (botón principal) | Local > hoja > botón inferior | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:926-986 (texto :663) | acción primaria | ocasional | solo con permiso; deshabilitado mientras escanea |
| Permitir (botón principal) | Local > hoja > botón inferior | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:926-986 (texto :665) | acción primaria | rara | solo si NO hay permiso |
| Escaneando dispositivo… (texto del botón) | Local > hoja > botón inferior | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:977 | informativo | rara | solo si escanea |
| (banner de error crudo) | Local > hoja > pie | app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt:988-1019 | informativo | rara | solo si `scanState.errorMessage != null` |

### 17.1.8 Álbumes favoritos — `FavoriteAlbumsScreen` (ruta `favorite_albums`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Álbumes favoritos (título) | Álbumes favoritos > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/library/FavoriteAlbumsScreen.kt:63 | informativo | constante | siempre |
| (atrás) (solo icono, cD = null) | Álbumes favoritos > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/library/FavoriteAlbumsScreen.kt:65-70 | navegación | constante | siempre |
| Los álbumes de la biblioteca aparecerán aquí | Álbumes favoritos > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/library/FavoriteAlbumsScreen.kt:89-93 | informativo | rara | solo si `albums.isEmpty()` — sin botón |
| Tarjeta de álbum → `album/{id}` | Álbumes favoritos > rejilla | app/src/main/kotlin/com/music/echo/ui/screens/library/FavoriteAlbumsScreen.kt:102-110 → Library.kt:147-149 | navegación | diaria | siempre que haya álbumes |
| ⋯ del álbum (pulsación larga → `AlbumMenu`) | Álbumes favoritos > tarjeta | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:150-158 | acción secundaria | ocasional | siempre |
| ▶ sobre la portada (solo icono, cD = null) | Álbumes favoritos > tarjeta | app/src/main/kotlin/com/music/echo/ui/component/Items.kt:807-819 | acción primaria | diaria | solo si el álbum no suena |

### 17.1.9 Novedades — `ReleaseRadarScreen` (ruta `release_radar`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Novedades (título) | Novedades > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/library/ReleaseRadarScreen.kt:77 | informativo | constante | siempre |
| (atrás) (solo icono, cD = null) | Novedades > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/library/ReleaseRadarScreen.kt:79-84 | navegación | constante | siempre |
| Actualizando… (solo icono; es el cD del botón refrescar) | Novedades > acciones | app/src/main/kotlin/com/music/echo/ui/screens/library/ReleaseRadarScreen.kt:87-101 (cD :99) | acción secundaria | ocasional | siempre; lanza `ReleaseRadarWorker.runNow` + Toast |
| Aún no hay novedades | Novedades > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/library/ReleaseRadarScreen.kt:117-120 | informativo | ocasional | solo si `releases.isEmpty()` — sin botón |
| Fila de estreno → `album/{playId}` | Novedades > lista | app/src/main/kotlin/com/music/echo/ui/screens/library/ReleaseRadarScreen.kt:128-141 (nav :137-139) | navegación | diaria | la fila es clicable siempre, pero solo navega si `playId` no está en blanco |
| Título + "Artista · Año" (formato hardcoded) | Novedades > fila | app/src/main/kotlin/com/music/echo/ui/screens/library/ReleaseRadarScreen.kt:154-168 (:162) | informativo | constante | siempre |
| Reproducir (solo icono) — reproduce el estreno completo | Novedades > fila | app/src/main/kotlin/com/music/echo/ui/screens/library/ReleaseRadarScreen.kt:176-204 (cD :202) | acción primaria | diaria | solo si `item.playId.isNotBlank()` |

---

## 17.2 ARTISTA

`isGuest` = invitado en sala de Escuchar Juntos (ArtistScreen.kt:169). `showLocal` (:225) = artista local o fila de biblioteca sin página de YouTube.

### 17.2.1 Artista — `ArtistScreen` (ruta `artist/{artistId}`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| No se pudo cargar el artista. Revisa tu conexión. | Artista > error | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:247 | informativo | rara | `artistPage == null && !showLocal && hasFailed` |
| Reintentar | Artista > error | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:251-253 | acción primaria | rara | ídem |
| (vídeo de fondo del artista, clic SIN efecto) | Artista > hero | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:387-391 | acción secundaria (NO-OP) | ocasional | `showArtistBackgroundVideo` y hay `backgroundVideoUrl` |
| (miniatura de vídeo 45×45 → reproduce radio/vídeo) (solo icono, cD = null) | Artista > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:432-444 | acción primaria | ocasional | `showArtistVideo` + sin vídeo de fondo + `artistVideoUrl != null` + `radioEndpoint != null` |
| Nombre del artista (o "Unknown" hardcoded) | Artista > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:452-460 | informativo | constante | siempre |
| «N Suscriptores» (chip) | Artista > chips | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:472-495 (:488) | informativo | constante | `ShowArtistSubscriberCountKey` ON y hay dato |
| «N Mensuales» (chip) | Artista > chips | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:500-523 (:516) | informativo | constante | `ShowMonthlyListenersKey` ON y hay dato |
| Acerca de | Artista > descripción | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:538-543 | informativo | ocasional | `!showLocal && showArtistDescription && artistPage != null` y hay descripción |
| (descripción expandible + Mostrar más/menos + enlaces) | Artista > descripción | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:545-554 → ExpandableText.kt:85-112 | conmutador / navegación | ocasional | solo si desborda 3 líneas |
| Suscribirme / Suscrito | Artista > botonera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:569-626 (etiqueta :615-621) | conmutador | diaria | siempre; texto según `bookmarkedAt != null` |
| Radio | Artista > botonera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:631-655 (:650) | acción primaria | diaria | `!showLocal && !isGuest` y `radioEndpoint != null` |
| Aleatorio (endpoint de YouTube) | Artista > botonera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:662-690 (:685) | acción primaria | diaria | `!showLocal && !isGuest` y `shuffleEndpoint != null` |
| Aleatorio (catálogo local, memoria `AR:`) | Artista > botonera (rama else) | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:738-760 (:755) | acción primaria | diaria | (`showLocal` o sin `shuffleEndpoint`) + `allLibrarySongs.isNotEmpty()` + `!isGuest` |
| Canciones (título de sección + flecha "ver todo") | Artista > sección biblioteca | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:772-780 | navegación | diaria | `showLocal && librarySongs.isNotEmpty()` |
| Fila de canción local → reproducir / pausar | Artista > canciones (biblioteca) | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:817-830 | acción primaria | constante | ídem; oculta explícitas si `HideExplicitKey` ON (:782-786) |
| ⋯ de canción → `SongMenu` (solo icono, cD = null) | Artista > canciones (biblioteca) | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:798-813 | acción secundaria | diaria | ídem |
| Álbumes (título de sección + flecha) | Artista > sección biblioteca | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:848-856 | navegación | ocasional | `showLocal && libraryAlbums.isNotEmpty()` |
| Tarjeta de álbum local → `album/{id}` | Artista > carrusel de álbumes | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:877-880 | navegación | diaria | ídem |
| ▶ sobre la portada (solo icono, cD = null) | Artista > carrusel de álbumes | app/src/main/kotlin/com/music/echo/ui/component/Items.kt:807-819 | acción primaria | ocasional | solo si el álbum no suena |
| Título de sección de YouTube (dinámico) + flecha "ver todo" | Artista > secciones online | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:901-919 | navegación | diaria | `!showLocal` y sección no vacía; va a `artist/{id}/items` si hay `moreEndpoint`, si no a `artist/section_buffer` |
| Fila de canción de YouTube → reproducir la sección desde ahí | Artista > sección de canciones online | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:958-978 | acción primaria | constante | `!showLocal` y todos los items son `SongItem` con `album != null` |
| ⋯ → `YouTubeSongMenu` (solo icono, cD = null) | Artista > sección de canciones online | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:940-955 | acción secundaria | diaria | ídem |
| Tarjeta de sección online → reproducir / abrir álbum / artista / lista | Artista > carruseles online | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:1017-1032 | navegación / acción primaria | constante | `!showLocal` y la sección no es 100 % canciones-con-álbum |
| (atrás) (solo icono, cD = null) | Artista > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:1225-1233 | navegación | constante | siempre |
| (copiar enlace → Toast "Enlace copiado al portapapeles") (solo icono, cD = null) | Artista > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:1236-1250 (toast :1242) | acción secundaria | ocasional | siempre visible; solo actúa si hay `shareLink` |
| Título de la barra (nombre) | Artista > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistScreen.kt:1223 | informativo | constante | solo tras desplazar > 100 px |

### 17.2.2 Artista > lista completa de sección — `ArtistItemsScreen` (ruta `artist/{artistId}/items?browseId=…&params=…`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Reintentar | Sección completa > error | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistItemsScreen.kt:133-135 | acción primaria | rara | `itemsPage == null && hasFailed` |
| Fila de canción → reproducir | Sección completa > vista lista | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistItemsScreen.kt:202-221 | acción primaria | constante | solo en vista lista (el primer item es `SongItem`) |
| Fila de álbum/artista/lista → navegar | Sección completa > vista lista | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistItemsScreen.kt:217-219 | navegación | ocasional | ídem |
| ⋯ del item (solo icono, cD = null) | Sección completa > fila | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistItemsScreen.kt:160-198 | acción secundaria | diaria | solo en vista lista |
| Tarjeta de item → reproducir / navegar | Sección completa > cuadrícula | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistItemsScreen.kt:265-278 | navegación / acción primaria | constante | solo en cuadrícula; orden fijo por año desc. (:249) |
| (atrás) (solo icono, cD = null) | Sección completa > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistItemsScreen.kt:328-336 | navegación | constante | siempre |
| Título de la barra (título de sección de YouTube) | Sección completa > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistItemsScreen.kt:326 | informativo | constante | siempre |

### 17.2.3 Artista > canciones — `ArtistSongsScreen` (ruta `artist/{artistId}/songs`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (botón de orden) | Artista > canciones > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:110-122 | navegación | diaria | siempre |
| Fecha añadida (orden) | Artista > canciones > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:117 | conmutador | diaria | solo con el desplegable abierto |
| Nombre (orden) | Artista > canciones > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:118 | conmutador | diaria | solo con el desplegable abierto |
| Tiempo de reproducción (orden) | Artista > canciones > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:119 | conmutador | diaria | solo con el desplegable abierto |
| "Toggle sort order" (hardcoded) — invertir asc/desc | Artista > canciones > cabecera | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:85-118 | conmutador | diaria | siempre |
| «%d canciones» | Artista > canciones > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:126-130 | informativo | constante | siempre |
| Fila de canción → reproducir toda la lista desde ahí | Artista > canciones | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:165-178 | acción primaria | constante | cola "Todas las canciones" (:172) |
| ⋯ de canción (solo icono, cD = null) | Artista > canciones > fila | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:145-160 | acción secundaria | diaria | siempre |
| (atrás) (solo icono, cD = null) | Artista > canciones > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:198-206 | navegación | constante | siempre |
| Aleatorio (FAB, memoria `AR:`) (cD = null) | Artista > canciones > FAB | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistSongsScreen.kt:210-240 | acción primaria | diaria | solo mientras `isScrollingUp` |

### 17.2.4 Artista > álbumes — `ArtistAlbumsScreen` (ruta `artist/{artistId}/albums`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (foto circular del artista) | Artista > álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsScreen.kt:130-139 | informativo | constante | solo si hay `thumbnailUrl` |
| Álbumes (título) | Artista > álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsScreen.kt:142-146 | informativo | constante | siempre |
| «%d álbumes» | Artista > álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsScreen.kt:147-151 | informativo | constante | siempre |
| Tarjeta de álbum → `album/{id}` | Artista > álbumes (cuadrícula) | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsScreen.kt:161-170 → Library.kt:147-149 | navegación | constante | orden fijo por año desc. (:157); subtítulo solo el año (:168) |
| ▶ sobre la portada (solo icono, cD = null) | Artista > álbumes > tarjeta | app/src/main/kotlin/com/music/echo/ui/component/Items.kt:807-819 | acción primaria | diaria | solo si el álbum no suena |
| (atrás) (solo icono, cD = null) | Artista > álbumes > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsScreen.kt:177-185 | navegación | constante | siempre |

### 17.2.5 Sección de artista en cuadrícula — `ArtistAlbumsGridScreen` (ruta `artist/section_buffer`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Título de la barra (`ArtistSectionBuffer.title`) | Sección en cuadrícula > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsGridScreen.kt:68 | informativo | constante | siempre |
| (atrás) (solo icono, cD = null) | Sección en cuadrícula > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsGridScreen.kt:70-72 | navegación | constante | siempre |
| Tarjeta de item → reproducir / abrir álbum / artista / lista | Sección en cuadrícula | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsGridScreen.kt:106-115 | navegación / acción primaria | constante | orden fijo por año desc. (:90) |
| (pulsación larga SIN efecto) | Sección en cuadrícula > tarjeta | app/src/main/kotlin/com/music/echo/ui/screens/artist/ArtistAlbumsGridScreen.kt:116 | — | — | `onLongClick = {}` (ver MUERTO) |

---

## 17.3 ÁLBUM — `AlbumScreen` (ruta `album/{albumId}`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (atrás) (solo icono, cD = null) | Álbum > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:991-1009 | navegación | constante | solo si `!inSelectMode` |
| Compartir (solo icono) | Álbum > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:1064-1087 (cD :1084) | acción secundaria | ocasional | `!inSelectMode` y `albumWithSongs != null` |
| Más opciones (solo icono) → `AlbumMenu` | Álbum > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:1089-1109 (cD :1106) | acción secundaria | diaria | ídem |
| «%d seleccionadas» | Álbum > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:979 | informativo | ocasional | solo en modo selección |
| (✕ cerrar selección) (cD = null) | Álbum > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:984-989 | navegación | ocasional | modo selección |
| (casilla seleccionar todo / ninguno) | Álbum > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:1014-1024 | conmutador | ocasional | modo selección |
| (⋯ acciones de selección → `SelectionSongMenu`) (cD = null) | Álbum > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:1025-1043 | acción primaria | ocasional | modo selección; deshabilitado si la selección está vacía |
| (portada, no clicable) | Álbum > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:293-298 | informativo | constante | con datos |
| (Canvas animado del álbum) | Álbum > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:300-307 | informativo | constante | `AlbumCanvasEnabledKey` ON (OFF con Ahorro de datos) y hay `canvasArtwork` |
| Título del álbum | Álbum > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:375-385 / :457-465 | informativo | constante | con datos |
| Fila artista (foto + nombre) → `artist/{id}` | Álbum > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:387-412 | navegación | diaria | solo si hay 1 artista |
| "Por " + artistas (cada nombre enlaza) | Álbum > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:722-744 | navegación | ocasional | solo si hay varios artistas |
| "Álbum • año • N Tracks • Xh Ym" ("Tracks"/"h"/"m" hardcoded en inglés) | Álbum > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:353-367 (render :419-424 / :473-478) | informativo | constante | con datos |
| Explícito (insignia) | Álbum > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:431-442 / :485-496 | informativo | ocasional | solo si `album.explicit` |
| Guardar / Guardado (solo icono ♥) | Álbum > botonera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:513-551 (cD :542) | conmutador | diaria | con datos |
| Reproducir / Pausar | Álbum > botonera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:554-613 (:604-606) | acción primaria | constante | con datos |
| Aleatorio (solo icono; memoria `AL:`) | Álbum > botonera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:663-680 (cD :675) | acción primaria | diaria | con datos |
| Acerca de (título) | Álbum > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:699-705 | informativo | constante | con datos |
| Descripción + Mostrar más/menos + enlaces | Álbum > Acerca de | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:707-716 → ExpandableText.kt:97-112 | conmutador / navegación | ocasional | texto de YouTube o texto estático hardcoded en inglés (:688-692) |
| Canciones (título de sección) | Álbum > lista | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:752-755 | informativo | constante | `filteredSongs.isNotEmpty()` |
| Fila de canción → reproducir / pausar / marcar | Álbum > lista | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:806-823 | acción primaria | constante | en selección alterna la casilla |
| (casilla de la fila) | Álbum > fila | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:779-782 | conmutador | ocasional | solo en modo selección |
| ⋯ de canción → `SongMenu` (cD = null) | Álbum > fila | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:784-799 | acción secundaria | diaria | solo si `!inSelectMode` |
| Otras versiones (título) | Álbum > sección | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:838-841 | informativo | ocasional | `otherVersions.isNotEmpty()` |
| Tarjeta → `album/{id}` | Álbum > Otras versiones | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:851-872 | navegación | ocasional | ídem |
| Lanzamientos para ti (título) | Álbum > sección | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:880-883 | informativo | ocasional | `releasesForYou.isNotEmpty()` |
| Tarjeta → `album/{id}` | Álbum > Lanzamientos para ti | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:893-914 | navegación | ocasional | ídem |
| Este álbum ya no está disponible. | Álbum > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:934-937 | informativo | rara | solo si `notFound` |
| No se pudo cargar el álbum. Revisa tu conexión. | Álbum > error | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:951-955 | informativo | rara | `hasFailed && !notFound` |
| Reintentar | Álbum > error | app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt:956-958 | acción primaria | rara | ídem |

---

## 17.4 LISTAS DE REPRODUCCIÓN

### 17.4.1 Lista local — `LocalPlaylistScreen` (ruta `local_playlist/{playlistId}`) — barra, lista y pie

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| «%d seleccionadas» | Lista local > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:894 | informativo | ocasional | solo en modo selección |
| Buscar (campo) | Lista local > barra superior (búsqueda) | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:896-918 (:901) | acción primaria | diaria | solo si `isSearching` |
| Nombre de la lista (título al desplazar) | Lista local > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:919-921 | informativo | constante | `firstVisibleItemIndex > 0` y ni búsqueda ni selección |
| (✕ cerrar selección) (cD = null) | Lista local > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:925-930 | navegación | ocasional | modo selección |
| (atrás / salir de la búsqueda) (cD = null) | Lista local > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:932-951 | navegación | constante | solo si `!inSelectMode` |
| (casilla seleccionar todo / ninguno) | Lista local > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:956-966 | conmutador | ocasional | modo selección |
| (⋯ acciones de selección → `SelectionSongMenu`) (cD = null) | Lista local > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:967-988 | acción primaria | ocasional | deshabilitado si la selección está vacía |
| (lupa: buscar dentro de la lista) (cD = null) | Lista local > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:991-998 | navegación | diaria | `!inSelectMode && !isSearching` |
| La lista de reproducción está vacía | Lista local > estado vacío | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:595-601 | informativo | rara | `songCount == 0 && remoteSongCount == 0` |
| (botón de orden) | Lista local > fila de controles | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:630-645 | acción secundaria | ocasional | solo si la lista no está vacía |
| Orden personalizado | Lista local > desplegable de orden | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:637 | conmutador | ocasional | siempre |
| Fecha añadida | Lista local > desplegable de orden | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:638 | conmutador | ocasional | siempre |
| Nombre | Lista local > desplegable de orden | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:639 | conmutador | ocasional | siempre |
| Artista | Lista local > desplegable de orden | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:640 | conmutador | ocasional | siempre |
| Tiempo de reproducción | Lista local > desplegable de orden | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:641 | conmutador | ocasional | siempre |
| "Toggle sort order" (hardcoded) — invertir asc/desc | Lista local > fila de controles | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:84-119 | conmutador | ocasional | solo si `sortType != CUSTOM`; con orden personalizado pasa a "Show sort options" |
| "Lock playlist" / "Unlock playlist" (hardcoded, sin traducir) — candado que bloquea la reordenación | Lista local > fila de controles | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:646-671 (texto :647) | conmutador | ocasional | solo si `editable` |
| Fila de canción → reproducir / pausar / marcar | Lista local > lista | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:794-814 | acción primaria | constante | en selección alterna la casilla |
| (casilla de la fila) | Lista local > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:753-756 | conmutador | ocasional | solo en modo selección |
| ⋯ de canción → `SongMenu` (con contexto de lista) (cD = null) | Lista local > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:758-775 | acción secundaria | diaria | solo si `!inSelectMode` |
| (asa de arrastre) (cD = null; `onClick` vacío intencional, el gesto es `draggableHandle`) | Lista local > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:777-787 | acción secundaria | ocasional | `sortType == CUSTOM && !locked && !inSelectMode && !isSearching && editable` |
| (marca "ya reproducida" del Aleatorio Mejorado) | Lista local > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:746 | informativo | diaria | memoria `PL:{id}` + `EnhancedShuffleKey` ON |
| Agregar música | Lista local > pie | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:843-850 → AddMusicComponents.kt:66-88 | acción primaria | diaria | `editable && !isSearching && !inSelectMode` |
| (sección Canciones sugeridas) | Lista local > pie | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:852-859 | acción secundaria | diaria | ídem |
| (sección Artistas destacados) | Lista local > pie | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:860-866 | navegación | ocasional | ídem |

### 17.4.1b Lista local > cabecera — `LocalPlaylistHeader`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Portada / mosaico 2×2 / placeholder | Lista local > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1222-1356 | informativo | constante | siempre |
| (lápiz sobre la portada) (cD = null) | Lista local > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1247-1289 / :1311-1353 | acción secundaria | rara | solo si `editable`; abre `CustomThumbnailMenu` si ya hay portada personalizada, si no el diálogo de aviso |
| Nombre de la lista | Lista local > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1360-1368 | informativo | constante | siempre |
| «%d canciones • duración» | Lista local > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1373-1388 | informativo | constante | duración solo si `playlistLength > 0` |
| Reproducir | Lista local > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1401-1432 (:1428) | acción primaria | constante | siempre |
| Aleatorio (memoria `PL:`) | Lista local > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1464-1489 (:1483) | acción primaria | constante | siempre |
| (⋯ menú de la lista) (cD = null) | Lista local > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1492-1589 | acción secundaria | diaria | siempre |
| "Aleatorio mejorado · X/Y reproducidas" (hardcoded en español) | Lista local > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1594-1604 | informativo | constante | `EnhancedShuffleKey` ON y hay canciones |
| Acerca de + descripción (texto hardcoded en inglés) + Mostrar más/menos | Lista local > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1608-1633 | informativo / conmutador | constante | descripción siempre visible |

### 17.4.1c Lista local > menú ⋯ — `LocalPlaylistMenu` (`ui/menu/PlaylistScreenMenus.kt`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Editar / *Editar lista de reproducción* | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:93-108 (cableado LocalPlaylistScreen.kt:1501) | acción secundaria | ocasional | siempre |
| Editar con IA / *Describe un cambio y revísalo antes de aplicarlo* | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:110-127 (gate LocalPlaylistScreen.kt:353) | acción secundaria | ocasional | `aiPlaylistEnabled && editable && browseId == null` |
| Sincronizar / *Sincronizar lista de reproducción con YouTube Music* | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:130-147 | acción secundaria | ocasional | solo si `browseId != null` |
| Añadir a la cola / *Añadir al final de la cola* | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:149-166 | acción secundaria | ocasional | solo si `!isGuest` |
| Descargar / *Descargar todas las canciones para reproducirlas sin conexión* | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:74-87 | acción secundaria | ocasional | `downloadState` ni COMPLETED ni DOWNLOADING |
| Descargando / *La descarga está en proceso* (tocar = cancelar) | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:60-73 | destructiva | rara | `downloadState == QUEUED/DOWNLOADING` |
| Eliminar descarga / *Eliminar todas las canciones descargadas de esta lista de reproducción* | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:46-59 | destructiva | ocasional | `downloadState == COMPLETED` |
| Compartir / *Compartir esta lista de reproducción con otros* | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:170-196 | acción secundaria | ocasional | siempre |
| Eliminar / *Eliminar esta lista de reproducción de forma permanente* | Lista local > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:198-213 | destructiva | rara | siempre |

### 17.4.1d Lista local > diálogos propios

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Editar lista de reproducción (título) + campo + Cancelar/Aceptar | Diálogo renombrar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:315-345 (título :324) | acción primaria | ocasional | "Aceptar" deshabilitado si el nombre está vacío; renombra también en YouTube si hay `browseId` (:340) |
| ¿Realmente quiere quitar todas las canciones … del almacenamiento de canciones descargadas? | Diálogo quitar descarga | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:376-383 | informativo | rara | `showRemoveDownloadDialog` |
| Cancelar | Diálogo quitar descarga | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:386-390 | acción secundaria | rara | ídem |
| Aceptar | Diálogo quitar descarga | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:392-411 | destructiva | rara | además vacía la lista si `!editable` (:395-399) |
| ¿Confirma que quiere eliminar la lista de reproducción «%s»? | Diálogo eliminar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:448-455 | informativo | rara | `showDeletePlaylistDialog` |
| Esta lista solo existe en Aura … así que no hay nada que eliminar allí. | Diálogo eliminar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:460-468 | informativo | rara | solo si `browseId == null` |
| Eliminar también de YouTube | Diálogo eliminar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:473-495 | destructiva | rara | solo si `browseId != null` |
| Solo eliminar de la app | Diálogo eliminar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:496-504 | destructiva | rara | solo si `browseId != null` |
| Cancelar | Diálogo eliminar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:508-514 | acción secundaria | rara | siempre |
| Aceptar (eliminar lista local) | Diálogo eliminar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:516-525 | destructiva | rara | solo si `browseId == null` |
| La lista se eliminó de la app, pero YouTube rechazó eliminarla de tu cuenta. (Toast) | Diálogo eliminar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:482-486 | informativo | rara | fallo del borrado remoto |
| Editar la portada de la lista de reproducción + Nota: Tu cuenta debe estar vinculada… + Después de seleccionar una imagen, espera… + Cancelar/Aceptar | Diálogo aviso de portada | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1182-1207 | informativo / acción primaria | rara | la "Nota" solo si `browseId != null` |
| Elija de la biblioteca | Menú de portada personalizada | app/src/main/kotlin/com/music/echo/ui/menu/CustomThumbnailMenu.kt:38-52 (cableado LocalPlaylistScreen.kt:1255-1259 / :1319-1323) | acción primaria | rara | `editable` y ya hay portada personalizada |
| Eliminar imagen personalizada | Menú de portada personalizada | app/src/main/kotlin/com/music/echo/ui/menu/CustomThumbnailMenu.kt:54-69 | destructiva | rara | ídem |
| Inicia sesión en YouTube Music (Toast) | Menú ⋯ > Sincronizar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1506-1510 | informativo | rara | `browseId != null` y NO `isLoggedIn` |
| Sincronizando… (Toast) | Menú ⋯ > Sincronizar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1512-1516 | informativo | ocasional | al lanzar la sincronización |
| Lista de reproducción sincronizada / No se pudo sincronizar (snackbar) | Menú ⋯ > Sincronizar | app/src/main/kotlin/com/music/echo/ui/screens/playlist/LocalPlaylistScreen.kt:1523-1529 | informativo | ocasional | al terminar |

### 17.4.2 Lista online — `OnlinePlaylistScreen` (ruta `online_playlist/{playlistId}`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (atrás / cerrar) (cD = null) | Lista online > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:517-540 | navegación | constante | icono `close` si `inSelectMode` |
| Buscar (solo icono, cD = null) | Lista online > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:574-581 | acción secundaria | ocasional | `!inSelectMode && !isSearching` |
| Buscar (campo, placeholder) | Lista online > barra superior (búsqueda) | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:479-501 | acción secundaria | ocasional | solo si `isSearching` |
| «%d canciones» (contador de selección) | Lista online > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:474-477 | informativo | ocasional | modo selección |
| (casilla seleccionar todo / ninguno) | Lista online > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:544-554 | conmutador | ocasional | modo selección |
| (⋯ → `YouTubeSelectionSongMenu`) (cD = null) | Lista online > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:555-572 | acción secundaria | ocasional | deshabilitado si la selección está vacía |
| Título de la lista (barra) | Lista online > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:503-513 | informativo | constante | solo tras desplazar (`!transparentAppBar`) |
| No se pudo cargar la lista. Revisa tu conexión. | Lista online > error | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:285 | informativo | rara | `playlist == null && !isLoading` |
| (causa cruda del error) | Lista online > error | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:292-297 | informativo | rara | ídem |
| Reintentar | Lista online > error | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:300-302 | acción primaria | rara | ídem |
| (portada grande + fondo desenfocado) | Lista online > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:635-642, :660-670 | informativo | constante | 320 dp máx. en pantalla ancha |
| Título de la lista (cabecera) | Lista online > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:681-687 | informativo | constante | siempre |
| Guardar / Guardado (guardar la lista en la biblioteca) | Lista online > botones principales | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:701-785 (:778-783) | conmutador | ocasional | deshabilitado salvo `songs.isNotEmpty() \|\| dbPlaylist != null` |
| Reproducir / Pausar | Lista online > botones principales | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:788-829 (:822-827) | acción primaria | constante | siempre |
| Compartir (solo icono) | Lista online > botones principales | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:832-856 | acción secundaria | ocasional | siempre |
| "Explicit" (hardcoded, cD) (insignia) | Lista online > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:862-887 (:874) | informativo | ocasional | alguna canción explícita |
| «%d canciones • Xh Ym» | Lista online > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:892-915 | informativo | constante | siempre |
| Nombre del autor/canal (NO es pulsable) | Lista online > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:918-927 | informativo | constante | solo si hay autor |
| Guardar / Guardando / Guardado (descargar toda la lista; cD "Descargar"/"Guardado") | Lista online > grupo de 3 | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:939-1013 (:1005-1012) | conmutador | ocasional | deshabilitado si `songs.isEmpty()` |
| Aleatorio (cD "Aleatorio") | Lista online > grupo de 3 | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:1016-1051 | acción primaria | diaria | siempre (no hace nada si no hay canciones) |
| Más (cD "Más opciones") → `YouTubePlaylistMenu` | Lista online > grupo de 3 | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:1054-1079 | navegación | ocasional | siempre |
| Fila de canción → reproducir / pausar / marcar | Lista online > lista | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:341-357 | acción primaria | constante | NO pulsable si `hideExplicit && songItem.explicit` (:340) |
| (casilla de la fila) | Lista online > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:369-372 | conmutador | ocasional | modo selección |
| ⋯ de canción → `YouTubeSongMenu` (cD = null) | Lista online > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:374-380 | navegación | diaria | `!inSelectMode` |
| Lista relacionada (título de sección) | Lista online > relacionados | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:401-404 | informativo | ocasional | `relatedItems.isNotEmpty() && !isSearching` |
| Tarjeta relacionada → navegar / reproducir | Lista online > relacionados | app/src/main/kotlin/com/music/echo/ui/screens/playlist/OnlinePlaylistScreen.kt:420-430 | navegación | ocasional | ídem |

### 17.4.3 Auto-lista — `AutoPlaylistScreen` (ruta `auto_playlist/{playlist}`)

Título según ruta (:140-145): `liked` → Canciones que me gustan, `uploaded` → Subidas, `exported` → Exportado, resto → Descargado.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (atrás / cerrar) (cD = null) | Auto-lista > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:546-574 | navegación | constante | icono `close` si `inSelectMode` |
| Buscar (solo icono) | Auto-lista > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:607-614 | acción secundaria | ocasional | `!inSelectMode && !isSearching` |
| Buscar (campo) | Auto-lista > barra superior (búsqueda) | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:513-535 | acción secundaria | ocasional | `isSearching` |
| «%d canciones» (selección) | Auto-lista > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:507-510 | informativo | ocasional | modo selección |
| (casilla seleccionar todo / ninguno) | Auto-lista > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:578-588 | conmutador | ocasional | modo selección |
| (⋯ → `SelectionSongMenu`) (cD = null) | Auto-lista > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:589-605 | acción secundaria | ocasional | deshabilitado si la selección está vacía |
| La lista de reproducción está vacía | Auto-lista > cuerpo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:336-341 | informativo | rara | `songs != null && songs.isEmpty()` |
| (portada = primera canción) | Auto-lista > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:656-665 | informativo | constante | 320 dp máx. en pantalla ancha |
| (icono ♥ junto al título) | Auto-lista > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:675-683 | informativo | constante | solo si es "Canciones que me gustan" |
| Aleatorio | Auto-lista > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:735-765 (:758-763) | acción primaria | diaria | siempre |
| Reproducir | Auto-lista > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:768-804 (:799-802) | acción primaria | constante | siempre |
| (⋯ → `AutoPlaylistMenu`) (cD = null) | Auto-lista > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:807-869 | navegación | ocasional | siempre |
| Sincronizar / "Traer los últimos cambios de YouTube Music" (hardcoded en español) | Auto-lista > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:278-293 (texto :281) | acción secundaria | ocasional | `isLoggedIn` y `playlistType == LIKE \|\| UPLOADED` |
| Añadir a la cola / *Añadir al final de la cola* | Auto-lista > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:294-309 | acción secundaria | ocasional | `!isGuest` |
| Descargar / *Descargar todas las canciones…* | Auto-lista > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:260-273 | acción secundaria | ocasional | ni COMPLETED ni DOWNLOADING |
| Descargando / *La descarga está en proceso* (cancela) | Auto-lista > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:246-259 | destructiva | rara | QUEUED/DOWNLOADING |
| Eliminar descarga / *Eliminar todas las canciones descargadas…* | Auto-lista > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:232-245 | destructiva | rara | COMPLETED |
| "Sincronizando con YouTube Music…" (hardcoded, Toast) | Auto-lista | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:361-365 | informativo | ocasional | tras pulsar Sincronizar |
| «%d canciones • Xh Ym» | Auto-lista > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:875-885 | informativo | constante | siempre |
| "Aleatorio mejorado · X/Y reproducidas" (hardcoded) | Auto-lista > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:888-896 | informativo | constante | `EnhancedShuffleKey` ON y `total != 0` |
| Acerca de + descripción (hardcoded en inglés) + Mostrar más/menos | Auto-lista > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:900-924 | informativo / conmutador | constante | siempre |
| (botón de orden) | Auto-lista > cabecera de canciones | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:380-394 | navegación | ocasional | solo si hay canciones |
| Fecha añadida (orden) | Auto-lista > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:387 | conmutador | ocasional | desplegable abierto |
| Nombre (orden) | Auto-lista > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:388 | conmutador | ocasional | desplegable abierto |
| Artista (orden) | Auto-lista > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:389 | conmutador | ocasional | desplegable abierto |
| Tiempo de reproducción (orden) | Auto-lista > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:390 | conmutador | ocasional | desplegable abierto |
| "Toggle sort order" (hardcoded) — invertir asc/desc | Auto-lista > cabecera de canciones | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:384 → SortHeader.kt:91-118 | conmutador | ocasional | siempre |
| Fila de canción → reproducir / pausar / marcar | Auto-lista > lista | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:447-463 | acción primaria | constante | siempre |
| (casilla de la fila) | Auto-lista > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:421-424 | conmutador | ocasional | modo selección |
| ⋯ de canción → `SongMenu` (cD = null) | Auto-lista > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:426-441 | navegación | diaria | `!inSelectMode` |
| (marca "ya reproducida", memoria `AP:`) | Auto-lista > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:417 | informativo | diaria | `EnhancedShuffleKey` ON |
| Cancelar (quitar descargas) | Auto-lista > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:268-272 | acción secundaria | rara | `showRemoveDownloadDialog` |
| Aceptar (quitar descargas) | Auto-lista > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AutoPlaylistScreen.kt:274-288 | destructiva | rara | ídem |

### 17.4.4 En caché — `CachePlaylistScreen` (ruta `cache_playlist/{playlist}`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (atrás / cerrar) (cD = null) | En caché > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:396-421 | navegación | constante | icono `close` si `inSelectMode` |
| Buscar (solo icono) | En caché > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:453-459 | acción secundaria | ocasional | `!inSelectMode && !isSearching` |
| Buscar (campo) | En caché > barra superior (búsqueda) | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:363-385 | acción secundaria | ocasional | `isSearching` |
| En caché (título) | En caché > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:387-392 | informativo | constante | `!inSelectMode && !isSearching` |
| «%d canciones» (selección) | En caché > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:356-361 | informativo | ocasional | modo selección |
| (casilla seleccionar todo / ninguno) | En caché > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:425-435 | conmutador | ocasional | modo selección |
| (⋯ → `SelectionSongMenu`) (cD = null) | En caché > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:436-452 | acción secundaria | ocasional | deshabilitado si la selección está vacía |
| La lista de reproducción está vacía | En caché > cuerpo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:208-214 | informativo | rara | vacía y sin búsqueda |
| No se han encontrado resultados | En caché > cuerpo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:218-224 | informativo | ocasional | vacía y con búsqueda |
| (portada + icono `cached` + título) | En caché > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:486-523 | informativo | constante | siempre |
| «%d canciones • Xh Ym» | En caché > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:528-538 | informativo | constante | siempre |
| Reproducir | En caché > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:551-581 (:576-580) | acción primaria | constante | siempre |
| Aleatorio | En caché > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:612-636 (:629-635) | acción primaria | diaria | siempre |
| (⋯ → `CachePlaylistMenu`) (cD = null) | En caché > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:639-683 | navegación | ocasional | siempre |
| Añadir a la cola / *Añadir al final de la cola* | En caché > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:452-466 | acción secundaria | ocasional | `!isGuest` |
| Descargar / *Descargar todas las canciones…* | En caché > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:434-447 | acción secundaria | ocasional | siempre (`downloadState` fijo a `STATE_STOPPED`, :643) |
| Acerca de + descripción (hardcoded en inglés) + Mostrar más/menos | En caché > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:688-713 | informativo / conmutador | constante | siempre |
| (botón de orden) | En caché > cabecera de canciones | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:246-260 | navegación | ocasional | solo si hay canciones |
| Fecha añadida (orden) | En caché > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:253 | conmutador | ocasional | desplegable abierto |
| Nombre (orden) | En caché > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:254 | conmutador | ocasional | desplegable abierto |
| Artista (orden) | En caché > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:255 | conmutador | ocasional | desplegable abierto |
| Tiempo de reproducción (orden) | En caché > desplegable | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:256 | conmutador | ocasional | desplegable abierto |
| "Toggle sort order" (hardcoded) — invertir asc/desc | En caché > cabecera de canciones | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:250 → SortHeader.kt:91-118 | conmutador | ocasional | siempre |
| Fila de canción → reproducir / pausar / marcar | En caché > lista | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:307-323 | acción primaria | constante | cola titulada "Cache Songs" (hardcoded, inglés) (:316) |
| (casilla de la fila) | En caché > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:282-285 | conmutador | ocasional | modo selección |
| ⋯ de canción → `SongMenu` (`isFromCache = true`) (cD = null) | En caché > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/CachePlaylistScreen.kt:287-301 | navegación | diaria | `!inSelectMode` |

### 17.4.5 Mi Top — `TopPlaylistScreen` (ruta `top_playlist/{top}`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (atrás / cerrar) (cD = null) | Mi Top > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:449-477 | navegación | constante | icono `close` si `inSelectMode` |
| Buscar (solo icono) | Mi Top > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:509-517 | acción secundaria | ocasional | `!inSelectMode && !isSearching` |
| Buscar (campo) | Mi Top > barra superior (búsqueda) | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:419-441 | acción secundaria | ocasional | `isSearching` |
| Mi Top {N} (título) | Mi Top > barra superior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:443-445 (fuente :183) | informativo | constante | `!inSelectMode && !isSearching` |
| «%d canciones» (selección) | Mi Top > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:412-417 | informativo | ocasional | modo selección |
| (casilla seleccionar todo / ninguno) | Mi Top > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:481-491 | conmutador | ocasional | modo selección |
| (⋯ → `SelectionSongMenu`) (cD = null) | Mi Top > barra de selección | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:492-508 | acción secundaria | ocasional | deshabilitado si la selección está vacía |
| La lista de reproducción está vacía | Mi Top > cuerpo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:265-270 | informativo | rara | `songs != null && songs.isEmpty()` |
| (portada + icono `queue_music` + título) | Mi Top > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:549-593 | informativo | constante | siempre |
| Aleatorio | Mi Top > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:632-659 (:652-657) | acción primaria | diaria | siempre |
| Reproducir | Mi Top > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:662-696 (:691-694) | acción primaria | constante | siempre |
| (⋯ → `TopPlaylistMenu`) (cD = null) | Mi Top > botonera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:699-757 | navegación | ocasional | siempre |
| Añadir a la cola / *Añadir al final de la cola* | Mi Top > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:373-388 | acción secundaria | ocasional | `!isGuest` |
| Descargar / *Descargar todas las canciones…* | Mi Top > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:355-368 | acción secundaria | ocasional | ni COMPLETED ni DOWNLOADING |
| Descargando / *La descarga está en proceso* (cancela) | Mi Top > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:341-354 | destructiva | rara | QUEUED/DOWNLOADING |
| Eliminar descarga / *Eliminar todas las canciones descargadas…* | Mi Top > menú ⋯ | app/src/main/kotlin/com/music/echo/ui/menu/PlaylistScreenMenus.kt:327-340 | destructiva | rara | COMPLETED |
| «%d canciones • Xh Ym» | Mi Top > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:763-773 | informativo | constante | siempre |
| Acerca de + descripción (hardcoded en inglés) + Mostrar más/menos | Mi Top > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:781-791 | informativo / conmutador | constante | siempre |
| (selector de periodo — botón principal) | Mi Top > selector de periodo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:292-310 | navegación | ocasional | solo si hay canciones |
| Desde siempre (periodo) | Mi Top > desplegable de periodo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:301 | conmutador | ocasional | desplegable abierto |
| Últimas 24 horas (periodo) | Mi Top > desplegable de periodo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:302 | conmutador | ocasional | desplegable abierto |
| Semana pasada (periodo) | Mi Top > desplegable de periodo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:303 | conmutador | ocasional | desplegable abierto |
| Mes pasado (periodo) | Mi Top > desplegable de periodo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:304 | conmutador | ocasional | desplegable abierto |
| Año pasado (periodo) | Mi Top > desplegable de periodo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:305 | conmutador | ocasional | desplegable abierto |
| "Show sort options" (hardcoded) — desplegar periodos | Mi Top > selector de periodo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:309 (`showDescending = false`) → SortHeader.kt:121-148 | navegación | ocasional | aquí NO hay toggle asc/desc |
| Fila de canción → reproducir / pausar / marcar | Mi Top > lista | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:363-379 | acción primaria | constante | siempre |
| (casilla de la fila) | Mi Top > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:338-341 | conmutador | ocasional | modo selección |
| ⋯ de canción → `SongMenu` (cD = null) | Mi Top > fila | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:343-358 | navegación | diaria | `!inSelectMode` |
| Cancelar (quitar descargas) | Mi Top > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:229-233 | acción secundaria | rara | `showRemoveDownloadDialog` |
| Aceptar (quitar descargas) | Mi Top > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/TopPlaylistScreen.kt:235-249 | destructiva | rara | ídem |

### 17.4.6 Hoja «Agregar música» — `AddMusicSheet` (sin pestañas, sin chips de filtro, sin ordenación)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (asa de arrastre de la hoja) | Agregar música > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:103 | navegación | constante | siempre |
| Agregar música (título) | Agregar música > cabecera | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:108 | informativo | constante | siempre |
| Buscar en Aura Hi-Res (campo) | Agregar música > búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:114-119 | acción primaria | constante | siempre |
| Cerrar (solo icono — borra la búsqueda, NO cierra la hoja) | Agregar música > búsqueda | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:128-133 | acción secundaria | diaria | `query.isNotEmpty()` |
| Fila de resultado → vista previa | Agregar música > resultados | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:168 | acción primaria | diaria | `query.isNotBlank()` |
| Portada del resultado → vista previa | Agregar música > resultados | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:171 | acción primaria | diaria | ídem |
| Agregar música / Listo (botón +) | Agregar música > resultados | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:161-166 | acción primaria | diaria | ídem; "Listo" si ya está añadida |
| Sin resultados | Agregar música > resultados | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:186 | informativo | ocasional | sin resultados y sin cargar |
| Canciones sugeridas (carrusel) | Agregar música > secciones | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:95, :196-205 | informativo | constante | `query.isBlank()` y hay sugerencias |
| Desde Replay (carrusel) | Agregar música > secciones | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:96, :207-216 | informativo | constante | `query.isBlank()` y hay datos |
| Agregado recientemente (carrusel) | Agregar música > secciones | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:97, :218-227 | informativo | constante | ídem |
| Fila del carrusel → vista previa | Agregar música > carruseles | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:347 | acción primaria | diaria | sección no vacía |
| Portada del carrusel → vista previa | Agregar música > carruseles | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:350 | acción primaria | diaria | ídem |
| Agregar música / Listo (botón + del carrusel) | Agregar música > carruseles | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:342-345 | acción primaria | diaria | ídem |
| Tu biblioteca (sección) | Agregar música > biblioteca | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:98, :230-232 | informativo | constante | `query.isBlank()` y hay biblioteca |
| Fila de biblioteca → vista previa | Agregar música > biblioteca | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:256 | acción primaria | diaria | ídem |
| Portada de biblioteca → vista previa | Agregar música > biblioteca | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:259 | acción primaria | diaria | ídem |
| Agregar música / Listo (botón +) | Agregar música > biblioteca | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:243-248 | acción primaria | diaria | ídem |
| (casilla de selección múltiple, sin etiqueta) | Agregar música > biblioteca | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:249-254 | conmutador | diaria | ídem |
| Agregar (N) | Agregar música > barra inferior | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicSheet.kt:268-283 (texto :280) | acción primaria | diaria | `selection.isNotEmpty()` |

NOTA: no existe "seleccionar todo" ni "limpiar selección" en esta hoja.

### 17.4.6b Pie de la lista local — `AddMusicComponents`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Agregar música (botón ancho) | Lista local > pie | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:66-88 (texto :84) | acción primaria | diaria | `editable && !isSearching && !inSelectMode` |
| Listo (check inerte, `enabled = false`) | Cualquier fila ya añadida | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:101-107 | informativo | diaria | `added == true` |
| Agregar música (botón +) | Cualquier fila no añadida | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:109-114 | acción primaria | diaria | `added == false` |
| Canciones sugeridas (título) | Lista local > pie > Sugeridas | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:151-155 | informativo | constante | `suggestions.isNotEmpty() \|\| showShimmer` |
| Actualizar (solo icono) — refresca sugerencias | Lista local > pie > Sugeridas | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:160-167 (cD :166) | acción secundaria | diaria | siempre que la sección se muestre (nunca se deshabilita, comentario :156-159) |
| Fila sugerida → REPRODUCE de verdad en el reproductor | Lista local > pie > Sugeridas | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:197-231 | acción primaria | diaria | hay sugerencias |
| Portada de la fila sugerida → VISTA PREVIA | Lista local > pie > Sugeridas | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:194 | acción primaria | diaria | ídem |
| Agregar música / Listo (botón +) | Lista local > pie > Sugeridas | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:186-191 | acción primaria | diaria | ídem |
| Artistas destacados (título) | Lista local > pie > Artistas | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:252-257 | informativo | constante | `artists.isNotEmpty()` |
| Avatar de artista → `artist/{id}` | Lista local > pie > Artistas | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AddMusicComponents.kt:263-266, :280 | navegación | ocasional | ídem |

### 17.4.7 Diálogo «Editar con IA» — `AiModifyPlaylistDialog`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Editar con IA (título) | Editar con IA > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:118 | informativo | ocasional | siempre |
| ¿Qué quieres cambiar? (ej. quita las lentas y añade 3 parecidas) (campo) | Editar con IA > estado A | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:127-135 (:130) | acción primaria | ocasional | `pendingPlan == null`; `enabled = !busy` |
| Consultando a la IA… | Editar con IA > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:142-143 | informativo | ocasional | `state == Planning` |
| Aplicando cambios… | Editar con IA > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:145-146 | informativo | ocasional | `state == Applying` |
| (mensaje de error: 4 variantes) | Editar con IA > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:148-154 (resolución :263-274) | informativo | rara | `state is Error` |
| Abrir Ajustes de IA → `settings/ai` | Editar con IA > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:155-159 | navegación | rara | error `AiServiceUnavailable` o `UnsupportedProvider` (:259-261) |
| Revisar | Editar con IA > estado A | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:169-184 (:183) | acción primaria | ocasional | `pendingPlan == null`; habilitado si `!busy && prompt.isNotBlank()` |
| Aplicar | Editar con IA > estado B | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:186-191 (:190) | destructiva | ocasional | `pendingPlan != null`; `enabled = !busy` |
| Cancelar | Editar con IA > diálogo | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:195-197 | acción secundaria | ocasional | siempre; `enabled = !busy` |
| Revisa los cambios. No se aplica nada hasta que confirmes. | Editar con IA > previsualización | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:205-208 | informativo | ocasional | `pendingPlan != null` |
| Se quitarán (N) + líneas `− Título — Artista` (formato hardcoded) | Editar con IA > previsualización | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:211-222 | informativo | ocasional | hay eliminaciones |
| Se añadirán (N) + líneas `+ Título — Artistas` (formato hardcoded) | Editar con IA > previsualización | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:226-236 | informativo | ocasional | hay adiciones |
| Se descartaron %1$d canciones sugeridas… | Editar con IA > previsualización | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:239-246 | informativo | ocasional | `skippedAdditions > 0` |
| %1$d quitadas, %2$d añadidas (Toast) | Editar con IA | app/src/main/kotlin/com/music/echo/ui/screens/playlist/AiModifyPlaylistDialog.kt:90-105 | informativo | ocasional | `state is Applied`; cierra el diálogo |

NOTA: no hay chips ni presets de instrucción; el único ejemplo está incrustado en la etiqueta del campo.

### 17.4.8 `SongPreviewController` — no expone ningún control propio

Comportamiento visible que el rediseño debe preservar:

- Tocar para previsualizar / volver a tocar para parar (solo una a la vez) — SongPreviewController.kt:109-115
- Pausa el reproductor principal al empezar y lo reanuda al parar (solo si estaba sonando) — :128, :200-215, :340-348
- Estado visual de la fila (activa + spinner) — :56, :60
- Auto-parada al terminar / al fallar — :308-311, :313-319
- Toast «Vista previa no disponible por ahora» — :104-106 (disparo :178 y :317)
- Volumen fijo 0.85 — :302
- Instanciado en AddMusicSheet.kt:74 y LocalPlaylistScreen.kt:215

---

## 17.5 COMPONENTES COMPARTIDOS

### 17.5.1 `SortHeader.kt` (cabecera de orden, usada por 8 pantallas)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (botón izquierdo: muestra el criterio activo, abre el desplegable) | Cualquier pantalla > cabecera de orden | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:65-81 | navegación | diaria | siempre |
| "Toggle sort order" (hardcoded, sin traducir) — invertir asc/desc; stateDescription "Descending"/"Ascending" (hardcoded) (solo icono) | Cabecera de orden > botón derecho | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:84-119 (tooltip :85-89, toggle :93) | conmutador | diaria | solo si `showDescending == true` y `sortType != PlaylistSongSortType.CUSTOM` (:61) |
| "Show sort options" (hardcoded, sin traducir) — abre/cierra el desplegable; stateDescription "Expanded"/"Collapsed" (solo icono) | Cabecera de orden > botón derecho (variante) | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:120-148 (cD :132) | navegación | ocasional | solo si `showDescending == false` (Mi Top) o `sortType == CUSTOM` (Lista local) |
| (una opción por valor del enum, con radio marcado) | Cabecera de orden > desplegable | app/src/main/kotlin/com/music/echo/ui/component/SortHeader.kt:159-181 | conmutador | diaria | solo con el desplegable abierto |

### 17.5.2 `LibraryViewToggle.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Vista lista (solo icono, `contentDescription = null` → sin etiqueta accesible) | Canciones / Artistas / Álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/component/LibraryViewToggle.kt:39-58 (icono `R.drawable.list` :52) | conmutador | ocasional | siempre donde se instancia (LibrarySongsScreen.kt:280, LibraryArtistsScreen.kt:211, LibraryAlbumsScreen.kt:189) |
| Vista cuadrícula (solo icono, `contentDescription = null`) | Canciones / Artistas / Álbumes > cabecera | app/src/main/kotlin/com/music/echo/ui/component/LibraryViewToggle.kt:39-58 (icono `R.drawable.grid_view` :53) | conmutador | ocasional | ídem |

### 17.5.3 `HideOnScrollFAB.kt` (3 sobrecargas: LazyListState / LazyGridState / ScrollState)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Lista AI (solo icono) | FAB pequeño superior | app/src/main/kotlin/com/music/echo/ui/component/HideOnScrollFAB.kt:61-72 / :128-139 / :195-206 (cD :69/:136/:203) | acción primaria | ocasional | solo si `onAiPlaylistClick != null` — ningún llamante lo pasa (ver MUERTO) |
| Reconocer música (solo icono) | FAB pequeño intermedio | app/src/main/kotlin/com/music/echo/ui/component/HideOnScrollFAB.kt:76-87 / :143-154 / :209-220 (cD :84/:151/:219) | acción primaria | ocasional | solo si `onRecognitionClick != null` — ningún llamante lo pasa (ver MUERTO) |
| (FAB principal, icono variable; `contentDescription = null`) | FAB inferior | app/src/main/kotlin/com/music/echo/ui/component/HideOnScrollFAB.kt:90-97 / :157-164 / :224-231 | acción primaria | diaria | `visible` del llamante y la lista se desplaza hacia arriba (`isScrollingUp`, :45/:112/:179) |

### 17.5.4 `EmptyPlaceholder.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (icono + texto de estado vacío; SIN ningún botón) | Estados vacíos de 8 pantallas | app/src/main/kotlin/com/music/echo/ui/component/EmptyPlaceholder.kt:22-49 | informativo | rara | según pantalla |

### 17.5.5 `DraggableScrollBarOverlay.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (barra de desplazamiento rápido arrastrable) | Lista local / Auto-lista / En caché / Mi Top > borde derecho | app/src/main/kotlin/com/music/echo/ui/component/DraggableScrollBarOverlay.kt:81-159 | navegación (gesto) | ocasional | solo si `contentCount > 15` y `contentCount > visibles` (:67-77) |

### 17.5.6 `ExpandableText.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (tocar el texto = expandir/contraer) | Artista / Álbum / Listas > "Acerca de" | app/src/main/kotlin/com/music/echo/ui/component/ExpandableText.kt:85-94 | conmutador | ocasional | solo si `hasOverflow` (> 3 líneas) |
| (tocar un enlace = abrir navegador externo) | ídem | app/src/main/kotlin/com/music/echo/ui/component/ExpandableText.kt:86-90 | navegación externa | rara | solo si hay `LinkSegment` con `url` |
| Mostrar más / Mostrar menos | ídem > chip bajo el texto | app/src/main/kotlin/com/music/echo/ui/component/ExpandableText.kt:97-112 (texto :107) | conmutador | ocasional | solo si `hasOverflow` |

### 17.5.7 `NavigationTitle.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| (fila entera del título de sección → "ver todo") + flecha `arrow_forward` | Artista / Álbum / Lista online > títulos de sección | app/src/main/kotlin/com/music/echo/ui/component/NavigationTitle.kt:50-52 (clic), :98-104 (flecha) | navegación | diaria | solo si `onClick != null` |
| Reproducir todo | Título de sección > botón contorneado | app/src/main/kotlin/com/music/echo/ui/component/NavigationTitle.kt:79-95 (texto :92) | acción primaria | ocasional | solo si `onPlayAllClick != null` — ningún llamante de estos ficheros lo pasa (ver MUERTO) |

### 17.5.8 `Library.kt` (filas/tarjetas de biblioteca)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Fila de artista → `artist/{id}` | Artistas > vista lista | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:55-57 | navegación | diaria | siempre |
| ⋯ del artista → `ArtistMenu` (solo icono, cD = null) | Artistas > fila | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:36-51 | acción secundaria | ocasional | siempre |
| Tarjeta de artista → `artist/{id}` | Artistas > cuadrícula | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:74-76 | navegación | diaria | siempre |
| (pulsación larga en tarjeta de artista → `ArtistMenu`) | Artistas > cuadrícula | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:77-85 | acción secundaria | ocasional | siempre |
| Fila de álbum → `album/{id}` | Álbumes > vista lista | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:121-123 | navegación | diaria | siempre |
| ⋯ del álbum → `AlbumMenu` (solo icono, cD = null) | Álbumes > fila | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:102-117 | acción secundaria | ocasional | siempre |
| Tarjeta de álbum → `album/{id}` | Álbumes / Álbumes favoritos > cuadrícula | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:147-149 | navegación | diaria | siempre |
| (pulsación larga en tarjeta de álbum → `AlbumMenu`) | Álbumes > cuadrícula | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:150-158 | acción secundaria | ocasional | siempre |
| Fila de lista → `online_playlist/…` o `local_playlist/…` | Listas > vista lista | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:220-225 | navegación | diaria | va a `online_playlist` solo si `!isEditable && songCount == 0 && remoteSongCount != 0`; si no, `local_playlist` |
| ⋯ de la lista → `PlaylistMenu` o `YouTubePlaylistMenu` (solo icono, cD = null) | Listas > fila | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:172-216 (bifurcación :175) | acción secundaria | diaria | `PlaylistMenu` si `isEditable \|\| songCount != 0`; si no `YouTubePlaylistMenu` y solo si hay `browseId` |
| Tarjeta de lista → abrir lista | Listas > cuadrícula | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:242-247 | navegación | diaria | misma bifurcación |
| (pulsación larga en tarjeta de lista → `PlaylistMenu` / `YouTubePlaylistMenu`) | Listas > cuadrícula | app/src/main/kotlin/com/music/echo/ui/component/Library.kt:248-286 | acción secundaria | ocasional | misma bifurcación |

---


---

# 18. AJUSTES

## 18.1 Ajustes (raíz) — `settings`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/SettingsScreen.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Buscar (campo de búsqueda de ajustes) | Ajustes | SettingsScreen.kt:131 | acción secundaria | ocasional | siempre |
| Borrar búsqueda (icono X) | Ajustes | SettingsScreen.kt:144 | acción secundaria | ocasional | solo si `searchQuery.isNotEmpty()` |
| Suscripción Pro (hardcoded) — desc. «Suscríbete o ingresa tu clave de licencia» (hardcoded) | Ajustes | SettingsScreen.kt:161 | navegación | rara | solo si `BuildConfig.REQUIRE_SUBSCRIPTION && subState != SUBSCRIPTION_ACTIVE` |
| Cuentas (hardcoded) | Ajustes | SettingsScreen.kt:171 | navegación | ocasional | siempre |
| Scrobbling — desc. «Last.fm & ListenBrainz» (hardcoded) | Ajustes | SettingsScreen.kt:179 | navegación | ocasional | siempre |
| Apariencia | Ajustes | SettingsScreen.kt:189 | navegación | ocasional | siempre |
| Aura Hi-Res Update (hardcoded) — desc. «Cambios de esta versión y nuevas actualizaciones» (hardcoded) | Ajustes | SettingsScreen.kt:199 | navegación | ocasional | siempre |
| Ajustes del reproductor (hardcoded) | Ajustes | SettingsScreen.kt:209 | navegación | ocasional | siempre |
| Sonido y ecualización (hardcoded) | Ajustes | SettingsScreen.kt:217 | navegación | ocasional | siempre |
| Rendimiento (hardcoded) | Ajustes | SettingsScreen.kt:226 | navegación | rara | siempre |
| Escuchar juntos | Ajustes | SettingsScreen.kt:235 | navegación | ocasional | siempre — OJO: navega a `Screens.ListenTogether.route` = `"listen_together"` (pantalla principal), NO a `settings/integrations/listen_together` |
| Contenido | Ajustes | SettingsScreen.kt:244 | navegación | ocasional | siempre |
| Traducción de letras con IA | Ajustes | SettingsScreen.kt:253 | navegación | ocasional | siempre |
| Privacidad | Ajustes | SettingsScreen.kt:262 | navegación | rara | siempre |
| Almacenamiento | Ajustes | SettingsScreen.kt:271 | navegación | ocasional | siempre |
| Copias de seguridad y restauración | Ajustes | SettingsScreen.kt:280 | navegación | rara | siempre |
| Estadísticas | Ajustes | SettingsScreen.kt:289 | navegación | ocasional | siempre |
| Registros — desc. «Consulta, copia y comparte los registros de la app y el último informe de fallos» | Ajustes | SettingsScreen.kt:298 | navegación | rara | siempre |
| Acerca de | Ajustes | SettingsScreen.kt:308 | navegación | rara | siempre |
| Resultados de sub-ajustes indexados (una fila por coincidencia, ~400 entradas) | Ajustes (búsqueda) | SettingsScreen.kt:326 | navegación | ocasional | solo si `searchQuery.isNotEmpty()` |
| «No se encontraron ajustes para "%1$s"» | Ajustes (búsqueda) | SettingsScreen.kt:340 | informativo | rara | solo si lista vacía y hay consulta |
| Atrás (flecha barra superior) | Ajustes | SettingsScreen.kt:369 | navegación | constante | siempre |

### 1.1 Suscripción Pro (sub-pantalla en línea, no es ruta)

Archivo: `app/src/main/kotlin/com/music/echo/license/LicenseScreens.kt` (invocada desde `SettingsScreen.kt:77`)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Clave de licencia (campo de texto) (hardcoded) | Ajustes > Suscripción Pro | LicenseScreens.kt:285 | acción secundaria | rara | siempre (dentro de la sub-pantalla) |
| Activar / «Verificando…» (hardcoded) | Ajustes > Suscripción Pro | LicenseScreens.kt:302 | acción primaria | rara | habilitado solo si `!loading && key.isNotBlank()` |
| Suscribirme por $X/mes (hardcoded) | Ajustes > Suscripción Pro | LicenseScreens.kt:309 | acción primaria | rara | siempre |
| Volver (hardcoded) | Ajustes > Suscripción Pro | LicenseScreens.kt:323 | navegación | rara | siempre |
| Autodetección de clave desde el portapapeles al volver a la app | Ajustes > Suscripción Pro | LicenseScreens.kt:264 | acción automática (informativo) | rara | ON_RESUME y clave detectada distinta a la ya probada |

---

## 18.2 Ajustes > Apariencia — `settings/appearance`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/AppearanceSettings.kt`

### Grupo «Tema»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Tema — desc. «Personaliza el tema de la app» | Ajustes > Apariencia | AppearanceSettings.kt:996 | navegación | ocasional | siempre |
| Habilitar alta frecuencia de actualización — desc. «Forzar a la pantalla a funcionar a la frecuencia de actualización más alta permitida (p. ej., 120Hz)» | Ajustes > Apariencia | AppearanceSettings.kt:1004 | conmutador | rara | siempre |
| Respuesta háptica — desc. «Vibra al tocar y al desplazarte por toda la app» | Ajustes > Apariencia | AppearanceSettings.kt:1027 | conmutador | rara | siempre |
| Habilitar tema dinámico | Ajustes > Apariencia | AppearanceSettings.kt:1053 | conmutador | ocasional | solo si `!isUsingCustomColor` (sin color personalizado elegido) |

### Grupo «Minirreproductor»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Estilo de fondo del minirreproductor | Ajustes > Apariencia | AppearanceSettings.kt:1085 | navegación (abre diálogo enum) | rara | siempre |

### Grupo «Reproductor»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Inspirado en Apple Music (hardcoded) | Ajustes > Apariencia | AppearanceSettings.kt:1120 | conmutador | ocasional | siempre |
| Ocultar control de volumen (hardcoded) — desc. «Oculta el control de volumen en el reproductor estilo Apple Music» (hardcoded) | Ajustes > Apariencia | AppearanceSettings.kt:1151 | conmutador | rara | solo si `!useNewPlayerDesign` |
| Estilo de fondo del reproductor | Ajustes > Apariencia | AppearanceSettings.kt:1172 | navegación (diálogo enum) | ocasional | siempre |
| Liquid Glass (Beta) — desc. «Superficies de cristal para el reproductor, el mini reproductor y la barra de navegación» / «No disponible en este dispositivo» | Ajustes > Apariencia | AppearanceSettings.kt:1196 | navegación | rara | fila SIEMPRE visible; habilitada solo si `glassEligible` (API ≥ 31, gama no baja, no TV/coche, Modo rendimiento OFF) |
| Ocultar miniatura del reproductor — desc. «Reemplazar la carátula del álbum con el logotipo de la aplicación en el reproductor» | Ajustes > Apariencia | AppearanceSettings.kt:1210 | conmutador | rara | siempre |
| Radio de esquina de la miniatura — desc. «Cambia el radio de las esquinas de la miniatura de la portada del álbum» | Ajustes > Apariencia | AppearanceSettings.kt:1231 | navegación (abre selector) | rara | siempre |
| Recortar portada del álbum — desc. «Forzar una relación de aspecto cuadrada recortando miniaturas de vídeo» | Ajustes > Apariencia | AppearanceSettings.kt:1244 | conmutador | rara | siempre |
| Colores de los botones del reproductor | Ajustes > Apariencia | AppearanceSettings.kt:1265 | navegación (diálogo enum) | rara | siempre |
| Estilo de la barra del reproductor | Ajustes > Apariencia | AppearanceSettings.kt:1279 | navegación (diálogo enum) | rara | siempre |
| Habilitar deslizar para cambiar de canción | Ajustes > Apariencia | AppearanceSettings.kt:1295 | conmutador | ocasional | siempre |
| Canvas (lienzo) — desc. «Muestra portadas animadas (video) en el reproductor y los álbumes cuando estén disponibles» | Ajustes > Apariencia | AppearanceSettings.kt:1315 | conmutador | ocasional | siempre |
| Miniatura giratoria — desc. «Activa un efecto de animación de miniatura giratoria» | Ajustes > Apariencia | AppearanceSettings.kt:1336 | conmutador | rara | siempre |
| Mostrar lienzo del álbum — desc. «Muestra el lienzo animado en el álbum» | Ajustes > Apariencia | AppearanceSettings.kt:1357 | conmutador | rara | siempre |
| Mostrar botón de comentarios — desc. «Muestra un botón para ver comentarios en la cola del reproductor» | Ajustes > Apariencia | AppearanceSettings.kt:1378 | conmutador | rara | siempre |
| Mostrar códec en el reproductor (hardcoded) — desc. «Muestra la información del códec de audio debajo de la línea de tiempo» (hardcoded) | Ajustes > Apariencia | AppearanceSettings.kt:1399 | conmutador | rara | siempre |
| Sensibilidad al deslizar el mini reproductor | Ajustes > Apariencia | AppearanceSettings.kt:1423 | navegación (abre diálogo con slider) | rara | solo si `swipeThumbnail` está activado |

### Grupo «Letra»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Posición de la letra de la canción | Ajustes > Apariencia | AppearanceSettings.kt:1522 | navegación (diálogo enum: Izquierda/Centro/Derecha) | rara | siempre |
| Estilo de animación de la letra | Ajustes > Apariencia | AppearanceSettings.kt:1536 | navegación (diálogo enum) | rara | siempre |
| Habilitar efecto de brillo de la letra — desc. «Añadir animación de brillo y efecto de rebote a la letra activa» | Ajustes > Apariencia | AppearanceSettings.kt:1557 | conmutador | rara | siempre |
| Desenfoque de letras Apple Music — desc. «Aplica desenfoque a las líneas de letra inactivas para un efecto de enfoque premium» | Ajustes > Apariencia | AppearanceSettings.kt:1579 | conmutador | rara | solo si `lyricsAnimationStyle == LyricsAnimationStyle.echomusic_1` |
| Desenfoque de letras estándar — desc. reutiliza «Aplica desenfoque a las líneas de letra inactivas…» | Ajustes > Apariencia | AppearanceSettings.kt:1601 | conmutador | rara | siempre |
| Tamaño del texto de la letra — desc. «{n} sp» | Ajustes > Apariencia | AppearanceSettings.kt:1622 | navegación (diálogo con slider) | rara | siempre |
| Interlineado de la letra — desc. «{n}x» | Ajustes > Apariencia | AppearanceSettings.kt:1628 | navegación (diálogo con slider) | rara | siempre |
| Cambiar letra al hacer clic | Ajustes > Apariencia | AppearanceSettings.kt:1634 | conmutador | rara | siempre |
| Desplazamiento automático de la letra | Ajustes > Apariencia | AppearanceSettings.kt:1654 | conmutador | rara | siempre |
| Desliza para cambiar de canción — desc. «Desliza a la izquierda o a la derecha en la sección del artista y el nombre de la canción en las letras a pantalla completa…» | Ajustes > Apariencia | AppearanceSettings.kt:1674 | conmutador | rara | siempre |
| Mostrar reproducir/pausar en la miniatura — desc. «Muestra un botón de reproducir/pausar superpuesto en la miniatura…» | Ajustes > Apariencia | AppearanceSettings.kt:1695 | conmutador | rara | siempre |
| Ocultar la barra de estado en pantalla completa — desc. «Oculta la barra de estado cuando el modo de letras a pantalla completa está activo» | Ajustes > Apariencia | AppearanceSettings.kt:1716 | conmutador | rara | siempre |

### Grupo «Otros»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Pestaña abierta predeterminada | Ajustes > Apariencia | AppearanceSettings.kt:1745 | navegación (diálogo enum: Inicio/Buscar/Biblioteca…) | rara | siempre |
| Cambiar chip de biblioteca predeterminado | Ajustes > Apariencia | AppearanceSettings.kt:1759 | navegación (diálogo enum: Álbumes/Artistas/Listas/Canciones/Local) | rara | siempre |
| Deslizar la canción a la derecha para reproducirla a continuación o a la izquierda para añadirla a la cola | Ajustes > Apariencia | AppearanceSettings.kt:1776 | conmutador | rara | siempre |
| Deslizar la canción para quitarla de la lista de reproducción | Ajustes > Apariencia | AppearanceSettings.kt:1796 | conmutador | rara | siempre |
| Escuchar juntos en la barra superior — desc. «Mostrar Escuchar juntos en la barra superior en lugar de en la barra de navegación» | Ajustes > Apariencia | AppearanceSettings.kt:1816 | conmutador | rara | siempre |
| Tamaño de la celda de la cuadrícula | Ajustes > Apariencia | AppearanceSettings.kt:1837 | navegación (diálogo enum: Pequeño/Grande) | rara | siempre |
| Densidad de pantalla | Ajustes > Apariencia | AppearanceSettings.kt:1850 | navegación (diálogo enum + reinicio) | rara | siempre |

### Grupo «Listas de reproducción automáticas»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Ver lista de reproducción "Canciones que me gustan" | Ajustes > Apariencia | AppearanceSettings.kt:1866 | conmutador | rara | siempre |
| Ver lista de reproducción "Descargado" | Ajustes > Apariencia | AppearanceSettings.kt:1886 | conmutador | rara | siempre |
| Exportado | Ajustes > Apariencia | AppearanceSettings.kt:1906 | conmutador | rara | siempre |
| Ver lista de reproducción "Top" | Ajustes > Apariencia | AppearanceSettings.kt:1926 | conmutador | rara | siempre |
| Ver lista de reproducción "En caché" | Ajustes > Apariencia | AppearanceSettings.kt:1946 | conmutador | rara | siempre |
| Mostrar lista de reproducción "Subidas" | Ajustes > Apariencia | AppearanceSettings.kt:1966 | conmutador | rara | siempre |

### Barra superior

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (tap) / Ir al inicio (mantener pulsado) | Ajustes > Apariencia | AppearanceSettings.kt:1996 | navegación | constante | siempre |

---

## 18.3 Ajustes > Apariencia > Tema y colores — `settings/appearance/theme`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/ThemeScreen.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (barra superior «Tema y colores») | Ajustes > Apariencia > Tema | ThemeScreen.kt:235 | navegación | constante | siempre |
| Modo de tema (encabezado de sección) | Ajustes > Apariencia > Tema | ThemeScreen.kt:262 | informativo | — | siempre |
| Tema del sistema (tarjeta) | Ajustes > Apariencia > Tema | ThemeScreen.kt:275 | acción primaria (selección exclusiva) | ocasional | siempre |
| Light (hardcoded) (tarjeta) | Ajustes > Apariencia > Tema | ThemeScreen.kt:285 | acción primaria (selección exclusiva) | ocasional | siempre |
| Dark (hardcoded) (tarjeta) | Ajustes > Apariencia > Tema | ThemeScreen.kt:297 | acción primaria (selección exclusiva) | ocasional | siempre |
| AMOLED (hardcoded) (tarjeta) — activa además negro puro en el minirreproductor | Ajustes > Apariencia > Tema | ThemeScreen.kt:304 | acción primaria (selección exclusiva) | ocasional | siempre |
| Paleta de colores (encabezado) | Ajustes > Apariencia > Tema | ThemeScreen.kt:323 | informativo | — | siempre |
| 44 muestras de color (una fila de control por muestra) — ver lista abajo | Ajustes > Apariencia > Tema | ThemeScreen.kt:347 (bucle) / definidas en ThemeScreen.kt:129-177 | acción primaria (selección exclusiva) | ocasional | siempre |
| Intensidad del color (encabezado + texto explicativo variable) | Ajustes > Apariencia > Tema | ThemeScreen.kt:425 | informativo | — | siempre; el texto cambia si hay preset activo / si no hay color elegido |
| Suave — «Tonos de Material (predeterminado)» | Ajustes > Apariencia > Tema | ThemeScreen.kt:453 | acción primaria (selección exclusiva) | rara | siempre (inerte si preset activo o color = dinámico) |
| Vívido — «Saturación completa» | Ajustes > Apariencia > Tema | ThemeScreen.kt:453 | acción primaria (selección exclusiva) | rara | ídem |
| Fiel — «Tu color exacto» | Ajustes > Apariencia > Tema | ThemeScreen.kt:453 | acción primaria (selección exclusiva) | rara | ídem |
| Color personalizado (encabezado) — «Escribe un código hexadecimal. #RRGGBB o el corto #RGB…» | Ajustes > Apariencia > Tema | ThemeScreen.kt:563 | informativo | — | siempre |
| Vista previa del color (recuadro «Aa») | Ajustes > Apariencia > Tema | ThemeScreen.kt:603 | informativo | — | siempre |
| Código hexadecimal (campo de texto, máx. 9 caracteres) | Ajustes > Apariencia > Tema | ThemeScreen.kt:616 | acción secundaria | rara | siempre; muestra «Código no válido. Usa #RRGGBB o #RGB.» si no parsea |
| Aplicar color (botón) | Ajustes > Apariencia > Tema | ThemeScreen.kt:640 | acción primaria | rara | habilitado solo si el hex parsea (`parsed != null`) |

Muestras de la paleta (ThemeScreen.kt:129-177), en orden: Dinámico · Muestreo · Carmesí · Rosa · Morado · Púrpura intenso · Índigo · Azul · Celeste · Cian · Verde azulado · Verde · Verde claro · Lima · Amarillo · Ámbar · Naranja · Naranja intenso · Marrón · Gris · Gris azulado · Rojo vivo · Naranja vivo · Ámbar vivo · Amarillo vivo · Lima vivo · Verde vivo · Menta vivo · Cian vivo · Azul vivo · Violeta vivo · Magenta vivo · Rosa vivo · Rojo oscuro · Ámbar oscuro · Verde oscuro · Verde azulado oscuro · Azul marino · Violeta oscuro · Ciruela · Carbón · Marfil. (44 controles individuales.)

---

## 18.4 Ajustes > Apariencia > Liquid Glass (Beta) — `settings/appearance/liquidglass`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/GlassEffectSettings.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Activar Liquid Glass — desc. «Renderizar el cristal líquido consume muchos recursos…» / «No disponible en este dispositivo» | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:168 | conmutador | rara | fila visible siempre; habilitada solo si `glassEligible` |
| Viveza — «Aumenta la saturación del fondo del cristal» | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:209 | navegación (diálogo con slider 0–2) | rara | siempre |
| Radio de desenfoque — «Cantidad de desenfoque en la superficie de cristal» | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:215 | navegación (diálogo con slider 0–100) | rara | siempre |
| Altura de refracción de la lente | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:221 | navegación (diálogo con slider 0–1) | rara | siempre |
| Cantidad de refracción de la lente | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:226 | navegación (diálogo con slider 0–1) | rara | siempre |
| Aberración cromática | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:231 | conmutador | rara | siempre |
| Efecto de profundidad | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:251 | conmutador | rara | siempre |
| Tinte de la superficie — «Color de tinte aplicado a la superficie de cristal…» | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:279 | navegación (abre selector de color) | rara | siempre |
| Opacidad de la superficie — «Opacidad del tinte del cristal para mejorar la legibilidad» | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:285 | navegación (diálogo con slider 0–1) | rara | siempre |
| Color del texto sobre cristal | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:291 | navegación (abre selector de color) | rara | siempre |
| Reproductor de cristal — desc. «Aún no disponible — el reproductor a pantalla completa no usa el efecto de cristal» | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:309 | conmutador (DESHABILITADO en código: `enabled = false`, `onCheckedChange = null`) | rara | nunca operable |
| Mini reproductor de cristal | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:329 | conmutador | rara | siempre |
| Barra de navegación de cristal | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:349 | conmutador | rara | siempre |
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:515 | navegación | constante | siempre |

Botones dentro de cada diálogo de slider (Viveza / Radio / Altura lente / Cantidad lente / Opacidad): **Restablecer** (GlassEffectSettings.kt:385, 404, 423, 442, 461), **Cancelar** (387, 406, 425, 444, 463), **OK [SIN TRADUCIR]** (388, 407, 426, 445, 464). El slider en sí: 394, 413, 432, 451, 471.

---

## 18.5 Ajustes > Ajustes del reproductor — `settings/player`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/PlayerSettings.kt`

### Grupo «Ahorro de datos» (hardcoded)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Modo ahorro de datos (hardcoded) — desc. «Fuerza el audio en Opus y desactiva letras automáticas, videos, precarga, canvas y scrobbling para gastar el mínimo de datos» (hardcoded) | Ajustes > Reproductor | PlayerSettings.kt:467 | conmutador | ocasional | siempre |

### Grupo «Reproductor»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Calidad de sonido | Ajustes > Reproductor | PlayerSettings.kt:493 | navegación (EnumDialog) | ocasional | siempre |
| Mostrar notificaciones de audio de respaldo (hardcoded) — desc. «Muestra una notificación cuando se baja a una calidad de transmisión inferior» (hardcoded) | Ajustes > Reproductor | PlayerSettings.kt:508 | conmutador | rara | siempre |
| Calidad de descarga | Ajustes > Reproductor | PlayerSettings.kt:529 | navegación (EnumDialog) | ocasional | siempre |
| Transición suave — desc. «Transición suave entre canciones» / «Desactivado porque…» | Ajustes > Reproductor | PlayerSettings.kt:546 | conmutador | ocasional | forzado a OFF si `isLosslessSelected`; al activar muestra el diálogo «Función beta» |
| Duración de la transición suave (slider en línea, 1–15 s) | Ajustes > Reproductor | PlayerSettings.kt:592 (slider 596) | acción secundaria (slider) | ocasional | solo si `crossfadeEnabled && !isLosslessSelected` |
| Estilo de transición (hardcoded) — desc. muestra la curva activa | Ajustes > Reproductor | PlayerSettings.kt:607 | navegación (EnumDialog de 9 curvas) | ocasional | solo si `crossfadeEnabled && !isLosslessSelected` |
| Desactivar para álbumes sin pausas — desc. «No hacer transición suave si el álbum es sin pausas» | Ajustes > Reproductor | PlayerSettings.kt:613 | conmutador | rara | solo si `crossfadeEnabled && !isLosslessSelected` |
| Entrada suave al cambiar de canción (hardcoded) — desc. «Al saltar o elegir otra canción a mano, la nueva entra con un fundido corto (estilo AIMP) en vez de golpe» (hardcoded) | Ajustes > Reproductor | PlayerSettings.kt:638 | conmutador | ocasional | siempre |
| Duración del historial (slider en línea, 5–120) | Ajustes > Reproductor | PlayerSettings.kt:651 (slider 663) | acción secundaria (slider) | rara | siempre |
| Activar descarga (offload) — desc. larga + motivos de bloqueo («Desactivado porque la transición suave está activa» / «…el ecualizador está activo» / «…el Volumen seguro está activo») | Ajustes > Reproductor | PlayerSettings.kt:684 | conmutador | rara | operable solo si `offloadBlockedReason == null` |
| Precargar la siguiente canción (hardcoded) — desc. «Almacena en caché la siguiente canción para reproducción sin cortes» (hardcoded) | Ajustes > Reproductor | PlayerSettings.kt:710 | conmutador | ocasional | siempre |
| Saltar segmentos sin música — desc. «Salta patrocinios, autopromoción y partes sin música usando la base comunitaria SponsorBlock…» | Ajustes > Reproductor | PlayerSettings.kt:732 | conmutador | ocasional | siempre |
| Límite de precarga (hardcoded) (slider en línea 1–10, 9 pasos) | Ajustes > Reproductor | PlayerSettings.kt:755 (slider 758) | acción secundaria (slider) | rara | solo si `preloadNextSongEnabled` |
| Precargar letras (hardcoded) — desc. «También almacena en caché las letras de las canciones precargadas» (hardcoded) | Ajustes > Reproductor | PlayerSettings.kt:771 | conmutador | rara | solo si `preloadNextSongEnabled` |
| Transmitir con Google Cast — desc. «Habilitar la transmisión de audio a Chromecast y otros dispositivos compatibles con Cast» | Ajustes > Reproductor | PlayerSettings.kt:795 | conmutador | rara | solo si `BuildConfig.CAST_AVAILABLE` |
| Búsqueda progresiva — desc. «Si está habilitado, agrega 5 segundos adicionales de forma incremental en cada salto de búsqueda» | Ajustes > Reproductor | PlayerSettings.kt:817 | conmutador | rara | siempre |

### Grupo «Cola»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Cola persistente — desc. «Restaura tu última cola al iniciar la aplicación» | Ajustes > Reproductor | PlayerSettings.kt:846 | conmutador | rara | siempre |
| Cargar automáticamente más canciones — desc. «Añade automáticamente más canciones cuando llegues al final de la cola, si es posible» | Ajustes > Reproductor | PlayerSettings.kt:867 | conmutador | ocasional | siempre |
| Deshabilitar cargar más al repetir todo — desc. «No cargar automáticamente más canciones ni contenido similar cuando el modo repetir todo esté habilitado» | Ajustes > Reproductor | PlayerSettings.kt:888 | conmutador | rara | siempre |
| Habilitar contenido similar — desc. «Añadir automáticamente más canciones similares cuando se alcance el final de la cola» | Ajustes > Reproductor | PlayerSettings.kt:909 | conmutador | ocasional | siempre |
| Aleatorio mejorado (hardcoded) — desc. «El aleatorio recuerda qué canciones ya sonaron en cada lista (y en toda la biblioteca) y no repite ninguna hasta haberlas puesto todas…» (hardcoded) | Ajustes > Reproductor | PlayerSettings.kt:930 | conmutador | ocasional | siempre |
| Aleatorio persistente — desc. «Mantener la reproducción aleatoria habilitada al iniciar nuevas canciones o listas de reproducción» | Ajustes > Reproductor | PlayerSettings.kt:951 | conmutador | ocasional | siempre |
| Recuerda mezclar y repetir — desc. «Recuerda el modo aleatorio y repetir al reiniciar la aplicación» | Ajustes > Reproductor | PlayerSettings.kt:972 | conmutador | rara | siempre |
| Aleatorizar lista de reproducción/álbum primero — desc. «Al reproducir aleatoriamente, reproduce primero todas las canciones de la lista/álbum original y luego el contenido similar» | Ajustes > Reproductor | PlayerSettings.kt:993 | conmutador | rara | siempre |
| Evitar pistas duplicadas en la cola — desc. «Al agregar una pista a la cola, elimínela de su posición anterior si ya está presente» | Ajustes > Reproductor | PlayerSettings.kt:1014 | conmutador | rara | siempre |
| Saltar automáticamente a la siguiente canción cuando se produce un error — desc. «Asegura una experiencia de reproducción continua» | Ajustes > Reproductor | PlayerSettings.kt:1035 | conmutador | rara | siempre |

### Grupo «Otros»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Detener la música cuando se cierre la aplicación | Ajustes > Reproductor | PlayerSettings.kt:1064 | conmutador | rara | siempre |
| Pausar la música cuando se silencia el medio | Ajustes > Reproductor | PlayerSettings.kt:1084 | conmutador | rara | siempre |
| Reanudar al conectar Bluetooth | Ajustes > Reproductor | PlayerSettings.kt:1104 | conmutador | rara | siempre |
| Mantener la pantalla encendida cuando el reproductor está expandido | Ajustes > Reproductor | PlayerSettings.kt:1124 | conmutador | rara | siempre |

### Grupo «Avanzado» (hardcoded)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Descifrado de reproducción — desc. «Descifrado autorreparable de las transmisiones» | Ajustes > Reproductor | PlayerSettings.kt:1152 | navegación | rara | siempre |
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Reproductor | PlayerSettings.kt:1169 | navegación | constante | siempre |

---

## 18.6 Ajustes > Sonido y ecualización — `settings/sound`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/SoundSettings.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Ecualizador — desc. «Activa o desactiva el procesamiento de audio profesional» | Ajustes > Sonido | SoundSettings.kt:69 | navegación | ocasional | siempre |
| Auto-EQ (por auricular) (hardcoded) — desc. «Busca tu modelo y aplica su perfil AutoEq» (hardcoded) | Ajustes > Sonido | SoundSettings.kt:75 | navegación | rara | siempre |
| Volumen seguro (hardcoded) — desc. «Baja los temas muy fuertes a un nivel parejo y agrega un limitador suave… Apagado = reproducción bit-perfect Hi-Res.» (hardcoded) | Ajustes > Sonido | SoundSettings.kt:91 | conmutador | ocasional | siempre (por defecto ON) |

Nota: esta pantalla NO tiene barra superior ni botón Atrás propios (`SoundSettings.kt` no declara `TopAppBar`).

---

## 18.7 Ajustes > Sonido > Ecualizador — `settings/equalizer`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqScreen.kt`
(También accesible desde el reproductor: `ui/menu/PlayerMenu.kt:838` y `ui/player/Player.kt:2100`.)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (barra superior «Ecualizador») | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:128 | navegación | constante | siempre |
| Ecualizador (activar/desactivar) — desc. «Activa o desactiva el procesamiento de audio profesional» | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:299 | conmutador | ocasional | siempre |
| «Auto-EQ activo — tu EQ se suma encima» (chip informativo) (hardcoded) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:346 | informativo | ocasional | solo si hay un perfil Auto-EQ aplicado |
| Quitar Auto-EQ (hardcoded) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:349 | destructiva | rara | solo si hay un perfil Auto-EQ aplicado |
| Guardar (preajuste) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:392 | acción primaria | ocasional | siempre |
| Exportar perfiles (hardcoded) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:424 | acción secundaria | rara | siempre (abre SAF `aura-eq-perfiles.json`) |
| Importar (hardcoded) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:429 | acción secundaria | rara | siempre (abre SAF `application/json`) |
| EQ por dispositivo (hardcoded) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:437 | navegación (abre diálogo) | rara | siempre |
| Preamplificación (slider, rango −20 dB … +6 dB) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:653 | acción secundaria (slider) | ocasional | siempre (inerte si EQ desactivado) |
| Chips de preajustes (fila con scroll horizontal; uno por preajuste) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:719 | acción primaria (selección) | ocasional | `onClick` solo actúa `if (enabled)` |
| 10 sliders de banda: 31 / 62 / 125 / 250 / 500 / 1k / 2k / 4k / 8k / 16k Hz | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:808 (bucle) — slider en 1366; etiquetas en `eq/data/EqConstants.kt:9-12` | acción primaria (slider ×10) | diaria | modo Gráfico; inerte si EQ desactivado |
| Restablecer (EQ gráfico) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:821 | destructiva | ocasional | habilitado solo si EQ activo |
| Gráfico (hardcoded) (botón segmentado) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:838 | acción primaria (cambio de modo) | ocasional | `onClick` solo `if (enabled)` |
| Paramétrico (hardcoded) (botón segmentado) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:844 | acción primaria (cambio de modo) | ocasional | `onClick` solo `if (enabled)` |
| Curva paramétrica interactiva (arrastre de bandas sobre el gráfico) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1088 (`pointerInput`) | acción primaria (gesto) | diaria | solo en modo Paramétrico |
| Q (slider de la banda paramétrica seleccionada) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1224 | acción secundaria (slider) | ocasional | solo modo Paramétrico, con banda seleccionada |
| Quitar banda (hardcoded) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1245 | destructiva | ocasional | habilitado solo si `canRemove` |
| Añadir banda (hardcoded) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1263 | acción primaria | ocasional | solo modo Paramétrico |
| Restablecer (paramétrico) (hardcoded) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1273 | destructiva | ocasional | solo modo Paramétrico |
| Tipo de filtro: PK / LSC / HSC (hardcoded, 3 botones segmentados) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1332 (bucle) | acción primaria (selección) | ocasional | `onClick` solo `if (enabled)` |
| Personalizado — icono de editar (gestionar preajustes) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1423 | navegación (abre diálogo) | rara | siempre |
| Fila de preajuste guardado (aplicar) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1446 (bucle) | acción primaria | ocasional | `onClick` solo `if (enabled)` |

---

## 18.8 Ajustes > Sonido > Auto-EQ (por auricular) — `settings/sound/autoeq`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/autoeq/AutoEqScreen.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Auto-EQ activado (tarjeta de estado, hardcoded) | Ajustes > Sonido > AutoEQ | AutoEqScreen.kt:171 | informativo | ocasional | solo si hay un perfil aplicado |
| «Catálogo: N modelos» / «Catálogo no cargado» (hardcoded) | Ajustes > Sonido > AutoEQ | AutoEqScreen.kt:191 | informativo | ocasional | siempre |
| Actualizar base de datos / «Actualizando…» (hardcoded) | Ajustes > Sonido > AutoEQ | AutoEqScreen.kt:196 | acción secundaria | rara | siempre |
| Buscar (ej. WH-1000XM5) (campo de texto, hardcoded) | Ajustes > Sonido > AutoEQ | AutoEqScreen.kt:204 | acción secundaria | ocasional | siempre |
| «No se pudo cargar el catálogo AutoEq. Pulsa 'Actualizar base de datos'.» (hardcoded) | Ajustes > Sonido > AutoEQ | AutoEqScreen.kt:216 | informativo | rara | solo si el catálogo falló |
| Favorito (estrella) por modelo | Ajustes > Sonido > AutoEQ | AutoEqScreen.kt:234 | acción secundaria (conmutador) | ocasional | una por fila del catálogo |
| Fila de auricular (aplicar perfil) | Ajustes > Sonido > AutoEQ | AutoEqScreen.kt:242 | acción primaria | rara | `clickable(enabled = eqEnabled)` — inerte si el ecualizador está apagado |

---

## 18.9 Ajustes > Contenido — `settings/content`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/ContentSettings.kt`

### Grupo «General»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Idioma de contenido predeterminado | Ajustes > Contenido | ContentSettings.kt:631 | navegación (diálogo de lista) | rara | siempre |
| País de contenido predeterminado | Ajustes > Contenido | ContentSettings.kt:641 | navegación (diálogo de lista) | rara | siempre |
| Región de sugerencias (hardcoded) | Ajustes > Contenido | ContentSettings.kt:651 | navegación (hoja inferior) | rara | siempre |
| Ocultar contenido explícito | Ajustes > Contenido | ContentSettings.kt:661 | conmutador | rara | siempre |
| Ocultar canciones de vídeo | Ajustes > Contenido | ContentSettings.kt:681 | conmutador | rara | siempre |
| Ocultar Shorts | Ajustes > Contenido | ContentSettings.kt:702 | conmutador | rara | siempre |

### Grupo «Página del Artista»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Mostrar descripción del artista | Ajustes > Contenido | ContentSettings.kt:730 | conmutador | rara | siempre |
| Ver numero de suscriptores | Ajustes > Contenido | ContentSettings.kt:750 | conmutador | rara | siempre |
| Ver oyentes mensuales | Ajustes > Contenido | ContentSettings.kt:770 | conmutador | rara | siempre |
| Mostrar video lienzo del artista — desc. «Muestra un video en bucle junto al nombre del artista en su página» | Ajustes > Contenido | ContentSettings.kt:790 | conmutador | rara | siempre |
| Mostrar video de fondo del artista — desc. «Muestra un video artístico en movimiento en bucle detrás de la imagen del artista» | Ajustes > Contenido | ContentSettings.kt:811 | conmutador | rara | siempre |

### Grupo «Idioma de la aplicación»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Idioma de la aplicación (abre los ajustes de idioma per-app del sistema) | Ajustes > Contenido | ContentSettings.kt:841 | navegación | rara | solo Android 13+ (`SDK_INT >= TIRAMISU`) |
| Idioma de la aplicación (diálogo interno de selección) | Ajustes > Contenido | ContentSettings.kt:854 | navegación (diálogo de lista) | rara | solo Android 12 o anterior |

### Grupo «Proxy»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Versión IP de la red (Automático / IPv4 forzado / IPv6 forzado) | Ajustes > Contenido | ContentSettings.kt:873 | navegación (diálogo enum) | rara | siempre |
| Activar «proxy» | Ajustes > Contenido | ContentSettings.kt:888 | conmutador | rara | siempre |
| Configurar proxy | Ajustes > Contenido | ContentSettings.kt:911 | navegación (diálogo) | rara | solo si `proxyEnabled` |

### Grupo «Letra»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Activar proveedor de letras LrcLib | Ajustes > Contenido | ContentSettings.kt:926 | conmutador | rara | siempre |
| Activar proveedor de letras KuGou | Ajustes > Contenido | ContentSettings.kt:946 | conmutador | rara | siempre |
| Habilitar Better Lyrics — desc. «Usar el proveedor Better Lyrics para obtener letras sincronizadas palabra por palabra» | Ajustes > Contenido | ContentSettings.kt:966 | conmutador | rara | siempre |
| Habilitar letras de SimpMusic — desc. «Utiliza el proveedor SimpMusic Lyrics para sincronizar las letras» | Ajustes > Contenido | ContentSettings.kt:987 | conmutador | rara | siempre |
| YouLyPlus (hardcoded) — desc. «Proveedor multiservidor LyricsPlus (backend de la extensión YouLy+)» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1008 | conmutador | rara | siempre |
| PaxSenix (hardcoded) — desc. «Letras sincronizadas con calidad Apple Music y precisión por sílaba» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1029 | conmutador | rara | siempre |
| Unison (hardcoded) — desc. «Base de letras colaborativa (unison.boidu.dev); ultimo recurso, su catalogo aun es escaso» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1050 | conmutador | rara | siempre |
| Prioridad de proveedores de letras — desc. «Arrastra para reordenar qué proveedor se intenta primero» | Ajustes > Contenido | ContentSettings.kt:1071 | navegación (diálogo con lista arrastrable) | rara | siempre |
| Romanización de letras | Ajustes > Contenido | ContentSettings.kt:1077 | navegación | rara | siempre |

### Grupo «Inicio y descubrimiento» (hardcoded)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Solo recomendaciones por mis gustos (hardcoded) — desc. «El inicio muestra solo lo basado en tus gustos: artistas seguidos, lo que escuchas y tus favoritos…» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1118 | conmutador | ocasional | siempre |
| Inicio enriquecido (hardcoded) — desc. «Carátulas más grandes y presentación editorial en el inicio…» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1139 | conmutador | ocasional | siempre |
| Mantener el estilo en autoplay (hardcoded) — desc. «El reproductor automático intenta seguir en la misma línea…» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1160 | conmutador | ocasional | siempre |
| Aprender géneros con datos móviles — desc. «Usa datos móviles para aprender los géneros de tus artistas… Desactivado = solo con WiFi.» | Ajustes > Contenido | ContentSettings.kt:1181 | conmutador | rara | siempre |
| Ordenar la pantalla de inicio al azar — desc. «Reordena al azar las secciones de inicio con prioridades ponderadas» | Ajustes > Contenido | ContentSettings.kt:1202 | conmutador | rara | siempre |

### Grupo «Otros»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Modo sin conexión (hardcoded) — desc. «Muestra solo el contenido descargado…» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1231 | conmutador | ocasional | siempre |
| Evitar que el sistema cierre la app (hardcoded) — desc. «Quita la app de la optimización de batería…» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1252 | acción primaria (abre diálogo del sistema) | rara | siempre |
| Ajustes del sistema de la app (hardcoded) — desc. «Abre la ficha de la app para activar Inicio automático/Autostart…» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1260 | navegación (fuera de la app) | rara | siempre |
| Longitud de la lista Mi Top | Ajustes > Contenido | ContentSettings.kt:1268 | navegación (diálogo) | rara | siempre |
| Establecer selecciones rápidas | Ajustes > Contenido | ContentSettings.kt:1274 | navegación (diálogo) | rara | siempre |
| Marcación rápida (hardcoded) — desc. «Mostrar la marcación rápida en la pantalla de inicio» (hardcoded) | Ajustes > Contenido | ContentSettings.kt:1287 | conmutador | rara | siempre |

### Grupo «Abrir enlaces»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Abrir enlaces — desc. «Establece Aura como predeterminada para abrir enlaces de YouTube Music» | Ajustes > Contenido | ContentSettings.kt:1311 | navegación (ajustes del sistema) | rara | siempre |

### Grupo «Diagnóstico» (hardcoded)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Registros de reproducción — desc. «Consulta en tiempo real la resolución de los streams y los eventos de mitigación de bots» | Ajustes > Contenido | ContentSettings.kt:1351 | navegación (diálogo) | rara | siempre |
| Estado del servicio — desc. «Comprueba el estado de los proveedores de música» | Ajustes > Contenido | ContentSettings.kt:1357 | navegación (ruta `uptime`) | rara | siempre |
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Contenido | ContentSettings.kt:1373 | navegación | constante | siempre |

---

## 18.10 Ajustes > Traducción de letras con IA — `settings/ai`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/AiSettings.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Lista por texto — desc. «Mostrar el botón Lista AI en tu biblioteca» (grupo «Lista AI») | Ajustes > IA | AiSettings.kt:406 | conmutador | rara | siempre |
| Playlist "Recomendado para ti (IA)" (hardcoded) | Ajustes > IA | AiSettings.kt:429 | conmutador | rara | siempre; al activarlo lanza `AutoRecoPlaylistWorker.runNow` |
| Refrescar ahora (hardcoded) — desc. «Vuelve a generar las recomendaciones en segundo plano.» (hardcoded) | Ajustes > IA | AiSettings.kt:456 | acción primaria | rara | siempre |
| Proveedor — desc. muestra el proveedor activo | Ajustes > IA | AiSettings.kt:478 | navegación (diálogo de lista) | rara | siempre |
| Obtener Llaves API (icono de ayuda ⓘ junto a Proveedor) | Ajustes > IA | AiSettings.kt:484 | acción secundaria (abre diálogo) | rara | siempre |
| URL base — desc. valor o «No establecido» | Ajustes > IA | AiSettings.kt:494 | navegación (diálogo con campo de texto) | rara | solo si `aiProvider == "Custom"` |
| DeepL Clave API (hardcoded + recurso) | Ajustes > IA | AiSettings.kt:513 | navegación (diálogo con campo de texto) | rara | solo si `aiProvider == "DeepL"` |
| Formalidad (Predeterminado / Menos formal / Más formal) | Ajustes > IA | AiSettings.kt:528 | navegación (diálogo de lista) | rara | solo si `aiProvider == "DeepL"` |
| Clave API | Ajustes > IA | AiSettings.kt:546 | navegación (diálogo con campo de texto) | rara | solo si `aiProvider != "DeepL"` |
| Modelo — desc. valor o «No establecido» | Ajustes > IA | AiSettings.kt:567 | navegación (diálogo/lista o campo si es Custom) | rara | solo si `aiProvider != "DeepL"` |
| Modo de traducción (Traducción / Transcrito) | Ajustes > IA | AiSettings.kt:591 | navegación (diálogo de lista) | rara | solo si `aiProvider != "DeepL"` |
| Ayuda del modo de traducción (icono ⓘ) | Ajustes > IA | AiSettings.kt:605 | acción secundaria (abre diálogo) | rara | solo si `aiProvider != "DeepL"` |
| Idioma de destino | Ajustes > IA | AiSettings.kt:617 | navegación (diálogo de lista) | rara | siempre |
| Preguntar traducir la letra al abrir (hardcoded) | Ajustes > IA | AiSettings.kt:625 | conmutador | rara | siempre |
| Atrás (barra superior) | Ajustes > IA | AiSettings.kt:649 | navegación | constante | siempre |

Texto de ayuda mostrado en la pantalla: «Opcional: usa tu propia clave/modelo; sin clave, Aura usa su IA integrada.» (`ai_key_optional_hint`).

---

## 18.11 Ajustes > Contenido > Romanización de letras — `settings/content/romanization`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/RomanizationSettings.kt`

### Grupo «General»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Mostrar letras romanizadas como principales | Ajustes > Contenido > Romanización | RomanizationSettings.kt:85 | conmutador | rara | siempre |
| Romanizar letras japonesas | Ajustes > Contenido > Romanización | RomanizationSettings.kt:105 | conmutador | rara | siempre |
| Romanizar letras coreanas | Ajustes > Contenido > Romanización | RomanizationSettings.kt:125 | conmutador | rara | siempre |
| Romanizar letras chinas | Ajustes > Contenido > Romanización | RomanizationSettings.kt:145 | conmutador | rara | siempre |
| Romanizar letras en hindi | Ajustes > Contenido > Romanización | RomanizationSettings.kt:165 | conmutador | rara | siempre |
| Romanizar letras en panyabí | Ajustes > Contenido > Romanización | RomanizationSettings.kt:185 | conmutador | rara | siempre |

### Grupo «Cirílico»

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Romanizar letras en ruso | Ajustes > Contenido > Romanización | RomanizationSettings.kt:213 | conmutador | rara | siempre |
| Romanizar letras en ucraniano | Ajustes > Contenido > Romanización | RomanizationSettings.kt:233 | conmutador | rara | siempre |
| Romanizar letras en serbio | Ajustes > Contenido > Romanización | RomanizationSettings.kt:253 | conmutador | rara | siempre |
| Romanizar letras en búlgaro | Ajustes > Contenido > Romanización | RomanizationSettings.kt:273 | conmutador | rara | siempre |
| Romanizar letras en bielorruso | Ajustes > Contenido > Romanización | RomanizationSettings.kt:293 | conmutador | rara | siempre |
| Romanizar la letra del kirguís | Ajustes > Contenido > Romanización | RomanizationSettings.kt:313 | conmutador | rara | siempre |
| Romanizar letras en macedonio | Ajustes > Contenido > Romanización | RomanizationSettings.kt:333 | conmutador | rara | siempre |
| EXPERIMENTAL: Detectar el lenguaje línea por línea — desc. «El idioma cirílico se detectará línea por línea en lugar de toda la canción.» | Ajustes > Contenido > Romanización | RomanizationSettings.kt:353 | conmutador | rara | siempre; al activarlo abre el diálogo «¿Está seguro?» |
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Contenido > Romanización | RomanizationSettings.kt:407 | navegación | constante | siempre |

---

## 18.12 Ajustes > Privacidad — `settings/privacy`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/PrivacySettings.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Pausar historial de reproducciones (grupo «Historial de reproducciones») | Ajustes > Privacidad | PrivacySettings.kt:157 | conmutador | rara | siempre |
| Borrar historial de reproducciones | Ajustes > Privacidad | PrivacySettings.kt:177 | destructiva | rara | siempre; pide confirmación «¿Confirma que quiere borrar todo el historial de reproducciones?» |
| Pausar historial de búsquedas (grupo «Historial de búsqueda») | Ajustes > Privacidad | PrivacySettings.kt:190 | conmutador | rara | siempre |
| Borrar historial de búsquedas | Ajustes > Privacidad | PrivacySettings.kt:210 | destructiva | rara | siempre; pide confirmación |
| Desactivar captura de pantalla — desc. «Cuando esta opción está activada, las capturas de pantalla y la vista de la aplicación en Recientes estará deshabilitada.» (grupo «Otros») | Ajustes > Privacidad | PrivacySettings.kt:223 | conmutador | rara | siempre |
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Privacidad | PrivacySettings.kt:254 | navegación | constante | siempre |

---

## 18.13 Ajustes > Rendimiento — `settings/performance`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/PerformanceSettings.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Modo alto rendimiento (hardcoded) (grupo «Rendimiento», hardcoded) | Ajustes > Rendimiento | PerformanceSettings.kt:96 | conmutador | rara | siempre |
| Hardware detectado (texto variable `detected`) — desc. «Se activa solo en el primer inicio en hardware de gama realmente baja, incluidos TV boxes y pantallas de auto modestos…» (hardcoded) | Ajustes > Rendimiento | PerformanceSettings.kt:129 | informativo (`onClick = {}`, no hace nada) | rara | siempre |
| Vista dividida estilo Spotify (hardcoded) (grupo «Pantalla grande», hardcoded) | Ajustes > Rendimiento | PerformanceSettings.kt:145 | conmutador | rara | siempre |
| Panel del reproductor a la izquierda (hardcoded) | Ajustes > Rendimiento | PerformanceSettings.kt:172 | conmutador | rara | siempre |
| Mostrar el panel del reproductor (hardcoded) | Ajustes > Rendimiento | PerformanceSettings.kt:198 | conmutador | rara | siempre |
| Fondo animado a pantalla completa en horizontal — desc. «Al girar a horizontal, muestra solo el fondo animado y los controles de reproducción…» | Ajustes > Rendimiento | PerformanceSettings.kt:228 | conmutador | rara | siempre |
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Rendimiento | PerformanceSettings.kt:259 | navegación | constante | siempre |

---

## 18.14 Ajustes > Almacenamiento — `settings/storage?autoOpenExportPicker={bool}`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/StorageSettings.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Canciones descargadas — desc. tamaño ocupado (grupo «Almacenamiento») | Ajustes > Almacenamiento | StorageSettings.kt:360 | informativo | ocasional | siempre |
| Borrar todas las descargas | Ajustes > Almacenamiento | StorageSettings.kt:367 | destructiva | rara | siempre; confirma «¿Estás seguro de que quieres borrar todas las descargas?» |
| Carpeta de exportación — desc. ruta o «No establecido» | Ajustes > Almacenamiento | StorageSettings.kt:374 | acción secundaria (abre selector SAF) | rara | siempre; si no hay selector muestra «Este dispositivo no tiene selector de carpetas». Se abre solo al entrar si `autoOpenExportPicker == true` |
| Descargar automáticamente al dar me gusta — desc. «Descargar canciones automáticamente al darle a me gusta» (grupo «Descargas», hardcoded) | Ajustes > Almacenamiento | StorageSettings.kt:395 | conmutador | ocasional | siempre |
| Exportar como MP3 — desc. «Mostrar 'Exportar como MP3' en los menús» (hardcoded) | Ajustes > Almacenamiento | StorageSettings.kt:416 | conmutador | rara | siempre |
| Tamaño máximo del caché de canciones (selector de tamaño; incluye «Ilimitado» y «Deshabilitar») (grupo «Caché de canciones») | Ajustes > Almacenamiento | StorageSettings.kt:443 | navegación (menú de opciones) | rara | siempre; si el nuevo límite es menor al uso actual muestra el diálogo «¡Espera!» → «Continuar» |
| Borrar caché de canciones | Ajustes > Almacenamiento | StorageSettings.kt:500 | destructiva | rara | siempre; confirma «¿Estás seguro de que quieres borrar todas las canciones en caché?» |
| Tamaño máximo de la caché de la imagen (grupo «Caché de imágenes») | Ajustes > Almacenamiento | StorageSettings.kt:513 | navegación (menú de opciones) | rara | siempre; mismo diálogo «¡Espera!» |
| Borrar caché de imágenes | Ajustes > Almacenamiento | StorageSettings.kt:561 | destructiva | rara | siempre; confirma «¿Estás seguro de que deseas borrar todas las imágenes almacenadas en caché?» |

---

## 18.15 Ajustes > Copias de seguridad y restauración — `settings/backup_restore`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/BackupAndRestore.kt`
Pantalla con dos sub-vistas (`BackupSubScreen.MAIN` / `BackupSubScreen.IMPORT`) intercambiadas con `Crossfade`; el botón Atrás vuelve primero a MAIN.

### Sub-vista principal

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Copia de seguridad local (hardcoded) — desc. «Crea una copia de seguridad ZIP manual de tus datos» (hardcoded) | Ajustes > Copias de seguridad | BackupAndRestore.kt:220 | acción primaria (abre selector SAF) | rara | solo en `BackupSubScreen.MAIN` |
| Migración selectiva (Aura) (hardcoded) — desc. «Elige qué playlists migrar, y/o todos los artistas y todos los presets de EQ» (hardcoded) | Ajustes > Copias de seguridad | BackupAndRestore.kt:233 | acción primaria | rara | solo en MAIN |
| Importar (hardcoded) — desc. «Restaura datos desde copias de seguridad u otras fuentes» (hardcoded) | Ajustes > Copias de seguridad | BackupAndRestore.kt:245 | navegación (a la sub-vista Import) | rara | solo en MAIN |

### Sub-vista «Import» / grupo «Import Data» (hardcoded, sin traducir)

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Importar desde Spotify (hardcoded) | Ajustes > Copias de seguridad > Import | BackupAndRestore.kt:258 | navegación | rara | solo en `BackupSubScreen.IMPORT` |
| Migrar playlists (Tidal, Deezer, archivo) (hardcoded) — desc. «Inicia sesión en Tidal para traer toda tu biblioteca, o importa por enlace/archivo» (hardcoded) | Ajustes > Copias de seguridad > Import | BackupAndRestore.kt:267 | navegación | rara | solo en IMPORT |
| Sincronizar desde YouTube Music (hardcoded) — desc. «Trae tu me gusta, álbumes, artistas, suscripciones y playlists de tu cuenta» (hardcoded) | Ajustes > Copias de seguridad > Import | BackupAndRestore.kt:273 | navegación (`settings/ytm_sync`) | rara | solo en IMPORT |
| Importar desde archivo local (hardcoded) | Ajustes > Copias de seguridad > Import | BackupAndRestore.kt:279 | acción primaria (selector SAF) | rara | solo en IMPORT |
| Importar lista 'm3u' (hardcoded) | Ajustes > Copias de seguridad > Import | BackupAndRestore.kt:289 | acción primaria (selector SAF) | rara | solo en IMPORT |
| Importar lista 'csv' (hardcoded) | Ajustes > Copias de seguridad > Import | BackupAndRestore.kt:296 | acción primaria (selector SAF) | rara | solo en IMPORT |
| Importar lista de Aura Hi-Res Player (hardcoded) — desc. «.jrpl.json exported from the desktop app» (hardcoded, EN) | Ajustes > Copias de seguridad > Import | BackupAndRestore.kt:303 | acción primaria (selector SAF) | rara | solo en IMPORT |
| Importar migración selectiva (Aura) (hardcoded) — desc. «Playlists, artistas y presets exportados de Aura — aditivo, no borra nada» (hardcoded) | Ajustes > Copias de seguridad > Import | BackupAndRestore.kt:311 | acción primaria (selector SAF) | rara | solo en IMPORT |
| Atrás (tap: vuelve a MAIN o sale) / Ir al inicio (mantener) | Ajustes > Copias de seguridad | BackupAndRestore.kt:333 (`onLongClick` en 341) | navegación | constante | siempre |

---

## 18.16 Ajustes > Cuentas — `settings/accounts`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/AccountsScreen.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Cuentas | AccountsScreen.kt:130 (`onLongClick` 132) | navegación | constante | siempre |
| Fila YouTube Music (nombre de la cuenta o «Cuenta») — grupo «YouTube Music» (hardcoded) | Ajustes > Cuentas | AccountsScreen.kt:156 | navegación | ocasional | tocar la fila navega a `settings/ytm_sync` solo si `ytLoggedIn` |
| Finalizar sesión / Cuenta (botón YouTube) | Ajustes > Cuentas | AccountsScreen.kt:188 | destructiva / acción primaria | rara | «Finalizar sesión» si `ytLoggedIn`, si no «Cuenta» (login) |
| Fila Spotify — grupo «Spotify» (hardcoded) | Ajustes > Cuentas | AccountsScreen.kt:215 | navegación | ocasional | siempre |
| Conectar / Finalizar sesión (botón Spotify) | Ajustes > Cuentas | AccountsScreen.kt:243 | acción primaria / destructiva | rara | según estado de sesión |
| Fila Last.fm — grupo «Last.fm» (hardcoded) | Ajustes > Cuentas | AccountsScreen.kt:269 | navegación | ocasional | siempre |
| Conectar / Finalizar sesión (botón Last.fm) | Ajustes > Cuentas | AccountsScreen.kt:284 | acción primaria / destructiva | rara | según estado de sesión |
| Fila Qobuz — «Hi-Res con tu suscripción — activo» / «Sesión no iniciada» — grupo «Qobuz» (hardcoded) | Ajustes > Cuentas | AccountsScreen.kt:310 | navegación (`settings/qobuz`) | rara | siempre |
| Administrar / Conectar (botón Qobuz) | Ajustes > Cuentas | AccountsScreen.kt:326 | acción primaria | rara | `settings/qobuz` |
| Fila ListenBrainz — grupo «ListenBrainz» (hardcoded) | Ajustes > Cuentas | AccountsScreen.kt:350 | navegación | rara | siempre |
| Conectar / Finalizar sesión (botón ListenBrainz) | Ajustes > Cuentas | AccountsScreen.kt:371 | acción primaria / destructiva | rara | según estado |

Diálogos de cierre de sesión (título «Cerrar sesión», mensaje «¿Deseas conservar todas las bibliotecas y los demás datos?»):

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Cancelar (diálogo YouTube) | Diálogo: Cerrar sesión YouTube | AccountsScreen.kt:401 | acción secundaria | rara | solo si `showYtLogoutDialog` |
| Borrar datos (diálogo YouTube) | Diálogo: Cerrar sesión YouTube | AccountsScreen.kt:410 | destructiva | rara | ídem |
| Conservar datos (diálogo YouTube) | Diálogo: Cerrar sesión YouTube | AccountsScreen.kt:425 | acción primaria | rara | ídem |
| Cancelar (diálogo Spotify) | Diálogo: Cerrar sesión Spotify | AccountsScreen.kt:457 | acción secundaria | rara | solo si `showSpotifyLogoutDialog` |
| Finalizar sesión (diálogo Spotify) | Diálogo: Cerrar sesión Spotify | AccountsScreen.kt:466 | destructiva | rara | ídem |
| Cancelar (diálogo Last.fm) | Diálogo: Cerrar sesión Last.fm | AccountsScreen.kt:501 | acción secundaria | rara | solo si `showLastFmLogoutDialog` |
| Finalizar sesión (diálogo Last.fm) | Diálogo: Cerrar sesión Last.fm | AccountsScreen.kt:510 | destructiva | rara | ídem |

---

## 18.17 Cuenta (pantalla `account`) — `AccountSettingsScreen.kt`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/AccountSettingsScreen.kt`
Ruta `account` registrada en `NavigationBuilder.kt:138`. **No se llega desde Ajustes** (el menú raíz va a `settings/accounts`); se abre desde otros puntos de la app.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (tap) / Ir al inicio (mantener) | Cuenta | AccountSettingsScreen.kt:77 (`onLongClick` 79) | navegación | constante | siempre |
| Fila de cuenta (grupo «Ajustes») | Cuenta | AccountSettingsScreen.kt:104 | informativo / navegación | ocasional | siempre |
| Finalizar sesión / Cuenta | Cuenta | AccountSettingsScreen.kt:127 | destructiva / acción primaria | rara | según estado de sesión |
| Más contenido (grupo «Reproductor y contenido») | Cuenta | AccountSettingsScreen.kt:158 | conmutador | rara | solo si hay sesión iniciada |
| Sincronización automática con la cuenta | Cuenta | AccountSettingsScreen.kt:185 | conmutador | rara | solo si hay sesión iniciada |
| Espejar favoritos desde mi cuenta (hardcoded) — desc. «Deja tus favoritos del app idénticos a los de tu cuenta de YouTube. Puede quitar los que ya no estén en tu cuenta.» (hardcoded) | Cuenta | AccountSettingsScreen.kt:205 | conmutador | rara | solo si hay sesión iniciada |
| Cancelar / Borrar datos / Conservar datos (diálogo Cerrar sesión) | Diálogo: Cerrar sesión | AccountSettingsScreen.kt:228 / 238 / 254 | acción secundaria / destructiva / acción primaria | rara | solo con el diálogo abierto |
| Cancelar / Finalizar sesión (segundo diálogo) | Diálogo: Cerrar sesión (2) | AccountSettingsScreen.kt:286 / 295 | acción secundaria / destructiva | rara | solo con el diálogo abierto |

---

## 18.18 Ajustes > Scrobbling — `settings/lastfm`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/LastFMSettingsScreen.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Fila de cuenta Last.fm — nombre de usuario o «Sesión no iniciada» (grupo «Cuenta») | Ajustes > Scrobbling | LastFMSettingsScreen.kt:336 | informativo | ocasional | siempre |
| Finalizar sesión | Ajustes > Scrobbling | LastFMSettingsScreen.kt:345 | destructiva | rara | solo si hay sesión |
| Acceder (abre el diálogo de inicio de sesión) | Ajustes > Scrobbling | LastFMSettingsScreen.kt:353 | acción primaria | rara | solo si NO hay sesión |
| Nombre de usuario (campo, diálogo de acceso) | Diálogo: Acceder a Last.fm | LastFMSettingsScreen.kt:168 | acción secundaria | rara | solo con el diálogo abierto |
| Contraseña (campo, diálogo de acceso) | Diálogo: Acceder a Last.fm | LastFMSettingsScreen.kt:178 | acción secundaria | rara | ídem |
| Botones del diálogo de acceso («Iniciando sesión…» / Cancelar) | Diálogo: Acceder a Last.fm | LastFMSettingsScreen.kt:219, 282 | acción primaria / secundaria | rara | ídem |
| Habilitar scrobbling (grupo «Opciones») | Ajustes > Scrobbling | LastFMSettingsScreen.kt:368 | conmutador | rara | siempre |
| Enviar Reproduciendo ahora | Ajustes > Scrobbling | LastFMSettingsScreen.kt:389 | conmutador | rara | siempre |
| Mejorar recomendaciones con Last.fm (hardcoded) | Ajustes > Scrobbling | LastFMSettingsScreen.kt:410 | conmutador | rara | siempre |
| Scrobble a canciones más largas que — desc. duración formateada (grupo «Configuración de scrobbling») | Ajustes > Scrobbling | LastFMSettingsScreen.kt:656 | navegación (diálogo con slider) | rara | siempre |
| Porcentaje de retraso de Scrobble — desc. «%d%%» | Ajustes > Scrobbling | LastFMSettingsScreen.kt:662 | navegación (diálogo con slider) | rara | siempre |
| Minutos de retraso de Scrobble — desc. duración formateada | Ajustes > Scrobbling | LastFMSettingsScreen.kt:668 | navegación (diálogo con slider) | rara | siempre |
| Botones de los tres diálogos de slider (Restablecer / Cancelar / OK) | Diálogos de scrobbling | LastFMSettingsScreen.kt:467, 477, 485 · 532, 542, 550 · 597, 607, 615 | acción secundaria / primaria | rara | solo con el diálogo abierto |
| ListenBrainz — desc. «Envía tus reproducciones a ListenBrainz. Requiere un token de usuario…» (grupo «ListenBrainz») | Ajustes > Scrobbling | LastFMSettingsScreen.kt:683 | conmutador | rara | siempre |
| Configurar token de ListenBrainz / «Token de ListenBrainz configurado» | Ajustes > Scrobbling | LastFMSettingsScreen.kt:713 | navegación (diálogo con campo de texto) | rara | siempre |
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Scrobbling | LastFMSettingsScreen.kt:735 (`onLongClick` 737) | navegación | constante | siempre |

`ListenBrainzManager.kt` no declara pantalla propia: aporta la lógica del token que consume esta pantalla.

---

## 18.19 Ajustes > Cuentas > Qobuz — `settings/qobuz`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/QobuzSettingsScreen.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:55 (`onLongClick` 57) | navegación | constante | siempre |
| Texto introductorio «Reproduce en calidad Qobuz (FLAC Hi-Res) con TU propia suscripción…» | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:80 | informativo | — | siempre |
| «Conectado a Qobuz» + plan («Plan: %s», «Hi-Res 24-bit disponible» / «Cuenta estándar/sin pérdida») | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:86 | informativo | ocasional | solo si `state.linked` |
| Cerrar sesión | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:135 | destructiva | rara | solo si `state.linked` |
| Usar mi suscripción Qobuz — desc. «Cuando está activo, las pistas sin pérdida se reproducen desde tu Qobuz (FLAC Hi-Res) en vez del proxy.» | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:177 (Switch 186) | conmutador | rara | solo si `state.linked` |
| Token (user_auth_token) (campo de texto) — hint «Pega tu user_auth_token de Qobuz (recomendado, sin contraseña).» | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:221 | acción secundaria | rara | solo si NO hay sesión |
| Usar token (botón) | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:229 | acción primaria | rara | solo si NO hay sesión |
| Correo electrónico (campo) — sección «O inicia sesión con tu correo y contraseña» | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:253 | acción secundaria | rara | solo si NO hay sesión |
| Contraseña (campo) | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:262 | acción secundaria | rara | solo si NO hay sesión |
| Iniciar sesión (botón) | Ajustes > Cuentas > Qobuz | QobuzSettingsScreen.kt:272 | acción primaria | rara | solo si NO hay sesión |
| OK [SIN TRADUCIR] (diálogo de aviso, p. ej. «No activamos "Usar mi suscripción Qobuz": tu plan no incluye 24-bit…») | Diálogo: aviso Qobuz | QobuzSettingsScreen.kt:304 | acción primaria | rara | solo con el diálogo abierto |

---

## 18.20 Sincronizar desde YouTube Music — `settings/ytm_sync?onboarding={bool}`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/YtmSyncScreen.kt`
Se llega desde Ajustes > Cuentas (fila YouTube), Ajustes > Copias de seguridad > Import, el onboarding y `MainActivity.kt:1115`. **No está en el menú raíz de Ajustes.**

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (tap) / Ir al inicio (mantener) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:80 | navegación | constante | siempre |
| Comenzar a usar Aura (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:93 | acción primaria | rara | solo si `onboarding == true` |
| Cuenta (botón de inicio de sesión) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:122 | acción primaria | rara | solo si `!isLoggedIn` |
| Sincronizar todo (hardcoded) — desc. «Me gusta, álbumes, artistas, suscripciones, playlists y biblioteca» (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:148 | acción primaria | rara | siempre |
| Me gusta (canciones) (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:159 | acción primaria | rara | siempre |
| Álbumes favoritos (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:164 | acción primaria | rara | siempre |
| Artistas y suscripciones (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:169 | acción primaria | rara | siempre |
| Playlists guardadas (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:174 | acción primaria | rara | siempre |
| Biblioteca (canciones) (hardcoded) — desc. «Incluye tus me gusta (favoritos)» (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:179 | acción primaria | rara | siempre |
| Subidas (canciones y álbumes) (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:192 | acción primaria | rara | siempre |
| Desactivada (frecuencia de auto-sincronización) (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:232 | acción primaria (selección) | rara | siempre |
| Cada día (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:238 | acción primaria (selección) | rara | siempre |
| Cada semana (hardcoded) | Sincronizar desde YouTube Music | YtmSyncScreen.kt:244 | acción primaria (selección) | rara | siempre |
| Sincronizar mi biblioteca con mi cuenta — desc. «Tus playlists, artistas seguidos, me gusta y álbumes se guardan en tu cuenta de YouTube Music…» (sección «Copia de seguridad en YouTube Music») | Sincronizar desde YouTube Music | YtmSyncScreen.kt:318 | conmutador | rara | siempre |
| Sincronizar toda mi biblioteca ahora — desc. «Sube todo lo que falte. Corre en segundo plano, por tandas, y continúa donde se quedó.» | Sincronizar desde YouTube Music | YtmSyncScreen.kt:335 | acción primaria | rara | siempre; muestra «Activa "Sincronizar mi biblioteca…"» si el conmutador está apagado y «Primero inicia sesión en YouTube Music» sin sesión |

---

## 18.21 Ajustes > Reproductor > Descifrado de reproducción — `settings/youtube_decryption`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/YoutubeDecryptionSettings.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Forzar actualización del descifrado — desc. «Obtiene ahora la configuración más reciente del reproductor» | Ajustes > Reproductor > Descifrado | YoutubeDecryptionSettings.kt:138 | acción primaria | rara | siempre; en enfriamiento muestra «Espera %s antes de volver a actualizar» |
| Configuraciones activas — desc. «%d configuración(es) autorreparable(s) aplicada(s)» | Ajustes > Reproductor > Descifrado | YoutubeDecryptionSettings.kt:206 | informativo | rara | siempre |
| Última actualización — desc. «Cargado (auto-reparación activa)» / «Aún nunca (se actualiza al abrir la app)» | Ajustes > Reproductor > Descifrado | YoutubeDecryptionSettings.kt:217 | informativo | rara | siempre |
| Atrás | Ajustes > Reproductor > Descifrado | YoutubeDecryptionSettings.kt:255 | navegación | constante | siempre |

Texto explicativo de la pantalla: «Aura mantiene la reproducción por sí sola: actualiza el descifrado al abrir la app y se repara automáticamente cuando el proveedor de streaming cambia su reproductor…» (`youtube_decryption_info`).

---

## 18.22 Ajustes > Aura Hi-Res Update — `settings/update`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/UpdateSettings.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| System update [SIN TRADUCIR] — desc. «Version %s» / «Up to date» / «Update» [SIN TRADUCIR] | Ajustes > Update | UpdateSettings.kt:94 | navegación (ruta `update`) | ocasional | siempre |
| Versión y variante — «{arch} - {variant} · Ver cambios de esta versión» (hardcoded) | Ajustes > Update | UpdateSettings.kt:109 | navegación (`settings/changelog`) | ocasional | siempre |
| Automatic update check [SIN TRADUCIR] — desc. «Automatically check for updates when opening the update screen» [SIN TRADUCIR] | Ajustes > Update | UpdateSettings.kt:122 | conmutador | rara | siempre |
| Habilitar notificaciones de actualización — desc. «Show a notification when a new update is found» [SIN TRADUCIR] | Ajustes > Update | UpdateSettings.kt:156 | conmutador | rara | siempre (pide permiso POST_NOTIFICATIONS en Android 13+) |
| Borrar actualizaciones descargadas — desc. «Elimina los archivos APK descargados para liberar espacio» | Ajustes > Update | UpdateSettings.kt:183 | destructiva | rara | acción real solo si `apkCount > 0` |
| Icono de información (junto a «Borrar actualizaciones descargadas») | Ajustes > Update | UpdateSettings.kt:200 | acción secundaria (abre diálogo) | rara | siempre |
| Atrás | Ajustes > Update | UpdateSettings.kt:239 | navegación | constante | siempre |

---

## 18.23 Ajustes > Acerca de — `settings/about`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/AboutScreen.kt`
Pantalla mayoritariamente informativa: tarjetas de funciones sin interacción salvo la fila legal.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Acerca de | AboutScreen.kt:163 (`onLongClick` 165) | navegación | constante | siempre |
| Reproducción y calidad de audio (hardcoded) — tarjeta informativa | Ajustes > Acerca de | AboutScreen.kt:198 | informativo | rara | siempre |
| Ecualizador y sonido (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:203 | informativo | rara | siempre |
| Descubrimiento y cola inteligente (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:208 | informativo | rara | siempre |
| Letras (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:213 | informativo | rara | siempre |
| Biblioteca y listas (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:218 | informativo | rara | siempre |
| Importar y migrar (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:223 | informativo | rara | siempre |
| Escuchar juntos (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:228 | informativo | rara | siempre |
| En el coche, en la tele y en tu pantalla de inicio (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:233 | informativo | rara | siempre |
| Reconocer y buscar (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:238 | informativo | rara | siempre |
| Apariencia y personalización (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:243 | informativo | rara | siempre |
| Privacidad, control y estabilidad (hardcoded) | Ajustes > Acerca de | AboutScreen.kt:248 | informativo | rara | siempre |
| Información legal (hardcoded) — tarjeta contenedora | Ajustes > Acerca de | AboutScreen.kt:253 | informativo | rara | siempre |
| Términos y condiciones / Información legal — desc. «Los términos que aceptaste al empezar a usar la app…» | Ajustes > Acerca de | AboutScreen.kt:254 (fila en 273) | navegación (`settings/terms`) | rara | siempre |
| Motor de audio Superpowered (hardcoded) — «El ecualizador y el Volumen Seguro usan el SDK de audio de Superpowered…» | Ajustes > Acerca de | AboutScreen.kt:326 | informativo | rara | siempre |

### 23.1 Términos y condiciones — `settings/terms`

Archivo: `app/src/main/kotlin/com/music/echo/legal/TermsScreens.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Acerca de > Términos | TermsScreens.kt:258 | navegación | constante | siempre |
| «Aceptados el %s (términos v%d)» (pie de página) | Ajustes > Acerca de > Términos | TermsScreens.kt:291 | informativo | rara | solo si `acceptedAt > 0` |

---

## 18.24 Ajustes > Registros — `settings/logs`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/LogsScreen.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Registros | LogsScreen.kt:87 (`onLongClick` 89) | navegación | constante | siempre |
| Copiar (registro al portapapeles) | Ajustes > Registros | LogsScreen.kt:95 | acción primaria | rara | siempre |
| Compartir (registro) | Ajustes > Registros | LogsScreen.kt:98 | acción secundaria | rara | siempre |
| Eliminar (borra los registros) | Ajustes > Registros | LogsScreen.kt:101 | destructiva | rara | siempre |
| Registro de la app (pestaña) | Ajustes > Registros | LogsScreen.kt:133 | acción primaria (selección) | rara | siempre |
| Último fallo (pestaña) | Ajustes > Registros | LogsScreen.kt:138 | acción primaria (selección) | rara | siempre |
| Cierres del sistema (pestaña) | Ajustes > Registros | LogsScreen.kt:143 | acción primaria (selección) | rara | siempre |
| «Aún no hay registros» | Ajustes > Registros | LogsScreen.kt (cuerpo) | informativo | rara | solo si la pestaña está vacía |

---

## 18.25 Ajustes > Contenido > Estado del servicio — `uptime`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/UptimeScreen.kt`

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Atrás | Estado del servicio | UptimeScreen.kt:99 | navegación | constante | siempre |
| Proveedores de música (sección con estado En línea / Sin conexión / Comprobando…) | Estado del servicio | UptimeScreen.kt:118 | informativo | rara | siempre |
| Proveedores de Canvas (sección) | Estado del servicio | UptimeScreen.kt:161 | informativo | rara | siempre |

Pantalla puramente informativa: no tiene conmutadores ni acciones más allá de Atrás.

---

## 18.26 Ajustes > Escuchar juntos (ajustes) — `settings/integrations/listen_together`

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/integrations/ListenTogetherSettings.kt`
**No se llega desde el menú raíz de Ajustes**: la fila «Escuchar juntos» de Ajustes va a la pantalla principal `listen_together`. El único acceso es `ui/screens/ListenTogetherScreen.kt:424`.

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Usuarios bloqueados — desc. «%d usuario(s) bloqueado(s)» / «No hay usuarios bloqueados» (grupo «Ajustes») | Ajustes > Escuchar juntos | ListenTogetherSettings.kt:347 | navegación (diálogo) | rara | siempre |
| URL del servidor — desc. valor o «No establecido» | Ajustes > Escuchar juntos | ListenTogetherSettings.kt:362 | navegación (diálogo «Elegir servidor») | rara | siempre |
| Nombre de usuario | Ajustes > Escuchar juntos | ListenTogetherSettings.kt:376 | navegación (diálogo con campo) | rara | bloqueado con «No se puede editar el nombre de usuario mientras se está en una sala» |
| Aprobar automáticamente solicitudes de conexión — desc. «…en lugar de revisarlas manualmente» | Ajustes > Escuchar juntos | ListenTogetherSettings.kt:388 (Switch 393) | conmutador | rara | siempre |
| Sincronizar volumen del anfitrión — desc. «Los invitados siguen el nivel de volumen del anfitrión» | Ajustes > Escuchar juntos | ListenTogetherSettings.kt:414 (Switch 419) | conmutador | rara | siempre |
| Resincronización inteligente de red — desc. «Ponte al día automáticamente con el anfitrión tras una caída de la conexión» | Ajustes > Escuchar juntos | ListenTogetherSettings.kt:437 (Switch 442) | conmutador | rara | siempre |
| Ver registros — desc. «Depurar conexión y mensajes» | Ajustes > Escuchar juntos | ListenTogetherSettings.kt:460 | navegación (diálogo) | rara | siempre |
| Atrás (tap) / Ir al inicio (mantener) | Ajustes > Escuchar juntos | ListenTogetherSettings.kt:476 (`onLongClick` 478) | navegación | constante | siempre |

Controles dentro de los diálogos de esta pantalla:

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Campo «Nombre de usuario» | Diálogo: Nombre de usuario | ListenTogetherSettings.kt:183 | acción secundaria | rara | diálogo abierto |
| Borrar texto (X) | Diálogo: Nombre de usuario | ListenTogetherSettings.kt:192 | acción secundaria | rara | diálogo abierto |
| Restablecer / Guardar | Diálogo: Nombre de usuario | ListenTogetherSettings.kt:174 / 178 | acción secundaria / primaria | rara | diálogo abierto |
| Campo «Código de la sala» + Cancelar/Crear | Diálogo: Crear sala | ListenTogetherSettings.kt:240 · 211 / 215 | acción secundaria / primaria | rara | diálogo abierto |
| Campos de unión + Cancelar/Unirse | Diálogo: Unirse a la sala | ListenTogetherSettings.kt:287, 297 · 262 / 266 | acción secundaria / primaria | rara | diálogo abierto |
| Copiar / Limpiar / OK (registros) | Diálogo: Registros de conexión | ListenTogetherSettings.kt:510 / 531 / 535 | acción primaria / destructiva / secundaria | rara | diálogo abierto |
| Servidor personalizado (campo + botón) | Diálogo: Elegir servidor | ListenTogetherSettings.kt:654 / 664 | acción secundaria / primaria | rara | diálogo abierto |
| Desbloquear (por usuario) / Cerrar | Diálogo: Usuarios bloqueados | ListenTogetherSettings.kt:802 · 750 | destructiva / secundaria | rara | diálogo abierto |

---

## 18.27 Diálogo de Ajustes rápido (`SettingDialoge`)

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/SettingDialoge.kt`
Se abre desde el icono de la barra superior en `MainActivity.kt:1309` (no desde la pantalla de Ajustes).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Cerrar (X) | Diálogo: Ajustes rápidos | SettingDialoge.kt:99 | acción secundaria | ocasional | siempre |
| Iniciar sesión (hardcoded) — grupo «Cuenta» (hardcoded) | Diálogo: Ajustes rápidos | SettingDialoge.kt:119 | navegación (`login`) | rara | siempre |
| Usar la cuenta para explorar (hardcoded) — grupo «Preferences» (hardcoded, sin traducir) | Diálogo: Ajustes rápidos | SettingDialoge.kt:133 | conmutador | rara | siempre |
| Sincronización con YouTube Music (hardcoded) | Diálogo: Ajustes rápidos | SettingDialoge.kt:152 | conmutador | rara | siempre |
| Ajustes (hardcoded) — grupo «App» (hardcoded) | Diálogo: Ajustes rápidos | SettingDialoge.kt:172 | navegación (`settings`) | ocasional | siempre |
| Acerca de (hardcoded) — muestra `BuildConfig.VERSION_NAME` a la derecha | Diálogo: Ajustes rápidos | SettingDialoge.kt:177 | navegación (`settings/about`) | rara | siempre |

---

## 18.28 Diálogo «Que Aura no se corte en segundo plano» (`BackgroundReliabilityDialog`)

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/BackgroundReliabilityDialog.kt`
Se muestra desde `MainActivity.kt:1772` cuando `showBatteryReliabilityDialog` es true (fabricantes con matanza de apps en segundo plano).

| Nombre (ES) | Dónde | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| «Que Aura no se corte en segundo plano» (título, hardcoded) | Diálogo: fiabilidad en segundo plano | BackgroundReliabilityDialog.kt:35 | informativo | rara | diálogo visible |
| 1) Permitir batería sin restricción (hardcoded) | Diálogo: fiabilidad en segundo plano | BackgroundReliabilityDialog.kt:52 | acción primaria (abre ajuste del sistema) | rara | diálogo visible |
| 2) Activar "Inicio automático" (hardcoded) | Diálogo: fiabilidad en segundo plano | BackgroundReliabilityDialog.kt:44 | acción primaria (abre ficha de la app) | rara | diálogo visible |
| Ahora no (hardcoded) | Diálogo: fiabilidad en segundo plano | BackgroundReliabilityDialog.kt:57 | acción secundaria | rara | diálogo visible |

---

## 18.29 `RingtoneViewModel` (sin pantalla propia)

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/RingtoneViewModel.kt`
Vive en `ui/screens/settings/` pero **no dibuja ninguna pantalla de ajustes**. Se expone vía `LocalRingtoneViewModel` (`MainActivity.kt:1197`) y se dispara desde el menú del reproductor (`ui/menu/OldPlayerMenu.kt:113`).

| Acción (ES) | Dónde se dispara | archivo:línea | Tipo | Frecuencia | Condicional |
|---|---|---|---|---|---|
| Abrir el recortador de tono | Menú de canción del reproductor | RingtoneViewModel.kt:46 | acción primaria | rara | desde `OldPlayerMenu` |
| Cerrar el recortador | Diálogo del recortador | RingtoneViewModel.kt:58 | acción secundaria | rara | recortador abierto |
| Establecer como tono de llamada | Diálogo del recortador | RingtoneViewModel.kt:66 | acción primaria | rara | requiere permiso WRITE_SETTINGS |
| Cerrar el progreso | Diálogo de progreso | RingtoneViewModel.kt:115 | acción secundaria | rara | progreso visible |
| Abrir los ajustes de tono del sistema | Diálogo del recortador | RingtoneViewModel.kt:119 | navegación (fuera de la app) | rara | siempre |
| Pedir permiso de ajustes (WRITE_SETTINGS) | Diálogo del recortador | RingtoneViewModel.kt:128 | acción primaria | rara | solo si `!hasSettingsPermission` |

---

## 18.30 Primitivas de componente que el rediseño debe reimplementar

### 30.1 `ui/component/Material3SettingsGroup.kt` — el contenedor real de TODOS los ajustes

| Elemento | archivo:línea | Interacción que ofrece | Notas |
|---|---|---|---|
| `Material3SettingsGroup(title, items)` | Material3SettingsGroup.kt:43 | agrupa filas con esquinas conectadas | `title` opcional |
| `Material3SettingsItemRow` | Material3SettingsGroup.kt:98 | tap en toda la fila | dibuja icono + título + descripción + `trailingContent` |
| `data class Material3SettingsItem` | Material3SettingsGroup.kt:232 | — | campos: `icon`, `title`, `description`, `trailingContent` (236), `onClick`, `enabled` (241) |

**No expone `onLongClick`.** Ninguna fila de ajustes tiene pulsación larga; la única pulsación larga de todo el árbol es la flecha Atrás.

### 30.2 `ui/component/EnumDialog.kt`

| Elemento | archivo:línea | Interacción | Notas |
|---|---|---|---|
| `EnumDialog(title, values, valueText, onValueSelected, onDismiss)` | EnumDialog.kt:20 | tap en una fila = selección | fila con radio (`onClick = null` en el radio, EnumDialog.kt:46; el tap va en la fila) |

### 30.3 `ui/component/ColorPicker.kt`

| Nombre (ES) | archivo:línea | Tipo | Condicional |
|---|---|---|---|
| Panel saturación/valor (tap + arrastre) | ColorPicker.kt:322 (tap 335, arrastre 343) | acción primaria (gesto) | siempre |
| Barra de tono (tap + arrastre) | ColorPicker.kt:369 (tap 382, arrastre 387) | acción primaria (gesto) | siempre |
| Campo hexadecimal (con prefijo «#») | ColorPicker.kt:251 | acción secundaria | siempre |
| Muestras predefinidas (`PresetSwatch`, dos filas) | ColorPicker.kt:280 y 289 (definición 297, tap 317) | acción primaria | siempre |
| Restablecer | ColorPicker.kt:189 | acción secundaria | solo si `defaultColor != null || onReset != null` |
| Cancelar | ColorPicker.kt:202 | acción secundaria | siempre |
| OK [SIN TRADUCIR] | ColorPicker.kt:205 | acción primaria | siempre |

### 30.4 `ui/component/ThumbnailCornerRadiusSelector.kt`

| Nombre (ES) | archivo:línea | Tipo | Condicional |
|---|---|---|---|
| Cuadrícula de chips de radio predefinido (`FilterChip`) | ThumbnailCornerRadiusSelector.kt:149 (cuadrícula 132/336) | acción primaria (selección) | siempre |
| Slider de radio personalizado | ThumbnailCornerRadiusSelector.kt:226 | acción secundaria (slider) | siempre |
| Restablecer | ThumbnailCornerRadiusSelector.kt:277 | acción secundaria | siempre |
| Cancelar | ThumbnailCornerRadiusSelector.kt:293 | acción secundaria | siempre |
| OK [SIN TRADUCIR] | ThumbnailCornerRadiusSelector.kt:303 | acción primaria | siempre |

Único invocador: `AppearanceSettings.kt:1507`.

### 30.5 `ui/component/PlaybackLogsDialog.kt`

| Nombre (ES) | archivo:línea | Tipo | Condicional |
|---|---|---|---|
| Copiar (registros al portapapeles) | PlaybackLogsDialog.kt:61 | acción primaria | siempre |
| Limpiar | PlaybackLogsDialog.kt:82 | destructiva | siempre |
| OK [SIN TRADUCIR] | PlaybackLogsDialog.kt:86 | acción secundaria | siempre |

Único invocador: `ContentSettings.kt:607`.

### 30.6 `ui/component/IntegrationCard.kt`

| Elemento | archivo:línea | Interacción | Notas |
|---|---|---|---|
| `IntegrationCard(...)` | IntegrationCard.kt:36 | contenedor de filas | único invocador: `ListenTogetherSettings.kt:342` |
| `IntegrationCardItemRow` | IntegrationCard.kt:86 | tap en la fila | `trailingContent` en 169 |
| `data class IntegrationCardItem` | IntegrationCard.kt:177 | — | `trailingContent` en 181 |

### 30.7 `ui/component/UpdaterComponents.kt`

| Elemento | archivo:línea | Interacción | Invocador |
|---|---|---|---|
| `AnimatedActionButton(text, enabled…)` | UpdaterComponents.kt:52 | tap | `echomusic/updater/echomusicupdater.kt:295, 308, 315` |
| `ExpressiveIconButton(enabled…)` | UpdaterComponents.kt:108 | tap | `echomusicupdater.kt:264` |
| `ErrorSnackbar(hostState)` | UpdaterComponents.kt:145 | informativo | `echomusicupdater.kt:381` |
| `leadingItemShape` / `middleItemShape` / `endItemShape` / `detachedItemShape` | UpdaterComponents.kt:168 / 175 / 177 / 184 | — (forma) | usados por el actualizador |
| `String.parseMarkdown()` | UpdaterComponents.kt:187 | — | render del changelog |
| `ChangelogItem(text, shape)` | UpdaterComponents.kt:256 | informativo | `echomusicupdater.kt:527` |

### 30.8 `ui/component/Preference.kt` — **NO SE USA EN NINGÚN SITIO** (ver sección MUERTO)

| Composable | archivo:línea | Soporta |
|---|---|---|
| `PreferenceEntry` | Preference.kt:46 | `trailingContent` (52), `isEnabled` (54), atenuación al 50 % si deshabilitado (64) |
| `ListPreference` | Preference.kt:105 | `isEnabled` (113) |
| (entrada de lista con acción) | Preference.kt:166 | `isEnabled` |
| `SwitchPreference` | Preference.kt:181 | `isEnabled` (188), Switch en `trailingContent` (195) |
| `EditTextPreference` | Preference.kt:217 | `isEnabled` (225) |
| `SliderPreference` | Preference.kt:257 | `isEnabled` (263) |
| `PreferenceGroupTitle` | Preference.kt:335 | — |

---

## 18.31 `SearchableSettings.kt` — índice de búsqueda de Ajustes

Archivo: `app/src/main/kotlin/com/music/echo/ui/screens/settings/SearchableSettings.kt`
`getAllSearchableSettings()` devuelve **400 entradas** `Triple(título, sección, ruta)`. Cada una es una fila navegable cuando el usuario escribe en el buscador de Ajustes (`SettingsScreen.kt:322-331`).

Reparto por ruta de destino (todas existen en `NavigationBuilder.kt`, ninguna rota):

| Ruta | Entradas |
|---|---|
| `settings/appearance` | 100 |
| `settings/content` | 53 |
| `settings/player` | 49 |
| `settings/integrations/listen_together` | 31 |
| `settings/ai` | 30 |
| `settings/appearance/theme` | 26 |
| `settings/storage` | 21 |
| `settings/content/romanization` | 20 |
| `settings/update` | 12 |
| `settings/privacy` | 12 |
| `settings/lastfm` | 11 |
| `settings/accounts` | 9 |
| `settings/logs` | 6 |
| `settings/performance` | 5 |
| `settings/youtube_decryption` | 4 |
| `settings/sound` | 4 |
| `settings/backup_restore` | 2 |
| `settings/spotify_import` | 1 |
| `settings/sound/autoeq` | 1 |
| `settings/equalizer` | 1 |
| `settings/appearance/liquidglass` | 1 |
| `settings/about` | 1 |

Nota: el resultado navega a la **pantalla padre**, no a la fila concreta (el «scroll-to-highlight» de upstream no se portó — comentado en `SearchableSettings.kt:14-16`). 31 entradas apuntan a `settings/integrations/listen_together`, pantalla que **no tiene acceso desde el menú de Ajustes** (ver MUERTO).

---


---

# 19. RECUENTO TOTAL

## 19.1 La cifra

| Métrica | Valor |
|---|---|
| **Filas inventariadas** (controles + indicadores) | **1.718** |
| De ellas, **interactivas** (se pulsan, se arrastran o se conmutan) | **≈ 1.376** |
| De ellas, **informativas** (no se pulsan, pero si desaparecen el usuario pierde información) | **342** |
| **Pantallas y superficies distintas** | **≈ 95** (70 rutas registradas + hojas, diálogos y menús sin ruta) |
| **Rutas de navegación registradas** | **70** (3 de ellas muertas) |
| **Menús contextuales distintos** | **17** |
| **Diálogos y hojas modales distintos** | **≈ 70** |
| **Gestos documentados** | **≈ 55** |

Desglose por tipo de las 1.718 filas:

| Tipo | Cuántos | Qué significa para el rediseño |
|---|---|---|
| Acción primaria | 382 | Son el motivo de existir de su pantalla. Ninguno puede quedar a más de un toque de distancia |
| Navegación | 373 | Llevan a otra pantalla, menú o diálogo. Definen el mapa de la app |
| Conmutador | 319 | Encienden/apagan o ciclan. Casi todos viven en Ajustes |
| Informativo | 342 | Estado, insignias, contadores, mensajes de error y de estado vacío |
| Acción secundaria | 212 | Útiles pero no centrales |
| **Destructiva** | **85** | Borran, quitan o interrumpen. **Merecen confirmación y no deberían quedar a un solo toque** |

Desglose por frecuencia de uso (estimación de producto, **no hay telemetría en el repositorio**):

| Frecuencia | Cuántos | Consecuencia |
|---|---|---|
| Constante (varias veces por sesión) | 232 | Tienen que estar en la superficie, sin menús de por medio |
| Diaria | 292 | Superficie o un solo toque |
| Ocasional | 594 | Aceptan vivir dentro de un menú |
| Rara | 573 | Ajustes, migraciones, licencia, onboarding: pueden estar enterrados |

## 19.2 Reparto por zona de la app

| Zona | Filas | Nota |
|---|---|---|
| **Ajustes** (31 pantallas) | ≈ 600 | Es más de un tercio del total. Casi todo son conmutadores |
| **Biblioteca, Artista, Álbum y Listas** | ≈ 647 | Ordenaciones, filtros, vistas, multiselección y menús ⋯ |
| **Reproductor + Cola + Letras + sus menús** | ≈ 260 | Es la zona más densa por metro cuadrado, y la que se rediseña |
| **Inicio, Búsqueda y Descubrimiento** | ≈ 240 | Muy fragmentada en secciones condicionales |
| **Esqueleto, onboarding, legal, licencia y migración** | ≈ 170 | Casi todo se ve una sola vez |

## 19.3 Sanidad de la cifra

Como comprobación independiente, en `app/src/main/kotlin/com/music/echo/ui/` hay:

- **1.050** apariciones de `onClick = {`
- **202** de `.clickable`
- **106** de `combinedClickable`
- **128** de `onLongClick`
- **≈ 246** filas de preferencia (`Material3SettingsItem`) solo en `ui/screens/settings/`

Las 1.718 filas de este inventario están en el orden de magnitud correcto: cubren esos puntos de
interacción más los que viven fuera de `ui/` (licencia, legal, actualizador, selector de audio, widgets)
y agrupan en una sola fila los controles que se repiten por composición (por ejemplo, un mismo botón ⋯
que aparece en 24 pantallas se cuenta una vez, en su menú).

---

# 20. CONTROLES CONDICIONALES — los más fáciles de perder

Un mockup se dibuja en **un** estado: sesión iniciada, con conexión, sin sala, en un móvil en vertical,
con una canción de audio normal sonando. Todo lo de esta lista **no se ve en ese estado**, o solo se ve
en él. Son los candidatos número uno a desaparecer sin que nadie lo note hasta que se queja un usuario.

## 20.1 Por ajuste del usuario (el rediseño debe soportar AMBOS estados)

| Condición | Qué cambia | Dónde |
|---|---|---|
| **`useNewPlayerDesign`** (ON por defecto) | El reproductor tiene **dos maquetaciones completas** de transporte, y la barra de la Cola cambia de 7 botones a 4. En el diseño ANTIGUO aparecen: el deslizador de volumen del sistema, el nombre del dispositivo Bluetooth, el chip del temporizador y **el ÚNICO acceso al selector de salida de audio de toda la app** | `ui/player/Player.kt:2513` vs `:2650` · `ui/player/Queue.kt:366-464` vs `:513-638` |
| **`UseNewMiniPlayerDesign`** (ON por defecto) | El mini reproductor tiene dos maquetaciones (anillo de progreso vs barra inferior; con y sin botón «anterior») | `ui/player/MiniPlayer.kt:440` vs `:773` |
| **Estilo de la barra de progreso** (4 valores) | DEFAULT / squiggly / wavy M3 / SLIM — cuatro implementaciones distintas del mismo control | `ui/player/Player.kt:2140`, `:2178`, `:2207`, `:2253` |
| **Modo Rendimiento** | Sustituye el carrusel héroe del Inicio y las tarjetas de mix diario por versiones compactas **sin pulsación larga (sin menú)**, y oculta secciones enteras | `ui/screens/HomeScreen.kt:1457`, `:1687`, `:1936` |
| **«Solo mi gusto» en el Inicio** (ON por defecto) | **Oculta** las secciones crudas de YouTube, «De la comunidad» y «Estado de ánimo y géneros» | `ui/screens/HomeScreen.kt:1611`, `:2265`, `:2400` |
| **Marcación rápida** (OFF por defecto) | Toda la sección de marcación rápida del Inicio, con su botón «aleatorio» y su paginación | `ui/screens/HomeScreen.kt:1247-1415` |
| **«Deslizar canción»** (OFF por defecto) | Los dos gestos de deslizar filas («Reproducir a continuación» / «Añadir a la cola») en TODAS las listas | `ui/component/Items.kt:1732-1742` |
| **`ShowCommentButton`** (OFF por defecto) | El botón de comentarios de la barra de la Cola y, con él, las 437 líneas de la hoja de comentarios | `ui/player/Queue.kt:413-426` |
| **`ImmersiveCanvasOnRotate`** (OFF por defecto) | Los 3 controles del canvas inmersivo al girar el móvil | `ui/player/Player.kt:3121-3135` |
| **`ShowCodecOnPlayer`** | El indicador de códec / bitrate / «Lossless» bajo la barra | `ui/player/Player.kt:2419-2445` |
| **`HidePlayerThumbnail`** | Sustituye la portada por un placeholder con el logo | `ui/player/Thumbnail.kt:786` |
| **`RotatingThumbnail`** | Portada giratoria con forma de trébol | `ui/player/Thumbnail.kt:774-780` |
| **`CanvasThumbnailAnimation`** | Portada animada (canvas de Apple/Tidal) | `ui/player/Thumbnail.kt:938` |
| **`SwipeThumbnail`** | El carrusel de portadas del reproductor y el deslizamiento del mini reproductor | `ui/player/Thumbnail.kt:525` · `ui/player/MiniPlayer.kt:328` |
| **`EnableLyricsThumbnailPlayPause`** | La miniatura de 56 dp que reproduce/pausa en la cabecera de las letras | `ui/player/Player.kt:1633` |
| **`hidePlayerSlider`** | La fila de volumen del diseño antiguo (⚠️ el nombre engaña, no es la barra de progreso) | `ui/player/Player.kt:2740` |
| **`EnableExportAsMp3`** (OFF por defecto) | Las 3 filas «Exportando / Exportado / Exportar» en 4 menús distintos | `SongMenu.kt:746-767` · `YouTubeSongMenu.kt:615-636` · `PlayerMenu.kt:637-657` · `OldPlayerMenu.kt:428-453` |
| **`ListenTogetherInTopBar`** (ON por defecto) | **La barra inferior tiene 3 o 4 pestañas.** Con ON, «Juntos» sale como icono del top bar | `MainActivity.kt:792-799`, `:1273-1284` |
| **`ForceSplitView`** | Fuerza el rail lateral y el panel «Sonando ahora» en cualquier tamaño de pantalla | `ui/utils/TvUi.kt:60-63` |
| **`ShowNowPlayingPanel`** (ON por defecto) / **`SidePanelOnLeft`** | Muestra/oculta y coloca el panel lateral de reproducción | `MainActivity.kt:879`, `:1540` |
| **Efecto Liquid Glass** (OFF por defecto) | Cambia el aspecto de 3 superficies. ⚠️ **El reproductor a pantalla completa NUNCA lo renderiza** aunque exista la opción (`ui/component/GlassEffect.kt:80-82`) | `ui/component/GlassEffect.kt:40-252` |
| **`AskTranslateLyricsOnOpen`** (OFF por defecto) | El diálogo «¿Traducir la letra?» | `ui/component/Lyrics.kt:643-666` |
| **`SpectrumVisualizerEnabled`** (sembrado ON) | ⚠️ Reserva espacio y **no dibuja nada** — ver la sección de placebos | `ui/player/Player.kt:2131` |

## 20.2 Por estado de sesión / cuenta

| Condición | Qué aparece o desaparece |
|---|---|
| **Sin sesión de YouTube** | Desaparecen: chip «Remoto» del Historial, «Sus listas de reproducción de YouTube» del Inicio, la pantalla Cuenta, «Sincronizar ahora» de las listas, «Sincronizar» de las auto-listas, los grupos «Preferences» de la hoja de cuenta. En Migración se **ocultan TODAS las fuentes** y solo queda «Iniciar sesión en YouTube Music» |
| **Con sesión** | Aparece el avatar en la barra superior en lugar del engranaje, y los dos interruptores de la hoja de cuenta |
| **Sin sesión de Spotify** | La pantalla de importación cambia de 7 filas a 2 |
| **Sin sesión de Tidal** | La pantalla de Tidal muestra el formulario de Client ID en vez de la lista de colecciones |
| **Sin clave de API de IA** | Desaparece «Traducción de letras con IA» del menú de letras (`ui/menu/LyricsMenu.kt:447`) |

## 20.3 Por estado de Escuchar juntos — el caso más invasivo

Ser **invitado** de una sala degrada el reproductor entero. Un mockup nunca enseña este estado:

- Reproducir/pausar **se convierte en silenciar/activar sonido** (`ui/player/Player.kt:2536`).
- Se deshabilitan anterior, siguiente y la barra de progreso (salvo las onduladas — ver errores).
- Se desactiva el deslizamiento de portadas y del mini reproductor.
- Desaparecen del menú: Iniciar radio, Aleatorio, Repetir.
- Desaparecen de los menús de elemento: Reproducir a continuación, Añadir a la cola, Reproducir, Aleatorio.
- La rejilla superior de `AlbumMenu` pasa **de 3 columnas a 1**; la de `PlayerMenu`, de 3 a 2.
- En la Cola se ocultan el asa de arrastre, el deslizar-para-eliminar y el menú ⋮ de cada fila.
- Aparece **«Re sincronizar»** en los dos menús del reproductor.
- Aparece **«Sugerir alojar»** en los menús de canción.
- La **velocidad de reproducción** se bloquea si estás en una sala (`ui/menu/PlayerMenu.kt:909-919`).
- Siendo **anfitrión** aparecen: solicitudes pendientes, sugerencias pendientes, el diálogo «Administrar usuario» (expulsar / bloquear / transferir) y los botones de copiar enlace y código.

## 20.4 Por contenido de la canción

| Condición | Qué aparece |
|---|---|
| **La canción tiene vídeo** | El conmutador vídeo/audio de la fila del título, y con él TRES maquetaciones más del reproductor (vídeo inmersivo en retrato, vídeo a pantalla completa en apaisado, y Picture-in-Picture) |
| **La canción tiene canvas** | Portada animada y, con el ajuste correspondiente, el canvas inmersivo al girar |
| **La canción tiene letra sincronizada** | El botón «Volver a sincronizar», el salto al tocar una línea, y el icono de sincronía en los resultados de búsqueda de letra |
| **La canción está descargada** | «Eliminar descarga» sustituye a «Descargar» en 8 menús distintos |
| **La canción se está descargando** | Aparece «Descargando» (que al pulsarlo cancela) |
| **La canción tiene más de un artista** | Aparece un diálogo de selección de artista en 5 menús |
| **Se resuelve el álbum** | Aparecen «Ver álbum» en los menús y el título del reproductor se vuelve pulsable |
| **Es un episodio de podcast** (id que empieza por `http`) | Aparece «Ir al podcast» en los dos menús del reproductor |
| **La canción es explícita** | Insignia «E» |
| **La canción es FLAC / AAC ≥320** | Insignias «LOSSLESS» / «320KBPS» |
| **Ya sonó en esta ronda del Aleatorio Mejorado** | Check «Ya reproducida en aleatorio» + fila atenuada al 50 % |
| **Hay error de reproducción** | Superposición «Reproducción fallida» + «Reintentar» sobre la portada, y aviso en el mini reproductor |
| **Está sonando un fundido cruzado** | Chip parpadeante «Fundido cruzado» |

## 20.5 Por contexto de origen (el mismo menú cambia según de dónde se abra)

`SongMenu` es el ejemplo extremo: se abre desde 24 sitios y muestra **tres entradas distintas** que solo
existen en un origen concreto:

- **«Eliminar del historial»** — solo si se abrió desde el Historial (`ui/menu/SongMenu.kt:588`).
- **«Quitar de la lista de reproducción»** — solo desde una lista (`:607`).
- **«Eliminar de la caché»** — solo desde la lista de Caché (`:642`).
- **«Eliminar»** de la selección múltiple — solo desde una lista (`ui/menu/SelectionSongsMenu.kt:492`).

Lo mismo pasa con `PlayerMenu`, que oculta el deslizador de volumen, el Ecualizador y «Avanzado» según
desde dónde se abra (`isQueueTrigger`).

## 20.6 Por tipo de lista

| Condición | Qué cambia |
|---|---|
| **Lista propia y editable** | Aparecen «Editar», «Eliminar», el lápiz de la portada y el arrastre para reordenar. **Desaparece el corazón de guardar** (`ui/menu/PlaylistMenu.kt:421`) |
| **Lista ajena o suscrita** | Al revés: aparece el corazón, desaparecen Editar y Eliminar |
| **Lista sincronizada con YouTube** | Aparecen «Sincronizar ahora», «Iniciar radio» y las DOS opciones del diálogo de borrado («Eliminar también de YouTube» / «Solo eliminar de la app») |
| **Lista puramente local** | Es la única que ofrece **«Editar con IA»** (`ui/menu/PlaylistScreenMenus.kt:113-114`) |
| **La lista ya tiene portada personalizada** | El lápiz abre un menú (Elegir / Eliminar); si no la tiene, abre el selector de imágenes directamente |

## 20.7 Por dispositivo y compilación

| Condición | Qué cambia |
|---|---|
| **Compilación `gms`** | El botón Cast existe. En la compilación FOSS **es un stub vacío** (`app/src/gms/.../CastButton.kt` vs el stub) |
| **Compilación con `-Pnosub=true`** | La puerta de licencia entera se salta (`license/LicenseGate.kt:21-24`) |
| **Pantalla ancha / tablet / plegable abierto** | Aparecen el rail lateral, el panel «Sonando ahora» y el reproductor dividido con la cola en el panel izquierdo |
| **TV o coche** | Anillos de foco por todas partes, foco inicial en reproducir/pausar, los controles de vídeo **no se auto-ocultan**, y la pulsación larga en «Buscar» del rail abre Reconocer |
| **Picture-in-Picture** | Vista limpia con solo título y artista, y 3 controles remotos |
| **Android 12+ y gama media/alta** | Es el único caso en que el efecto Liquid Glass es elegible |
| **Android 13+** | Petición de permiso de notificaciones al arrancar |
| **Fabricante «agresivo» sin exención de batería** | El diálogo de fiabilidad en segundo plano (una sola vez en la vida de la instalación) |
| **Dispositivo caliente (throttling térmico)** | Se apagan solos el canvas animado, el visualizador, el vídeo de fondo del artista y el modo vídeo en gama baja — **sin avisar al usuario** |
| **Sin conexión / modo sin conexión** | El Inicio se sustituye entero por la versión sin conexión; la búsqueda pierde la fuente online; aparece la superposición «Parece que no tienes conexión» |

## 20.8 Por primera ejecución

Aparecen una sola vez y son invisibles para siempre después: la puerta de Términos, la puerta de
licencia (6 pantallas), el diálogo de Bienvenida (que además **vuelve en cada actualización**), los
4 pasos de onboarding, el diálogo de fiabilidad en segundo plano y la petición de notificaciones.

---

# 21. GESTOS — lo que ningún mockup puede enseñar

Esta es, con diferencia, la sección más peligrosa del rediseño. Un gesto no se ve en una captura de
pantalla, no aparece en una lista de botones y no se echa de menos hasta que un usuario que lo usaba a
diario descubre que ya no está. Todos los de abajo existen hoy y están verificados en el código.

## 21.1 Gestos del reproductor

| Gesto | Qué hace exactamente | archivo:línea | Condicional |
|---|---|---|---|
| **Mantener pulsado el título** | Copia el título al portapapeles + Toast «Título copiado» | `ui/player/Player.kt:1932` | siempre |
| **Mantener pulsado el artista** | Copia el nombre del artista + Toast «Artista copiado» | `ui/player/Player.kt:1964` | siempre |
| **Doble toque en el centro de la portada** (35–65 % del ancho) | Reproducir / pausar | `ui/player/Thumbnail.kt:735` | no como invitado de Escuchar juntos |
| **Doble toque en la mitad izquierda de la portada** | Retrocede 5 s. Con «segundos extra» activado y toques a menos de 1 s, se acumula: 5, 10, 15… + overlay «−N segundos hacia atrás» | `ui/player/Thumbnail.kt:757` | igual; se invierte en idiomas de derecha a izquierda |
| **Doble toque en la mitad derecha de la portada** | Avanza 5 s con el mismo multiplicador acumulativo | `ui/player/Thumbnail.kt:760` | igual |
| **Deslizar horizontal sobre el carrusel de portadas** | Cambia de canción con encaje (snap) | `ui/player/Thumbnail.kt:525-559` · salto `:363-371` · encaje `ui/player/ThumbnailSnapUtils.kt:16` | solo con «Deslizar miniatura» ON, reproductor expandido y sin ser invitado |
| **Deslizar horizontal sobre la portada a pantalla completa** | Derecha = anterior, izquierda = siguiente (umbral 100 px) | `ui/player/Player.kt:3497` (retrato), `:3238`, `:3212` (apaisado) | **solo en modo pantalla completa** |
| **Deslizar hacia ARRIBA en el cuerpo del reproductor** | Abre la hoja de la Cola arrastrando | `ui/player/Player.kt:3468-3488` | solo retrato sin vídeo, expandido, no pantalla completa y cola cerrada |
| **Deslizar hacia ABAJO en el reproductor** | Colapsa a mini reproductor. Si se pasa del umbral inferior, **descarta la hoja: para la reproducción, limpia la cola y el automix** | `ui/component/BottomSheet.kt:92-105` · efecto `ui/player/Player.kt:1551-1555` | siempre — **es una acción destructiva escondida en un gesto** |
| **Atrás del sistema con el reproductor abierto** | Colapsa a mini reproductor (deliberadamente NO sale del modo vídeo) | `ui/component/BottomSheet.kt:114-116` · nota `ui/player/Player.kt:3311` | mientras no esté colapsado |
| **Deslizar horizontal sobre el MINI reproductor** | Cambia de canción, con animación de arrastre e icono direccional; umbral por velocidad (ajuste «Sensibilidad del deslizamiento», 0,73 por defecto) o por distancia | `ui/player/MiniPlayer.kt:328` (diseño nuevo), `:678` (legacy) | solo con «Deslizar miniatura» ON y sin ser invitado |
| **Tocar el vídeo en retrato** | Alterna TODOS los controles y el título. **Sin auto-ocultado** | `ui/player/Player.kt:3316` | solo vídeo en retrato |
| **Tocar el vídeo en apaisado** | Alterna la barra de controles; se auto-ocultan a los 3,5 s (**no en TV/coche**) | `ui/player/Player.kt:2968`, `:2985` | solo vídeo apaisado, sin PiP |
| **Tocar el canvas en apaisado** | Alterna la barra de transporte; auto-ocultado a 3,5 s | `ui/player/Player.kt:3100` | solo con canvas inmersivo activado |
| **Cualquier tecla del mando (TV/coche)** | Vuelve a mostrar los controles de vídeo o canvas | `ui/player/Player.kt:2964`, `:3096` | solo TV/coche |
| **Arrastrar o tocar las barras onduladas** | Tocar = salto directo; arrastrar = búsqueda continua (la onda se aplana al arrastrar) | `ui/component/SquigglySlider.kt:121`/`:129` · `ui/component/WavySlider.kt:86`/`:94` | según el estilo de barra elegido |
| **Deslizar desde el borde con la barra de estado oculta** | Revela temporalmente las barras del sistema | `ui/player/Player.kt:967-969`, `:2930-2938` | pantalla completa con «ocultar barra de estado» ON |
| **Foco inicial del mando al abrir el reproductor** | Enfoca reproducir/pausar (hasta 40 reintentos de 50 ms) | `ui/player/Player.kt:423-438` | solo TV/coche |

**No existen en el reproductor:** pinza/zoom, doble toque en el mini reproductor, ni arrastre vertical
para el volumen.

## 21.2 Gestos de la Cola

| Gesto | Qué hace exactamente | archivo:línea | Condicional |
|---|---|---|---|
| **Deslizar una fila (a cualquier lado)** | La quita de la cola + snackbar «Eliminado «X»…» con **Deshacer**, que la reinserta en su posición original | `ui/player/Queue.kt:1307-1344`, `:1470-1476`, snackbar `:1325-1329` | **bloqueado si la cola está bloqueada** o si eres invitado |
| **Arrastrar por el asa para reordenar** | Mueve la canción; si el aleatorio está encendido reescribe el orden barajado en lugar de la cola | `ui/player/Queue.kt:1399-1407`, estado `:734-754`, soltar `:756-778` | el asa se oculta con la cola bloqueada o siendo invitado |
| **Mantener pulsada una fila de la cola** | Entra en modo selección (con háptica) y marca esa fila | `ui/player/Queue.kt:1453-1461` | siempre |
| **Mantener pulsada una fila de «Relacionados»** | Abre el menú de la canción — ⚠️ **es la ÚNICA acción de esa fila: el toque simple no hace nada** | `ui/player/Queue.kt:1647-1663` (toque vacío en `:1646`) | siempre |
| **Atrás en modo selección** | Sale del modo selección | `ui/player/Queue.kt:280-282` | en selección |
| **Arrastrar la lista de la cola** | Colapsa o expande la hoja (scroll anidado) | `ui/player/Queue.kt:1200`, `:1223`, `:1247`, `:1284` | siempre |
| **Colapsar la hoja** | Vuelve automáticamente a la pestaña SIGUIENTE | `ui/player/Queue.kt:291-293` | siempre |

## 21.3 Gestos de las Letras

| Gesto | Qué hace exactamente | archivo:línea | Condicional |
|---|---|---|---|
| **Tocar una línea de letra** | Salta a ese momento de la canción y la centra con una animación de 1,5 s | `ui/component/Lyrics.kt:1274-1319` (estándar), `:1144-1174` (estilo echomusic), `:1225-1254` (estilo Metro) | solo con letra sincronizada, ajuste «tocar letra» ON, sin fundido en curso y sin ser invitado |
| **Mantener pulsada una línea** | Activa el modo selección de letras y la marca | `ui/component/Lyrics.kt:1321-1332` · `ui/component/EchoMusicLyrics.kt:183-187` · `ui/component/MetroLyrics.kt:193-197` | siempre |
| **Tocar más líneas en modo selección** | Añade o quita, **máximo 5**; al pasarse sale un toast | `ui/component/Lyrics.kt:742`, `:749` | en selección |
| **Desplazar la lista de letras con el dedo** | Desactiva el auto-scroll y aparece el botón **«Volver a sincronizar»** | `ui/component/Lyrics.kt:1022-1049`, botón `:2090` | con letra sincronizada |
| **Atrás en modo selección de letras** | Cancela la selección | `ui/component/Lyrics.kt:736-739` | en selección |
| *(efecto invisible)* **La pantalla no se apaga mientras las letras están visibles** | `FLAG_KEEP_SCREEN_ON` | `ui/component/Lyrics.kt:759-767` | siempre con letras |

## 21.4 Gestos de listas y pantallas de contenido

| Gesto | Qué hace exactamente | archivo:línea | Condicional |
|---|---|---|---|
| **Mantener pulsada una fila o tarjeta** | Abre el menú del elemento (canción, álbum, artista, lista). Es la vía alternativa al botón **⋯**, y en algunas pantallas la ÚNICA | ~40 sitios; p. ej. `ui/screens/HomeScreen.kt:1185`, `ui/component/Library.kt:79`, `ui/screens/search/OnlineSearchResult.kt:312` | casi todas las listas. Sin háptica en Explorar genérico y Búsqueda local |
| **Mantener pulsada una fila del historial local** | Entra en modo selección múltiple (no abre menú) | `ui/screens/HistoryScreen.kt:373-379` | fuera del modo selección |
| **Deslizar una fila de canción a la DERECHA** | «Reproducir a continuación» + toast | `ui/component/Items.kt:1732-1736` | **solo si el ajuste «Deslizar canción» está ON — por defecto está APAGADO** |
| **Deslizar una fila de canción a la IZQUIERDA** | «Añadir a la cola» + toast | `ui/component/Items.kt:1738-1742` | igual |
| **Deslizar una canción en una lista local** | La quita de la lista | `ui/screens/playlist/LocalPlaylistScreen.kt` (`SwipeToDismiss` + `ReorderableItem`) | según el ajuste y el bloqueo de la lista |
| **Arrastrar para reordenar una lista local** | Reordena las canciones de la lista | `ui/screens/playlist/LocalPlaylistScreen.kt` (`rememberReorderableLazyListState`) | solo listas editables |
| **Arrastrar para reordenar los proveedores de letras** | Cambia la prioridad de las fuentes de letra | `ui/component/DraggableLyricsProviderList.kt:90-99` | solo en Ajustes > Contenido |
| **Arrastrar la barra de desplazamiento rápido** | Salto rápido dentro de listas largas | `ui/component/DraggableScrollBarOverlay.kt` | listas largas |
| **Deslizar hacia abajo para actualizar** | Recarga la pantalla | Inicio `ui/screens/HomeScreen.kt:1049-1061` · Sugerencias `ui/screens/search/suggestions/TabNewsSuggestion.kt:99-113` · Registro de cambios `echomusic/changelog/changelogscreen.kt:278-282` | **solo en esas tres**; NO existe en Biblioteca, Explorar, Podcasts, Historial, Estadísticas, Cuenta ni Juntos |
| **Mantener pulsada la flecha «volver»** | Vuelve DIRECTAMENTE a la pantalla principal, saltándose toda la pila | `ui/component/IconButton.kt:64-96`; usado en ~12 pantallas | ⚠️ **gesto oculto, sin ninguna pista visual** |
| **Volver a tocar la pestaña ya seleccionada** | Hace *scroll* al principio de la pantalla en vez de navegar | `MainActivity.kt:1358-1362` (barra), `:1503-1507` (rail) | siempre |
| **Mantener pulsado «Buscar» en el rail lateral** | Abre Reconocer música | `ui/component/AppNavigation.kt:80-103` | ⚠️ **solo en el rail; la barra inferior del móvil NO tiene este gesto** |
| **Mantener pulsada una burbuja del chat** | Activa el modo responder (el toque simple no hace nada) | `ui/screens/CommentTogether.kt:249-252` | en el chat de la sala |
| **Atrás con un chip de mood activo en Inicio** | Desactiva el chip en vez de navegar | `ui/screens/HomeScreen.kt:700-704` | con chip activo |
| **Atrás con un filtro de Biblioteca activo** | Vuelve al filtro «Biblioteca» | `ui/screens/library/LibraryScreen.kt:70-72` | con filtro activo |
| **Atrás en cualquier modo selección** | Sale del modo selección | Cola, Álbum, Historial, listas, Letras — 10 pantallas | en selección |
| **Deslizar entre pestañas de la barra inferior** | ⚠️ **NO EXISTE.** El cambio de pestaña anima lateralmente pero solo responde al toque | `MainActivity.kt:1555-1618` | — |

## 21.5 Gestos de otras superficies

| Gesto | Qué hace exactamente | archivo:línea | Condicional |
|---|---|---|---|
| **Doble toque en la portada del Modo Ambiente** | Reproducir / pausar | `ui/screens/ambient/AmbientModeScreen.kt:136-151` | siempre |
| **Deslizar horizontal en el Modo Ambiente** | Más de 150 px: derecha = anterior, izquierda = siguiente | `ui/screens/ambient/AmbientModeScreen.kt:85-97` | siempre |
| **Deslizar vertical en el Modo Ambiente** | Sube o baja el volumen del sistema | `ui/screens/ambient/AmbientModeScreen.kt:103-113` | siempre |
| **Tocar y arrastrar la barra de volumen del selector de audio** | Fija el volumen del sistema | `echomusic/AudioDeviceBottomSheet.kt:698-718` | con la hoja abierta |
| **Arrastrar el asa de cualquier hoja / tocar fuera** | Cierra la hoja | `ui/component/BottomSheetMenu.kt:127-142`, `ui/component/BottomSheetPage.kt` | siempre |
| **Tocar cualquier dato en «Información de la canción»** | Lo copia al portapapeles | `ui/utils/ShowMediaInfo.kt:360-364` | siempre |
| **Flechas arriba/abajo del mando en los Términos** | Desplaza el documento 260 px sin mover el foco de los botones | `legal/TermsScreens.kt:133-146` | solo con mando |
| **Pellizco/zoom en el WebView de inicio de sesión** | Zoom de la página de Google | `ui/screens/LoginScreen.kt:229-231` | siempre |
| **Salir de la app con vídeo reproduciéndose** | Entra en Picture-in-Picture con 3 controles remotos | `MainActivity.kt:450-471`, `:492-513` | solo en modo vídeo reproduciendo |
| **Háptica global al pulsar y al arrastrar** | Vibración sutil en toda la app | `MainActivity.kt:751-776` | ajuste «Háptica» ON (por defecto) |
| **Anillo de foco del mando (TV/coche)** | Halo + anillo + escala 1,06 en el elemento enfocado | `ui/utils/TvUi.kt:142-256` | solo TV/coche o vista dividida forzada |

## 21.6 Gestos de Biblioteca, Artista, Álbum y Listas

| Gesto | Dónde | archivo:línea | Condicional |
|---|---|---|---|
| Pulsación larga → modo multiselección (con háptica) | Álbum, Lista local, Lista online, Auto-lista, En caché, Mi Top | AlbumScreen.kt:824-830 · LocalPlaylistScreen.kt:815-821 · OnlinePlaylistScreen.kt:358-364 · AutoPlaylistScreen.kt:464-470 · CachePlaylistScreen.kt:324-330 · TopPlaylistScreen.kt:380-386 | solo si `!inSelectMode` |
| Pulsación larga → menú contextual (canción) | Canciones (cuadrícula), Artista, Artista>canciones, Local, Biblioteca | LibrarySongsScreen.kt:456-465 · ArtistScreen.kt:831-840, :979-988 · ArtistSongsScreen.kt:179-188 · LocalSongScreen.kt:466-475 · LibraryMixScreen.kt:506-515 / :804-813 | siempre |
| Pulsación larga → menú contextual (artista / álbum / lista) | Biblioteca, Artistas, Álbumes, Listas, Álbumes favoritos, Artista, Álbum | Library.kt:77-85, :150-158, :248-286 · LibraryMixScreen.kt:448-453 / :594-603 / :762-767 / :859-868 · ArtistScreen.kt:881-890, :1033-1065 · ArtistItemsScreen.kt:279-307 · AlbumScreen.kt:860-869, :902-911 | siempre. SIN háptica en artistas/álbumes/listas de biblioteca (solo canciones vibran) |
| ~~Pulsación larga en la flecha atrás → volver al inicio (`backToMain`)~~ **ELIMINADO (0.6.145)** | Artista×5, Álbum, Lista local, Lista online, Auto-lista, En caché, Mi Top | ArtistScreen.kt:1227 · ArtistItemsScreen.kt:330 · ArtistSongsScreen.kt:200 · ArtistAlbumsScreen.kt:179 · ArtistAlbumsGridScreen.kt:70 · AlbumScreen.kt:993 · LocalPlaylistScreen.kt:941-945 · OnlinePlaylistScreen.kt:528-532 · AutoPlaylistScreen.kt:562-566 · CachePlaylistScreen.kt:410-414 · TopPlaylistScreen.kt:465-469 | **YA NO EXISTE.** Gesto oculto que reventaba la pila de navegación sin aviso; `backToMain()` borrado y `IconButton.onLongClick` pasa a opcional (`null`). Ver REGRESSION_REGISTRY fila 111 |
| Arrastrar para reordenar canciones (`ReorderableItem` + `draggableHandle`) | Lista local | LocalPlaylistScreen.kt:535-549 (estado), :681-684 (item), :777-787 (asa), :551-578 (persistencia DB + `YouTube.moveSongPlaylist`) | `sortType == CUSTOM && !locked && !inSelectMode && !isSearching && editable`; `headerItems = 2` (:530) |
| Deslizar para eliminar canción (`SwipeToDismissBox`, ambos sentidos) | Lista local | LocalPlaylistScreen.kt:712-730, :826-838 (borrado :687-709) | solo si `SwipeToRemoveSongKey` ON — **por defecto false** (:711) + `!locked` + `!inSelectMode` |
| Deslizar la fila: derecha = "Reproducir a continuación", izquierda = "Añadir a la cola" (umbral 300 px, con Toast) | Canciones, Local, Agregar música (biblioteca y sugeridas), las 4 listas | Items.kt:516-525 y :1709-1747; desde LibrarySongsScreen.kt:338, AddMusicSheet.kt:235, AddMusicComponents.kt:179 | solo si `SwipeToSongKey` ON — **por defecto false** (Items.kt:463). Desactivado explícitamente en resultados de búsqueda (AddMusicSheet.kt:158) y filas de carrusel (:339) |
| Tirar para refrescar (pull-to-refresh) — sincronización completa | Biblioteca (hub) | LibraryMixScreen.kt:286-296 | siempre en el hub |
| Tirar para refrescar | Auto-lista | AutoPlaylistScreen.kt:315-328 (gesto), :493-501 (indicador) | solo si `playlistType == LIKE \|\| UPLOADED` (:313). NO existe en Lista online, En caché, Mi Top, Novedades, Local ni Álbumes favoritos |
| Arrastre de la barra de desplazamiento rápida | Lista local, Auto-lista, En caché, Mi Top | LocalPlaylistScreen.kt:880-889 · AutoPlaylistScreen.kt:482-491 · CachePlaylistScreen.kt:342-351 · TopPlaylistScreen.kt:398-407 → DraggableScrollBarOverlay.kt:81-159 | > 15 ítems y lista desbordada; `headerItems = 2` |
| Ocultar/mostrar FAB al desplazar | Canciones, Artista>canciones | LibrarySongsScreen.kt:502/510 · ArtistSongsScreen.kt:210 → HideOnScrollFAB.kt:45/112/179 | siempre |
| Volver a tocar la pestaña Biblioteca → scroll al principio | Biblioteca y sus 4 sub-pantallas | LibraryMixScreen.kt:236-248 · LibrarySongsScreen.kt:133-145 · LibraryArtistsScreen.kt:165-177 · LibraryAlbumsScreen.kt:144-156 · LibraryPlaylistsScreen.kt:200-227 | siempre |
| Atrás del sistema → salir de búsqueda / de selección / volver al hub | LibraryScreen, Álbum, las 5 listas | LibraryScreen.kt:70-72 · AlbumScreen.kt:210-212 · LocalPlaylistScreen.kt:262-269 · OnlinePlaylistScreen.kt:246-253 · AutoPlaylistScreen.kt:197-204 · CachePlaylistScreen.kt:175-182 · TopPlaylistScreen.kt:173-180 | la búsqueda tiene prioridad sobre la selección |
| Cabecera con parallax / barra transparente→opaca | Artista, Álbum, Lista online, Lista local | ArtistScreen.kt:205-213, :367-376 · AlbumScreen.kt:266-288 · OnlinePlaylistScreen.kt:622-626, :240-244, :584-588 · LocalPlaylistScreen.kt:580-584 | umbral 100 px (Artista) / 150 px (Lista online); parallax desactivado en pantalla ancha |
| Barra grande colapsable (`exitUntilCollapsedScrollBehavior`) | Local | LocalSongScreen.kt:150, :276, :370 | siempre |
| Desplazamiento infinito (carga más al final) | Artista>lista de sección, Lista online | ArtistItemsScreen.kt:98-114, :225-233, :313-319 · OnlinePlaylistScreen.kt:387-396 | siempre |
| Carrusel horizontal con enganche por página (hasta 4 filas apiladas) | Agregar música > 3 carruseles | AddMusicSheet.kt:310-329 | siempre |
| Deslizar/arrastrar para cerrar la hoja | Agregar música, hoja de escaneo local | AddMusicSheet.kt:100-105 (`skipPartiallyExpanded = true`) · LocalSongScreen.kt:674-675, :147 | siempre |
| Recorte de imagen (UCrop: pellizcar/rotar) | Lista local > editar portada | LocalPlaylistScreen.kt:1088-1108 | tras elegir imagen |
| Toque en el texto de la descripción → expandir/contraer o abrir enlace | Artista, Álbum, las 4 listas | ExpandableText.kt:85-94 | solo si desborda 3 líneas |
| Foco D-pad (TV/coche) | prácticamente todas | `tvFocusRestorer()` / `tvFocusable()` en LibraryMixScreen.kt:303/622, LibrarySongsScreen.kt:310/401, LibraryArtistsScreen.kt:226/283, LibraryAlbumsScreen.kt:204/262, LibraryPlaylistsScreen.kt:304/435, FavoriteAlbumsScreen.kt:85, LocalSongScreen.kt:382, ReleaseRadarScreen.kt:113, AddMusicSheet.kt:328, AlbumScreen.kt:528/586/667, LocalPlaylistScreen.kt:1414/1469/1576, SortHeader.kt:72/99/129 | solo en TV/coche |

NO existen en ningún fichero asignado: doble toque; pull-to-refresh en Novedades/Local/Álbumes favoritos/Lista online/En caché/Mi Top; arrastre de reordenación fuera de la Lista local; multiselección en Biblioteca (Canciones/Artistas/Álbumes/Listas), Local, Novedades, Álbumes favoritos ni Artista.

---


## 21.7 Gestos de Ajustes

| Gesto | Dónde | archivo:línea | Efecto |
|---|---|---|---|
| ~~**Pulsación larga en la flecha Atrás** = volver a la pantalla principal (`backToMain`)~~ **ELIMINADO (0.6.145)** | Casi todas las pantallas de ajustes | SettingsScreen.kt:371 · AppearanceSettings.kt:1996 · GlassEffectSettings.kt:515 · PlayerSettings.kt:1169 · ContentSettings.kt:1373 · PrivacySettings.kt:254 · PerformanceSettings.kt:259 · BackupAndRestore.kt:341 · AccountsScreen.kt:132 · AccountSettingsScreen.kt:79 · LastFMSettingsScreen.kt:737 · QobuzSettingsScreen.kt:57 · RomanizationSettings.kt:409 · LogsScreen.kt:89 · ListenTogetherSettings.kt:478 · IntegrationScreen.kt:47 · TermsScreens.kt:260 | **YA NO EXISTE.** Gesto oculto que reventaba la pila de navegación sin aviso; `backToMain()` borrado y `IconButton.onLongClick` pasa a opcional (`null`). Ver REGRESSION_REGISTRY fila 111 |
| **Arrastre para reordenar** proveedores de letras | Ajustes > Contenido > Prioridad de proveedores de letras (diálogo) | ContentSettings.kt:488 (`DraggableLyricsProviderList`), lista en 398-403 | cambia el orden de intento |
| **Arrastre de bandas sobre la curva del EQ paramétrico** | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1088 (`pointerInput` + `detectDragGestures`/`detectTapGestures`) | mueve la banda seleccionada |
| **Arrastre de los 10 sliders de banda del EQ gráfico** | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1366 (`EqBandSlider`) | ganancia por banda |
| **Arrastre del slider de preamplificación** | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:653 | −20…+6 dB |
| **Arrastre del slider Q** (paramétrico) | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:1224 | ancho de banda |
| **Scroll horizontal** de la fila de chips de preajuste | Ajustes > Sonido > Ecualizador | AxionEqScreen.kt:~728 (`horizontalScroll(presetScrollState)`) | navegación por preajustes |
| **Tap + arrastre** en el panel saturación/valor del selector de color | Diálogos de color (Liquid Glass) | ColorPicker.kt:335 y 343 | elige color |
| **Tap + arrastre** en la barra de tono | Diálogos de color | ColorPicker.kt:382 y 387 | elige tono |
| **Arrastre del slider de radio personalizado** | Ajustes > Apariencia > Radio de esquina | ThumbnailCornerRadiusSelector.kt:226 | radio en dp |
| **Arrastre de sliders en línea** (duración de transición, historial, límite de precarga) | Ajustes > Reproductor | PlayerSettings.kt:596, 663, 758 | valor continuo |
| **Arrastre de sliders en diálogo** (Liquid Glass: viveza, radio, altura y cantidad de lente, opacidad) | Ajustes > Apariencia > Liquid Glass | GlassEffectSettings.kt:394, 413, 432, 451, 471 | valor continuo |
| **Arrastre de sliders en diálogo** (tamaño y interlineado de letra, sensibilidad al deslizar) | Ajustes > Apariencia | AppearanceSettings.kt:1440-1500 (sensibilidad), 1622, 1628 | valor continuo |
| **Arrastre de sliders en diálogo** (scrobble: duración mínima, % de retraso, minutos) | Ajustes > Scrobbling | LastFMSettingsScreen.kt:~460, ~525, ~590 | valor continuo |
| **Retroceso del sistema (BackHandler)** | Ajustes (sub-pantalla Suscripción) / Copias de seguridad (sub-vista Import) | SettingsScreen.kt:75 · BackupAndRestore.kt:197 | vuelve a la vista anterior sin salir |
| **Scroll que revela el título** en la barra superior | Ajustes (raíz) | SettingsScreen.kt:357 | el título aparece al pasar de 100 px |

**No existe ninguna pulsación larga sobre una fila de preferencia** en todo el árbol de Ajustes: `Material3SettingsItem` no tiene parámetro `onLongClick`.

---


---

# 22. DIÁLOGOS Y HOJAS MODALES (apéndice)

Los diálogos del **reproductor** están en §3.8, §4.3, §5.1, §5.2, §7.1 y §8. Aquí se recogen los del resto de la app.

## 22.1 Diálogos y hojas de Biblioteca, Artista, Álbum y Listas

| Diálogo / hoja | Qué control lo abre | archivo:línea del control |
|---|---|---|
| Importar lista de reproducción desde YouTube Music (`TextFieldDialog`) | Menú del FAB Importar → Importar desde YouTube Music | LibraryScreen.kt:221-227 → diálogo :242-272 |
| Importar lista por link (Spotify) | Menú del FAB Importar → Importar desde Spotify | LibraryScreen.kt:214-220 → diálogo :275-312 |
| Toast «El enlace de la lista de reproducción no es válido» | Aceptar con URL inválida en cualquiera de los dos | LibraryScreen.kt:264-268 / :303-307 |
| `CreatePlaylistDialog` | FAB Crear lista de reproducción | LibraryScreen.kt:194 → :314-324 |
| `AiPlaylistDialog` ("Lista AI") | FAB pequeño Lista AI | LibraryScreen.kt:178 → :326-338 |
| `ArtistMenu` | ⋯ / pulsación larga en artista | Library.kt:38-45, :78-85 · LibraryMixScreen.kt:436-442, :448-453 |
| `AlbumMenu` | ⋯ / pulsación larga en álbum; Más opciones de la barra del Álbum | Library.kt:104-110, :151-157 · LibraryMixScreen.kt:570-585 · ArtistScreen.kt:881-890 · AlbumScreen.kt:1089-1109 |
| `PlaylistMenu` / `YouTubePlaylistMenu` | ⋯ / pulsación larga en lista de biblioteca | Library.kt:176-206, :251-281 · LibraryMixScreen.kt:482-497 |
| `SongMenu` | ⋯ / pulsación larga en canción local | LibrarySongsScreen.kt:349-364 · LocalSongScreen.kt:429-444 · ArtistScreen.kt:798-813 · ArtistSongsScreen.kt:145-160 · AlbumScreen.kt:784-799 · LocalPlaylistScreen.kt:758-775 · AutoPlaylistScreen.kt:426-441 · CachePlaylistScreen.kt:287-301 (`isFromCache=true`) · TopPlaylistScreen.kt:343-358 |
| `SelectionSongMenu` | ⋯ de la barra de selección | AlbumScreen.kt:1025-1043 · LocalPlaylistScreen.kt:967-988 · AutoPlaylistScreen.kt:589-605 · CachePlaylistScreen.kt:436-452 · TopPlaylistScreen.kt:492-508 |
| `YouTubeSongMenu` / `YouTubeAlbumMenu` / `YouTubeArtistMenu` / `YouTubePlaylistMenu` | ⋯ / pulsación larga en contenido online | ArtistScreen.kt:940-955, :1038-1062 · ArtistItemsScreen.kt:164-189, :283-304 · AlbumScreen.kt:862-868, :904-910 · OnlinePlaylistScreen.kt:374-380, :431-456, :1054-1064 |
| `YouTubeSelectionSongMenu` | ⋯ de la barra de selección (Lista online) | OnlinePlaylistScreen.kt:555-572 |
| `LocalPlaylistMenu` | ⋯ de la cabecera de la Lista local | LocalPlaylistScreen.kt:1492-1589 |
| `AutoPlaylistMenu` / `CachePlaylistMenu` / `TopPlaylistMenu` | ⋯ circular de la cabecera | AutoPlaylistScreen.kt:807-853 · CachePlaylistScreen.kt:639-667 · TopPlaylistScreen.kt:699-741 |
| `AddMusicSheet` ("Agregar música") | Botón Agregar música del pie de la Lista local — ÚNICO punto en todo el repo | LocalPlaylistScreen.kt:843-850 → render :873-878 |
| `AiModifyPlaylistDialog` ("Editar con IA") | Menú ⋯ de la Lista local → Editar con IA — ÚNICO punto en todo el repo | PlaylistScreenMenus.kt:110-127 → LocalPlaylistScreen.kt:612 → render :355-366 |
| `TextFieldDialog` renombrar lista | Menú ⋯ → Editar | LocalPlaylistScreen.kt:1501 → :315-345 |
| `DefaultDialog` confirmar quitar descarga | Menú ⋯ → Eliminar descarga | LocalPlaylistScreen.kt:1537 → :372-414 · AutoPlaylistScreen.kt:820-822 → :257-291 · TopPlaylistScreen.kt:707-709 → :218-252 |
| `DefaultDialog` confirmar eliminar lista (3 botones) | Menú ⋯ → Eliminar | LocalPlaylistScreen.kt:1534 → :419-528 |
| `ActionPromptDialog` aviso de portada | Lápiz sobre la portada SIN portada personalizada | LocalPlaylistScreen.kt:1285-1287 / :1349-1351 → :1182-1207 |
| `CustomThumbnailMenu` | Lápiz sobre la portada CON portada personalizada | LocalPlaylistScreen.kt:1251-1284 / :1315-1348 |
| Selector de imágenes (`PickVisualMedia`) → UCrop | "Aceptar" del aviso o "Elija de la biblioteca" | LocalPlaylistScreen.kt:1188-1190, :1256-1258 → :1075-1110 |
| `LocalSongScanSheet` (hoja de escaneo) | Icono Ajustes de la barra de Local | LocalSongScreen.kt:359-364 → :245-264 |
| Selector de carpetas del sistema (SAF `OpenDocumentTree`) | Añadir carpeta de la hoja | LocalSongScreen.kt:885-890 → lanzador :190-197 |
| Diálogo de permiso del sistema | Permitir de la hoja | LocalSongScreen.kt:926 → :256-262 |
| «Ya escuchaste parte de esta lista» / «Llevas %1$d de %2$d canciones reproducidas. ¿Continuar sin repetirlas, o empezar de cero?» / Continuar / Empezar de cero (destructiva: borra la memoria) | Botón/FAB Aleatorio | LibrarySongsScreen.kt:475-515 · ArtistScreen.kt:703-740 · ArtistSongsScreen.kt:213-239 · AlbumScreen.kt:624-680 · LocalPlaylistScreen.kt:1437-1489 · AutoPlaylistScreen.kt:710-736 · CachePlaylistScreen.kt:586-613 · TopPlaylistScreen.kt:606-633 → ShuffleMemoryPrompt.kt:62-100 (solo si `contextId != null && playedCount > 0`, :104) |
| Desplegable de orden / de periodo | Botón del `SortHeader` | SortHeader.kt:154-183 |
| Tooltip "Toggle sort order" (hardcoded) | Mantener/hover el botón de dirección | SortHeader.kt:86-89 |
| Selector del sistema Compartir | Compartir del Álbum / de la Lista online / del menú ⋯ | AlbumScreen.kt:1064-1087 · OnlinePlaylistScreen.kt:832-840 · PlaylistScreenMenus.kt:180-193 |
| Toast «Enlace copiado al portapapeles» | Icono de enlace del Artista | ArtistScreen.kt:1236-1243 |
| Toast «Actualizando…» / «Error desconocido» | Refrescar / Reproducir de Novedades | ReleaseRadarScreen.kt:90-94 / :190-195 |
| Toast «Vista previa no disponible por ahora» | Fallo de previsualización | SongPreviewController.kt:104-106 |
| Navegador externo | Enlace dentro de una descripción | ExpandableText.kt:86-89 |

---


## 22.2 Diálogos y hojas de Ajustes

| Diálogo / hoja | Se abre desde | archivo:línea |
|---|---|---|
| Selector enum de estilo de fondo del minirreproductor | Apariencia | AppearanceSettings.kt:1085 |
| Selector enum de estilo de fondo del reproductor | Apariencia | AppearanceSettings.kt:1172 |
| Selector de radio de esquina (`ThumbnailCornerRadiusModal`) | Apariencia | AppearanceSettings.kt:1231 → modal en 1507 |
| Selector enum de colores de botones del reproductor | Apariencia | AppearanceSettings.kt:1265 |
| Selector enum de estilo de la barra | Apariencia | AppearanceSettings.kt:1279 |
| Diálogo de sensibilidad al deslizar (slider) | Apariencia | AppearanceSettings.kt:1423 → 1440 |
| Selector enum de posición de la letra | Apariencia | AppearanceSettings.kt:1522 |
| Selector enum de estilo de animación de la letra | Apariencia | AppearanceSettings.kt:1536 |
| Diálogo de tamaño del texto de la letra (slider) | Apariencia | AppearanceSettings.kt:1622 |
| Diálogo de interlineado (slider) | Apariencia | AppearanceSettings.kt:1628 |
| Selector enum de pestaña predeterminada | Apariencia | AppearanceSettings.kt:1745 |
| Selector enum de chip de biblioteca | Apariencia | AppearanceSettings.kt:1759 |
| Selector enum de tamaño de celda | Apariencia | AppearanceSettings.kt:1837 |
| Selector de densidad + diálogo «Reinicio necesario» / «Reiniciar» | Apariencia | AppearanceSettings.kt:1850 |
| 5 diálogos de slider de Liquid Glass | Apariencia > Liquid Glass | GlassEffectSettings.kt:383, 402, 421, 440, 455 |
| `ColorPickerDialog` (tinte de superficie) | Apariencia > Liquid Glass | GlassEffectSettings.kt:476 |
| `ColorPickerDialog` (color del texto) | Apariencia > Liquid Glass | GlassEffectSettings.kt:493 |
| `EnumDialog` de calidad de sonido | Reproductor | PlayerSettings.kt:302 |
| `EnumDialog` de calidad de descarga | Reproductor | PlayerSettings.kt:328 |
| `EnumDialog` de curva de transición (9 curvas) | Reproductor | PlayerSettings.kt:364 |
| Diálogo «Función beta» del crossfade | Reproductor | PlayerSettings.kt:390 |
| Diálogo «¿Activar Saavn (320 kbps)?» (hardcoded) | Reproductor | PlayerSettings.kt:410 |
| Diálogo «Activar audio sin pérdida» (advertencia) | Reproductor | PlayerSettings.kt:430 |
| Diálogo de configuración de proxy | Contenido | ContentSettings.kt:198 |
| Diálogo de idioma de contenido | Contenido | ContentSettings.kt:321 |
| Diálogo de país de contenido | Contenido | ContentSettings.kt:341 |
| Diálogo de idioma de la app (Android ≤ 12) | Contenido | ContentSettings.kt:361 |
| Diálogo de prioridad de proveedores (lista arrastrable) | Contenido | ContentSettings.kt:379 → lista en 488 |
| Diálogo de selecciones rápidas | Contenido | ContentSettings.kt:525 |
| Diálogo de longitud de Mi Top | Contenido | ContentSettings.kt:548 |
| Diálogo de versión IP | Contenido | ContentSettings.kt:586 |
| `PlaybackLogsDialog` | Contenido | ContentSettings.kt:606 |
| Hoja inferior de región de sugerencias | Contenido | ContentSettings.kt:614 |
| Diálogo «¿Está seguro?» de detección línea por línea | Contenido > Romanización | RomanizationSettings.kt:~370 |
| Diálogos de ayuda de proveedor y de modo de traducción | IA | AiSettings.kt:173 y 199 |
| `EnumDialog` ×4 (proveedor, formalidad, modo, idioma destino) | IA | AiSettings.kt:232, 258, 278, 318 |
| Diálogos de clave/URL/modelo (campo de texto) | IA | AiSettings.kt:494, 513, 546, 567 |
| Confirmaciones de borrado de historial (×2) | Privacidad | PrivacySettings.kt:177, 210 |
| Confirmaciones de borrado (descargas, caché de canciones, caché de imágenes) | Almacenamiento | StorageSettings.kt:367, 500, 561 |
| Diálogo «¡Espera!» de tamaño de caché | Almacenamiento | StorageSettings.kt:443 / 513 |
| Selector de carpeta SAF (exportación) | Almacenamiento | StorageSettings.kt:374 |
| Selectores SAF de copia/importación (×8) | Copias de seguridad | BackupAndRestore.kt:220, 233, 258-311 |
| Diálogos de cierre de sesión (YouTube / Spotify / Last.fm) | Cuentas | AccountsScreen.kt:~395, ~451, ~495 |
| Diálogo de acceso a Last.fm (usuario + contraseña) | Scrobbling | LastFMSettingsScreen.kt:~160 |
| 3 diálogos de slider de scrobbling | Scrobbling | LastFMSettingsScreen.kt:~460, ~525, ~590 |
| Diálogo de token de ListenBrainz | Scrobbling | LastFMSettingsScreen.kt:713 |
| Diálogo de aviso de Qobuz | Cuentas > Qobuz | QobuzSettingsScreen.kt:~300 |
| Diálogo de información de «Borrar actualizaciones descargadas» | Update | UpdateSettings.kt:200 |
| Diálogos de Escuchar juntos (nombre de usuario, crear sala, unirse, registros, elegir servidor, usuarios bloqueados) | Escuchar juntos | ListenTogetherSettings.kt:172, 209, 260, 508, 583, 748 |
| Pantalla de suscripción en línea (no es diálogo, sustituye el contenido) | Ajustes raíz | SettingsScreen.kt:77 |

---


---

# 23. CONTROLES MUERTOS, PANTALLAS INALCANZABLES Y PLACEBOS

> El dueño ha prohibido expresamente los controles placebo. Esto es lo que se ha encontrado.
> **No se propone quitar nada** — solo se documenta, con el grado de certeza de cada hallazgo.

## 23.1 PLACEBOS — controles visibles que no hacen lo que dicen

| Qué | Dónde | Evidencia | Certeza |
|---|---|---|---|
| **Visualizador de espectro: no dibuja NADA.** El composable completo es `Box(modifier = modifier)`, un contenedor vacío. Reserva el espacio sobre la barra de progreso y no pinta un solo píxel. Además la preferencia se **siembra ENCENDIDA** en dispositivos que no son de gama baja | `ui/component/SpectrumVisualizer.kt:9-14` · uso `ui/player/Player.kt:2131` · siembra `App.kt:624-625` | El archivo entero son 14 líneas; el cuerpo es un `Box` sin hijos. Hoy **no hay ningún interruptor en Ajustes** para él (`ui/screens/settings/SoundSettings.kt:29` documenta que se quitaron los imports) | **Total** |
| **`applyAudioQuality` es una función vacía.** El selector de calidad de audio de la hoja de dispositivos llama a una función cuyo cuerpo no tiene nada | `echomusic/AudioDeviceBottomSheet.kt:912-914`, llamada en `:818` | Funciona igual porque el resolver lee la preferencia por su cuenta, pero la llamada es engañosa | **Total** |
| **Deslizador cableado que nunca se dibuja.** En la hoja de dispositivos se crea un `sliderState` completo, con callbacks y una animación viva, pero no existe ningún `Slider(` en el archivo | `echomusic/AudioDeviceBottomSheet.kt:632-668` | El volumen real lo mueven los gestos de `:698-718`. El estado y su animación se mantienen vivos para nada | **Total** |
| **Botones ▶ decorativos.** El play centrado sobre las tarjetas de canción y sobre las miniaturas locales **no tiene `onClick`**: parece un botón y no lo es (el toque lo captura la tarjeta entera) | `ui/component/Items.kt:1618-1643`, usos `:584`, `:1238`, `:1485-1508`, `:1510-1533` | Definiciones sin parámetro de clic | **Total** |
| **Contador de «me gusta» de los comentarios: no es un botón** | `ui/screens/CommentSheet.kt:286-301` | Es un `Row` informativo | **Total** |
| **Icono ▶ de los episodios de podcast: no es un botón** | `ui/screens/PodcastScreen.kt:257` | Icono decorativo dentro de la fila | **Total** |
| **«Solo descargas. Desactívalo en Ajustes…» no es pulsable** — el texto dirige a Ajustes pero no lleva a ninguna parte | `ui/screens/OfflineHome.kt:173` | Sin `clickable` | **Total** |
| **El código de sala de Escuchar juntos no se copia al tocarlo**, y los botones «Copiar» solo existen para el anfitrión: **un invitado no puede compartir el código** | `ui/screens/ListenTogetherScreen.kt:714-721`, botones tras `if (isHost)` en `:756` | Verificado | **Total** |
| **Filas de menú «Exportando» / «Exportado» con `onClick = {}`**: se dibujan como opciones pulsables y no responden | `ui/menu/SongMenu.kt:746`/`:756` · `ui/menu/YouTubeSongMenu.kt:615`/`:625` · `ui/menu/PlayerMenu.kt:637`/`:647` · `ui/menu/OldPlayerMenu.kt:428`/`:440` | Intencionalmente informativas, pero visualmente indistinguibles de una acción | **Total** |
| **Tocar una canción de «Relacionados» en la Cola no hace nada** (`onClick = {}`); solo funcionan los dos botones de acción y la pulsación larga | `ui/player/Queue.kt:1646` | Lambda vacía | **Total** |
| **Tocar el vídeo de fondo del artista no hace nada**, pero el `Box` es `clickable`: consume el toque y muestra ondas de pulsación | `ui/screens/artist/ArtistScreen.kt:387-391` · `artistvideo/.../ArtistVideo.kt:126` | Lambda vacía sobre un elemento clicable | **Total** |
| **Tocar una burbuja del chat no hace nada** (solo la pulsación larga responde) | `ui/screens/CommentTogether.kt:250` | Lambda vacía | **Total** |
| **Ramas vacías en Explorar genérico**: si el `browseId` devuelve canciones o vídeos, ni el toque ni la pulsación larga hacen nada | `ui/screens/BrowseScreen.kt:86-88`, `:116-118` | Bloques `{ }` literalmente vacíos | Alta en el código |
| **Auto-foco de la barra de búsqueda muerto**: se crea y se invoca un `focusRequester` que **nunca se aplica a ningún componente**; lanza excepción y se traga en un log | `ui/screens/search/SearchScreen.kt:137`, `:574-579` | Verificado | Alta |
| **La preferencia de «actualizaciones beta» no afecta a la comprobación**: se lee en Ajustes pero el comprobador siempre pide la última versión estable | lectura `ui/screens/settings/UpdateSettings.kt:71` · comprobador `echomusic/updater/echomusicupdater.kt:672-808` | La preferencia no aparece en el comprobador | Media-alta |

## 23.2 PANTALLAS INALCANZABLES — existen, compilan, se traducen, y no hay forma de llegar

| Pantalla | Dónde | Por qué es inalcanzable | Certeza |
|---|---|---|---|
| **Listas de éxitos** (`ChartsScreen`, 365 líneas, ~14 controles) | `ui/screens/ChartsScreen.kt:84` | Su ruta `charts_screen` está **des-registrada** con un comentario en `ui/screens/NavigationBuilder.kt:146`. Cero referencias en el repo | **Total** |
| **Álbumes recién lanzados** (`NewReleaseScreen`) | `ui/screens/NewReleaseScreen.kt` | La ruta `new_release` SÍ está registrada (`NavigationBuilder.kt:142`) pero **nadie la navega**. El Inicio pinta sus propias novedades en línea | **Total** |
| **Commits** (`CommitScreen`) | `echomusic/commitscreen/commitscreen.kt:86` | Ruta `settings/commits` registrada (`NavigationBuilder.kt:548`), cero llamadas. **Además apunta al repositorio upstream ajeno** (`EchoMusicApp/Echo-Music`, `:108`), no al del dueño | **Total** |
| **Escuchar juntos con barra superior** | ruta `listen_together_from_topbar`, `NavigationBuilder.kt:105` | Registrada, nadie navega; solo se compara como ruta actual en `MainActivity.kt:1046`. Su parámetro `showTopBar` **siempre llega apagado** | **Total** |
| **Ajustes de cuenta** (`AccountSettingsScreen`) | `ui/screens/settings/AccountSettingsScreen.kt` | Su ruta `settings/account` está des-registrada (`NavigationBuilder.kt:340`) | **Total** |
| **Integraciones** (`IntegrationScreen`) | `ui/screens/settings/integrations/IntegrationScreen.kt` | Ruta des-registrada (`NavigationBuilder.kt:432`) y cuerpo vacío | **Total** |
| **Diálogo de canción compartida** | `MainActivity.kt:1694-1718` | El estado `sharedSong` **solo se asigna a `null`** (`:1053`, `:1697`, `:1712`); jamás recibe una canción | **Total** |
| **«Importar lista de reproducción»** (`ImportPlaylistDialog`) | `ui/menu/ImportPlaylistDialog.kt` | Su único punto de montaje (`ui/menu/YouTubePlaylistMenu.kt:265`) nunca pone su bandera a verdadero | **Total** |
| **Selector de dispositivo de audio desde el mini reproductor** | `ui/player/MiniPlayer.kt:252`, `:464-465` | La bandera solo se pone a `false`, nunca a `true`. **Solo se llega a la hoja desde la Cola en el diseño ANTIGUO** (`ui/player/Queue.kt:543`) | **Total** |
| **«Tempo y tono» desde el menú del reproductor** | `ui/menu/OldPlayerMenu.kt:212-214` | El diálogo está montado pero su bandera nunca se activa: **no hay entrada «Avanzado» en el menú del reproductor**. Solo se llega desde el menú de la Cola | **Total** |
| **«Seleccionar todo» del menú de lista online** | `ui/menu/YouTubePlaylistMenu.kt:631` | Depende de un parámetro `canSelect` que **ningún punto de llamada del repo pasa como verdadero** | **Total** |
| **Diálogo «Ya está en la lista de reproducción»** | `ui/menu/SongMenu.kt:248` · `AlbumMenu.kt:230` · `YouTubeAlbumMenu.kt:163` · `YouTubePlaylistMenu.kt:285` | Su bandera nunca se activa; la lógica de duplicados se movió al diálogo de añadir | **Total** |
| **Sub-diálogo «Duplicados» de la versión online** | `ui/menu/AddToPlaylistDialogOnline.kt:394-449` | La bandera solo se pone a `false`; además sus dos listas son constantes que nunca se rellenan | **Total** |
| **Rama de descarga de la lista de Caché** | `ui/menu/PlaylistScreenMenus.kt:407`, `:421` | La pantalla siempre pasa el estado «detenido», así que «Eliminar descarga» y «Descargando» no se muestran nunca ahí | **Total** |
| **Comentarios de YouTube** (`CommentSheet`, 437 líneas) | `ui/screens/CommentSheet.kt` | Su único acceso está tras una preferencia **apagada por defecto** (`ui/player/Queue.kt:299-302`). No es código muerto, pero de fábrica es invisible | **Total** |
| **Contador total de comentarios** | `ui/screens/CommentSheet.kt:74`, `:113-119` | El valor se inicializa a nulo y **nunca se asigna** | **Total** |

## 23.3 COMPOSABLES HUÉRFANOS — código de interfaz que nadie usa

Verificado con `grep` sobre todo `app/src`: la única aparición es su propia definición.

| Composable | Archivo:línea | Nota |
|---|---|---|
| **`ui/component/Preference.kt` ENTERO** | `PreferenceEntry:46`, `ListPreference:105`, `EnumListPreference:159`, `SwitchPreference:181`, `EditTextPreference:217`, `SliderPreference:257`, `PreferenceGroupTitle:335` | El sistema de preferencias heredado de InnerTune. Lo sustituyó `Material3SettingsItem` / `Material3SettingsGroup`. **Ninguna pantalla de Ajustes lo usa** |
| **`ui/component/NewMenuComponents.kt` casi entero** | `NewMenuItem:127`, `NewMenuContainer:328`, `NewIconButton`, `NewActionButton`, `NewMenuSectionHeader`, `NewMenuContent` | De este archivo solo viven `NewActionGrid` y `NewAction` (la rejilla de botones grandes de los menús). Unas 200 de sus 340 líneas están muertas |
| **`ui/component/SearchBar.kt` ENTERO** | `TopSearch:66` + su privada `SearchBarInputField:151` | La búsqueda real usa el `SearchBar` de Material 3. Incluye un manejador de «atrás» que nunca se ejecuta |
| **`AppNavigationBar`** | `ui/component/AppNavigation.kt:129-218` | La barra inferior real es `FloatingNavigationToolbar`. Arrastra consigo la preferencia `SlimNavBarKey`, que **no se lee en ningún sitio** |
| **`AutoResizeText`** | `ui/component/AutoResizeText.kt` | Sin referencias |
| **`MoreActionsButton`** | `ui/player/Player.kt:3918` | Envoltorio de `PlayerMenu` sin puntos de llamada |
| **`PlayerMoreMenuButton`** | `ui/player/Player.kt:3960` | Ídem |
| **`FavoriteButton`** | `ui/player/MiniPlayer.kt:949` | Sin referencias |
| **`DeviceSelector`** | `echomusic/AudioDeviceBottomSheet.kt:1226-1278` | ~53 líneas de selección por chips que nadie renderiza |
| **`ConnectionStatusCard`** | `ui/screens/ListenTogetherScreen.kt:565-684` | Tarjeta con 3 botones (Conectar / Desconectar / Reconectar); su parámetro `onReconnect` no tiene implementación en ninguna parte |

## 23.4 ESTADO Y VARIABLES MUERTAS (no se ven, pero indican funciones retiradas)

- `isPortrait` calculado y nunca usado en **12 archivos de menú** (`SongMenu.kt:362`, `YouTubeSongMenu.kt:275`, `AlbumMenu.kt:346`, `ArtistMenu.kt:123`, `YouTubeAlbumMenu.kt:272`, `YouTubeArtistMenu.kt:73`, `PlaylistMenu.kt:443`, `YouTubePlaylistMenu.kt:338`, `SelectionSongsMenu.kt:188`, `PlayerMenu.kt:320`, `QueueMenu.kt:250`, `LyricsMenu.kt:379`) — resto de un diseño responsive retirado.
- `showRomanizationDialog` / `showRomanization` declarados y nunca usados (`ui/menu/LyricsMenu.kt:360`, `:364`) — hubo un diálogo de romanización que desapareció.
- `isBluetoothConnected` calculado y nunca usado (`ui/player/MiniPlayer.kt:251`).
- `clipboardManager`, `currentFormat`, `selectedSongs`, `selectedItems` muertos o ensombrecidos en `ui/player/Queue.kt:193`, `:253`, `:255`, `:256`.
- `notAddedList` sin lector en `ui/menu/SelectionSongsMenu.kt:128` y `:555`.
- `onSelectionChange` de `SongListItem` que ningún llamante pasa (`ui/component/Items.kt:452`).
- En `MainActivity`: `onSearch` (`:825-839`), `query` (`:821`), `focusManager` (`:778`) y 7 imports de auto-actualización — restos de una barra de búsqueda global y de un comprobador automático de actualizaciones ya retirados.
- `LocalIsPlayerExpanded` **nunca se provee** (`MainActivity.kt:2076`), así que `SearchScreen.kt:139` siempre lee «falso».
- `snackbarHostState`, `bottomSheetPageState`, `allLocalItems`, `allYtItems`, `isMoodAndGenresLoading`, `foundInSettings`, `quickPicksSnapLayoutInfoProvider` sin usar en `ui/screens/HomeScreen.kt:579-1069`.
- `scrollBehavior` recibido y **nunca aplicado** en 4 pantallas (`BrowseScreen.kt:50`, `MoodAndGenresScreen.kt:49`, `NewReleaseScreen.kt:49`, `AccountScreen.kt:52`): sus cabeceras no colapsan al desplazar.
- FAB de **aleatorio** y FAB de **crear lista** de la barra flotante: el código está vivo pero `MainActivity.kt:1404-1406` deliberadamente no pasa sus manejadores, así que no se renderizan nunca.

## 23.5 ERRORES REALES ENCONTRADOS DE PASO

No son parte del inventario, pero salieron al leer el código y el dueño querría saberlos.

| Qué pasa | Dónde | Efecto visible |
|---|---|---|
| Se pasa el **ID numérico del recurso** convertido a texto como motivo de expulsión | `ui/menu/PlayerMenu.kt:1167` · `ui/screens/ListenTogetherScreen.kt:231` | El usuario bloqueado ve un número tipo `2131886712` en vez de «Usuario bloqueado por el anfitrión» |
| `connected_users` se llama con un argumento pero el texto no tiene marcador | `ui/menu/PlayerMenu.kt:1548` | Nunca se ve el número de usuarios conectados |
| `max_selection_limit` idem | `ui/component/Lyrics.kt:749` | El aviso no dice cuántas líneas son el máximo |
| El diálogo de borrar una entrada del historial de reconocimiento usa el texto de **borrar una lista de reproducción** | `ui/screens/recognition/RecognitionHistoryScreen.kt:179` | «¿Confirma que quiere eliminar la lista de reproducción «…»?» al borrar un reconocimiento |
| El nombre de cola en la selección múltiple está en inglés y a mano: `"Selection"` | `ui/menu/SelectionSongsMenu.kt:215`, `:236`, `:296`, `:690`, `:711` | El usuario ve «Selection» como título de la cola |
| El texto de confirmación de borrar descargas de una selección inserta el literal `"selection"` | `ui/menu/SelectionSongsMenu.kt:154`, `:606` · `YouTubeSelectionSongMenu.kt:174` | «…de la lista de reproducción «selection»…» |
| `allInLibrary` se calcula con `remember {}` sin claves | `ui/menu/SelectionSongsMenu.kt:85` | El texto «Añadir/Quitar de la biblioteca» no se actualiza tras pulsarlo |
| «Me gusta a todo» nunca cambia a «No me gusta a todo» en el menú de la Cola (el icono sí cambia) | `ui/menu/SelectionSongsMenu.kt:781`, `:787` | Incoherente con los otros dos menús de selección |
| Las barras onduladas **no reciben** `enabled = !esInvitado`, a diferencia de las otras dos | `ui/player/Player.kt:2178`, `:2207` vs `:2162`, `:2275` | Un invitado de Escuchar juntos SÍ puede mover la posición con el estilo ondulado |
| `hidePlayerSlider` («ocultar la barra del reproductor») en realidad oculta la **fila de volumen**, no la barra de progreso | `ui/player/Player.kt:303`, `:2740` | ⚠️ Si el rediseño interpreta el nombre literalmente, romperá el ajuste |
| Cifras de reproducciones **fabricadas** en la pestaña Sugerencias (`2.500.000 / (rank+2)` y `15.000.000 / (rank+8)`) | `ui/screens/search/suggestions/TabNewsSuggestion.kt:323-327`, `:403-407` | Se muestran «X.XM plays» que no vienen de ninguna fuente real |
| La marca **«echo-music»** aparece en el pie de Sugerencias | `ui/screens/search/suggestions/TabNewsSuggestion.kt:244` | Contradice la regla de marca «solo Aura» |
| Dos `println` de depuración en producción | `ui/player/Thumbnail.kt:830`, `:926` | Ruido en el log |
| Tres acciones destructivas de Escuchar juntos (expulsar, bloquear, transferir propiedad) **sin diálogo de confirmación** | `ui/screens/ListenTogetherScreen.kt:1425`, `:1460`, `:1494` | Un toque accidental cede la sala o expulsa a alguien |

## 23.6 TEXTO QUE HOY SE VE EN INGLÉS DENTRO DE LA APP EN ESPAÑOL

Los **1.598 strings de recursos están traducidos al 100 %**. El problema es el texto escrito a mano en
Kotlin, que no pasa por el sistema de traducción.

| Dónde | Texto |
|---|---|
| Pantalla de actualizaciones ENTERA | `app/src/main/res/values/updater_strings.xml` (77 cadenas) **no tiene versión en español**: «Check for update», «Later», «Update», «Install», «Current version», «Released on», «Size», «Can't check for updates», «Download Failed» |
| Reproductor | «Cast» / «Stop casting» (`gms/.../CastButton.kt:138`, `:206`), «Casting» (`MiniPlayer.kt:429`), «Hosting Listen Together» / «Listening Together» (`Thumbnail.kt:610`), mensajes de error de edad y de servidor (`PlaybackError.kt:61`, `:63`) |
| Letras | «Lyrics from <proveedor>» (`Lyrics.kt:1061`) |
| Información de la canción | «<etiqueta> copied» (`ShowMediaInfo.kt:363`) |
| Desfase de la letra | «Reset», «Decrease», «Increase» (`ShowOffsetDialog.kt:180`, `:200`, `:225`) |
| Tono de llamada | «Trim Ringtone», «Select the part of…», «Selected duration…», «Play Preview», «Setting Ringtone…», «Success!», «Failed», «Open Settings», «Close» (`RingtoneTrimmerDialog.kt:94-178`, `RingtoneProgressDialog.kt:36-83`) |
| Escuchar juntos | «How it Works» + 3 pasos (`ListenTogetherScreen.kt:444-464`), «Listen Together Link», «Room Code» (`PlayerMenu.kt:1498`, `:1517`), motivos «Removed by host» / «Rejected by host» que viajan al servidor (`:222`, `:326`, `:337`) |
| Chat de la sala | «Room: {código}», «No messages yet», «Start the conversation!» (`CommentTogether.kt:81`, `:401`, `:407`) |
| Comentarios | «Replies», «Back», «N Replies» (`CommentSheet.kt:317`, `:374`, `:378`) |
| Historial de reconocimiento | «No recognition history», «No results for …» (`RecognitionHistoryScreen.kt:281`, `:305`) |
| Modo Ambiente | «Album Art» (`AmbientModeScreen.kt:138`) |
| Región de sugerencias | «Choose Suggestions Region», «System», «Countries & Regions», «Search», «Selected» (`SuggestionRegionSheet.kt:52`, `:90`, `:119`, `:69`, `:198`) |
| Sugerencias | «Apple Music Top 100», «Trending Artists», «Trending Music Videos», «More», «Previous», «Next», «No suggestions available at the moment.», «Data from Apple Music» (`TabNewsSuggestion.kt`) |
| Selector de dispositivo de audio | «Connected» / «Available», «Volume level», «Bluetooth Device», «USB Audio», «Wired Headphones», «External Speaker», «Phone Speaker», «Failed to load devices: …» (`AudioDeviceBottomSheet.kt:1180`, `:1210`, `:969`, `:989`, `:1075-1084`, `:1040`) |
| Importación CSV | «Preview» (`CsvColumnMappingDialog.kt:86`) |
| Selector de esquinas de portada | «Custom», «Value», «Or adjust Radius» (`ThumbnailCornerRadiusSelector.kt:159`, `:189`, `:209`) |
| Fila de canción | «LOSSLESS», «320KBPS» (`Items.kt:405`, `:419`) |
| Hoja de cuenta | «Close», grupo «Preferences» (`SettingDialoge.kt:98`, `:129`) |
| Acerca de | «DEBUG» (`AboutScreen.kt:410`) |

**Además hay ~90 etiquetas escritas a mano EN ESPAÑOL** (chips del reproductor «Agregar / Compartir /
Descargar / Mix / Audio / Más», todo el onboarding, todo `WelcomeDialog`, toda la puerta de licencia,
todo `PodcastScreen`, todo `SettingDialoge`, «Sonido» del menú del reproductor, «Exportar playlist» /
«Exportar CSV», «Menos de esto», «Reconectar», el diálogo «¿Traducir la letra?»…). Funcionan hoy, pero
**no son traducibles**: si algún día se añade otro idioma, se quedarán en español.

## 23.7 Hallazgos en Biblioteca, Artista, Álbum y Listas

### 8.1 Verificado directamente (grep/lectura propios)

| Hallazgo | archivo:línea | Por qué | Certeza |
|---|---|---|---|
| FAB de alternar local↔online del Artista: `visible = false` literal | ArtistScreen.kt:1084-1092 | `HideOnScrollFAB` hace `visible && isScrollingUp` (HideOnScrollFAB.kt:45) → nunca se dibuja. Comentario :1082 dice que se quitó a petición del usuario | Total |
| Todo el FAB "Play All" del Artista tras `if (false && canPlayAll)`; cD "Play All" hardcoded | ArtistScreen.kt:1103-1211 (guard :1103; cD :1195 y :1205) | Bloque inalcanzable; `canPlayAll` (:1095) y `showLocalFab` (:1079) solo se usan dentro | Total |
| La rama de vista LISTA de `LibraryPlaylistsScreen` es inalcanzable | LibraryPlaylistsScreen.kt:299-425 (lectura :110) | `PlaylistViewTypeKey` solo aparece 3 veces en `app/src` (definición, import, lectura) — nadie lo escribe; `viewType` nunca se asigna. Única pantalla de biblioteca sin `LibraryViewToggle` | Total |
| `LocalSongBadge` nunca se llama | LocalSongScreen.kt:487-515 | grep en `app/src`: solo la definición; es `private` | Total |
| `MetadataChip` nunca se llama | LocalPlaylistScreen.kt:1639-1668 | grep en `app/src`: solo la definición; es `private` | Total |
| La vista lista/cuadrícula del hub Biblioteca solo se cambia desde la pestaña Álbumes | LibraryMixScreen.kt:118 y LibraryAlbumsScreen.kt:90 comparten `AlbumViewTypeKey` | grep de `AlbumViewTypeKey`: 5 coincidencias; la única escritura es el toggle de LibraryAlbumsScreen.kt:189-192 | Total |
| `onLongClick = {}` vacío en las tarjetas de `ArtistAlbumsGridScreen` | ArtistAlbumsGridScreen.kt:116 | Consume la pulsación larga y no abre menú, a diferencia de las demás cuadrículas de artista | Total |
| `onClick = { }` del vídeo de fondo del Artista | ArtistScreen.kt:390 | `ArtistVideo` envuelve todo en `.clickable`; el hero entero es clicable y no hace nada | Total |
| `onClick = { }` del asa de arrastre — **NO es un bug** | LocalPlaylistScreen.kt:779 | El gesto lo maneja `Modifier.draggableHandle()` (:780). Un rediseño que sustituya el `IconButton` DEBE conservar `draggableHandle` | Total |
| `IconButton(onClick = {}, enabled = false)` en `AddOrAddedButton` — **NO es un bug** | AddMusicComponents.kt:101 | Estado "ya añadida" deliberadamente inerte con check y cD "Listo". Puede convertirse en un `Icon` sin botón | Total |
| `NavigationTitle.onPlayAllClick` ("Reproducir todo") sin llamantes en estos ficheros | NavigationTitle.kt:79-95 | Ninguna de las 7 invocaciones (ArtistScreen.kt:773/849/902, AlbumScreen.kt:752/838/880, OnlinePlaylistScreen.kt:401) pasa `onPlayAllClick` | Alta |
| `HideOnScrollFAB.onRecognitionClick` y `onAiPlaylistClick` sin llamantes en estos ficheros | HideOnScrollFAB.kt:61-87 (y :128-154, :195-220) | Las 3 invocaciones (LibrarySongsScreen.kt:502/510, ArtistSongsScreen.kt:210) pasan solo `icon` y `onClick` | Alta |

### 8.2 Reportado por los trabajadores (con evidencia)

| Hallazgo | archivo:línea | Certeza |
|---|---|---|
| Estados vacíos literalmente vacíos en `LibraryPlaylistsScreen` (`item { }` sin cuerpo): sin listas propias o sin resultados de búsqueda NO se muestra nada | LibraryPlaylistsScreen.kt:406-408, :541-543 | Total |
| Duplicado muerto de las 6 tarjetas de auto-listas dentro de la rama lista | LibraryPlaylistsScreen.kt:333-388 | Total |
| 5 objetos `Playlist` construidos con `UUID.randomUUID()` en cada recomposición y nunca usados | LibraryMixScreen.kt:129-177 · LibraryPlaylistsScreen.kt:140-188 | Total |
| Rama `is Artist ->` inalcanzable (la lista iterada es `nonArtistItems`) | LibraryMixScreen.kt:521-562, :819-843 (filtro :230) | Total |
| `AlbumScreen` calcula `downloadState` y no lo lee nunca; no tiene botón de descarga propio | AlbumScreen.kt:222-245 | Total |
| `transparentAppBar` calculado y nunca usado en `AlbumScreen` (la barra es siempre transparente) | AlbumScreen.kt:253-257 vs :1114 | Total |
| `scrollBehavior` recibido y nunca conectado a ningún `TopAppBar` | ArtistScreen.kt:158 · ArtistItemsScreen.kt:80 · ArtistSongsScreen.kt:65 · AlbumScreen.kt:147 · LocalPlaylistScreen.kt:180 · OnlinePlaylistScreen.kt:156 · AutoPlaylistScreen.kt:130 · CachePlaylistScreen.kt:116 · TopPlaylistScreen.kt:116 | Total |
| Modo multiselección de `ArtistAlbumsScreen` completo pero inalcanzable (`inSelectMode` nunca se pone a true) | ArtistAlbumsScreen.kt:90-103 | Total |
| `SnackbarHost` montado sin ningún emisor | ArtistScreen.kt:1214-1219 · ArtistAlbumsScreen.kt:190-195 · OnlinePlaylistScreen.kt:591-594 | Total |
| `onStartSearch` de `LocalPlaylistHeader` declarado y nunca invocado (la cabecera no tiene botón de búsqueda) | LocalPlaylistScreen.kt:1020, pasado :616 | Total |
| `playlistLength` y `downloadState` duplicados y muertos en el cuerpo de `LocalPlaylistScreen`; el `collect` de :294-308 corre sin lector | LocalPlaylistScreen.kt:196-199, :271-309 | Total |
| `hideExplicit` declarado y no usado (el filtrado lo hace el ViewModel) | AutoPlaylistScreen.kt:163 · CachePlaylistScreen.kt:138 | Total |
| `mutableSongs` escrito y nunca leído | AutoPlaylistScreen.kt:148-151, :230-234 · TopPlaylistScreen.kt:129, :193-197 | Total |
| `CachePlaylistScreen` pasa `downloadState = STATE_STOPPED` fijo: "Descargando" y "Eliminar descarga" son inalcanzables ahí y el ítem siempre dice "Descargar" | CachePlaylistScreen.kt:643 | Total |
| Enhanced Shuffle incompleto: Álbum, En caché y Mi Top tienen memoria pero NO pintan la marca "ya reproducida" ni el chip | AlbumScreen.kt:770-776 · CachePlaylistScreen.kt:274-279 · TopPlaylistScreen.kt:329-335 (vs AutoPlaylistScreen.kt:417, :888-896) | Total |
| `gridItemSize` leído pero ignorado (las columnas se calculan con `maxWidth / 152.dp`) | ArtistAlbumsScreen.kt:88, :112 · ArtistItemsScreen.kt:92, :238 | Total |
| `haptic` declarado y nunca usado → la pulsación larga en artistas/álbumes/listas de biblioteca no vibra; en canciones sí | LibraryArtistsScreen.kt:87 · LibraryAlbumsScreen.kt:84 · LibraryPlaylistsScreen.kt:106 | Total |
| Estado vacío de Álbumes usa `albums.isEmpty()` pero pinta `filteredAlbums…`: con `HideExplicitKey` ON y todo explícito la pantalla queda en blanco sin mensaje | LibraryAlbumsScreen.kt:221/281 vs :231-235/291-295 | Alta |
| Filas de Novedades con `playId` en blanco: parecen tocables y no hacen nada, y no tienen botón ▶ | ReleaseRadarScreen.kt:132 vs :137, :176 | Total |
| Novedades no tiene pull-to-refresh pese a lo prometido en el plan (`docs/superpowers/plans/2026-06-15-echo-7-features.md:616`) | ReleaseRadarScreen.kt (fichero completo) | Total |
| `ArtistAlbumsGridScreen` usa un `object` global mutable (`ArtistSectionBuffer`) como transporte: no sobrevive a la muerte del proceso → título y cuadrícula vacíos al restaurar | ArtistAlbumsGridScreen.kt:47-50, escrito en ArtistScreen.kt:914-915 | Alta (no probado en dispositivo) |
| Parámetros `initialTextFieldValue` / `allowSyncing` de `LibraryPlaylistsScreen` nunca usados; `isLoggedIn` calculado y nunca usado | LibraryPlaylistsScreen.kt:102-103, :204-207 | Alta |
| Cabecera "Listas de reproducción" del hub encabeza una sección que contiene listas Y álbumes | LibraryMixScreen.kt:464/779 (filtro :230) | Total |
| Entradas duplicadas del hub: "Local" es chip y botón; "Álbumes favoritos" duplica el filtro *Me gusta* de Álbumes | LibraryScreen.kt:83 vs LibraryMixScreen.kt:407/729; LibraryMixScreen.kt:386/708 | Alta — NO borrar sin decisión explícita: hoy son rutas reales |

### 8.3 Textos sin traducir / hardcoded que el usuario ve

| Texto | archivo:línea |
|---|---|
| "Toggle sort order", "Descending"/"Ascending", "Show sort options", "Expanded"/"Collapsed" (inglés) | SortHeader.kt:85, :101, :131-132 |
| "Lock playlist" / "Unlock playlist" (inglés) | LocalPlaylistScreen.kt:647 |
| "Playlists" como cabecera de sección (inglés) | LibraryPlaylistsScreen.kt:397, :532 |
| "Play All" (inglés, cD del FAB muerto) | ArtistScreen.kt:1195, :1205 |
| "Explicit" (inglés, cD) | OnlinePlaylistScreen.kt:874 |
| "Unknown" (inglés, nombre de artista sin datos) | ArtistScreen.kt:452-460 |
| "N Tracks", "Xh Ym" concatenados en inglés dentro de un texto en español | AlbumScreen.kt:358, :363, :365 |
| "Cache Songs" (inglés, título de la cola del reproductor) | CachePlaylistScreen.kt:316 |
| Descripciones de "Acerca de" generadas en inglés | AlbumScreen.kt:688-692 · LocalPlaylistScreen.kt:1608-1614 · AutoPlaylistScreen.kt:900-905 · CachePlaylistScreen.kt:688-694 · TopPlaylistScreen.kt:789 |
| "Aleatorio mejorado · X/Y reproducidas" (español hardcoded, no en `values-es`) | EnhancedShuffleIndicator.kt:79 |
| "Ya reproducida en aleatorio" (español hardcoded, cD) | Items.kt:498 |
| "Traer los últimos cambios de YouTube Music" (español hardcoded) | PlaylistScreenMenus.kt:281 |
| "Sincronizando con YouTube Music…" (español hardcoded, Toast) | AutoPlaylistScreen.kt:361-365 |
| `− Título — Artista` / `+ Título — Artistas` (formato hardcoded) | AiModifyPlaylistDialog.kt:219, :233 |
| "Artista · Año" (formato hardcoded) | ReleaseRadarScreen.kt:162 |

### 8.4 Accesibilidad: controles sin etiqueta

- `contentDescription = null` en prácticamente todos los ⋯; todas las flechas atrás (FavoriteAlbumsScreen.kt:68, LocalSongScreen.kt:348, ReleaseRadarScreen.kt:83 y las 11 pantallas con `backToMain`); los dos iconos de `LibraryViewToggle` (LibraryViewToggle.kt:56); el FAB principal de `HideOnScrollFAB` (:95/:162/:229); el ✕ de los chips de carpeta (LocalSongScreen.kt:1153).
- `contentDescription = ""` (cadena vacía) en los tres chips de cierre de sección: LibrarySongsScreen.kt:201, LibraryArtistsScreen.kt:113, LibraryAlbumsScreen.kt:112.

### 8.5 Fuera de alcance (riesgo de pérdida silenciosa)

Los ficheros `ui/menu/*.kt` no formaban parte del encargo. Aquí solo se inventarían `LocalPlaylistMenu`, `AutoPlaylistMenu`, `TopPlaylistMenu`, `CachePlaylistMenu` y `CustomThumbnailMenu` porque se cablean literalmente dentro de estas pantallas. **NO están inventariados**: `SongMenu`, `AlbumMenu`, `ArtistMenu`, `PlaylistMenu`, `SelectionSongMenu`, `YouTube*Menu`, `AddToPlaylistDialog`, `CreatePlaylistDialog`, `AiPlaylistDialog` — decenas de acciones. Tampoco `ui/component/Items.kt` (filas/tarjetas reales: icono "en biblioteca", ♥, insignias LOSSLESS/320KBPS, tamaño de fichero, ▶ superpuesto, `SwipeToSongBox`).

## 23.8 Hallazgos en Ajustes

## A. Código muerto confirmado (certeza ALTA)

| Qué | Dónde | Evidencia |
|---|---|---|
| **`ui/component/Preference.kt` entero — 7 composables sin un solo invocador** | Preference.kt:46, 105, 166, 181, 217, 257, 335 | `grep -rn "PreferenceEntry\|SwitchPreference\|ListPreference\|EditTextPreference\|SliderPreference\|PreferenceGroupTitle" app/src/main/kotlin/` fuera del propio archivo devuelve **0 resultados**. Todas las pantallas usan `Material3SettingsGroup`/`Material3SettingsItem`. El rediseño NO debe partir de este archivo. |
| **`IntegrationScreen.kt` — pantalla vacía y sin ruta** | integrations/IntegrationScreen.kt:34-40 (cuerpo `Column { }` vacío) | La ruta `settings/integrations` fue **de-registrada a propósito** en `NavigationBuilder.kt` (comentario en la línea ~468: «IA hygiene (F0): "settings/integrations" (IntegrationScreen) de-registered — empty screen body, no navigate() caller»). Solo dibuja una barra superior con Atrás. |
| **Ruta `settings/commits` registrada pero sin ningún invocador** | NavigationBuilder.kt:548 | `grep -rn "settings/commits"` solo aparece en `NavigationBuilder.kt`. Inalcanzable desde la UI. |
| **Conmutador «Reproductor de cristal» inoperable** | GlassEffectSettings.kt:309-327 | `enabled = false`, `onCheckedChange = null`, `checked = false` fijos. El propio comentario del código lo dice: nada llama a `GlassEffectConfig.isEnabledFor(GlassComponent.PLAYER)`. **No es placebo engañoso** — está etiquetado «Aún no disponible…» — pero es una fila que no hace nada. |
| **Import muerto `EnableDynamicIconKey`** | AppearanceSettings.kt:70 | Importado y jamás usado en el cuerpo; la clave (`enableDynamicIcon`, PreferenceKeys.kt:223) no aparece en ningún otro `.kt`. Rastro de un conmutador de icono dinámico eliminado. |
| **Import muerto `Slider`** | SoundSettings.kt:15 | Importado, ningún `Slider(` en el archivo. |

## B. Claves de preferencia escritas pero nunca leídas fuera de su pantalla (certeza ALTA sobre el grep, MEDIA sobre el impacto)

| Clave | Escrita en | Lectura fuera | Veredicto |
|---|---|---|---|
| `CustomAccentColorKey` (`customAccentColorArgb`, PreferenceKeys.kt:235) | ThemeScreen.kt:207 | **ninguna** en todo `app/` | **Redundante, no placebo.** El color personalizado SÍ se aplica, pero por otra vía: `CustomAccentSection.onApply` llama también a `handleColorSelection(color, ThemePreset.NONE)`, que escribe `SelectedThemeColorKey`, leída por `MainActivity.kt:661`. Esta clave es solo un registro paralelo que nadie consulta. |
| `DensityScaleKey` (`density_scale_factor`, PreferenceKeys.kt:248) | AppearanceSettings.kt:261 | **ninguna** como clave DataStore | **Vestigial, no placebo.** El control de «Densidad de pantalla» funciona porque escribe/lee SharedPreferences con el literal `"density_scale_factor"` (AppearanceSettings.kt:259 y 269), que sí consume `com/dpi/DensityScaler.kt:18`. La clave DataStore es un duplicado inerte. |
| `CipherManualRefreshAtKey` | YoutubeDecryptionSettings.kt:89 | ninguna | Marca de tiempo local para el enfriamiento del botón. Correcto. |
| `YtmAutoSyncFreqDaysKey` | YtmSyncScreen.kt:208 | ninguna | **NO es placebo** (verificado): `applyFreq` llama a `YtmAutoSyncWorker.schedule(context, days)` en YtmSyncScreen.kt:219, así que la frecuencia se materializa en WorkManager, no en la lectura de la clave. |

**Conclusión sobre placebos: no encontré ningún conmutador de Ajustes que mienta al usuario.** El único control que no hace nada («Reproductor de cristal») está explícitamente rotulado como no disponible y deshabilitado. Certeza: **alta** para las pantallas cubiertas; ver INCIERTO para lo que no pude cerrar.

## C. Claves declaradas en `PreferenceKeys.kt` que no aparecen en NINGÚN `.kt` (sin UI, sin lector) — certeza ALTA

Sin interfaz asociada, así que no son controles placebo, pero son basura que confunde al leer el archivo de claves:

`AutoPlaylistSongSortDescendingKey` · `AutoPlaylistSongSortTypeKey` · `CanvasDefaultOffAppliedKey` · `CustomDensityScaleKey` (249) · `DeveloperModeKey` (282) · `DiscordAvatarKey` · `DiscordInfoDismissedKey` · `DiscordNameKey` · `DiscordUsernameKey` · `EnableDynamicIconKey` (223) · `EnableListenTogetherKey` (412) · `EnablePlayerSwipeKey` (509) · `EqAudiophileDefaultAppliedKey` · `IsFirstRunKey` · `LastAlbumSyncKey` · `LastArtistSyncKey` · `LastLibSongSyncKey` · `LastLikeSongSyncKey` · `LastPlaylistSyncKey` · `MiniPlayerOutlineKey` (247) · `SelectedYtmPlaylistsKey` · `SlimNavBarKey` (264) · `TranslateLyricsKey` (726)

(23 claves. Verificado con un barrido de las 343 claves declaradas contra todo `app/src/main/kotlin`.)

## D. Pantallas de ajustes SIN acceso desde el menú de Ajustes (certeza ALTA)

| Pantalla | Ruta | Cómo se llega realmente |
|---|---|---|
| `ListenTogetherSettings` | `settings/integrations/listen_together` | Solo desde `ui/screens/ListenTogetherScreen.kt:424`. La fila «Escuchar juntos» de Ajustes (`SettingsScreen.kt:235`) navega a `Screens.ListenTogether.route` = `"listen_together"`, que es la **pantalla principal**, no estos ajustes. Sin embargo, 31 entradas del buscador de Ajustes sí apuntan aquí. |
| `AccountSettingsScreen` | `account` | No está en el menú raíz (que va a `settings/accounts`). |
| `YtmSyncScreen` | `settings/ytm_sync` | Desde Cuentas, Copias de seguridad, onboarding y `MainActivity.kt:1115`. |
| `QobuzSettingsScreen` | `settings/qobuz` | Solo desde `AccountsScreen.kt:327/339`. |
| `YoutubeDecryptionSettings` | `settings/youtube_decryption` | Solo desde `PlayerSettings.kt:1156`. |
| `AxionEqScreen` | `settings/equalizer` | Desde `SoundSettings.kt:73`, `PlayerMenu.kt:838` y `Player.kt:2100`. |
| `AutoEqScreen` | `settings/sound/autoeq` | Solo desde `SoundSettings.kt:79`. |
| `UptimeScreen` | `uptime` | Solo desde `ContentSettings.kt:1361`. |
| `SettingDialoge` | (diálogo) | Solo desde `MainActivity.kt:1309`. |
| `BackgroundReliabilityDialog` | (diálogo) | Solo desde `MainActivity.kt:1772`. |

## E. Otros detalles que el rediseño debe conocer

- **`SoundSettings` no tiene barra superior ni Atrás propios** (SoundSettings.kt:55-113 solo dibuja una `Column`). Depende del contenedor.
- **Cadenas sin traducir visibles al usuario**: `Aura Hi-Res Update`, `System update`, `Automatic update check` (+ subtítulo), `Show a notification when a new update is found`, `Up to date`, `Update`, `Version %s` (todas en `UpdateSettings.kt`); el `OK` de `android.R.string.ok` en todos los diálogos; el grupo «Import Data» y la descripción «.jrpl.json exported from the desktop app» en `BackupAndRestore.kt`; «Preferences» en `SettingDialoge.kt`; el texto de aviso de Saavn en `PlayerSettings.kt:425` está en inglés dentro de un diálogo en español.
- **Mucho texto está hardcoded en español** y no pasa por recursos: los grupos «Ahorro de datos», «Avanzado», «Inicio y descubrimiento», «Diagnóstico», «Rendimiento», «Pantalla grande», «Ecualizador», «Volumen», «Cuentas», y decenas de títulos/descripciones marcados arriba. Un rediseño que reorganice pantallas debe moverlos tal cual o perderá el idioma.

---


---

# 24. LO QUE NO SE HA PODIDO DETERMINAR

Este inventario prefiere decir «no lo sé» a inventarse una respuesta. Estas son las lagunas conocidas.

## 24.1 Lagunas del método

1. **La columna «Frecuencia» es un juicio de producto, no una medida.** En el repositorio **no hay
   telemetría de uso**. Se ha estimado por la posición del control (superficie vs. tercer nivel de menú)
   y por la naturaleza de la acción. Si el dueño tiene datos reales de uso, deberían sustituir a esta columna
   antes de decidir qué baja a un menú.
2. **Los conteos agrupan por composición.** Un mismo botón ⋯ que aparece en 24 pantallas se cuenta una
   vez (en su menú), no 24. Contarlo por instancias daría una cifra mucho mayor y menos útil.
3. **No se ha ejecutado la app.** Todo está leído del código. Algunas condiciones (`isTvOrCar`,
   estrangulamiento térmico, `RecognizerIntent` ausente) solo se pueden confirmar en dispositivo.
4. **Los textos de terceros no se han inventariado**: el WebView de inicio de sesión de Google, el
   contenido remoto de Gumroad, el documento de términos (`assets/legal/TERMINOS_Y_CONDICIONES.md`) y
   los textos de la guía de Apple Music (viven en el módulo `:migration`, fuera de `app/`).

## 24.2 Dudas concretas sin resolver

- **Selector de dispositivo de audio**: su único acceso vivo está en la barra de la Cola del **diseño
  antiguo** (`ui/player/Queue.kt:543`). No se ha podido confirmar si ese botón se oculta para un invitado
  de Escuchar juntos: el botón vecino del temporizador sí lo comprueba, este no.
- **`ShowCommentButton`** está apagado por defecto. No se ha verificado si alguna migración lo enciende.
- **`useNewPlayerDesign`**: no se ha confirmado si el interruptor sigue expuesto en Ajustes. Si no lo
  está, toda la rama del diseño antiguo (incluido el selector de audio) sería inalcanzable en la práctica.
- **`ChartsScreen`, `NewReleaseScreen` y `CommitScreen`** están inalcanzables hoy. No se sabe si es
  deliberado o si el dueño quiere recuperarlas. Se marcan como muertas, **no como eliminables**.
- **`AlbumMenu` no ofrece «No recomendar»** y `YouTubeAlbumMenu` sí. No se ha podido determinar si es
  intencional (los álbumes locales no alimentan recomendaciones) o una omisión.
- **Índices cruzados en Estadísticas**: `ui/screens/StatsScreen.kt:278` y `:287` indexan una lista con la
  posición de otra distinta. Podría mostrar la canción equivocada o reventar. No verificado en ejecución.
- **`album.songCountListened!!`** (`ui/screens/StatsScreen.kt:368`) es una aserción no nula sobre un campo
  que puede ser nulo. No verificado si algún álbum lo devuelve vacío.
- **Formas plurales en español** (`%d de canciones`) están definidas, pero no se ha comprobado que Android
  elija la forma correcta en todos los valores.
- **Reordenación TV/mando**: `ListenTogetherScreen` y el chat **no contienen ninguna marca de foco de TV**,
  lo que sugiere que no están adaptados, pero no se ha confirmado leyendo la capa de navegación de TV.

## 24.3 Dudas concretas en Ajustes

1. **Números de línea de los diálogos de slider de Last.fm** (`LastFMSettingsScreen.kt:~460, ~525, ~590`): confirmé que existen tres bloques de diálogo con botones Restablecer/Cancelar/OK en las líneas 467/477/485, 532/542/550 y 597/607/615, pero no verifiqué a cuál de los tres ajustes (duración mínima, % de retraso, minutos) corresponde cada bloque.
2. **`AppearanceSettings.kt` líneas 1-960**: son declaraciones de `rememberPreference` y helpers; asumí que no dibujan controles porque el primer `Material3SettingsGroup` está en la línea 970. No leí ese bloque línea a línea.
3. **Contenido exacto de los diálogos enum** (qué opciones lista cada uno) para: estilo de fondo del minirreproductor, estilo de fondo del reproductor, colores de botones, estilo de barra, calidad de sonido y calidad de descarga. Las etiquetas de las opciones existen en recursos (`default_style`, `gradient`, `blur`, `live_mesh`, `apple_music`, `player_background_liquid_glass`, `squiggly`, `wavy`, `slim`, `primary_color_style`, `tertiary_color_style`…), pero no mapeé una a una cada opción con su enum.
4. **`UptimeScreen`**: los proveedores concretos que lista (número de filas) se generan en tiempo de ejecución; solo documenté las dos secciones.
5. **`AboutScreen`**: cada `AboutSectionCard` contiene varias `FeatureRow` informativas (definidas alrededor de `AboutScreen.kt:339`). No enumeré cada viñeta individual porque ninguna es interactiva.
6. **`ListenTogetherSettings`**: las líneas exactas de los campos dentro de los diálogos «Crear sala» / «Unirse» son aproximadas a ±3 líneas.
7. **Frecuencia de uso**: la columna «Frecuencia» es un juicio mío sobre el patrón de uso típico, no un dato medido.
8. **Siete agentes de inventario en paralelo no devolvieron resultado a tiempo**; estas tablas están hechas con extracción directa (grep verificado + lectura de los bloques relevantes). Los números de línea de los controles principales están verificados uno a uno; los marcados con `~` son aproximados.

---

# 25. ANEXO — Superficies FUERA de la interfaz Compose

Estas superficies no se rediseñan con Compose, pero **exponen funciones de la app** y forman parte del
producto. Si el rediseño cambia iconografía, colores o nombres, hay que decidir qué pasa con ellas.

## 25.1 Widgets de pantalla de inicio (4)

| Widget | Controles | Layout |
|---|---|---|
| **Reproductor de música** | Carátula (abre la app), título, artista, anterior, reproducir/pausar, siguiente, me gusta, barra de progreso | `res/layout/widget_music_player.xml` · `widget/MusicWidgetReceiver.kt:16` |
| **Reproductor compacto (cuadrado)** | Carátula, reproducir/pausar | `res/layout/widget_compact_square.xml` |
| **Reproductor compacto (ancho)** | Carátula, título, artista, reproducir/pausar, me gusta | `res/layout/widget_compact_wide.xml` |
| **Tocadiscos** | Carátula, anterior, reproducir/pausar, siguiente | `res/layout/widget_turntable.xml` · `widget/TurntableWidgetReceiver.kt:15` |
| **Listas de reproducción** | Mini reproductor (5 controles) + rejilla de tarjetas que navegan a `auto_playlist/liked`, `auto_playlist/downloaded`, `top_playlist/{id}`, `local_playlist/{id}`, `online_playlist/{id}` | `res/layout/widget_playlist.xml` · `widget/PlaylistWidgetReceiver.kt:22` · rutas `MainActivity.kt:1818-1838` |
| **Reconocer música** (3 tamaños) | Botón de micrófono, carátula, título, artista | `res/layout/widget_recognizer_{tiny,compact,wide}.xml` · `widget/MusicRecognizerWidgetReceiver.kt:51` |

⚠️ El widget **Tocadiscos** menciona «me gusta» en su descripción pero **su layout no cablea ese botón**.

## 25.2 Mosaico de Ajustes rápidos

**«Reconocimiento»** — `widget/RecognitionTileService.kt:16` (manifiesto `:311-320`) →
`recognition/RecognitionLaunchActivity.kt:29`.

## 25.3 Accesos directos del icono de la app (pulsación larga en el lanzador)

| Etiqueta (ES) | Destino | Definición |
|---|---|---|
| **Buscar** | `search_input` | `res/xml/shortcuts.xml` → `MainActivity.kt:1801-1814` |
| **Biblioteca** | `library` | ídem |

No hay accesos directos dinámicos.

## 25.4 Botones de la notificación de reproducción y de Android Auto

`playback/MusicService.kt:2632-2681`:

| Etiqueta (ES) | Comando |
|---|---|
| **Me gusta** / **Quitar me gusta** | `CommandToggleLike` (`:2646`) |
| **Repetición desactivada** / **Repetir canción actual** / **Repetir cola** | `CommandToggleRepeatMode` (`:2667`) |
| **Activar aleatorio** / **Desactivar aleatorio** | `CommandToggleShuffle` (`:2673`) |
| **Iniciar radio** | `CommandToggleStartRadio` (`:2678`) |

## 25.5 Android Auto / Automotive

`playback/MusicService.kt:245` (`MediaLibraryService`) + `res/xml/automotive_app_desc.xml`.
El árbol de exploración tiene 4 categorías (canciones, artistas, álbumes, listas). En «listas» se
anteponen **Canciones que me gustan** y **Canciones descargadas**, y cada carpeta antepone un ítem
**Aleatorio** (`playback/MediaLibrarySessionCallback.kt:722-755`).

## 25.6 Otras entradas del manifiesto

- `ui/screens/CrashActivity` — proceso `:crash`, no exportada (`AndroidManifest.xml:65`).
- `MainActivityAlias` (habilitada, con `LEANBACK_LAUNCHER` para Android TV) y `MainActivityStatic`
  (deshabilitada — icono alternativo), `AndroidManifest.xml:208-231`.
- `recognition/RecognitionLaunchActivity` (`:295`).
- `MainActivity` es `singleTask` con **9 filtros de intención**: enlace verificado de Escuchar juntos,
  `echomusic://listen`, `echomusic://tidal-callback`, enlaces de YouTube y youtu.be, `vnd.youtube*`,
  y `ACTION_SEND text/plain`.
- ⚠️ `MEDIA_PLAY_FROM_SEARCH` está declarado en 3 filtros del manifiesto pero
  **`handleDeepLinkIntent` nunca lee la consulta de voz** (`MainActivity.kt:1795-1880`): una petición de
  voz con texto y sin `data` solo abre la app.

**No hay módulo de Wear OS.**

---

*Documento generado leyendo el código fuente de la rama `feature/migration`. Todas las referencias
`archivo:línea` están verificadas contra el código en ese momento; si el código se mueve, las líneas
cambian pero los nombres de composable siguen siendo válidos.*
