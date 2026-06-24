# Aura Hi-Res Player 0.6.23

## Portada en alta calidad para canciones que son video 🖼️
- Cuando reproduces una canción que en realidad es un **videoclip** (formato video), su portada se veía **pobre/pixelada**. Ahora el reproductor carga la miniatura en **alta resolución** (1280×720, sin bordes negros) y solo baja a calidad media si esa versión no existe — así nunca queda en blanco.
- Solo afecta a la **portada grande del reproductor** (las listas siguen ligeras).

## Técnico
- `Thumbnail.ThumbnailImage`: para miniaturas `i.ytimg.com` arranca en `maxresdefault.jpg`; `onError` cae a `sddefault.jpg` (antes recibía `sddefault` de `resize()` y nunca intentaba la HD).
