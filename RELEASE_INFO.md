# Aura Hi-Res Player 0.6.17

## Pantalla horizontal e inmersión 🔄
- **Rotar con video** → el video pasa a **pantalla completa** y los **controles se ocultan solos** (toca para mostrarlos/ocultarlos).
- **Rotar con canvas** (fondo animado Apple Music) → el **canvas a pantalla completa**, también con controles que se auto-ocultan.

## Cambio video↔canción más rápido en gama baja ⚡
- Ahora se **pre-cargan** las URLs de video en segundo plano (la actual y la siguiente cuando estás en video), así el cambio es casi instantáneo en teléfonos lentos.

## Botones del reproductor
- La fila de botones ahora **se difumina en el borde** para que se note que hay **más opciones desplazando**.
- **Arreglado el botón "Audio"**: ahora abre el ecualizador (antes el reproductor lo tapaba y parecía que no hacía nada).

## Primer inicio (Android con 3 botones)
- En las pantallas de bienvenida/migración, los botones ya **respetan la barra de navegación** de Android (antes quedaban debajo y no se podían tocar en celulares sin gestos).

## Técnico
- `Player.kt`: rama landscape con `if videoMode → fullscreen video / else if canvas → fullscreen canvas / else split`, overlay de controles con auto-hide (3.5s). Fade del borde en la fila de chips (`drawWithContent` DstIn). "Audio" añade `state.collapseSoft()`.
- `MusicService.prefetchVideoUrl()` precachea muxed (actual + siguiente) cuando se usa video.
- Onboarding screens: `navigationBarsPadding()` en los bottomBars.
