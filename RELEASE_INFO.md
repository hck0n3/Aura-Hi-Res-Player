# Aura Hi-Res Player 0.6.1

## Arranque de reproducción un poco más rápido ⏱️
- La música empieza a sonar en cuanto hay ~0.5 s en búfer (antes 1 s), así arranca antes, sobre todo en teléfonos de gama media (p. ej. Honor 90 Lite).
- Nota honesta: la mayor parte del retraso en esos equipos es la resolución del stream de YouTube en su CPU (descifrado de firma), que es inherente al método y no se puede acelerar de forma segura. Este cambio ayuda en lo que sí depende de la app.

## Técnico
- MusicService DefaultLoadControl: `bufferForPlaybackMs` 1000→500, `bufferForPlaybackAfterRebufferMs` 2000→1000.
