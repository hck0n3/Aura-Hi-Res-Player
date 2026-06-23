# Aura Hi-Res Player 0.3.0

## Sincronización con YouTube Music (hub manual) 🔁
- Nuevo apartado en **Ajustes ▸ Cuenta ▸ "Sincronización con YouTube Music"** (visible al iniciar sesión). Tú eliges qué traer y cuándo — nada se sincroniza solo:
  - **Sincronizar todo** (me gusta, álbumes, artistas, suscripciones, playlists, biblioteca)
  - **Me gusta (canciones)**
  - **Álbumes favoritos**
  - **Artistas y suscripciones**
  - **Playlists guardadas**
  - **Biblioteca (canciones)**
  - **Subidas (canciones y álbumes)**
- Así haces que tu contenido de la cuenta **aparezca** cuando quieras, en un solo lugar ("todo en uno").
- Se menciona en la **introducción** (paso de migración) y en Bienvenida/Acerca de.

## Notas
- Es un **hub manual** (lo pediste así): no hay auto-sync al loguear; tú disparas cada sincronización.
- Próximo refinamiento posible: elegir **qué playlists** mostrar de forma individual.

## Técnico
- AccountSettingsViewModel: `syncAll/syncLikedSongs/syncLikedAlbums/syncArtists/syncPlaylists/syncLibrarySongs/syncUploads` sobre las funciones `*Suspend` ya existentes de SyncUtils.
- AccountSettingsScreen: grupo "Sincronización con YouTube Music" (solo logueado) con acción + toast por tipo.
- OnboardingSpotifyScreen / WelcomeDialog / AboutScreen: mención del hub.
