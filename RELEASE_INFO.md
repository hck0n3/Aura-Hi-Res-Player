# Aura Hi-Res Player 0.2.0

## Arreglo: dar "me gusta" ya no baja el volumen 🔊
- La auto-descarga al dar like re-guardaba el formato sin loudness y la normalización caía al valor por defecto → bajaba el volumen. Ahora la pista en curso no se re-normaliza al darle like, y nunca se sobrescribe el loudness real con vacío.

## Descarga al gustar un álbum 💿
- Al dar "me gusta" a un álbum ahora también se descarga completo (igual que con las canciones). Respeta tu ajuste de "descargar al dar me gusta".

## Efectos eliminados 🧹
- Se quitaron **Sala virtual (HRTF)** y **Compresor multibanda** de Efectos DSP (y quedan desactivados aunque los tuvieras encendidos). El resto del sonido (EQ, Tono, normalización, limitador multibanda, firma Aura, etc.) sigue igual.

## En camino (próximas versiones)
- Mostrar y sincronizar tu contenido de YouTube Music (bidireccional, estilo "todo en uno") + en la introducción.
- Copia de seguridad/importación selectiva (playlists, artistas, presets por separado).
- Mejorar las transiciones (precargar la pista entrante para que no haya cortes).

## Técnico
- MusicService.setupLoudnessEnhancer: el guard no re-aplica normalización a la pista en curso (solo "upgrade" de default→loudness real); el upsert de formato preserva el loudness existente si la nueva carga no lo trae.
- JrDspAudioProcessor.Config: hrtfEnabled/mbCompEnabled forzados a false; toggles quitados de SoundSettings; menciones fuera de About/README.
- utils/AutoDownload.downloadSongsIfAutoOnLike(): descarga las canciones de un álbum al gustarlo (gated por AutoDownloadOnLikeKey), usado en AlbumMenu y AlbumScreen.
