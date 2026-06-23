# Aura Hi-Res Player 0.4.4

## Mejor match de audio (Saavn): adiós versiones karaoke/instrumental 🎯
- Al resolver una canción desde Saavn, ahora se **penalizan** las variantes karaoke, instrumental, "minus one", cover, remix, lo-fi, sped up, slowed y reverb — **a menos que tú las pidas** en el título.
- Resultado: menos veces te suena por error una versión karaoke/instrumental en lugar del tema original.

## Técnico
- Portado del upstream Echo (PR #571): nuevo `com.music.jiosaavn.SaavnMatcher.variantPenalty()` (con test) aplicado en el scoring de `YTPlayerUtils`. Sin tocar la base de datos. Bajo riesgo.
