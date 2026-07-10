# Aura Hi-Res Player 0.6.84 — Arranque, audio y video más rápidos + Agregar música

## ➕ Nuevo: Agregar música a tus playlists (estilo Apple Music)
- Al final de tus playlists ahora aparece **"Canciones sugeridas"**: 5 canciones basadas en el contenido de esa playlist, cada una con **preview** (la escuchas sin salir) y un botón **`+`** para agregarla al instante. Un icono de **actualizar** regenera las 5.
- Debajo, **"Artistas destacados"**: los artistas de tu playlist en círculos; tócalos para ir a su página.
- Botón **"Agregar música"**: abre una ventana deslizante con **búsqueda global** (todo YouTube Music), **Desde Replay** (lo más escuchado), **Agregado recientemente**, más sugeridas, y **selección múltiple** de tu biblioteca para agregar varias de una vez.

## 🚀 Arranque en gama baja
- **Arranca mucho más rápido:** el escaneo de la caché de reproducción ya no bloquea la pantalla al abrir (se hace fuera del hilo principal). Menos "pantalla de carga eterna" en cajas Android TV, car box y tablets de auto.
- Migraciones de ajustes en una sola escritura, lectura de ajustes sin bloquear, y carga diferida de piezas pesadas al inicio.

## 🎧 Audio
- **Corregido el audio acelerado** (tono agudo) en algunas canciones/videos en equipos de gama baja: ahora si el decodificador del equipo falla con ciertos formatos (AAC/HE-AAC), la app usa el decodificador por software para que suene a la velocidad correcta.
- El sonido **flotante de 32 bits** se mantiene en equipos capaces (ya no se apaga por el Modo Rendimiento).
- El ecualizador se **re-sincroniza** correctamente al cambiar el formato de audio entre pistas.

## ⚡ Canciones que arrancan más parejo
- **Menos espera al iniciar una canción:** la app pre-resuelve las siguientes también en gama baja/TV (antes solo en equipos potentes), guarda mejor y no re-descarga lo ya cacheado.

## 📺 Video que ya no se corta
- **Corregidos los cortes de video** ("como si fallara el internet"): el video ahora **arranca en una resolución que tu conexión aguanta** (según el ancho de banda) en vez de forzar 1080p siempre, así deja de tartamudear en redes flojas. En TV sigue subiendo hasta 1080p cuando la red lo permite.

## 🎤 Letras
- **Ya no cargan la letra de otra canción**, cargan **más rápido** (proveedores en paralelo) y si no hay letra lo dice claro.

## 🎬 Cambio canción → video
- El **primer cambio a video** ya no tarda >5s en equipos capaces (la URL se resuelve antes).

## ✅ Actualización segura
- La base de datos se actualiza sola al abrir (sin perder historial, playlists ni descargas). Suscripción, licencia y demo intactos. Sin tocar el crossfade de 9s ni el motor de audio. Auditado contra crashes con varios modelos.
