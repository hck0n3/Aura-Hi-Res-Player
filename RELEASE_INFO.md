# Aura Hi-Res Player 0.6.36

## Reconocimiento de música: posible arreglo 🎙️
La llamada a Shazam usaba el motor de red **CIO** de Ktor, que parece fallar (TLS/red) en varios Android — por eso **siempre daba error**. Ahora usa **OkHttp** (el mismo motor fiable que usa el resto de la app). Si aún falla, el mensaje de error ahora dice **el tipo exacto** de fallo para poder arreglarlo con precisión.

## Modo video
- **Horizontal a pantalla completa de verdad:** el video ahora **rellena los lados** (recorta lo justo) en vez de dejar barras negras a los costados.
- **Video más centrado:** cuando los controles están visibles, el video baja para verse centrado en pantalla (antes quedaba alto porque los botones de abajo ocupan más que el título).

## Portadas de video al máximo 🖼️
La portada de las canciones-video ahora cubre **más formatos** de miniatura de YouTube (i.ytimg, i9.ytimg, img.youtube, jpg y webp) y tiene **cadena de respaldo** maxres → sd → hq. (Recuerda: el máximo real de YouTube es 1280×720; si una canción concreta no tiene esa versión, no se puede más nítido desde YouTube.)

## Técnico
- `shazamkit/Shazam.kt`: `HttpClient(CIO)` → `HttpClient(OkHttp)`. `MusicRecognitionService`: el error incluye `e.javaClass.simpleName`.
- `PlayerVideoSurface`: nuevo `fillCrop` (cover via BoxWithConstraints + clipToBounds); landscape lo usa.
- `Player.kt`: video con `BiasAlignment(0,0.28)` cuando hay controles, `Center` cuando ocultos.
- `Thumbnail.kt`: reescritura de miniatura amplía hosts/extensiones; `onError` encadena maxres→sd→hq.
