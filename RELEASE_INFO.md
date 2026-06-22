# Aura Hi-Res Player 0.0.7

## Nivelación de volumen inteligente: las canciones flojas ya suenan parejo
- Antes, unas canciones sonaban muy bajo y otras alto. La nivelación solo subía las flojas hasta +6 dB, así que las muy bajas seguían sonando débiles.
- Ahora se suben hasta +12 dB para llegar al mismo volumen que el resto, y el limitador true-peak se encarga de que **no distorsione** en ningún dispositivo (streaming incluido).
- En resumen: las fuertes se bajan, las flojas se suben, y los picos se limpian → todo suena parejo y sin saturar.

## Lo que se hizo (técnico)
- AudioGain.loudnessMakeupDb: el tope de makeup de loudness sube de +6 dB a +12 dB (el mismo techo combinado que ya permite el TruePeakLimiter, MAX_MAKEUP). Las pistas por debajo de la referencia se nivelan hacia ella; el limitador 2× oversampled convierte los picos en techo limpio (CEILING / OUTPUT_TRIM) sin clipping.
