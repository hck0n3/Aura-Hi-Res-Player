# Aura Hi-Res Player 0.6.24

## Podcasts de video: elige audio o video 🎙️📺
- En los podcasts que publican **video** (enclosure `video/mp4` en su feed), ahora aparece el **botón de video** en el reproductor — puedes alternar entre **escucharlo (audio)** o **verlo (video)**, igual que en las canciones (mismo motor: audio en segundo plano, controles nativos, sin doble audio).
- Si un podcast es **solo audio**, el botón no aparece (no aplica).

> Nota: depende de que el podcast ofrezca video en su feed RSS. Muchos podcasts son solo audio; en esos no habrá botón de video.

## Calidad (revisión interna antes de publicar)
Este cambio toca el motor de reproducción, así que pasó por una **revisión adversarial**. Se detectaron y corrigieron 3 cosas **antes** de subir:
- **Spinner infinito (alto):** en modo video pegajoso, al avanzar a un episodio solo-audio quedaba un círculo de carga eterno sobre la portada. Corregido: vuelve a audio limpio.
- **Stream equivocado (bajo):** en un episodio solo-video con imagen adjunta, podía agarrar la imagen como stream. Corregido.
- **Diseño de reproductor clásico (medio):** el botón de video no salía si tenías activado el diseño antiguo. Corregido.

## Técnico
- `PodcastRepository`: captura el enclosure `video/*` en `videoUrl` (fallback de stream primario solo si no hay audio ni video).
- `PodcastEpisode.videoUrl` → `MediaMetadata.podcastVideoUrl`.
- `MusicService.applyVideoToCurrent`: para podcasts usa la URL directa (sin YouTube); tracks http/local sin video → desarma video (sin spinner colgado).
- `Player.kt`: el toggle de video aparece con `isVideoSong || podcastVideoUrl` en ambos diseños.
