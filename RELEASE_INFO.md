# Aura Hi-Res Player 0.6.5

## El modo video por fin reproduce el video 🎥
Antes el botón de video decía que lo hacía pero solo se veía la portada/canvas. La razón: YouTube ya casi no entrega un stream "combinado" (video+audio en uno). Ahora Aura **trae el video y el audio por separado y los une en el reproductor** (como hacen los reproductores de YouTube):

- Funciona en **todos** los videos disponibles (los streams separados sí están siempre).
- Calidad **fija** ~720p, priorizando **H.264** (compatible con casi todos los teléfonos; se evita AV1).
- **Aislado al modo video**: el audio normal **no cambia ni corre riesgo**.
- Si un tema **no** tiene video, no se interrumpe el sonido: vuelve a solo-audio y te avisa ("Este video no está disponible").

### Cómo probarlo
Pon una canción con videoclip, abre el reproductor y toca el botón de **video**. Debe verse el video con su sonido. Si dice "no disponible", ese tema no tiene videoclip en YouTube.

## Técnico
- `MergingMediaSource(video-only adaptativo + audio)`. La URL de video se **pre-resuelve** en `toggleVideoMode` (fuera del hilo principal); solo se fusiona si la URL está lista → un fallo de video nunca corta el audio.
- Video-only sale del **mismo MAIN_CLIENT** que el audio → idéntico descifrado + PoToken. `findFormat(preferVideo)` elige avc1 ≤720p (sin av01). Clave de fuente de video con prefijo `ECHOVIDEO::`.
