# Aura Hi-Res Player 0.6.31

## Modo video, rediseñado 🎬
- **Título y artista arriba** del video otra vez, y el **video centrado** entre el título (arriba) y los controles (abajo) — se ve más equilibrado.
- **Botón audio↔video, uno solo y bien puesto:** en modo audio va al **final del título**; en video va **superpuesto en la esquina inferior derecha** del video. Se oculta junto con los demás controles cuando tocas para limpiar la vista.
- **Horizontal = pantalla completa real:** al girar con un video, se **ocultan las barras del sistema** y el video cubre toda la pantalla (desliza para ver las barras). Los controles se ocultan solos / al tocar.
- **La pantalla ya no se apaga** mientras el video está reproduciéndose (al pausar, sí puede apagarse, para no gastar batería).

## Calidad (revisión interna antes de publicar)
Revisión adversarial: todo lo encontrado fue menor y se ajustó antes de subir — el botón de la esquina ahora se oculta con los controles, el "mantener pantalla encendida" solo aplica con el video **reproduciéndose** (no en pausa), y se limpió un import sin uso.

## Técnico
- `Player.kt`: rama inmersiva con título `TopCenter`, video en `Box(fillMaxWidth).align(Center)` y el toggle en `BottomEnd` gateado por `ptControls && !inPip`; título inferior re-oculto en `immersiveVideo`.
- `keepScreenOn = videoMode && isPlaying` (DisposableEffect). Landscape: `WindowInsetsControllerCompat.hide(systemBars)` con `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, restaurado en `onDispose`.
