# Aura Hi-Res Player 0.1.1

## Arreglado: saturación de la voz a volumen alto
- En una versión reciente, la nivelación subía demasiado las canciones flojas (+12 dB) y eso exigía de más al limitador, lo que ensuciaba la voz (áspera/estridente o "entubada"), sobre todo a volumen máximo.
- Ahora la subida de las canciones flojas es más moderada (+8 dB): se siguen nivelando, pero sin saturar. Las fuertes y los picos se siguen controlando igual.

## Lo que se hizo (técnico)
- AudioGain.loudnessMakeupDb: el tope de makeup de loudness baja de +12 dB a +8 dB para no sobre-conducir el TruePeakLimiter (la causa de la saturación armónica audible a alto volumen). Test unitario actualizado.
