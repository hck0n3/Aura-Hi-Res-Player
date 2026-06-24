# Aura Hi-Res Player 0.6.16

## Botones del reproductor estilo YouTube Music 🎛️
- El **título y artista** ahora ocupan todo el ancho y se leen mejor (los botones ya no los aprietan).
- Debajo, una **fila horizontal desplazable** con botones **etiquetados** (icono + nombre), estilo YT Music, que combinan con tu tema:
  **Me gusta · No me gusta · Agregar · Descargar · Mix · Audio · +**
- Cada acción es directa y **visible** (ya no escondida en el "+"):
  - **Agregar** → elegir playlist.
  - **Descargar** → muestra el estado (descargando / descargada).
  - **Mix** → inicia radio.
  - **Audio** → abre el **ecualizador / ajustes de audio** (donde antes estaba la letra).
  - **+** → opciones avanzadas restantes (shuffle, repetir, ver artista/álbum, biblioteca, detalles, etc.).

> Si quieres que saque también alguna de las opciones avanzadas del "+" a la fila, dime cuáles.

## Técnico
- `Player.kt`: nuevo `PlayerActionChip` (pill icono+label); fila `horizontalScroll` bajo el título; quitados los botones de la fila del título. `AddToPlaylistDialog` cableado al `showChoosePlaylistDialog`. Acciones reutilizadas: `toggleLike`, `dislikeCurrentSong`, descarga (`DownloadService`), `startRadioSeamlessly`, `navigate("settings/equalizer")`.
