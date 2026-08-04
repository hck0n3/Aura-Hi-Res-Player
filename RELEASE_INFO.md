# Aura Hi-Res Player 0.6.142

La versión más importante para el sonido en mucho tiempo: **el ecualizador, el Volumen Seguro y todo el procesado no se estaban aplicando en alta resolución**. Ya se aplican. Además, el aleatorio deja de repetir artista, tu biblioteca se sincroniza en las dos direcciones con YouTube Music, y Escuchar Juntos vuelve a ir sincronizado de verdad.

---

## 🔊 El motor de audio ahora sí procesa la alta resolución
- **El ecualizador de 10 bandas, el preamplificador, el de-esser, el Volumen Seguro y el limitador no se ejecutaban** al reproducir contenido de 24 o 32 bits. La pantalla dibujaba la curva, pero el sonido salía sin tocar.
- La causa era una trampa de la librería de reproducción: al usar salida en coma flotante, se salta el único punto donde se insertan los procesadores de la app.
- En los móviles cuyo decodificador acepta coma flotante, la cadena se perdía **también en la reproducción normal**, no solo en alta resolución.
- **No se sacrifica calidad**: se descartó la salida fácil de bajarlo todo a 16 bits. Un entero de 24 bits cabe exacto en un decimal de 32.
- Con el ecualizador y el Volumen Seguro apagados, la salida es **idéntica bit a bit**. Con el ecualizador activo, la ruta nueva es **más barata** que la anterior.
- **Qué vas a notar**: el ecualizador se oye en archivos de 24 bits, y el Volumen Seguro actúa por primera vez en ese contenido — puede sonar a un nivel distinto al que estabas acostumbrado.
- Se cerró además una fuga de memoria del motor en cada transición entre canciones y en cada cambio a vídeo.

## 🔀 Aleatorio: se acabó el mismo artista una y otra vez
- La app separaba los artistas y **después** movía la canción actual al principio, creando una pareja *actual → siguiente* que ya nadie revisaba. Y esa es, literalmente, la próxima canción que suena.
- La memoria de qué artistas venían sonando **se reiniciaba cada vez que la cola se reconstruía**, así que la separación se perdía al añadir la radio o al encadenar canciones.
- El modo "primero la lista, luego la radio" no tenía separación por artista en absoluto.
- **La separación es preferencia, no regla**: en una lista de un solo artista, o donde uno copa casi todo, el orden se queda tal cual salió al azar.
- Se mantiene intacto: tu elección manual siempre suena, y ninguna canción se pierde, se duplica ni se repite.

## 🔁 Sincronización completa con YouTube Music, en las dos direcciones
- **Artistas**: lo que sigues se sube; lo que dejas de seguir se quita también arriba.
- **Playlists**: se sincronizan por defecto. Las que importes quedan en tu cuenta, y las que ya tenías se asocian a ella.
- **Me gusta y álbumes** se suben igual. Al estrenar móvil, entras y está todo.
- **Te informa** de qué está sincronizado, qué se está subiendo y cuándo termina.
- Seguir a un artista a propósito **no es lo mismo** que tener una canción suya en la biblioteca: solo lo primero se sube, para que no se te llene la cuenta de gente que no sigues.
- **Escribir en tu cuenta lo decides tú**: si ya tenías Aura, esta actualización llega con la subida **apagada**. La enciendes en Ajustes cuando quieras. Actualizar nunca es un permiso para tocar tu cuenta de Google.
- **Nada se borra de tu cuenta sin que tú lo pidas**: cerrar sesión, restaurar una copia o borrar el contenido sincronizado son acciones locales y ya no pueden interpretarse como "desuscríbeme de todo".

## 👥 Escuchar Juntos
- **El invitado ya no aparece una canción por delante**: cada salto publicaba dos mensajes contradictorios y el invitado obedecía los dos.
- **El volumen ahora sí se sigue**: se vigilaba un control que casi nadie usa, no el volumen real del reproductor ni el de los botones físicos. Al salir de la sala se te restaura tu propio volumen.
- **Ya no se desincronizan al cambiar de canción**: la espera de carga del invitado quedaba incrustada como desfase permanente.
- Corregido también un caso en que la cola del invitado se borraba a media canción.

## 🎨 Colores
- Paleta mucho más amplia, ya no limitada a tonos pastel.
- Puedes **introducir el color a mano en hexadecimal**.

## 🏷️ Aura Hi-Res, no YouTube
- Se quitaron las menciones a YouTube y YouTube Music de la interfaz —incluida la búsqueda— en **66 idiomas**.

## 🧾 Errores y registros: para dejar de adivinar
- **La avería ya no destruye las pruebas de la avería**: una sola canción que fallara podía borrar el registro entero justo cuando ibas a reportar.
- Los errores llegan por fin al archivo que puedes compartir; antes iban a un sitio que el usuario no puede enviar.
- Cada informe lleva **versión, dispositivo, Android y el estado de los ajustes** que cambian el comportamiento.
- **12 puntos que se tragaban errores en silencio** ahora dejan constancia. Uno cubría la ruta de pulsar play: tocabas reproducir, no pasaba nada, y no quedaba rastro.
- Privacidad reforzada: la limpieza de datos sensibles se aplica ahora también al texto de fallo que se muestra en pantalla.

## 🩹 Arreglos menores
- **Precio unificado a $3.74/mes**: una pantalla mostraba otro importe.
- **Términos y condiciones**: los títulos se veían en negro sobre fondo oscuro al abrir la app por primera vez.
