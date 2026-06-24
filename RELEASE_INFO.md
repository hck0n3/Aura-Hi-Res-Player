# Aura Hi-Res Player 0.6.3

## El modo Video ahora SÍ muestra el video 🎬
- Antes, al cambiar a "Video" decía que lo hacía pero solo sonaba el audio: el cliente de reproducción (el que da buen audio) solo entrega streams **separados** (video y audio por aparte), así que la URL de video volvía vacía y caía a audio.
- Ahora, **solo en modo video**, se pide el stream **combinado** (video+audio) a un cliente que sí lo entrega → el video se reproduce en la carátula. (En canciones que tengan video disponible.)
- No afecta el audio normal (el cambio está aislado al modo video).

## Nota
- El video sale del stream combinado (~360p en muchos casos). Subir la calidad requiere fusionar los streams separados (un cambio mayor) — lo dejo para después si quieres mejor resolución.

## Técnico
- YTPlayerUtils: nuevo `VIDEO_CLIENT = TVHTML5` usado en el request principal cuando `preferVideo=true` (devuelve formatos muxed itag 18/22); el camino de audio sigue con ANDROID_VR.
