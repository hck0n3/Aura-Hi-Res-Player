# Backlog maestro — Aura Hi-Res (2026-07-07)

Todos los items investigados (read-only, verificados contra código actual). Regla: no publicar release oficial; builds privados en nube (`test-build.yml`). No implementar features sin OK.

Leyenda esfuerzo: XS (1 línea) · S (1 archivo) · M (varios) · L (subsistema).

## Bugs confirmados (reales en código actual)

| # | Bug | Causa raíz | Archivos | Esf. |
|---|-----|-----------|----------|------|
| 1 | Migración selectiva importa playlists **sin carátulas** | `JrTrack` no tiene campo thumbnail (se pierde en export); `importDirect` inserta `MediaMetadata` con `thumbnailUrl=null`; insert IGNORE | JrPlaylistImporter.kt, BackupRestoreViewModel.kt:233 | S |
| 4 | **Búsqueda ≠ YouTube Music** | search va sin auth (`useLoginForBrowse=false`) → ranking de invitado; `gl/hl` por device con `?:"es"`; filtros locales podan; `searchSummary` re-agrupa | InnerTube.kt:73,222; App.kt:621; OnlineSearchViewModel.kt:73 | M |
| 6A | YT **no migra favoritos** | "Biblioteca" (`TYPE_LIBRARY`) escribe solo `inLibrary`, nunca `liked`; likes solo con `TYPE_LIKED_SONGS`(LM) | YtmSyncWorker.kt:55, SyncUtils.kt:649 | S |
| 6B | Spotify migración **sin verificación + pierde tracks** | paginado corta con 1 track null; match sin umbral; resumen compara vs fetched no vs total real | SpotifyImportRepository.kt:426,512,276 | M |
| 7 | Gama baja: **touch pega en otra cosa** + pantallas chicas **no redimensionan** | mini-player `alpha=0` sigue clickable; gate wide tosco (`sw>=600`); panel fijo 340dp; dp fijos | BottomSheet.kt:124, MainActivity.kt:1180,1264, TvUi.kt:40 | M |
| 10 | Agregar **2ª canción a playlist reusa la 1ª** | `songIds` `remember` sin key + guard `if(null)`; en Player el diálogo no se dispone | AddToPlaylistDialog.kt:95,226; Player.kt:869 | S |
| 5* | Borrar playlist sincronizada **borra en YT sin preguntar** (data-loss) | delete llama `YouTube.deletePlaylist` incondicional | PlaylistMenu.kt:259, LocalPlaylistScreen.kt:381 | S |

## Features (necesitan diseño + OK)

| # | Feature | Estado | Esf. |
|---|---------|--------|------|
| PM | **Modo Rendimiento** (fusionado, ON=ULTRA) | Spec APROBADO, listo para implementar | M |
| 2 | **Release Radar tipo Spotify** | semilla estrecha, sin cap/1-por-artista, refresh no solo-viernes; ventana fiel NO viable (YT solo da año) | M |
| 3 | **Transiciones**: mejor preset | ya casi óptimo; bajar duración 13→~9s + ocultar skip-silence (humo) | XS |
| 5 | **Borrar playlist → preguntar** (YT o solo local) | diálogo 3 opciones; API YT ya existe; corrige data-loss | S |
| 8 | **Modo offline** (botón "Continuar offline" + revertible) | no existe pantalla; hay observer + query `downloadedSongs` + búsqueda local para reusar | M-L |
| 9 | **Interfaz dual** mejoras | panel sin cola, fijo 340dp, gate tosco; WindowSizeClass + panel flexible + toggle ocultar | M |
| 11 | **Reorg IA/UX** (agrupación funciones) | propuesta 12 grupos, plan F0-F5; ver doc IA | L |

\* Item 5 es feature pero destapa un bug de pérdida de datos.

## Clusters de conflicto de archivos (para paralelizar)

- **Cluster PLAYER/LAYOUT** (secuencial, mismos archivos): bug 7 + item 9 dual + bug 10 (Player.kt) + Modo Rendimiento (Player.kt, HomeScreen, MainActivity). Tocan MainActivity.kt/Player.kt/TvUi.kt → hacer en serie o 1 worktree.
- **Independientes (paralelizables en worktrees)**: bug 1 (importer), bug 4 (innertube), bug 6A (sync), bug 6B (spotify module), item 5 (playlist menu), item 2 (releaseradar), item 3 (defaults).

## Orden de ataque recomendado

1. **Quick wins bajo riesgo, en paralelo:** item 3 (XS, default 13→9s + ocultar skip-silence), bug 1, bug 6A, item 5 (+data-loss fix), bug 10.
2. **Paralelo independiente:** bug 4, bug 6B.
3. **Cluster player/layout en serie:** Modo Rendimiento → bug 7 + item 9 dual (comparten gate/panel).
4. **Features grandes con diseño:** item 2 Radar, item 8 offline.
5. **Item 11 IA** por fases F0→F5 al final (mayor superficie).

## Docs relacionados
- `2026-07-07-modo-ultra-ligero-design.md` (Modo Rendimiento, aprobado)
- `2026-07-07-ia-ux-audit-propuesta.md` (IA/UX)
