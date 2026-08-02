# Aura Hi-Res Player 0.6.140-beta12 — Memoria del aleatorio en toda la app (BETA PRIVADA)

> ⚠️ Build de PRUEBA, solo para el dueño. Prerelease: el actualizador NO la ofrece a nadie más.

beta11 arregló que el aleatorio repitiera. Esta cierra **el resto**: la memoria no existía en media app, y donde existía se guardaba en dos sitios distintos.

## 🔀 Memoria entre días donde no había ninguna
- **"Mi Top"** (memoria separada por periodo, para que Top-semana y Top-histórico no se mezclen) y **"Caché"**.
- **Álbumes** y **artistas**. Además, el menú "···" y la pantalla usan ahora **el mismo** identificador: da igual por dónde entres.
- **"Me gusta", "Descargadas", "Subidas", "Exportadas"**: antes Biblioteca y la tarjeta guardaban en **sitios distintos**, así que lo escuchado por un lado era invisible por el otro.

## 🚗 Android Auto
Lo que reproducías en el coche **se apuntaba en la lista equivocada** — la última que hubieras abierto en el móvil. Ensuciaba una lista que ni estabas oyendo, mientras la que sonaba no aprendía nada. Y su "Aleatorio" era un desorden de una sola vez, con el modo apagado.

Ahora enciende el sistema completo y apunta donde toca, compartiendo memoria con el teléfono.

## ⏱️ Una carrera que ensuciaba los datos
Al tocar otra playlist, el contexto cambiaba **al instante** pero las canciones tardaban segundos en cargar. Todo lo que avanzaba la lista vieja en esa ventana se apuntaba en la nueva.

## 📋 Cola
- El re-anclaje elegía mal cuando una lista tiene **la misma canción dos veces** (rebobinaba a la primera copia), y si la canción actual quedaba filtrada **volvía al principio de la cola**.
- El guardado periódico de posición podía quedar **desincronizado** de la cola guardada, así que al reabrir aterrizabas en otra canción.

## 📱 Interfaz, Cast y tamaño
- La **cola colapsada ya no tapa** la parte baja del reproductor en horizontal y tablet (unos 40 puntos).
- Los **botones flotantes** respetan tu color de texto de Liquid Glass.
- **Cast**: el volumen que cambies **en el altavoz o con el mando de la tele** se refleja en la app.
- **4 MB menos de APK**: se empaquetaba una tipografía que no usaba nadie.

---

## Lo que cazó la revisión (y por eso esta build tardó)
La revisión adversarial encontró un **bloqueante en mi propio código**: usé un campo interno que **nunca se asigna en toda la app** — lleva muerto desde el origen del proyecto. Habría lanzado un error en **cada** reproducción desde Android Auto, y de forma **silenciosa**: el sistema se traga ese fallo sin dejar rastro. El coche habría dejado de reproducir del todo, y en el móvil no se habría notado.

De paso salió algo que te afecta: como ese campo nunca se asignó, **el bloqueo anti-arranque-fantasma (que la app no se ponga a sonar sola cuando se conecta el Bluetooth o el coche) nunca ha estado activo**. Está apuntado como resuelto desde 0.6.104 y era código muerto. Lo he dejado **desactivado a propósito** en esta build: activarlo de golpe podría hacer que el PLAY del volante no arranque la cola tras un reinicio, y eso merece una prueba tuya en el coche, no colarlo dentro de otro arreglo.

Otros 7 hallazgos aplicados, incluido **uno que invalidaba un arreglo mío**: al reabrir, preferir el índice "re-anclado" te dejaba en una canción **anterior**, porque el índice guardado del reproductor es más fresco que el de la cola. Revertido.

---
Compila en ambos sabores, 259/259 pruebas.
