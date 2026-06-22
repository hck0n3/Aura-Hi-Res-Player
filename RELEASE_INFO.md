# Aura Hi-Res Player 0.0.8

## Transición suave entre canciones, activada por defecto
- La **transición suave (crossfade)** viene activada, con **9 segundos** de duración y estilo **Suave (igual potencia)** — las dos canciones llevan la misma potencia durante la mezcla, sin bajón de volumen.
- **Omitir silencios** y **saltar silencio al instante** vienen activados.

## Arreglado: la transición ya no "suena dos veces"
- Antes, al hacer la transición se oía la canción siguiente como duplicada al inicio. Era porque el reproductor que se desvanecía seguía avanzando solo a la canción siguiente (que ya estaba sonando en el otro reproductor). Corregido: ahora termina limpio.

## Arreglado: el estilo de transición estaba mal etiquetado
- En Ajustes, "Lineal" y "Suave (igual potencia)" estaban invertidos respecto a lo que realmente sonaba. Ahora el nombre coincide con el efecto real.

## Lo que se hizo (técnico)
- Defaults (seed + migración única): CrossfadeEnabled=true, CrossfadeDuration=9s, CrossfadeCurve=1 (equal-power), SkipSilence=true, SkipSilenceInstant=true.
- MusicService.performCrossfadeSwap: al iniciar el crossfade se le recorta la cola al reproductor saliente (REPEAT_MODE_OFF + removeMediaItems tras el ítem actual) para que no auto-avance a la pista siguiente y suene duplicada.
- crossfadeGains: el default pasa de 0 (lineal) a 1 (igual potencia). PlayerSettings.crossfadeCurveName: etiquetas corregidas para que coincidan con la matemática real.
