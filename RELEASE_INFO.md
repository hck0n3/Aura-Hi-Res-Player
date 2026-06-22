# Aura Hi-Res Player 0.0.9

## Transición suave (igual potencia) ahora sin recorte
- La transición en estilo "Suave (igual potencia)" mantiene las dos canciones con la misma potencia, y ahora además **no puede saturar** durante el cruce: se aplica un pequeño margen (headroom) solo mientras se mezclan las dos pistas, así la suma nunca pasa del máximo en ningún dispositivo. Al terminar el cruce, el volumen vuelve a tope.

## Lo que se hizo (técnico)
- MusicService.performCrossfadeSwap: durante el crossfade con curvas de igual potencia (1 = igual potencia, 2 = curva S) se multiplica el volumen de ambos reproductores por ~0.75 (≈ −2.5 dB) para que la suma en el mixer de Android (que no limita) se mantenga bajo 0 dBFS; el reproductor superviviente se restaura a volumen completo al finalizar. Curvas lineal (0) y exponencial (3) ya suman ≤ 1.0, así que no llevan headroom.
