# Aura Hi-Res Player 0.6.6

## El video por fin se RENDERIZA 🎥✅
La fusión de video+audio (0.6.5) estaba bien, pero **nunca llegaba a ejecutarse**. La razón real (la causa de que el video no apareciera en NINGÚN intento):

> Al activar video, el reproductor reemplazaba la canción por **el mismo item**. ExoPlayer, al no ver cambios, hacía una actualización "ligera" (solo metadatos) y **no reconstruía** su motor interno — así que el código que arma el video nunca corría. Veías la portada/canvas con el audio igual.

**Fix:** al activar video cambio la URI del item (marcador `#video`), lo que **obliga a ExoPlayer a reconstruir** el motor → la fusión de video+audio por fin se ejecuta y se dibuja en pantalla. La clave de caché no cambia, así que el **audio se resuelve/cachea idéntico** (sin riesgo).

### Cómo probarlo
Pon una canción **con videoclip**, abre el reproductor y toca el botón de **video**. Ahora debe **verse el video con su sonido**. Si sale "Este video no está disponible", ese tema no tiene videoclip en YouTube — prueba otro.

## Técnico
- `toggleVideoMode` ahora reconstruye el item con `setUri(base + "#video")` (y lo quita al apagar), manteniendo `customCacheKey = mediaId`. Esto invalida `ProgressiveMediaSource.canUpdateMediaItem` → recreación completa del media source → corre la fábrica que devuelve `MergingMediaSource(video-only + audio)`.
