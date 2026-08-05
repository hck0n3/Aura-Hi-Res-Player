# Aura Hi-Res Player 0.6.146-beta1

Beta **privada**. Me dijiste *"parece que al inicio solo le hubieras cambiado el color"*, y tenías razón: rehice seis pantallas de **contenido** y dejé el **armazón** clásico. Esta beta ataca el armazón. Todo lo de 0.6.145 va dentro.

---

## 🎛️ Cómo se enciende y cómo se apaga
- **Ajustes ▸ abajo del todo ▸ "Interfaz nueva".** Enciéndela y apágala cuando quieras.
- **Apagarla no toca nada**: ni tu base de datos, ni tus ajustes, ni la cola, ni la reproducción. Vuelves exactamente a la app que ya conoces.
- El interruptor está **en las dos interfaces**, así que nunca te quedas atrapado sin poder volver.

## 🏛️ El armazón — lo que se ve en todas las pantallas
- **La barra negra de arriba se fue.** Era un rectángulo opaco que además **tapaba el resplandor** que las pantallas nuevas pintan detrás. Ahora es transparente y el color la atraviesa.
- **Ya no hay dos cabeceras apiladas**: el inicio dibujaba la global *y* la suya propia, una encima de la otra.
- **Barra inferior nueva.** Dijiste que los botones de abajo seguían saliendo como antes — y salían, porque **era literalmente el mismo componente**. Su altura alimenta **a la vez** el desplazamiento y los márgenes de las listas; si solo se cambia una de las dos, todas las listas quedan mal espaciadas.
- **Mini reproductor propio.** Preguntaste por qué te seguía saliendo el flotante de cristal líquido: porque la pantalla nueva llamaba **al mini reproductor clásico tal cual**.

## 🌌 Inmersiva
- **64 dp de barra fantasma**: todas las pantallas reservaban hueco para una barra superior que la interfaz nueva ya no dibuja. Un margen muerto arriba, en toda la app. Fuera.
- **Dos franjas opacas abajo** cortaban el resplandor a media altura. Fuera también.
- El contenido corre ahora **de borde a borde**, por debajo de las barras del sistema.

## 🖼️ Las portadas ya no salen recortadas
- Tu ajuste de **"no recortar las portadas"** simplemente **se ignoraba** en la interfaz nueva: se forzaba el recorte en los tres sitios que dibujan carátulas.
- Las portadas se dibujaban **siempre cuadradas**, incluso las de vídeo de YouTube, que son **16:9**. Meter un 16:9 en un cuadrado tira el **44%** de la imagen. Ahora cada portada conoce su proporción.

## 📱 Las pantallas que faltaban — de 6 a más de 50
En vez de rehacerlas una a una, fui a los **componentes compartidos**, que es donde estaba la palanca:

- **`Items.kt`** — **45 archivos** lo usan: búsqueda, álbum, artista, historial, modo offline, playlists y la biblioteca entera. Todas las filas y rejillas de contenido pasan por aquí.
- **`Material3SettingsGroup.kt`** — **395 usos repartidos en 26 archivos**, las sub-pantallas de ajustes entre ellos.
- Y a mano, las que no comparten componente: **reconocimiento de música** (las dos pantallas), **Escuchar Juntos** (las dos), **Acerca de** y **Qobuz**.
- Cada uno resuelve su estilo **una sola vez por pantalla** y lo reparte hacia abajo — no hay una lectura de ajustes por fila. Con el interruptor apagado, cada rama vuelve **exactamente** a los valores de antes.

## ⚠️ Lo que sigue pendiente
- **Horizontal, TV y coche siguen cayendo al reproductor clásico.** Al girar el móvil, el diseño nuevo desaparece. Es el siguiente trabajo, y es grande: son layouts distintos, no un reestilizado.
- El resto de pantallas propias: letras, descargas, migración y estadísticas.
