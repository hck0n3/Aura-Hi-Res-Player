# Aura Hi-Res Player 0.5.2

## YouTube Music en la introducción — paso propio y funcionando 🎬
- Ahora el primer inicio tiene **dos pasos de migración SEPARADOS**: primero **Spotify**, luego **YouTube Music** (cada uno en su pantalla).
- En el paso de YouTube: tocas **"Conectar YouTube Music"** → inicias sesión → la app se reinicia (es normal en el login de Google) y **vuelve sola a la pantalla para elegir qué sincronizar** (me gusta, álbumes, artistas, suscripciones, playlists). Si ya estás logueado, va directo a elegir. Puedes **Omitir**.
- Ya **no se reinicia en bucle ni te saca del login** (arreglado en 0.5.1): el regreso a la selección se hace una sola vez tras el reinicio.

## Técnico
- Nueva `OnboardingYouTubeScreen` (ruta `onboarding_youtube`), encadenada: artists → genres → spotify → **youtube** → home.
- Login: marca `OpenYtmSyncAfterLoginKey` y abre el login; MainActivity lo lee una sola vez al arrancar y navega a `settings/ytm_sync`. Si ya hay sesión, va directo. "Omitir" limpia el flag.
- OnboardingSpotifyScreen: "Continuar" pasa al paso de YouTube (se le quitó el prompt de Google de Spotify).
