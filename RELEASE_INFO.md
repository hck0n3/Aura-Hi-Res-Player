# Aura Hi-Res Player 0.6.149-beta1

Beta **privada**. El cierre que reportaste al tocar Buscar, seis pantallas más con el diseño nuevo, los cuatro botones que pediste, y la personalización que te había quitado sin darme cuenta.

---

## 💥 El cierre al tocar Buscar

Se cerraba la app al abrir Buscar. **No era intermitente: le pasaba a cualquiera.**

Cada fila de "Explorar" se identificaba con el título de la sección más el identificador de su primera entrada. Pero **todas las categorías de "Estados de ánimo y momentos" comparten el mismo identificador** — solo cambian los parámetros. Así que desde la segunda fila el identificador se repetía y el sistema lanzaba el error al dibujar.

Ahora las claves van **por posición**: no pueden repetirse devuelva lo que devuelva el servidor.

## 📱 Seis pantallas más con el diseño nuevo

**Álbum**, **artista**, **lista de reproducción** (online y local), **en caché** y **más escuchadas**. De 10 a **16 de 69 rutas**.

- **Álbum y artista van primero por un motivo**: el reproductor nuevo te lleva a ellas. Tocabas el título o el artista y aterrizabas en una pantalla vieja. Ese salto ya no está.
- Las cuatro pantallas de listas comparten ahora **un solo molde**, así que "en caché" y "más escuchadas" dejan de ser las raras del grupo.
- **Tu Canvas está intacto** — la portada animada, su ajuste, y que se apague con el modo ahorro de datos. Verificado línea a línea, no de palabra.
- Y el **Aleatorio Mejorado comparte la misma memoria** entre las dos interfaces: no hay dos listas de "ya sonadas" que se desincronicen.

## 🎛️ Los cuatro botones que pediste

**No me gusta**, **Añadir a playlist**, **Ecualizador** y **Compartir**, en el reproductor a pantalla completa.

Van en **dos filas de cuatro**, y la razón es una medida: siete botones a 48dp con separación son 420dp, y tu pantalla tiene 360. Comprimirlos dejaría los objetivos táctiles pegados justo encima del transporte — una fábrica de toques equivocados. Siguen estando también en el menú.

## ▶️ La cola ya no miente

Decía "Siguiente en la cola" y debajo aparecía **la canción que estabas oyendo**. Ahora la actual va bajo su propio rótulo **"REPRODUCIENDO"** y **"Siguiente" empieza en la siguiente de verdad** — respetando el aleatorio, el repetir-una y el final de la cola.

## 🎨 La personalización que te había quitado

Tenías razón: había ocultado controles. **Ocultar también es perder.**

- **"Estilo de fondo del minirreproductor" vuelve, y funciona.** El mini nuevo lee su propia preferencia.
- **Y "Desenfoque" ya hace algo distinto de "Predeterminado".** Antes eran 30 y 34 puntos de desenfoque sobre la misma imagen: imperceptible. Ahora **Predeterminado es el fondo liso del tema** (y ni siquiera decodifica una imagen) y **Desenfoque es la portada difuminada**.
- **Liquid Glass ya no desaparece**: la fila está visible, atenuada, y **con una explicación que se puede leer** — pasó de 3,2:1 a 5,7:1 de contraste. El efecto real muestrea la pantalla entera en cada fotograma, y eso es exactamente lo que la regla de calor prohíbe en superficies que están visibles mientras suena música.

## 🔍 Y lo que reportaste de la interfaz

- El **botón ⋮ repetido** de la barra de la cola: fuera. Era el duplicado de verdad.
- La **lupa superpuesta** sobre las pestañas de Biblioteca: movida a cada pestaña, **sin perder la búsqueda**.
- Los botones de **IA, crear playlist e importar**: ya se leen.
- Una fila del menú decía **"Transmitir"** con icono de emisión cuando en realidad abre los **dispositivos de audio**.
- **34 textos** movidos del código a los recursos, en los dos idiomas.

## ⚠️ Lo que sigue pendiente, dicho claro

- **53 de las 69 rutas** conservan su disposición clásica. Se ven oscuras y coherentes, pero con la estructura de antes.
- **Las cuatro sub-pantallas de artista** siguen clásicas — y ahí vive el **único control de ordenación** de toda la zona de artista.
- **La interfaz nueva sigue siendo solo oscura.** Ya sé lo que cuesta el tema claro: **5 valores y 9 opacidades**, no una reescritura. Dilo y lo hago.
- **Los cortes de audio en Android Auto que reportaste no están arreglados.** Seis hipótesis fueron investigadas y **las seis quedaron refutadas** — incluidas mis dos favoritas. Nada de este lote toca la cadena de audio; lo verifiqué barriendo el código entero. Necesito el registro de un trayecto para seguir.
