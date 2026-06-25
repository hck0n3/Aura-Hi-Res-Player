# Aura Hi-Res Player 0.6.27

## Modo video vertical: más pulido aún ✨
Afinando el modo video en vertical:
- **Transición suave:** al cambiar entre video y canción ahora hay un **crossfade** (fundido) en vez de un corte brusco.
- **Controles solo al tocar:** se acabó el ocultado por tiempo. Ahora **tocas la pantalla del video una vez para ocultar** los controles (vista limpia) y **tocas otra vez para mostrarlos**. Tocar en los botones/barra hace su acción normal (no oculta).
- **Video un poco más arriba:** el video se sitúa por encima del centro, así los controles de abajo quedan sobre el fondo ambiente, no encima del video.
- **Sin capa oscura tras los botones:** se quitó la barra oscura detrás de los controles para que el **video se vea mejor**. (Solo en modo video; al pasar a canción vuelve a su apariencia normal.)

## Calidad (revisión interna antes de publicar)
Como toca el render del video, pasó por **revisión adversarial**. El crossfade no provoca doble video (Thumbnail ya no pinta el video; lo hace siempre la capa inmersiva) y el layout/cola quedan intactos. Único detalle corregido: tocar en huecos vacíos junto a los controles ya **no** los oculta por accidente.

## Técnico
- `Player.kt`: `Crossfade(targetState = videoMode && videoUrl, tween(350), fillMaxSize)` envuelve premium/normal; auto-hide eliminado (toggle solo por tap); video con `BiasAlignment(0f,-0.35f)`; Column de controles sin `.background`, con `pointerInput { detectTapGestures { } }` para absorber taps.
- `Thumbnail.kt`: ya no compone `PlayerVideoSurface` (evita doble TextureView en el crossfade); solo spinner mientras resuelve; portada siempre visible.
