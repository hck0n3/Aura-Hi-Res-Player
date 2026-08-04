# Registro de cambios — 0.6.142

> Salida pública directa (sin ronda beta). Agrupado por tema.

---

## 🔊 El motor de audio ahora sí procesa la alta resolución

**Esto es lo más importante de esta versión.** El ecualizador de 10 bandas, el preamplificador, el
de-esser, el Volumen Seguro y el limitador **no se estaban ejecutando** al reproducir contenido de alta
resolución (24 bits, 32 bits o coma flotante) — mientras la pantalla del ecualizador seguía dibujando la
curva como si funcionara.

**Por qué pasaba**: al activar la salida en coma flotante —que Aura activa en cualquier móvil con más de
~3 GB de RAM, es decir casi todos— la librería de reproducción toma un atajo interno que **se salta el
único punto donde se insertan los procesadores de la app**. No era un fallo de Aura; era una trampa
estructural de la librería, invisible desde el código propio.

**El alcance era mayor de lo evidente**: con la salida en coma flotante activa, el reproductor **pide a
todos los decodificadores** (AAC, Opus, MP3, FLAC) que entreguen coma flotante. En los móviles cuyo
decodificador acepta esa petición, la cadena se perdía **también en la reproducción normal**. Los
decodificadores de muchos fabricantes la rechazan y caen a 16 bits — por eso a la mayoría le seguía
sonando el ecualizador y el problema pasó desapercibido.

**El arreglo no sacrifica resolución.** Se descartó el atajo fácil (apagar la coma flotante y bajarlo todo
a 16 bits), que habría cambiado un problema silencioso por una pérdida real de calidad justo en el
contenido que le da nombre a la app. En su lugar, cuando Aura detecta que la cadena va a descartarse, monta
la suya propia. Un entero de 24 bits cabe exacto en un decimal de 32, así que **no se degrada nada**.

- Con el ecualizador apagado y el Volumen Seguro apagado, la cadena se salta entera y la salida es
  **idéntica bit a bit**.
- Con el ecualizador activo, la ruta nueva es **más barata** que la anterior: se ahorra dos conversiones
  y el motor Superpowered trabaja en coma flotante de forma nativa.

**Qué vas a notar**: el ecualizador ahora se oye en archivos de 24 bits. Y el **Volumen Seguro actúa por
primera vez en alta resolución** — si lo tienes activado, ese contenido va a sonar a un nivel distinto al
que estabas acostumbrado.

También se cerraron dos problemas de camino: una **fuga de memoria nativa en cada transición** entre
canciones y en cada cambio a vídeo, y un caso en que el ecualizador se habría **encendido y apagado
bloque a bloque** en archivos de alta resolución.

---

## 🔀 Aleatorio: se acabó el mismo artista una y otra vez

El aleatorio ya no repetía canciones (eso se arregló en 0.6.140), pero **seguía poniendo al mismo artista
muy seguido**.

**La causa**: la app separaba los artistas correctamente… y **después** movía la canción actual al
principio de la lista. Ese movimiento final creaba una pareja nueva —*canción actual → siguiente*— que la
comprobación ya no revisaba, porque había terminado antes. Y esa no es una pareja cualquiera: **es
literalmente la próxima canción que suena**. La única adyacencia que el oído nota siempre era la única que
nada comprobaba, y ocurría el 100% de las veces.

Se sumaban dos cosas más: la memoria de qué artistas venían sonando **se reiniciaba cada vez que la cola se
reconstruía** (al añadir la radio, al hacer una transición), así que un artista podía caer en la posición
1, luego en la 4, luego en la 7 de reconstrucciones sucesivas; y el modo "primero la lista, luego la radio"
**no tenía separación por artista en absoluto**.

**La separación es preferencia, no regla.** Se calcula según lo que la lista puede dar de sí: en una lista
de un solo artista, o donde uno copa el 90%, la separación baja a cero y el orden se queda tal cual salió
al azar. Pedir más separación de la que la lista permite hace que el algoritmo gaste los artistas raros al
principio y **amontone al dominante al final** — peor que el problema original.

Se mantiene intacto: tu elección manual siempre suena, ninguna canción se pierde ni se duplica, y la
memoria de "ya sonó" funciona igual que antes.

---

## 🔁 Sincronización completa con YouTube Music, en las dos direcciones

Ahora tu biblioteca vive en tu cuenta, no solo en el teléfono. Al estrenar un móvil, entras y está todo.

- **Artistas**: lo que sigues se sube; lo que dejas de seguir se quita también arriba.
- **Playlists**: se sincronizan **por defecto, sin preguntarte**. Las que importes de otro servicio quedan
  en tu cuenta de YouTube Music, y las que ya tenías creadas se **asocian** a ella.
- **Me gusta y álbumes**: se suben igual.
- **Te informa**: qué está ya sincronizado, qué se está sincronizando ahora, y un aviso cuando termina todo.

**Una distinción deliberada, para que no te llenen la cuenta de gente que no sigues**: seguir a un artista
a propósito **no es lo mismo** que tener una canción suya en la biblioteca. Solo lo primero se sube. Lo
segundo se queda en el teléfono.

**Escribir en tu cuenta lo decides tú**: si ya tenías Aura instalada, esta actualización **no** empieza a
escribir en tu cuenta de YouTube por su cuenta. La subida llega **apagada** y la enciendes cuando quieras en
*Ajustes ▸ Sincronizar con YouTube Music*. En una instalación nueva viene encendida desde el principio, que
es como está pensada. Actualizar nunca es un permiso para tocar tu cuenta de Google.

**Nada se borra de tu cuenta sin que tú lo pidas**: solo se quita una suscripción cuando **tú** dejas de
seguir a ese artista dentro de Aura. Cerrar sesión, borrar el contenido sincronizado, restaurar una copia de
seguridad o cambiar de cuenta son cosas **locales**: ya no pueden interpretarse como "desuscríbeme de todo".

**Límites reales**: 600 escrituras por pasada, espaciadas, y nunca al arrancar la app ni con la batería
baja. Una playlist que ya existe en tu cuenta **se enlaza, no se duplica**. Crear una playlist sin conexión
sigue funcionando: si falla la subida, la playlist local se crea igual.

---

## 👥 Escuchar Juntos: tres fallos, tres causas distintas

- **El invitado aparecía una canción por delante.** Cada salto del anfitrión publicaba **dos mensajes
  contradictorios**: el cambio de canción y, además, un "salta al siguiente". El invitado obedecía los dos.
  Un tercer mensaje le hacía buscar, dentro de la canción vieja, la posición de la nueva.
- **El volumen no se seguía.** Estaba implementado de punta a punta… pero el anfitrión vigilaba un control
  que casi nadie usa (el deslizador del menú de tres puntos). El volumen real —el de la pantalla del
  reproductor y el de los botones físicos— no lo leía nadie. Silenciar tampoco se veía. Ahora se publica el
  nivel efectivo, y al salir de la sala **se te restaura tu propio volumen**.
- **Se desincronizaban al cambiar de canción.** El invitado guardaba la posición del momento en que llegaba
  el aviso, pero solo la aplicaba tras cargar la canción — a veces segundos. El anfitrión no espera a
  nadie, así que esa espera quedaba incrustada como desfase permanente, y la corrección automática solo
  actuaba por encima de 3 segundos.

Además: un mensaje sin cola llegaba interpretado como **cola vacía** y borraba la cola del invitado a media
canción; y un volumen de 0 procedente del servidor habría **silenciado a todo el que entrara**.

---

## 🎨 Colores: paleta amplia y hexadecimal

La personalización ya no se limita a tonos pastel. Puedes elegir de una paleta mucho más amplia o
**introducir el color a mano en hexadecimal**.

---

## 🏷️ Aura Hi-Res, no YouTube

Se quitaron las menciones a YouTube y YouTube Music de la interfaz —incluida la búsqueda, que decía
"búsqueda en YouTube Music"— en **66 idiomas**. La app dice Aura Hi-Res.

---

## 🧾 Errores y registros: para dejar de adivinar

Si alguna vez reportas un problema, el registro que envías ahora sirve de verdad.

- **La avería ya no destruye las pruebas de la avería.** Una canción que fallaba podía escribir hasta
  130 KB de trazas contra un límite de 256 KB, **borrando el registro entero** —transiciones, aleatorio,
  tiempos de arranque— justo en el momento en que decidías reportar.
- **Los errores llegan al archivo que puedes compartir.** Antes iban a un sitio que el usuario no puede
  enviar, así que el registro llegaba vacío de errores.
- **Cabecera completa** en cada informe: versión, dispositivo, Android, ahorro de batería y el estado de los
  ajustes que cambian el comportamiento.
- **12 puntos que se tragaban errores en silencio** ahora escriben. Uno cubría la ruta de *pulsar play*:
  tocabas reproducir, no pasaba nada, y no quedaba constancia de nada.
- **Privacidad reforzada**: la limpieza de datos sensibles se aplica ahora en los tres puntos de escritura,
  incluido el texto de fallo **que se muestra en pantalla** —el que se copia y se comparte— que hasta ahora
  iba sin limpiar.

---

## 🩹 Arreglos menores

- **Precio unificado**: una pantalla mostraba $3.74/mes y otra $10/mes. Ahora es **$3.74** en las dos, desde
  un único sitio en el código para que no puedan volver a divergir.
- **Términos y condiciones**: los títulos se veían en negro sobre fondo oscuro al abrir la app por primera
  vez. Ahora son legibles en ambos temas.
