# Aura Hi-Res Player 0.6.46 — Crossfade sin "pumping" de volumen (M3)

## Qué cambia
Durante el **crossfade**, las dos canciones (la que sale y la que entra) compartían el mismo valor de normalización de volumen, así que al mezclarse había un leve **"pumping"** (la canción saliente saltaba de nivel). Ahora **cada reproductor lleva su propia normalización**: la saliente mantiene su nivel y la entrante el suyo → la mezcla es suave.

## Cómo se hizo (con mucho cuidado)
Esto toca el motor de audio, así que fue **evaluado por 7 agentes** (mapeo del motor + 2 planes independientes + un juez que estresó el riesgo) antes de implementarlo, y luego **revisado adversarialmente** (se encontró y corrigió un caso límite en la ruta rápida del crossfade).

- Cambio **estrictamente additivo**: sin crossfade, el audio es **idéntico** a antes (los valores por-reproductor quedan nulos → usa el camino global de siempre).
- La normalización por-reproductor se **limpia** al terminar/saltar/cancelar el crossfade, así que las siguientes canciones se normalizan normal.

## 👉 Para probar (importante)
Activa **crossfade + normalización de volumen** y encola una canción **MUY fuerte seguida de una MUY suave** (y al revés). Durante la mezcla, el volumen debe sonar **estable**, sin que la canción saliente pegue un salto. Si notas algo raro, dímelo y lo reverto al instante.

*(Nota: el otro pendiente de la auditoría, #6 — refactor interno de transacciones de BD — lo dejo fuera: cero beneficio visible y riesgo en toda la sincronización. Recomiendo no tocarlo.)*
