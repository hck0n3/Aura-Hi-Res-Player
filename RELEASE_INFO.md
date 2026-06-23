# Aura Hi-Res Player 0.4.2

## Arreglo real: el login de YouTube Music ahora SÍ te lleva a la selección 🔁
- En 0.4.1 el flag se limpiaba antes de tiempo y eso **cancelaba** la navegación → la app se quedaba en el inicio y no te dejaba elegir.
- Ahora, tras iniciar sesión y reiniciar, la app **abre sola la pantalla "Sincronizar desde YouTube Music"** para que elijas qué traer (me gusta, álbumes, artistas, suscripciones, playlists), igual que Spotify.

## Técnico
- MainActivity: en el LaunchedEffect del flag se **navega primero y se limpia el flag después** (limpiar antes cambiaba la key del LaunchedEffect y cancelaba el delay/navigate). Delay 700 ms para asegurar el grafo de navegación.
