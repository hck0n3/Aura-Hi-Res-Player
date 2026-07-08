# Release Radar tipo Spotify — Diseño

**Fecha:** 2026-07-08 · **Estado:** en implementación (OK general del usuario)

## Mecánica exacta de Spotify (deep-research, verificado con fuentes)
- **Cada viernes** (oficial). Sin hora/timezone publicada (~medianoche, escalonado).
- **NO wipe estricto:** parcialmente acumulativo — un track **no reproducido** persiste hasta **28 días**; se **cae al reproducirlo**. Ventana real ~28 días (los "7 días" son deadline de entrega, no de elegibilidad).
- **~30 tracks**, **1 por artista** (hard cap).
- **Fuentes:** artistas que SIGUES (prioridad) + que ESCUCHAS + picks algorítmicos.
- **Orden:** seguidos primero, luego por fecha de lanzamiento desc.
- Discover Weekly (lunes, gustos) es OTRA cosa — no confundir.

## Decisión (elección explícita del usuario)
**Wipe estricto de 7 días** (no acumulativo): cada viernes se BORRA todo y se re-siembra con los estrenos de los últimos 7 días. Sin retención 28 días, sin drop-al-reproducir. Todo lo demás lo más Spotify posible: viernes + 30 cap + 1-por-artista + **fecha exacta de Spotify** + fuentes seguidos(prioridad)+escuchados + orden seguidos→fecha desc.

Nota honesta: difiere del Spotify real (28 días rodante + drop-al-reproducir); es la elección del usuario y simplifica la implementación.

## Por qué Spotify-sourced
YouTube solo da AÑO. La capa Spotify del app (API interna GraphQL, auth `sp_dc` persistente con auto-refresh) da **fecha ISO exacta** (`newReleases()`, `album()`, discography). Deprecaciones oficiales Nov-2024 NO aplican (API interna). Infra del radar ya ~90% hecha.

## Implementación (sin cambio de schema Room)
1. Fuente Spotify: parser `artistDiscography()` (o fallback `newReleases()`) → estrenos de artistas seguidos con fecha exacta. Semilla = seguidos Spotify ∪ artistas bookmarked ∪ top-playcount.
2. Guardar la fecha real en la columna `releaseDate` existente (sin migración).
3. Ventana: **wipe estricto 7 días** — cada viernes borra todo y re-siembra con estrenos de los últimos 7 días; cap 30; 1-por-artista; orden seguidos→fecha desc.
4. Sin drop-al-reproducir ni retención (el wipe semanal lo cubre).
5. Refresh: solo viernes (quitar `runNow` en arranque/apertura; mantener seed inicial).
6. Matching Spotify→YouTube (matcher existente) para playId reproducible; dedupe prefiere yt.

## Límite honesto
Enfoque interno de Spotify (TOTP + hashes GQL) es no-oficial; puede romperse si Spotify rota hashes (mitigado por refresh de hashes). Fallback a la ruta YouTube (año) si Spotify no está logueado.
