# Aura Hi-Res Player 0.6.131 — 4 arreglos a tu oído: silencios largos, bajada audible, volumen parejo, sin rachas de artista

## 🔇 Canciones con silencios MÁS largos: ahora sí transicionan (causa raíz profunda)
Los silencios largos fallaban en canciones de alta resolución (24-bit): el sistema de audio del teléfono **saca al detector de la cadena** en ese formato — no era ajustable, era invisible. Solución de fondo: ahora **medimos directamente en la salida de audio** (funciona con TODOS los formatos, 16-bit y hi-res). Da igual la calidad y da igual cuánto dure el silencio: la transición dispara al acabar la música.

## 🎚️ La que termina ahora SÍ se oye bajar (tu reporte exacto)
Tenías razón: la curva anterior mantenía a la canción saliente casi a volumen pleno media transición — su bajada quedaba escondida bajo la subida de la nueva. Curva rediseñada: **las dos se mueven juntas y audiblemente desde el inicio** — la que sale baja claro mientras la nueva sube, resuelto al 85% del cruce (el final es ya solo la nueva).

## 🔊 El cruce ya no suena MÁS fuerte de lo habitual
El volumen combinado durante la transición queda **matemáticamente capado al de una sola canción** — nunca un empujón de sonoridad en medio del cruce.

## 🎵 Entrada manual aún más gentil
Rampa de ~1.1s con la curva más suave que existe (suavizado doble en ambos extremos): el arranque es imperceptible, sube por el medio y aterriza exacto en tu volumen.

## 🔀 Aleatorio sin rachas del mismo cantante (tu reporte)
El orden inteligente agrupaba las canciones de tus artistas favoritos → rachas de un solo cantante. Ahora hay **espaciado por artista**: nunca dos seguidas del mismo (mientras haya variedad disponible), en playlists y biblioteca.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — tu registro ya trae el diagnóstico por etapas: repórtalo cuando pase.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
