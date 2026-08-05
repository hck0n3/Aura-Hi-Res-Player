# Aura Hi-Res Player 0.6.145

Cuatro cosas que reportaste, con su causa exacta encontrada en el código. **Ninguna era lo que parecía.** Y una función nueva que pediste.

---

## 🎨 Cambiar el color del tema ahora repinta la app entera

Elegías uno de los 42 colores y casi nada se movía. Había **dos causas, las dos estructurales**.

- El ajuste de intensidad viene en **"Suave" por defecto**, y en ese modo la única función que aplica la saturación de tu color **se saltaba a sí misma**. Solo llegaba el matiz, y Material lo fijaba en un pastel: los 42 tonos **no podían diferenciarse en otra cosa**.
- **El fondo y todas las superficies estaban escritos a fuego.** El lienzo era `#111A1D` pasara lo que pasara. El tinte derivado de tu color se calculaba y **se descartaba una línea antes de usarlo**.
- **Ahora**: eliges cian y el fondo se vuelve un negro con matiz de cian; eliges rojo, un negro con matiz de granate. Con ellos se mueven la barra de navegación, la barra superior y el mini reproductor.
- Cada superficie **conserva su propio valor y su propia saturación** —su peldaño en la escalera de profundidad— y solo se le sustituye el matiz. Una prueba fija que el fondo **nunca se aclara**. El **modo AMOLED sigue siendo negro puro**.
- **Bug de propina**: el rojo por defecto hacía doble función de "no ha elegido nada", así que elegir **ese color concreto** reactivaba el color automático del fondo de pantalla en vez de aplicarse.
- **Botón de restablecer**, abajo del todo y con confirmación. Devuelve las ocho preferencias de tema, incluidas las dos del modo negro puro **juntas** — separadas se desincronizan.

## 🎯 La cola inteligente deja de mezclar géneros

Contaste que acertaba, fallaba, acertaba y volvía a fallar. **Ese patrón intermitente era la pista.**

- Una canción de **género desconocido puntuaba exactamente cero**: ni premio ni castigo, **invisible al filtro en las dos direcciones**, compitiendo solo por relevancia bruta.
- Y una **cuota de exploración reserva 1 de cada 5 huecos** para artistas nuevos — y un candidato sin género es "nuevo" por definición. Uno mal, uno bien, dos mal seguidos: **era esa cuota**.
- El género desconocido ahora recibe un empujón de **exactamente un puesto**: pierde el empate contra el género correcto **y nada más**. Es seis veces menor que el castigo por género equivocado, así que **"no lo sé" nunca se trata como "está mal"**, y nunca se descarta.
- **Los géneros se aprenden antes de puntuar**, no después de encolar. Tope de 1,5 segundos; si se agota, la app sigue igual — **nunca puede causar un silencio**.
- **Y ahora aprende también con datos móviles.** Antes era solo por WiFi: **en el coche no aprendía nada**, que es justo donde te fallaba. Son consultas diminutas; el ajuste sigue en *Ajustes ▸ Contenido*.

## 🚗 Los micro-cortes en Android Auto

El arreglo de 0.6.141 seguía intacto — **esto era otra cosa**.

- Una función recorría **la línea de tiempo entera en el hilo principal** en cada recálculo del orden. Ya no ocurría por canción, **pero sí en cada añadido de la radio infinita** — sin parar cuando se acaba la lista. Ahora es una sola lectura.
- La ordenación creaba **miles de objetos** por recálculo. En una cola de 5.000 canciones: de 66.438 a **cero**, y de 2,44 ms a 0,99 ms.
- Preparar la transición entre canciones copiaba la cola completa: en esa misma cola, **10.002 accesos menos al reproductor por cada canción**.
- Una **reserva de memoria en el hilo de audio**, en cada bloque de sonido, ya no ocurre en la ruta normal.
- **El crossfade suena exactamente igual**: misma forma, mismos tiempos, las 9 curvas, el modo sin pausas y el mezclado de doble reloj. Solo se abarató **cómo se prepara**, no lo que hace.

## ↩️ El botón de atrás ya no te manda al inicio

Ibas de una playlist a un artista, dabas atrás, y aparecías en el inicio.

- **No era un fallo de navegación: era un gesto oculto.** Existía una función que retrocedía hasta la raíz de la pestaña, atada a la **pulsación larga de la misma flecha de atrás** cuyo toque normal hacía lo correcto — en **51 pantallas**. Sin vibración, sin aviso, sin confirmación.
- Una pulsación un poco lenta y perdías la playlist de la pila. Y como paraba en la raíz **de esa cadena concreta**, el destino cambiaba según de dónde vinieras: por eso parecía que **no tenía lógica**.
- **Eliminado.** Un atajo que nadie descubre y que destruye la navegación en silencio es peor que no tener atajo.
- Volver a la app **desde un acceso directo o desde el widget ya no borra la cadena** de pantallas.
- El grafo de navegación **se reconstruía en cada refresco de pantalla**, justo sobre la ruta del problema. Y "ver todos los álbumes" de un artista aterrizaba en la pantalla equivocada.

## ➕ Volver a la cola anterior

Estabas escuchando una playlist, te desviaste a un álbum o a un artista, y ahora **la app te ofrece volver donde estabas** — a la canción por la que ibas, no al principio.

- Un detalle que costó encontrar: el botón de **Reproducir** de un álbum arranca una cola **sin etiqueta**; solo el de Aleatorio la pone. Una regla basada en esa etiqueta **no habría saltado nunca** en el caso que describiste.
- De playlist a playlist **no pregunta** — eso es cambiar de idea, no desviarse.
- La oferta **caduca a los 10 minutos**, y lo que ya está en pantalla **siempre se puede aceptar**.
- Se apaga en *Ajustes ▸ Reproductor ▸ Cola*.
