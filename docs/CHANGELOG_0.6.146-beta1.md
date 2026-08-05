# Registro de cambios — 0.6.146-beta1

> Me dijiste: *"parece que al inicio solo le hubieras cambiado el color"*. Tenías razón, y el error fue mío:
> rehice seis pantallas de **contenido** y dejé el **armazón** clásico. La barra de arriba, la de abajo y el
> mini reproductor son lo que ves en todo momento, en todas las pantallas. Mientras siguieran siendo los de
> antes, la app iba a seguir sintiéndose la de antes por muchas pantallas que rehiciera.
>
> Esta beta ataca el armazón primero. Todo sigue detrás del mismo interruptor reversible.

---

## 🏛️ El armazón — lo que se ve en todas las pantallas

- **La barra negra de arriba se fue.** Contaste que "la parte donde está el título de la app está fea, solo
  es una barra negra". Era un rectángulo opaco que además **tapaba el resplandor** que las pantallas nuevas
  pintan detrás de sí. Ahora es transparente y el color de la pantalla la atraviesa.
- **Y ya no hay dos cabeceras apiladas.** El inicio dibujaba la cabecera global *y* la suya propia, una
  encima de la otra. Con la interfaz nueva, las pantallas que traen cabecera propia ya no reciben la global.
- **Barra inferior nueva.** Me dijiste "los botones de abajo siguen saliendo como antes" — y salían, porque
  era literalmente el mismo componente. Ahora es una barra propia con los iconos del diseño nuevo. Su altura
  alimenta **a la vez** el desplazamiento y los márgenes de las listas: si solo se cambia una de las dos,
  todas las listas de la app quedan mal espaciadas.
- **Mini reproductor propio.** Preguntaste por qué te seguía saliendo el flotante de cristal líquido: porque
  la pantalla nueva llamaba **al mini reproductor clásico tal cual**. Ahora tiene el suyo.

## 🌌 Inmersiva

- **64 dp de barra fantasma.** Todas las pantallas reservaban hueco para una barra superior que la interfaz
  nueva ya no dibuja — un margen muerto arriba, en toda la app. Fuera.
- **Dos franjas opacas abajo** cortaban el resplandor a media altura. Fuera también.
- El contenido ahora corre **de borde a borde**, por debajo de las barras del sistema.

## 🖼️ Las portadas ya no salen recortadas

Dos fallos distintos, los dos arreglados:

1. Tu ajuste de **"no recortar las portadas"** simplemente **se ignoraba** en la interfaz nueva: se forzaba
   el recorte en los tres sitios que dibujan carátulas. Ahora se respeta.
2. Las portadas se dibujaban **siempre cuadradas**, incluso las de vídeo de YouTube, que son **16:9**. Meter
   un 16:9 en un cuadrado tira el **44%** de la imagen. Ahora cada portada conoce su proporción.

## 📱 Las pantallas que faltaban — de 6 a más de 50

En vez de rehacer pantallas una a una, fui a los **componentes compartidos**, que es donde estaba la palanca:

- **`Items.kt`** — **45 archivos** lo usan: búsqueda, álbum, artista, historial, modo offline, playlists,
  biblioteca entera. Todas las filas y rejillas de contenido de la app pasan por aquí.
- **`Material3SettingsGroup.kt`** — **395 usos repartidos en 26 archivos**, las sub-pantallas de ajustes entre ellos.
- Y a mano, las que no comparten componente: **reconocimiento de música** (las dos pantallas),
  **Escuchar Juntos** (las dos), **Acerca de** y **Qobuz**.

Cada uno resuelve su estilo **una sola vez** por pantalla y lo reparte hacia abajo — no hay una lectura de
ajustes por fila. Con el interruptor apagado, cada rama vuelve **exactamente** a los valores de antes.

## ↩️ Volver a la cola anterior

Me lo pediste y aquí está: estabas escuchando una playlist, te desviaste a un álbum o a un artista, y ahora
**la app te ofrece volver donde estabas** — a la canción por la que ibas, no al principio.

Un detalle que costó encontrar: el botón de **Reproducir** de un álbum arranca una cola **sin etiqueta**;
solo el de Aleatorio la pone. Una regla basada en esa etiqueta **no habría saltado nunca** en tu caso. Así
que la regla es otra: salir de una lista con la que estabas sentado hacia cualquier otra cosa.

- De playlist a playlist **no** pregunta — eso es cambiar de idea, no desviarse.
- La oferta **caduca a los 10 minutos**, y lo que está en pantalla siempre se puede aceptar.
- Se puede apagar en *Ajustes ▸ Reproductor ▸ Cola*.
- **Funciona también con la interfaz clásica**, no es exclusiva de la beta.

---

## Lo que sigue pendiente

- **Horizontal, TV y coche siguen cayendo al reproductor clásico.** Al girar el móvil, el diseño nuevo
  desaparece. Es el siguiente trabajo, y es grande: son layouts distintos, no un reestilizado.
- El resto de pantallas propias: letras, descargas, migración, estadísticas.

Todo lo de **0.6.145** (el color del tema, la cola inteligente, los micro-cortes en Auto y el botón de atrás)
va incluido aquí también.
