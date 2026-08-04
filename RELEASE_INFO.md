# Aura Hi-Res Player 0.6.141

Corrección rápida sobre 0.6.140: **micro-tirones en Android Auto** y **sugerencias que no pegaban con lo que estabas escuchando**. Además, el aleatorio ahora te pregunta si quieres continuar o empezar de cero.

---

## 🚗 Los micro-tirones en Android Auto

Venían de un efecto secundario de arreglar otra cosa. Hasta 0.6.140, el botón "Aleatorio" del coche **ni siquiera activaba el modo aleatorio**: solo desordenaba la lista una vez. Al arreglarlo (para que el coche tuviera memoria anti-repetición como el teléfono), se activó en las colas más grandes de la app un recálculo completo del orden **en cada cambio de canción**, justo durante la transición suave — y ese trabajo compite con el propio audio.

Ahora ese recálculo **solo se hace cuando algo cambió de verdad** (añadir canciones, tocar el aleatorio, terminar la vuelta). Sin perder ninguna garantía: la canción que entra hereda el orden ya calculado, así que no repite.

## 🎯 La cola inteligente ahora sí sigue lo que escuchas

Tres fallos distintos, dos de ellos de siempre:

- **Lo que reproducías en el coche heredaba las semillas de la última lista abierta en el teléfono.** La radio recomendaba a partir de una playlist que ni estaba sonando. Ahora cada cola del coche siembra desde sus propias canciones.
- **La orientación por género era solo un empujón**: movía una canción unos puestos, pero nunca la apartaba. Con un lote malo, las canciones que no pegaban sonaban igual, solo un poco más tarde. Ahora se van al final de verdad — **sin descartar nada**, para no perder artistas del mismo estilo con etiqueta distinta.
- **El aprendizaje de géneros se moría en silencio**: un fallo de red pasajero se guardaba **para siempre** como "género desconocido". Ahora un fallo se reintenta en la siguiente sesión, y hay un ajuste nuevo para aprender géneros también con datos móviles (desactivado por defecto: sigue funcionando solo con WiFi salvo que tú lo actives).

## 🔀 Aleatorio: continuar o empezar de cero

Al tocar el botón Aleatorio en una lista, álbum o artista **donde ya habías escuchado algo**, te pregunta:

> **Ya escuchaste parte de esta lista.** Llevas 23 de 50 canciones reproducidas. ¿Continuar sin repetirlas, o empezar de cero?

Si no hay nada escuchado, no pregunta nada y funciona igual que siempre. Está en los 13 botones de aleatorio de la app.

Y si una canción ya sonó, **puedes ponerla a mano cuando quieras** — la marca es solo informativa.

## 🎛️ Ecualizador

Tus ajustes se guardan **como los dejaste, aunque no guardes un preset**. Antes, si editabas un valor del ecualizador paramétrico y salías de la pantalla sin que se asentara el cambio, se perdía.

Las salidas de audio con una ecualización asignada mantienen la suya, como hasta ahora.

## 📻 Otros

- La marcación rápida viene **desactivada** por defecto.
- Corregido que una lista pudiera perder su registro de canciones escuchadas al reproducir desde el coche.

---

Gracias por usar Aura.
