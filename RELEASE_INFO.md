# Aura Hi-Res Player 0.6.15

## Modo video reintegrado en el motor principal 🎥 (un solo reproductor)
El video ahora es una **pista del mismo reproductor** que lleva el audio (ya no hay un reproductor aparte). Eso arregla de raíz lo que reportaste:
- ✅ **Sin doble audio**, nunca.
- ✅ Al **minimizar**, el **audio sigue** en segundo plano (con notificación) — sin cortes.
- ✅ La **barra de progreso adelanta/retrocede** el video.
- ✅ **Sin parpadeo** de la barra.
- ✅ **Sigue en video** al cambiar de canción (hasta que tú lo apagues).
- ✅ Controles nativos (play/pausa, siguiente, anterior) controlan lo que ves.
- ✅ Rotar no detiene el video.
- ✅ La **letra** se desactiva mientras ves el video.

> Nota: el video usa el stream combinado (con su audio, sin EQ). Si una canción no tiene ese formato, sale "Video no disponible" y sigue en audio. Entre canciones puede haber un breve cargado al entrar el video (lo afino luego).

## Mejoras traídas de Echo (upstream) ✨
- **Portada de playlist:** si YouTube rechaza la subida (403), ahora se guarda **en local** en vez de fallar en silencio.
- **Doble-tap al centro** de la carátula = **play/pausa** (los lados siguen siendo adelantar/atrás).
- **Exportar playlist a CSV** (además del JSON).
- **Copia de seguridad** se crea en segundo plano (no congela con bibliotecas grandes).
- Timeouts de red de reproducción ahora van a **recuperación** (menos cuelgues de carga).
- Quitado un borrado de base de datos heredado del arranque (protege tu historial/estadísticas).

## Técnico
- `MusicService`: `videoDataSourceFactory` (OkHttp + User-Agent por cliente, sin caché) + `createMediaSourceFactory` que enruta la pista de video; `swapToVideo`/`restoreVideoTrackToAudio`/`applyVideoToCurrent`; sticky en `onMediaItemTransition`; error en pista de video → `exitVideoMode` (fallback a audio); crossfade desactivado en video; `findFormat(preferVideo)` solo muxed.
