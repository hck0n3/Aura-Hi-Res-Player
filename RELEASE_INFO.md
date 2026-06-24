# Aura Hi-Res Player 0.6.19

## Videos oficiales del artista 🎬
- En la página de cada artista ahora aparece una sección **"Videos oficiales"** con sus videos oficiales (de YouTube), **reproducibles** con el modo video integrado: tócalos y se ven con sonido.
- Aparece automáticamente cuando hay videos; si no hay, no se muestra (no estorba).

> Nota sobre iTunes: la API de iTunes solo da **vistas previas de ~30s + enlaces a Apple Music**, no un video reproducible. Por eso la fuente que de verdad reproduce es **YouTube**. Si quieres, en una próxima versión añado enlaces "Ver en Apple Music" como descubrimiento extra.

## Técnico
- `ArtistViewModel`: tras cargar la página del artista, un `launch(IO)` busca `YouTube.search(artista, FILTER_VIDEO)`, filtra `isVideoSong` + coincidencia de artista (máx 12) y añade un `ArtistSection("Videos oficiales", …)`. La UI del artista ya renderiza secciones nuevas (LazyRow de `YouTubeGridItem`); reproducir un video usa el flujo existente (`playQueue(YouTubeQueue(WatchEndpoint(videoId)))`) → modo video. Aislado: cualquier fallo de red deja la página intacta.
