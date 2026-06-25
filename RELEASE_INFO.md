# Aura Hi-Res Player 0.6.26

## Modo video vertical, ahora premium 🎬
Antes, en vertical, el video se veía "pegado encima de la portada" y todo básico. Ahora es un reproductor de video de verdad:
- **Fondo ambiente:** la carátula difuminada + un velo oscuro detrás del video (en vez del fondo normal del reproductor). Se siente envolvente. Es **carátula difuminada** (barato) para que vaya **fluido también en gama baja**.
- **Video a todo el ancho (edge-to-edge):** el video ocupa toda la anchura, centrado y con su proporción correcta. Mucho más protagonismo.
- **Controles limpios que se autoocultan:** título, barra de progreso (con scrub), ⏮⏯⏭ y los botones se superponen y **se desvanecen a los ~3.5s**; tocas para mostrarlos/ocultarlos. Mientras arrastras la barra **no se ocultan**.
- Se conserva todo: **atrás** sale del video, **horizontal** sigue en pantalla completa, scrub, chips, y la letra deshabilitada en video.

## Calidad (revisión interna antes de publicar)
Como toca el render del video, pasó por **revisión adversarial** y se corrigieron 3 cosas **antes** de subir:
- **Contraste (alto):** con el estilo de fondo "Predeterminado" en tema claro, el título/botones podían quedar oscuro-sobre-oscuro (ilegibles). Ahora se fuerzan **blancos** sobre el fondo oscuro del video.
- **Scrub (medio):** los controles se ocultaban a media barra mientras adelantabas. Corregido: no se ocultan durante el arrastre.
- **Transporte (medio):** en cierto estado, ⏮⏯⏭ podían no aparecer en el video. Corregido: siempre visibles en modo video.

## Técnico
- `Player.kt`: nueva rama PORTRAIT premium (`videoMode && videoUrl`): backdrop = `AsyncImage(cover 100x100).blur(150.dp)` + scrim, `PlayerVideoSurface(fillMaxWidth)` centrado, overlay autoocultable con `controlsContent`.
- `controlsContent` parametrizado con `immersiveVideo`: sombrea `TextBackgroundColor/textButtonColor/iconButtonColor` a blanco-sobre-oscuro y fuerza el transporte aunque `isFullScreen` (la cola conserva sus colores).
- Auto-hide keyed en `sliderPosition` (null salvo al arrastrar) → no se oculta en scrub.
