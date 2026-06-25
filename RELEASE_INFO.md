# Aura Hi-Res Player 0.6.37 — Video y PiP (parte 1 del plan grande)

## Picture-in-Picture limpio 🪟
La ventana flotante ahora muestra **solo el video** a pantalla completa — se acabó la **barra/título** arriba y el contenido de la playlist detrás. Se logra dibujando únicamente el video encima (sin desmontar la app, así que **no se congela** como antes).

## Detección de canciones con video 🎬
Las canciones del **inicio (Home)** no mostraban el botón de cambiar a video porque ahí no se leía el tipo de video. **Corregido:** ahora las canciones con videoclip muestran el toggle también desde el inicio (las demás pantallas ya lo hacían).

## Cambio de video a audio más suave
Si vas en modo video y la **siguiente canción no tiene video**, ahora **pasa a audio en silencio** (sin el molesto aviso "Video falló").

## Horizontal
El video en horizontal ya llena toda la pantalla (cover, sin barras negras — desde 0.6.36) con las barras del sistema ocultas. *(Nota: si notas algún parpadeo de las barras al girar, dímelo — quité un intento de fix que rompía el fullscreen de carátula/letra, lo afino aparte.)*

## Técnico
- `MainActivity`: overlay top-level `if (inPipMode) Box(Black){ PlayerVideoSurface }` sobre el NavHost (sin teardown). Surfaces de la hoja gateadas `!inPip` (sin doble TextureView).
- `Player.kt`: surfaces inmersiva/landscape gateadas `!inPip`.
- `MusicService.applyVideoToCurrent`: disarm silencioso si `isVideoSong != true`.
- `innertube/HomePage.kt`: SongItem ahora setea `musicVideoType`.
- Revertido el `onConfigurationChanged` (rompía fullscreen de carátula/letra + ocultaba barras con mini-player colapsado).
