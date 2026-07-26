# Plan de mejoras — 6 frentes (pedido del dueño, 2026-07-26)

> Investigación completa (evidencia file:line) en los outputs de los workflows
> `wf_eb6e6ec0-ca4` (5 áreas) y `wf_c20f9728-092` (auditoría shuffle). Este doc es el resumen ejecutable.
> Método por ola: implementar → compilar + 144 tests → verificación adversarial del diff → release.

## Frente 0 — Aleatorio: repetidas al activarlo (CONFIRMADO + auditoría en curso)
- ✅ `PlaylistMenu` ⋯→Aleatorio bypasseaba TODO el sistema (sin contextId, sin modo) → `f6089e0`.
- Botón de la cola (Queue.kt): siembra bien SOLO si la cola nació con contexto — cubierto al arreglar los orígenes.
- Menús de Álbum/Artista/Selección: mismo patrón sin memoria. Álbum/Artista requieren namespace nuevo (`AL:`/`AR:`) + poda — Ola 2.
- Resto: lo que devuelva la auditoría de 3 familias (activación / orden+memoria / persistencia).

## Frente 1 — Cuentas unificadas (Ola 1, worktree)
Añadir filas Last.fm + ListenBrainz a `AccountsScreen` reusando `settings/lastfm` (sin auth nueva, sin rutas nuevas):
estado desde `LastFMSessionKey`/`LastFMUsernameKey` y `ListenBrainzTokenKey`/`ListenBrainzEnabledKey`; conectar → navigate("settings/lastfm"); logout inline (3 líneas Last.fm / limpiar token LB). SearchableSettings + About + Welcome. Discord NO (riesgo de ban, decisión previa). Qobuz/Saavn no son cuentas.

## Frente 2 — Portadas de playlists (Ola 1, worktree)
Causas: URL muerta suprime el mosaico; portadas personalizadas en `cacheDir` (Android las borra); thumbnailUrl solo refresca en sync. Fix 4 piezas SIN migración: (1) fallback a mosaico `onError` en `PlaylistThumbnail` (+`fallbackThumbnails`), (2) portadas custom a `filesDir/playlist_covers/`, (3) refresh de thumbnailUrl al abrir la playlist online, (4) superficies de imagen única usan el mismo fallback.

## Frente 3 — Arranque lento de algunas canciones (Ola 2, MusicService/YTPlayerUtils)
Contribuyentes cuantificados: YouTube.next serializado pre-prepare (+0.4-1.5s), bucle de 12 clientes (0.3-1s c/u + HEAD 4-5s en URL muerta), PoToken frío 2-5s (runBlocking, cap 8s), sts, Lossless búsqueda difusa 9s, runBlocking de DB en el loader. Orden de ejecución: (1) TELEMETRÍA por etapa (una línea RESOLVE_TIMING en el log del usuario — convierte cada reporte en datos), (2) sts async+prewarm, (3) fix del prewarm de PoToken (hoy no-op), (4) resto según telemetría real.

## Frente 4 — Cola infinita que analiza la playlist (Ola 2, MusicService + reco/)
Diseño `ContextProfile` (reco/ContextProfile.kt, testeable): al terminar un contexto (playlist/álbum/EP/single uniforme) construir perfil del POOL COMPLETO (artistas + géneros vía GenreCache + languageHint débil), gate de señal mínima (coverage ≥0.3 o ≥3 artistas conocidos), y usarlo para (i) semillas representativas por clúster de género, (ii) STEERING SUAVE (re-score, nunca filtro duro — lección fila #39: señal cacheada no puede colapsar la infinita) de cada lote de radio. Enrichment fire-and-forget WiFi-only. Invariantes intocables: #22 no-repeat, never-silence, never fully-sort.

## Frente 5 — Catálogo de transiciones para A/B (Ola 1, worktree; curvas 5-8)
Investigado (fuentes en el output): añadir como estilos seleccionables:
5. **V secuencial** (Poweramp-style fade-out→fade-in; gap 0 por defecto, cap 300ms por never-silence)
6. **Logarítmica dB-lineal** (R=60dB — "la canción muere de verdad")
7. **Dipped parabólica** ((1−p)² / p² — respiro de −6dB sin silencio)
8. **Coseno alzado equal-gain** (sin²/cos² — suma exacta 1, extremos pendiente-cero)
+ tests de las 4 en CrossfadeMathTest (endpoints/monotonía/forma). El default no cambia.

## Orden de olas
- **Ola 1 (paralela, worktrees):** Frentes 1, 2, 5 → merge → build → verificación → release.
- **Ola 2 (secuencial en MusicService):** fixes de la auditoría shuffle → Frente 4 (ContextProfile) → Frente 3 (telemetría+wins) → verificación adversarial → release.
- Cada release: RELEASE_INFO honesto + About/Welcome + registry + 144 tests verdes.
