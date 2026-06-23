# Aura Hi-Res Player 0.3.1

## Sincronización de YouTube Music: ahora en su sitio 🔁
- El apartado para **elegir qué sincronizar de YouTube Music** se movió a **Ajustes ▸ Importar ▸ "Sincronizar desde YouTube Music"** (junto a "Importar desde Spotify"), para que todo tenga orden.
- Ya **no** está en la pantalla de Cuenta.
- También aparece en el **primer inicio** (paso de migración): botón "Sincronizar YouTube Music". Si aún no iniciaste sesión, el propio apartado te ofrece iniciar sesión.

## Técnico
- Nueva pantalla `YtmSyncScreen` (ruta `settings/ytm_sync`), auto-gestiona el login (si no, botón de iniciar sesión).
- Entrada en BackupAndRestore ▸ Importar + botón en OnboardingSpotifyScreen.
- Quitado el grupo de sync de AccountSettingsScreen (se conservan los métodos del ViewModel).
