# Aura Hi-Res Player 0.6.39 — Volumen uniforme (parte 3 del plan)

## Se acabó el salto de volumen 🔊
A veces el volumen **subía solo a mitad de canción** (sobre todo con la pantalla apagada). Causa: el valor real de sonoridad de YouTube llega **un segundo después** de empezar la canción, pero la app lo **bloqueaba** y se quedaba con un valor por defecto; cualquier ajuste posterior era un salto brusco.

**Arreglado:** ahora la app aplica la sonoridad real en cuanto llega, **con una transición suave** (rampa de ~120 ms) tanto en la ganancia como en el realce, así que el nivel se corrige sin que lo notes — nada de saltos.

## Bajo el capó (revisado adversarialmente antes de subir)
- La normalización vuelve a aplicarse **una sola vez** cuando llega la sonoridad real (nunca en bucle ni hacia atrás).
- **Rampa por tiempo** (no por muestras): dura lo mismo a 44.1/48/96 kHz; ganancia y realce ahora cambian de forma gradual.
- **Priming:** cada reproductor nuevo (incl. el de crossfade y el del arranque) empieza **directo en el nivel correcto** — sin el "swell" al inicio de cada canción.
- Rampa **por frame** (sin desbalance izquierda/derecha) y re-primado tras un salto (seek).

## Para probar
- Pon canciones con la pantalla apagada un buen rato: el volumen debe mantenerse **uniforme** dentro de cada canción y entre canciones, sin subidas repentinas.
