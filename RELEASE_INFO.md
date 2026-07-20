# Aura Hi-Res Player 0.6.109 — El vídeo ya no se congela, el Xiaomi ya no se pega, y la cola infinita deja de morirse sola

## 🎬 El vídeo ya no se congela
- Arreglado: el vídeo se quedaba congelado **mientras el audio seguía sonando**, y al salir de la app y volver **ya no se recuperaba nunca**.
- Causa: cada vez que la pantalla se redibujaba, la app le decía al reproductor "vuelve a engancharte al lienzo" creyendo que era inofensivo. En realidad, si el lienzo aún no estaba listo, esa orden **borraba la imagen**. El sonido no necesita lienzo, por eso seguía.
- Al salir de la app se reconstruía todo por dentro, lo que caía justo en ese instante malo. Ahora no se reconstruye nada si el reproductor sigue vivo.

## ❤️ El "Me gusta" en Android Auto ya se actualiza
- El "me gusta" **siempre se guardó bien** — lo que fallaba era que la pantalla del coche no se enteraba.
- El cambio se anunciaba por un canal que Android Auto no escucha. Ahora se anuncia también por el que sí lee.

## 🔁 La cola infinita ya no se muere sola
- Arreglado el "a veces funciona, a veces no": un contador de fallos **que nunca se reiniciaba**.
- Tres fallos de red en toda la sesión —ni siquiera seguidos— y la cola quedaba marcada como "no hay más canciones" **para siempre**. Un ascensor, un túnel o un cambio de WiFi a datos mataba la reproducción automática **en silencio**, y seguía muerta al volver la señal.
- Ahora un fallo de red no se confunde con "se acabó la lista": se reintenta con pausas y la cola sobrevive al corte.

## 📱 El Xiaomi ya no se queda pegado
- Arreglado el "no responde" (ANR). Es el cambio más grande de esta versión.
- Cada vez que se redibujaba cualquier pantalla, la app leía ajustes del disco **bloqueando el hilo principal**. Con unos 560 sitios que lo hacen, un solo fotograma podía apilar decenas de lecturas bloqueantes.
- Esto además debería arreglar la **animación trabada** de los botones de abajo (inicio, búsqueda, biblioteca, micrófono), que sufría exactamente la misma causa.

## 🔋 Menos batería y menos calor
- Los widgets se refrescaban **una vez por segundo, tuvieras widgets o no** — con varias consultas a la base de datos cada vez, incluso con la pantalla apagada. Ahora comprueba si tienes alguno y, si no, se salta todo.
- La lista de reproducción se guardaba entera en disco **cada 30 segundos aunque no hubiera cambiado nada**. Ahora solo cuando cambia de verdad.
- Cerradas tres fugas de memoria en las transiciones entre canciones.

## 🚗 La miniatura en el carro llega a tiempo
- Para un iconito pequeño, la app descargaba la **portada gigante** (unas 10 veces más grande de lo necesario) y sin límite de tiempo. Con datos lentos eso tardaba una eternidad y mientras tanto se veía la carátula anterior.
- Ahora se pide directamente el tamaño pequeño, con tope de tiempo.

## 📖 Plegables
- **La interfaz ya no desaparece al girar.** Una parte del código secuestraba la pantalla completa dejando solo el fondo animado. Ahora esa vista inmersiva es opcional y viene apagada.
- **Letra centrada** con el móvil abierto.
- **Vista dividida automática** al desplegar, estilo Spotify.
- **La portada ya no se pone gigante**: tiene tope en pantallas grandes.
- **El ecualizador se expande** con la pantalla. Solo el diseño: bandas, frecuencias, filtros y presets **sin tocar**.
- Además: el diseño ancho y el modo TV eran **el mismo interruptor** por dentro, y el umbral (840 puntos) era mayor que un plegable abierto (690). Por eso ningún plegable recibía nunca el diseño ancho.

## 🔊 Volumen Seguro: ahora nivela de verdad
- Antes **solo bajaba, nunca subía**. La parte que sube los temas flojos se calculaba bien pero se entregaba a un componente vacío, y el código nativo descartaba cualquier refuerzo.
- Ahora sube y baja, con subida limitada a +3 dB y aplicada de forma **gradual** (no de golpe) para que no se note ningún salto.
- En mono no se refuerza: ahí no hay limitador que proteja, y distorsionar sería peor.
- El nivelado completo de los temas muy flojos llega en una próxima versión, cuando se mida el **pico** de cada canción — es la única forma de subir más sin que el sonido "bombee".

## 🔒 Privacidad: los documentos ya dicen la verdad
- La política de privacidad decía que la app recoge analíticas y las comparte con Google. Es **falso**: no hay analíticas activas ni se envía ningún informe de fallos. Documentos corregidos.

## 🧹 Limpieza
- Fuera dos enlaces heredados al proyecto del que nació Aura. Uno mandaba a los usuarios **al APK de otro proyecto** si alguien encendía esa ruta.
- Borrado un reproductor de vídeo que no se usaba desde ningún sitio.

## ⚠️ Todavía no arreglado
- **Arranque lento / "canción no disponible"** en móviles de gama baja: causa localizada (hay un tope de 30 segundos para resolver una canción, y al agotarse se muestra como "no disponible" — son el mismo problema). Se arregla en la siguiente.
- **Cortes de milisegundos** que adelantan la canción: causa probable identificada, sin confirmar.
