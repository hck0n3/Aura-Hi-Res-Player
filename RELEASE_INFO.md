# Aura Hi-Res Player 0.2.1

## Transiciones sin cortes 🎚️
- La pista entrante ahora se **precarga y bufferea unos segundos ANTES** de que empiece el fundido, así suena en el instante exacto en que arranca la transición. Esto elimina el corte/silencio ocasional que aparecía cuando la red iba lenta (antes la siguiente canción empezaba a cargar recién cuando ya había comenzado el fundido).
- Si saltas de canción o cambias la cola, el player precargado se libera solo (sin fugas).

## Técnico
- MusicService: nuevo `crossfadePreloadJob` + `prepareSecondaryPlayer()` que construye y bufferea el player entrante (cola completa, posicionado en la siguiente, en silencio) ~6 s antes del fundido (CROSSFADE_PRELOAD_LEAD_MS). `startCrossfade()` reutiliza ese player precargado (o lo crea como fallback). Limpieza en `scheduleCrossfade()` y `onDestroy()`.
