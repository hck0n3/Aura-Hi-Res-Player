# Aura Hi-Res Player 0.1.9

## Adiós voz rasposa a máximo volumen 🔊
- A volumen máximo, la señal salía demasiado "caliente" (techo −1 dBTP) y **saturaba el amplificador del teléfono** → voz rasposa. Ahora el techo de salida baja ~2.5 dB (headroom para el amp): suena **limpio incluso a tope**, sin distorsión y sin "oleadas" (el multibanda sigue intacto).
- Baja el mismo nivel para TODAS las canciones, así que la consistencia entre temas no cambia — solo un nivel general más cómodo. Si lo quieres más fuerte, sube el volumen del dispositivo (ahora con margen) o el preamp del EQ.

## Volumen parejo en TODAS las canciones 🎚️
- Antes, las pistas sin metadato de loudness (común en fuentes que no son YouTube: descargas, Saavn/Qobuz) se saltaban la normalización y sonaban a su nivel nativo → unas más fuertes que otras.
- Ahora **toda** pista se normaliza al mismo nivel de referencia; si no hay metadato, se usa un valor conservador para que no suene más fuerte que el resto.

## Técnico
- TruePeakLimiterAudioProcessor: CEILING 0.891→0.67 y OUTPUT_TRIM 0.85→0.64 (≈ −2.5 dB de headroom uniforme); softLimit final 0.74/0.70.
- AudioGain: `effectiveLoudnessDb()` + `DEFAULT_UNKNOWN_LOUDNESS_DB` (4 dB) → ninguna pista escapa de la normalización.
- MusicService.setupLoudnessEnhancer: normaliza con `effectiveLoudnessDb` (se quitó la rama de ganancia unidad para pistas sin loudness); se mantiene el guard por mediaId (no salta el volumen al dar me gusta).
- Tests JVM AudioGain: consistencia (ganancia neta = −loudnessDb), atenuación-sólo, makeup acotado y default para loudness desconocido.
