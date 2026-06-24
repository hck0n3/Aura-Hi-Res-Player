# Aura Hi-Res Player 0.6.10

## Video: relación de aspecto correcta 🎥
- El videoclip ahora se muestra con su **relación de aspecto real** (16:9, vertical, etc.) en vez de estirarse: la superficie escucha `onVideoSizeChanged` y ajusta el `aspectRatio`, centrado.
- La capa de **portada/canvas** se aísla (compositing offscreen propio) para que no interfiera con la superficie de video.

### Cómo probarlo
Canción **con videoclip** → botón **video** → el video se ve centrado y proporcionado, sin portada/canvas detrás. Si saliera "Video falló — …", mándame ese texto.

## Técnico
- `PlayerVideoSurface`: `DisposableEffect` con `Player.Listener.onVideoSizeChanged` (incluye `pixelWidthHeightRatio` y `unappliedRotationDegrees`) → `aspectRatio` del `TextureView` dentro de un `Box` centrado.
- `Thumbnail`: la tarjeta portada/canvas va en su propio `Box` con `CompositingStrategy.Offscreen`, separada de la superficie de video; corregido el balance de llaves.
- Se mantiene: búsqueda en ambas listas de formatos (adaptive + muxed), cliente de video TVHTML5, recreación del media source por URI `#video`, y toast diagnóstico.
