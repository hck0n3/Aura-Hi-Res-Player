# SUPER PLAN de Modernización — Aura Hi-Res (versión futura PRIVADA)

_Estudio de arquitecto líder (workflow: 4 auditorías DB/toolchain/arquitectura/rendimiento + síntesis). Solo análisis + recomendación — NO implementa nada._ · 2026-07-08

## 1. Resumen ejecutivo
**Sano en lo moderno, frágil en lo estructural.** Toolchain en punta (JDK 21, AGP 9.0.0, Kotlin 2.3.10, SDK 36, media3 1.10.1, Room 2.8.4). El valor NO es "subir versiones" sino **corregir desalineaciones, salir de pre-releases, blindar la cadena de suministro y pagar deuda estructural** del fork.

- **DB: SÍ está en su última versión (v37)**, esquema exportado y coherente. Deuda: migraciones solapadas + parches defensivos + "broken v27"; **faltan índices de alto ROI** (sobre todo `event.timestamp`); **sin Paging3** en Library (riesgo OOM/ANR con 15-20k canciones).
- **Toolchain:** riesgos reales = **`ffmpeg-kit` archivado** (build irreproducible) y **material3/adaptive en `alpha` en app de pago**. Ruido: KSP desalineado, `sdk.dir` macOS commiteado, build sin paralelismo.
- **Arquitectura:** god-objects — `MusicService.kt` (4843), `Player.kt`/BottomSheetPlayer (~3040 en 1 @Composable), `DatabaseDao.kt` (1827/217 fn), `SyncUtils.kt` (1430). Sin capa repository. Duplicados. Desajuste paquete↔carpeta.
- **Rendimiento/build:** APK ~256MB (ffmpeg duplicado + youtubedl 31MB/ABI), `runBlocking` DataStore en arranque de Coil, build mal configurada. **PerformanceMode = lo más maduro, NO tocar.**

**Veredicto:** sin emergencia funcional, pero riesgo de suministro (ffmpeg) a atacar ya + deuda estructural (paginación + god-objects) como el gran cambio.

## 2. Plan por fases (F1 rápido/bajo-riesgo → F6 estructural/alto)

### F1 — Blindaje + quick wins (bajo riesgo)
- F1.1 **Vendorizar el `.aar` de ffmpeg-kit-full** (archivado). ⚠️ ruta AudioExport/EQ — mismo binario, no cambiar API.
- F1.2 Limpiar `gradle.properties` (quitar `sdk.dir` macOS → local.properties; `parallel`/`caching`/`configuration-cache`).
- F1.3 Alinear KSP↔Kotlin 2.3.10 + material-icons-extended→1.10.2.
- F1.4 Borrar código muerto (`AlbumArtUri`, stub SilenceDetector, ~6 pantallas huérfanas). ⚠️ no reactivar skip-silence.
- F1.5 Mover deps hardcodeadas al catálogo.

### F2 — Índices DB + arranque (bajo riesgo, impacto medible) → bump v38
- F2.1 **Índice `event(timestamp)`** (mayor ROI: home/stats/quickPicks full-scan).
- F2.2 Índices parciales en `song` (liked/inLibrary/isDownloaded…) + `bookmarkedAt` + release_radar + speed_dial.
- F2.3 **Quitar `runBlocking` de `newImageLoader`** (bloquea 1er frame). ⚠️ respetar PerformanceMode.
- F2.4 WorkManager schedule/seed fuera del main + fusionar migraciones DataStore en IO. ⚠️ regla "key nueva".

### F3 — Tamaño APK / distribución (impacto usuario máximo)
- F3.1 **AAB o arm64 principal** (−55%, 256→~113MB). ⚠️ re-probar licencia/demo/suscripción por-ABI.
- F3.2 `ffmpeg-kit-full`→`audio/min` + quitar ffmpeg duplicado de youtubedl. ⚠️ validar antes que EQ/export no use filtros de vídeo.
- F3.3 youtubedl bundle (31MB/ABI) a flavor aparte/lazy. ⚠️ validar cobertura media3.

### F4 — Fragmentar DB de consulta (medio)
- F4.1 **Partir `DatabaseDao.kt`** en DAOs por dominio (Song/Playlist/Artist/Album/Lyrics/History). ⚠️ like=upsert, lyrics=live idénticos.
- F4.2 FTS4/5 para búsqueda local (vs `LIKE '%…%'`) + materializar `songCount`.
- F4.3 Migraciones: onPostMigrate fila-a-fila → UPDATE en bloque; consolidar helper.

### F5 — Paginación (alto valor estructural)
- F5.1 **Paging3 en listas de Library** (songs/albums/artists/playlists). ⚠️ no starve el playback.
- F5.2 Paging3 en historial/descargas/búsqueda.
- F5.3 **Baseline profile** (módulo `:baselineprofile` + Macrobenchmark).

### F6 — Reestructuración mayor (alto riesgo, versión futura+1)
- F6.1 🚨 **Descomponer `MusicService.kt` (4843)** en controladores inyectables (Normalization/Queue/Dsp/Discord/SponsorBlock/SleepTimer) tras `PlaybackRepository`. **MÁXIMA ZONA SENSIBLE** — aquí viven crossfade, swap primary/secondary, loudness, Safe Volume, Superpowered. Extraer con **paridad byte-a-byte** + tests de audio, pasos pequeños.
- F6.2 Descomponer `BottomSheetPlayer` (~3040) en sub-composables + `PlayerUiState`.
- F6.3 Capa **repository** (SyncUtils→SyncRepository+Orchestrator). ⚠️ upsert de likes + bookmarkedAt.
- F6.4 Modularizar (`:spotify`, `:lyrics`, `:canvas`; unificar los 2 PlayerMenu). ⚠️ video=player dedicado.
- F6.5 Alinear paquete↔carpetas + navegación en grafos anidados type-safe.
- F6.6 Estabilizar material3/adaptive fuera de alpha. ⚠️ validar TV/car.
- F6.7 Nuevo DSL de AGP 9 + convention plugin para los 12 módulos.

## 3. Reglas innegociables (respetar en todo)
Aura-only branding · NO tocar Superpowered salvo mejora · NO romper transiciones de audio (crossfade) · NO romper demo/licencia/suscripción · like=upsert · lyrics=live position · video=ExoPlayer dedicado · PerformanceMode maduro (apoyarse) · DSP son stubs a propósito (Safe Volume = reemplazo) · migraciones one-time necesitan key nueva.

## 4. Toolchain: actual → recomendada → riesgo
| Componente | Actual | Recomendada | Riesgo |
|---|---|---|---|
| ffmpeg-kit-full | 6.0-2 (archivado) | Vendorizar/mirror; evaluar `audio` | **ALTO (suministro)** |
| material3 | 1.5.0-alpha18 | última estable 1.x | ALTO |
| material3-adaptive | 1.3.0-alpha09 | última estable | ALTO |
| KSP | 2.3.5 | alineado a Kotlin 2.3.10 | MEDIO |
| material-icons-extended | 1.7.8 | 1.10.2 (catálogo) | MEDIO |
| firebase-bom | 33.1.0 | última | MEDIO |
| youtubedl bundle | 0.17.3 (~31MB/ABI) | flavor/lazy | MEDIO |
| Kotlin / AGP / Gradle / JDK | 2.3.10 / 9.0.0 / 9.3.1 / 21 | mantener | — |
| media3 | 1.10.1 | mantener, vigilar patch | BAJO |
| Room | 2.8.4 | mantener; v37→v38 índices | BAJO |
| SDK 36/36/26 | — | mantener | — |

## 5. Qué NO hacer / trampas
No subir versiones "porque sí" · No reescribir MusicService de golpe (incremental+paridad+escucha) · No recortar ffmpeg/youtubedl sin validar cobertura · No pasar a AAB/arm64 sin re-probar licencia por-ABI · No reactivar skip-silence al borrar el stub · No borrar pantallas huérfanas sin confirmar deep-links/PiP · No añadir índices sin bump v38 + test migración · No cambiar semántica al partir el DAO · No confiar en `./gradlew | tail` (enmascara exit code) · No tocar PerformanceMode salvo apoyarse · No revivir DSP muertos · ⚠️ trampa: `withTransaction` usa `runInTransaction{runBlocking{}}` — no propagar el runBlocking anidado si se toca la capa DB.

## Secuencia de aprobación
**F1→F2→F3 = 80% del beneficio, riesgo bajo/medio, aprobable ya.** F4-F5 = deuda de datos. **F6 = versión futura+1**, solo con presupuesto de tests de audio + validación TV/car, pasos pequeños con paridad verificada.
