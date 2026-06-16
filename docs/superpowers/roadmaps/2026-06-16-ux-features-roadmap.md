# Roadmap — Nuevas funciones UX (JR MUSIC PRO)

**Creado:** 2026-06-16 (para continuar al día siguiente)
**Branch:** feat/jr-dsp-and-android-improvements
**Estado:** PLANIFICACIÓN — nada implementado aún. Dar respuestas/diseño antes de codear.

---

## Contexto / estado actual del proyecto
- **Suscripción Gumroad + candado de dispositivo: COMPLETO, probado, compilado en nube.** (ver `docs/superpowers/specs/2026-06-15-*` y `plans/2026-06-15-*`). Worker desplegado: `https://round-math-d64e.toberto4000.workers.dev`. product_id `wcPehkIWHRbPKR4_hZLdJQ==`.
- IA YA cableada (OpenRouter, OpenAI, Perplexity, Claude, Gemini, xAI, Mistral, Nvidia, DeepL) pero HOY solo se usa para **traducir letras** → gran oportunidad de reutilizarla.
- Crossfade YA existe. No tocar.

## Ya existe (NO duplicar)
Listen Together (=Jam/SharePlay), Recognition (=Shazam/SongCatcher), Release Radar, letras sync+traducción+romanización, Canvas/video, local media (=subir MP3 parcial), crossfade, EQ paramétrico, Stats, Charts/Explore/Mood, descargas, widgets (vinilo/playlist/reconocedor), import Spotify, álbumes favoritos, búsqueda por voz.

## Descartadas
- **AI DJ** (#4) — el usuario NO la quiere.
- **Samples / feed vertical** (#8) — el usuario NO la quiere.
- **Lossless / Hi-Res / Dolby Atmos** — no factible: la fuente (YouTube) no entrega audio sin pérdidas.

---

## Funciones aprobadas a implementar (7)

Orden por valor vs riesgo. Additivas: pantallas/secciones nuevas, sin reescribir lo existente.

### Ola A — IA y descubrimiento (bajo riesgo, alto valor)
1. **Lista AI por texto** (Spotify) — usuario escribe una idea ("rock para correr de noche") → la IA arma una playlist → se resuelven las canciones en el catálogo (innertube/YT) → se crea la playlist. Reusa la IA ya cableada (hoy solo traduce). Esfuerzo M, ~1 sesión, riesgo bajo. **EMPEZAR POR AQUÍ.**
3. **Smart Shuffle** (Spotify) — shuffle que intercala recomendaciones entre tus canciones. Reusa "related/radio" de innertube. Esfuerzo S–M, ~½–1 sesión, riesgo bajo.
2. **daylist** (Spotify) — playlist que cambia de humor por hora del día y se auto-actualiza. Reusa historial de escucha + buckets de hora. Esfuerzo M, ~1 sesión, riesgo bajo.
9. **Flow Tuner** (Deezer) — stream infinito ajustable (quitar géneros). Reusa recomendaciones. Esfuerzo M, ~1 sesión, riesgo bajo.

### Ola B — datos / visual (bajo-medio riesgo)
7. **Créditos detallados** (Tidal) — compositor/productor/ingeniero vía MusicBrainz. Esfuerzo S–M, ~½–1 sesión, riesgo muy bajo.
5. **Resumen anual estilo Wrapped** (Spotify) — recap compartible; extiende la pantalla **Stats**. Esfuerzo M–L (UI pesada), ~1–2 sesiones, riesgo bajo.

### Ola C — playback (riesgo más alto, AL FINAL)
6. **AutoMix IA** (Apple) — transiciones beat-matched entre temas; sube de nivel el crossfade. Toca `MusicService`/cola. Detrás de toggle, con tests. Esfuerzo L, ~1–2 sesiones, **riesgo medio-alto**.

**Total estimado ≈ 6–9 sesiones de build.** De a una; cada una compila/prueba/APK en nube antes de la siguiente.

---

## Principios para NO romper lo existente
- Mismo pipeline: **brainstorm → spec → plan → subagentes con doble review** (spec + calidad), como en licencias.
- Cada feature **additiva** (archivos/pantallas nuevas). No reescribir lo que funciona.
- **Toggle en Settings** por cada feature → si falla, se apaga.
- **TDD** en la lógica pura (selección de canciones, buckets de hora, dedupe, tuning).
- AutoMix al final y detrás de toggle (toca el motor de reproducción).
- Cada paso: build verde + APK en nube antes de continuar. Sin mergear a main salvo que el usuario lo pida.

---

## Mejoras EXTRA de UX/Interfaz (a validar contra el código actual)
Mayor impacto primero:
- **Onboarding inicial** — elegir artistas/géneros → buenas recomendaciones desde el día 1.
- **Home personalizado** — "Porque escuchaste X", "Continuar escuchando", saludo por hora.
- **Reproductor (now playing) rediseñado** — inmersivo, letras a pantalla completa, gestos (swipe cambia/cierra).
- Búsqueda con resultados instantáneos + filtros + historial.
- Skeleton loaders + estados vacíos con arte (percepción de velocidad).
- Transiciones compartidas Material 3 entre lista → reproductor.
- Tarjetas "now playing"/letra para compartir (ampliar LyricsImageCard).
- Modo coche / pantalla grande (botones grandes).
- Haptics sutiles, sleep timer (verificar si existe), accesibilidad (TalkBack/contraste).
- Pulir Material 3 Expressive / dynamic color (ya en uso).

---

## MAÑANA — por dónde empezar
1. Confirmar con el usuario si la primera ola incluye también alguna mejora de UI (ej. Home o Reproductor).
2. Arrancar **brainstorm + spec** de **#1 Lista AI por texto** (mayor valor, bajo riesgo, reusa IA).
3. Antes de explorar a fondo: revisar cómo crea/guarda playlists la app hoy (DAO/Room, CachePlaylistViewModel, LibraryViewModels) y cómo se llama a la IA (api/OpenRouterService, MistralService) para reutilizar.
4. Seguir el pipeline de siempre (brainstorm → writing-plans → subagentes).
