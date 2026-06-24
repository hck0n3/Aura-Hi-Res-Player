# Aura Hi-Res Player 0.6.4

## Introducción más fluida (solo avanzar) 🎬
- Dentro de **Importar desde Spotify** (en el primer inicio) ahora hay un botón **"Continuar (siguiente paso)"** abajo — ya no tienes que ir hacia atrás para seguir.
- Dentro de **Sincronizar desde YouTube Music** (en el primer inicio) hay un botón **"Comenzar a usar Aura"** abajo — terminas desde ahí; la sincronización sigue en segundo plano.
- El paso de **Artistas** ahora se puede **Omitir** (como Géneros y los demás). Ningún paso te obliga.

## Técnico
- Rutas `settings/spotify_import` y `settings/ytm_sync` con arg opcional `onboarding`; los pasos del onboarding navegan con `?onboarding=true` y muestran el botón de avanzar/terminar (bottomBar). OnboardingArtistsScreen: botón "Omitir".
