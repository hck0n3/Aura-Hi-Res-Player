# Aura Hi-Res Player 0.6.22

## Arreglos
- **Congelón / pausa al cambiar de app (regresión de PiP):** al entrar en Picture-in-Picture la app **reconstruía toda la interfaz**, lo que la congelaba unos segundos (y podía pausar la canción) cada vez que cambiabas de app con un video. Ya no: PiP muestra el reproductor sin desmontar nada — sin congelón ni pausa.
- **Sin pantalla negra al cargar el video:** ahora, al tocar el botón de video, **se mantiene la portada/canvas con el indicador de carga encima** y luego aparece el video (antes salía una pantalla negra mientras cargaba).

## Bienvenida y Acerca de
- Actualizadas con las funciones reales nuevas: **video que de verdad se reproduce** (con sonido, pegajoso entre canciones, pantalla completa al girar, **Picture-in-Picture**), **Videos oficiales** del artista, y reconocimiento desde **Ajustes Rápidos**.

## Pendiente (próxima)
- Portada en **alta calidad** para canciones que son video (ahora se ve pobre).
- **Podcasts de video:** poder elegir entre audio o video.

## Técnico
- `MainActivity`: eliminado el early-return que renderizaba solo-video en PiP (causaba el teardown del NavHost → freeze/pausa). PiP conserva el enter (`onUserLeaveHint`).
- `Thumbnail`: la tarjeta portada/canvas se oculta solo cuando el video YA se muestra (`!videoShowing`); el spinner ya no tiene fondo negro.
