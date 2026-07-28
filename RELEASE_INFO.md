# Aura Hi-Res Player 0.6.136 — Revisión total: ~127 fallos auditados, los graves corregidos

Auditoría completa de **todas las funciones, características y botones** de la app (4 informes independientes, cada hallazgo verificado con archivo y línea). Esto es lo que estaba roto y ya no lo está.

## 🔴 La raíz de media docena de fallos: el avance automático se saltaba avisos internos
Con la transición suave activada (el ajuste por defecto), cada cambio automático de canción usa un camino que **esquivaba el aviso interno de "empezó otra canción"**. Todo lo que dependía de ese aviso llevaba tiempo sin funcionar, en silencio:
- **Scrobbling a Last.fm / ListenBrainz** — no registraba los cambios automáticos.
- **SponsorBlock** (saltar segmentos sin música) — nunca obtenía los segmentos de las canciones que entraban solas.
- **Chromecast** — se quedaba en la canción anterior en cada avance.
- **Playlists largas** — no cargaban su página siguiente, así que la lista se cortaba y saltaba a la radio infinita en vez de continuar.
- **Widget, notificación y Android Auto** — mostraban título, artista y portada de la canción **anterior** mientras la barra avanzaba con la nueva.
- **Velocidad, tono y salida de audio elegida** — se reseteaban en cada canción.
- **Precarga de la siguiente canción** — dejaba de re-armarse (arranques más lentos en cadena).

## 🛟 Fallos que perdían o estropeaban datos
- **"Me gusta" en bloque** (selección de YouTube) reconstruía mal la ficha: ponía el tiempo reproducido a **0** y **sacaba canciones de tu Biblioteca**. Corregido: ahora conserva estadísticas, fecha, y todo lo demás.
- **Borrado en bloque** dentro de una playlist dejaba una canción **no seleccionada** teletransportada al final de la lista.
- **Doble alta**: cada canción se añadía **dos veces** a las playlists sincronizadas con YouTube.
- **"Me gusta" masivo** desde la cola no guardaba nada si la canción aún no estaba en la base de datos.
- **Un toque ejecutaba la acción dos veces** ("Reproducir a continuación", "Añadir a la cola", "Descargar", "Compartir", "Iniciar radio").

## 🔘 Botones que no existían o no hacían nada
- **Vuelve "Reproducir"** en el menú ⋯ de álbumes y playlists (solo salía [Aleatorio][Compartir]).
- **Widget de playlists**: el botón de reproducir de cada tarjeta ya funciona, y tocar una tarjeta ya te lleva a su destino.
- **Widget de tocadiscos**: ya se actualiza al colocarlo o redimensionarlo.
- **Atajos del icono de la app** (Buscar / Biblioteca): ya funcionan con la app abierta, no solo al abrirla de cero.
- **Artistas seguidos** que nunca aparecían en Biblioteca (según cómo se hubieran creado) ya se listan.
- **Aleatorio** en Mi Top, En caché y pantallas de artista: ahora sí enciende el modo aleatorio de verdad.

## 🎚️ Ajustes que mentían
- Dos pestañas (Mix y Álbumes) **compartían la misma ordenación** por una clave duplicada.
- Valores por defecto que no eran los que usaba el motor: **precarga** (10 → 2 real), **historial** (1 → 30 segundos real, ahora con la unidad escrita), **cachés** (512/1024 → 2048 e ilimitado reales).
- **Tema dinámico**: su interruptor nunca llegaba a mostrarse, y ninguna paleta salía marcada.
- **Notificaciones de actualización**: el interruptor no las detenía.
- **Liquid Glass "en el reproductor"**: no dibujaba nada pero **gastaba batería**; ahora está desactivado y etiquetado con honestidad.
- **Cuentas** decía "scrobbling activo" mientras el **Ahorro de datos** lo estaba bloqueando.
- **"Entrada suave al cambiar de canción"** solo era visible con la transición activada, aunque funciona siempre.
- El **modelo de IA** no se podía escribir justo con el proveedor "Personalizado", que es cuando hace falta.

## 🚫 Ya no se queda todo en blanco
Cinco pantallas trataban un fallo de red como "sigue cargando" → esqueleto eterno sin salida. Ahora **playlists online, artista y álbum** muestran el error con botón de **Reintentar**. Una playlist online vacía ya muestra su cabecera y sus botones.

## 👥 Listen Together y Last.fm
- El **enlace de invitación** apuntaba al dominio de otro proyecto (caído): nadie podía entrar. Ahora abre Aura de verdad.
- "Esperando aprobación" ya no se queda colgado para siempre si falla la conexión.
- La **lista de servidores** ya no la reemplaza en cada arranque el repositorio de un tercero; el servidor de Aura siempre está y va primero.
- **Last.fm** daba por bueno todo scrobble: su API responde "correcto" con el error dentro del cuerpo. Ahora los fallos se detectan y quedan registrados.

## Recordatorio
- Si un cruce no suena perfecto: **Ajustes ▸ Registros** → compartir → mandar.
- **Cortes a mitad de canción** (Lossless/Saavn): mismo registro.
