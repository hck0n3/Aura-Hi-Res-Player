# Aura Hi-Res Player 0.6.148-beta1

Beta **privada**. Esta es la que responde a los once fallos que reportaste con capturas, más la personalización que pediste. Todo lo de 0.6.147 va dentro.

---

## 🎛️ Cómo se enciende y cómo se apaga
- **Ajustes ▸ abajo del todo ▸ "Interfaz nueva".** Apagarla no toca tu base de datos, tus ajustes, la cola ni la reproducción.
- El interruptor está en las dos interfaces, así que nunca te quedas atrapado.

## 🔴 La Biblioteca ya no te manda a Ajustes
Reportaste que la pestaña Biblioteca no mostraba nada y abría Ajustes. **No era la ruta**: era la pila de navegación. La celda de Ajustes era la única que se apilaba encima de la pestaña en la que estabas, y al volver, el sistema guardaba las dos juntas como si fueran la pila de Biblioteca — y la restauraba entera. **Se reenvenenaba sola** y quedaba rota el resto de la sesión.
- Encontramos y cerramos **cuatro puertas** con esa misma forma. Una de ellas envenenaba **Ajustes**, que es donde vive el interruptor para volver a la interfaz clásica.

## 🎨 Todo salía blanco — arreglado en la raíz
Casi todas las pantallas y **todos los diálogos** salían en blanco chillón. No era que faltaran pantallas por convertir: la interfaz nueva **se pintaba su propia paleta oscura** y nunca le decía nada al tema de la app. Se veía dentro de **una sola pantalla**: la biblioteca pintaba su cabecera oscura y, debajo, la pantalla Local en blanco.
- Ahora la interfaz nueva **manda sobre el tema**, así que las ~89 pantallas clásicas y todos los diálogos dejan de chirriar de golpe.
- **Consecuencia que debes saber: con la interfaz nueva la app es solo oscura.**

## 🖌️ Tus 46 colores ya llegan a la interfaz nueva
Era tu queja original **volviendo por la puerta de atrás**: el color repintaba las pantallas clásicas pero las nuevas seguían con un turquesa fijo, puesto 143 veces. Ahora el acento que elijas conduce toda la paleta nueva — y también el hex a mano, la intensidad y el acento dinámico.
- **Con contraste garantizado**: cada tono se comprueba a 4,5:1 por separado, con una prueba que recorre **los 46 colores** sobre el fondo normal y sobre negro puro. Ninguno puede dejar texto ilegible.

## 🖼️ Los 7 estilos de fondo del reproductor funcionan
Seis de los siete no hacían nada en vertical — incluido **el que viene puesto de fábrica**. La regla es una sola: **el fondo del reproductor es tuyo; lo que va encima es de Aura.** El estilo pinta el suelo del reproductor, su cola y el mini reproductor; la portada, el título y los controles no los toca.
- **AMOLED** ya alcanza las 14 superficies nuevas: antes arrastrabas la cola hacia arriba y era negro puro, hacia abajo y volvía el azul.
- El **radio de esquina** y el **tamaño de celda** también se honran ahora.

## 📱 Horizontal, tablet, TV y coche
Al girar el móvil desaparecía el diseño nuevo. Ya no: hay forma ancha propia, con foco de mando a distancia en cada control.
- **Rotar ya no destruye la pantalla entera.** Antes se desechaba todo el árbol y se montaba el clásico.
- Y se recuperaron **dos gestos que se habían perdido también en vertical**: deslizar hacia arriba para la cola, y el gesto sobre la portada con la letra a pantalla completa.

## 🔎 Las pantallas que faltaban, y esta vez alcanzables
**Buscar** (con historial, sugerencias, pestañas y búsqueda por voz), **Estadísticas**, **Migrar lista** (con Tidal y Apple), **Descargados** y las otras cinco auto-listas, y **Letras**.
- Buscar nunca se había rehecho, y no te lo dije en su momento. Ahora está.

## 🌫️ Cristal y movimiento
- El **mini reproductor** ya se lee: tenía un 7% de blanco sobre lo que hubiera detrás. Ahora arranca de un fondo opaco y lleva la portada desenfocada encima, **una vez por canción** — no un desenfoque en vivo, que sería calor y batería sin descanso.
- La **barra inferior** ya se anima: píldora que se desliza, tinte y escala de icono. Y sus etiquetas se leen: estaban al 42% de opacidad, por debajo del mínimo legible.
- Interruptores, chips y filas ya no saltan en seco.

## ⚠️ Lo que sé que sigue imperfecto
- **"Liquid Glass" como estilo de fondo promete más de lo que da**: dibuja un esmerilado real, pero no muestrea el fondo en vivo — eso es justo lo que rompe la regla de calor.
- **En Android 8 a 11 no existe el desenfoque del sistema**, así que cuatro estilos pierden la portada.
- **Nada está probado en un dispositivo.** Los contrastes están calculados y fijados con pruebas; el veredicto de vista es tuyo.
