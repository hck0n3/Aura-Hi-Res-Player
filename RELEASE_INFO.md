# Aura Hi-Res Player 0.4.3

## Tus artistas de YouTube Music ahora salen como seguidos 👤
- Al sincronizar desde YouTube Music, **todos los artistas que se importan** (no solo tus suscripciones de canal, también los de tus "me gusta", álbumes y biblioteca) quedan **marcados como seguidos** y aparecen en "tus artistas" — igual que la importación de Spotify.
- Aplica al sincronizar **Artistas y suscripciones**, **Me gusta**, **Álbumes** o **Sincronizar todo**.

## Técnico
- Nuevo DAO `followArtistsWithContent(now)`: marca `bookmarkedAt` en todo artista que tenga canciones o álbumes en la biblioteca y no esté ya seguido.
- Se llama al final de `executeSyncArtistsSubscriptions`, `executeSyncLikedSongs` y `executeSyncLikedAlbums` (y por tanto también en "Sincronizar todo"). La migración selectiva ya insertaba artistas como seguidos.
