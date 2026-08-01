# Aura Hi-Res Player 0.6.140-beta7 — Correcciones grandes + fluidez (BETA PRIVADA)

> ⚠️ **Build de PRUEBA, solo para el dueño.** Sale como *prerelease*: el actualizador NO la ofrece a nadie más. Instálala a mano. Todo lo de 0.6.139 sigue igual.

## 🔧 Correcciones
- **Streaming reparado** — YouTube rotó su reproductor; el descifrado nuevo ya está VIVO para todos (config remota, sin actualizar). Las canciones que fallaban vuelven a sonar.
- **poToken** — un archivo necesario faltaba en las compilaciones (estaba ignorado por git). Corregido: menos fallos y resoluciones más rápidas.
- **Reconocer canción** — reproducía otra canción distinta a la reconocida (usaba un id frágil de Shazam). Ahora suena exactamente la que reconoció; si no hay match fiable, avisa.
- **Letra palabra-por-palabra** — se desincronizaba en canciones con letra por líneas (inventaba el ritmo). Ahora ilumina la línea a tiempo; el barrido real se reserva para las que traen tiempo por palabra.

## 🎵 Migración de playlists
- **Requiere sesión de YouTube Music** (ahí se crea la playlist): si no la tienes, la pantalla te lo pide primero — ya no eliges playlist para fallar al final. Tidal / Deezer / Archivo / Apple como en betas previas.

## 📚 Biblioteca y playlists
- **Buscar entre tus playlists** — campo de búsqueda en la pestaña Playlists (insensible a mayúsculas y acentos).
- **Sincronizar una playlist a mano** — "Sincronizar ahora" en cada playlist de YouTube. (Corregido de paso un fallo que podía vaciar una lista en una sincronización con respuesta vacía momentánea.)

## 🔗 Enlaces de YouTube
- Aura abre más tipos: canción (watch/embed/v/shorts/youtu.be/vnd.youtube), playlist, álbum, artista, búsqueda — varios se caían. Ajuste para aparecer en el selector "Abrir con". *(Android no deja reemplazar a la fuerza a la app de YT Music; ponla por defecto en Ajustes ▸ Apps ▸ Aura.)*

## ⚡ Fluidez (todas las gamas)
- Fondos animados del reproductor **no se dibujan al minimizar** (gasto invisible eliminado).
- Fondo del **mini-reproductor** respeta Alto Rendimiento y freno térmico.
- **Piso por hardware**: gama baja/ultra-baja nunca corre shaders animados ni 2º decodificador, aunque apagues Alto Rendimiento — fluida en equipos débiles.

---
Verificado por auditoría adversarial (7 áreas): sin crashes, sin pérdida de datos. 206/206 tests.
