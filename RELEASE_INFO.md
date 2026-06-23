# Aura Hi-Res Player 0.5.1

## Arreglos del primer inicio (onboarding) 🧩
- **Login de YouTube Music ya no se reinicia ni te saca de la página** → ahora sí puedes iniciar sesión. (El causante era un flag que reaccionaba al instante de tocar "Iniciar sesión" y te mandaba a la selección antes de tiempo; ahora se lee una sola vez al arrancar, tras el reinicio del login.)
- **Spotify y YouTube quedan separados**: la introducción solo migra **Spotify**; **YouTube Music** se sincroniza cuando quieras desde **Ajustes ▸ Importar ▸ Sincronizar desde YouTube Music** (ahí el login funciona bien).

## Técnico
- MainActivity: la apertura post-login de la pantalla de sync YT pasa a `LaunchedEffect(Unit)` (lectura única del flag al arrancar vía DataStore), en vez de un efecto reactivo que se disparaba al marcar el flag en sesión.
- OnboardingSpotifyScreen: quitado el botón "Sincronizar YouTube Music" (queda solo Spotify + una mención de dónde está YT).
