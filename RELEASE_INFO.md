# Aura Hi-Res Player 0.4.1

## Sincronización YouTube Music: el login ya te devuelve a la selección 🔁
- Antes, al iniciar sesión con Google la app se **reiniciaba** y nunca volvías a la pantalla para elegir qué sincronizar.
- Ahora, igual que la migración de Spotify: **inicias sesión → la app vuelve sola a la pantalla "Sincronizar desde YouTube Music"** para que elijas qué traer (me gusta, álbumes, artistas, suscripciones, playlists). Al terminar, el programa queda con tu contenido.

## Técnico
- Flag `OpenYtmSyncAfterLoginKey`: se marca al pulsar "Iniciar sesión" desde el apartado de sync; el login hace su cold-restart habitual (ProcessPhoenix) y, tras reiniciar, MainActivity navega automáticamente a `settings/ytm_sync`.
