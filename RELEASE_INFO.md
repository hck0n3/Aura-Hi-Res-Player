# Aura Hi-Res Player 0.6.67 — TODAS las canciones al mismo volumen, sin saltos ni "de golpe"

## 🔊 El nivelado ahora es invisible (fix de raíz)
El problema venía de dos cosas: la canción **empezaba a sonar antes** de saber su volumen y se corregía a mitad ("de golpe"), y el nivel **no quedaba fijado**, así que cualquier cosa (darle ❤️, la precarga del siguiente tema cerca del final, una descarga) lo movía.

Arreglado de raíz:

- **La canción ya entra nivelada.** El volumen de cada tema se resuelve y se **precarga ANTES** de que suene el primer instante → no hay subidón al empezar ni corrección a los pocos segundos.
- **En el crossfade, el tema entrante entra a SU nivel** desde el primer momento (antes entraba al nivel del tema saliente y luego saltaba).
- **El nivel del tema que suena es INMUTABLE.** Una vez puesto, **nada lo cambia a mitad**: ni darle/quitar ❤️, ni la precarga cerca del final, ni una descarga, ni apagar la pantalla. La única corrección permitida es la de los primeros segundos (cuando llega el dato real de YouTube), y solo si de verdad cambia.

Resultado: todas las canciones suenan al mismo volumen, las bajas se suben y las altas se bajan **sin que se note**, y darle ❤️ ya no toca el volumen.

> Nota técnica: el pre-nivelado del crossfade se hace desde memoria (sin lecturas de disco en el hilo de audio), para no cortar la transición. Pasó por revisión adversarial multi-agente.

## 👉 Para probar
- Pon canciones de volúmenes muy distintos en cola → deben entrar todas igual de fuerte, sin ajuste audible al empezar ni en el crossfade.
- Dale ❤️ y quítalo a mitad de una canción (sobre todo una que venía baja y se subió) → el volumen **no** debe moverse.
- Deja terminar una canción con crossfade → no debe bajar/saltar el volumen cerca del final.
