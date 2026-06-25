# Aura Hi-Res Player 0.6.33

## Audio que ya no se para a las pocas canciones 🎧 (importante)
Si habías usado el modo video y luego escuchabas audio normal, la app **pre-cargaba** la URL de video de cada canción en segundo plano. Eso disparaba muchas peticiones a YouTube y te **limitaba (rate-limit)**, así que tras unas canciones el audio se paraba y solo continuaba al rato. **Quitado:** ya no se pre-carga durante el audio normal (el video se resuelve al activarlo, con un breve giro de carga). Esto arregla el corte tras ~3 canciones (Redmi Note 13 Pro y otros).

## Modo video vertical: por fin centrado y con el botón funcionando 🎬
- **Video centrado de verdad** entre el título (arriba) y los controles (abajo) — ahora se reparte el espacio con un layout en columna, no "pegado abajo".
- **El botón de cambiar a audio ya funciona:** antes quedaba debajo de los controles y estos se "comían" el toque; ahora va anclado a la esquina del video, libre.
- **Botón audio↔video (en modo audio):** estilo de los chips de abajo, pegado a la derecha del título.
- **Atrás (gesto de Android) ya NO sale del video** — solo minimiza el reproductor, el video sigue.

## Horizontal: controles que se ocultan 📺
En pantalla completa, los controles ahora **se ocultan al tocar** la pantalla (y se muestran al volver a tocar) y **se ocultan solos** a los ~3.5 s. (El TextureView se tragaba el toque y el temporizador se reiniciaba con el buffering — corregido.)

## Técnico
- `MusicService.onMediaItemTransition`: eliminado el prefetch de video durante audio normal (`!_videoMode`); se mantiene solo el del siguiente track en modo video.
- `Player.kt`: rama inmersiva en `Column` (título / video `weight(1f)` centrado con Box interno para anclar el toggle a la esquina / controles); toggle de audio con estilo chip; sin `BackHandler` de salir-de-video (también quitado en `Thumbnail.kt`). Landscape: capa transparente sobre el video para el tap; auto-hide sin `isPlaying` en la key.
