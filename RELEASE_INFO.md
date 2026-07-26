# Aura Hi-Res Player 0.6.124 — Aleatorio auditado a fondo + Cuentas + Portadas + 9 transiciones

## 🔀 Aleatorio: auditoría completa — 8 arreglos (tus repetidas)
Auditamos TODA la lógica del aleatorio mejorado atacándola por 3 frentes. Lo que se corrigió:
- **La canción de arranque ya nunca es repetida**: al activar el aleatorio, la primera canción se elegía al azar entre TODAS (a mitad de ciclo, ~50% de probabilidad de arrancar con una ya oída — tu queja exacta). Ahora siempre abre con una que NO has oído, mientras queden.
- **El menú "⋯ → Aleatorio" ahora sí usa la memoria** (antes barajaba una vez y ya, sin recordar nada — otro de tus caminos).
- **Lo que escuchas SIN aleatorio también cuenta**: si oyes media playlist en orden y luego activas el aleatorio, ya no te repite esas canciones.
- Saltar a mano al final de la lista ya no borra la memoria de una playlist a medias.
- Quitar canciones de la cola ya no dispara un borrado de memoria en falso.
- Playlists con la misma canción dos veces ya no la suenan doble por ciclo.
- Cerrar la app ya no resucita la primera canción como "no oída".
- "Ocultar explícitas" ya no apagaba en silencio la memoria de la biblioteca.

## 👤 Cuentas: todas en un solo lugar
**Last.fm y ListenBrainz** entran al hub de Cuentas junto a YouTube Music y Spotify: ves el estado de cada una, conectas y cierras sesión desde ahí mismo.

## 🖼️ Portadas de playlists arregladas
- Si la portada original muere (enlace caducado, archivo purgado), ahora cae a un **mosaico con las carátulas de sus canciones** en vez de quedarse sin imagen.
- Las portadas personalizadas se guardan en almacenamiento **permanente** (antes Android podía borrarlas).
- Las playlists de YouTube refrescan su portada al abrirlas.

## 🎚️ 9 estilos de transición — pruébalos todos
4 nuevos en Ajustes ▸ Reproductor ▸ Estilo de transición:
- **V (sale, luego entra)** — la vieja se apaga del todo y la nueva nace justo después; nunca se pisan.
- **Logarítmica (muere de verdad)** — la bajada que el oído percibe como pareja, estilo radio/mastering.
- **Respiro profundo (pausa marcada)** — un respiro audible entre canciones, sin llegar al silencio.
- **Ultra suave (ganancia constante)** — la más gentil: suma exacta de volúmenes, arranque y aterrizaje imperceptibles.
El estilo por defecto no cambia. Elige el que más te guste al oído.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — necesito tu registro en esa calidad.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
