# Aura Hi-Res Player 0.6.32

## El video ya no se para a cada rato 🎥
Tras un atasco (rebuffer), el reproductor reanudaba el video con apenas **1 segundo** de búfer y se volvía a atascar enseguida — un bucle de tirones. Ahora, al atascarse, **acumula ~5 segundos** antes de reanudar (el valor por defecto de Media3), así el video HD va **fluido y sin pararse** constantemente.

> El arranque del audio sigue siendo rápido (no cambió); solo cambia cuánto búfer junta tras un atasco.

## Técnico
- `MusicService` LoadControl: `bufferForPlaybackAfterRebufferMs` 1000 → `DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS` (~5000). `bufferForPlaybackMs` se mantiene en 500 (arranque rápido).
