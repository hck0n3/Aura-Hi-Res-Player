# Echo Music — 7 nuevas funciones · Diseño

- **Fecha:** 2026-06-15
- **Rama:** feat/jr-dsp-and-android-improvements
- **App:** Echo Music 5.1.92 (versionCode 512)
- **Namespace real:** `iad1tya.echo.music` (los directorios fuente usan `com/music/echo/...` pero el `package` declarado es `iad1tya.echo.music.*`)
- **i18n:** la app está forzada a español. Toda cadena visible va en `values/strings.xml` **y** `values-es/strings.xml`.

## Resumen

Siete funciones, construidas por fases (quick wins → pesadas). Cada fase es compilable y probable de forma aislada. Gran parte de la infraestructura ya existe en el repo; varias funciones son "exponer/extender lo existente + UI" más que código nuevo.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|------|----------|
| Orden | Plan único por fases incrementales, quick wins primero |
| #3 Playlists | Agregar playlist mete sus canciones a la Biblioteca (Canciones) |
| #2 Spotify | Seguir local (bookmark) **+** suscribir canal en YouTube Music |
| #6 Fuente novedades | Combinar YT Music + Spotify (dedupe) |
| #4 Álbumes favoritos | Pantalla **nueva separada** (no solo el filtro LIKED existente) |
| #6 Estrategia | WorkManager **semanal (viernes)** + notificación |

## Orden de fases

1. #7 Búsqueda por voz
2. #4 Álbumes favoritos (pantalla nueva)
3. #5 Predeterminar enlaces YouTube Music
4. #1 Widget disco de vinilo
5. #2 Migrar artistas seguidos de Spotify
6. #3 Canciones de playlist → Biblioteca
7. #6 Lista de novedades (Release Radar)

---

## Fase 1 · #7 Búsqueda por voz (estilo YouTube)

**Objetivo:** botón de micrófono en la barra de búsqueda; dicta → texto → busca.

**Infra existente:** `SearchScreen.kt` ya tiene el campo de búsqueda con `leadingIcon` y `trailingIcon` (`IconButton`), estado `query: TextFieldValue`, y `onSearch(String)`. Drawable `ic_widget_mic` ya existe (se usará uno propio de búsqueda para no acoplar al widget recognizer).

**Cambios:**
- `SearchScreen.kt`: `rememberLauncherForActivityResult(StartActivityForResult)` lanzando `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (extra `EXTRA_LANGUAGE` = locale es). Resultado → `query = TextFieldValue(texto)` y `onSearch(texto)`.
- Mostrar `IconButton` de mic en `trailingIcon` cuando `query` está vacío (cuando hay texto, se mantiene la "X" de limpiar).
- Manejar dispositivo sin reconocedor: `try/catch ActivityNotFoundException` → toast "Reconocimiento de voz no disponible".
- Drawable nuevo `ic_search_mic.xml`.

**Sin permiso `RECORD_AUDIO`** — la Activity del sistema gestiona el micrófono.

**Archivos:** `ui/screens/search/SearchScreen.kt`, `res/drawable/ic_search_mic.xml`, strings (es + default).

**Verificación:** abrir búsqueda → tocar mic → dictar → el texto entra y se ejecuta la búsqueda.

---

## Fase 2 · #4 Álbumes favoritos (pantalla nueva separada)

**Objetivo:** apartado dedicado de álbumes favoritos, independiente de `LibraryAlbumsScreen`.

**Infra existente:** `AlbumEntity` ya tiene `likedDate`/`bookmarkedAt`/`inLibrary`. El DAO ya expone `albumsLikedByCreateDateAsc/NameAsc/YearAsc/SongCountAsc/LengthAsc/PlayTimeAsc()` (y sus desc). Componentes `LibraryAlbumGridItem` / `LibraryAlbumListItem` reutilizables.

**Cambios:**
- Nueva `FavoriteAlbumsScreen.kt` + `FavoriteAlbumsViewModel` que consume los `albumsLikedBy*` existentes (orden + grid/list reutilizando los componentes y el patrón de `LibraryAlbumsScreen`).
- Ruta de navegación nueva + entrada visible (en el landing de Biblioteca o como ítem de Inicio).
- Reutiliza ordenamiento/preferencias existentes; no toca el DAO.

**Archivos:** `ui/screens/library/FavoriteAlbumsScreen.kt` (nuevo), `viewmodels/FavoriteAlbumsViewModel.kt` (nuevo), `ui/screens/NavigationBuilder.kt`, punto de entrada (Library/Home), strings.

**Verificación:** marcar álbum como favorito → aparece en la pantalla nueva; quitar favorito → desaparece.

---

## Fase 3 · #5 Predeterminar enlaces de YouTube Music

**Objetivo:** sección en Ajustes para que Echo sea la app por defecto que abre enlaces de YouTube Music; al compartir una canción, que abra en Echo.

**Infra existente:** el `AndroidManifest.xml` principal ya tiene `intent-filter` con `autoVerify="true"` para `music.youtube.com`, `youtube.com`, `m.youtube.com`, `youtu.be`, y esquemas `vnd.youtube`. Es decir, los App Links ya están declarados.

**Realidad de Android:** una app **no puede** forzarse como handler por defecto vía código. Lo correcto: mostrar el estado y llevar al usuario a la pantalla del sistema "Abrir por defecto".

**Cambios:**
- Nueva sección en Ajustes "Abrir enlaces": estado actual de verificación de dominios (Android 12+: `DomainVerificationManager`) + botón que lanza `Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS` con `package:` (fallback `ACTION_APPLICATION_DETAILS_SETTINGS`).
- Manejar `ACTION_SEND` (texto/URL compartido) además de `VIEW`: añadir `intent-filter` de `SEND` (mimeType `text/plain`) a la Activity de deep-link, y parsear URLs de YT Music dentro del texto compartido.
- Reutiliza el parser de enlaces existente (`[LINK_PARSE_DEBUG]` ya está en `SearchScreen`/deep-link).

**Archivos:** `ui/screens/settings/SettingsScreen.kt` o `ContentSettings.kt` (entrada), nueva sección/screen de "Abrir enlaces", `AndroidManifest.xml` (filtro SEND), handler de intent compartido, strings.

**Verificación:** Ajustes → Abrir enlaces → botón abre pantalla sistema; compartir un link `music.youtube.com/...` desde otra app → Echo aparece y reproduce.

---

## Fase 4 · #1 Widget de disco de vinilo

**Objetivo:** el widget turntable muestra un disco de vinilo **vacío** mientras no se reproduce nada; al reproducir, cambia a la **portada circular** (comportamiento actual).

**Infra existente:** `EchoMusicWidgetManager` ya genera `circularAlbumArt` con `getCircularBitmap(...)`, cachea arte, y llama `createTurntableRemoteViews(circularAlbumArt, ...)` para los widgets turntable. `TurntableWidgetReceiver` muestra el layout default (`widget_turntable.xml`) cuando el servicio no corre.

**Cambios:**
- Nuevo drawable `ic_vinyl_empty.xml` (disco de vinilo sin portada).
- `EchoMusicWidgetManager.createTurntableRemoteViews(...)`: si `artworkUri == null` (o no hay reproducción activa) → `setImageViewResource(R.id.<art>, R.drawable.ic_vinyl_empty)`; si hay arte → `setImageViewBitmap` con la portada circular (igual que hoy).
- `widget_turntable.xml`: imagen inicial = `ic_vinyl_empty` (estado en reposo antes de que arranque el servicio).

**Archivos:** `widget/EchoMusicWidgetManager.kt`, `res/layout/widget_turntable.xml`, `res/drawable/ic_vinyl_empty.xml`.

**Verificación:** sin reproducción → vinilo vacío; al reproducir → portada circular; al parar → vuelve a vinilo vacío.

---

## Fase 5 · #2 Migrar artistas seguidos de Spotify

**Objetivo:** importar los artistas que el usuario sigue en Spotify y dejarlos ya seguidos en Echo (local + suscripción en YouTube Music).

**Infra existente:**
- `Spotify.myArtists(...)` ya devuelve los artistas seguidos en Spotify.
- Paquete `spotifyimport/` (`SpotifyImportRepository/Models/ViewModel` + `SpotifyImportScreen`) ya importa playlists y liked songs con sesión por cookies, progreso y resumen. Hoy `loadSources()` trae solo liked songs + playlists (no artistas).
- Seguir artista en Echo = `ArtistEntity.bookmarkedAt` + `YouTube.subscribeChannel(channelId, true)` (patrón en `ArtistMenu`/`SyncUtils`).

**Cambios:**
- `SpotifyImportModels`: nuevo `SpotifyImportSourceType.ARTISTS` y fuente `SpotifyImportSource.FollowedArtists`.
- `SpotifyImportRepository.loadSources()`: agregar la fuente "Artistas seguidos" (conteo vía `Spotify.myArtists`).
- `importSources(...)` para artistas: por cada artista Spotify → `YouTube.search(nombre, filtro=ARTISTS)` → mejor coincidencia (match por nombre normalizado; descartar baja confianza) → `upsert ArtistEntity(bookmarkedAt = now)` + `YouTube.subscribeChannel(channelId, subscribe = true)` (requiere sesión YT; si no hay login, solo bookmark local y avisar en el resumen).
- UI: la pantalla de import existente lista "Artistas seguidos (N)" con progreso y resumen (X migrados, Y sin coincidencia).

**Archivos:** `spotifyimport/SpotifyImportModels.kt`, `SpotifyImportRepository.kt`, `SpotifyImportViewModel.kt`, `ui/screens/SpotifyImportScreen.kt`, strings.

**Edge cases:** artista sin match en YT (reportar, no romper); sin login YT (solo local); rate limit Spotify (ya hay `spotifyCallWithTokenRetry`).

**Verificación:** conectar Spotify → seleccionar "Artistas seguidos" → importar → aparecen como seguidos en Biblioteca→Artistas; verificar suscripción YT si hay login.

---

## Fase 6 · #3 Canciones de playlist → Biblioteca

**Objetivo:** cuando agrego/importo una playlist, sus canciones quedan detectadas en **Canciones** de la biblioteca.

**Infra existente:** `mirrorPlaylist(...)` en `SpotifyImportRepository` crea `PlaylistEntity` + `PlaylistSongMap` con las canciones matcheadas. `SongEntity.inLibrary` es el flag; Library Songs filtra `WHERE inLibrary IS NOT NULL`. Hoy las canciones del mirror no necesariamente quedan con `inLibrary`.

**Cambios:**
- En `mirrorPlaylist(...)` (import Spotify): al insertar/matchear cada canción, set `inLibrary = now` (vía update o insert con `inLibrary` poblado) para que salgan en Library→Canciones.
- Aplicar el mismo comportamiento al guardar/agregar playlists de YouTube Music (camino de "guardar playlist"), para cumplir "todas las playlist que agregue".
- Opción de Ajustes "Agregar canciones de playlists importadas a la biblioteca" (default: activado), por si el usuario no lo quiere siempre.

**Archivos:** `spotifyimport/SpotifyImportRepository.kt` (mirror), lógica de guardar playlist (YT), `constants/PreferenceKeys.kt` (toggle), Ajustes, strings.

**Verificación:** importar/guardar playlist → sus canciones aparecen en Biblioteca→Canciones; sin duplicar las ya existentes.

---

## Fase 7 · #6 Lista de novedades de artistas seguidos (Release Radar)

**Objetivo:** lista inteligente con los últimos lanzamientos de los artistas seguidos, estilo "viernes de novedades" de Spotify. Programado semanal (viernes) + notificación.

**Infra existente:**
- Seguidos = `ArtistEntity` (bookmarked).
- YT: `YouTube.artist(browseId)` → página con singles/álbumes; `YouTube.artistItems(endpoint)` pagina discografía con año.
- Spotify: `Spotify.album(albumId)` y discografía GQL por artista (`discography`, ~L1568) → álbumes/singles con fecha.

**Diseño:**
- **DB:** nueva entidad `ReleaseRadarItem` (id, artistId, título, tipo single/álbum, fecha lanzamiento, artworkUri, fuente YT/Spotify, fetchedAt, seen) + DAO. Migración Room (siguiente versión, p.ej. v37).
- **Worker:** `ReleaseRadarWorker` (WorkManager, `PeriodicWorkRequest` ancla viernes ~08:00). Pasos: leer artistas seguidos → por artista, YT singles/álbumes + Spotify discografía → filtrar lanzamientos de la ventana reciente (últimos ~7 días desde la última corrida) → dedupe por (título normalizado + artista + fecha) → ordenar por fecha desc → ponderar por afinidad (reproducciones del artista) → upsert en DB → notificación "Novedades de tus artistas" (canal nuevo) si hay items nuevos.
- **UI:** `ReleaseRadarScreen` + `ReleaseRadarViewModel` que leen la cache; pull-to-refresh y botón "Actualizar" que dispara el worker de inmediato. Entrada en Inicio/Biblioteca.
- **Fuente combinada:** preferir YT para reproducción; Spotify solo para descubrir lanzamientos que falten en YT (match a YT al reproducir).

**Archivos:** `db/entities/ReleaseRadarItem.kt` (nuevo), `DatabaseDao.kt` (+queries), `db/MusicDatabase.kt` (+migración), `worker/ReleaseRadarWorker.kt` (nuevo), `ui/screens/ReleaseRadarScreen.kt` (nuevo), `viewmodels/ReleaseRadarViewModel.kt` (nuevo), `spotify/Spotify.kt` (exponer discografía de artista si hace falta método público), canal de notificación, `NavigationBuilder.kt`, strings.

**Edge cases:** muchos artistas → limitar concurrencia y aplicar backoff; sin red → mostrar cache; sin login Spotify → solo YT; primera corrida sin "última fecha" → ventana por defecto (~8 semanas).

**Verificación:** seguir artistas con lanzamientos recientes → forzar worker → aparecen ordenados por fecha + llega notificación.

---

## Aspectos transversales

- **i18n:** cada cadena nueva en `values/strings.xml` + `values-es/strings.xml`.
- **Build:** tasks `universalFoss` con JDK 21 (`JAVA_HOME` por comando). Compilación local antes de cualquier verificación.
- **Room:** las fases que tocan DB (#6, y posible flag en #3) requieren bump de versión + migración; mantener `AutoMigration` donde aplique.
- **Sesiones:** #2/#6 dependen de sesión Spotify (cookies, ya implementado) y/o login YT; degradar con elegancia si falta alguna.
- **Pruebas:** el repo tiene cobertura de tests limitada; verificación principal = build limpio + prueba manual del flujo descrito en cada fase. Donde sea barato, test unitario de helpers puros (normalización de nombres para match, dedupe de releases, parseo de fecha).

## Riesgos / puntos abiertos

- **Match Spotify→YouTube** (#2, #6): la coincidencia por nombre puede fallar en artistas homónimos; mitigar con normalización + verificación por popularidad/imagen. Aceptar "sin coincidencia" como resultado válido reportado.
- **Verificación de App Links** (#5): `autoVerify` depende de `assetlinks.json` del dominio (no controlado por nosotros); por eso el flujo se apoya en la pantalla del sistema "Abrir por defecto".
- **Costo de red del worker** (#6): acotar nº de artistas por corrida y cachear; revisar consumo de batería/datos.
- **Estado "reproduciendo" en widget** (#1): confirmar la señal exacta de "no hay reproducción" (artworkUri null vs estado STOPPED) al implementar.
