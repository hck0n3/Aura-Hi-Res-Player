# Aura Hi-Res Player 0.6.178

Arreglo urgente: los álbumes vuelven a reproducirse (regresión de 0.6.176). También Auto-EQ, biblioteca del artista, vídeo manual, canvas y más.

---

## Crítico — reproducción

- Tocar una canción de un álbum vuelve a sonar **esa** canción (el filtro “solo música” ya no vacía álbumes, playlists ni Me gusta).
- La radio automática sigue filtrando tutoriales; las colas que tú eliges no.

## Ecualizador

- Desactivar Auto-EQ **solo** quita Auto-EQ; tu curva manual y EQ ON se quedan.
- Los tres presets de nombre más largo van en la última fila.
- Menos scroll de la pantalla al arrastrar bandas.

## Artista / biblioteca

- “Tu biblioteca” incluye Me gusta, temas en tus playlists, locales y descargas.

## Vídeo y portadas

- Toque manual de un vídeo entra en modo vídeo aunque el modo sea canción / alto rendimiento.
- Al salir de Picture-in-Picture con vídeo, se abre el reproductor con el vídeo.
- Si un álbum no tiene canvas animado pero el artista sí, la portada usa el motion del artista (Interfaz nueva y clásica).
- FFT del EQ: rebind al cambiar a modo vídeo (menos “sin señal”).

## Otros

- Mejor recuperación ante error de contenedor 3003 / NoDeclaredBrand.
- Avisos: se refrescan al volver a la app (edita `announcements.json` en GitHub main).
