# Aura Hi-Res Player 0.6.38 — Colas, recomendaciones e IA (parte 2 del plan)

## La música nunca se detiene 🎶
Cuando se acaba una **lista finita** (un álbum, el top de un artista, el radar de novedades, una lista importada o una sola canción), la app **sigue sola con música similar** (radio inteligente). Funciona **incluso recién instalada** sin historial ni inicio de sesión.

## Tocar = reproducir la lista entera
- **Top del artista:** tocar una canción ahora pone **toda esa lista como cola** (como en los álbumes), no solo esa canción.
- **Radar de novedades:** tocar un lanzamiento lo **reproduce directo** en vez de mandarte al álbum. *(Para abrir el álbum o ir al artista: mantén pulsado → "Ver álbum" / "Ver artista".)*

## La IA usa TODA tu biblioteca importada
El Inicio, el autoplay, la radio y el shuffle ahora aprenden también de **toda tu biblioteca importada** de Spotify + YouTube Music (no solo de lo que reproduces dentro de la app), con un peso bien calibrado para que un artista con muchísimas canciones importadas no opaque a tus favoritos reales.

## Aleatorio sin repetir 🔀
El modo aleatorio ahora tiene **memoria anti-repetición**: no vuelve a sonar una canción **hasta agotar la lista** (ideal con miles de "Me gusta"), y luego reinicia el ciclo.

## Detección de video desde el inicio
Las canciones del **inicio (Home)** con videoclip ya muestran el botón de cambiar a video (antes solo desde búsqueda/álbum/artista).

## Bajo el capó (revisado adversarialmente antes de subir)
- B3 radio al terminar: guard anti-doble-disparo (`radioSeedInFlight`), solo en auto-avance/primer play y reproduciendo, índice recalculado en vivo (no trunca tu cola).
- "Mix activo" solo se enciende si de verdad se añadió radio.
- Biblioteca para la IA: consulta acotada con `LIMIT` en SQL y fuera del hilo principal (no más carga completa de 15-20k canciones).
- Cap por artista/género en el sembrado de biblioteca (no infla la normalización).
- Nuevo "Ver álbum" en el menú del álbum (restaura el acceso al álbum desde el radar).
