# Aura Hi-Res Player 0.6.147

Tres cosas que rompían la confianza en la app: letras que no eran de la canción, un actualizador que te dejaba en la versión anterior, y un reproductor del que no se podía salir. Ninguna avisaba de nada.

---

## 🎤 Las letras ya son las de la canción que suena

Usuarios reportaron leer la letra de **otra canción**. Eran **dos causas distintas**, y ninguna daba error.

- **La primera es del fundido entre canciones.** Durante el cruce, la letra se fija a propósito a la canción saliente, para que no salte a mitad. Pero esa fijación solo se soltaba cuando terminaban **las dos** rampas del fundido — y no duran lo mismo. Con una canción entrante lenta de resolver, la saliente **se callaba a los 0,6 segundos y la letra aguantaba 5**. Si pausabas en ese momento, el reloj se congelaba y aguantaba **toda la pausa**. Y si saltabas de canción durante el fundido, te quedabas con la letra de una tercera. Ahora se suelta **cuando la canción deja de oírse**, no cuando termina la cuenta.
- **La segunda es del proveedor de letras.** Cuando no encontraba la canción con título y artista, **reintentaba sin el artista** — y elegía al ganador **solo por duración**. El artista no se comprobaba nunca. Por eso salían letras perfectamente sincronizadas… de otro. Golpeaba sobre todo a música **en español, regional e independiente**.
- Ahora el artista se compara como **lista de créditos**, partida por comas y "feat.", así que una colaboración con los nombres en distinto orden **ya no se rechaza**. Y cuando el artista no puede avalar —alias, recopilatorios, alfabetos distintos— **el título tiene que ser exacto**.
- **Y las letras equivocadas que ya estaban guardadas se reparan solas** al mostrarse. Una reparación **nunca borra**: el texto anterior se conserva aparte, y lo que hayas escrito o pegado tú **no se toca jamás**.

## ⬇️ El actualizador instala la versión nueva

Si no pulsabas "Borrar actualizaciones descargadas", **siempre instalaba la anterior**. Eran **tres causas**, todas mudas: veías el diálogo de instalación normal, instalabas, y seguías donde estabas.

- El sistema de descargas **repite el resultado del trabajo anterior durante días**. Al abrir la pantalla llegaba el "completado" de la actualización pasada, con su archivo — así que el botón ponía **"Instalar"** nada más entrar e instalaba el APK viejo **sin descargar nada**. Lo único que se comprobaba era si el archivo existía. Y la firma pasaba, claro: ese APK viejo **es** un APK legítimo de Aura.
- El archivo se guardaba **siempre con el mismo nombre**, así que las versiones colisionaban y un reintento podía pegar la cola del archivo nuevo sobre el cuerpo del viejo.
- Y "¿hay actualización?" se decidía comparando si la versión era **distinta**, no si era **más nueva**. Como las betas se publican como versión previa, **quien iba en una beta recibía la estable anterior como si fuera nueva**.
- **Ahora la app lee la versión del propio archivo descargado** antes de instalar nada — lo único que un archivo rancio no puede falsear. Si no coincide, lo borra y vuelve a descargar.
- **Y libera espacio**: cada actualización dejaba un APK de ~100 MB en Descargas **para siempre**. Ahora queda una sola copia.

## 🚗 Android Auto

Un usuario reportó que la app **desapareció de Android Auto**. La investigación descartó el manifiesto, las reglas de ofuscación, los límites de la lista y todo el código de Auto — **ninguno cambió**. Lo único que 0.6.147 puede hacer al respecto es quitar de en medio lo que la versión anterior añadió a la memoria del proceso.

- La oferta de "volver a la cola anterior" guardaba **la cola entera** hasta 10 minutos — y podía ser tu biblioteca completa, la cola más larga que la app construye. Ahora guarda solo lo justo para reconstruirla.
- El servicio **ya responde cuando el sistema pide memoria**, cosa que antes no hacía.
- **Honestamente: esto es un seguro, no una cura demostrada.** Si el icono te sigue desapareciendo, hace falta saber si **aparece y luego desaparece** o si **nunca aparece** — son dos problemas distintos.

## 🔧 Y cuatro cosas que estaban rotas desde hace tiempo

- **El reproductor se quedaba atrapado a pantalla completa.** Cerrabas la letra y la marca no se borraba: quedaba un reproductor **sin controles y sin cola**, y solo se salía volviendo a abrir la letra.
- **Las canciones de "reproducción automática" de la cola no se podían tocar.** La fila hacía el gesto de pulsarse y no pasaba nada — toda esa sección era decorativa.
- **Emitiendo a un altavoz**, tocar una fila de la cola saltaba en el **móvil**, no en el altavoz.
- **Cuatro puertas de navegación** podían dejar una pestaña abriendo la pantalla equivocada el resto de la sesión.
