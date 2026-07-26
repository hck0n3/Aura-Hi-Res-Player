# Aura Hi-Res Player 0.6.128 — Ahora SÍ: entrada suave real al cambiar de canción

## 🔴 Corregido: los cambios manuales seguían entrando de golpe (tu reporte)
El fundido del 0.6.126 tenía un bug real: corría con reloj desde el momento del CAMBIO, pero una canción por internet tarda medio segundo o más en resolver y cargar — **el fundido terminaba antes de que sonara el primer sample**, así que el audio real entraba a volumen pleno. De golpe, siempre, en canciones streameadas.

Ahora el fundido **espera a que el audio realmente empiece a sonar** y AHÍ hace la subida suave de ~medio segundo. Da igual si la canción tarda en cargar: la entrada siempre es gradual.

- Cubre next/anterior, tocar otra canción de la lista y arrancar otra playlist.
- Tu volumen queda exacto al terminar, siempre (blindado contra pausas, skips rápidos y el crossfade).
- El interruptor sigue en Ajustes ▸ Reproductor ▸ "Entrada suave al cambiar de canción".

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — tu registro ya trae el diagnóstico por etapas: repórtalo cuando pase.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
