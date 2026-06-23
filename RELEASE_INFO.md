# Aura Hi-Res Player 0.1.2

## Arreglado: el volumen "en olas" (pumping) y la saturación
- Subir tanto las canciones flojas exigía de más al limitador, lo que se oía como saturación o como el volumen subiendo y bajando solo ("olas"). Volvimos la subida de las flojas a +6 dB, el punto donde el sonido queda limpio: sin saturación y sin pumping.
- Las canciones fuertes y los picos se siguen controlando igual.

## Lo que se hizo (técnico)
- AudioGain.loudnessMakeupDb: el tope de makeup vuelve a +6 dB (de +8). Subirlo más sobre-conducía el TruePeakLimiter → artefactos audibles (saturación armónica, luego pumping). Para subir las flojas más sin esos artefactos se requiere normalización peak-aware (aplicar solo la ganancia que cabe bajo el true-peak del tema). Test unitario actualizado.
