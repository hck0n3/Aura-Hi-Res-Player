# Aura Hi-Res Player 0.0.4

## Arreglado: tras restaurar un respaldo, la app no reproducía
- Al restaurar, arrancaba la sincronización de tus favoritos y, si esa sincronización se cancelaba, el proceso no se detenía: recorría toda tu biblioteca lanzando errores cientos de veces por segundo, saturaba el CPU y la reproducción dejaba de funcionar.
- Ahora la sincronización se detiene limpiamente al cancelarse. Restaurar un respaldo ya no satura la app ni rompe la reproducción, y las copias futuras tampoco tendrán este problema.

## Lo que se hizo (técnico)
- SyncUtils: los bucles de sincronización ahora re-lanzan CancellationException en lugar de tragársela en `catch (e: Exception)`. Antes, una cancelación dejaba el bucle "vivo" iterando miles de canciones al instante (flood de "Failed to process song / Job was cancelled" + pico de CPU), lo que impedía reproducir justo después de restaurar.
