# Aura Hi-Res Player 0.6.14

## Video pulido ✨
Con el video ya funcionando, esta versión lo deja redondo:

- **Sin texto de diagnóstico** (era temporal).
- **Al salir del video, el audio continúa donde iba el video** (ya no salta atrás).
- **La barra de tiempo avanza** mientras ves el video.
- **Apariencia limpia**: se oculta el canvas de fondo en modo video (ya no se ve la portada superpuesta detrás).
- **Cambio más responsivo**: al tocar video sale un indicador de carga al instante (ya no se siente "muerto").
- **Rotar la pantalla** ya no detiene el video, y **bloquear/desbloquear** ya no lo reinicia (continúa donde iba).

## Correcciones de robustez (revisión interna)
- Cancelar el video durante la carga ya **no** hace que el audio salte a una posición vieja.
- En modo video, la barra **no** se puede arrastrar por error (movía el reproductor pausado y "rebotaba").
- Al salir del video con la música en pausa, la barra **vuelve correctamente** a la posición/duración de la canción.

## Técnico
- `exitVideoMode` solo reanuda en la posición del video si un video realmente se reprodujo; resetea
  `_videoPositionMs/_videoDurationMs` al salir / fallar / cambiar de pista.
- Seekbar: callbacks de los 4 estilos de slider gateados con `!videoMode`.
- `LaunchedEffect(videoMode)` restaura `position/duration` del player principal al salir.
- Diagnóstico eliminado de `MusicVideoPlayer`; reporta progreso vía `reportVideoProgress`.
- `AndroidManifest` MainActivity con `configChanges` (no recrea en rotación).
