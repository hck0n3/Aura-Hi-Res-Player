# Plan súper auditado — Cuentas · Buscador seguidos · Sync · Changelog v5.2.2→5.2.4

**Fecha:** 2026-07-08 · **Estado:** plan (pendiente elección del usuario) · 4 investigaciones read-only.

## 1. Apartado "Cuentas" en Ajustes
- **YT:** logueado = cookie con SAPISID; hay nombre/email/handle/avatar (`AccountNameKey`/`AccountEmailKey`/`AccountChannelHandleKey`, `HomeViewModel.accountName/accountImageUrl`). Login=`LoginScreen` (ruta "login"), logout=`App.forgetAccount` (diálogo 3 opciones ya existe).
- **Spotify:** logueado = cookie `sp_dc`; hay nombre+avatar (email null vía GQL). Login/logout ya en `SpotifyImportScreen`/`repository.logout()`.
- **Discord/Last.fm:** claves + backend vivos pero **sin UI de login** → omitir (Discord=riesgo ban).
- **Diseño:** ítem "Cuentas" arriba en `SettingsScreen` → ruta nueva `settings/accounts`. Reusar el esqueleto del **huérfano** `AccountSettingsScreen` + `AccountSettingsViewModel` + sesión Spotify. Una tarjeta por servicio (avatar+nombre+estado+login/logout). Riesgo: bajo. Sin auth nueva, solo agregación + UI.

## 2. Buscador solo de artistas seguidos
- Seguido = `bookmarkedAt IS NOT NULL` (`artistsBookmarked()`). La búsqueda local tiene filtro ARTIST pero por `songCount>0`, **no** por seguidos.
- **Recomendado (cero SQL):** search box en Library→Artists filtrando `allArtists` (filtro LIKED = seguidos) en memoria (`LibraryArtistsViewModel` + `combine(allArtists, query)`). Reutiliza list/grid items. Riesgo: muy bajo.
- **Alt:** nuevo `LocalFilter.FOLLOWED_ARTIST` + query DAO `searchBookmarkedArtists`. Más integrada, más invasiva.

## 3. Sync más rápida (YT + Spotify)
Cuellos (con file:line en el informe): 
- **YT:** `delay(50ms)` fila-a-fila (¡~200s en 4000 likes!), paginado serial `.completed()`, N+1 lecturas DB, insert por fila en transacción propia, N+1 red álbumes/artistas.
- **Spotify:** matching = 1 `YouTube.search`/track, `Semaphore(2)`, `mapperMutex` serializa scoring.
- **Fixes:** quitar `delay`; **`insertAll` en lote** (una transacción por 200-500 filas); matar N+1 lecturas; Spotify subir concurrencia 4-6 + backoff 429 + quitar mutex; paginado paralelo/streaming. Riesgo: medio (tocar SyncUtils/DAO — probar bien).

## 4. Sync en tiempo real sin parpadeo
- La UI YA es reactiva (Flow + keys estables). El parpadeo es por: **YT** inserta cada fila en su propia transacción → Room re-emite la lista completa por fila → VM re-filtra O(n²) + reordena (fecha DESC empuja arriba). **Spotify** al revés: `awaitAll()` todo y aparece de golpe (flash).
- **Fixes:** inserts por **lote atómico** (Room emite 1×lote); filtrado a SQL; `conflate/sample(150ms)` el Flow; evitar reordenamiento visible; Paging 3 para librerías grandes. Riesgo: medio.

---

## 5. Changelog upstream v5.2.2 → v5.2.4 (76 commits, exhaustivo)

Etiquetas: **[alta]** portar · **[media]** quizá · **[skip]** infra/traducción/config o fork divergió.

### Reproducción / audio
- SponsorBlock (salta patrocinios/intros) `0680db0` **[alta]**
- Optimización arranque + buffer inicial 750ms `97787ed` **[alta]**
- Refetch en Opus (menú player) `191e04b` **[alta]**
- Recuperación robusta de WebView renderer (evita zombie en poca RAM) `9a43e2f` **[alta]**
- Remote-config self-healing del cipher `2a05a26` **[media]** (memoria: redundante en fork)
- Deshabilitar 320/Lossless `edf72e2` **[skip]** (fork puede querer conservarlas)

### Import / biblioteca
- Import Spotify por **enlace** (cualquier playlist, no solo tuyas) `05f56a8`+ **[alta]**
- **Botón flotante de import** en Biblioteca (Spotify o YTM por URL) `be22065` **[alta]**

### UI / UX
- Transición carátula estilo Apple Music (zoom+fade) + indicador crossfade "shining" `fb8bba5` **[alta]**
- Set as Ringtone (menú player) `0f0e66a` **[alta]**
- Búsqueda de Ajustes indexada completa `e244ac9`/`353e574` **[media]**
- Metro Lyrics tamaño/espaciado configurable `e7e71a1` **[media]**
- Menú player más limpio (sin slider volumen) `191e04b`/`13deea1` **[media]**

### Fixes
- **Fix sync YT Music** (`musicShelfRenderer`) + preserva fechas de like `13deea1` **[alta]**
- Miniaturas de canciones locales (media store) `a6f53b5` **[alta]**
- `removeFirst()`→`removeAt(0)` (compat APIs viejas) `4c3893c` **[alta]**
- Letras Unison (TTML crudo) `8f9fb0f` **[media]**
- Crash selector de directorio de export `3e2c21f`+ **[media]**
- Crash migración BD columna `provider` duplicada `4ed4bbd`+ **[media]** (esquema divergente — cuidado)

### Integraciones / Otros
- Last.fm scrobbling `b4c7aff` **[alta]** · ListenBrainz `51deb8b` **[alta]** · Discord RPC `6a5ad31` **[skip: ban]**
- JioSaavn servidores remotos `6eb8d15` **[media]**
- Echo Brain cola dinámica con poda `ad7bc98` **[media]** (solapa con radio propia del fork)
- Listen Together: host togglea control de participantes `353e574` **[media]**

### Skip (infra/traducciones/merges/version-bump): ~35 commits.

**Completitud:** compare `v5.2.2...v5.2.4` = 76 commits, 0 behind, ~150 archivos, todos categorizados. Incluye v5.2.22, v5.2.3, v5.2.4.
