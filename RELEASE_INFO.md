# Aura Hi-Res Player v5.7.115

## Los álbumes ahora abren al instante
- Antes, cada vez que entrabas a un álbum la app volvía a descargar TODA su lista de canciones desde internet, pidiendo varias "páginas" seguidas. Eso hacía que tardara en abrir, sobre todo en álbumes o recopilaciones muy grandes.
- Ahora, si ya habías abierto ese álbum antes, se muestra al momento usando lo que ya quedó guardado y solo refresca los datos por detrás, sin hacerte esperar.

## Las sugerencias de búsqueda de YouTube vuelven a aparecer
- A veces el buscador no mostraba ninguna sugerencia porque YouTube cambia el lugar donde las coloca y la app las buscaba siempre en un sitio fijo.
- Ahora la app las recoge de todo el resultado, así que las sugerencias aparecen siempre, sin importar el orden en que YouTube las mande.

## La primera canción suena más rápido
- Al abrir la app, el motor que prepara la reproducción tardaba en arrancar (esperaba 2,5 segundos antes de empezar a prepararse).
- Ahora ese arranque se redujo a medio segundo, así que la primera canción que pones después de abrir la app empieza mucho antes. Las siguientes ya iban rápidas gracias a la precarga automática.

## Lo que se hizo (técnico)
- AlbumViewModel: si el álbum ya está cacheado en la base de datos, la actualización vía YouTube.album() se hace con withSongs = false (solo metadatos, sin bloquear).
- YouTube.albumSongs: maxRequests reducido (de 50 a 1) para no estrangular la carga de álbumes enormes.
- YouTube.searchSuggestions: en vez de un índice fijo (getOrNull(1)), ahora recolecta los musicResponsiveListItemRenderer con flatMap sobre todas las secciones de la respuesta.
- MusicService: prewarm del PoToken reducido de 2500 ms a 500 ms para que la extracción de streaming esté lista casi de inmediato.
