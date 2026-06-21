# Aura Hi-Res Player v5.7.93

## ARREGLO IMPORTANTE: la música no arrancaba tras restaurar un respaldo
- Corregido el problema por el que, tras restaurar un respaldo grande, las canciones no empezaban a reproducirse.
- Causa: el nuevo motor de gustos cargaba TODO tu historial de escucha (con todas sus relaciones) varias veces, y en historiales grandes eso saturaba la base de datos/memoria y bloqueaba el arranque de la canción.
- Ahora el motor solo usa tu historial reciente (acotado), ya no se calcula al pulsar play, y el shuffle inteligente no recorre colas enormes en primer plano. Reproducción restaurada.
