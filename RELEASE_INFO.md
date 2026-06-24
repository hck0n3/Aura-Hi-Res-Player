# Aura Hi-Res Player 0.6.4

## Introducción más fluida (solo avanzar) 🎬
- Dentro de **Importar desde Spotify** (primer inicio) hay un botón **"Continuar (siguiente paso)"** abajo — ya no tienes que ir hacia atrás para seguir.
- Dentro de **Sincronizar desde YouTube Music** (primer inicio) hay un botón **"Comenzar a usar Aura"** abajo — terminas desde ahí; la sincronización sigue en segundo plano.
- El paso de **Artistas** ahora se puede **Omitir** (como Géneros). Ningún paso te obliga.

## Fotos de artistas 👤
- Al sincronizar, ahora se **rellenan las fotos** de los artistas que entraron sin portada (p. ej. los que vinieron solo por canciones). Es acotado y suave (no satura tu red), y se completa según vas sincronizando.

## Técnico
- Rutas `settings/spotify_import` y `settings/ytm_sync` con arg `onboarding`; los pasos navegan con `?onboarding=true` (bottomBar para avanzar/terminar). OnboardingArtistsScreen: "Omitir".
- SyncUtils.fillMissingArtistImages() (máx 250/run, throttle 150 ms) tras el sync de artistas; DAO `bookmarkedArtistsMissingImage`.
