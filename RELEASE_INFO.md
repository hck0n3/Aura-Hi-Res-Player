# Aura Hi-Res Player 0.6.13

## El video ya tiene tamaño (debería verse) 🎥
La causa de que NO se viera nada (ni siquiera el texto de diagnóstico) era de **layout**, no de
stream: el Thumbnail usa `animateContentSize`, y al ocultar la portada, el video quedaba con
**tamaño 0**. Por eso solo se oía el audio del clip y se veía el canvas de fondo.

Arreglos:
- El video ahora usa `fillMaxSize` → ocupa el área real y **se ve** (con su texto de diagnóstico).
- **Atrás (back)** mientras hay video → sale del modo video y **reanuda el audio** (ya no se queda mudo).
- El video sigue continuando desde la posición de la canción (no reinicia).

### Cómo probarlo
Canción **con videoclip** → botón **video** → debe **verse el video** (arriba a la izquierda sale el
texto de diagnóstico). Si se ve bien, en la próxima quito ese texto. Si no, **mándame ese texto**.

## Técnico
- `Thumbnail`: `MusicVideoPlayer` pasa de `matchParentSize` a `fillMaxSize` (con `animateContentSize`
  un hijo matchParentSize colapsa a 0). `BackHandler(enabled = videoModeOn)` → `exitVideoMode()`.
