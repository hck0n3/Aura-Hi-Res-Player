# Aura Hi-Res Player 0.6.29

## Picture-in-Picture, ahora limpio 🪟
La ventana flotante (PiP) ya no muestra el reproductor completo amontonado:
- **Solo el video con el título y el artista encima** (lo que te gustaba), sin barra de tiempo ni botones encima.
- **Al tocar la ventana** aparecen los **controles de reproducción** (anterior · play/pausa · siguiente) y el botón de **pantalla completa** — son los controles nativos de Android, integrados ahora con la app.
- El icono play/pausa se actualiza solo según lo que esté sonando.

## Calidad (revisión interna antes de publicar)
Pasó por revisión adversarial. Corregido **antes** de subir: como un video 16:9 hace la ventana apaisada, faltaba limpiar también ese caso (el más común) — ahora **tanto vertical como horizontal** salen limpios en PiP. También se evitó una posible fuga de recursos al entrar/salir de PiP varias veces.

## Técnico
- Nuevo `LocalIsInPipMode`; en PiP se ocultan los controles inferiores y el toggle en AMBAS ramas (portrait y landscape) y se muestra solo título+artista.
- `MainActivity`: `RemoteAction`s (prev/play-pausa/next) vía `BroadcastReceiver` (registrado/desregistrado), `setActions` en `enterPipModeIfVideo`, y `pipPlayPauseJob` que sincroniza el icono play/pausa (con cancel antes de reasignar).
