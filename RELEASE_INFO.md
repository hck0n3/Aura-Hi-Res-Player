# Aura Hi-Res Player 0.1.4

## Ecualizador con más pegada (paso 1: estilo Poweramp)
- Antes, al subir una banda del EQ se oía como si TODA la música bajara/ondeara. Era porque el realce empujaba el limitador a bajar la ganancia de toda la mezcla.
- Ahora, al ecualizar, la banda que subes queda **igual de presente** pero **deja de "tirar abajo" el resto** — el EQ se siente con pegada y limpio. Cuesta un pelín de volumen base (sube el del teléfono), a cambio de un EQ que pega claro.
- Es el primer paso para acercar el sonido al del ecualizador de Poweramp. Siguen: tono Graves/Agudos y modo paramétrico con Q.

## Lo que se hizo (técnico)
- TruePeakLimiterAudioProcessor: cuando el EQ está realzando, se restaura solo la mitad del auto-headroom del EQ (sqrt del eqMakeup). El realce se mantiene relativo a la mezcla pero sus picos ya no sobre-conducen el limitador de banda ancha → sin ducking/pumping de toda la pista al ecualizar.
