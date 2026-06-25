# Aura Hi-Res Player 0.6.30

## Botón de video: uno solo y bien puesto 🎯
- **Se acabó el botón duplicado** audio↔video. Ahora hay **un único botón**, al **final del título** de la canción.
- **Ya no aparece el título arriba del video** en el modo normal (no te gustaba): el título vuelve a ir con los controles. *(En Picture-in-Picture sí se mantiene el título+artista sobre el video, que ahí sí gusta.)*

## Recordatorio: horizontal
Al girar a horizontal con un video, ya salen **solo los controles multimedia** (anterior · play/pausa · siguiente), que **se ocultan solos** a los ~3.5 s y **aparecen/desaparecen al tocar** la pantalla.

## Técnico
- `Player.kt`: eliminado el toggle del diseño clásico (acción duplicada) y el toggle del título superior; queda el único `FilledIconButton` al final de la fila del título. El título superior sobre el video ahora se renderiza solo cuando `inPip`.
