# Aura Hi-Res Player 0.0.1

## Tus favoritos ya no desaparecen al sincronizar
- Antes, al abrir la app, la sincronización con YouTube podía quitar canciones de "Favoritos" y de tu biblioteca aunque sí las tuvieras marcadas: comparaba contra una respuesta de YouTube que viene recortada y borraba todo lo que no apareciera en ella.
- Lo mismo le pasaba a los álbumes favoritos y al contenido de algunas listas.
- Ahora la sincronización es solo aditiva: nunca borra ni le quita el "me gusta" a algo que tengas guardado localmente; como mucho lo sube a tu cuenta.
- ¿Se te bajaron los favoritos? Actualiza a esta versión, entra a Biblioteca → Favoritos y sincroniza: tus "me gusta" siguen en tu cuenta de YouTube y se restauran solos.

## Relanzamiento como versión estable 0.0.1
- Reiniciamos el número de versión a 0.0.1. Es solo el número visible; tu app se actualiza con normalidad y sin desinstalar.

## Lo que se hizo (técnico)
- SyncUtils.executeSyncLibrarySongs: ya no llama a toggleLibrary() sobre canciones locales ausentes de la página remota de "liked videos" (esa página viene paginada/recortada, y toggleLibrary() además limpiaba liked/likedDate, lo que des-likeaba miles de favoritos). Ahora es aditivo: sube a la cuenta los temas locales que falten, nunca los borra.
- SyncUtils.executeSyncPlaylist: una respuesta remota vacía ya no vacía la playlist local (casi siempre es un fetch transitorio).
- SyncUtils.executeSyncSavedPlaylists: ya no des-marca playlists guardadas que falten de la página remota.
