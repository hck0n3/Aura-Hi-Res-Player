# Aura Hi-Res Player 0.6.28

## Video en HD de verdad (WiFi 720p) 📺✨
El gran cambio: antes el modo video usaba un stream **muxed**, que YouTube casi solo da en **360p** — por eso en WiFi se veía mal. Ahora usa un stream de **video HD por separado** y le **fusiona el audio normal** del tema:
- **WiFi → 720p**, **datos móviles → 360p** (automático, por tu conexión).
- Audio = el mismo audio de calidad de siempre, **sincronizado** con el video, sin doble audio.
- Prioriza **H.264** (compatible y fluido en gama baja); evita AV1/VP9 que la gama baja no decodifica bien.

## Interfaz del modo video, más pulida 🎬
- **Título y artista ARRIBA del video** (ya no queda vacío el espacio de arriba).
- **Botón audio↔video al final del título** (donde lo pediste). En audio aparece junto al título; en video, arriba.
- **Sin choque de controles:** se oculta la barra de cola mientras ves el video, así ⏮⏯⏭ ya no se montan con los botones de abajo.
- **Video un poco más abajo** (ya no tan alto).
- **Portada a resolución original** (más nítida).

## Calidad (revisión interna antes de publicar)
Dos revisiones adversariales. Corregido **antes** de subir: los **podcast de video** ya no intentan fusionar un 2º audio (su video ya trae audio), y el filtro de códec ya **excluye AV1** (que tartamudeaba en gama baja). La UI quedó verificada sin doble título y con el toggle siempre alcanzable.

## Técnico
- `YTPlayerUtils.findFormat(preferVideo)`: adaptive video-only por altura real (≤720 WiFi / ≤360 datos), pool avc1/avc3 (excluye av01/vp9).
- `MusicService`: `MergingMediaSource(video-only, audio-original)` salvo podcast muxed (`videoModeIsMuxedPodcast`).
- `Player.kt`: título inmersivo en `TopCenter`; toggle al final del título; `onImmersiveVideo` oculta la cola; video en `BiasAlignment(0,-0.12)`.
- `Thumbnail.kt`: cover con `size(Size.ORIGINAL)`.
