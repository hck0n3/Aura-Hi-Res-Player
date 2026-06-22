# Aura Hi-Res Player 0.1.0

## Nivelación de volumen más precisa en tus descargas
- Ahora, al descargar una canción, la app mide su sonoridad real (estándar ITU‑R BS.1770, lo mismo que usan Spotify/YouTube/TIDAL) y la usa para nivelar el volumen — incluso cuando YouTube no trae ese dato. Así tus descargas suenan todas al mismo nivel, sin distorsionar.
- Es automático y en segundo plano; si por algo no se puede medir, se mantiene el dato de YouTube (nunca afecta la reproducción).

## Lo que se hizo (técnico)
- Nuevo LoudnessAnalyzer (ITU‑R BS.1770: K-weighting por sample rate + bloques de 400 ms al 75 % + gating absoluto/relativo) con tests unitarios.
- DownloadLoudnessMeasurer: al completar una descarga, decodifica el archivo cacheado (MediaExtractor + MediaCodec), mide LUFS integrado y guarda loudnessDb = LUFS − (−14). Best-effort: cualquier fallo conserva el valor previo.
