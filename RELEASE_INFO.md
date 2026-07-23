# Aura Hi-Res Player 0.6.120 — El aleatorio ya NO repite + transiciones más musicales

## 🔴 Corregido: el aleatorio repetía canciones que ya sabía reproducidas
Las marcas ✓ y el contador estaban bien, pero el orden las ignoraba. Eran **tres** caminos rotos, todos arreglados:
- **Al entrar a una lista con el aleatorio ya encendido**, la memoria guardada nunca se cargaba al orden — la app "sabía" qué habías oído pero barajaba como si nada. Ahora la carga siempre.
- **El botón "Aleatorio" de las listas ni siquiera encendía el modo aleatorio**: solo revolvía una vez, sin memoria y sin anotar lo reproducido. Ahora el botón activa el aleatorio mejorado de verdad (y la primera canción sigue siendo al azar).
- **Al agotarse la lista con la transición suave activada**, en vez de continuar con la radio infinita volvía a repetir la playlist. Ahora detecta el fin del ciclo y siembra la radio a tiempo — el fundido aterriza en música nueva.

## 🎶 Transiciones más agradables (el fundido de 9s que te gusta, intacto)
- **Canciones con silencios largos al final ya hacen la transición**: la app detecta cuándo terminó la MÚSICA (no el archivo) y arranca el fundido ahí. Se acabó el "se queda callada y luego entra la otra de golpe".
- **Nuevo estilo de transición opcional "Ascenso"** (Ajustes ▸ Reproductor ▸ Estilo de transición): la canción que entra sube a volumen pleno rápido mientras la que sale baja suave — el estilo de segue de radio/DJ. El estilo por defecto no cambia.
- Por dentro: 6 pruebas nuevas fijan las matemáticas de las 5 curvas de fundido para que nunca se rompan sin avisar.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — necesito tu registro en esa calidad.
- **Volumen Seguro** sube máximo +3 dB.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
