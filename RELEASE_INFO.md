# Aura Hi-Res Player 0.6.41 — Sincronización por playlist + diferencial (parte 5, fin de sincronización)

## Sincronizar "Me gusta" y "Subidas" con un toque
En las listas **Me gusta** y **Subidas** ahora hay la opción **"Sincronizar"** (menú ⋮) para traer los últimos cambios de YouTube Music al momento (solo con sesión iniciada). Las playlists de YouTube ya tenían su botón de sincronizar.

## Sincronización diferencial (solo lo nuevo/cambiado) ⚡
La sincronización de **"Me gusta"** ya **no reescribe las 4000+ canciones en cada pase**. Antes recalculaba la fecha de cada canción cada vez (y esperaba por cada una), volviéndose lenta. Ahora **solo toca las canciones nuevas o que cambiaron** y se salta las que ya están igual → mucho más rápida y suave, sin tocar la batería de más.
*(Las playlists y la biblioteca ya eran diferenciales: si nada cambió, no se reescribe nada.)*

## Bajo el capó (revisado antes de subir)
- "Me gusta": la decisión de escribir se toma en el hilo correcto (la transacción de la BD es asíncrona), conservando la fecha original de cada canción (orden estable) y limitando el ritmo solo en escrituras reales.
- Nuevo `onSync` opcional en el menú de auto-playlists (Me gusta/Subidas), solo con sesión.
