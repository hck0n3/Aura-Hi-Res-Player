# Aura Hi-Res Player 0.1.6

## Ecualizador con pegada real: subir graves ya NO baja toda la canción
- El gran cambio que pediste, estilo Poweramp. Antes, al subir una banda (sobre todo graves), el limitador bajaba el volumen de TODA la mezcla cada vez que esa banda pegaba un pico.
- Ahora el limitador es **multibanda**: separa los graves del resto y controla cada parte por separado. Subes los graves → solo los graves se controlan, **los medios/agudos se quedan a tope** → la canción ya no baja de volumen al ecualizar. EQ con pegada y limpio.

## Lo que se hizo (técnico)
- TruePeakLimiterAudioProcessor: ahora es un limitador true-peak de 2 bandas. División por filtro complementario (graves = paso-bajo ~250 Hz; resto = señal − graves; suman exactamente la entrada → sin colorear cuando no limita). Cada banda con su detección de pico 2× oversampled y su envolvente (ataque instantáneo, release suave), recombinadas con un softLimit de seguridad. Así un realce de graves ya no "ducked" toda la pista.
