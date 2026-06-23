# Aura Hi-Res Player 0.1.5

## Revertido el cambio del ecualizador que bajaba el volumen
- El ajuste del EQ de la versión anterior hacía que, al subir una banda, la base bajara de forma constante. Se revirtió: el ecualizador vuelve a comportarse como antes.
- (En camino: un limitador multibanda para que subir graves NO baje el volumen de toda la canción — el método de Poweramp.)

## Lo que se hizo (técnico)
- TruePeakLimiterAudioProcessor: se revirtió el "half-restore" (sqrt del eqMakeup) introducido en 0.1.4; vuelve la restauración completa del auto-headroom del EQ.
