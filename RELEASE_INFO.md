# Aura Hi-Res Player 0.6.11

## Modo video reconstruido sobre lo que SÍ funciona 🎥
Tras varios intentos metiendo el video en el reproductor de música principal (sin éxito), ahora el
video usa **el mismo motor probado del canvas** de tu app — un reproductor dedicado con el
`User-Agent` correcto para los servidores de YouTube (sin ese encabezado, las URLs de video daban
**403** y nunca cargaban; esa era la causa real).

Cómo funciona ahora:
- Al activar **video**: se resuelve un stream **combinado (video+audio)**, el **motor de música se
  pausa** y el videoclip se reproduce **con su propio sonido**, con proporción correcta y sin
  portada/canvas detrás.
- Al **desactivar** (o cuando el video termina): **vuelve el audio** donde estaba.
- Si un tema no tiene video combinado: aparece un **toast con el motivo** (diagnóstico).

> Nota: durante el video, el audio sale del propio video (sin EQ/DSP), tal como elegiste para máxima
> fiabilidad. Tu audio normal (con EQ/DSP) queda intacto fuera del modo video.

### Cómo probarlo
Canción **con videoclip** → botón **video** → debe verse el video con sonido. Si sale
"Video falló — …", mándame ese texto.

## Técnico
- Nuevo `MusicVideoPlayer.kt` (calcado de `CanvasArtworkPlayer`): ExoPlayer propio + `OkHttpDataSource`
  con interceptor de `User-Agent` por cliente; `TextureView` en `AspectRatioFrameLayout` (RESIZE_MODE_FIT);
  `volume=1`, `REPEAT_MODE_OFF`; `onEnded` → sale del modo video.
- `findFormat(preferVideo)` prefiere **muxed** (itag 22→18→otro) de `streamingData.formats`.
- `MusicService.toggleVideoMode`: resuelve la URL muxed, **pausa** el player principal y publica
  `videoUrl`; `exitVideoMode()` **reanuda**. Se eliminó el `MergingMediaSource`/`#video`; el player
  principal vuelve a ser sólo audio (`DefaultMediaSourceFactory`).
- `PlayerConnection`: expone `videoUrl` + `exitVideoMode()`. `Thumbnail` usa `MusicVideoPlayer`.
