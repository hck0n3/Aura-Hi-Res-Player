# Registro de cambios — 0.6.145

> Cuatro problemas que reportaste, con su causa exacta encontrada en el código. Ninguno era lo que parecía.
> Y una función nueva que pediste.

---

## 🎨 Cambiar el color del tema ahora cambia la app entera

Elegías uno de los 42 colores y casi nada se movía. Había **dos causas**, las dos estructurales.

La primera: el ajuste de intensidad viene en **"Suave" por defecto**, y en ese modo la única función que
aplica la saturación de tu color **se saltaba a sí misma**. Lo único que llegaba al tema era el matiz —
Material descartaba la saturación y la fijaba en un pastel. Por eso los 42 tonos se veían casi iguales:
matemáticamente no podían diferenciarse en otra cosa.

La segunda: **el fondo y todas las superficies estaban escritos a fuego**. El lienzo de la app era
`#111A1D` pasara lo que pasara. Se calculaba el tinte derivado de tu color y se descartaba una línea antes
de usarlo.

**Ahora**: eliges cian y el fondo se vuelve un negro con matiz de cian; eliges rojo y se vuelve un negro con
matiz de granate. Y con ellos se mueven la barra de navegación, la barra superior y el mini reproductor.

Cada superficie **conserva su propio valor y su propia saturación** —su peldaño en la escalera de
profundidad que el diseño ya tenía— y solo se le sustituye el matiz. El fondo sigue siendo casi negro,
con una prueba que lo fija: nunca se aclara. **El modo AMOLED sigue siendo negro puro.**

- **Y un bug de propina**: el rojo por defecto hacía doble función de "no ha elegido nada", así que elegir
  ese color concreto **reactivaba el color automático del fondo de pantalla** en vez de aplicarse.
- **Botón de restablecer**, abajo del todo y con confirmación. Devuelve las ocho preferencias de tema,
  incluidas las dos del modo negro puro **juntas** — que si se separan se desincronizan.

---

## 🎯 La cola inteligente deja de mezclar géneros

Contaste que acertaba, fallaba, acertaba y volvía a fallar. **Ese patrón intermitente era la pista.**

Una canción cuyo **género se desconoce puntuaba exactamente cero**: ni se premiaba ni se castigaba, quedaba
**invisible al filtro en las dos direcciones** y competía solo por relevancia bruta. Y encima hay una cuota
que reserva **1 de cada 5 huecos** para artistas nuevos — y un candidato sin género es "nuevo" por
definición. Uno mal, uno bien, dos mal seguidos: era esa cuota.

- El género desconocido ahora recibe un empujón de **exactamente un puesto**. Pierde el empate contra una
  canción del género correcto **y nada más** — nunca se descarta, y es seis veces menor que el castigo por
  género equivocado, así que "no lo sé" nunca se trata como "está mal".
- **La cuota de exploración ya no reserva hueco** para un candidato que el filtro acaba de empujar atrás.
- **Los géneros se aprenden antes de puntuar**, no después de encolar. Tope de 1,5 segundos, y si se agota
  la app continúa igual: nunca puede causar un silencio.
- **Y ahora aprende también con datos móviles.** Antes era solo por WiFi, así que **en el coche no aprendía
  nada** — que es justo donde te fallaba. Son consultas diminutas; si prefieres lo contrario, el ajuste
  sigue en *Ajustes ▸ Contenido*.

---

## 🚗 Los micro-cortes en Android Auto

El arreglo de 0.6.141 seguía intacto — esto era otra cosa.

- Una función recorría **la línea de tiempo entera** en el hilo principal cada vez que se recalculaba el
  orden. Ya no ocurría en cada canción, pero **sí en cada añadido de la radio infinita** — o sea, sin parar
  cuando se acaba la lista. Ahora es una sola lectura.
- La ordenación creaba **miles de objetos** por recálculo. En una cola de 5.000 canciones: de 66.438 a
  **cero**, y de 2,44 ms a 0,99 ms.
- Y al preparar la transición entre canciones se copiaba la cola completa. En esa misma cola de 5.000 son
  **10.002 accesos menos al reproductor**, por cada canción.
- Una reserva de memoria **en el hilo de audio**, en cada bloque de sonido, ya no ocurre en la ruta normal.

**El crossfade suena exactamente igual**: misma forma, mismos tiempos, las 9 curvas, el modo sin pausas y
el mezclado de doble reloj. Solo se abarató cómo se prepara, no lo que hace.

---

## ↩️ El botón de atrás ya no te manda al inicio

Ibas de una playlist a un artista, dabas atrás, y aparecías en el inicio.

**No era un fallo de navegación: era un gesto oculto.** Existía una función que retrocedía hasta la raíz de
la pestaña, y estaba atada a la **pulsación larga de la misma flecha de atrás** cuyo toque normal hacía lo
correcto — en 51 pantallas. Sin vibración, sin aviso, sin confirmación. Una pulsación un poco lenta y
perdías la playlist de la pila. Y como se detenía en la raíz de esa cadena concreta, el destino cambiaba
según de dónde vinieras: por eso parecía que no tenía lógica.

**Eliminado.** Un atajo que nadie descubre y que destruye la navegación en silencio es peor que no tener
atajo.

- Volver a la app desde un acceso directo o desde el widget **ya no borra la cadena** de pantallas.
- El grafo de navegación **se reconstruía en cada refresco de pantalla** por una instancia que no se
  recordaba. Puro desperdicio, y estaba justo sobre la ruta del problema.
- Y "ver todos los álbumes" de un artista aterrizaba en la pantalla equivocada por una ruta mal registrada.

---

## ➕ Volver a la cola anterior

Estabas escuchando una playlist, te desviaste a un álbum o a un artista, y ahora **la app te ofrece volver
donde estabas** — a la canción por la que ibas, no al principio.

Un detalle que costó encontrar: el botón de **Reproducir** de un álbum arranca una cola **sin etiqueta de
contexto**; solo el de Aleatorio la pone. Una regla basada en esa etiqueta **no habría saltado nunca** en el
caso que describiste. Así que la regla es otra: salir de una lista con la que estabas sentado hacia algo que
no es una lista.

- De playlist a playlist **no pregunta** — eso es cambiar de idea, no desviarse. De álbum a artista tampoco:
  un álbum está a un toque de volver, y solo lleva etiqueta si lo arrancaste en aleatorio, así que la oferta
  funcionaría a medias.
- La oferta **caduca a los 10 minutos**, y lo que ya está en pantalla **siempre se puede aceptar** — el reloj
  del temporizador no avanza con el móvil en suspensión profunda, así que sin ese margen podía aparecerte un
  botón muerto.
- Se apaga en *Ajustes ▸ Reproductor ▸ Cola*, y el interruptor se consulta **al capturar**, no al ofrecer:
  con él apagado no se guarda nada en ningún momento.
