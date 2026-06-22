# Aura Hi-Res Player v5.7.117

## El volumen ya no sube ni baja en las transiciones suaves
- Antes, al usar el fundido entre canciones (crossfade) se notaba un "bajón" o "bombeo" de volumen en mitad de la mezcla.
- Pasaba porque la curva por defecto dejaba que las dos canciones sumadas se pasaran de nivel, y el limitador de picos tenía que bajar el volumen de golpe para evitar saturación.
- Ahora el fundido por defecto usa una curva lineal que nunca se pasa de nivel, así que el volumen se mantiene parejo durante toda la transición. (La curva anterior sigue disponible como otro estilo de transición.)

## Las bibliotecas de artista vuelven a mostrarse completas
- Antes, al tocar "Más" en una sección de un artista (Álbumes, Canciones, etc.) a veces salía una lista genérica o vacía en lugar de todo el contenido.
- Era por un error al armar el enlace interno: usaba un separador equivocado y se perdía el dato que pide la lista completa.
- Ahora el enlace se arma bien y se carga la lista completa de álbumes, canciones y demás.

## La app ya no desaparece de Android Auto al desconectar del coche
- Antes, al desconectar del coche la app se quitaba de Android Auto y costaba que volviera a aparecer.
- El sistema de Android moderno bloqueaba el servicio cuando intentaba ponerse en primer plano por su cuenta al recrearse en segundo plano, y eso lo cerraba.
- Ahora se deja que el sistema multimedia gestione ese estado automáticamente, así que la app se mantiene disponible en Android Auto entre conexiones.

## Los álbumes cargan sin fallos ni tirones
- Antes, entrar a un álbum podía ir lento porque la lectura de su lista de canciones fallaba cuando YouTube devolvía el álbum en un formato distinto según la región.
- Ahora la app entiende los dos formatos posibles y tolera datos faltantes, así que la lista de canciones del álbum se arma sin errores.

## Lo que se hizo (técnico)
- MusicService.crossfadeGains: la curva por defecto (0) pasa a ser lineal (1 - p) para que la suma de amplitudes no supere 1.0 y no dispare el TruePeakLimiter; la equal-power (sin/cos) se movió a la curva 1.
- MusicService.onCreate: se eliminó la llamada manual a startForeground() (y su notificación temporal); MediaSessionService de Media3 gestiona el primer plano al iniciar la reproducción. Evita ForegroundServiceStartNotAllowedException al re-vincular desde segundo plano (Android 12+), que rompía Android Auto.
- NavigationBuilder y ArtistScreen: la ruta de "ver más" de artista usa el separador correcto &params={params} (antes ?params=), para no perder el token params en la petición a InnerTube.
- YouTube.albumSongs: se quitó el !! y se añadió respaldo a singleColumnBrowseResultsRenderer además de twoColumnBrowseResultsRenderer, con null-checks en la lista y en la continuación.
