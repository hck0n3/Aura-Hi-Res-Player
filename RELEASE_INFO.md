# Aura Hi-Res Player 0.6.7

## Video: cliente correcto + video al frente 🎥
Dos arreglos sobre el modo video:

1. **"Este video no está disponible" (aunque sí existía):** el stream de video salía del cliente de **solo audio** (ANDROID_VR), que no devuelve formatos de video → siempre fallaba. Ahora el video sale de **TVHTML5** (cliente con video adaptativo). El **audio sigue igual** (ANDROID_VR), sin riesgo.
2. **Video al frente:** al activar video se **ocultan la portada y el canvas** por completo, así solo se ve el videoclip (ya iba encima, ahora sin nada detrás compitiendo).

### Cómo probarlo
Canción **con videoclip** → reproductor → botón **video** → debe verse el video con su sonido, sin portada/canvas. Si sale "no disponible", ese tema no tiene videoclip o el cliente no lo entregó — dime y pruebo otro cliente.

## Técnico
- `resolvePlaybackData(preferVideo=true)` vuelve a usar `VIDEO_CLIENT` (TVHTML5) para el request principal; `findFormat(preferVideo)` elige formato de **video adaptativo** (avc1 ≤720p, sin av01). El audio del merge se resuelve por separado con MAIN_CLIENT.
- Thumbnail: la tarjeta portada/canvas se oculta (`!videoModeOn`) en modo video.
