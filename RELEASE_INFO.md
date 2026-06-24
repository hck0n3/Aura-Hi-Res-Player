# Aura Hi-Res Player 0.6.12 (video: posición + diagnóstico)

## Cambios
1. **El video continúa desde donde ibas** (ya no reinicia la canción): al activar video se pasa la
   posición actual y el reproductor de video hace seek a ese punto.
2. **Diagnóstico en pantalla**: arriba a la izquierda del video aparece un texto pequeño con el estado
   real, p. ej. `itag=22 · estado=ready · vid=1 aud=1 · size=1280x720 · frame=sí`.

### Por qué el diagnóstico
"Se oye pero no se ve" puede ser por dos cosas distintas; ese texto lo aclara:
- `vid=0` → el stream NO trae pista de video (formato equivocado).
- `vid=1` pero `frame=no` → hay video pero no se pinta (render/superficie).
- `itag=140/251` → me dio un formato de SOLO audio.
- `ERR=...` → error de decodificación/red.

**Qué hacer:** activa video y mándame una **captura** de ese texto. Con eso arreglo el punto exacto.

## Técnico
- `MusicVideoPlayer`: `setMediaItem(item, startPositionMs)`; overlay de diagnóstico (itag de la URL,
  estado del player, nº de pistas video/audio vía `onTracksChanged`, `onVideoSizeChanged`,
  `onRenderedFirstFrame`, `onPlayerError`).
- `MusicService.toggleVideoMode`: publica `videoStartMs = player.currentPosition` antes de pausar.
- `PlayerConnection`/`Thumbnail`: pasan `videoStartMs` al reproductor de video.
