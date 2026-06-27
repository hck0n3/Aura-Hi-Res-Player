# Aura Hi-Res Player 0.6.69 — portada que no se queda pegada + agregar a playlist la canción correcta

## 🖼️ Portada / now-playing ya no se queda pegada ni en blanco
- La **portada de lo que suena ya no se queda con la anterior** al cambiar de canción / entrar a otro álbum. Fuera de un crossfade, siempre se actualiza al tema actual.
- Al **salir y volver a entrar a la app**, el reproductor **ya no aparece vacío**: si al reconectar no hay dato momentáneo, conserva el último en vez de borrarlo, y se refresca solo.
- Se agregó un segundo camino (`onMediaMetadataChanged`) que refresca la portada/título aunque la resolución llegue tarde — recupera de un estado pegado.

## ➕ Agregar a playlist agrega la canción CORRECTA
Misma causa: cuando agregabas a una playlist desde el reproductor, agregaba la **canción anterior** porque leía un estado pegado del now-playing. Ahora el now-playing siempre está al día → agrega la que realmente está sonando.

> Sigo con el resto de tu lista (EQ plano/calidad, transición 10s lineal, links de artistas, "ver artista" en el menú, likes, volumen, PiP) — los voy soltando por tandas, verificando cada uno para no romper el audio.
