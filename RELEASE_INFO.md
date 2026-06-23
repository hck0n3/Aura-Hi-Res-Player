# Aura Hi-Res Player 0.1.7

## Ecualizador: ya no queda demasiado alto al subir bandas
- Con el limitador multibanda nuevo, subir bandas dejaba el sonido muy alto y había que bajar el preamp a mano. Ahora el ecualizador da **headroom automático** al realzar: el nivel queda parejo y cómodo, sin que tengas que tocar el preamp.
- Se mantiene todo lo bueno del multibanda: las subidas pegan, el resto de la canción NO baja, y sin distorsión ni "oleadas".

## Lo que se hizo (técnico)
- TruePeakLimiterAudioProcessor: cuando el EQ realza, se restaura solo la mitad del auto-headroom (sqrt del eqMakeup). Ahora es seguro porque el limitador es multibanda (no hay ducking de banda ancha), así que baja el nivel del realce sin pumping.
