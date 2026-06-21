# Aura Hi-Res Player v5.7.116

## Las páginas de artista abren al instante
- Antes, al entrar a un artista la app se podía quedar atascada cargando: mientras preparaba la pantalla, se ponía a buscar uno por uno los videos animados (lienzos) de sus canciones, y eso bloqueaba la aparición del resto de la información.
- Ahora esa búsqueda de videos se hace por detrás, en segundo plano. La foto, las canciones populares, los álbumes y la biografía aparecen de inmediato, y el video de fondo se suma cuando esté listo sin hacerte esperar.

## Los artistas ya no se quedan en blanco
- A veces, cuando YouTube no enviaba algún dato del artista (el nombre, la imagen o alguna sección), la app fallaba al armar la página y no mostraba nada.
- Ahora la app tolera esos datos faltantes y muestra siempre lo que sí está disponible, en lugar de quedarse vacía.

## Lo que se hizo (técnico)
- ArtistViewModel: el recorrido que resuelve el lienzo/video del artista (ArtistVideoCanvasProvider.getBySongArtist) se movió a launch(Dispatchers.IO) para que no bloquee el armado de la página.
- YouTube.artist(): se eliminaron las aserciones no-nulas (!!) sobre el título, el thumbnail y las secciones; ahora usan respaldos seguros (cadena vacía y lista vacía) para no lanzar excepción cuando YouTube cambia el formato de la respuesta.
